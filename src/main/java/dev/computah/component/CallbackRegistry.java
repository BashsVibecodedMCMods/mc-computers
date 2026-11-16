package dev.computah.component;

import dev.computah.api.Arguments;
import dev.computah.api.Callback;
import dev.computah.api.Component;
import dev.computah.api.Context;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/** Finds and caches the {@link Callback} methods of each component class. */
public final class CallbackRegistry {
    public record Entry(String name, boolean direct, int limit, String doc, MethodHandle handle) {
        public Object[] invoke(Component target, Context ctx, Arguments args) throws Throwable {
            return (Object[]) handle.invoke(target, ctx, args);
        }
    }

    private static final Map<Class<?>, Map<String, Entry>> CACHE = new ConcurrentHashMap<>();

    private CallbackRegistry() {
    }

    public static Map<String, Entry> of(Component component) {
        return CACHE.computeIfAbsent(component.getClass(), CallbackRegistry::scan);
    }

    private static Map<String, Entry> scan(Class<?> type) {
        Map<String, Entry> out = new TreeMap<>();
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        for (Method m : type.getMethods()) {
            Callback cb = m.getAnnotation(Callback.class);
            if (cb == null || Modifier.isStatic(m.getModifiers())) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p.length != 2 || p[0] != Context.class || p[1] != Arguments.class || m.getReturnType() != Object[].class) {
                throw new IllegalStateException("Bad @Callback signature: " + m);
            }
            String name = cb.value().isEmpty() ? m.getName() : cb.value();
            try {
                out.put(name, new Entry(name, cb.direct(), cb.limit(), cb.doc(), lookup.unreflect(m)));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot access @Callback " + m, e);
            }
        }
        return Collections.unmodifiableMap(out);
    }
}
