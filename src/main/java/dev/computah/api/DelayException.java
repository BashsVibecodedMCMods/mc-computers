package dev.computah.api;

/**
 * Thrown by a direct callback when the operation has to take time (for example a slow drive). The machine waits
 * {@link #ticks()} ticks without blocking other computers, then performs the call on the server thread with
 * {@link Context#synchronous()} true. Callbacks must never delay a synchronous call.
 */
public final class DelayException extends RuntimeException {
    private final int ticks;

    public DelayException(int ticks) {
        super("delayed", null, false, false);
        this.ticks = Math.max(1, ticks);
    }

    public int ticks() {
        return ticks;
    }
}
