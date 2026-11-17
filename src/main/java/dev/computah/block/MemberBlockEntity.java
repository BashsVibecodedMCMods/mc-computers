package dev.computah.block;

import dev.computah.network.NetworkManager;
import dev.computah.network.NetworkMember;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Base for network blocks: tells the network manager when it appears or disappears. */
public abstract class MemberBlockEntity extends BlockEntity implements NetworkMember {
    protected MemberBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        NetworkManager.bump(level);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        NetworkManager.bump(level);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        NetworkManager.bump(level);
    }

    /** Sends the update tag to clients. */
    protected void sync() {
        if (level != null && !level.isClientSide) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        writeClient(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        readClient(tag);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        readClient(pkt.getTag());
    }

    /** Data the client needs (rendering, GUIs). */
    protected void writeClient(CompoundTag tag) {
    }

    protected void readClient(CompoundTag tag) {
    }
}
