package dev.computah.menu;

import dev.computah.block.RackBlockEntity;
import dev.computah.item.ServerItem;
import dev.computah.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** The rack GUI: four server slots, each with a power button and a button to open its parts. */
public class RackMenu extends AbstractContainerMenu {
    public final BlockPos pos;
    private final Container servers;

    public RackMenu(int id, Inventory inv, Container servers, BlockPos pos) {
        super(ModMenus.RACK.get(), id);
        this.pos = pos;
        this.servers = servers;
        for (int i = 0; i < RackBlockEntity.SERVERS; i++) {
            addSlot(new Slot(servers, i, 26, 8 + i * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof ServerItem;
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, 84 + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, 142));
    }

    public static RackMenu fromNetwork(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        return new RackMenu(id, inv, new SimpleContainer(RackBlockEntity.SERVERS), buf.readBlockPos());
    }

    /** Buttons 0-3 toggle a server's power; 4-7 open its parts. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player.level().getBlockEntity(pos) instanceof RackBlockEntity rack)) return false;
        int slot = id % 4;
        if (rack.servers().getItem(slot).isEmpty()) return false;
        if (id < 4) {
            rack.core(slot).toggle();
        } else if (player instanceof ServerPlayer sp) {
            sp.openMenu(new SimpleMenuProvider((cid, inv, p) -> HostMenu.forRackServer(cid, inv, rack, slot),
                    Component.translatable("gui.computah.server", slot + 1)), buf -> HostMenu.writeRackServer(buf, pos, slot));
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < RackBlockEntity.SERVERS) {
            if (!moveItemStackTo(stack, RackBlockEntity.SERVERS, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, RackBlockEntity.SERVERS, false)) {
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
