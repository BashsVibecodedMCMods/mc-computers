package dev.computah.block;

import dev.computah.api.Component;
import dev.computah.component.KeyboardComponent;
import dev.computah.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/** A keyboard attached to the block it was placed against (normally a screen). */
public class KeyboardBlockEntity extends MemberBlockEntity {
    private String address = UUID.randomUUID().toString();
    private @Nullable KeyboardComponent component;

    public KeyboardBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.KEYBOARD.get(), pos, state);
    }

    public KeyboardComponent component() {
        if (component == null) component = new KeyboardComponent(address);
        return component;
    }

    public BlockPos attachedPos() {
        return worldPosition.relative(getBlockState().getValue(KeyboardBlock.FACING).getOpposite());
    }

    public @Nullable ScreenBlockEntity screen() {
        return level != null && level.getBlockEntity(attachedPos()) instanceof ScreenBlockEntity s ? s.origin() : null;
    }

    @Override
    public List<Component> networkComponents() {
        return List.of(component());
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
