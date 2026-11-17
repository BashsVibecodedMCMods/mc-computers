package dev.computah.component;

import dev.computah.api.*;
import dev.computah.display.DisplayBuffer;
import dev.computah.display.Palette;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * The {@code gpu} component (graphics card). It draws on a bound screen, or on off-screen buffers, through two
 * layers: text cells (character, foreground, background, style) and a palette-indexed pixel layer under them.
 * Lua coordinates are 1-based. Drawing costs come out of a per-tick budget that grows with the card's tier; when it
 * runs out the call waits for the next tick. See docs/GRAPHICS.md.
 */
public class GpuComponent extends Component implements PersistentComponent {
    private static final double[] BUDGET = {0, 96, 192, 384};

    private final String address;
    private final DisplayBuffer.Tier tier;
    private volatile @Nullable String screenAddress;
    private volatile @Nullable ScreenComponent screen;
    private int foreground = 0xFFFFFF, background = 0x000000, style;
    private int foregroundIndex = -1, backgroundIndex = -1;
    private final Map<Integer, DisplayBuffer> buffers = new HashMap<>();
    private int nextBuffer = 1;
    private int activeBuffer;
    private long budgetTick = -1;
    private double budget;

    public GpuComponent(String address, int tier) {
        this.address = address;
        this.tier = DisplayBuffer.Tier.of(tier);
    }

    @Override
    public String address() {
        return address;
    }

    @Override
    public String type() {
        return "gpu";
    }

    public DisplayBuffer.Tier tier() {
        return tier;
    }

    /** The memory available for off-screen buffers, in bytes. */
    public long totalVram() {
        return switch (tier.tier()) {
            case 1 -> 64 * 1024;
            case 2 -> 384 * 1024;
            default -> 1536 * 1024;
        };
    }

    private static long cost(DisplayBuffer b) {
        return (long) b.cols() * b.rows() * 13 + (long) b.pixelWidth() * b.pixelHeight();
    }

    private synchronized long usedVram() {
        long used = 0;
        for (DisplayBuffer b : buffers.values()) used += cost(b);
        return used;
    }

    @Override
    public void onDisconnect(dev.computah.machine.Machine machine) {
        if (machines().isEmpty()) {
            synchronized (this) {
                buffers.clear();
                activeBuffer = 0;
            }
        }
    }

    // region Helpers

    private void spend(Context ctx, double amount) {
        if (ctx.synchronous()) return;
        synchronized (this) {
            long tick = Math.round(ctx.machine().uptime() * 20);
            if (tick != budgetTick) {
                budgetTick = tick;
                budget = BUDGET[tier.tier()];
            }
            if (budget < amount && budget < BUDGET[tier.tier()]) throw LimitReachedException.INSTANCE;
            budget -= amount;
        }
    }

    private synchronized DisplayBuffer target(Context ctx) throws ComponentException {
        if (activeBuffer != 0) {
            DisplayBuffer b = buffers.get(activeBuffer);
            if (b == null) throw new ComponentException("invalid buffer index");
            return b;
        }
        ScreenComponent s = screen;
        if (s == null && screenAddress != null && ctx.machine().component(screenAddress) instanceof ScreenComponent found) {
            screen = s = found;
        }
        if (s == null || ctx.machine().component(s.address()) != s) throw new ComponentException("no screen");
        return s.buffer();
    }

    private DisplayBuffer.Tier effectiveTier(DisplayBuffer b) {
        return DisplayBuffer.Tier.min(tier, b.tier());
    }

    private static int[] codepoints(Arguments args, int i) throws ComponentException {
        return args.checkString(i).codePoints().toArray();
    }

    private int pixelColor(Arguments args, int i, DisplayBuffer b) throws ComponentException {
        int c = args.checkInteger(i);
        int max = Math.min(tier.pixelColors(), b.tier().pixelColors()) - 1;
        if (c < 0 || c > 255) throw new ComponentException("invalid color index");
        return Math.min(c, max);
    }

