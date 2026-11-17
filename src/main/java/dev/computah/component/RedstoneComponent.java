package dev.computah.component;

import dev.computah.api.Arguments;
import dev.computah.api.Callback;
import dev.computah.api.Component;
import dev.computah.api.ComponentException;
import dev.computah.api.Context;
import net.minecraft.core.Direction;

import java.util.HashMap;
import java.util.Map;

/**
 * The {@code redstone} component, from a redstone card (using the sides of the case or rack) or a redstone I/O block.
 * Sides are numbered like Minecraft's directions: 0 down, 1 up, 2 north, 3 south, 4 west, 5 east. Input changes send
 * {@code redstone_changed(address, side, oldValue, newValue)}.
 */
public class RedstoneComponent extends Component implements dev.computah.api.PersistentComponent {
    /** The block this component reads and drives. */
    public interface Host {
        /** Current input on a side (server thread). */
        int input(Direction side);

        /** Called on the server thread after outputs changed. */
        void outputsChanged();
    }

    private final String address;
    private final Host host;
    private final int[] outputs = new int[6];
    private final int[] lastInputs = new int[6];

    public RedstoneComponent(String address, Host host) {
        this.address = address;
        this.host = host;
    }

    @Override
    public String address() {
        return address;
    }

    @Override
    public String type() {
        return "redstone";
    }

    public synchronized int output(Direction side) {
        return outputs[side.ordinal()];
    }

    public synchronized void resetOutputs() {
        java.util.Arrays.fill(outputs, 0);
    }

    /** Re-reads inputs and signals changes (server thread). */
    public void updateInputs() {
        for (Direction d : Direction.values()) {
            int now = host.input(d);
            int old;
            synchronized (this) {
                old = lastInputs[d.ordinal()];
                lastInputs[d.ordinal()] = now;
            }
            if (old != now) signalAll("redstone_changed", address, d.ordinal(), old, now);
        }
    }

    @Override
    public synchronized net.minecraft.nbt.CompoundTag saveState(dev.computah.machine.Machine machine) {
        net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
        t.putIntArray("outputs", outputs.clone());
        t.putIntArray("inputs", lastInputs.clone());
        return t;
    }

    @Override
    public void loadState(dev.computah.machine.Machine machine, net.minecraft.nbt.CompoundTag t) {
        synchronized (this) {
            int[] o = t.getIntArray("outputs"), in = t.getIntArray("inputs");
            if (o.length == 6) System.arraycopy(o, 0, outputs, 0, 6);
            if (in.length == 6) System.arraycopy(in, 0, lastInputs, 0, 6);
        }
        host.outputsChanged();
    }

    private static Direction side(Arguments args, int i) throws ComponentException {
        int s = args.checkInteger(i);
        if (s < 0 || s > 5) throw new ComponentException("invalid side");
        return Direction.from3DDataValue(s);
    }

    @Callback(direct = false, doc = "function([side:number]):number|table -- The redstone input on a side, or a table of all sides.")
    public Object[] getInput(Context ctx, Arguments args) throws ComponentException {
        if (args.isNil(0)) {
            Map<Integer, Integer> all = new HashMap<>();
            for (Direction d : Direction.values()) all.put(d.ordinal(), host.input(d));
            return new Object[]{all};
        }
        return new Object[]{host.input(side(args, 0))};
    }

    @Callback(doc = "function([side:number]):number|table -- The redstone output on a side, or a table of all sides.")
    public synchronized Object[] getOutput(Context ctx, Arguments args) throws ComponentException {
        if (args.isNil(0)) {
            Map<Integer, Integer> all = new HashMap<>();
            for (Direction d : Direction.values()) all.put(d.ordinal(), outputs[d.ordinal()]);
            return new Object[]{all};
        }
        return new Object[]{outputs[side(args, 0).ordinal()]};
    }

    @Callback(direct = false, doc = "function(side:number|table, [value:number]):number -- Sets the output (0-15) on a side, or several sides from a table. Returns the old value.")
    public Object[] setOutput(Context ctx, Arguments args) throws ComponentException {
        Object old;
        if (args.isTable(0)) {
            Map<Object, Object> t = args.checkTable(0);
            synchronized (this) {
                Map<Integer, Integer> prev = new HashMap<>();
                for (Map.Entry<Object, Object> e : t.entrySet()) {
                    if (!(e.getKey() instanceof Number k) || !(e.getValue() instanceof Number v)) continue;
                    int s = k.intValue();
                    if (s < 0 || s > 5) continue;
                    prev.put(s, outputs[s]);
                    outputs[s] = Math.max(0, Math.min(15, v.intValue()));
                }
                old = prev;
            }
        } else {
            Direction d = side(args, 0);
            int v = Math.max(0, Math.min(15, args.checkInteger(1)));
            synchronized (this) {
                old = outputs[d.ordinal()];
                outputs[d.ordinal()] = v;
            }
        }
        host.outputsChanged();
        return new Object[]{old};
    }
}
