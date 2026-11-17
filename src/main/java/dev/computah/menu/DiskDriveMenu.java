package dev.computah.menu;

import dev.computah.item.PartItem;
import dev.computah.item.PartKind;
import dev.computah.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** The disk drive GUI: one floppy slot. */
public class DiskDriveMenu extends AbstractContainerMenu {
    public final BlockPos pos;

    public DiskDriveMenu(int id, Inventory inv, Container drive, BlockPos pos) {
        super(ModMenus.DISK_DRIVE.get(), id);
        this.pos = pos;
        addSlot(new PartSlot(drive, 0, 80, 35, PartKind.SlotType.FLOPPY));
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, 84 + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, 142));
    }

    public static DiskDriveMenu fromNetwork(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        return new DiskDriveMenu(id, inv, new SimpleContainer(1), buf.readBlockPos());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!(stack.getItem() instanceof PartItem p && p.kind().slot == PartKind.SlotType.FLOPPY) || !moveItemStackTo(stack, 0, 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
