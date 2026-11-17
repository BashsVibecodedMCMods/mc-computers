package dev.computah.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** A keyboard plate. Place it on a screen block; right-click it to use that screen. */
public class KeyboardBlock extends DirectionalBlock implements EntityBlock {
    private static final VoxelShape[] SHAPES = new VoxelShape[6];

    static {
        SHAPES[Direction.DOWN.ordinal()] = Block.box(1, 14, 3, 15, 16, 13);
        SHAPES[Direction.UP.ordinal()] = Block.box(1, 0, 3, 15, 2, 13);
        SHAPES[Direction.NORTH.ordinal()] = Block.box(1, 3, 14, 15, 13, 16);
        SHAPES[Direction.SOUTH.ordinal()] = Block.box(1, 3, 0, 15, 13, 2);
        SHAPES[Direction.WEST.ordinal()] = Block.box(14, 3, 1, 16, 13, 15);
        SHAPES[Direction.EAST.ordinal()] = Block.box(0, 3, 1, 2, 13, 15);
    }

    public KeyboardBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return simpleCodec(KeyboardBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getClickedFace());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES[state.getValue(FACING).ordinal()];
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new KeyboardBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof KeyboardBlockEntity kb) {
            ScreenBlockEntity screen = kb.screen();
            if (screen == null) return InteractionResult.PASS;
            if (level.isClientSide) dev.computah.net.ClientHooks.openScreen(screen.getBlockPos());
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }
}
