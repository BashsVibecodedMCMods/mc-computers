package dev.computah.api;

import dev.computah.machine.Machine;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Something a machine can see in {@code component.list()} and call through {@code component.invoke}. Methods are
 * found by reflection on {@link Callback} annotations. Implementations must be thread-safe: direct callbacks run on
 * machine threads while the server thread may change the component.
 */
public abstract class Component {
    private final Set<Machine> machines = new CopyOnWriteArraySet<>();
    private volatile int slot = -1;

    public abstract String address();

    public abstract String type();

    /** The host slot this component is installed in, or -1 when it is an external block. */
    public int slot() {
        return slot;
    }

    public void setSlot(int slot) {
        this.slot = slot;
    }

    /** Machines that currently see this component. */
    public final Set<Machine> machines() {
        return machines;
    }

    /** Sends a signal to every machine that sees this component. */
    public final void signalAll(String name, Object... args) {
        for (Machine m : machines) m.signal(name, args);
    }

    /** Called (on the server thread) when a machine starts seeing this component. */
    public void onConnect(Machine machine) {
    }

    /** Called (on the server thread) when a machine stops seeing this component. */
    public void onDisconnect(Machine machine) {
    }

    public final void attach(Machine machine) {
        if (machines.add(machine)) onConnect(machine);
    }

    public final void detach(Machine machine) {
        if (machines.remove(machine)) onDisconnect(machine);
    }
}
