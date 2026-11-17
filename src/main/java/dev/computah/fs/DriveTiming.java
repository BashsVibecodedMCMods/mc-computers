package dev.computah.fs;

import dev.computah.Config;
import dev.computah.api.Context;
import dev.computah.api.DelayException;

/**
 * Simulated drive speed, like OpenComputers: every operation on a floppy or hard drive takes time (a seek, plus the
 * transfer at the drive's speed; floppies also spin up after a pause). Costs are added up per drive, and once a tick
 * or more is owed the call is delayed with a {@link DelayException}, so the program waits but other computers and the
 * program's other threads keep running. Calls that already waited (synchronous ones) are never charged again.
 * All speeds come from the server config ({@code [drives]}).
 */
public final class DriveTiming {
    /** Seconds without use after which a floppy has to spin up again. */
    public static final double SPIN_DOWN_SECONDS = 3.0;

    public enum Medium { FLOPPY, HDD }

    private final Medium medium;
    private final int tier;
    private double debt;
    private double lastUse = Double.NEGATIVE_INFINITY;

    public DriveTiming(Medium medium, int tier) {
        this.medium = medium;
        this.tier = Math.max(1, Math.min(3, tier));
    }

    public static DriveTiming floppy() {
        return new DriveTiming(Medium.FLOPPY, 1);
    }

    public static DriveTiming hdd(int tier) {
        return new DriveTiming(Medium.HDD, tier);
    }

    public Medium medium() {
        return medium;
    }

    private double tierFactor() {
        return medium == Medium.HDD ? 1 << (tier - 1) : 1;
    }

    /** Seconds one operation takes before data moves. */
    public double seekSeconds() {
        return (medium == Medium.FLOPPY ? Config.get(Config.FLOPPY_SEEK) : Config.get(Config.HDD_SEEK)) / 1000.0;
    }

    /** Seconds to read this many bytes (without the seek). */
    public double readSeconds(long bytes) {
        double kibps = (medium == Medium.FLOPPY ? Config.get(Config.FLOPPY_READ) : Config.get(Config.HDD_READ)) * tierFactor();
        return bytes / 1024.0 / kibps;
    }

    /** Seconds to write this many bytes (without the seek). */
    public double writeSeconds(long bytes) {
        double kibps = (medium == Medium.FLOPPY ? Config.get(Config.FLOPPY_WRITE) : Config.get(Config.HDD_WRITE)) * tierFactor();
        return bytes / 1024.0 / kibps;
    }

    /** Charges a seek (open, list, exists, ...). */
    public void seek(Context ctx) {
        charge(ctx, seekSeconds());
    }

    /** Charges a seek plus reading {@code bytes}. */
    public void read(Context ctx, long bytes) {
        charge(ctx, seekSeconds() + readSeconds(bytes));
    }

    /** Charges a seek plus writing {@code bytes}. */
    public void write(Context ctx, long bytes) {
        charge(ctx, seekSeconds() + writeSeconds(bytes));
    }

    /**
     * Adds {@code seconds} to what the drive owes, and throws a {@link DelayException} once a tick or more is owed.
     * Does nothing for synchronous calls (they already waited) or when simulation is off.
     */
    public void charge(Context ctx, double seconds) {
        if (ctx.synchronous() || !Config.get(Config.DRIVE_SIMULATE)) return;
        double now = ctx.machine().uptime();
        int ticks;
        synchronized (this) {
            if (medium == Medium.FLOPPY && (now - lastUse > SPIN_DOWN_SECONDS || now < lastUse)) {
                seconds += Config.get(Config.FLOPPY_SPIN_UP) / 1000.0;
            }
            lastUse = now;
            debt += seconds / Config.get(Config.DRIVE_SPEED);
            ticks = (int) Math.floor(debt * 20);
            if (ticks < 1) return;
            debt -= ticks / 20.0;
            // The delay keeps the drive busy: the next use starts after it.
            lastUse = now + ticks / 20.0;
        }
        throw new DelayException(ticks);
    }
}
