package dev.computah.machine;

import dev.computah.api.Arguments;
import dev.computah.api.Callback;
import dev.computah.api.Component;
import dev.computah.api.Context;
import dev.computah.component.EepromComponent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Helpers to run a machine headless in unit tests. */
public final class TestMachines {
    private TestMachines() {
    }

    /** A component that records what Lua reports to it. */
    public static final class Sink extends Component {
        public final List<Object> values = new CopyOnWriteArrayList<>();
        public volatile boolean done;
        private final String address = UUID.randomUUID().toString();

        @Override
        public String address() {
            return address;
        }

        @Override
        public String type() {
            return "sink";
        }

        @Callback
        public Object[] put(Context ctx, Arguments args) {
            for (int i = 0; i < args.count(); i++) values.add(args.get(i));
            return new Object[0];
        }

        @Callback
        public Object[] finish(Context ctx, Arguments args) {
            done = true;
            return new Object[0];
        }

        @Callback(direct = false)
        public Object[] indirect(Context ctx, Arguments args) {
            return new Object[]{"server:" + Thread.currentThread().getName().startsWith("Computah-Machine")};
        }

        /** Takes 10 ticks (like a slow drive): delays itself unless the call already waited. */
        @Callback
        public Object[] slow(Context ctx, Arguments args) {
            if (!ctx.synchronous()) throw new dev.computah.api.DelayException(10);
            return new Object[]{"done"};
        }

        @Callback(limit = 2)
        public Object[] limited(Context ctx, Arguments args) {
            return new Object[]{true};
        }
    }

    public static class Host implements MachineHost {
        public MachineSpec spec = new MachineSpec(2 * 1024 * 1024, 32, 1, 1, 64 * 1024);
        public final List<String> states = new ArrayList<>();

        @Override
        public MachineSpec spec() {
            return spec;
        }

        @Override
        public void onStateChanged(Machine machine) {
            states.add(machine.state() + ":" + machine.crashMessage());
        }
    }

    public static EepromComponent eeprom(String code) {
        return new EepromComponent(UUID.randomUUID().toString(),
                new EepromComponent.Contents(code.getBytes(StandardCharsets.UTF_8), new byte[0], "test", false), c -> {
        });
    }

    /** Ticks the machine (sleeping 5 ms per tick) until the predicate holds or the machine stops. */
    public static void run(Machine m, java.util.function.BooleanSupplier until, int maxTicks) throws InterruptedException {
        for (int i = 0; i < maxTicks; i++) {
            if (until.getAsBoolean()) return;
            if (m.state() != Machine.State.RUNNING && i > 0) return;
            m.update();
            Thread.sleep(5);
        }
    }

    public static String sinkLua(Sink sink) {
        return "local sink = component.proxy(component.list('sink')())\n";
    }
}
