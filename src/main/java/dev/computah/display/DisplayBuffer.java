package dev.computah.display;

import java.io.*;
import java.util.Arrays;

/**
 * What a screen shows: a text layer (a grid of cells with a character, foreground, background and style) drawn over
 * a palette-indexed pixel layer. A cell whose background is {@link Palette#TRANSPARENT} lets the pixels show through.
 * <p>
 * The server and client both hold one. Every change is an operation; the server applies it and, while
 * {@link #startRecording() recording}, also writes it to a log that is sent to clients, who apply the same
 * operations with {@link #applyLog(byte[])}. Coordinates here are 0-based; the GPU component converts from Lua's
 * 1-based ones. All methods are synchronized.
 */
public final class DisplayBuffer {
    public static final int STYLE_BOLD = 1, STYLE_ITALIC = 2, STYLE_UNDERLINE = 4, STYLE_STRIKE = 8, STYLE_INVERSE = 16, STYLE_BLINK = 32;

    private static final int OP_SET = 1, OP_FILL = 2, OP_COPY = 3, OP_RES = 4, OP_DEPTH = 5, OP_PALETTE = 6, OP_CELLS = 7,
            OP_PSET = 10, OP_PFILL = 11, OP_PLINE = 12, OP_PRECT = 13, OP_PCIRCLE = 14, OP_PTEXT = 15, OP_PBLIT = 16,
            OP_PCOPY = 17, OP_PCLEAR = 18, OP_SNAPSHOT = 20;

    /** Screen and GPU capabilities by tier (1-3). */
    public record Tier(int tier, int cols, int rows, int depth, int pixelWidth, int pixelHeight, int pixelColors) {
        public static final Tier[] TIERS = {
                new Tier(1, 50, 16, 1, 160, 100, 16),
                new Tier(2, 80, 25, 4, 320, 200, 256),
                new Tier(3, 160, 50, 8, 640, 400, 256),
        };

        public static Tier of(int tier) {
            return TIERS[Math.max(1, Math.min(3, tier)) - 1];
        }

        public static Tier min(Tier a, Tier b) {
            return a.tier <= b.tier ? a : b;
        }
    }

    private Tier tier;
    private int cols, rows, depth;
    private final int[] palette = Palette.DEFAULTS.clone();
    private int[] chars, fg, bg;
    private byte[] style;
    private int pixelWidth, pixelHeight;
    private byte[] pixels;
    private int version;

    private ByteArrayOutputStream logBytes;
    private DataOutputStream log;

    public DisplayBuffer(Tier tier) {
        this.tier = tier;
        resetTo(tier);
    }

    /** An off-screen buffer of the given text size, with a proportional pixel area. */
    public DisplayBuffer(Tier tier, int cols, int rows) {
        this.tier = tier;
        this.cols = Math.max(1, cols);
        this.rows = Math.max(1, rows);
        this.depth = tier.depth();
        this.pixelWidth = Math.max(1, Math.round((float) this.cols * tier.pixelWidth() / tier.cols()));
        this.pixelHeight = Math.max(1, Math.round((float) this.rows * tier.pixelHeight() / tier.rows()));
        allocate();
    }

    private void resetTo(Tier t) {
        tier = t;
        cols = t.cols();
        rows = t.rows();
        depth = t.depth();
        pixelWidth = t.pixelWidth();
        pixelHeight = t.pixelHeight();
        allocate();
    }

    private void allocate() {
        int n = cols * rows;
        chars = new int[n];
        fg = new int[n];
        bg = new int[n];
        style = new byte[n];
        Arrays.fill(chars, ' ');
        Arrays.fill(fg, 0xFFFFFF);
        Arrays.fill(bg, Palette.TRANSPARENT);
        pixels = new byte[pixelWidth * pixelHeight];
        version++;
    }

    // region Accessors

    public synchronized Tier tier() {
        return tier;
    }

    public synchronized int cols() {
        return cols;
    }

    public synchronized int rows() {
        return rows;
    }

    public synchronized int depth() {
        return depth;
    }

    public synchronized int pixelWidth() {
        return pixelWidth;
    }

    public synchronized int pixelHeight() {
        return pixelHeight;
    }

    /** Increases on every change; renderers compare it to know when to redraw. */
    public synchronized int version() {
        return version;
    }

    public synchronized int paletteColor(int i) {
        return palette[i & 15];
    }

