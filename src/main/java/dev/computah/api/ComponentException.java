package dev.computah.api;

/** An error reported to Lua as a normal Lua error with this message. */
public class ComponentException extends Exception {
    public ComponentException(String message) {
        super(message, null, false, false);
    }
}