    private static Map<String, Object> styleTable(int s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("bold", (s & DisplayBuffer.STYLE_BOLD) != 0);
        m.put("italic", (s & DisplayBuffer.STYLE_ITALIC) != 0);
        m.put("underline", (s & DisplayBuffer.STYLE_UNDERLINE) != 0);
        m.put("strikethrough", (s & DisplayBuffer.STYLE_STRIKE) != 0);
        m.put("inverse", (s & DisplayBuffer.STYLE_INVERSE) != 0);
        m.put("blink", (s & DisplayBuffer.STYLE_BLINK) != 0);
        return m;
    }

    // endregion

    // region Screen binding

    @Callback(doc = "function(address:string[, reset:boolean=true]):boolean -- Binds to a screen. reset restores its default resolution and colours.")
    public Object[] bind(Context ctx, Arguments args) throws ComponentException {
        String addr = args.checkString(0);
        Component c = ctx.machine().component(addr);
        if (!(c instanceof ScreenComponent s)) return new Object[]{false, "not a screen"};
        boolean reset = args.optBoolean(1, true);
        synchronized (this) {
            screen = s;
            screenAddress = addr;
            activeBuffer = 0;
        }
        if (reset) {
            DisplayBuffer b = s.buffer();
            DisplayBuffer.Tier t = effectiveTier(b);
            b.setResolution(t.cols(), t.rows());
            b.setDepth(t.depth());
            synchronized (this) {
                foreground = 0xFFFFFF;
                background = 0x000000;
                foregroundIndex = backgroundIndex = -1;
                style = 0;
            }
        }
        return new Object[]{true};
    }

    @Callback(doc = "function():string -- The address of the bound screen, or nil.")
    public Object[] getScreen(Context ctx, Arguments args) {
        return new Object[]{screen == null ? null : screenAddress};
    }

    // endregion

    // region Colours and modes

    private Object[] setColor(Context ctx, Arguments args, boolean fore) throws ComponentException {
        int value = args.checkInteger(0);
        boolean isIndex = args.optBoolean(1, false);
        int rgb;
        if (value == Palette.TRANSPARENT && !isIndex) {
            if (fore) throw new ComponentException("the foreground can't be transparent");
            rgb = Palette.TRANSPARENT;
        } else if (isIndex) {
            if (value < 0 || value > 15) throw new ComponentException("invalid palette index");
            rgb = target(ctx).paletteColor(value);
        } else {
            rgb = value & 0xFFFFFF;
        }
        synchronized (this) {
            Object[] old = fore ? new Object[]{foreground, foregroundIndex >= 0 ? foregroundIndex : null}
                    : new Object[]{background, backgroundIndex >= 0 ? backgroundIndex : null};
            if (fore) {
                foreground = rgb;
                foregroundIndex = isIndex ? value : -1;
            } else {
                background = rgb;
                backgroundIndex = isIndex ? value : -1;
            }
            return old;
        }
    }

    @Callback(doc = "function(color:number[, isPaletteIndex:boolean]):number -- Sets the text foreground (0xRRGGBB or a palette index 0-15). Returns the old one.")
    public Object[] setForeground(Context ctx, Arguments args) throws ComponentException {
        return setColor(ctx, args, true);
    }

    @Callback(doc = "function(color:number[, isPaletteIndex:boolean]):number -- Sets the text background; -1 is transparent (shows the pixel layer). Returns the old one.")
    public Object[] setBackground(Context ctx, Arguments args) throws ComponentException {
        return setColor(ctx, args, false);
    }

    @Callback(doc = "function():number, number|nil -- The foreground colour, and its palette index if it was set from one.")
    public synchronized Object[] getForeground(Context ctx, Arguments args) {
        return new Object[]{foreground, foregroundIndex >= 0 ? foregroundIndex : null};
    }

    @Callback(doc = "function():number, number|nil -- The background colour (-1 = transparent), and its palette index if it was set from one.")
    public synchronized Object[] getBackground(Context ctx, Arguments args) {
        return new Object[]{background, backgroundIndex >= 0 ? backgroundIndex : null};
    }

