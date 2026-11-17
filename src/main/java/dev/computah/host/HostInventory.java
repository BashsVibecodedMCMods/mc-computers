package dev.computah.host;

import dev.computah.item.PartItem;
import dev.computah.item.PartKind;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/** The part slots of a case or server. Each slot only accepts parts of its slot type. */
public class HostInventory extends SimpleContainer {
    private final PartKind.SlotType[] layout;
    private final Runnable onChange;

    public HostInventory(PartKind.SlotType[] layout, Runnable onChange) {
        super(layout.length);
        this.layout = layout;
        this.onChange = onChange;
    }

    public PartKind.SlotType[] layout() {
        return layout;
    }

    public static boolean accepts(PartKind.SlotType type, ItemStack stack) {
        return stack.getItem() instanceof PartItem p && p.kind().slot == type;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot >= 0 && slot < layout.length && accepts(layout[slot], stack);
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        onChange.run();
    }
}
