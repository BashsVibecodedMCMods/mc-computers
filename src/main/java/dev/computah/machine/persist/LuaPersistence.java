package dev.computah.machine.persist;

import dev.computah.repack.cobalt.*;
import sun.misc.Unsafe;

import java.io.*;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Saves and restores a running Lua heap, including suspended coroutines, like Eris does for native Lua.
 * <p>
 * The object graph reachable from the roots is written as a table of objects followed by their contents, so cycles
 * and shared references survive and deep structures don't overflow the Java stack. Lua values have compact
 * encodings; tables are stored through their public API; everything else from the (repackaged) Cobalt runtime is
 * stored field by field with reflection – coroutines, call frames, upvalues, prototypes, closures, pcall state.
 * <p>
 * Objects that must not be copied – library functions, the machine's native functions, the LuaState itself – are
 * "permanents": they are written by name and looked up in the fresh state on restore (see {@link Perms}).
 * Anything that can't be stored (for example a Java lambda captured by a Cobalt internal) makes saving fail with
 * {@link NotPersistableException}; callers then fall back to rebooting the computer.
 */
public final class LuaPersistence {
    public static final int FORMAT = 1;
    private static final int MAGIC = 0x43504C53; // "CPLS"
    private static final String PACKAGE = LuaValue.class.getPackageName().substring(0, LuaValue.class.getPackageName().lastIndexOf('.') + 1);

    private static final int T_NULL = 0, T_NIL = 1, T_TRUE = 2, T_FALSE = 3, T_INT = 4, T_DOUBLE = 5, T_REF = 6, T_PERM = 7,
            T_JSTRING = 8, T_ENUM = 9, T_BOOL = 10, T_BYTE = 11, T_SHORT = 12, T_CHAR = 13, T_JINT = 14, T_LONG = 15,
            T_FLOAT = 16, T_JDOUBLE = 17;
    private static final int K_STRING = 1, K_TABLE = 2, K_OBJECT = 3, K_ARRAY = 4;

    private static final Unsafe UNSAFE;

    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (Unsafe) f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static final Map<Class<?>, Field[]> FIELDS = new ConcurrentHashMap<>();

    private LuaPersistence() {
    }

    public static final class NotPersistableException extends Exception {
        public NotPersistableException(String message) {
            super(message);
        }
    }

    // region Reflection helpers

