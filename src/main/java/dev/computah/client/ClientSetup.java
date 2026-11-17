package dev.computah.client;

import dev.computah.Computah;
import dev.computah.block.CaseBlock;
import dev.computah.block.ScreenBlock;
import dev.computah.registry.ModBlockEntities;
import dev.computah.registry.ModBlocks;
import dev.computah.registry.ModMenus;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client registration: GUIs, the monitor renderer, tier colours and link lines. */
@EventBusSubscriber(modid = Computah.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
@SuppressWarnings("removal")
public final class ClientSetup {
    /** Tier tints for the (grey) OpenComputers case and screen textures. */
    public static final int[] TIER_COLORS = {0xFFFFFF, 0xC8C8C8, 0xFFD966, 0x7FE8FF};

    private ClientSetup() {
    }

    static {
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent e) -> LinkLineRenderer.render(e));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            ClientDisplays.clear();
            LinkLineRenderer.clear();
        });
    }

    @SubscribeEvent
    public static void screens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.HOST.get(), HostScreen::new);
        event.register(ModMenus.RACK.get(), RackScreen::new);
        event.register(ModMenus.DISK_DRIVE.get(), DiskDriveScreen::new);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.SCREEN.get(), ScreenRenderer::new);
    }

    private static int tierOf(Block b) {
        if (b instanceof CaseBlock c) return c.tier();
        if (b instanceof ScreenBlock s) return s.tier();
        return 0;
    }

    @SubscribeEvent
    public static void blockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> tint == 0 ? TIER_COLORS[tierOf(state.getBlock())] : 0xFFFFFF,
                ModBlocks.CASE1.get(), ModBlocks.CASE2.get(), ModBlocks.CASE3.get(),
                ModBlocks.SCREEN1.get(), ModBlocks.SCREEN2.get(), ModBlocks.SCREEN3.get());
    }

    @SubscribeEvent
    public static void itemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> tint == 0 && stack.getItem() instanceof BlockItem bi ? 0xFF000000 | TIER_COLORS[tierOf(bi.getBlock())] : 0xFFFFFFFF,
                ModBlocks.CASE1.get(), ModBlocks.CASE2.get(), ModBlocks.CASE3.get(),
                ModBlocks.SCREEN1.get(), ModBlocks.SCREEN2.get(), ModBlocks.SCREEN3.get());
    }
}
