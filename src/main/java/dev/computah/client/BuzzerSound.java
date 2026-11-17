package dev.computah.client;

import dev.computah.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

/**
 * A computer beep: plays through Minecraft's sound engine (so it follows the block volume slider and fades with
 * distance), but streams a generated {@link BuzzerWave} instead of a sound file, so any frequency is exact.
 */
public final class BuzzerSound extends AbstractSoundInstance {
    private static final AudioFormat FORMAT = new AudioFormat(BuzzerWave.SAMPLE_RATE, 16, 1, true, false);
    private final double frequency, seconds;

    private BuzzerSound(BlockPos pos, double frequency, double seconds) {
        super(ModSounds.BEEP.get(), SoundSource.BLOCKS, RandomSource.create());
        this.frequency = frequency;
        this.seconds = seconds;
        this.x = pos.getX() + 0.5;
        this.y = pos.getY() + 0.5;
        this.z = pos.getZ() + 0.5;
        this.volume = 0.6f;
        this.pitch = 1.0f;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
    }

    public static void play(BlockPos pos, double frequency, double seconds) {
        Minecraft.getInstance().getSoundManager().play(new BuzzerSound(pos, frequency, seconds));
    }

    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary library, net.minecraft.client.resources.sounds.Sound sound, boolean looping) {
        BuzzerWave wave = new BuzzerWave(frequency, seconds);
        return CompletableFuture.completedFuture(new AudioStream() {
            @Override
            public AudioFormat getFormat() {
                return FORMAT;
            }

            @Override
            public ByteBuffer read(int size) {
                int samples = Math.min(size / 2, wave.remaining());
                if (samples <= 0) return null;
                ByteBuffer buf = BufferUtils.createByteBuffer(samples * 2);
                wave.next(buf, samples);
                buf.flip();
                return buf;
            }

            @Override
            public void close() {
            }
        });
    }
}
