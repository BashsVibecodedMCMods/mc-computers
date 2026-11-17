package dev.computah.net;

import dev.computah.Computah;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Server to client: the linker's selected host and its links, for drawing lines while the linker is held. */
public record LinkViewPayload(BlockPos host, List<BlockPos> targets, boolean visible) implements CustomPacketPayload {
    public static final Type<LinkViewPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Computah.MOD_ID, "link_view"));
    public static final StreamCodec<ByteBuf, LinkViewPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, LinkViewPayload::host, BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(1024)), LinkViewPayload::targets,
            ByteBufCodecs.BOOL, LinkViewPayload::visible, LinkViewPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
