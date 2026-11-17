package dev.computah.client;

import dev.computah.Computah;
import dev.computah.item.PartKind;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Shared drawing for the Computah GUIs (textures from OpenComputers). */
final class GuiParts {
    static final ResourceLocation BACKGROUND = tex("gui/background.png");
    static final ResourceLocation SLOT = tex("gui/slot.png");
    static final ResourceLocation POWER = tex("gui/button_power.png");

    private GuiParts() {
    }

    static ResourceLocation tex(String path) {
        return ResourceLocation.fromNamespaceAndPath(Computah.MOD_ID, "textures/" + path);
    }

    static void background(GuiGraphics g, int x, int y) {
        g.blit(BACKGROUND, x, y, 0, 0, 176, 166, 256, 256);
    }

    /** A slot frame at a slot's item position, with the type icon when it is empty. */
    static void slot(GuiGraphics g, int x, int y, PartKind.SlotType type, boolean empty) {
        g.blit(SLOT, x - 1, y - 1, 0, 0, 18, 18, 18, 18);
        if (empty && type != null) g.blit(tex("gui/icons/" + type.icon + ".png"), x, y, 0, 0, 16, 16, 16, 16);
    }

    /** The power button (18x18): left column off, right column on; bottom row when hovered. */
    static void power(GuiGraphics g, int x, int y, boolean on, boolean hovered) {
        g.blit(POWER, x, y, on ? 18 : 0, hovered ? 18 : 0, 18, 18, 36, 36);
    }

    static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }
}