    public synchronized int[] paletteCopy() {
        return palette.clone();
    }

    public synchronized int charAt(int x, int y) {
        return inText(x, y) ? chars[y * cols + x] : ' ';
    }

    public synchronized int fgAt(int x, int y) {
        return inText(x, y) ? fg[y * cols + x] : 0;
    }

    public synchronized int bgAt(int x, int y) {
        return inText(x, y) ? bg[y * cols + x] : Palette.TRANSPARENT;
    }

    public synchronized int styleAt(int x, int y) {
        return inText(x, y) ? style[y * cols + x] : 0;
    }

    public synchronized int pixel(int x, int y) {
        return inPixels(x, y) ? pixels[y * pixelWidth + x] & 0xFF : 0;
    }

    /** Whether any cell blinks (the renderer then redraws periodically). */
    public synchronized boolean hasBlink() {
        for (byte s : style) if ((s & STYLE_BLINK) != 0) return true;
        return false;
    }

    private boolean inText(int x, int y) {
        return x >= 0 && y >= 0 && x < cols && y < rows;
    }

    private boolean inPixels(int x, int y) {
        return x >= 0 && y >= 0 && x < pixelWidth && y < pixelHeight;
    }

    private int clampColor(int c) {
        return c & 0xFF;
    }

    // endregion

    // region Recording

    /** Starts writing operations to the log (server side). */
    public synchronized void startRecording() {
        if (log == null) {
            logBytes = new ByteArrayOutputStream();
            log = new DataOutputStream(logBytes);
        }
    }

    /** Returns and clears the operations recorded since the last call, or null if there are none. */
    public synchronized byte[] takeLog() {
        if (logBytes == null || logBytes.size() == 0) return null;
        byte[] out = logBytes.toByteArray();
        logBytes.reset();
        return out;
    }

    /** Size of the pending log in bytes. */
    public synchronized int logSize() {
        return logBytes == null ? 0 : logBytes.size();
    }

    /** Drops pending operations (used when a full snapshot is sent instead). */
    public synchronized void clearLog() {
        if (logBytes != null) logBytes.reset();
    }

