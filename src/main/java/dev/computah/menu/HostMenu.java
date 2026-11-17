package dev.computah.menu;

import dev.computah.block.CaseBlockEntity;
import dev.computah.block.RackBlockEntity;
import dev.computah.host.HostCore;
import dev.computah.host.HostInventory;
import dev.computah.item.PartKind;
import dev.computah.item.ServerItem;
import dev.computah.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** The parts GUI of a computer case, a rack server, or a server item held in hand. */
public class HostMenu extends AbstractContainerMenu {
    public enum Mode {CASE, RACK_SERVER, ITEM}

    public final Mode mode;
    public final BlockPos pos;
    public final int serverSlot;
    public final PartKind.SlotType[] layout;
    private final @Nullable HostCore core;
    private final @Nullable InteractionHand hand;
    private final Container parts;

    private HostMenu(int id, Inventory playerInv, Mode mode, BlockPos pos, int serverSlot, PartKind.SlotType[] layout,
                     Container parts, @Nullable HostCore core, @Nullable InteractionHand hand) {
        super(ModMenus.HOST.get(), id);
        this.mode = mode;
        this.pos = pos;
        this.serverSlot = serverSlot;
        this.layout = layout;
        this.core = core;
        this.hand = hand;
        this.parts = parts;
        int[][] positions = slotPositions(layout);
        for (int i = 0; i < layout.length; i++) addSlot(new PartSlot(parts, i, positions[i][0], positions[i][1], layout[i]));
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(playerInv, c + r * 9 + 9, 8 + c * 18, 84 + r * 18));
        for (int c = 0; c < 9; c++) {
            final int index = c;
            addSlot(new Slot(playerInv, c, 8 + c * 18, 142) {
                @Override
                public boolean mayPickup(Player player) {
                    // The held server can't be moved while its GUI is open.
                    return !(mode == Mode.ITEM && hand == InteractionHand.MAIN_HAND && index == playerInv.selected);
                }
            });
        }
    }

    /** Positions of the part slots: one column per slot type group. */
    public static int[][] slotPositions(PartKind.SlotType[] layout) {
        int[][] out = new int[layout.length][];
        int[] counts = new int[4];
        for (int i = 0; i < layout.length; i++) {
            int col = switch (layout[i]) {
                case CARD -> 0;
                case CPU, RAM -> 1;
                case HDD, FLOPPY -> 2;
                case EEPROM -> 3;
            };
            out[i] = new int[]{80 + col * 22, 8 + counts[col]++ * 18};
        }
        return out;
    }

    public static HostMenu forCase(int id, Inventory inv, CaseBlockEntity be) {
        return new HostMenu(id, inv, Mode.CASE, be.getBlockPos(), -1, be.core().inventory().layout(), be.core().inventory(), be.core(), null);
    }

    public static HostMenu forRackServer(int id, Inventory inv, RackBlockEntity rack, int slot) {
        HostCore core = rack.core(slot);
        return new HostMenu(id, inv, Mode.RACK_SERVER, rack.getBlockPos(), slot, PartKind.SERVER, core.inventory(), core, null);
    }

    public static HostMenu forItem(int id, Inventory inv, InteractionHand hand) {
        ItemStack stack = inv.player.getItemInHand(hand);
        HostInventory[] holder = new HostInventory[1];
        holder[0] = new HostInventory(PartKind.SERVER, () -> {
            if (holder[0] == null) return;
            List<ItemStack> list = new ArrayList<>();
            for (int i = 0; i < holder[0].getContainerSize(); i++) list.add(holder[0].getItem(i));
            ItemStack held = inv.player.getItemInHand(hand);
            if (held.getItem() instanceof ServerItem) ServerItem.setParts(held, list);
        });
        List<ItemStack> parts = ServerItem.parts(stack);
        HostInventory container = holder[0];
        holder[0] = null;
        for (int i = 0; i < parts.size() && i < container.getContainerSize(); i++) container.setItem(i, parts.get(i).copy());
        holder[0] = container;
        return new HostMenu(id, inv, Mode.ITEM, BlockPos.ZERO, -1, PartKind.SERVER, container, null, hand);
    }

    public static void writeCase(RegistryFriendlyByteBuf buf, CaseBlockEntity be) {
        buf.writeEnum(Mode.CASE);
        buf.writeBlockPos(be.getBlockPos());
        buf.writeVarInt(be.tier());
    }

    public static void writeRackServer(RegistryFriendlyByteBuf buf, BlockPos rack, int slot) {
        buf.writeEnum(Mode.RACK_SERVER);
        buf.writeBlockPos(rack);
        buf.writeVarInt(slot);
    }

    public static void writeItem(RegistryFriendlyByteBuf buf, InteractionHand hand) {
        buf.writeEnum(Mode.ITEM);
        buf.writeBlockPos(BlockPos.ZERO);
        buf.writeVarInt(hand.ordinal());
    }

    /** Client-side constructor. */
    public static HostMenu fromNetwork(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        Mode mode = buf.readEnum(Mode.class);
        BlockPos pos = buf.readBlockPos();
        int extra = buf.readVarInt();
        PartKind.SlotType[] layout = mode == Mode.CASE ? PartKind.caseLayout(extra) : PartKind.SERVER;
        return new HostMenu(id, inv, mode, pos, mode == Mode.RACK_SERVER ? extra : -1, layout, new SimpleContainer(layout.length), null,
                mode == Mode.ITEM ? InteractionHand.values()[extra] : null);
    }

    public boolean hasPower() {
        return mode != Mode.ITEM;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == 0 && core != null) {
            core.toggle();
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int n = layout.length;
        if (index < n) {
            if (!moveItemStackTo(stack, n, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            for (int i = 0; i < n && !moved; i++) {
                Slot target = slots.get(i);
                if (!target.hasItem() && target.mayPlace(stack)) {
                    target.setByPlayer(stack.split(1));
                    moved = true;
                }
            }
            if (!moved) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        if (mode == Mode.ITEM) return player.getItemInHand(hand).getItem() instanceof ServerItem;
        if (mode == Mode.RACK_SERVER && player.level().getBlockEntity(pos) instanceof RackBlockEntity rack
                && rack.servers().getItem(serverSlot).isEmpty()) return false;
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
