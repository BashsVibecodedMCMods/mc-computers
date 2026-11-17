package dev.computah.block;

import dev.computah.api.Component;
import dev.computah.component.AdapterComponents;
import dev.computah.network.NetworkManager;
import dev.computah.network.NetworkMember;
import dev.computah.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * An adapter: exposes the inventories, tanks and energy storage of the blocks next to it as {@code inventory},
 * {@code tank} and {@code energy} components on its network.
 */
public class AdapterBlockEntity extends MemberBlockEntity implements SimpleEntityBlock.Hooks {
    public AdapterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ADAPTER.get(), pos, state);
    }

    @Override
    public List<Component> networkComponents() {
        List<Component> out = new ArrayList<>();
        if (!(level instanceof ServerLevel server)) return out;
        for (Direction d : Direction.values()) {
            BlockPos n = worldPosition.relative(d);
            if (!server.isLoaded(n) || server.getBlockEntity(n) instanceof NetworkMember) continue;
            out.addAll(AdapterComponents.forBlock(server, n, d.getOpposite()));
        }
        return out;
    }

    @Override
    public void neighborChanged() {
        NetworkManager.bump(level);
    }
}
