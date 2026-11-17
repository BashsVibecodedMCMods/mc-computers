package dev.computah;

import dev.computah.block.ScreenFormation;
import dev.computah.fs.OsFiles;
import dev.computah.link.Links;
import dev.computah.machine.MachineExecutor;
import dev.computah.net.DisplayPayload;
import dev.computah.net.LinkViewPayload;
import dev.computah.net.ScreenInputPayload;
import dev.computah.net.ServerHandlers;
import dev.computah.network.NetworkManager;
import dev.computah.registry.*;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mod(Computah.MOD_ID)
public final class Computah {
    public static final String MOD_ID = "computah";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    public Computah(IEventBus modBus, ModContainer container) {
        ModComponents.REGISTER.register(modBus);
        ModBlocks.REGISTER.register(modBus);
        ModItems.REGISTER.register(modBus);
        ModBlockEntities.REGISTER.register(modBus);
        ModMenus.REGISTER.register(modBus);
        ModSounds.REGISTER.register(modBus);
        ModTabs.REGISTER.register(modBus);
        ModRecipes.REGISTER.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);

        modBus.addListener(this::registerCapabilities);
        modBus.addListener(this::registerPayloads);

        NeoForge.EVENT_BUS.addListener(this::serverStarting);
        NeoForge.EVENT_BUS.addListener(this::serverStopped);
        NeoForge.EVENT_BUS.addListener(this::addReloadListeners);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Pre e) -> NetworkManager.startTick());
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            ScreenFormation.process();
            NetworkManager.deliver();
        });
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.CASE.get(), (be, side) -> be.energyStorage());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.RACK.get(), (be, side) -> be.energyStorage());
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(DisplayPayload.TYPE, DisplayPayload.CODEC, (p, ctx) -> dev.computah.client.ClientPayloads.display(p));
        r.playToClient(dev.computah.net.BeepPayload.TYPE, dev.computah.net.BeepPayload.CODEC, (p, ctx) -> dev.computah.client.ClientPayloads.beep(p));
        r.playToClient(LinkViewPayload.TYPE, LinkViewPayload.CODEC, (p, ctx) -> dev.computah.client.ClientPayloads.linkView(p));
        r.playToServer(ScreenInputPayload.TYPE, ScreenInputPayload.CODEC, ServerHandlers::screenInput);
    }

    private void serverStarting(ServerStartingEvent event) {
        MachineExecutor.setThreads(Config.get(Config.MACHINE_THREADS));
        OsFiles.load(event.getServer().getResourceManager());
    }

    private void serverStopped(ServerStoppedEvent event) {
        NetworkManager.reset();
        Links.reset();
        MachineExecutor.shutdown();
    }

    private void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new PreparableReloadListener() {
            @Override
            public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager manager, ProfilerFiller p1, ProfilerFiller p2,
                                                  Executor background, Executor game) {
                return CompletableFuture.runAsync(() -> OsFiles.load(manager), background).thenCompose(barrier::wait);
            }
        });
    }
}
