package dev.computah.block;

import dev.computah.api.Component;
import dev.computah.component.RedstoneComponent;
import dev.computah.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/** A redstone I/O block: a {@code redstone} component for its own six sides, usable from the network. */
public class RedstoneIOBlockEntity extends MemberBlockEntity implements SimpleEntityBlock.Hooks, RedstoneComponent.Host {
    private String address = UUID.randomUUID().toString();
    private @Nullable RedstoneComponent component;

    public RedstoneIOBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REDSTONE_IO.get(), pos, state);
    }

    private RedstoneComponent component() {
        if (component == null) component = new RedstoneComponent(address, this);
        return component;
    }

    @Override
    public List<Component> networkComponents() {
        return List.of(component());
    }

    @Override
    public int input(Direction side) {
        return level == null ? 0 : level.getSignal(worldPosition.relative(side), side);
    }

    @Override
    public void outputsChanged() {
        if (level != null) level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    }

    @Override
    public int signal(Direction towards) {
        return component == null ? 0 : component.output(towards);
    }

    @Override
    public void neighborChanged() {
        component().updateInputs();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("address", address);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("address")) address = tag.getString("address");
    }
}
