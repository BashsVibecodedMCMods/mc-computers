package dev.computah.net;

import dev.computah.Computah;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: a computer at pos beeps (the client generates the tone). */
public record BeepPayload(BlockPos pos, float frequency, float seconds) implements CustomPacketPayload {
    public static final Type<BeepPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Computah.MOD_ID, "beep"));
    public static final StreamCodec<ByteBuf, BeepPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, BeepPayload::pos, ByteBufCodecs.FLOAT, BeepPayload::frequency, ByteBufCodecs.FLOAT, BeepPayload::seconds,
            BeepPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
