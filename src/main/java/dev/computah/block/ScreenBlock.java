package dev.computah.block;

import com.mojang.serialization.MapCodec;
import dev.computah.component.ScreenComponent;
import dev.computah.display.ScreenGeometry;
import dev.computah.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A monitor block (tiers 1-3). With a keyboard attached, right-clicking opens the screen GUI; otherwise (or while
 * sneaking) a right-click on the face sends a touch signal on tier 2 and 3 screens.
 */
public class ScreenBlock extends HorizontalDirectionalBlock implements EntityBlock {
    /** Where a block sits in its monitor, so the bezel textures connect: horizontally (viewer's left to right) and vertically. */
    public enum Part implements net.minecraft.util.StringRepresentable {
        SINGLE, FIRST, MIDDLE, LAST;

        public static Part of(int index, int size) {
            return size <= 1 ? SINGLE : index == 0 ? FIRST : index == size - 1 ? LAST : MIDDLE;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final net.minecraft.world.level.block.state.properties.EnumProperty<Part> HORIZONTAL =
            net.minecraft.world.level.block.state.properties.EnumProperty.create("horizontal", Part.class);
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<Part> VERTICAL =
            net.minecraft.world.level.block.state.properties.EnumProperty.create("vertical", Part.class);

    private final int tier;

    public ScreenBlock(BlockBehaviour.Properties properties, int tier) {
        super(properties);
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(HORIZONTAL, Part.SINGLE).setValue(VERTICAL, Part.SINGLE));
    }

    public int tier() {
        return tier;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(p -> new ScreenBlock(p, tier));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HORIZONTAL, VERTICAL);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ScreenBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || type != ModBlockEntities.SCREEN.get() ? null
                : (BlockEntityTicker<T>) (BlockEntityTicker<ScreenBlockEntity>) ScreenBlockEntity::serverTick;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ScreenBlockEntity be)) return InteractionResult.PASS;
        ScreenBlockEntity origin = be.origin();
        if (origin == null) return InteractionResult.PASS;
        boolean gui = !player.isShiftKeyDown() && origin.hasKeyboard();
        if (level.isClientSide) {
            if (gui) dev.computah.net.ClientHooks.openScreen(origin.getBlockPos());
            return InteractionResult.SUCCESS;
        }
        if (!gui && tier >= 2 && hit.getDirection() == be.facing()) touch(be, origin, player, hit.getLocation());
        return InteractionResult.CONSUME;
    }

    private static void touch(ScreenBlockEntity be, ScreenBlockEntity origin, Player player, Vec3 location) {
        ScreenComponent c = origin.component();
        if (c == null) return;
        Direction right = ScreenBlockEntity.right(be.facing());
        BlockPos pos = be.getBlockPos();
        double along = right.getAxis() == Direction.Axis.X ? location.x - pos.getX() : location.z - pos.getZ();
        if (right.getAxisDirection() == Direction.AxisDirection.NEGATIVE) along = 1 - along;
        double down = 1 - (location.y - pos.getY());
        double[] cell = ScreenGeometry.toCell(origin.width(), origin.height(), c.buffer(), be.gx() + along, be.gy() + down);
        if (cell == null) return;
        Object x = c.precise() ? cell[0] : (Object) (int) Math.floor(cell[0]);
        Object y = c.precise() ? cell[1] : (Object) (int) Math.floor(cell[1]);
        c.signalAll("touch", c.address(), x, y, 0, player.getName().getString());
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        super.onRemove(state, level, pos, newState, moved);
        if (!state.is(newState.getBlock()) && !level.isClientSide) ScreenFormation.scheduleAround(level, pos);
    }
}
