package dev.computah.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.*;

/**
 * Merges adjacent screens into rectangular multiblock monitors. Screens join when they have the same block (tier)
 * and facing and lie in one vertical plane. Each group is split greedily into rectangles from the top-left, at most
 * {@link #MAX_WIDTH} by {@link #MAX_HEIGHT} blocks. Work is queued and done at the end of the server tick.
 */
public final class ScreenFormation {
    public static final int MAX_WIDTH = 8, MAX_HEIGHT = 6;
    private static final int MAX_GROUP = 512;
    private static final Map<ResourceKey<Level>, Set<BlockPos>> PENDING = new HashMap<>();
    private static final Map<ResourceKey<Level>, Level> LEVELS = new HashMap<>();

    private ScreenFormation() {
    }

    public static synchronized void schedule(Level level, BlockPos pos) {
        if (level.isClientSide) return;
        PENDING.computeIfAbsent(level.dimension(), k -> new HashSet<>()).add(pos.immutable());
        LEVELS.put(level.dimension(), level);
    }

    /** Schedules the neighbours of a removed screen. */
    public static void scheduleAround(Level level, BlockPos pos) {
        for (Direction d : Direction.values()) schedule(level, pos.relative(d));
    }

    public static void process() {
        Map<ResourceKey<Level>, Set<BlockPos>> work;
        Map<ResourceKey<Level>, Level> levels;
        synchronized (ScreenFormation.class) {
            if (PENDING.isEmpty()) return;
            work = new HashMap<>(PENDING);
            levels = new HashMap<>(LEVELS);
            PENDING.clear();
            LEVELS.clear();
        }
        for (Map.Entry<ResourceKey<Level>, Set<BlockPos>> e : work.entrySet()) {
            Level level = levels.get(e.getKey());
            Set<BlockPos> done = new HashSet<>();
            for (BlockPos p : e.getValue()) {
                if (done.contains(p) || !level.isLoaded(p) || !(level.getBlockEntity(p) instanceof ScreenBlockEntity)) continue;
                form(level, p, done);
            }
        }
    }

    private static void form(Level level, BlockPos start, Set<BlockPos> done) {
        ScreenBlockEntity first = (ScreenBlockEntity) level.getBlockEntity(start);
        Block block = first.getBlockState().getBlock();
        Direction facing = first.facing();
        Direction right = ScreenBlockEntity.right(facing);
        // Flood fill in the plane.
        Map<BlockPos, ScreenBlockEntity> group = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>(List.of(start));
        while (!queue.isEmpty() && group.size() < MAX_GROUP) {
            BlockPos p = queue.poll();
            if (group.containsKey(p) || !level.isLoaded(p)) continue;
            if (!(level.getBlockEntity(p) instanceof ScreenBlockEntity s) || s.getBlockState().getBlock() != block || s.facing() != facing) continue;
            group.put(p, s);
            for (Direction d : new Direction[]{right, right.getOpposite(), Direction.UP, Direction.DOWN}) queue.add(p.relative(d));
        }
        done.addAll(group.keySet());
        // Grid coordinates: u along the viewer's right, v up.
        Map<Long, BlockPos> grid = new HashMap<>();
        for (BlockPos p : group.keySet()) grid.put(key(u(p, right), p.getY()), p);
        List<BlockPos> order = new ArrayList<>(group.keySet());
        order.sort(Comparator.comparingInt((BlockPos p) -> -p.getY()).thenComparingInt(p -> u(p, right)));
        Set<BlockPos> assigned = new HashSet<>();
        for (BlockPos o : order) {
            if (assigned.contains(o)) continue;
            int ou = u(o, right), ov = o.getY();
            int w = 1;
            while (w < MAX_WIDTH && free(grid, assigned, ou + w, ov)) w++;
            int h = 1;
            outer:
            while (h < MAX_HEIGHT) {
                for (int x = 0; x < w; x++) if (!free(grid, assigned, ou + x, ov - h)) break outer;
                h++;
            }
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    BlockPos p = grid.get(key(ou + x, ov - y));
                    assigned.add(p);
                    group.get(p).setShape(x, y, w, h);
                }
            }
        }
    }

    private static boolean free(Map<Long, BlockPos> grid, Set<BlockPos> assigned, int u, int v) {
        BlockPos p = grid.get(key(u, v));
        return p != null && !assigned.contains(p);
    }

    private static int u(BlockPos p, Direction right) {
        return p.getX() * right.getStepX() + p.getZ() * right.getStepZ();
    }

    private static long key(int u, int v) {
        return ((long) u << 32) | (v & 0xFFFFFFFFL);
    }
}
