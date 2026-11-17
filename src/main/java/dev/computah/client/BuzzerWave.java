package dev.computah.client;

/**
 * Generates the computer's buzzer tone: a square wave like a PC speaker, at any frequency, as signed 16-bit mono PCM.
 * The first and last few milliseconds fade in and out so the tone starts and stops without clicks. Plain Java (no
 * client classes), so it can be tested anywhere.
 */
public final class BuzzerWave {
    public static final int SAMPLE_RATE = 22050;
    public static final double MIN_FREQUENCY = 20, MAX_FREQUENCY = 2000;
    /** Peak amplitude (out of 32767); a square wave is loud, so it stays well below full scale. */
    public static final int AMPLITUDE = 9000;
    private static final double FADE_SECONDS = 0.003;

    private final double frequency;
    private final int totalSamples;
    private int position;

    public BuzzerWave(double frequency, double seconds) {
        this.frequency = Math.max(MIN_FREQUENCY, Math.min(MAX_FREQUENCY, frequency));
        this.totalSamples = Math.max(1, (int) Math.round(Math.max(0, seconds) * SAMPLE_RATE));
    }

    public int totalSamples() {
        return totalSamples;
    }

    public int remaining() {
        return totalSamples - position;
    }

    /** The sample at index i (0-based) of the whole tone. */
    public short sample(int i) {
        double phase = (i * frequency / SAMPLE_RATE) % 1.0;
        double v = phase < 0.5 ? 1 : -1;
        int fade = (int) (FADE_SECONDS * SAMPLE_RATE);
        if (fade > 0) {
            if (i < fade) v *= (double) i / fade;
            int fromEnd = totalSamples - 1 - i;
            if (fromEnd < fade) v *= (double) Math.max(0, fromEnd) / fade;
        }
        return (short) Math.round(v * AMPLITUDE);
    }

    /** Fills {@code out} with up to maxSamples next samples (little-endian), returns the number written. */
    public int next(java.nio.ByteBuffer out, int maxSamples) {
        int n = Math.min(maxSamples, remaining());
        for (int k = 0; k < n; k++) {
            short s = sample(position++);
            out.put((byte) (s & 0xFF));
            out.put((byte) ((s >> 8) & 0xFF));
        }
        return n;
    }
}
