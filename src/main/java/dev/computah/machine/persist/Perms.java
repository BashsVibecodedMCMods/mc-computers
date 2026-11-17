package dev.computah.machine.persist;

import dev.computah.repack.cobalt.*;
import dev.computah.repack.cobalt.function.LuaFunction;

import java.util.*;

/**
 * Objects that are written by name instead of being copied when a Lua state is saved: the LuaState, its main
 * thread, the standard library (everything reachable from the globals) and the machine's native functions. A fresh
 * state built the same way yields the same names, so a restored heap points at the new state's library.
 */
public final class Perms {
    private final IdentityHashMap<Object, String> names = new IdentityHashMap<>();
    private final Map<String, Object> objects = new HashMap<>();
    private final String signature;

    public Perms(LuaState state, LuaTable natives) {
        add("state", state);
        add("mainThread", state.getMainThread());
        add("NONE", Constants.NONE);
        walk("G", state.globals());
        walk("N", natives);
        // Library functions can hold other functions internally (ipairs keeps its iterator in the library object it
        // captures). Those show up in running code too, so name them after where they were found.
        List<String> found = new ArrayList<>(objects.keySet());
        Collections.sort(found);
        for (String name : found) {
            Object o = objects.get(name);
            if (o instanceof LuaFunction) inside(name, o, 0, Collections.newSetFromMap(new IdentityHashMap<>()));
        }
        // A short fingerprint of the names, so a state saved with other libraries is rejected.
        List<String> sorted = new ArrayList<>(objects.keySet());
        Collections.sort(sorted);
        signature = Integer.toHexString(String.join(",", sorted).hashCode()) + ":" + sorted.size();
    }

    private void add(String name, Object o) {
        if (o == null || names.containsKey(o) || objects.containsKey(name)) return;
        names.put(o, name);
        objects.put(name, o);
    }

    private void walk(String rootName, LuaTable root) {
        ArrayDeque<Object[]> queue = new ArrayDeque<>();
        queue.add(new Object[]{rootName, root});
        while (!queue.isEmpty()) {
            Object[] item = queue.poll();
            String path = (String) item[0];
            LuaTable table = (LuaTable) item[1];
            if (names.containsKey(table)) continue;
            add(path, table);
            TreeMap<String, LuaValue> entries = new TreeMap<>();
            LuaValue k = Constants.NIL;
            try {
                while (true) {
                    Varargs n = table.next(k);
                    k = n.first();
                    if (k.isNil()) break;
                    if (k instanceof LuaString) entries.put(k.toString(), n.arg(2));
                }
            } catch (LuaError e) {
                continue;
            }
            for (Map.Entry<String, LuaValue> e : entries.entrySet()) {
                String p = path + "." + e.getKey();
                LuaValue v = e.getValue();
                if (v instanceof LuaTable t) queue.add(new Object[]{p, t});
                else if (v instanceof LuaFunction) add(p, v);
            }
            LuaTable mt = table.getMetatable(null);
            if (mt != null) queue.add(new Object[]{path + "#mt", mt});
        }
    }

    private void inside(String path, Object o, int depth, Set<Object> seen) {
        if (depth > 4 || o == null || !seen.add(o)) return;
        Class<?> c = o.getClass();
        if (c.isArray() || c.isPrimitive() || c.getName().startsWith("java.") || o instanceof LuaState || o instanceof LuaString) return;
        List<java.lang.reflect.Field> fields = new ArrayList<>();
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            for (java.lang.reflect.Field f : k.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(f.getModifiers()) && !f.getType().isPrimitive()) fields.add(f);
            }
        }
        fields.sort(Comparator.comparing((java.lang.reflect.Field f) -> f.getDeclaringClass().getName()).thenComparing(java.lang.reflect.Field::getName));
        for (java.lang.reflect.Field f : fields) {
            Object v;
            try {
                f.setAccessible(true);
                v = f.get(o);
            } catch (ReflectiveOperationException | RuntimeException e) {
                continue;
            }
            String p = path + "/" + f.getName();
            if (v instanceof LuaFunction && !names.containsKey(v)) add(p, v);
            if (!(v instanceof LuaTable)) inside(p, v, depth + 1, seen);
        }
    }

    public String name(Object o) {
        return names.get(o);
    }

    public Object object(String name) {
        return objects.get(name);
    }

    public String signature() {
        return signature;
    }
}
