package dev.computah.net;

import dev.computah.Computah;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: input from the screen GUI. Kinds: 0 key_down, 1 key_up, 2 clipboard (text), 3 touch, 4 drag,
 * 5 drop, 6 scroll, 7 request snapshot.
 */
public record ScreenInputPayload(BlockPos pos, int kind, int a, int b, double x, double y, String text) implements CustomPacketPayload {
    public static final int KEY_DOWN = 0, KEY_UP = 1, CLIPBOARD = 2, TOUCH = 3, DRAG = 4, DROP = 5, SCROLL = 6, REQUEST = 7;
    public static final Type<ScreenInputPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Computah.MOD_ID, "screen_input"));
    public static final StreamCodec<ByteBuf, ScreenInputPayload> CODEC = StreamCodec.of((buf, p) -> {
        BlockPos.STREAM_CODEC.encode(buf, p.pos());
        ByteBufCodecs.VAR_INT.encode(buf, p.kind());
        ByteBufCodecs.VAR_INT.encode(buf, p.a());
        ByteBufCodecs.VAR_INT.encode(buf, p.b());
        buf.writeDouble(p.x());
        buf.writeDouble(p.y());
        ByteBufCodecs.stringUtf8(4096).encode(buf, p.text());
    }, buf -> new ScreenInputPayload(BlockPos.STREAM_CODEC.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf), buf.readDouble(), buf.readDouble(), ByteBufCodecs.stringUtf8(4096).decode(buf)));

    public static ScreenInputPayload key(BlockPos pos, boolean down, int ch, int code) {
        return new ScreenInputPayload(pos, down ? KEY_DOWN : KEY_UP, ch, code, 0, 0, "");
    }

    public static ScreenInputPayload pointer(BlockPos pos, int kind, double x, double y, int button) {
        return new ScreenInputPayload(pos, kind, button, 0, x, y, "");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