    @Callback(doc = "function(style:table):table -- Sets the text style for following writes: {bold, italic, underline, strikethrough, inverse, blink}. Returns the old one.")
    public Object[] setStyle(Context ctx, Arguments args) throws ComponentException {
        Map<Object, Object> t = args.checkTable(0);
        int s = 0;
        if (Boolean.TRUE.equals(t.get("bold"))) s |= DisplayBuffer.STYLE_BOLD;
        if (Boolean.TRUE.equals(t.get("italic"))) s |= DisplayBuffer.STYLE_ITALIC;
        if (Boolean.TRUE.equals(t.get("underline"))) s |= DisplayBuffer.STYLE_UNDERLINE;
        if (Boolean.TRUE.equals(t.get("strikethrough"))) s |= DisplayBuffer.STYLE_STRIKE;
        if (Boolean.TRUE.equals(t.get("inverse"))) s |= DisplayBuffer.STYLE_INVERSE;
        if (Boolean.TRUE.equals(t.get("blink"))) s |= DisplayBuffer.STYLE_BLINK;
        synchronized (this) {
            Object[] old = new Object[]{styleTable(style)};
            style = s;
            return old;
        }
    }

    @Callback(doc = "function():table -- The current text style.")
    public synchronized Object[] getStyle(Context ctx, Arguments args) {
        return new Object[]{styleTable(style)};
    }

    @Callback(doc = "function(index:number):number -- A palette colour (0-15).")
    public Object[] getPaletteColor(Context ctx, Arguments args) throws ComponentException {
        int i = args.checkInteger(0);
        if (i < 0 || i > 15) throw new ComponentException("invalid palette index");
        return new Object[]{target(ctx).paletteColor(i)};
    }

    @Callback(doc = "function(index:number, color:number):number -- Changes a palette colour (0-15); also recolours pixels using it. Returns the old colour.")
    public Object[] setPaletteColor(Context ctx, Arguments args) throws ComponentException {
        int i = args.checkInteger(0);
        if (i < 0 || i > 15) throw new ComponentException("invalid palette index");
        DisplayBuffer b = target(ctx);
        spend(ctx, 2);
        int old = b.paletteColor(i);
        b.setPaletteColor(i, args.checkInteger(1));
        return new Object[]{old};
    }

    @Callback(doc = "function():number -- The highest colour depth (1, 4 or 8 bits) of this GPU and screen.")
    public Object[] maxDepth(Context ctx, Arguments args) throws ComponentException {
        return new Object[]{effectiveTier(target(ctx)).depth()};
    }

    @Callback(doc = "function():number -- The current colour depth.")
    public Object[] getDepth(Context ctx, Arguments args) throws ComponentException {
        return new Object[]{target(ctx).depth()};
    }

    @Callback(doc = "function(depth:number):string -- Sets the colour depth (1, 4 or 8). Returns the old one as a name.")
    public Object[] setDepth(Context ctx, Arguments args) throws ComponentException {
        int d = args.checkInteger(0);
        DisplayBuffer b = target(ctx);
        if (d != 1 && d != 4 && d != 8 || d > effectiveTier(b).depth()) throw new ComponentException("unsupported depth");
        int old = b.depth();
        b.setDepth(d);
        return new Object[]{old == 1 ? "OneBit" : old == 4 ? "FourBit" : "EightBit"};
    }

    @Callback(doc = "function():number, number -- The largest text resolution of this GPU and screen.")
    public Object[] maxResolution(Context ctx, Arguments args) throws ComponentException {
        DisplayBuffer.Tier t = effectiveTier(target(ctx));
        return new Object[]{t.cols(), t.rows()};
    }

    @Callback(doc = "function():number, number -- The current text resolution.")
    public Object[] getResolution(Context ctx, Arguments args) throws ComponentException {
        DisplayBuffer b = target(ctx);
        return new Object[]{b.cols(), b.rows()};
    }

    @Callback(doc = "function(width:number, height:number):boolean -- Sets the text resolution.")
    public Object[] setResolution(Context ctx, Arguments args) throws ComponentException {
        int w = args.checkInteger(0), h = args.checkInteger(1);
        DisplayBuffer b = target(ctx);
        DisplayBuffer.Tier t = effectiveTier(b);
        if (w < 1 || h < 1 || w > t.cols() || h > t.rows()) throw new ComponentException("unsupported resolution");
        boolean changed = w != b.cols() || h != b.rows();
        b.setResolution(w, h);
        if (changed && activeBuffer == 0 && screen != null) screen.signalAll("screen_resized", screen.address(), w, h);
        return new Object[]{changed};
    }

