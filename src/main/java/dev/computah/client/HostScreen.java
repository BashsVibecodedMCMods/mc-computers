package dev.computah.client;

import dev.computah.block.CaseBlockEntity;
import dev.computah.block.RackBlockEntity;
import dev.computah.menu.HostMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/** GUI for the parts of a computer case or server, with the power button and the last error. */
public class HostScreen extends AbstractContainerScreen<HostMenu> {
    private static final int POWER_X = 20, POWER_Y = 20;

    public HostScreen(HostMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = imageHeight - 94;
    }

    private boolean running() {
        if (minecraft == null || minecraft.level == null) return false;
        if (menu.mode == HostMenu.Mode.CASE && minecraft.level.getBlockEntity(menu.pos) instanceof CaseBlockEntity be) return be.clientRunning();
        if (menu.mode == HostMenu.Mode.RACK_SERVER && minecraft.level.getBlockEntity(menu.pos) instanceof RackBlockEntity be) return be.clientRunning(menu.serverSlot);
        return false;
    }

    private String error() {
        if (minecraft == null || minecraft.level == null) return "";
        if (menu.mode == HostMenu.Mode.CASE && minecraft.level.getBlockEntity(menu.pos) instanceof CaseBlockEntity be) return be.clientError();
        if (menu.mode == HostMenu.Mode.RACK_SERVER && minecraft.level.getBlockEntity(menu.pos) instanceof RackBlockEntity be) return be.clientError(menu.serverSlot);
        return "";
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        GuiParts.background(g, leftPos, topPos);
        for (int i = 0; i < menu.layout.length; i++) {
            var slot = menu.slots.get(i);
            GuiParts.slot(g, leftPos + slot.x, topPos + slot.y, menu.layout[i], !slot.hasItem());
        }
        if (menu.hasPower()) {
            GuiParts.power(g, leftPos + POWER_X, topPos + POWER_Y, running(),
                    GuiParts.inside(mouseX, mouseY, leftPos + POWER_X, topPos + POWER_Y, 18, 18));
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 6, 0x404040, false);
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, 0x404040, false);
        if (menu.hasPower()) {
            String err = error();
            Component status = running() ? Component.translatable("gui.computah.running") : err.isEmpty()
                    ? Component.translatable("gui.computah.off") : Component.translatable("gui.computah.crashed");
            g.drawString(font, status, 8, 44, running() ? 0x207020 : err.isEmpty() ? 0x404040 : 0xA02020, false);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (menu.hasPower() && GuiParts.inside(mouseX, mouseY, leftPos + POWER_X, topPos + POWER_Y, 18, 18)) {
            g.renderTooltip(font, Component.translatable(running() ? "gui.computah.power_off" : "gui.computah.power_on"), mouseX, mouseY);
        }
        String err = error();
        if (!err.isEmpty() && !running() && GuiParts.inside(mouseX, mouseY, leftPos + 8, topPos + 42, 60, 12)) {
            List<FormattedCharSequence> lines = font.split(Component.literal(err), 220);
            g.renderTooltip(font, lines.subList(0, Math.min(lines.size(), 16)), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (menu.hasPower() && GuiParts.inside(mx, my, leftPos + POWER_X, topPos + POWER_Y, 18, 18) && minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }
}
