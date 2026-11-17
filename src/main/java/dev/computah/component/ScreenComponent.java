package dev.computah.component;

import dev.computah.api.Arguments;
import dev.computah.api.Callback;
import dev.computah.api.Component;
import dev.computah.api.Context;
import dev.computah.display.DisplayBuffer;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The {@code screen} component of a (multiblock) monitor. GPUs bind to it and draw into its {@link DisplayBuffer};
 * the same buffer is drawn on the block in the world and in the screen GUI.
 */
public class ScreenComponent extends Component {
    private final String address;
    private volatile DisplayBuffer buffer;
    private volatile boolean on = true;
    private volatile boolean precise;
    private volatile int blocksWide = 1, blocksHigh = 1;
    private final Supplier<List<String>> keyboards;

    public ScreenComponent(String address, DisplayBuffer buffer, Supplier<List<String>> keyboards) {
        this.address = address;
        this.buffer = buffer;
        this.keyboards = keyboards;
    }

    public ScreenComponent(DisplayBuffer buffer) {
        this(UUID.randomUUID().toString(), buffer, List::of);
    }

    @Override
    public String address() {
        return address;
    }

    @Override
    public String type() {
        return "screen";
    }

    public DisplayBuffer buffer() {
        return buffer;
    }

    public void setBuffer(DisplayBuffer buffer) {
        this.buffer = buffer;
    }

    public boolean isOn() {
        return on;
    }

    public void setOn(boolean on) {
        this.on = on;
    }

    public boolean precise() {
        return precise;
    }

    public void setSize(int wide, int high) {
        blocksWide = wide;
        blocksHigh = high;
    }

    @Callback(doc = "function():boolean -- Whether the screen is on.")
    public Object[] isOn(Context ctx, Arguments args) {
        return new Object[]{on};
    }

    @Callback(doc = "function():boolean -- Turns the screen on. Returns whether it changed.")
    public Object[] turnOn(Context ctx, Arguments args) {
        boolean was = on;
        on = true;
        return new Object[]{!was, true};
    }

    @Callback(doc = "function():boolean -- Turns the screen off (it shows nothing). Returns whether it changed.")
    public Object[] turnOff(Context ctx, Arguments args) {
        boolean was = on;
        on = false;
        return new Object[]{was, false};
    }

    @Callback(doc = "function():number, number -- The size of the monitor in blocks (width, height).")
    public Object[] getAspectRatio(Context ctx, Arguments args) {
        return new Object[]{blocksWide, blocksHigh};
    }

    @Callback(doc = "function():table -- Addresses of the keyboards attached to this monitor.")
    public Object[] getKeyboards(Context ctx, Arguments args) {
        return new Object[]{keyboards.get()};
    }

    @Callback(doc = "function(enabled:boolean):boolean -- Precise mode: touch events report fractional cell coordinates.")
    public Object[] setPrecise(Context ctx, Arguments args) throws dev.computah.api.ComponentException {
        boolean old = precise;
        precise = args.checkBoolean(0);
        return new Object[]{old};
    }

    @Callback(doc = "function():boolean -- Whether precise mode is on.")
    public Object[] isPrecise(Context ctx, Arguments args) {
        return new Object[]{precise};
    }
}
