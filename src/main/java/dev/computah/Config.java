package dev.computah;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server configuration (config/computah-server.toml in the world). */
public final class Config {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue MACHINE_THREADS;
    public static final ModConfigSpec.DoubleValue TIMEOUT;
    public static final ModConfigSpec.BooleanValue POWER_ENABLED;
    public static final ModConfigSpec.DoubleValue COMPUTER_COST;
    public static final ModConfigSpec.DoubleValue SERVER_COST;
    public static final ModConfigSpec.IntValue CASE_BUFFER;
    public static final ModConfigSpec.IntValue RACK_BUFFER;
    public static final ModConfigSpec.IntValue WIRELESS_RANGE;
    public static final ModConfigSpec.IntValue LINK_RANGE;
    public static final ModConfigSpec.IntValue MAX_LINKS;
    public static final ModConfigSpec.IntValue MAX_HOST_UPDATES_PER_TICK;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("machines");
        MACHINE_THREADS = b.comment("Threads that run Lua for all computers. Takes effect after a restart.")
                .defineInRange("threads", 2, 1, 16);
        TIMEOUT = b.comment("Seconds a computer may run without yielding before it gets a \"too long without yielding\" error.")
                .defineInRange("timeout", 5.0, 0.1, 60.0);
        MAX_HOST_UPDATES_PER_TICK = b.comment("How many computers may rescan their network per tick (the rest wait for the next tick).")
                .defineInRange("maxNetworkScansPerTick", 32, 1, 1024);
        b.pop();

        b.push("power");
        POWER_ENABLED = b.comment("Whether computers and racks need Forge Energy (FE) to run. Off by default.")
                .define("enabled", false);
        COMPUTER_COST = b.comment("FE per tick used by a running computer.").defineInRange("computerCost", 4.0, 0, 10000);
        SERVER_COST = b.comment("FE per tick used by each running rack server.").defineInRange("serverCost", 6.0, 0, 10000);
        CASE_BUFFER = b.comment("FE a computer case can store.").defineInRange("caseBuffer", 20000, 0, Integer.MAX_VALUE);
        RACK_BUFFER = b.comment("FE a rack can store.").defineInRange("rackBuffer", 80000, 0, Integer.MAX_VALUE);
        b.pop();

        b.push("network");
        WIRELESS_RANGE = b.comment("Maximum range in blocks of a tier 1 wireless card (tier 2 is unlimited and works across dimensions).")
                .defineInRange("wirelessRange", 400, 1, 100000);
        LINK_RANGE = b.comment("Range in blocks for wireless peripheral links through a tier 1 wireless card (tier 2 is unlimited). 0 = unlimited.")
                .defineInRange("linkRange", 64, 0, 100000);
        MAX_LINKS = b.comment("Maximum number of linked blocks per computer or rack.").defineInRange("maxLinks", 32, 1, 1024);
        b.pop();
        SPEC = b.build();
    }

    private Config() {
    }

    /** Reads a value, falling back to its default before the config has loaded (e.g. in unit tests). */
    public static <T> T get(ModConfigSpec.ConfigValue<T> value) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return value.getDefault();
        }
    }
}
