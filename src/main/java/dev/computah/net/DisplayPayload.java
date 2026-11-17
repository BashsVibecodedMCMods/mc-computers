package dev.computah.net;

import dev.computah.Computah;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/** Server to client: display operations (or a full snapshot) for a monitor, deflate-compressed. */
public record DisplayPayload(BlockPos pos, byte[] data, boolean snapshot, boolean on) implements CustomPacketPayload {
    public static final Type<DisplayPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Computah.MOD_ID, "display"));
    public static final StreamCodec<ByteBuf, DisplayPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, DisplayPayload::pos, ByteBufCodecs.byteArray(4 * 1024 * 1024), DisplayPayload::data,
            ByteBufCodecs.BOOL, DisplayPayload::snapshot, ByteBufCodecs.BOOL, DisplayPayload::on, DisplayPayload::new);

    public static DisplayPayload of(BlockPos pos, byte[] raw, boolean snapshot, boolean on) {
        Deflater d = new Deflater(Deflater.BEST_SPEED);
        d.setInput(raw);
        d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, raw.length / 4));
        byte[] buf = new byte[8192];
        while (!d.finished()) out.write(buf, 0, d.deflate(buf));
        d.end();
        return new DisplayPayload(pos, out.toByteArray(), snapshot, on);
    }

    public byte[] inflate() throws DataFormatException {
        Inflater i = new Inflater();
        i.setInput(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length * 4);
        byte[] buf = new byte[8192];
        while (!i.finished()) {
            int n = i.inflate(buf);
            if (n == 0 && (i.needsInput() || i.needsDictionary())) break;
            out.write(buf, 0, n);
            if (out.size() > 32 * 1024 * 1024) throw new DataFormatException("too large");
        }
        i.end();
        return out.toByteArray();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
