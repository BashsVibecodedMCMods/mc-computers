package dev.computah.client;

import dev.computah.net.ScreenInputPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * The screen GUI: shows the monitor's display (the same image drawn on the block) and sends keyboard, clipboard and
 * mouse input to the computers that see the monitor's keyboards. Key codes are GLFW key codes. Escape closes it.
 */
public class MonitorScreen extends Screen {
    private final BlockPos pos;
    private int pendingCode = -1;
    private int pendingChar;
    private double dx, dy, dw, dh;
    private boolean dragging;

    public MonitorScreen(BlockPos pos) {
        super(Component.translatable("gui.computah.screen"));
        this.pos = pos;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void send(ScreenInputPayload p) {
        PacketDistributor.sendToServer(p);
    }

    private void flushKey() {
        if (pendingCode >= 0) {
            send(ScreenInputPayload.key(pos, true, pendingChar, pendingCode));
            pendingCode = -1;
            pendingChar = 0;
        }
    }

    @Override
    public void tick() {
        flushKey();
        if (minecraft != null && minecraft.player != null && minecraft.player.distanceToSqr(pos.getCenter()) > 20 * 20) onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        flushKey();
        g.fill(0, 0, width, height, 0xC0101010);
        ClientDisplays.Display d = ClientDisplays.get(pos);
        if (d == null) {
            g.drawCenteredString(font, Component.translatable("gui.computah.screen.waiting"), width / 2, height / 2, 0xAAAAAA);
            return;
        }
        int tw = d.width(), th = d.height();
        double scale = Math.min((width - 20) / (double) tw, (height - 20) / (double) th);
        dw = tw * scale;
        dh = th * scale;
        dx = (width - dw) / 2;
        dy = (height - dh) / 2;
        g.fill((int) dx - 4, (int) dy - 4, (int) (dx + dw) + 4, (int) (dy + dh) + 4, 0xFF3A3A3A);
        if (d.on) {
            ResourceLocation tex = d.texture();
            g.pose().pushPose();
            g.pose().translate(dx, dy, 0);
            g.pose().scale((float) scale, (float) scale, 1);
            g.blit(tex, 0, 0, tw, th, 0, 0, tw, th, tw, th);
            g.pose().popPose();
        } else {
            g.fill((int) dx, (int) dy, (int) (dx + dw), (int) (dy + dh), 0xFF000000);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && (modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
            String clip = minecraft == null ? "" : minecraft.keyboardHandler.getClipboard();
            if (!clip.isEmpty()) send(new ScreenInputPayload(pos, ScreenInputPayload.CLIPBOARD, 0, 0, 0, 0, clip.length() > 4000 ? clip.substring(0, 4000) : clip));
            return true;
        }
        flushKey();
        pendingCode = keyCode;
        pendingChar = 0;
        // Control combinations produce no character event; give them their control character like a terminal.
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_Z) {
            pendingChar = keyCode - GLFW.GLFW_KEY_A + 1;
        } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            pendingChar = 13;
        } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            pendingChar = 8;
        } else if (keyCode == GLFW.GLFW_KEY_TAB) {
            pendingChar = 9;
        } else if (keyCode == GLFW.GLFW_KEY_DELETE) {
            pendingChar = 127;
        }
        return true;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (pendingCode >= 0 && pendingChar == 0) {
            pendingChar = c;
            flushKey();
        } else {
            send(ScreenInputPayload.key(pos, true, c, 0));
        }
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        flushKey();
        send(ScreenInputPayload.key(pos, false, 0, keyCode));
        return true;
    }

    private double[] cell(double mx, double my) {
        ClientDisplays.Display d = ClientDisplays.get(pos);
        if (d == null || dw <= 0) return null;
        double fx = (mx - dx) / dw, fy = (my - dy) / dh;
        if (fx < 0 || fy < 0 || fx >= 1 || fy >= 1) return null;
        return new double[]{fx * d.buffer.cols() + 1, fy * d.buffer.rows() + 1};
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        double[] c = cell(mx, my);
        if (c != null) {
            dragging = true;
            send(ScreenInputPayload.pointer(pos, ScreenInputPayload.TOUCH, c[0], c[1], button));
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double ddx, double ddy) {
        double[] c = cell(mx, my);
        if (c != null && dragging) send(ScreenInputPayload.pointer(pos, ScreenInputPayload.DRAG, c[0], c[1], button));
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        double[] c = cell(mx, my);
        if (c != null && dragging) send(ScreenInputPayload.pointer(pos, ScreenInputPayload.DROP, c[0], c[1], button));
        dragging = false;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        double[] c = cell(mx, my);
        if (c != null && sy != 0) send(ScreenInputPayload.pointer(pos, ScreenInputPayload.SCROLL, c[0], c[1], sy > 0 ? 1 : -1));
        return true;
    }
}
