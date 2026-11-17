package dev.computah.block;

import com.mojang.serialization.MapCodec;
import dev.computah.network.NetworkConductor;
import dev.computah.network.NetworkManager;
import dev.computah.network.NetworkMember;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** A network cable. It connects computers, screens and other network blocks. */
public class CableBlock extends PipeBlock implements NetworkConductor {
    public CableBlock(BlockBehaviour.Properties properties) {
        super(0.1875f, properties);
        BlockState s = stateDefinition.any();
        for (BooleanProperty p : PROPERTY_BY_DIRECTION.values()) s = s.setValue(p, false);
        registerDefaultState(s);
    }

    @Override
    protected MapCodec<? extends PipeBlock> codec() {
        return simpleCodec(CableBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    private static boolean connects(BlockGetter level, BlockPos pos, Direction dir) {
        BlockPos n = pos.relative(dir);
        BlockState s = level.getBlockState(n);
        if (s.getBlock() instanceof NetworkConductor) return true;
        return level.getBlockEntity(n) instanceof NetworkMember m && m.connects(dir.getOpposite());
    }

    private BlockState withConnections(BlockGetter level, BlockPos pos, BlockState state) {
        for (Direction d : Direction.values()) state = state.setValue(PROPERTY_BY_DIRECTION.get(d), connects(level, pos, d));
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return withConnections(ctx.getLevel(), ctx.getClickedPos(), defaultBlockState());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.setValue(PROPERTY_BY_DIRECTION.get(dir), connects(level, pos, dir));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (!old.is(this)) NetworkManager.bump(level);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        super.onRemove(state, level, pos, newState, moved);
        if (!newState.is(this)) NetworkManager.bump(level);
    }
}
