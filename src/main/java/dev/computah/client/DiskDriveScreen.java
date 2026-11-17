package dev.computah.client;

import dev.computah.item.PartKind;
import dev.computah.menu.DiskDriveMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class DiskDriveScreen extends AbstractContainerScreen<DiskDriveMenu> {
    public DiskDriveScreen(DiskDriveMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        GuiParts.background(g, leftPos, topPos);
        var slot = menu.slots.get(0);
        GuiParts.slot(g, leftPos + slot.x, topPos + slot.y, PartKind.SlotType.FLOPPY, !slot.hasItem());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
