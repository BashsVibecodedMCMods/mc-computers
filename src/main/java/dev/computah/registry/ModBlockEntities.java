package dev.computah.registry;

import dev.computah.Computah;
import dev.computah.block.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> REGISTER = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Computah.MOD_ID);

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CaseBlockEntity>> CASE = REGISTER.register("case",
            () -> BlockEntityType.Builder.of(CaseBlockEntity::new, ModBlocks.CASE1.get(), ModBlocks.CASE2.get(), ModBlocks.CASE3.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ScreenBlockEntity>> SCREEN = REGISTER.register("screen",
            () -> BlockEntityType.Builder.of(ScreenBlockEntity::new, ModBlocks.SCREEN1.get(), ModBlocks.SCREEN2.get(), ModBlocks.SCREEN3.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<KeyboardBlockEntity>> KEYBOARD = REGISTER.register("keyboard",
            () -> BlockEntityType.Builder.of(KeyboardBlockEntity::new, ModBlocks.KEYBOARD.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DiskDriveBlockEntity>> DISK_DRIVE = REGISTER.register("disk_drive",
            () -> BlockEntityType.Builder.of(DiskDriveBlockEntity::new, ModBlocks.DISK_DRIVE.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RelayBlockEntity>> RELAY = REGISTER.register("relay",
            () -> BlockEntityType.Builder.of(RelayBlockEntity::new, ModBlocks.RELAY.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AdapterBlockEntity>> ADAPTER = REGISTER.register("adapter",
            () -> BlockEntityType.Builder.of(AdapterBlockEntity::new, ModBlocks.ADAPTER.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RedstoneIOBlockEntity>> REDSTONE_IO = REGISTER.register("redstone_io",
            () -> BlockEntityType.Builder.of(RedstoneIOBlockEntity::new, ModBlocks.REDSTONE_IO.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RackBlockEntity>> RACK = REGISTER.register("rack",
            () -> BlockEntityType.Builder.of(RackBlockEntity::new, ModBlocks.RACK.get()).build(null));

    private ModBlockEntities() {
    }
}
