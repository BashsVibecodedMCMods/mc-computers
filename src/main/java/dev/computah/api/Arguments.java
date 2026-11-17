package dev.computah.api;

import dev.computah.machine.LuaConverter;
import dev.computah.repack.cobalt.LuaError;
import dev.computah.repack.cobalt.LuaString;
import dev.computah.repack.cobalt.LuaValue;
import dev.computah.repack.cobalt.Varargs;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Arguments of a component call. Indices start at 0, as in OpenComputers. */
public final class Arguments {
    private final Varargs args;

    public Arguments(Varargs args) {
        this.args = args;
    }

    public static Arguments of(Object... values) {
        return new Arguments(LuaConverter.toVarargs(values));
    }

    public int count() {
        return args.count();
    }

    public LuaValue raw(int i) {
        return args.arg(i + 1);
    }

    public boolean isNil(int i) {
        return raw(i).isNil();
    }

    public boolean isString(int i) {
        return raw(i) instanceof LuaString;
    }

    public boolean isNumber(int i) {
        return raw(i).type() == dev.computah.repack.cobalt.Constants.TNUMBER;
    }

    public boolean isTable(int i) {
        return raw(i).type() == dev.computah.repack.cobalt.Constants.TTABLE;
    }

    public boolean isBoolean(int i) {
        return raw(i).type() == dev.computah.repack.cobalt.Constants.TBOOLEAN;
    }

    private ComponentException bad(int i, String expected) {
        return new ComponentException("bad argument #" + (i + 1) + " (" + expected + " expected, got " + raw(i).typeName() + ")");
    }

    public Object get(int i) {
        return LuaConverter.toJava(raw(i));
    }

    public byte[] checkBytes(int i) throws ComponentException {
        LuaValue v = raw(i);
        if (v instanceof LuaString s) {
            byte[] out = new byte[s.length()];
            s.copyTo(out, 0);
            return out;
        }
        if (v.isNumber()) return v.toString().getBytes(StandardCharsets.UTF_8);
        throw bad(i, "string");
    }

    /** A string argument decoded as UTF-8. */
    public String checkString(int i) throws ComponentException {
        return new String(checkBytes(i), StandardCharsets.UTF_8);
    }

    public String optString(int i, String def) throws ComponentException {
        return isNil(i) ? def : checkString(i);
    }

    public double checkDouble(int i) throws ComponentException {
        LuaValue v = raw(i);
        if (v.type() == dev.computah.repack.cobalt.Constants.TNUMBER) return v.toDouble();
        if (v instanceof LuaString) {
            LuaValue n = v.toNumber();
            if (!n.isNil()) return n.toDouble();
        }
        throw bad(i, "number");
    }

    public double optDouble(int i, double def) throws ComponentException {
        return isNil(i) ? def : checkDouble(i);
    }

    public int checkInteger(int i) throws ComponentException {
        double d = checkDouble(i);
        if (Double.isNaN(d)) throw bad(i, "number");
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, Math.floor(d)));
    }

    public int optInteger(int i, int def) throws ComponentException {
        return isNil(i) ? def : checkInteger(i);
    }

    public boolean checkBoolean(int i) throws ComponentException {
        LuaValue v = raw(i);
        if (v.type() != dev.computah.repack.cobalt.Constants.TBOOLEAN) throw bad(i, "boolean");
        return v.toBoolean();
    }

    public boolean optBoolean(int i, boolean def) throws ComponentException {
        return isNil(i) ? def : checkBoolean(i);
    }

    @SuppressWarnings("unchecked")
    public Map<Object, Object> checkTable(int i) throws ComponentException {
        if (!isTable(i)) throw bad(i, "table");
        return (Map<Object, Object>) LuaConverter.toJava(raw(i));
    }

    public Map<Object, Object> optTable(int i, Map<Object, Object> def) throws ComponentException {
        return isNil(i) ? def : checkTable(i);
    }

}
