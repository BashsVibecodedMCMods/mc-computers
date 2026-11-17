package dev.computah.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Client actions requested from common code (through {@link dev.computah.net.ClientHooks}). */
public final class ClientAccess {
    private ClientAccess() {
    }

    public static void openScreen(BlockPos origin) {
        Minecraft.getInstance().setScreen(new MonitorScreen(origin));
    }
}
