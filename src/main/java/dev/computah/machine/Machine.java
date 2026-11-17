package dev.computah.machine;

import dev.computah.Computah;
import dev.computah.api.Arguments;
import dev.computah.api.Callback;
import dev.computah.api.Component;
import dev.computah.api.ComponentException;
import dev.computah.api.Context;
import dev.computah.api.LimitReachedException;
import dev.computah.component.CallbackRegistry;
import dev.computah.component.FilesystemComponent;
import dev.computah.fs.RamFileSystem;
import org.jetbrains.annotations.Nullable;
import org.squiddev.cobalt.*;
import org.squiddev.cobalt.compiler.CompileException;
import org.squiddev.cobalt.function.LuaClosure;
import org.squiddev.cobalt.compiler.LoadState;
import org.squiddev.cobalt.function.LibFunction;
import org.squiddev.cobalt.interrupt.InterruptAction;
import org.squiddev.cobalt.lib.Bit32Lib;
import org.squiddev.cobalt.lib.CoreLibraries;
import org.squiddev.cobalt.lib.DebugLib;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One running computer: a Cobalt Lua state, its signal queue and the components it can see.
 * <p>
 * Lua runs on {@link MachineExecutor} threads, never on the server thread. The server calls {@link #update()} once per
 * tick, which resumes the machine when a signal arrives, a pull times out or an indirect call has been performed.
 * The machine itself is also a component of type {@code computer}.
 */
public final class Machine extends Component {
    public enum State {STOPPED, RUNNING, CRASHED}

    public static final int MAX_SIGNALS = 256;
    private static final double TICKS_PER_SECOND = 20;
    /** How long one executor run may keep handling queued signals before giving the thread back. */
    private static final long SLICE_NANOS = 5_000_000L;

    private final MachineHost host;
    private final String address;
    private final Map<String, Component> external = new ConcurrentHashMap<>();
    private final Map<String, Component> components = new ConcurrentHashMap<>();
    private final ArrayDeque<Object[]> signals = new ArrayDeque<>();

    private volatile State state = State.STOPPED;
    private volatile @Nullable String crashMessage;
    private @Nullable MachineSpec spec;
    private @Nullable FilesystemComponent tmp;

    // Lua state, only touched by the executor thread while executing, or by the server thread while not.
    private @Nullable LuaState lua;
    private @Nullable LuaThread main;
    private final LuaTable sysMarker = new LuaTable();
    private final LuaTable syncMarker = new LuaTable();
    private long baselineMemory;
    private volatile long usedMemory;
    private long lastHeapCheckTick = -1000;

    // Scheduling.
    private volatile boolean executing;
    private volatile long resumeStartNanos;
    private volatile boolean softAborted;
    private volatile boolean killRequested;
    private volatile long ticks;
    private boolean waitingPull;
    private double pullDeadline;
    private boolean waitingSync;
    private @Nullable PendingCall pendingCall;
    private Varargs resumeArgs = Constants.NONE;
    private volatile boolean shutdownRequested;
    private volatile boolean rebootRequested;
    private double callBudget;
    private final Map<CallbackRegistry.Entry, Integer> limitedCalls = new HashMap<>();

    private record PendingCall(Component component, CallbackRegistry.Entry entry, Arguments args) {
    }

    public Machine(MachineHost host, String address) {
        this.host = host;
        this.address = address;
    }

    public Machine(MachineHost host) {
        this(host, UUID.randomUUID().toString());
    }

    // region Component

    @Override
    public String address() {
        return address;
    }

    @Override
    public String type() {
        return "computer";
    }

    @Callback(doc = "function():boolean -- Whether the computer is running.")
    public Object[] isRunning(Context ctx, Arguments args) {
        return new Object[]{state == State.RUNNING};
    }

    @Callback(doc = "function([frequency:number[, duration:number]]) -- Plays a tone.")
    public Object[] beep(Context ctx, Arguments args) throws ComponentException {
        double freq = Math.max(20, Math.min(2000, args.optDouble(0, 440)));
        double duration = Math.max(0.05, Math.min(5, args.optDouble(1, 0.1)));
        host.beep(freq, duration);
        return new Object[0];
    }

    @Callback(direct = false, doc = "function():boolean -- Stops the computer (when called on another computer).")
    public Object[] stop(Context ctx, Arguments args) {
        if (ctx.machine() == this) {
            shutdownRequested = true;
            return new Object[]{true};
        }
        stop();
        return new Object[]{true};
    }

    @Callback(direct = false, doc = "function():boolean -- Starts this computer.")
    public Object[] start(Context ctx, Arguments args) {
        return new Object[]{start()};
    }

    // endregion

    public MachineHost host() {
        return host;
    }

    public State state() {
        return state;
    }

    public boolean isOn() {
        return state == State.RUNNING;
    }

    public @Nullable String crashMessage() {
        return crashMessage;
    }

    public double uptime() {
        return ticks / TICKS_PER_SECOND;
    }

    public long usedMemory() {
        return usedMemory;
    }

    public long totalMemory() {
        return spec == null ? 0 : spec.memory();
    }

    public @Nullable MachineSpec spec() {
        return spec;
    }

    public @Nullable String tmpAddress() {
        FilesystemComponent t = tmp;
        return t == null ? null : t.address();
    }

    public @Nullable Component component(String address) {
        return components.get(address);
    }

    public Collection<Component> components() {
        return Collections.unmodifiableCollection(components.values());
    }

    // region Lifecycle (server thread)

    /**
     * Sets the components this machine can see (its parts, the network and linked blocks). Call this from the server
     * thread whenever they change; a running machine gets component_added/component_removed signals.
     */
    public void setComponents(Collection<? extends Component> list) {
        Map<String, Component> next = new LinkedHashMap<>();
        for (Component c : list) if (c != null && c != this) next.put(c.address(), c);
        for (Iterator<Map.Entry<String, Component>> it = external.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Component> e = it.next();
            if (next.get(e.getKey()) != e.getValue()) {
                it.remove();
                removeVisible(e.getValue());
            }
        }
        for (Component c : next.values()) {
            if (external.putIfAbsent(c.address(), c) == null) addVisible(c);
        }
        if (state == State.RUNNING && spec != null && components.size() > spec.maxComponents() + 1) {
            crash("too many components");
        }
    }

    private void addVisible(Component c) {
        if (state != State.RUNNING) return;
        if (components.putIfAbsent(c.address(), c) == null) {
            c.attach(this);
            signal("component_added", c.address(), c.type());
        }
    }

    private void removeVisible(Component c) {
        if (state != State.RUNNING) return;
        if (components.remove(c.address(), c)) {
            c.detach(this);
            signal("component_removed", c.address(), c.type());
        }
    }

    /** Starts the machine. Returns false if it is already running or the hardware can't boot. */
    public synchronized boolean start() {
        if (state == State.RUNNING || executing) return false;
        MachineSpec s = host.spec();
        if (s == null) {
            setCrashed(host.bootProblem());
            return false;
        }
        if (external.size() > s.maxComponents()) {
            setCrashed("too many components");
            return false;
        }
        if (!host.consumeEnergy(0)) {
            setCrashed("not enough energy");
            return false;
        }
        spec = s;
        crashMessage = null;
        ticks = 0;
        signals.clear();
        waitingPull = false;
        waitingSync = false;
        pendingCall = null;
        shutdownRequested = false;
        rebootRequested = false;
        killRequested = false;
        softAborted = false;
        pendingCrash = null;
        usedMemory = 0;
        lastHeapCheckTick = -1000;
        components.clear();
        components.put(address, this);
        attach(this);
        if (s.tmpSize() > 0) {
            tmp = new FilesystemComponent(new RamFileSystem(s.tmpSize()), "tmpfs");
            components.put(tmp.address(), tmp);
            tmp.attach(this);
        }
        for (Component c : external.values()) {
            components.put(c.address(), c);
            c.attach(this);
        }
        state = State.RUNNING;
        callBudget = s.callBudget();
        resumeArgs = null; // first run creates the state
        submit();
        host.onStateChanged(this);
        return true;
    }

    /** Stops the machine without an error. */
    public void stop() {
        stopWith(null);
    }

    /** Stops the machine with an error message. */
    public void crash(String message) {
        stopWith(message == null ? "unknown error" : message);
    }

    private synchronized void stopWith(@Nullable String message) {
        if (state != State.RUNNING) return;
        if (executing) {
            killRequested = true;
            LuaState l = lua;
            if (l != null) l.interrupt();
        }
        if (message != null) Computah.LOG.debug("{} crashed: {}", host.describe(), message);
        teardown(message);
    }

    private void teardown(@Nullable String message) {
        state = message == null ? State.STOPPED : State.CRASHED;
        crashMessage = message;
        if (!executing) {
            lua = null;
            main = null;
        }
        for (Component c : components.values()) c.detach(this);
        components.clear();
        if (tmp != null) {
            tmp.close();
            tmp = null;
        }
        synchronized (signals) {
            signals.clear();
        }
        waitingPull = false;
        waitingSync = false;
        pendingCall = null;
        host.onStateChanged(this);
    }

    private void setCrashed(String message) {
        state = State.CRASHED;
        crashMessage = message;
        host.onStateChanged(this);
    }

    /** Runs one server tick: energy, timers, indirect calls and resuming. */
    public void update() {
        if (state != State.RUNNING) return;
        String c = pendingCrash;
        if (c != null && !executing) {
            pendingCrash = null;
            crash(c);
            return;
        }
        ticks++;
        synchronized (this) {
            callBudget = spec == null ? 1 : spec.callBudget();
            limitedCalls.clear();
        }
        if (!host.consumeEnergy(1)) {
            crash("not enough energy");
            return;
        }
        if (executing) return;
        if (shutdownRequested) {
            boolean reboot = rebootRequested;
            stop();
            if (reboot) start();
            return;
        }
        synchronized (this) {
            if (state != State.RUNNING || executing) return;
            if (waitingSync) {
                PendingCall call = pendingCall;
                pendingCall = null;
                waitingSync = false;
                resumeArgs = call == null ? ValueFactory.varargsOf(Constants.FALSE, LuaConverter.str("no pending call"))
                        : callNow(call.component(), call.entry(), call.args(), true);
                submit();
                return;
            }
            if (waitingPull) {
                Object[] sig = pollSignal();
                if (sig != null) {
                    waitingPull = false;
                    resumeArgs = LuaConverter.toVarargs(sig);
                    submit();
                } else if (uptime() + 1e-9 >= pullDeadline) {
                    waitingPull = false;
                    resumeArgs = Constants.NONE;
                    submit();
                }
            }
        }
    }

    // endregion

    // region Signals

    /** Queues a signal. Safe from any thread. Signals are dropped when the queue is full or the machine is off. */
    public boolean signal(String name, Object... args) {
        if (state != State.RUNNING) return false;
        Object[] sig = new Object[args.length + 1];
        sig[0] = name;
        System.arraycopy(args, 0, sig, 1, args.length);
        synchronized (signals) {
            if (signals.size() >= MAX_SIGNALS) return false;
            signals.add(sig);
        }
        return true;
    }

    private @Nullable Object[] pollSignal() {
        synchronized (signals) {
            return signals.poll();
        }
    }

    // endregion

    // region Executor

    private void submit() {
        executing = true;
        MachineExecutor.submit(this, this::run);
    }

    void checkTimeout() {
        LuaState l = lua;
        if (l == null || !executing) return;
        if (killRequested || (resumeStartNanos != 0 && System.nanoTime() - resumeStartNanos > timeoutNanos())) {
            l.interrupt();
        }
    }

    private long timeoutNanos() {
        MachineSpec s = spec;
        return (long) ((s == null ? 5 : s.timeoutSeconds()) * 1e9);
    }

    private InterruptAction onInterrupt() throws LuaError {
        if (killRequested) throw new HardAbort("killed");
        long start = resumeStartNanos;
        if (start == 0) return InterruptAction.CONTINUE;
        long elapsed = System.nanoTime() - start;
        if (elapsed > timeoutNanos()) {
            if (!softAborted) {
                softAborted = true;
                throw new LuaError("too long without yielding");
            }
            if (elapsed > timeoutNanos() + 2_000_000_000L) throw new HardAbort("too long without yielding");
        }
        return InterruptAction.CONTINUE;
    }

    private void run() {
        String crash = null;
        boolean halted = false;
        try {
            if (killRequested) return;
            Varargs args = resumeArgs;
            if (args == null) {
                args = boot();
            }
            long sliceStart = System.nanoTime();
            while (true) {
                if (killRequested) return;
                softAborted = false;
                resumeStartNanos = System.nanoTime();
                Varargs r;
                try {
                    r = LuaThread.run(main, args);
                } finally {
                    resumeStartNanos = 0;
                }
                if (main.getStatus() == LuaThread.Status.DEAD) {
                    halted = true;
                    break;
                }
                checkHeap();
                if (r.first() != sysMarker) {
                    // A bare coroutine.yield at the top level: treat it as pullSignal() without a timeout.
                    if (handlePull(Double.POSITIVE_INFINITY, sliceStart)) {
                        args = nextSignalArgs();
                        continue;
                    }
                    break;
                }
                String kind = r.arg(2).toString();
                if (kind.equals("pull")) {
                    double timeout = r.arg(3).isNil() ? Double.POSITIVE_INFINITY : r.arg(3).toDouble();
                    if (handlePull(timeout, sliceStart)) {
                        args = nextSignalArgs();
                        continue;
                    }
                    break;
                } else if (kind.equals("sync")) {
                    synchronized (this) {
                        waitingSync = true;
                    }
                    break;
                } else if (kind.equals("shutdown")) {
                    rebootRequested = r.arg(3).toBoolean();
                    shutdownRequested = true;
                    break;
                } else {
                    crash = "unknown system yield: " + kind;
                    break;
                }
            }
        } catch (LuaError e) {
            crash = e.getMessage() == null ? "error" : e.getMessage();
        } catch (HardAbort e) {
            crash = killRequested ? null : e.getMessage();
        } catch (OutOfMemoryError e) {
            crash = "not enough memory";
        } catch (Throwable e) {
            Computah.LOG.error("Error running {}", host.describe(), e);
            crash = "internal error: " + e;
        } finally {
            executing = false;
        }
        if (killRequested) {
            synchronized (this) {
                lua = null;
                main = null;
            }
            return;
        }
        if (halted) crash = crash == null ? "computer halted" : crash;
        if (crash != null) {
            final String msg = crash;
            // State changes happen on the server thread; the next update() sees this flag.
            pendingCrash = msg;
        }
    }

    private volatile @Nullable String pendingCrash;

    /** Returns true if a signal is ready and the slice has time left, so the caller should resume right away. */
    private boolean handlePull(double timeout, long sliceStart) {
        synchronized (this) {
            boolean hasSignal;
            synchronized (signals) {
                hasSignal = !signals.isEmpty();
            }
            if (hasSignal && System.nanoTime() - sliceStart < SLICE_NANOS && !shutdownRequested) return true;
            waitingPull = true;
            pullDeadline = uptime() + Math.max(0, timeout);
            return false;
        }
    }

    private Varargs nextSignalArgs() {
        Object[] sig = pollSignal();
        return sig == null ? Constants.NONE : LuaConverter.toVarargs(sig);
    }

    private void checkHeap() throws LuaError {
        long now = ticks;
        if (now - lastHeapCheckTick < 10) return;
        lastHeapCheckTick = now;
        long limit = totalMemory();
        long used = Math.max(0, HeapEstimator.estimate(limit + baselineMemory + 1, lua.globals(), main) - baselineMemory);
        usedMemory = used;
        if (used > limit) throw new LuaError("not enough memory");
    }

    private Varargs boot() throws LuaError {
        LuaState state = LuaState.builder().interruptHandler(this::onInterrupt).build();
        CoreLibraries.standardGlobals(state);
        Bit32Lib.add(state);
        DebugLib.add(state);
        LuaTable natives = MachineApis.create(this, state);
        LuaClosure chunk;
        try (InputStream in = Machine.class.getResourceAsStream("/data/computah/lua/machine.lua")) {
            if (in == null) throw new LuaError("machine.lua is missing");
            chunk = LoadState.load(state, in, "=machine", state.globals());
        } catch (CompileException e) {
            throw new LuaError("machine.lua: " + e.getMessage());
        } catch (IOException e) {
            throw new LuaError("machine.lua: " + e);
        }
        lua = state;
        main = new LuaThread(state, chunk);
        baselineMemory = HeapEstimator.estimate(Long.MAX_VALUE, state.globals());
        return ValueFactory.varargsOf(natives, sysMarker, syncMarker);
    }

    // endregion

    // region Component calls (machine thread)

    LuaTable syncMarker() {
        return syncMarker;
    }

    /** component.invoke from Lua: returns true, results... / false, error / the sync marker. */
    Varargs invoke(Varargs args) {
        String addr = LuaConverter.utf8(args.arg(1));
        String method = LuaConverter.utf8(args.arg(2));
        Component c = components.get(addr);
        if (c == null) return ValueFactory.varargsOf(Constants.FALSE, LuaConverter.str("no such component"));
        CallbackRegistry.Entry e = CallbackRegistry.of(c).get(method);
        if (e == null) return ValueFactory.varargsOf(Constants.FALSE, LuaConverter.str("no such method"));
        Arguments a = new Arguments(copy(args.subargs(3)));
        if (!e.direct() || !takeBudget(e)) {
            synchronized (this) {
                pendingCall = new PendingCall(c, e, a);
            }
            return syncMarker;
        }
        try {
            return callNow(c, e, a, false);
        } catch (LimitReachedException ex) {
            synchronized (this) {
                pendingCall = new PendingCall(c, e, a);
            }
            return syncMarker;
        }
    }

    private static Varargs copy(Varargs v) {
        LuaValue[] arr = new LuaValue[v.count()];
        for (int i = 0; i < arr.length; i++) arr[i] = v.arg(i + 1);
        return ValueFactory.varargsOf(arr);
    }

    private synchronized boolean takeBudget(CallbackRegistry.Entry e) {
        if (e.limit() <= 0) return true;
        int used = limitedCalls.getOrDefault(e, 0);
        double cost = 1.0 / e.limit();
        if (used >= Math.ceil(e.limit() * Math.max(0.5, spec == null ? 1 : spec.callBudget())) || callBudget < cost) return false;
        limitedCalls.put(e, used + 1);
        callBudget -= cost;
        return true;
    }

    private Varargs callNow(Component c, CallbackRegistry.Entry e, Arguments a, boolean synchronous) {
        try {
            if (!components.containsKey(c.address())) {
                return ValueFactory.varargsOf(Constants.FALSE, LuaConverter.str("no such component"));
            }
            Object[] result = e.invoke(c, new Context(this, synchronous), a);
            if (result == null || result.length == 0) return Constants.TRUE;
            LuaValue[] out = new LuaValue[result.length + 1];
            out[0] = Constants.TRUE;
            for (int i = 0; i < result.length; i++) out[i + 1] = LuaConverter.toLua(result[i]);
            return ValueFactory.varargsOf(out);
        } catch (LimitReachedException ex) {
            if (!synchronous) throw ex;
            return ValueFactory.varargsOf(Constants.FALSE, LuaConverter.str("limit reached"));
        } catch (ComponentException ex) {
            return ValueFactory.varargsOf(Constants.FALSE, LuaConverter.str(ex.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException | IndexOutOfBoundsException ex) {
            return ValueFactory.varargsOf(Constants.FALSE, LuaConverter.str(String.valueOf(ex.getMessage())));
        } catch (Throwable ex) {
            Computah.LOG.error("Error in {}.{}", c.type(), e.name(), ex);
            return ValueFactory.varargsOf(Constants.FALSE, LuaConverter.str("internal error"));
        }
    }

    // endregion
}
