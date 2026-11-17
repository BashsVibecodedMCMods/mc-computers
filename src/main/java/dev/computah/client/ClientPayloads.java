package dev.computah.client;

import dev.computah.Computah;
import dev.computah.net.DisplayPayload;
import dev.computah.net.LinkViewPayload;

/** Client-side payload handling (runs on the network thread; work is moved to the main thread). */
public final class ClientPayloads {
    private ClientPayloads() {
    }

    public static void display(DisplayPayload p) {
        net.minecraft.client.Minecraft.getInstance().execute(() -> {
            try {
                byte[] data = p.inflate();
                ClientDisplays.Display d = p.snapshot() ? ClientDisplays.getOrCreate(p.pos()) : ClientDisplays.get(p.pos());
                if (d == null) return;
                d.on = p.on();
                d.buffer.applyLog(data);
            } catch (Exception e) {
                Computah.LOG.warn("Bad display update for {}", p.pos(), e);
                ClientDisplays.remove(p.pos());
                dev.computah.net.ClientHooks.requestDisplay(p.pos());
            }
        });
    }

    public static void linkView(LinkViewPayload p) {
        net.minecraft.client.Minecraft.getInstance().execute(() -> LinkLineRenderer.set(p));
    }
}
