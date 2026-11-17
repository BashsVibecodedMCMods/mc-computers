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
    public static final ModConfigSpec.BooleanValue DRIVE_SIMULATE;
    public static final ModConfigSpec.DoubleValue DRIVE_SPEED;
    public static final ModConfigSpec.DoubleValue FLOPPY_READ;
    public static final ModConfigSpec.DoubleValue FLOPPY_WRITE;
    public static final ModConfigSpec.IntValue FLOPPY_SEEK;
    public static final ModConfigSpec.IntValue FLOPPY_SPIN_UP;
    public static final ModConfigSpec.DoubleValue HDD_READ;
    public static final ModConfigSpec.DoubleValue HDD_WRITE;
    public static final ModConfigSpec.IntValue HDD_SEEK;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends Integer>> RAM_KIB;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends Integer>> HDD_KIB;
    public static final ModConfigSpec.IntValue FLOPPY_KIB;
    public static final ModConfigSpec.IntValue TMPFS_KIB;

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

        b.push("drives");
        DRIVE_SIMULATE = b.comment("Whether hard drives and floppies take time to read and write (like OpenComputers). Off = instant.")
                .define("simulateSpeed", true);
        DRIVE_SPEED = b.comment("Multiplies every drive speed below (2 = twice as fast).")
                .defineInRange("speedMultiplier", 1.0, 0.01, 1000.0);
        FLOPPY_READ = b.comment("Floppy read speed in KiB per second.").defineInRange("floppyReadKiBps", 24.0, 0.1, 1_000_000.0);
        FLOPPY_WRITE = b.comment("Floppy write speed in KiB per second.").defineInRange("floppyWriteKiBps", 12.0, 0.1, 1_000_000.0);
        FLOPPY_SEEK = b.comment("Milliseconds every floppy operation (open, list, read, ...) takes before transferring data.")
                .defineInRange("floppySeekMillis", 25, 0, 10_000);
        FLOPPY_SPIN_UP = b.comment("Milliseconds a floppy takes to spin up after 3 seconds without use.")
                .defineInRange("floppySpinUpMillis", 750, 0, 10_000);
        HDD_READ = b.comment("Tier 1 hard drive read speed in KiB per second (tier 2: twice that, tier 3: four times).")
                .defineInRange("hddReadKiBps", 64.0, 0.1, 1_000_000.0);
        HDD_WRITE = b.comment("Tier 1 hard drive write speed in KiB per second (tier 2: twice that, tier 3: four times).")
                .defineInRange("hddWriteKiBps", 48.0, 0.1, 1_000_000.0);
        HDD_SEEK = b.comment("Milliseconds every hard drive operation takes before transferring data.")
                .defineInRange("hddSeekMillis", 5, 0, 10_000);
        b.pop();

        b.push("parts");
        RAM_KIB = b.comment("Memory of the four RAM tiers in KiB.")
                .defineList("ramKiB", java.util.List.of(512, 1024, 2048, 4096), () -> 1024, o -> o instanceof Integer i && i >= 64 && i <= 1 << 20);
        HDD_KIB = b.comment("Capacity of the three hard drive tiers in KiB.")
                .defineList("hddKiB", java.util.List.of(1024, 2048, 4096), () -> 1024, o -> o instanceof Integer i && i >= 16 && i <= 1 << 22);
        FLOPPY_KIB = b.comment("Floppy capacity in KiB.").defineInRange("floppyKiB", 512, 16, 1 << 22);
        TMPFS_KIB = b.comment("Size of every computer's temporary filesystem in KiB.").defineInRange("tmpfsKiB", 64, 4, 1 << 20);
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
