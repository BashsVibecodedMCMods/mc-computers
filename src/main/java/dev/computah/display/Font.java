package dev.computah.display;

import dev.computah.Computah;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * The unscii screen font (public domain, by viznut, expanded by asie; the file comes from OpenComputers). Glyphs are
 * 8x16; 16-pixel-wide glyphs are squeezed to 8 columns. Used by the server (for pixel text, so getPixel answers
 * correctly) and the client (for rendering).
 */
public final class Font {
    public static final int WIDTH = 8, HEIGHT = 16;
    private static final byte[] MISSING = new byte[HEIGHT];
    private static volatile Map<Integer, byte[]> glyphs;

    static {
        for (int i = 2; i < 14; i++) MISSING[i] = (byte) (i == 2 || i == 13 ? 0x7E : 0x42);
    }

    private Font() {
    }

    private static Map<Integer, byte[]> load() {
        Map<Integer, byte[]> map = new HashMap<>(80_000);
        try (InputStream in = Font.class.getResourceAsStream("/assets/computah/font.hex")) {
            if (in == null) {
                Computah.LOG.warn("font.hex is missing; screen text will not render");
                return map;
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.US_ASCII));
            String line;
            while ((line = r.readLine()) != null) {
                int colon = line.indexOf(':');
                if (colon <= 0) continue;
                int cp = Integer.parseInt(line.substring(0, colon), 16);
                String hex = line.substring(colon + 1);
                byte[] rows = new byte[HEIGHT];
                if (hex.length() == 32) {
                    for (int y = 0; y < HEIGHT; y++) rows[y] = (byte) Integer.parseInt(hex.substring(y * 2, y * 2 + 2), 16);
                } else if (hex.length() == 64) {
                    for (int y = 0; y < HEIGHT; y++) {
                        int wide = Integer.parseInt(hex.substring(y * 4, y * 4 + 4), 16);
                        int narrow = 0;
                        for (int x = 0; x < 8; x++) {
                            if ((wide & (0xC000 >>> (x * 2))) != 0) narrow |= 0x80 >>> x;
                        }
                        rows[y] = (byte) narrow;
                    }
                } else {
                    continue;
                }
                map.put(cp, rows);
            }
        } catch (Exception e) {
            Computah.LOG.error("Failed to load font.hex", e);
        }
        return map;
    }

    /** The 16 rows of a glyph; bit 7 is the leftmost pixel. */
    public static byte[] glyph(int codePoint) {
        Map<Integer, byte[]> g = glyphs;
        if (g == null) {
            synchronized (Font.class) {
                if (glyphs == null) glyphs = load();
                g = glyphs;
            }
        }
        byte[] rows = g.get(codePoint);
        if (rows != null) return rows;
        return codePoint == ' ' || codePoint == 0 ? new byte[HEIGHT] : MISSING;
    }
}
