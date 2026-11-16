package dev.computah.machine;

import dev.computah.api.Component;
import dev.computah.component.CallbackRegistry;
import org.squiddev.cobalt.*;
import org.squiddev.cobalt.function.LibFunction;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Map;

/** Java functions handed to machine.lua, which builds the sandboxed computer/component/unicode/os libraries from them. */
final class MachineApis {
    private MachineApis() {
    }

    private static void fn(LuaTable t, String name, LibFunction.ManyArgs f) {
        t.rawset(name, LibFunction.createV(f));
    }

    static LuaTable create(Machine m, LuaState state) {
        LuaTable n = new LuaTable();

        // component
        fn(n, "invoke", (s, a) -> m.invoke(a));
        fn(n, "list", (s, a) -> {
            LuaTable t = new LuaTable();
            for (Component c : m.components()) t.rawset(LuaConverter.str(c.address()), LuaConverter.str(c.type()));
            return t;
        });
        fn(n, "type", (s, a) -> {
            Component c = m.component(LuaConverter.utf8(a.arg(1)));
            return c == null ? ValueFactory.varargsOf(Constants.NIL, LuaConverter.str("no such component")) : LuaConverter.str(c.type());
        });
        fn(n, "slot", (s, a) -> {
            Component c = m.component(LuaConverter.utf8(a.arg(1)));
            return c == null ? ValueFactory.varargsOf(Constants.NIL, LuaConverter.str("no such component")) : ValueFactory.valueOf(c.slot());
        });
        fn(n, "methods", (s, a) -> {
            Component c = m.component(LuaConverter.utf8(a.arg(1)));
            if (c == null) return ValueFactory.varargsOf(Constants.NIL, LuaConverter.str("no such component"));
            LuaTable t = new LuaTable();
            for (Map.Entry<String, CallbackRegistry.Entry> e : CallbackRegistry.of(c).entrySet()) {
                LuaTable info = new LuaTable();
                info.rawset("direct", ValueFactory.valueOf(e.getValue().direct()));
                info.rawset("limit", ValueFactory.valueOf(e.getValue().limit()));
                t.rawset(LuaConverter.str(e.getKey()), info);
            }
            return t;
        });
        fn(n, "doc", (s, a) -> {
            Component c = m.component(LuaConverter.utf8(a.arg(1)));
            if (c == null) return ValueFactory.varargsOf(Constants.NIL, LuaConverter.str("no such component"));
            CallbackRegistry.Entry e = CallbackRegistry.of(c).get(LuaConverter.utf8(a.arg(2)));
            return e == null || e.doc().isEmpty() ? Constants.NIL : LuaConverter.str(e.doc());
        });

        // computer
        fn(n, "address", (s, a) -> LuaConverter.str(m.address()));
        fn(n, "tmpAddress", (s, a) -> {
            String t = m.tmpAddress();
            return t == null ? Constants.NIL : LuaConverter.str(t);
        });
        fn(n, "uptime", (s, a) -> ValueFactory.valueOf(m.uptime()));
        fn(n, "energy", (s, a) -> ValueFactory.valueOf(cap(m.host().energy())));
        fn(n, "maxEnergy", (s, a) -> ValueFactory.valueOf(cap(m.host().maxEnergy())));
        fn(n, "totalMemory", (s, a) -> ValueFactory.valueOf((double) m.totalMemory()));
        fn(n, "freeMemory", (s, a) -> ValueFactory.valueOf((double) Math.max(0, m.totalMemory() - m.usedMemory())));
        fn(n, "pushSignal", (s, a) -> {
            if (!(a.arg(1) instanceof LuaString)) throw new LuaError("bad argument #1 (string expected)");
            Object[] args = new Object[Math.max(0, a.count() - 1)];
            for (int i = 0; i < args.length; i++) {
                LuaValue v = a.arg(i + 2);
                int t = v.type();
                if (t == Constants.TTABLE || t == Constants.TFUNCTION || t == Constants.TTHREAD || t == Constants.TUSERDATA) {
                    // Signals carry only simple values (tables are copied), like OpenComputers.
                    args[i] = t == Constants.TTABLE ? LuaConverter.toJava(v) : null;
                } else {
                    args[i] = v;
                }
            }
            return ValueFactory.valueOf(m.signal(LuaConverter.utf8(a.arg(1)), args));
        });
        fn(n, "beep", (s, a) -> {
            double freq = a.arg(1).isNil() ? 440 : a.arg(1).checkDouble();
            double dur = a.arg(2).isNil() ? 0.1 : a.arg(2).checkDouble();
            m.host().beep(Math.max(20, Math.min(2000, freq)), Math.max(0.05, Math.min(5, dur)));
            return Constants.NONE;
        });

        // os
        fn(n, "clock", (s, a) -> ValueFactory.valueOf(m.uptime()));
        fn(n, "realTime", (s, a) -> ValueFactory.valueOf(System.currentTimeMillis() / 1000.0));
        fn(n, "date", (s, a) -> date(a));

        // unicode
        fn(n, "uchar", (s, a) -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= a.count(); i++) {
                int cp = a.arg(i).checkInteger();
                if (!Character.isValidCodePoint(cp)) throw new LuaError("bad argument #" + i + " (invalid code point)");
                sb.appendCodePoint(cp);
            }
            return LuaConverter.str(sb.toString());
        });
        fn(n, "ulen", (s, a) -> {
            String str = LuaConverter.utf8(a.arg(1).checkLuaString());
            return ValueFactory.valueOf(str.codePointCount(0, str.length()));
        });
        fn(n, "usub", (s, a) -> {
            String str = LuaConverter.utf8(a.arg(1).checkLuaString());
            int len = str.codePointCount(0, str.length());
            int i = a.arg(2).isNil() ? 1 : a.arg(2).checkInteger();
            int j = a.arg(3).isNil() ? -1 : a.arg(3).checkInteger();
            if (i < 0) i = Math.max(1, len + i + 1);
            if (i == 0) i = 1;
            if (j < 0) j = len + j + 1;
            if (j > len) j = len;
            if (i > j) return LuaConverter.str("");
            int from = str.offsetByCodePoints(0, i - 1);
            int to = str.offsetByCodePoints(0, j);
            return LuaConverter.str(str.substring(from, to));
        });
        fn(n, "uupper", (s, a) -> LuaConverter.str(LuaConverter.utf8(a.arg(1).checkLuaString()).toUpperCase(Locale.ROOT)));
        fn(n, "ulower", (s, a) -> LuaConverter.str(LuaConverter.utf8(a.arg(1).checkLuaString()).toLowerCase(Locale.ROOT)));
        fn(n, "ureverse", (s, a) -> LuaConverter.str(new StringBuilder(LuaConverter.utf8(a.arg(1).checkLuaString())).reverse().toString()));
        fn(n, "ucodepoints", (s, a) -> {
            String str = LuaConverter.utf8(a.arg(1).checkLuaString());
            int[] cps = str.codePoints().toArray();
            LuaTable t = new LuaTable(cps.length, 0);
            for (int i = 0; i < cps.length; i++) t.rawset(i + 1, ValueFactory.valueOf(cps[i]));
            return t;
        });
        return n;
    }

    private static double cap(double d) {
        return Double.isInfinite(d) ? 1e9 : d;
    }

    /** os.date with the common strftime formats and "*t". A leading "!" means UTC. */
    private static Varargs date(Varargs a) throws LuaError {
        String format = a.arg(1).isNil() ? "%c" : LuaConverter.utf8(a.arg(1).checkLuaString());
        long time = a.arg(2).isNil() ? System.currentTimeMillis() / 1000 : a.arg(2).checkLong();
        ZoneId zone = ZoneId.systemDefault();
        if (format.startsWith("!")) {
            zone = ZoneOffset.UTC;
            format = format.substring(1);
        }
        ZonedDateTime d = Instant.ofEpochSecond(time).atZone(zone);
        if (format.startsWith("*t")) {
            LuaTable t = new LuaTable();
            t.rawset("year", ValueFactory.valueOf(d.getYear()));
            t.rawset("month", ValueFactory.valueOf(d.getMonthValue()));
            t.rawset("day", ValueFactory.valueOf(d.getDayOfMonth()));
            t.rawset("hour", ValueFactory.valueOf(d.getHour()));
            t.rawset("min", ValueFactory.valueOf(d.getMinute()));
            t.rawset("sec", ValueFactory.valueOf(d.getSecond()));
            t.rawset("wday", ValueFactory.valueOf(d.getDayOfWeek().getValue() % 7 + 1));
            t.rawset("yday", ValueFactory.valueOf(d.getDayOfYear()));
            t.rawset("isdst", Constants.FALSE);
            return t;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < format.length(); i++) {
            char c = format.charAt(i);
            if (c != '%' || i + 1 >= format.length()) {
                sb.append(c);
                continue;
            }
            char f = format.charAt(++i);
            switch (f) {
                case 'Y' -> sb.append(d.getYear());
                case 'y' -> sb.append(String.format("%02d", d.getYear() % 100));
                case 'm' -> sb.append(String.format("%02d", d.getMonthValue()));
                case 'd' -> sb.append(String.format("%02d", d.getDayOfMonth()));
                case 'H' -> sb.append(String.format("%02d", d.getHour()));
                case 'I' -> sb.append(String.format("%02d", (d.getHour() + 11) % 12 + 1));
                case 'M' -> sb.append(String.format("%02d", d.getMinute()));
                case 'S' -> sb.append(String.format("%02d", d.getSecond()));
                case 'p' -> sb.append(d.getHour() < 12 ? "AM" : "PM");
                case 'A' -> sb.append(d.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ROOT));
                case 'a' -> sb.append(d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ROOT));
                case 'B' -> sb.append(d.getMonth().getDisplayName(TextStyle.FULL, Locale.ROOT));
                case 'b' -> sb.append(d.getMonth().getDisplayName(TextStyle.SHORT, Locale.ROOT));
                case 'j' -> sb.append(String.format("%03d", d.getDayOfYear()));
                case 'c' -> sb.append(String.format("%s %s %2d %02d:%02d:%02d %d",
                        d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ROOT),
                        d.getMonth().getDisplayName(TextStyle.SHORT, Locale.ROOT), d.getDayOfMonth(),
                        d.getHour(), d.getMinute(), d.getSecond(), d.getYear()));
                case 'x' -> sb.append(String.format("%02d/%02d/%02d", d.getMonthValue(), d.getDayOfMonth(), d.getYear() % 100));
                case 'X' -> sb.append(String.format("%02d:%02d:%02d", d.getHour(), d.getMinute(), d.getSecond()));
                case '%' -> sb.append('%');
                default -> throw new LuaError("bad argument #1 to 'date' (invalid conversion specifier '%" + f + "')");
            }
        }
        return ValueFactory.valueOf(sb.toString().getBytes(StandardCharsets.UTF_8));
    }
}