    @Callback(doc = "function():number, number -- The resolution that fills the bound monitor's shape without borders (most rows within maxResolution). Apply it with setResolution(getFitResolution()).")
    public Object[] getFitResolution(Context ctx, Arguments args) throws ComponentException {
        ScreenComponent s;
        synchronized (this) {
            s = screen;
        }
        if (s == null) throw new ComponentException("no screen");
        DisplayBuffer.Tier t = effectiveTier(s.buffer());
        int[] fit = dev.computah.display.ScreenGeometry.fitResolution(s.blocksWide(), s.blocksHigh(), t.cols(), t.rows());
        return new Object[]{fit[0], fit[1]};
    }

    @Callback(doc = "function():number, number -- Same as getResolution (kept for OpenComputers programs).")
    public Object[] getViewport(Context ctx, Arguments args) throws ComponentException {
        return getResolution(ctx, args);
    }

    @Callback(doc = "function(width:number, height:number):boolean -- Same as setResolution.")
    public Object[] setViewport(Context ctx, Arguments args) throws ComponentException {
        return setResolution(ctx, args);
    }

    // endregion

    // region Text

    @Callback(doc = "function(x:number, y:number):string, number, number, nil, nil, table -- The character, foreground, background and style at a cell.")
    public Object[] get(Context ctx, Arguments args) throws ComponentException {
        DisplayBuffer b = target(ctx);
        int x = args.checkInteger(0) - 1, y = args.checkInteger(1) - 1;
        if (x < 0 || y < 0 || x >= b.cols() || y >= b.rows()) throw new ComponentException("index out of bounds");
        String ch = new String(Character.toChars(b.charAt(x, y)));
        return new Object[]{ch, b.fgAt(x, y), b.bgAt(x, y), null, null, styleTable(b.styleAt(x, y))};
    }

    @Callback(doc = "function(x:number, y:number, value:string[, vertical:boolean]):boolean -- Writes text with the current colours and style.")
    public Object[] set(Context ctx, Arguments args) throws ComponentException {
        DisplayBuffer b = target(ctx);
        int[] text = codepoints(args, 2);
        spend(ctx, 1 + text.length / 64.0);
        int f, bg, s;
        synchronized (this) {
            f = foreground;
            bg = background;
            s = style;
        }
        b.set(args.checkInteger(0) - 1, args.checkInteger(1) - 1, text, args.optBoolean(3, false), f, bg, s);
        return new Object[]{true};
    }

    @Callback(doc = "function(x:number, y:number, width:number, height:number, char:string):boolean -- Fills a rectangle of cells.")
    public Object[] fill(Context ctx, Arguments args) throws ComponentException {
        DisplayBuffer b = target(ctx);
        int[] cp = codepoints(args, 4);
        if (cp.length != 1) throw new ComponentException("invalid fill value");
        int w = args.checkInteger(2), h = args.checkInteger(3);
        spend(ctx, 1 + Math.max(0, w) * (double) Math.max(0, h) / 512);
        int f, bg, s;
        synchronized (this) {
            f = foreground;
            bg = background;
            s = style;
        }
        b.fill(args.checkInteger(0) - 1, args.checkInteger(1) - 1, w, h, cp[0], f, bg, s);
        return new Object[]{true};
    }

    @Callback(doc = "function(x:number, y:number, width:number, height:number, tx:number, ty:number):boolean -- Copies a rectangle of cells by an offset.")
    public Object[] copy(Context ctx, Arguments args) throws ComponentException {
        DisplayBuffer b = target(ctx);
        int w = args.checkInteger(2), h = args.checkInteger(3);
        spend(ctx, 1 + Math.max(0, w) * (double) Math.max(0, h) / 512);
        b.copy(args.checkInteger(0) - 1, args.checkInteger(1) - 1, w, h, args.checkInteger(4), args.checkInteger(5));
        return new Object[]{true};
    }

    @Callback(doc = "function() -- Makes every cell an empty, transparent space so only the pixel layer shows.")
    public Object[] clearText(Context ctx, Arguments args) throws ComponentException {
        DisplayBuffer b = target(ctx);
        spend(ctx, 2);
        b.fill(0, 0, b.cols(), b.rows(), ' ', 0xFFFFFF, Palette.TRANSPARENT, 0);
        return new Object[0];
    }

