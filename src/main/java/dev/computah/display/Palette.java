package dev.computah.display;

/**
 * Colours. Indices 0-15 are the adjustable palette (OpenComputers-like defaults); 16-255 are a fixed 6x8x5 RGB cube.
 * Text colours are 24-bit RGB values, reduced to the screen's depth when drawn.
 */
public final class Palette {
    public static final int TRANSPARENT = -1;
    public static final int[] DEFAULTS = {
            0x000000, 0x333333, 0x4C4C4C, 0x666666, 0x808080, 0x999999, 0xB3B3B3, 0xCCCCCC,
            0xE6E6E6, 0xFFFFFF, 0xCC4C4C, 0x57A64E, 0x3366CC, 0xF2B233, 0xB266E5, 0x4C99B2,
    };
    private static final int[] CUBE = new int[240];

    static {
        int i = 0;
        for (int r = 0; r < 6; r++) {
            for (int g = 0; g < 8; g++) {
                for (int b = 0; b < 5; b++) {
                    CUBE[i++] = (r * 255 / 5) << 16 | (g * 255 / 7) << 8 | (b * 255 / 4);
                }
            }
        }
    }

    private Palette() {
    }

    /** Colour of a pixel-layer index for the given adjustable palette. */
    public static int color(int[] palette, int index) {
        index &= 0xFF;
        return index < 16 ? palette[index] : CUBE[index - 16];
    }

    private static int dist(int a, int b) {
        int dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF);
        int dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF);
        int db = (a & 0xFF) - (b & 0xFF);
        return dr * dr * 2 + dg * dg * 4 + db * db * 3;
    }

    /** The nearest palette index for an RGB colour; limited to 16 entries when maxIndex is 15. */
    public static int nearest(int[] palette, int rgb, int maxIndex) {
        int best = 0, bestD = Integer.MAX_VALUE;
        for (int i = 0; i <= maxIndex; i++) {
            int d = dist(color(palette, i), rgb);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    /** Reduces a text colour to what a screen of this depth can show. */
    public static int reduce(int[] palette, int rgb, int depth, int monochrome) {
        if (rgb == TRANSPARENT) return rgb;
        return switch (depth) {
            case 1 -> {
                int lum = ((rgb >> 16) & 0xFF) * 3 + ((rgb >> 8) & 0xFF) * 6 + (rgb & 0xFF);
                yield lum > 1270 ? monochrome : 0;
            }
            case 4 -> color(palette, nearest(palette, rgb, 15));
            default -> rgb;
        };
    }
}
