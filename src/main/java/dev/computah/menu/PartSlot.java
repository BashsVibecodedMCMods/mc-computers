package dev.computah.menu;

import dev.computah.host.HostInventory;
import dev.computah.item.PartKind;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** A slot that only takes parts of one type. */
public class PartSlot extends Slot {
    public final PartKind.SlotType type;

    public PartSlot(Container container, int index, int x, int y, PartKind.SlotType type) {
        super(container, index, x, y);
        this.type = type;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return HostInventory.accepts(type, stack);
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }
}