    // endregion

    // region Pixels

    @Callback(doc = "function():number, number -- The size of the pixel layer.")
    public Object[] getPixelResolution(Context ctx, Arguments args) throws ComponentException {
        DisplayBuffer b = target(ctx);
        return new Object[]{b.pixelWidth(), b.pixelHeight()};
    }

    @Callback(doc = "function():number -- How many pixel colours are available (16 or 256).")
    public Object[] getPixelColors(Context ctx, Arguments args) throws ComponentException {
        return new Object[]{Math.min(tier.pixelColors(), target(ctx).tier().pixelColors())};
    }

    @Callback(doc = "function(index:number):number -- The RGB colour of a pixel colour index (0-15 palette, 16-255 fixed).")
    public Object[] getPixelColor(Context ctx, Arguments args) throws ComponentException {
        int i = args.checkInteger(0);
        if (i < 0 || i > 255) throw new ComponentException("invalid color index");
        return new Object[]{Palette.color(target(ctx).paletteCopy(), i)};
    }

    @Callback(doc = "function(rgb:number):number -- The pixel colour index nearest to an RGB colour.")
    public Object[] findPixelColor(Context ctx, Arguments args) throws ComponentException {
        DisplayBuffer b = target(ctx);
        int max = Math.min(tier.pixelColors(), b.tier().pixelColors()) - 1;
        return new Object[]{Palette.nearest(b.paletteCopy(), args.checkInteger(0) & 0xFFFFFF, max)};
    }

    @Callback(doc = "function(x:number, y:number, color:number) -- Sets one pixel to a colour index.")
    public Object[] setPixel(Context ctx, Arguments args) throws ComponentException {
        DisplayBuffer b = target(ctx);
        spend(ctx, 0.125);
        b.setPixel(args.checkInteger(0) - 1, args.checkInteger(1) - 1, pixelColor(args, 2, b));
        return new Object[0];
    }

    @Callback(doc = "function(x:number, y:number):number -- The colour index of a pixel.")
    public Object[] getPixel(Context ctx, Arguments args) throws ComponentException {
        DisplayBuffer b = target(ctx);
        int x = args.checkInteger(0) - 1, y = args.checkInteger(1) - 1;
        if (x < 0 || y < 0 || x >= b.pixelWidth() || y >= b.pixelHeight()) throw new ComponentException("index out of bounds");
        return new Object[]{b.pixel(x, y)};
    }

    @Callback(doc = "function(x:number, y:number, width:number, height:number, color:number) -- Fills a rectangle of pixels.")
    public Object[] fillRect(Context ctx, Arguments args) throws ComponentException {
        DisplayBuffer b = target(ctx);
        int w = args.checkInteger(2), h = args.checkInteger(3);
        spend(ctx, 0.5 + Math.max(0, w) * (double) Math.max(0, h) / 16384);
        b.fillRect(args.checkInteger(0) - 1, args.checkInteger(1) - 1, w, h, pixelColor(args, 4, b));
        return new Object[0];
    }

    @Callback(doc = "function(x1:number, y1:number, x2:number, y2:number, color:number) -- Draws a line.")
    public Object[] drawLine(Context ctx, Arguments args) throws ComponentException {
        DisplayBuffer b = target(ctx);
        spend(ctx, 0.5);
        b.drawLine(args.checkInteger(0) - 1, args.checkInteger(1) - 1, args.checkInteger(2) - 1, args.checkInteger(3) - 1, pixelColor(args, 4, b));
        return new Object[0];
    }

    @Callback(doc = "function(x:number, y:number, width:number, height:number, color:number) -- Draws a rectangle outline.")
    public Object[] drawRect(Context ctx, Arguments args) throws ComponentException {
        DisplayBuffer b = target(ctx);
        spend(ctx, 0.5);
        b.drawRect(args.checkInteger(0) - 1, args.checkInteger(1) - 1, args.checkInteger(2), args.checkInteger(3), pixelColor(args, 4, b));
        return new Object[0];
    }