    private void rec(int... values) {
        if (log == null) return;
        try {
            for (int v : values) log.writeInt(v);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void recByte(int op) {
        if (log == null) return;
        try {
            log.writeByte(op);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void recBytes(byte[] b) {
        if (log == null) return;
        try {
            log.writeInt(b.length);
            log.write(b);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // endregion

    // region Text operations

    /** Writes code points starting at (x, y), horizontally or vertically. */
    public synchronized void set(int x, int y, int[] text, boolean vertical, int f, int b, int s) {
        for (int i = 0; i < text.length; i++) {
            int cx = vertical ? x : x + i, cy = vertical ? y + i : y;
            if (inText(cx, cy)) {
                int k = cy * cols + cx;
                chars[k] = text[i];
                fg[k] = f;
                bg[k] = b;
                style[k] = (byte) s;
            }
        }
        version++;
        recByte(OP_SET);
        rec(x, y, vertical ? 1 : 0, f, b, s, text.length);
        rec(text);
    }

    public synchronized void fill(int x, int y, int w, int h, int cp, int f, int b, int s) {
        int x0 = Math.max(0, x), y0 = Math.max(0, y), x1 = Math.min(cols, x + w), y1 = Math.min(rows, y + h);
        for (int yy = y0; yy < y1; yy++) {
            for (int xx = x0; xx < x1; xx++) {
                int k = yy * cols + xx;
                chars[k] = cp;
                fg[k] = f;
                bg[k] = b;
                style[k] = (byte) s;
            }
        }
        version++;
        recByte(OP_FILL);
        rec(x, y, w, h, cp, f, b, s);
    }

    public synchronized void copy(int x, int y, int w, int h, int dx, int dy) {
        if (w <= 0 || h <= 0) return;
        int n = cols * rows;
        int[] c2 = chars.clone(), f2 = fg.clone(), b2 = bg.clone();
        byte[] s2 = style.clone();
        for (int yy = y; yy < y + h; yy++) {
            for (int xx = x; xx < x + w; xx++) {
                int tx = xx + dx, ty = yy + dy;
                if (!inText(xx, yy) || !inText(tx, ty)) continue;
                int from = yy * cols + xx, to = ty * cols + tx;
                if (to >= n) continue;
                chars[to] = c2[from];
                fg[to] = f2[from];
                bg[to] = b2[from];
                style[to] = s2[from];
            }
        }
        version++;
        recByte(OP_COPY);
        rec(x, y, w, h, dx, dy);
    }

    /** Writes a block of cells (from another buffer). */
    public synchronized void cells(int x, int y, int w, int h, int[] c, int[] f, int[] b, byte[] s) {
        for (int yy = 0; yy < h; yy++) {
            for (int xx = 0; xx < w; xx++) {
                int tx = x + xx, ty = y + yy;
                if (!inText(tx, ty)) continue;
                int from = yy * w + xx, to = ty * cols + tx;
                chars[to] = c[from];
                fg[to] = f[from];
                bg[to] = b[from];
                style[to] = s[from];
            }
        }
        version++;
        if (log != null) {
            recByte(OP_CELLS);
            rec(x, y, w, h);
            rec(c);
            rec(f);
            rec(b);
            recBytes(s);
        }
    }

    /** Copies a block of this buffer's cells into arrays, for {@link #cells}. */
    public synchronized Object[] readCells(int x, int y, int w, int h) {
        int[] c = new int[w * h], f = new int[w * h], b = new int[w * h];
        byte[] s = new byte[w * h];
        for (int yy = 0; yy < h; yy++) {
            for (int xx = 0; xx < w; xx++) {
                int i = yy * w + xx, sx = x + xx, sy = y + yy;
                if (inText(sx, sy)) {
                    int k = sy * cols + sx;
                    c[i] = chars[k];
                    f[i] = fg[k];
                    b[i] = bg[k];
                    s[i] = style[k];
                } else {
                    c[i] = ' ';
                    b[i] = Palette.TRANSPARENT;
                }
            }
        }
        return new Object[]{c, f, b, s};
    }

    /** Changes the text resolution (up to the tier's maximum). Contents in the kept area remain. */
    public synchronized void setResolution(int w, int h) {
        w = Math.max(1, Math.min(tier.cols(), w));
        h = Math.max(1, Math.min(tier.rows(), h));
        if (w != cols || h != rows) {
            int[] c = new int[w * h], f = new int[w * h], b = new int[w * h];
            byte[] s = new byte[w * h];
            Arrays.fill(c, ' ');
            Arrays.fill(f, 0xFFFFFF);
            Arrays.fill(b, Palette.TRANSPARENT);
            for (int y = 0; y < Math.min(h, rows); y++) {
                for (int x = 0; x < Math.min(w, cols); x++) {
                    c[y * w + x] = chars[y * cols + x];
                    f[y * w + x] = fg[y * cols + x];
                    b[y * w + x] = bg[y * cols + x];
                    s[y * w + x] = style[y * cols + x];
                }
            }
            cols = w;
            rows = h;
            chars = c;
            fg = f;
            bg = b;
            style = s;
        }
        version++;
        recByte(OP_RES);
        rec(w, h);
    }

    public synchronized void setDepth(int d) {
        depth = d <= 1 ? 1 : d <= 4 ? Math.min(4, tier.depth()) : tier.depth();
        version++;
        recByte(OP_DEPTH);
        rec(depth);
    }

    public synchronized void setPaletteColor(int index, int rgb) {
        palette[index & 15] = rgb & 0xFFFFFF;
        version++;
        recByte(OP_PALETTE);
        rec(index & 15, rgb & 0xFFFFFF);
    }

    // endregion

    // region Pixel operations

    private void plot(int x, int y, int c) {
        if (inPixels(x, y)) pixels[y * pixelWidth + x] = (byte) c;
    }

    public synchronized void setPixel(int x, int y, int c) {
        plot(x, y, clampColor(c));
        version++;
        recByte(OP_PSET);
        rec(x, y, c);
    }

    public synchronized void fillRect(int x, int y, int w, int h, int c) {
        int x0 = Math.max(0, x), y0 = Math.max(0, y), x1 = Math.min(pixelWidth, x + w), y1 = Math.min(pixelHeight, y + h);
        for (int yy = y0; yy < y1; yy++) Arrays.fill(pixels, yy * pixelWidth + x0, yy * pixelWidth + Math.max(x0, x1), (byte) clampColor(c));
        version++;
        recByte(OP_PFILL);
        rec(x, y, w, h, c);
    }

    public synchronized void drawLine(int x0, int y0, int x1, int y1, int c) {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx + dy;
        int x = x0, y = y0, guard = 0;
        while (guard++ < 10_000) {
            plot(x, y, clampColor(c));
            if (x == x1 && y == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y += sy;
            }
        }
        version++;
        recByte(OP_PLINE);
        rec(x0, y0, x1, y1, c);
    }

    public synchronized void drawRect(int x, int y, int w, int h, int c) {
        if (w > 0 && h > 0) {
            int cc = clampColor(c);
            for (int i = 0; i < w; i++) {
                plot(x + i, y, cc);
                plot(x + i, y + h - 1, cc);
            }
            for (int i = 0; i < h; i++) {
                plot(x, y + i, cc);
                plot(x + w - 1, y + i, cc);
            }
        }
        version++;
        recByte(OP_PRECT);
        rec(x, y, w, h, c);
    }

    public synchronized void drawCircle(int cx, int cy, int r, int c, boolean filled) {
        int cc = clampColor(c);
        r = Math.max(0, Math.min(2048, r));
        for (int y = -r; y <= r; y++) {
            int half = (int) Math.floor(Math.sqrt((double) r * r - (double) y * y) + 0.5);
            if (filled) {
                for (int x = -half; x <= half; x++) plot(cx + x, cy + y, cc);
            } else {
                plot(cx - half, cy + y, cc);
                plot(cx + half, cy + y, cc);
            }
        }
        if (!filled) {
            for (int x = -r; x <= r; x++) {
                int half = (int) Math.floor(Math.sqrt((double) r * r - (double) x * x) + 0.5);
                plot(cx + x, cy - half, cc);
                plot(cx + x, cy + half, cc);
            }
        }
        version++;
        recByte(OP_PCIRCLE);
        rec(cx, cy, r, c, filled ? 1 : 0);
    }

    /** Draws text into the pixel layer with the screen font, scaled by an integer factor. Transparent background. */
    public synchronized void drawText(int x, int y, int[] text, int c, int scale) {
        int cc = clampColor(c);
        scale = Math.max(1, Math.min(8, scale));
        for (int i = 0; i < text.length; i++) {
            byte[] g = Font.glyph(text[i]);
            for (int gy = 0; gy < Font.HEIGHT; gy++) {
                for (int gx = 0; gx < Font.WIDTH; gx++) {
                    if ((g[gy] & (0x80 >>> gx)) == 0) continue;
                    for (int sy = 0; sy < scale; sy++) {
                        for (int sx = 0; sx < scale; sx++) {
                            plot(x + (i * Font.WIDTH + gx) * scale + sx, y + gy * scale + sy, cc);
                        }
                    }
                }
            }
        }
        version++;
        recByte(OP_PTEXT);
        rec(x, y, c, scale, text.length);
        rec(text);
    }

    /** Writes a w*h block of palette indices (row-major). */
    public synchronized void blit(int x, int y, int w, int h, byte[] data) {
        for (int yy = 0; yy < h; yy++) {
            for (int xx = 0; xx < w; xx++) {
                int i = yy * w + xx;
                if (i < data.length) plot(x + xx, y + yy, data[i] & 0xFF);
            }
        }
        version++;
        if (log != null) {
            recByte(OP_PBLIT);
            rec(x, y, w, h);
            recBytes(data);
        }
    }

    public synchronized byte[] readPixels(int x, int y, int w, int h) {
        byte[] out = new byte[Math.max(0, w) * Math.max(0, h)];
        for (int yy = 0; yy < h; yy++) {
            for (int xx = 0; xx < w; xx++) {
                out[yy * w + xx] = (byte) pixel(x + xx, y + yy);
            }
        }
        return out;
    }

    public synchronized void copyPixels(int x, int y, int w, int h, int dx, int dy) {
        byte[] block = readPixels(x, y, w, h);
        for (int yy = 0; yy < h; yy++) {
            for (int xx = 0; xx < w; xx++) {
                if (inPixels(x + xx, y + yy)) plot(x + xx + dx, y + yy + dy, block[yy * w + xx] & 0xFF);
            }
        }
        version++;
        recByte(OP_PCOPY);
        rec(x, y, w, h, dx, dy);
    }

    public synchronized void clearPixels(int c) {
        Arrays.fill(pixels, (byte) clampColor(c));
        version++;
        recByte(OP_PCLEAR);
        rec(c);
    }

    // endregion

    // region Snapshot and replay

    /** The complete state, for players who start watching. */
    public synchronized byte[] snapshot() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(OP_SNAPSHOT);
            out.writeInt(tier.tier());
            out.writeInt(cols);
            out.writeInt(rows);
            out.writeInt(depth);
            for (int p : palette) out.writeInt(p);
            for (int i = 0; i < cols * rows; i++) {
                out.writeInt(chars[i]);
                out.writeInt(fg[i]);
                out.writeInt(bg[i]);
                out.writeByte(style[i]);
            }
            out.writeInt(pixelWidth);
            out.writeInt(pixelHeight);
            out.write(pixels);
            out.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int[] readInts(DataInputStream in, int n) throws IOException {
        if (n < 0 || n > 1 << 20) throw new IOException("bad length");
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = in.readInt();
        return out;
    }

    private static byte[] readBytes(DataInputStream in) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > 1 << 22) throw new IOException("bad length");
        byte[] b = new byte[n];
        in.readFully(b);
        return b;
    }

    /** Applies operations (or a snapshot) recorded by another buffer. */
    public synchronized void applyLog(byte[] data) throws IOException {
        DataOutputStream saved = log;
        log = null;
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
            while (in.available() > 0) {
                int op = in.readUnsignedByte();
                switch (op) {
                    case OP_SET -> {
                        int x = in.readInt(), y = in.readInt(), v = in.readInt(), f = in.readInt(), b = in.readInt(), s = in.readInt();
                        set(x, y, readInts(in, in.readInt()), v != 0, f, b, s);
                    }
                    case OP_FILL -> fill(in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt());
                    case OP_COPY -> copy(in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt());
                    case OP_RES -> setResolution(in.readInt(), in.readInt());
                    case OP_DEPTH -> setDepth(in.readInt());
                    case OP_PALETTE -> setPaletteColor(in.readInt(), in.readInt());
                    case OP_CELLS -> {
                        int x = in.readInt(), y = in.readInt(), w = in.readInt(), h = in.readInt();
                        int[] c = readInts(in, w * h), f = readInts(in, w * h), b = readInts(in, w * h);
                        cells(x, y, w, h, c, f, b, readBytes(in));
                    }
                    case OP_PSET -> setPixel(in.readInt(), in.readInt(), in.readInt());
                    case OP_PFILL -> fillRect(in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt());
                    case OP_PLINE -> drawLine(in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt());
                    case OP_PRECT -> drawRect(in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt());
                    case OP_PCIRCLE -> drawCircle(in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt() != 0);
                    case OP_PTEXT -> {
                        int x = in.readInt(), y = in.readInt(), c = in.readInt(), s = in.readInt();
                        drawText(x, y, readInts(in, in.readInt()), c, s);
                    }
                    case OP_PBLIT -> {
                        int x = in.readInt(), y = in.readInt(), w = in.readInt(), h = in.readInt();
                        blit(x, y, w, h, readBytes(in));
                    }
                    case OP_PCOPY -> copyPixels(in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt());
                    case OP_PCLEAR -> clearPixels(in.readInt());
                    case OP_SNAPSHOT -> {
                        resetTo(Tier.of(in.readInt()));
                        int w = in.readInt(), h = in.readInt();
                        cols = Math.max(1, Math.min(tier.cols(), w));
                        rows = Math.max(1, Math.min(tier.rows(), h));
                        if (cols != w || rows != h) throw new IOException("bad snapshot");
                        allocate();
                        depth = in.readInt();
                        for (int i = 0; i < 16; i++) palette[i] = in.readInt();
                        for (int i = 0; i < cols * rows; i++) {
                            chars[i] = in.readInt();
                            fg[i] = in.readInt();
                            bg[i] = in.readInt();
                            style[i] = in.readByte();
                        }
                        int pw = in.readInt(), ph = in.readInt();
                        if (pw != pixelWidth || ph != pixelHeight) throw new IOException("bad snapshot");
                        in.readFully(pixels);
                        version++;
                    }
                    default -> throw new IOException("unknown display op " + op);
                }
            }
        } finally {
            log = saved;
        }
    }

    /** Resets everything to the tier's defaults (e.g. when the screen's tier or multiblock shape changes). */
    public synchronized void reset(Tier t) {
        resetTo(t);
        System.arraycopy(Palette.DEFAULTS, 0, palette, 0, 16);
        if (log != null) {
            logBytes.reset();
            byte[] snap = snapshot();
            try {
                log.write(snap);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    // endregion
}
