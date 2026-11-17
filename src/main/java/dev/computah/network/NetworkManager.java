package dev.computah.network;

import dev.computah.Config;
import dev.computah.api.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Wired network discovery and message delivery. All world access happens on the server thread.
 * <p>
 * Each level has an epoch that increases whenever a network block is placed, removed, loaded or unloaded; hosts
 * rescan when it changes (a limited number per tick). Links have their own global epoch.
 */
public final class NetworkManager {
    private static final int MAX_NODES = 4096;
    private static final Map<ResourceKey<Level>, AtomicLong> EPOCHS = new ConcurrentHashMap<>();
    private static final AtomicLong LINK_EPOCH = new AtomicLong();
    private static final Set<ModemComponent> WIRELESS = ConcurrentHashMap.newKeySet();
    private static final ConcurrentLinkedQueue<Packet> PACKETS = new ConcurrentLinkedQueue<>();
    private static final Map<ResourceKey<Level>, Map<BlockPos, List<BlockPos>>> SEGMENTS = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Long> SEGMENT_EPOCH = new ConcurrentHashMap<>();
    private static int scansLeft;

    public record Packet(ModemComponent sender, @Nullable String target, int port, Object[] values) {
    }

    private NetworkManager() {
    }

    // region Epochs

    public static long epoch(Level level) {
        return EPOCHS.computeIfAbsent(level.dimension(), k -> new AtomicLong()).get();
    }

    public static void bump(@Nullable Level level) {
        if (level == null || level.isClientSide) return;
        EPOCHS.computeIfAbsent(level.dimension(), k -> new AtomicLong()).incrementAndGet();
    }

    public static long linkEpoch() {
        return LINK_EPOCH.get();
    }

    public static void bumpLinks() {
        LINK_EPOCH.incrementAndGet();
    }

    /** Called at the start of each server tick. */
    public static void startTick() {
        scansLeft = Config.get(Config.MAX_HOST_UPDATES_PER_TICK);
    }

    /** Takes one network scan from this tick's budget. */
    public static boolean tryScan() {
        if (scansLeft <= 0) return false;
        scansLeft--;
        return true;
    }

    public static void reset() {
        EPOCHS.clear();
        WIRELESS.clear();
        PACKETS.clear();
        SEGMENTS.clear();
        SEGMENT_EPOCH.clear();
    }

    // endregion

    // region Discovery

    private interface Visitor {
        void node(BlockPos pos, NetworkMember member);

        void host(BlockPos pos, NetworkMember member);
    }

    private static void walk(Level level, BlockPos origin, boolean throughRelays, Visitor visitor) {
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        ArrayDeque<Direction> sides = new ArrayDeque<>();
        visited.add(origin);
        BlockEntity originBe = level.getBlockEntity(origin);
        NetworkMember originMember = originBe instanceof NetworkMember m ? m : null;
        for (Direction d : Direction.values()) {
            if (originMember != null && !originMember.connects(d)) continue;
            queue.add(origin.relative(d));
            sides.add(d.getOpposite());
        }
        while (!queue.isEmpty() && visited.size() < MAX_NODES) {
            BlockPos pos = queue.poll();
            Direction entered = sides.poll();
            if (!visited.add(pos) || !level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            boolean expand;
            NetworkMember member = null;
            if (state.getBlock() instanceof NetworkConductor) {
                expand = true;
            } else {
                BlockEntity be = level.getBlockEntity(pos);
                if (!(be instanceof NetworkMember m) || !m.connects(entered)) continue;
                member = m;
                switch (m.memberKind()) {
                    case NODE -> {
                        visitor.node(pos, m);
                        expand = true;
                    }
                    case HOST -> {
                        visitor.host(pos, m);
                        expand = false;
                    }
                    default -> expand = throughRelays;
                }
            }
            if (!expand) continue;
            for (Direction d : Direction.values()) {
                if (member != null && !member.connects(d)) continue;
                BlockPos next = pos.relative(d);
                if (!visited.contains(next)) {
                    queue.add(next);
                    sides.add(d.getOpposite());
                }
            }
        }
    }

    /** The components a host at this position can see over cables (not through relays or other hosts). */
    public static List<Component> scanComponents(Level level, BlockPos origin) {
        List<Component> out = new ArrayList<>();
        walk(level, origin, false, new Visitor() {
            @Override
            public void node(BlockPos pos, NetworkMember member) {
                out.addAll(member.networkComponents());
            }

            @Override
            public void host(BlockPos pos, NetworkMember member) {
            }
        });
        return out;
    }

    /** Hosts reachable for wired messages from a host (through relays), including itself. */
    private static List<BlockPos> segmentHosts(Level level, BlockPos origin) {
        long epoch = epoch(level);
        Long seen = SEGMENT_EPOCH.get(level.dimension());
        Map<BlockPos, List<BlockPos>> cache = SEGMENTS.computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>());
        if (seen == null || seen != epoch) {
            cache.clear();
            SEGMENT_EPOCH.put(level.dimension(), epoch);
        }
        return cache.computeIfAbsent(origin.immutable(), o -> {
            List<BlockPos> hosts = new ArrayList<>();
            hosts.add(o);
            walk(level, o, true, new Visitor() {
                @Override
                public void node(BlockPos pos, NetworkMember member) {
                }

                @Override
                public void host(BlockPos pos, NetworkMember member) {
                    hosts.add(pos.immutable());
                }
            });
            return hosts;
        });
    }

    // endregion

    // region Messages

    public static void registerWireless(ModemComponent modem) {
        WIRELESS.add(modem);
    }

    public static void unregisterWireless(ModemComponent modem) {
        WIRELESS.remove(modem);
    }

    public static void send(Packet packet) {
        if (PACKETS.size() < 10_000) PACKETS.add(packet);
    }

    /** Delivers queued messages. Called at the end of each server tick. */
    public static void deliver() {
        Packet p;
        int budget = 2000;
        while (budget-- > 0 && (p = PACKETS.poll()) != null) {
            ModemComponent sender = p.sender();
            Level level = sender.location().level();
            if (level == null) continue;
            BlockPos from = sender.location().pos();
            if (sender.isWireless()) {
                for (ModemComponent r : WIRELESS) {
                    if (r == sender || !matches(r, p)) continue;
                    Level rl = r.location().level();
                    if (rl == null) continue;
                    double distance;
                    if (rl == level) {
                        distance = Math.sqrt(r.location().pos().distSqr(from));
                        if (sender.wirelessTier() < 2 && distance > sender.strength()) continue;
                    } else {
                        if (sender.wirelessTier() < 2 || r.wirelessTier() < 2) continue;
                        distance = 0;
                    }
                    r.receive(sender, p.port(), distance, p.values());
                }
            } else {
                for (BlockPos hostPos : segmentHosts(level, from)) {
                    if (!level.isLoaded(hostPos)) continue;
                    if (!(level.getBlockEntity(hostPos) instanceof NetworkMember host)) continue;
                    for (ModemComponent r : host.wiredModems()) {
                        if (r != sender && matches(r, p)) r.receive(sender, p.port(), 0, p.values());
                    }
                }
            }
        }
    }

    private static boolean matches(ModemComponent r, Packet p) {
        return p.target() == null || p.target().equals(r.address());
    }

    // endregion
}
