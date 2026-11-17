package dev.computah.client;

import dev.computah.block.RackBlockEntity;
import dev.computah.menu.RackMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** The rack GUI: server slots, power buttons, and buttons to open each server's parts. */
public class RackScreen extends AbstractContainerScreen<RackMenu> {
    public RackScreen(RackMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = imageHeight - 94;
        titleLabelX = 1000;
    }

    @Override
    protected void init() {
        super.init();
        for (int i = 0; i < RackBlockEntity.SERVERS; i++) {
            final int id = 4 + i;
            addRenderableWidget(Button.builder(Component.translatable("gui.computah.parts"), b -> click(id))
                    .bounds(leftPos + 68, topPos + 7 + i * 18, 44, 16).build());
        }
    }

    private void click(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private RackBlockEntity rack() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(menu.pos) instanceof RackBlockEntity r ? r : null;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        GuiParts.background(g, leftPos, topPos);
        RackBlockEntity rack = rack();
        for (int i = 0; i < RackBlockEntity.SERVERS; i++) {
            var slot = menu.slots.get(i);
            GuiParts.slot(g, leftPos + slot.x, topPos + slot.y, null, false);
            GuiParts.power(g, leftPos + 46, topPos + 7 + i * 18, rack != null && rack.clientRunning(i),
                    GuiParts.inside(mouseX, mouseY, leftPos + 46, topPos + 7 + i * 18, 18, 18));
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, 0x404040, false);
        RackBlockEntity rack = rack();
        for (int i = 0; i < RackBlockEntity.SERVERS; i++) {
            g.drawString(font, String.valueOf(i + 1), 12, 12 + i * 18, 0x404040, false);
            if (rack == null || !menu.slots.get(i).hasItem()) continue;
            boolean on = rack.clientRunning(i);
            String err = rack.clientError(i);
            Component status = on ? Component.translatable("gui.computah.running") : err.isEmpty()
                    ? Component.translatable("gui.computah.off") : Component.translatable("gui.computah.crashed");
            g.drawString(font, status, 116, 12 + i * 18, on ? 0x207020 : err.isEmpty() ? 0x404040 : 0xA02020, false);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        RackBlockEntity rack = rack();
        if (rack == null) return;
        for (int i = 0; i < RackBlockEntity.SERVERS; i++) {
            String err = rack.clientError(i);
            if (!err.isEmpty() && GuiParts.inside(mouseX, mouseY, leftPos + 116, topPos + 10 + i * 18, 56, 12)) {
                g.renderTooltip(font, font.split(Component.literal(err), 220), mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = 0; i < RackBlockEntity.SERVERS; i++) {
            if (GuiParts.inside(mx, my, leftPos + 46, topPos + 7 + i * 18, 18, 18)) {
                click(i);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }
}
