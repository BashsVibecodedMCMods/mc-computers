package dev.computah.registry;

import dev.computah.Computah;
import dev.computah.block.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks REGISTER = DeferredRegister.createBlocks(Computah.MOD_ID);

    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f, 5.0f).sound(SoundType.METAL);
    }

    public static final DeferredBlock<CaseBlock> CASE1 = REGISTER.register("case1", () -> new CaseBlock(metal(), 1));
    public static final DeferredBlock<CaseBlock> CASE2 = REGISTER.register("case2", () -> new CaseBlock(metal(), 2));
    public static final DeferredBlock<CaseBlock> CASE3 = REGISTER.register("case3", () -> new CaseBlock(metal(), 3));
    public static final DeferredBlock<ScreenBlock> SCREEN1 = REGISTER.register("screen1", () -> new ScreenBlock(metal(), 1));
    public static final DeferredBlock<ScreenBlock> SCREEN2 = REGISTER.register("screen2", () -> new ScreenBlock(metal(), 2));
    public static final DeferredBlock<ScreenBlock> SCREEN3 = REGISTER.register("screen3", () -> new ScreenBlock(metal(), 3));
    public static final DeferredBlock<KeyboardBlock> KEYBOARD = REGISTER.register("keyboard",
            () -> new KeyboardBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.0f).sound(SoundType.STONE).noOcclusion()));
    public static final DeferredBlock<CableBlock> CABLE = REGISTER.register("cable",
            () -> new CableBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.5f).sound(SoundType.METAL).noOcclusion()));
    public static final DeferredBlock<SimpleEntityBlock> DISK_DRIVE = REGISTER.register("disk_drive",
            () -> new SimpleEntityBlock(metal(), DiskDriveBlockEntity::new, ModBlockEntities.DISK_DRIVE, false));
    public static final DeferredBlock<SimpleEntityBlock> RELAY = REGISTER.register("relay",
            () -> new SimpleEntityBlock(metal(), RelayBlockEntity::new, ModBlockEntities.RELAY, false));
    public static final DeferredBlock<SimpleEntityBlock> ADAPTER = REGISTER.register("adapter",
            () -> new SimpleEntityBlock(metal(), AdapterBlockEntity::new, ModBlockEntities.ADAPTER, false));
    public static final DeferredBlock<SimpleEntityBlock> REDSTONE_IO = REGISTER.register("redstone_io",
            () -> new SimpleEntityBlock(metal(), RedstoneIOBlockEntity::new, ModBlockEntities.REDSTONE_IO, true));
    public static final DeferredBlock<SimpleEntityBlock> RACK = REGISTER.register("rack",
            () -> new SimpleEntityBlock(metal(), RackBlockEntity::new, ModBlockEntities.RACK, true));

    private ModBlocks() {
    }
}
