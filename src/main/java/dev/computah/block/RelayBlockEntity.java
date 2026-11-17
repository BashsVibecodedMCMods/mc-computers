package dev.computah.block;

import dev.computah.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** A relay: joins the networks on its sides for messages, but keeps their components apart. */
public class RelayBlockEntity extends MemberBlockEntity {
    public RelayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RELAY.get(), pos, state);
    }

    @Override
    public Kind memberKind() {
        return Kind.RELAY;
    }
}
