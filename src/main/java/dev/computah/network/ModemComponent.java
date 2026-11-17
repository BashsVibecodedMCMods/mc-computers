package dev.computah.network;

import dev.computah.Config;
import dev.computah.api.Arguments;
import dev.computah.api.Callback;
import dev.computah.api.Component;
import dev.computah.api.ComponentException;
import dev.computah.api.Context;
import dev.computah.machine.Machine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The {@code modem} component of network cards (wired, tier 0) and wireless cards (tier 1: limited range; tier 2:
 * unlimited and across dimensions). Messages arrive as {@code modem_message(receiver, sender, port, distance, ...)}.
 */
public class ModemComponent extends Component implements dev.computah.api.PersistentComponent {
    public static final int MAX_PACKET_SIZE = 8192;
    public static final int MAX_VALUES = 8;
    public static final int MAX_OPEN_PORTS = 128;

    /** Where the card is. */
    public interface Location {
        @Nullable
        Level level();

        BlockPos pos();
    }

    private final String address;
    private final int wirelessTier;
    private final Location location;
    private final Set<Integer> openPorts = ConcurrentHashMap.newKeySet();
    private volatile double strength;

    public ModemComponent(String address, int wirelessTier, Location location) {
        this.address = address;
        this.wirelessTier = wirelessTier;
        this.location = location;
        this.strength = wirelessTier == 1 ? Config.get(Config.WIRELESS_RANGE) : 0;
    }

    @Override
    public String address() {
        return address;
    }

    @Override
    public String type() {
        return "modem";
    }

    public boolean isWireless() {
        return wirelessTier > 0;
    }

    public int wirelessTier() {
        return wirelessTier;
    }

    public Location location() {
        return location;
    }

    public double strength() {
        return strength;
    }

    public boolean isOpen(int port) {
        return openPorts.contains(port);
    }

    @Override
    public void onConnect(Machine machine) {
        if (isWireless()) NetworkManager.registerWireless(this);
    }

    @Override
    public void onDisconnect(Machine machine) {
        if (machines().isEmpty()) {
            openPorts.clear();
            NetworkManager.unregisterWireless(this);
        }
    }

    private static int checkPort(Arguments args, int i) throws ComponentException {
        int port = args.checkInteger(i);
        if (port < 1 || port > 65535) throw new ComponentException("invalid port number");
        return port;
    }

    @Callback(doc = "function(port:number):boolean -- Opens a port (1-65535) to receive messages on it. Returns whether it was closed before.")
    public Object[] open(Context ctx, Arguments args) throws ComponentException {
        int port = checkPort(args, 0);
        if (openPorts.size() >= MAX_OPEN_PORTS && !openPorts.contains(port)) throw new ComponentException("too many open ports");
        return new Object[]{openPorts.add(port)};
    }

    @Callback(doc = "function([port:number]):boolean -- Closes a port, or all ports. Returns whether anything was closed.")
    public Object[] close(Context ctx, Arguments args) throws ComponentException {
        if (args.isNil(0)) {
            boolean any = !openPorts.isEmpty();
            openPorts.clear();
            return new Object[]{any};
        }
        return new Object[]{openPorts.remove(checkPort(args, 0))};
    }

    @Callback(doc = "function(port:number):boolean -- Whether a port is open.")
    public Object[] isOpen(Context ctx, Arguments args) throws ComponentException {
        return new Object[]{openPorts.contains(checkPort(args, 0))};
    }

    @Callback(doc = "function():boolean -- Whether this is a wireless card.")
    public Object[] isWireless(Context ctx, Arguments args) {
        return new Object[]{isWireless()};
    }

    @Callback(doc = "function():boolean -- Whether this card uses cables.")
    public Object[] isWired(Context ctx, Arguments args) {
        return new Object[]{!isWireless()};
    }

    @Callback(doc = "function():number -- The largest message, in bytes (each value costs its size plus 2).")
    public Object[] maxPacketSize(Context ctx, Arguments args) {
        return new Object[]{MAX_PACKET_SIZE};
    }

    @Callback(doc = "function():number -- Wireless range in blocks (tier 1).")
    public Object[] getStrength(Context ctx, Arguments args) {
        return new Object[]{wirelessTier == 2 ? Double.POSITIVE_INFINITY : strength};
    }

    @Callback(doc = "function(range:number):number -- Sets the wireless range (tier 1, up to the configured maximum). Returns the new range.")
    public Object[] setStrength(Context ctx, Arguments args) throws ComponentException {
        if (wirelessTier != 1) return new Object[]{wirelessTier == 2 ? Double.POSITIVE_INFINITY : 0};
        strength = Math.max(0, Math.min(Config.get(Config.WIRELESS_RANGE), args.checkDouble(0)));
        return new Object[]{strength};
    }

    private Object[] values(Arguments args, int from) throws ComponentException {
        int n = args.count() - from;
        if (n > MAX_VALUES) throw new ComponentException("packet has too many parts");
        Object[] out = new Object[Math.max(0, n)];
        int size = 0;
        for (int i = 0; i < n; i++) {
            Object v = args.get(from + i);
            if (args.isString(from + i)) {
                byte[] b = args.checkBytes(from + i);
                size += b.length + 2;
                out[i] = b;
            } else if (v == null || v instanceof Boolean || v instanceof Number) {
                size += 8 + 2;
                out[i] = v;
            } else {
                throw new ComponentException("unsupported data type");
            }
        }
        if (size > MAX_PACKET_SIZE) throw new ComponentException("packet too big (max " + MAX_PACKET_SIZE + ")");
        return out;
    }

    @Callback(doc = "function(address:string, port:number, ...):boolean -- Sends a message to one card. Values may be nil, booleans, numbers or strings.")
    public Object[] send(Context ctx, Arguments args) throws ComponentException {
        String target = args.checkString(0);
        int port = checkPort(args, 1);
        NetworkManager.send(new NetworkManager.Packet(this, target, port, values(args, 2)));
        return new Object[]{true};
    }

    @Callback(doc = "function(port:number, ...):boolean -- Sends a message to every reachable card with the port open.")
    public Object[] broadcast(Context ctx, Arguments args) throws ComponentException {
        int port = checkPort(args, 0);
        NetworkManager.send(new NetworkManager.Packet(this, null, port, values(args, 1)));
        return new Object[]{true};
    }

    @Override
    public net.minecraft.nbt.CompoundTag saveState(Machine machine) {
        net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
        t.putIntArray("ports", openPorts.stream().mapToInt(Integer::intValue).toArray());
        t.putDouble("strength", strength);
        return t;
    }

    @Override
    public void loadState(Machine machine, net.minecraft.nbt.CompoundTag t) {
        for (int p : t.getIntArray("ports")) openPorts.add(p);
        if (t.contains("strength") && wirelessTier == 1) strength = Math.min(Config.get(Config.WIRELESS_RANGE), t.getDouble("strength"));
    }

    /** Delivers a message (server thread). */
    public void receive(ModemComponent sender, int port, double distance, Object[] values) {
        if (!openPorts.contains(port) || sender == this) return;
        Object[] sig = new Object[4 + values.length];
        sig[0] = address;
        sig[1] = sender.address();
        sig[2] = port;
        sig[3] = distance;
        System.arraycopy(values, 0, sig, 4, values.length);
        signalAll("modem_message", sig);
    }
}
