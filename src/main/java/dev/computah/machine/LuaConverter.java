package dev.computah.machine;

import org.squiddev.cobalt.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Converts between Java values and Lua values. Strings go out as UTF-8 bytes; tables come in as maps. */
public final class LuaConverter {
    private static final int MAX_DEPTH = 32;

    private LuaConverter() {
    }

    public static LuaValue toLua(Object o) {
        return toLua(o, new IdentityHashMap<>(), 0);
    }

    private static LuaValue toLua(Object o, IdentityHashMap<Object, LuaValue> seen, int depth) {
        if (o == null) return Constants.NIL;
        if (o instanceof LuaValue v) return v;
        if (o instanceof Boolean b) return ValueFactory.valueOf(b);
        if (o instanceof Integer || o instanceof Short || o instanceof Byte) return ValueFactory.valueOf(((Number) o).intValue());
        if (o instanceof Long l) {
            if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) return ValueFactory.valueOf(l.intValue());
            return ValueFactory.valueOf((double) l);
        }
        if (o instanceof Number n) return ValueFactory.valueOf(n.doubleValue());
        if (o instanceof String s) return ValueFactory.valueOf(s.getBytes(StandardCharsets.UTF_8));
        if (o instanceof byte[] b) return ValueFactory.valueOf(b);
        if (o instanceof Character c) return ValueFactory.valueOf(String.valueOf(c).getBytes(StandardCharsets.UTF_8));
        if (depth > MAX_DEPTH) return Constants.NIL;
        LuaValue existing = seen.get(o);
        if (existing != null) return existing;
        if (o instanceof Map<?, ?> m) {
            LuaTable t = new LuaTable();
            seen.put(o, t);
            for (Map.Entry<?, ?> e : m.entrySet()) {
                LuaValue k = toLua(e.getKey(), seen, depth + 1);
                if (k.isNil()) continue;
                try {
                    t.rawset(k, toLua(e.getValue(), seen, depth + 1));
                } catch (LuaError ignored) {
                    // NaN keys
                }
            }
            return t;
        }
        if (o instanceof Collection<?> c) {
            LuaTable t = new LuaTable(c.size(), 0);
            seen.put(o, t);
            int i = 1;
            for (Object x : c) t.rawset(i++, toLua(x, seen, depth + 1));
            t.rawset("n", ValueFactory.valueOf(c.size()));
            return t;
        }
        if (o instanceof Object[] arr) return toLua(Arrays.asList(arr), seen, depth);
        if (o instanceof int[] arr) {
            LuaTable t = new LuaTable(arr.length, 0);
            for (int i = 0; i < arr.length; i++) t.rawset(i + 1, ValueFactory.valueOf(arr[i]));
            return t;
        }
        return ValueFactory.valueOf(o.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static Varargs toVarargs(Object... values) {
        if (values == null || values.length == 0) return Constants.NONE;
        LuaValue[] out = new LuaValue[values.length];
        for (int i = 0; i < values.length; i++) out[i] = toLua(values[i]);
        return ValueFactory.varargsOf(out);
    }

    public static Object toJava(LuaValue v) {
        return toJava(v, new IdentityHashMap<>(), 0);
    }

    private static Object toJava(LuaValue v, IdentityHashMap<LuaValue, Object> seen, int depth) {
        switch (v.type()) {
            case Constants.TNIL:
                return null;
            case Constants.TBOOLEAN:
                return v.toBoolean();
            case Constants.TNUMBER: {
                double d = v.toDouble();
                if (d == Math.rint(d) && Math.abs(d) < 1e15) return (long) d == (int) d ? (Object) (int) d : (Object) (long) d;
                return d;
            }
            case Constants.TSTRING: {
                LuaString s = (LuaString) v;
                byte[] b = new byte[s.length()];
                s.copyTo(b, 0);
                return new String(b, StandardCharsets.UTF_8);
            }
            case Constants.TTABLE: {
                if (depth > MAX_DEPTH) return null;
                Object existing = seen.get(v);
                if (existing != null) return existing;
                LuaTable t = (LuaTable) v;
                Map<Object, Object> map = new LinkedHashMap<>();
                seen.put(v, map);
                LuaValue k = Constants.NIL;
                try {
                    while (true) {
                        Varargs n = t.next(k);
                        k = n.first();
                        if (k.isNil()) break;
                        Object jk = toJava(k, seen, depth + 1);
                        Object jv = toJava(n.arg(2), seen, depth + 1);
                        if (jk != null) map.put(jk, jv);
                    }
                } catch (LuaError ignored) {
                }
                return map;
            }
            default:
                return null;
        }
    }

    public static String utf8(LuaValue v) {
        if (v instanceof LuaString s) {
            byte[] b = new byte[s.length()];
            s.copyTo(b, 0);
            return new String(b, StandardCharsets.UTF_8);
        }
        return v.toString();
    }

    public static LuaString str(String s) {
        return ValueFactory.valueOf(s.getBytes(StandardCharsets.UTF_8));
    }
}