    private static Field[] fields(Class<?> type) {
        return FIELDS.computeIfAbsent(type, t -> {
            List<Field> out = new ArrayList<>();
            for (Class<?> c = t; c != null && c != Object.class; c = c.getSuperclass()) {
                Field[] declared = c.getDeclaredFields();
                Arrays.sort(declared, Comparator.comparing(Field::getName));
                for (Field f : declared) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    out.add(f);
                }
            }
            return out.toArray(new Field[0]);
        });
    }

    private static boolean storable(Class<?> type) {
        if (type.isArray()) return true;
        if (type.isHidden() || type.isSynthetic() || type.isAnonymousClass() && !type.getName().startsWith(PACKAGE)) return false;
        return type.getName().startsWith(PACKAGE);
    }

    private static Class<?> classFor(String name) throws IOException {
        try {
            return switch (name) {
                case "Z" -> boolean.class;
                case "B" -> byte.class;
                case "S" -> short.class;
                case "C" -> char.class;
                case "I" -> int.class;
                case "J" -> long.class;
                case "F" -> float.class;
                case "D" -> double.class;
                default -> Class.forName(name, false, LuaPersistence.class.getClassLoader());
            };
        } catch (ClassNotFoundException e) {
            throw new IOException("unknown class " + name);
        }
    }

    private static String nameOf(Class<?> c) {
        if (c == boolean.class) return "Z";
        if (c == byte.class) return "B";
        if (c == short.class) return "S";
        if (c == char.class) return "C";
        if (c == int.class) return "I";
        if (c == long.class) return "J";
        if (c == float.class) return "F";
        if (c == double.class) return "D";
        return c.getName();
    }

    // endregion

    // region Writing

    /** Serializes the roots. Throws if something reachable can't be stored. */
    public static byte[] save(Object[] roots, Perms perms) throws NotPersistableException {
        try {
            return new Writer(perms).write(roots);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (IllegalAccessException e) {
            throw new NotPersistableException("reflection failed: " + e.getMessage());
        }
    }

    private static final class Writer {
        private final Perms perms;
        private final IdentityHashMap<Object, Integer> ids = new IdentityHashMap<>();
        private final List<Object> objects = new ArrayList<>();
        private final ArrayDeque<Object> pending = new ArrayDeque<>();
        private final Map<String, Integer> classIds = new HashMap<>();
        private final IdentityHashMap<Object, Object> parents = new IdentityHashMap<>();
        private Object current;
        private final List<String> classNames = new ArrayList<>();

        Writer(Perms perms) {
            this.perms = perms;
        }

        private boolean inline(Object o) {
            return o == null || o instanceof LuaNil || o instanceof LuaBoolean || o instanceof LuaInteger || o instanceof LuaDouble
                    || o instanceof String || o instanceof Boolean || o instanceof Number || o instanceof Character
                    || o instanceof Enum<?> || perms.name(o) != null;
        }

        private void discover(Object o) throws NotPersistableException, IllegalAccessException {
            if (inline(o) || ids.containsKey(o)) return;
            Class<?> c = o.getClass();
            if (!(o instanceof LuaString) && !(o instanceof LuaTable) && !storable(c)) {
                StringBuilder path = new StringBuilder();
                for (Object p = current; p != null && path.length() < 600; p = parents.get(p)) path.append(" <- ").append(p.getClass().getSimpleName());
                throw new NotPersistableException("can't save a " + c.getName() + path);
            }
            parents.put(o, current);
            if (o instanceof LuaState) throw new NotPersistableException("unregistered LuaState");
            ids.put(o, objects.size());
            objects.add(o);
            pending.add(o);
        }

        private void discoverChildren(Object o) throws NotPersistableException, IllegalAccessException {
            if (o instanceof LuaString) return;
            if (o instanceof LuaTable t) {
                LuaValue k = Constants.NIL;
                while (true) {
                    Varargs n;
                    try {
                        n = t.next(k);
                    } catch (LuaError e) {
                        throw new NotPersistableException("table iteration failed");
                    }
                    k = n.first();
                    if (k.isNil()) break;
                    discover(k);
                    discover(n.arg(2));
                }
                LuaTable mt = t.getMetatable(null);
                if (mt != null) discover(mt);
                return;
            }
            Class<?> c = o.getClass();
            if (c.isArray()) {
                if (!c.getComponentType().isPrimitive()) {
                    for (int i = 0, n = Array.getLength(o); i < n; i++) discover(Array.get(o, i));
                }
                return;
            }
            for (Field f : fields(c)) {
                if (!f.getType().isPrimitive()) discover(f.get(o));
            }
        }

        private int classId(Class<?> c) {
            return classIds.computeIfAbsent(nameOf(c), n -> {
                classNames.add(n);
                return classNames.size() - 1;
            });
        }

        byte[] write(Object[] roots) throws IOException, NotPersistableException, IllegalAccessException {
            for (Object r : roots) discover(r);
            while (!pending.isEmpty()) {
                current = pending.poll();
                discoverChildren(current);
            }

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new java.util.zip.DeflaterOutputStream(bytes), 65536));
            out.writeInt(MAGIC);
            out.writeInt(FORMAT);
            out.writeUTF(perms.signature());

            // Headers: enough to allocate every object.
            ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
            DataOutputStream h = new DataOutputStream(headerBytes);
            for (Object o : objects) {
                if (o instanceof LuaString s) {
                    h.writeByte(K_STRING);
                    h.writeInt(s.length());
                    s.write((DataOutput) h);
                } else if (o instanceof LuaTable) {
                    h.writeByte(K_TABLE);
                } else if (o.getClass().isArray()) {
                    h.writeByte(K_ARRAY);
                    h.writeShort(classId(o.getClass().getComponentType()));
                    h.writeInt(Array.getLength(o));
                } else {
                    h.writeByte(K_OBJECT);
                    h.writeShort(classId(o.getClass()));
                }
            }
            out.writeInt(classNames.size());
            for (String n : classNames) out.writeUTF(n);
            out.writeInt(objects.size());
            headerBytes.writeTo(out);

            // Bodies.
            for (Object o : objects) {
                if (o instanceof LuaString) continue;
                if (o instanceof LuaTable t) {
                    List<LuaValue> entries = new ArrayList<>();
                    LuaValue k = Constants.NIL;
                    while (true) {
                        Varargs n;
                        try {
                            n = t.next(k);
                        } catch (LuaError e) {
                            throw new NotPersistableException("table iteration failed");
                        }
                        k = n.first();
                        if (k.isNil()) break;
                        entries.add(k);
                        entries.add(n.arg(2));
                    }
                    out.writeInt(t.length());
                    out.writeInt(entries.size() / 2);
                    for (LuaValue v : entries) value(out, v);
                    value(out, t.getMetatable(null));
                    continue;
                }
                Class<?> c = o.getClass();
                if (c.isArray()) {
                    Class<?> ct = c.getComponentType();
                    int n = Array.getLength(o);
                    if (ct == int.class) for (int x : (int[]) o) out.writeInt(x);
                    else if (ct == byte.class) out.write((byte[]) o);
                    else if (ct == boolean.class) for (boolean x : (boolean[]) o) out.writeBoolean(x);
                    else if (ct == long.class) for (long x : (long[]) o) out.writeLong(x);
                    else if (ct == double.class) for (double x : (double[]) o) out.writeDouble(x);
                    else if (ct == short.class) for (short x : (short[]) o) out.writeShort(x);
                    else if (ct == char.class) for (char x : (char[]) o) out.writeChar(x);
                    else if (ct == float.class) for (float x : (float[]) o) out.writeFloat(x);
                    else for (int i = 0; i < n; i++) value(out, Array.get(o, i));
                    continue;
                }
                for (Field f : fields(c)) {
                    Class<?> ft = f.getType();
                    if (ft == int.class) out.writeInt(f.getInt(o));
                    else if (ft == boolean.class) out.writeBoolean(f.getBoolean(o));
                    else if (ft == long.class) out.writeLong(f.getLong(o));
                    else if (ft == double.class) out.writeDouble(f.getDouble(o));
                    else if (ft == byte.class) out.writeByte(f.getByte(o));
                    else if (ft == short.class) out.writeShort(f.getShort(o));
                    else if (ft == char.class) out.writeChar(f.getChar(o));
                    else if (ft == float.class) out.writeFloat(f.getFloat(o));
                    else value(out, f.get(o));
                }
            }
            out.writeInt(roots.length);
            for (Object r : roots) value(out, r);
            out.close();
            return bytes.toByteArray();
        }

        private void value(DataOutputStream out, Object o) throws IOException {
            if (o == null) {
                out.writeByte(T_NULL);
                return;
            }
            String perm = perms.name(o);
            if (perm != null) {
                out.writeByte(T_PERM);
                out.writeUTF(perm);
            } else if (o instanceof LuaNil) {
                out.writeByte(T_NIL);
            } else if (o instanceof LuaBoolean b) {
                out.writeByte(b.toBoolean() ? T_TRUE : T_FALSE);
            } else if (o instanceof LuaInteger i) {
                out.writeByte(T_INT);
                out.writeInt(i.toInteger());
            } else if (o instanceof LuaDouble d) {
                out.writeByte(T_DOUBLE);
                out.writeDouble(d.toDouble());
            } else if (o instanceof String s) {
                out.writeByte(T_JSTRING);
                out.writeUTF(s);
            } else if (o instanceof Enum<?> e) {
                out.writeByte(T_ENUM);
                out.writeUTF(e.getDeclaringClass().getName());
                out.writeUTF(e.name());
            } else if (o instanceof Boolean b) {
                out.writeByte(T_BOOL);
                out.writeBoolean(b);
            } else if (o instanceof Byte b) {
                out.writeByte(T_BYTE);
                out.writeByte(b);
            } else if (o instanceof Short s) {
                out.writeByte(T_SHORT);
                out.writeShort(s);
            } else if (o instanceof Character c) {
                out.writeByte(T_CHAR);
                out.writeChar(c);
            } else if (o instanceof Integer i) {
                out.writeByte(T_JINT);
                out.writeInt(i);
            } else if (o instanceof Long l) {
                out.writeByte(T_LONG);
                out.writeLong(l);
            } else if (o instanceof Float f) {
                out.writeByte(T_FLOAT);
                out.writeFloat(f);
            } else if (o instanceof Double d) {
                out.writeByte(T_JDOUBLE);
                out.writeDouble(d);
            } else {
                Integer id = ids.get(o);
                if (id == null) throw new IllegalStateException("undiscovered object " + o.getClass());
                out.writeByte(T_REF);
                out.writeInt(id);
            }
        }
    }

    // endregion

    // region Reading

    /** Restores the roots into the state the perms were built from. */
    public static Object[] load(byte[] data, Perms perms, LuaState state) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(new java.util.zip.InflaterInputStream(new ByteArrayInputStream(data)), 65536));
        if (in.readInt() != MAGIC) throw new IOException("not a saved Lua state");
        if (in.readInt() != FORMAT) throw new IOException("saved with a different format");
        String signature = in.readUTF();
        if (!signature.equals(perms.signature())) throw new IOException("saved with a different set of libraries");

        int nClasses = in.readInt();
        Class<?>[] classes = new Class<?>[nClasses];
        for (int i = 0; i < nClasses; i++) classes[i] = classFor(in.readUTF());

        int n = in.readInt();
        Object[] objects = new Object[n];
        Class<?>[] recordTypes = new Class<?>[n];
        for (int i = 0; i < n; i++) {
            int kind = in.readUnsignedByte();
            switch (kind) {
                case K_STRING -> {
                    int len = in.readInt();
                    byte[] b = new byte[len];
                    in.readFully(b);
                    objects[i] = LuaString.valueOf(b);
                }
                case K_TABLE -> objects[i] = new LuaTable();
                case K_ARRAY -> {
                    Class<?> ct = classes[in.readUnsignedShort()];
                    objects[i] = Array.newInstance(ct, in.readInt());
                }
                case K_OBJECT -> {
                    Class<?> c = classes[in.readUnsignedShort()];
                    if (!storable(c)) throw new IOException("refusing to create " + c.getName());
                    if (c.isRecord()) {
                        // Records can only be made through their constructor, once their components are known.
                        recordTypes[i] = c;
                    } else {
                        try {
                            objects[i] = UNSAFE.allocateInstance(c);
                        } catch (InstantiationException e) {
                            throw new IOException("can't create " + c.getName());
                        }
                    }
                }
                default -> throw new IOException("bad object kind " + kind);
            }
        }
        // Read every body first, keeping references as ids, so records can be built before anything uses them.
        Object[][] bodies = new Object[n][];
        for (int i = 0; i < n; i++) {
            Object o = objects[i];
            if (o instanceof LuaString) continue;
            if (o instanceof LuaTable) {
                int arrayLength = in.readInt();
                int entries = in.readInt();
                Object[] body = new Object[2 + entries * 2 + 1];
                body[0] = arrayLength;
                body[1] = entries;
                for (int j = 0; j < entries * 2 + 1; j++) body[2 + j] = token(in, perms);
                bodies[i] = body;
            } else if (o != null && o.getClass().isArray()) {
                Class<?> ct = o.getClass().getComponentType();
                int len = Array.getLength(o);
                if (ct == int.class) for (int j = 0; j < len; j++) ((int[]) o)[j] = in.readInt();
                else if (ct == byte.class) in.readFully((byte[]) o);
                else if (ct == boolean.class) for (int j = 0; j < len; j++) ((boolean[]) o)[j] = in.readBoolean();
                else if (ct == long.class) for (int j = 0; j < len; j++) ((long[]) o)[j] = in.readLong();
                else if (ct == double.class) for (int j = 0; j < len; j++) ((double[]) o)[j] = in.readDouble();
                else if (ct == short.class) for (int j = 0; j < len; j++) ((short[]) o)[j] = in.readShort();
                else if (ct == char.class) for (int j = 0; j < len; j++) ((char[]) o)[j] = in.readChar();
                else if (ct == float.class) for (int j = 0; j < len; j++) ((float[]) o)[j] = in.readFloat();
                else {
                    Object[] body = new Object[len];
                    for (int j = 0; j < len; j++) body[j] = token(in, perms);
                    bodies[i] = body;
                }
            } else {
                Class<?> c = o != null ? o.getClass() : recordTypes[i];
                Field[] fs = fields(c);
                Object[] body = new Object[fs.length];
                for (int j = 0; j < fs.length; j++) {
                    Class<?> ft = fs[j].getType();
                    if (ft == int.class) body[j] = in.readInt();
                    else if (ft == boolean.class) body[j] = in.readBoolean();
                    else if (ft == long.class) body[j] = in.readLong();
                    else if (ft == double.class) body[j] = in.readDouble();
                    else if (ft == byte.class) body[j] = in.readByte();
                    else if (ft == short.class) body[j] = in.readShort();
                    else if (ft == char.class) body[j] = in.readChar();
                    else if (ft == float.class) body[j] = in.readFloat();
                    else body[j] = token(in, perms);
                }
                bodies[i] = body;
            }
        }
        Resolver r = new Resolver(objects, recordTypes, bodies);
        try {
            for (int i = 0; i < n; i++) if (recordTypes[i] != null) r.record(i);
            for (int i = 0; i < n; i++) {
                Object o = objects[i];
                Object[] body = bodies[i];
                if (body == null || recordTypes[i] != null) continue;
                if (o instanceof LuaTable t) {
                    int arrayLength = (Integer) body[0];
                    int entries = (Integer) body[1];
                    LuaTable mt = (LuaTable) r.resolve(body[2 + entries * 2]);
                    if (arrayLength > 0) t.presize(arrayLength);
                    if (mt != null) t.setMetatable(state, mt);
                    for (int j = 0; j < entries; j++) {
                        t.rawset((LuaValue) r.resolve(body[2 + j * 2]), (LuaValue) r.resolve(body[3 + j * 2]));
                    }
                } else if (o.getClass().isArray()) {
                    for (int j = 0; j < body.length; j++) Array.set(o, j, r.resolve(body[j]));
                } else {
                    Field[] fs = fields(o.getClass());
                    for (int j = 0; j < fs.length; j++) fs[j].set(o, r.resolve(body[j]));
                }
            }
        } catch (ReflectiveOperationException | IllegalArgumentException | ClassCastException | LuaError e) {
            throw new IOException("corrupt saved state: " + e, e);
        }
        Object[] roots = new Object[in.readInt()];
        try {
            for (int i = 0; i < roots.length; i++) roots[i] = r.resolve(token(in, perms));
        } catch (ReflectiveOperationException e) {
            throw new IOException("corrupt saved state: " + e, e);
        }
        return roots;
    }

    /** A reference to object number {@code id}, resolved once all objects exist. */
    private record Ref(int id) {
    }

    private static final class Resolver {
        private final Object[] objects;
        private final Class<?>[] recordTypes;
        private final Object[][] bodies;
        private final boolean[] building;

        Resolver(Object[] objects, Class<?>[] recordTypes, Object[][] bodies) {
            this.objects = objects;
            this.recordTypes = recordTypes;
            this.bodies = bodies;
            this.building = new boolean[objects.length];
        }

        Object resolve(Object token) throws ReflectiveOperationException, IOException {
            if (!(token instanceof Ref ref)) return token;
            if (ref.id() < 0 || ref.id() >= objects.length) throw new IOException("bad reference");
            if (recordTypes[ref.id()] != null && objects[ref.id()] == null) record(ref.id());
            return objects[ref.id()];
        }

        void record(int i) throws ReflectiveOperationException, IOException {
            if (objects[i] != null) return;
            if (building[i]) throw new IOException("records refer to each other in a cycle");
            building[i] = true;
            Class<?> c = recordTypes[i];
            var components = c.getRecordComponents();
            Field[] fs = fields(c);
            Class<?>[] types = new Class<?>[components.length];
            Object[] values = new Object[components.length];
            for (int k = 0; k < components.length; k++) {
                types[k] = components[k].getType();
                for (int j = 0; j < fs.length; j++) {
                    if (fs[j].getName().equals(components[k].getName())) values[k] = resolve(bodies[i][j]);
                }
            }
            var ctor = c.getDeclaredConstructor(types);
            ctor.setAccessible(true);
            objects[i] = ctor.newInstance(values);
        }
    }

    private static Object token(DataInputStream in, Perms perms) throws IOException {
        in.mark(5);
        int tag = in.readUnsignedByte();
        if (tag == T_REF) return new Ref(in.readInt());
        in.reset();
        return value(in, null, perms);
    }


    private static Object value(DataInputStream in, Object[] objects, Perms perms) throws IOException {
        int tag = in.readUnsignedByte();
        return switch (tag) {
            case T_NULL -> null;
            case T_NIL -> Constants.NIL;
            case T_TRUE -> Constants.TRUE;
            case T_FALSE -> Constants.FALSE;
            case T_INT -> ValueFactory.valueOf(in.readInt());
            case T_DOUBLE -> ValueFactory.valueOf(in.readDouble());
            case T_REF -> {
                int id = in.readInt();
                if (id < 0 || id >= objects.length) throw new IOException("bad reference");
                yield objects[id];
            }
            case T_PERM -> {
                String name = in.readUTF();
                Object o = perms.object(name);
                if (o == null) throw new IOException("unknown permanent " + name);
                yield o;
            }
            case T_JSTRING -> in.readUTF();
            case T_ENUM -> {
                Class<?> c = classFor(in.readUTF());
                String name = in.readUTF();
                Object found = null;
                for (Object e : c.getEnumConstants()) if (((Enum<?>) e).name().equals(name)) found = e;
                if (found == null) throw new IOException("unknown enum constant " + name);
                yield found;
            }
            case T_BOOL -> in.readBoolean();
            case T_BYTE -> in.readByte();
            case T_SHORT -> in.readShort();
            case T_CHAR -> in.readChar();
            case T_JINT -> in.readInt();
            case T_LONG -> in.readLong();
            case T_FLOAT -> in.readFloat();
            case T_JDOUBLE -> in.readDouble();
            default -> throw new IOException("bad value tag " + tag);
        };
    }

    // endregion
}
