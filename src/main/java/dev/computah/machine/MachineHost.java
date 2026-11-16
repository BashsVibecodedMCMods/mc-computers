package dev.computah.machine;

import org.jetbrains.annotations.Nullable;

/** The block (case or rack server) a machine lives in. All methods are called on the server thread. */
public interface MachineHost {
    /**
     * Checks that the hardware can boot and returns its limits.
     *
     * @return The limits, or null with {@link #bootProblem()} explaining why it can't start.
     */
    @Nullable
    MachineSpec spec();

    /** Why {@link #spec()} returned null, e.g. "no CPU". */
    default String bootProblem() {
        return "missing hardware";
    }

    /** Takes energy for one tick of running. Return false to stop the machine for lack of power. */
    default boolean consumeEnergy(double amount) {
        return true;
    }

    default double energy() {
        return Double.POSITIVE_INFINITY;
    }

    default double maxEnergy() {
        return Double.POSITIVE_INFINITY;
    }

    /** Called when the machine starts, stops or crashes. */
    default void onStateChanged(Machine machine) {
    }

    default void beep(double frequency, double duration) {
    }

    /** A short description for logs. */
    default String describe() {
        return "machine";
    }
}
