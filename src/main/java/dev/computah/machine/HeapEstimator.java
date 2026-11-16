package dev.computah.machine;

import org.squiddev.cobalt.*;
import org.squiddev.cobalt.debug.DebugFrame;
import org.squiddev.cobalt.debug.Upvalue;
import org.squiddev.cobalt.function.LuaInterpretedFunction;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;

/**
 * Estimates how much memory a Lua state uses by walking everything reachable from its roots. Cobalt has no allocation
 * hooks, so this is approximate: it counts strings, tables, closures, prototypes and coroutine stacks with rough
 * per-object costs (similar to 64-bit native Lua). It stops early once the limit is passed.
 */
public final class HeapEstimator {
    private static final int TABLE = 56, SLOT = 32, STRING = 24, CLOSURE = 40, UPVALUE = 40, THREAD = 200, PROTO = 120;

    private HeapEstimator() {
    }

    public static long estimate(long limit, LuaValue... roots) {
        IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
        ArrayDeque<Object> queue = new ArrayDeque<>();
        for (LuaValue r : roots) if (r != null) queue.add(r);
        long total = 0;
        while (!queue.isEmpty()) {
            if (total > limit) return total;
            Object o = queue.poll();
            if (o == null || seen.put(o, Boolean.TRUE) != null) continue;
            if (o instanceof LuaString s) {
                total += STRING + s.length();
            } else if (o instanceof LuaTable t) {
                total += TABLE + (long) t.size() * SLOT;
                LuaValue k = Constants.NIL;
                try {
                    while (true) {
                        Varargs n = t.next(k);
                        k = n.first();
                        if (k.isNil()) break;
                        add(queue, k);
                        add(queue, n.arg(2));
                    }
                } catch (LuaError ignored) {
                }
                LuaTable mt = t.getMetatable(null);
                if (mt != null) queue.add(mt);
            } else if (o instanceof LuaInterpretedFunction f) {
                total += CLOSURE;
                queue.add(f.getPrototype());
                for (Upvalue u : f.upvalues) {
                    if (u != null && seen.put(u, Boolean.TRUE) == null) {
                        total += UPVALUE;
                        add(queue, u.getValue());
                    }
                }
            } else if (o instanceof Prototype p) {
                total += PROTO + p.code.length * 4L + p.constants.length * 16L;
                for (LuaValue c : p.constants) add(queue, c);
                for (Prototype c : p.children) queue.add(c);
            } else if (o instanceof LuaThread t) {
                total += THREAD;
                DebugFrame f = t.getDebugState().getStackUnsafe();
                while (f != null) {
                    if (f.func != null) queue.add(f.func);
                    if (f.stack != null) {
                        total += f.stack.length * 16L;
                        for (LuaValue v : f.stack) add(queue, v);
                    }
                    f = f.previous;
                }
            }
        }
        return total;
    }

    private static void add(ArrayDeque<Object> queue, LuaValue v) {
        if (v == null) return;
        int t = v.type();
        if (t == Constants.TSTRING || t == Constants.TTABLE || t == Constants.TFUNCTION || t == Constants.TTHREAD) queue.add(v);
    }
}
