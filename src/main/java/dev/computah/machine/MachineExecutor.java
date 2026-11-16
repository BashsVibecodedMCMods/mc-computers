package dev.computah.machine;

import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** The threads that run Lua, plus a watchdog that interrupts machines which run too long. */
public final class MachineExecutor {
    private static volatile ExecutorService pool;
    private static volatile ScheduledExecutorService watchdog;
    private static final Set<Machine> RUNNING = ConcurrentHashMap.newKeySet();
    private static volatile int threads = 2;

    private MachineExecutor() {
    }

    public static void setThreads(int count) {
        threads = Math.max(1, count);
    }

    private static synchronized ExecutorService pool() {
        if (pool == null) {
            AtomicInteger n = new AtomicInteger();
            pool = Executors.newFixedThreadPool(threads, r -> {
                Thread t = new Thread(r, "Computah-Machine-" + n.incrementAndGet());
                t.setDaemon(true);
                return t;
            });
            watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "Computah-Watchdog");
                t.setDaemon(true);
                return t;
            });
            watchdog.scheduleAtFixedRate(() -> {
                for (Machine m : RUNNING) m.checkTimeout();
            }, 50, 50, TimeUnit.MILLISECONDS);
        }
        return pool;
    }

    static void submit(Machine machine, Runnable task) {
        pool().execute(() -> {
            RUNNING.add(machine);
            try {
                task.run();
            } finally {
                RUNNING.remove(machine);
            }
        });
    }

    /** Stops all threads, e.g. when the server stops. Machines must already be stopped. */
    public static synchronized void shutdown() {
        if (pool != null) {
            pool.shutdownNow();
            watchdog.shutdownNow();
            pool = null;
            watchdog = null;
        }
    }
}
