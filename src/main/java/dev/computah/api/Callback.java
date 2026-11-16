package dev.computah.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method of a {@link Component} as callable from Lua. The method must have the signature
 * {@code Object[] name(Context context, Arguments args)}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Callback {
    /** The Lua name; defaults to the Java method name. */
    String value() default "";

    /**
     * Direct callbacks run on the machine's own thread and return immediately. Indirect ones (direct = false) run on
     * the server thread at the next tick, and the calling machine waits for them. Anything touching the world must be
     * indirect.
     */
    boolean direct() default true;

    /**
     * For direct callbacks: how many times per tick a machine may call this before further calls are delayed to the
     * next tick. 0 means no limit beyond the CPU's call budget.
     */
    int limit() default 0;

    /** Short documentation shown by {@code component.doc}. */
    String doc() default "";
}
