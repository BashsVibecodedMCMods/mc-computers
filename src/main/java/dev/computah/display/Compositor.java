package dev.computah.display;

/**
 * Turns a {@link DisplayBuffer} into an image: the pixel layer scaled to the text area, with the text layer drawn on
 * top. The image is cols*8 by rows*16 ARGB pixels. Pure Java so it can be unit tested; the client uploads the result
 * to a texture.
 */
public final class Compositor {
    private Compositor() {
    }

    public static int width(DisplayBuffer b) {
        return b.cols() * Font.WIDTH;
    }

    public static int height(DisplayBuffer b) {
        return b.rows() * Font.HEIGHT;
    }

    /**
     * Renders into out (resized if needed) and returns it.
     *
     * @param blinkOn   whether blinking cells are currently visible.
     * @param monochrome the colour of "on" pixels on depth-1 screens.
     */
    public static int[] render(DisplayBuffer b, int[] out, boolean blinkOn, int monochrome) {
        synchronized (b) {
            int cols = b.cols(), rows = b.rows();
            int w = cols * Font.WIDTH, h = rows * Font.HEIGHT;
            if (out == null || out.length != w * h) out = new int[w * h];
            int depth = b.depth();
            int[] palette = b.paletteCopy();
            int pw = b.pixelWidth(), ph = b.pixelHeight();
            int maxIndex = b.tier().pixelColors() - 1;
            int[] pixelColor = new int[256];
            for (int i = 0; i < 256; i++) {
                int c = Palette.color(palette, Math.min(i, maxIndex));
                pixelColor[i] = 0xFF000000 | (depth == 1 ? Palette.reduce(palette, c, 1, monochrome) : c);
            }
            for (int y = 0; y < h; y++) {
                int py = Math.min(ph - 1, y * ph / h);
                for (int x = 0; x < w; x++) {
                    int px = Math.min(pw - 1, x * pw / w);
                    out[y * w + x] = pixelColor[b.pixel(px, py)];
                }
            }
            for (int cy = 0; cy < rows; cy++) {
                for (int cx = 0; cx < cols; cx++) {
                    int s = b.styleAt(cx, cy);
                    int f = Palette.reduce(palette, b.fgAt(cx, cy), depth, monochrome);
                    int bg = Palette.reduce(palette, b.bgAt(cx, cy), depth, monochrome);
                    if ((s & DisplayBuffer.STYLE_INVERSE) != 0) {
                        int t = f;
                        f = bg == Palette.TRANSPARENT ? 0 : bg;
                        bg = t;
                    }
                    boolean hidden = (s & DisplayBuffer.STYLE_BLINK) != 0 && !blinkOn;
                    int cp = b.charAt(cx, cy);
                    byte[] g = Font.glyph(cp);
                    int ox = cx * Font.WIDTH, oy = cy * Font.HEIGHT;
                    for (int gy = 0; gy < Font.HEIGHT; gy++) {
                        int row = g[gy] & 0xFF;
                        if ((s & DisplayBuffer.STYLE_BOLD) != 0) row |= row >>> 1;
                        if ((s & DisplayBuffer.STYLE_ITALIC) != 0) {
                            int shift = (Font.HEIGHT - 1 - gy) / 5 - 1;
                            row = shift > 0 ? row >>> shift : shift < 0 ? (row << -shift) & 0xFF : row;
                        }
                        if ((s & DisplayBuffer.STYLE_UNDERLINE) != 0 && gy == 14) row = 0xFF;
                        if ((s & DisplayBuffer.STYLE_STRIKE) != 0 && gy == 8) row = 0xFF;
                        if (hidden) row = 0;
                        int base = (oy + gy) * w + ox;
                        for (int gx = 0; gx < Font.WIDTH; gx++) {
                            if ((row & (0x80 >>> gx)) != 0) out[base + gx] = 0xFF000000 | f;
                            else if (bg != Palette.TRANSPARENT) out[base + gx] = 0xFF000000 | bg;
                        }
                    }
                }
            }
            return out;
        }
    }
}
