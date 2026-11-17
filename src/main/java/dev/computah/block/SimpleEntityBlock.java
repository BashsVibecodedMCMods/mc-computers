package dev.computah.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * A horizontal-facing block with a block entity: disk drives, relays, adapters, redstone I/O blocks and racks.
 * Opens the block entity's menu if it has one, drops its container when broken, and forwards redstone.
 */
public class SimpleEntityBlock extends HorizontalDirectionalBlock implements EntityBlock {
    /** Optional hooks a block entity can implement. */
    public interface Hooks {
        default void serverTick() {
        }

        default void neighborChanged() {
        }

        default int signal(Direction towards) {
            return 0;
        }

        default @Nullable Container dropContainer() {
            return null;
        }

        default void removed() {
        }
    }

    private final BiFunction<BlockPos, BlockState, BlockEntity> factory;
    private final Supplier<? extends BlockEntityType<?>> type;
    private final boolean redstone;

    public SimpleEntityBlock(BlockBehaviour.Properties properties, BiFunction<BlockPos, BlockState, BlockEntity> factory,
                             Supplier<? extends BlockEntityType<?>> type, boolean redstone) {
        super(properties);
        this.factory = factory;
        this.type = type;
        this.redstone = redstone;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(p -> new SimpleEntityBlock(p, factory, type, redstone));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return factory.apply(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> t) {
        if (level.isClientSide || t != type.get()) return null;
        return (l, p, s, be) -> {
            if (be instanceof Hooks h) h.serverTick();
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof MenuProvider menu) {
            if (!level.isClientSide) player.openMenu(menu, pos);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof Hooks h) {
            h.removed();
            Container c = h.dropContainer();
            if (c != null) Containers.dropContents(level, pos, c);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return redstone;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return redstone && level.getBlockEntity(pos) instanceof Hooks h ? h.signal(direction.getOpposite()) : 0;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof Hooks h) h.neighborChanged();
    }
}
