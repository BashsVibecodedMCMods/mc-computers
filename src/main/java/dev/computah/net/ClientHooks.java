package dev.computah.net;

import net.minecraft.core.BlockPos;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Entry points that common code calls on the client side. Client-only classes are reached by name only behind a
 * dist check, so dedicated servers never load them.
 */
public final class ClientHooks {
    private ClientHooks() {
    }

    public static void requestDisplay(BlockPos pos) {
        if (!FMLEnvironment.dist.isClient()) return;
        PacketDistributor.sendToServer(new ScreenInputPayload(pos, ScreenInputPayload.REQUEST, 0, 0, 0, 0, ""));
    }

    public static void displayRemoved(BlockPos pos) {
        if (FMLEnvironment.dist.isClient()) dev.computah.client.ClientDisplays.remove(pos);
    }

    public static void openScreen(BlockPos origin) {
        if (FMLEnvironment.dist.isClient()) dev.computah.client.ClientAccess.openScreen(origin);
    }
}
