package dev.computah.api;

/**
 * Thrown by a direct callback when a per-tick budget is used up. The machine then retries the call on the server
 * thread at the next tick (with {@link Context#synchronous()} true), so the program just waits a little.
 */
public final class LimitReachedException extends RuntimeException {
    public static final LimitReachedException INSTANCE = new LimitReachedException();

    private LimitReachedException() {
        super("limit reached", null, false, false);
    }
}