    @Callback(doc = "function(x:number, y:number, radius:number, color:number[, filled:boolean]) -- Draws a circle.")
    public Object[] drawCircle(Context ctx, Arguments args) throws ComponentException {
        DisplayBuffer b = target(ctx);
        spend(ctx, 1);
        b.drawCircle(args.checkInteger(0) - 1, args.checkInteger(1) - 1, args.checkInteger(2), pixelColor(args, 3, b), args.optBoolean(4, false));
        return new Object[0];
    }

    @Callback(doc = "function(x:number, y:number, text:string, color:number[, scale:number=1]) -- Draws text into the pixel layer with the screen font (8x16 per character, times scale).")
    public Object[] drawText(Context ctx, Arguments args) throws ComponentException {
        DisplayBuffer b = target(ctx);
        int[] text = codepoints(args, 2);
        spend(ctx, 0.5 + text.length / 32.0);
        b.drawText(args.checkInteger(0) - 1, args.checkInteger(1) - 1, text, pixelColor(args, 3, b), args.optInteger(4, 1));
        return new Object[0];
    }

    @Callback(doc = "function(x:number, y:number, width:number, height:number, data:string) -- Draws a block of pixels; data holds one colour index byte per pixel, row by row.")
    public Object[] blit(Context ctx, Arguments args) throws ComponentException {
        DisplayBuffer b = target(ctx);
        int w = args.checkInteger(2), h = args.checkInteger(3);
        if (w < 0 || h < 0 || (long) w * h > 1 << 20) throw new ComponentException("invalid size");
        byte[] data = args.checkBytes(4);
        int max = Math.min(tier.pixelColors(), b.tier().pixelColors()) - 1;
        if (max < 255) for (int i = 0; i < data.length; i++) if ((data[i] & 0xFF) > max) data[i] = (byte) max;
        spend(ctx, 0.5 + w * (double) h / 4096);
        b.blit(args.checkInteger(0) - 1, args.checkInteger(1) - 1, w, h, data);
        return new Object[0];
    }

    @Callback(doc = "function(x:number, y:number, width:number, height:number):string -- Reads a block of pixels in the format blit takes.")
    public Object[] readPixels(Context ctx, Arguments args) throws ComponentException {
        DisplayBuffer b = target(ctx);
        int w = args.checkInteger(2), h = args.checkInteger(3);
        if (w < 0 || h < 0 || (long) w * h > 1 << 20) throw new ComponentException("invalid size");
        spend(ctx, 0.5 + w * (double) h / 8192);
        return new Object[]{b.readPixels(args.checkInteger(0) - 1, args.checkInteger(1) - 1, w, h)};
    }

    @Callback(doc = "function(x:number, y:number, width:number, height:number, tx:number, ty:number) -- Copies a block of pixels by an offset.")
    public Object[] copyPixels(Context ctx, Arguments args) throws ComponentException {
        DisplayBuffer b = target(ctx);
        int w = args.checkInteger(2), h = args.checkInteger(3);
        spend(ctx, 0.5 + Math.max(0, w) * (double) Math.max(0, h) / 16384);
        b.copyPixels(args.checkInteger(0) - 1, args.checkInteger(1) - 1, w, h, args.checkInteger(4), args.checkInteger(5));
        return new Object[0];
    }

    @Callback(doc = "function([color:number=0]) -- Sets every pixel to a colour index.")
    public Object[] clearPixels(Context ctx, Arguments args) throws ComponentException {
        DisplayBuffer b = target(ctx);
        spend(ctx, 1);
        b.clearPixels(args.isNil(0) ? 0 : pixelColor(args, 0, b));
        return new Object[0];
    }

    // endregion

    // region Buffers

    @Callback(doc = "function([width:number, height:number]):number -- Allocates an off-screen buffer (default: max resolution). Returns its index.")
    public Object[] allocateBuffer(Context ctx, Arguments args) throws ComponentException {
        int w = args.optInteger(0, tier.cols()), h = args.optInteger(1, tier.rows());
        if (w < 1 || h < 1 || w > tier.cols() * 2 || h > tier.rows() * 2) throw new ComponentException("invalid buffer size");
        DisplayBuffer b = new DisplayBuffer(tier, w, h);
        synchronized (this) {
            if (usedVram() + cost(b) > totalVram()) return new Object[]{null, "not enough video memory"};
            int id = nextBuffer++;
            buffers.put(id, b);
            return new Object[]{id};
        }
    }

