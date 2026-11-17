package dev.computah.component;

import dev.computah.api.Arguments;
import dev.computah.api.Callback;
import dev.computah.api.Component;
import dev.computah.api.ComponentException;
import dev.computah.api.Context;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Components for other mods' and vanilla blocks, reached through an adapter block or a wireless link:
 * {@code inventory} (item handlers), {@code tank} (fluid handlers) and {@code energy} (FE storage). All methods
 * touch the world, so they are indirect (one call per tick).
 */
public final class AdapterComponents {
    private AdapterComponents() {
    }

    /** A stable address for a block's capability, so programs can keep using it. */
    public static String addressFor(ServerLevel level, BlockPos pos, @Nullable Direction side, String kind) {
        String key = level.dimension().location() + "@" + pos.asLong() + "@" + (side == null ? "-" : side.getName()) + "@" + kind;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    /** The components a block offers, or an empty list. */
    public static List<Component> forBlock(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        List<Component> out = new ArrayList<>();
        if (!level.isLoaded(pos)) return out;
        if (level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side) != null) out.add(new Inventory(level, pos, side));
        if (level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side) != null) out.add(new Tank(level, pos, side));
        if (level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side) != null) out.add(new Energy(level, pos, side));
        return out;
    }

    public abstract static class Base extends Component {
        final ServerLevel level;
        final BlockPos pos;
        final @Nullable Direction side;
        private final String address;

        Base(ServerLevel level, BlockPos pos, @Nullable Direction side, String kind) {
            this.level = level;
            this.pos = pos.immutable();
            this.side = side;
            this.address = addressFor(level, pos, side, kind);
        }

        @Override
        public String address() {
            return address;
        }

        @Callback(direct = false, doc = "function():string -- The block's registry name.")
        public Object[] getBlockName(Context ctx, Arguments args) {
            return new Object[]{BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString()};
        }

        @Callback(doc = "function():number, number, number -- The block's coordinates.")
        public Object[] getPosition(Context ctx, Arguments args) {
            return new Object[]{pos.getX(), pos.getY(), pos.getZ()};
        }
    }

    static Map<String, Object> describe(ItemStack stack) {
        if (stack.isEmpty()) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        m.put("label", stack.getHoverName().getString());
        m.put("count", stack.getCount());
        m.put("maxCount", stack.getMaxStackSize());
        m.put("damage", stack.getDamageValue());
        m.put("maxDamage", stack.getMaxDamage());
        return m;
    }

    public static final class Inventory extends Base {
        Inventory(ServerLevel level, BlockPos pos, @Nullable Direction side) {
            super(level, pos, side, "inventory");
        }

        @Override
        public String type() {
            return "inventory";
        }

        private IItemHandler handler() throws ComponentException {
            IItemHandler h = level.isLoaded(pos) ? level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side) : null;
            if (h == null) throw new ComponentException("inventory is gone");
            return h;
        }

        private int slot(IItemHandler h, Arguments args, int i) throws ComponentException {
            int s = args.checkInteger(i);
            if (s < 1 || s > h.getSlots()) throw new ComponentException("invalid slot");
            return s - 1;
        }

        @Callback(direct = false, doc = "function():number -- The number of slots.")
        public Object[] getInventorySize(Context ctx, Arguments args) throws ComponentException {
            return new Object[]{handler().getSlots()};
        }

        @Callback(direct = false, doc = "function(slot:number):table -- The item in a slot (1-based): {name, label, count, maxCount, damage, maxDamage}, or nil.")
        public Object[] getStackInSlot(Context ctx, Arguments args) throws ComponentException {
            IItemHandler h = handler();
            return new Object[]{describe(h.getStackInSlot(slot(h, args, 0)))};
        }

        @Callback(direct = false, doc = "function():table -- All slots: {[slot] = item}.")
        public Object[] list(Context ctx, Arguments args) throws ComponentException {
            IItemHandler h = handler();
            Map<Integer, Object> out = new LinkedHashMap<>();
            for (int i = 0; i < h.getSlots(); i++) {
                Map<String, Object> d = describe(h.getStackInSlot(i));
                if (d != null) out.put(i + 1, d);
            }
            return new Object[]{out};
        }

        @Callback(direct = false, doc = "function(target:string, fromSlot:number[, count:number[, toSlot:number]]):number -- Moves items into another inventory component. Returns how many moved.")
        public Object[] pushItems(Context ctx, Arguments args) throws ComponentException {
            IItemHandler from = handler();
            if (!(ctx.machine().component(args.checkString(0)) instanceof Inventory target)) throw new ComponentException("target is not an inventory");
            IItemHandler to = target.handler();
            int fromSlot = slot(from, args, 1);
            int count = args.optInteger(2, 64);
            Integer toSlot = args.isNil(3) ? null : target.slot(to, args, 3);
            ItemStack extracted = from.extractItem(fromSlot, count, true);
            if (extracted.isEmpty()) return new Object[]{0};
            ItemStack rest = toSlot != null ? to.insertItem(toSlot, extracted, true) : insertAll(to, extracted, true);
            int moved = extracted.getCount() - rest.getCount();
            if (moved <= 0) return new Object[]{0};
            ItemStack real = from.extractItem(fromSlot, moved, false);
            ItemStack left = toSlot != null ? to.insertItem(toSlot, real, false) : insertAll(to, real, false);
            if (!left.isEmpty()) from.insertItem(fromSlot, left, false);
            return new Object[]{moved - left.getCount()};
        }

        private static ItemStack insertAll(IItemHandler h, ItemStack stack, boolean simulate) {
            ItemStack rest = stack.copy();
            for (int i = 0; i < h.getSlots() && !rest.isEmpty(); i++) rest = h.insertItem(i, rest, simulate);
            return rest;
        }
    }

    public static final class Tank extends Base {
        Tank(ServerLevel level, BlockPos pos, @Nullable Direction side) {
            super(level, pos, side, "tank");
        }

        @Override
        public String type() {
            return "tank";
        }

        private IFluidHandler handler() throws ComponentException {
            IFluidHandler h = level.isLoaded(pos) ? level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side) : null;
            if (h == null) throw new ComponentException("tank is gone");
            return h;
        }

        @Callback(direct = false, doc = "function():table -- Every tank: {{name, amount, capacity}, ...} (amounts in millibuckets).")
        public Object[] getTanks(Context ctx, Arguments args) throws ComponentException {
            IFluidHandler h = handler();
            List<Object> out = new ArrayList<>();
            for (int i = 0; i < h.getTanks(); i++) {
                FluidStack f = h.getFluidInTank(i);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", f.isEmpty() ? null : BuiltInRegistries.FLUID.getKey(f.getFluid()).toString());
                m.put("label", f.isEmpty() ? null : f.getHoverName().getString());
                m.put("amount", f.getAmount());
                m.put("capacity", h.getTankCapacity(i));
                out.add(m);
            }
            return new Object[]{out};
        }
    }

    public static final class Energy extends Base {
        Energy(ServerLevel level, BlockPos pos, @Nullable Direction side) {
            super(level, pos, side, "energy");
        }

        @Override
        public String type() {
            return "energy";
        }

        private IEnergyStorage handler() throws ComponentException {
            IEnergyStorage h = level.isLoaded(pos) ? level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side) : null;
            if (h == null) throw new ComponentException("energy storage is gone");
            return h;
        }

        @Callback(direct = false, doc = "function():number -- Stored energy (FE).")
        public Object[] getEnergyStored(Context ctx, Arguments args) throws ComponentException {
            return new Object[]{handler().getEnergyStored()};
        }

        @Callback(direct = false, doc = "function():number -- Energy capacity (FE).")
        public Object[] getMaxEnergyStored(Context ctx, Arguments args) throws ComponentException {
            return new Object[]{handler().getMaxEnergyStored()};
        }
    }
}