    @Callback(doc = "function([index:number]):boolean -- Frees a buffer (default: the active one).")
    public synchronized Object[] freeBuffer(Context ctx, Arguments args) throws ComponentException {
        int id = args.optInteger(0, activeBuffer);
        if (id == 0 || buffers.remove(id) == null) return new Object[]{false};
        if (activeBuffer == id) activeBuffer = 0;
        return new Object[]{true};
    }

    @Callback(doc = "function() -- Frees every buffer.")
    public synchronized Object[] freeAllBuffers(Context ctx, Arguments args) {
        buffers.clear();
        activeBuffer = 0;
        return new Object[0];
    }

    @Callback(doc = "function():table -- Indices of all allocated buffers.")
    public synchronized Object[] buffers(Context ctx, Arguments args) {
        return new Object[]{new ArrayList<>(new TreeSet<>(buffers.keySet()))};
    }

    @Callback(doc = "function():number -- The active buffer (0 = the screen).")
    public synchronized Object[] getActiveBuffer(Context ctx, Arguments args) {
        return new Object[]{activeBuffer};
    }

    @Callback(doc = "function(index:number):number -- Makes drawing go to a buffer (0 = the screen). Returns the previous index.")
    public synchronized Object[] setActiveBuffer(Context ctx, Arguments args) throws ComponentException {
        int id = args.checkInteger(0);
        if (id != 0 && !buffers.containsKey(id)) return new Object[]{null, "invalid buffer index"};
        int old = activeBuffer;
        activeBuffer = id;
        return new Object[]{old};
    }

    @Callback(doc = "function(index:number):number, number -- The text size of a buffer.")
    public Object[] getBufferSize(Context ctx, Arguments args) throws ComponentException {
        DisplayBuffer b = resolve(args.optInteger(0, activeBuffer));
        return new Object[]{b.cols(), b.rows()};
    }

    @Callback(doc = "function():number -- Total video memory for buffers, in bytes.")
    public Object[] totalMemory(Context ctx, Arguments args) {
        return new Object[]{totalVram()};
    }

    @Callback(doc = "function():number -- Free video memory, in bytes.")
    public Object[] freeMemory(Context ctx, Arguments args) {
        return new Object[]{totalVram() - usedVram()};
    }

    private synchronized DisplayBuffer resolve(int id) throws ComponentException {
        if (id == 0) {
            ScreenComponent s = screen;
            if (s == null && screenAddress != null) {
                for (dev.computah.machine.Machine m : machines()) {
                    if (m.component(screenAddress) instanceof ScreenComponent found) screen = s = found;
                }
            }
            if (s == null) throw new ComponentException("no screen");
            return s.buffer();
        }
        DisplayBuffer b = buffers.get(id);
        if (b == null) throw new ComponentException("invalid buffer index");
        return b;
    }

    @Callback(doc = "function([dst:number=0, col:number=1, row:number=1, width, height, src:number=active, fromCol:number=1, fromRow:number=1]) -- Copies text cells between buffers (0 = the screen).")
    public Object[] bitblt(Context ctx, Arguments args) throws ComponentException {
        DisplayBuffer dst = resolve(args.optInteger(0, 0));
        DisplayBuffer src = resolve(args.optInteger(5, activeBuffer));
        int col = args.optInteger(1, 1) - 1, row = args.optInteger(2, 1) - 1;
        int w = args.optInteger(3, src.cols()), h = args.optInteger(4, src.rows());
        int fc = args.optInteger(6, 1) - 1, fr = args.optInteger(7, 1) - 1;
        if (w < 0 || h < 0 || (long) w * h > 1 << 16) throw new ComponentException("invalid size");
        spend(ctx, 1 + w * (double) h / 1024);
        Object[] cells = src.readCells(fc, fr, w, h);
        dst.cells(col, row, w, h, (int[]) cells[0], (int[]) cells[1], (int[]) cells[2], (byte[]) cells[3]);
        return new Object[]{true};
    }

    @Callback(doc = "function(dst:number, x:number, y:number, width:number, height:number, src:number[, fromX:number=1, fromY:number=1]) -- Copies pixels between buffers (0 = the screen).")
    public Object[] bitbltPixels(Context ctx, Arguments args) throws ComponentException {
        DisplayBuffer dst = resolve(args.checkInteger(0));
        DisplayBuffer src = resolve(args.checkInteger(5));
        int w = args.checkInteger(3), h = args.checkInteger(4);
        if (w < 0 || h < 0 || (long) w * h > 1 << 20) throw new ComponentException("invalid size");
        spend(ctx, 0.5 + w * (double) h / 4096);
        byte[] data = src.readPixels(args.optInteger(6, 1) - 1, args.optInteger(7, 1) - 1, w, h);
        dst.blit(args.checkInteger(1) - 1, args.checkInteger(2) - 1, w, h, data);
        return new Object[]{true};
    }

    // endregion

    @Override
    public synchronized net.minecraft.nbt.CompoundTag saveState(dev.computah.machine.Machine machine) {
        net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
        if (screenAddress != null) t.putString("screen", screenAddress);
        t.putInt("fg", foreground);
        t.putInt("bg", background);
        t.putInt("fgIndex", foregroundIndex);
        t.putInt("bgIndex", backgroundIndex);
        t.putInt("style", style);
        t.putInt("active", activeBuffer);
        t.putInt("next", nextBuffer);
        net.minecraft.nbt.CompoundTag bufs = new net.minecraft.nbt.CompoundTag();
        for (Map.Entry<Integer, DisplayBuffer> e : buffers.entrySet()) bufs.putByteArray(String.valueOf(e.getKey()), e.getValue().snapshot());
        t.put("buffers", bufs);
        return t;
    }

    @Override
    public synchronized void loadState(dev.computah.machine.Machine machine, net.minecraft.nbt.CompoundTag t) {
        screenAddress = t.contains("screen") ? t.getString("screen") : null;
        screen = null;
        foreground = t.getInt("fg");
        background = t.getInt("bg");
        foregroundIndex = t.getInt("fgIndex");
        backgroundIndex = t.getInt("bgIndex");
        style = t.getInt("style");
        activeBuffer = t.getInt("active");
        nextBuffer = Math.max(1, t.getInt("next"));
        buffers.clear();
        net.minecraft.nbt.CompoundTag bufs = t.getCompound("buffers");
        for (String k : bufs.getAllKeys()) {
            DisplayBuffer b = new DisplayBuffer(tier, 1, 1);
            try {
                b.applyLog(bufs.getByteArray(k));
                buffers.put(Integer.parseInt(k), b);
            } catch (Exception ignored) {
            }
        }
        if (activeBuffer != 0 && !buffers.containsKey(activeBuffer)) activeBuffer = 0;
    }

    /** Writes an error message onto a screen buffer (used when a machine crashes). */
    public static void showCrash(DisplayBuffer b, String message) {
        b.fill(0, 0, b.cols(), b.rows(), ' ', 0xFFFFFF, 0x000000, 0);
        List<String> lines = new ArrayList<>();
        lines.add("Unrecoverable error");
        lines.add("");
        for (String raw : message.split("\n")) {
            String l = raw.replace("\t", "  ");
            while (l.length() > b.cols()) {
                lines.add(l.substring(0, b.cols()));
                l = l.substring(b.cols());
            }
            lines.add(l);
        }
        for (int i = 0; i < Math.min(lines.size(), b.rows()); i++) {
            byte[] bytes = lines.get(i).getBytes(StandardCharsets.UTF_8);
            b.set(0, i, new String(bytes, StandardCharsets.UTF_8).codePoints().toArray(), false, i == 0 ? 0xFF5555 : 0xFFFFFF, 0x000000, 0);
        }
    }

    @Override
    public Map<String, String> deviceInfo() {
        Map<String, String> m = super.deviceInfo();
        m.put("description", "Graphics card");
        m.put("product", "Computah GPU tier " + tier.tier());
        m.put("width", String.valueOf(tier.cols()) + "x" + tier.rows());
        m.put("capacity", String.valueOf(tier.pixelWidth()) + "x" + tier.pixelHeight());
        m.put("size", String.valueOf(tier.depth()));
        return m;
    }
}
