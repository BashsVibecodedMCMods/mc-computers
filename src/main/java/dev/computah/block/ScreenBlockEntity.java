package dev.computah.block;

import dev.computah.api.Component;
import dev.computah.component.ScreenComponent;
import dev.computah.display.DisplayBuffer;
import dev.computah.net.DisplayPayload;
import dev.computah.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One block of a monitor. Adjacent screens of the same tier and facing merge into rectangles of up to
 * {@link ScreenFormation#MAX_WIDTH} x {@link ScreenFormation#MAX_HEIGHT} blocks. The top-left block (the origin) owns
 * the display buffer and the screen component; it renders the whole display and syncs it to watching players.
 */
public class ScreenBlockEntity extends MemberBlockEntity {
    /** Bigger update batches are replaced by a full snapshot. */
    private static final int MAX_LOG = 192 * 1024;

    private final int tier;
    private int gx, gy, width = 1, height = 1;
    private String address = UUID.randomUUID().toString();
    private @Nullable DisplayBuffer buffer;
    private @Nullable ScreenComponent component;
    private boolean on = true;

    public ScreenBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SCREEN.get(), pos, state);
        this.tier = state.getBlock() instanceof ScreenBlock s ? s.tier() : 1;
    }

    public int tier() {
        return tier;
    }

    public Direction facing() {
        return getBlockState().getValue(ScreenBlock.FACING);
    }

    public boolean isOrigin() {
        return gx == 0 && gy == 0;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int gx() {
        return gx;
    }

    public int gy() {
        return gy;
    }

    /** The viewer's right along the screen face. */
    public static Direction right(Direction facing) {
        return facing.getCounterClockWise();
    }

    public BlockPos originPos() {
        return worldPosition.relative(right(facing()), -gx).above(gy);
    }

    public @Nullable ScreenBlockEntity origin() {
        if (isOrigin()) return this;
        return level != null && level.getBlockEntity(originPos()) instanceof ScreenBlockEntity s ? s : null;
    }

    /** All block positions of this multiblock. */
    public List<BlockPos> blocks() {
        List<BlockPos> out = new ArrayList<>();
        BlockPos o = originPos();
        Direction r = right(facing());
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) out.add(o.relative(r, x).below(y));
        return out;
    }

    /** The display (origin only; created on demand). */
    public DisplayBuffer buffer() {
        if (buffer == null) {
            buffer = new DisplayBuffer(DisplayBuffer.Tier.of(tier));
            if (level != null && !level.isClientSide) buffer.startRecording();
        }
        return buffer;
    }

    public @Nullable ScreenComponent component() {
        if (!isOrigin()) return null;
        if (component == null) {
            component = new ScreenComponent(address, buffer(), this::keyboards);
            component.setSize(width, height);
            component.setOn(on);
        }
        return component;
    }

    /** Whether the monitor shows its picture: switched on (screen.turnOn) and powered by a running computer. */
    public boolean screenOn() {
        return component != null ? component.isOn() && component.powered() : false;
    }

    /** The screen.turnOn/turnOff setting, saved with the block. */
    private boolean switchedOn() {
        return component != null ? component.isOn() : on;
    }

    /** Keyboards attached to any block of this monitor. */
    public List<String> keyboards() {
        List<String> out = new ArrayList<>();
        for (KeyboardBlockEntity k : keyboardEntities()) out.add(k.component().address());
        return out;
    }

    public List<KeyboardBlockEntity> keyboardEntities() {
        List<KeyboardBlockEntity> out = new ArrayList<>();
        if (level == null) return out;
        for (BlockPos p : blocks()) {
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (level.isLoaded(n) && level.getBlockEntity(n) instanceof KeyboardBlockEntity k && k.attachedPos().equals(p)) out.add(k);
            }
        }
        return out;
    }

    public boolean hasKeyboard() {
        return !keyboardEntities().isEmpty();
    }

    @Override
    public List<Component> networkComponents() {
        ScreenComponent c = component();
        return c == null ? List.of() : List.of(c);
    }

    /** Sets this block's place in its multiblock (server, from {@link ScreenFormation}). */
    void setShape(int gx, int gy, int width, int height) {
        if (this.gx == gx && this.gy == gy && this.width == width && this.height == height) return;
        boolean wasOrigin = isOrigin();
        this.gx = gx;
        this.gy = gy;
        this.width = width;
        this.height = height;
        if (!isOrigin()) {
            if (wasOrigin) deleteSavedDisplay();
            buffer = null;
            component = null;
        } else if (component != null) {
            component.setSize(width, height);
        }
        if (wasOrigin != isOrigin() || isOrigin()) dev.computah.network.NetworkManager.bump(level);
        if (level != null) {
            BlockState state = getBlockState();
            BlockState shaped = state.setValue(ScreenBlock.HORIZONTAL, ScreenBlock.Part.of(gx, width))
                    .setValue(ScreenBlock.VERTICAL, ScreenBlock.Part.of(gy, height));
            if (shaped != state) level.setBlock(worldPosition, shaped, 3);
        }
        sync();
    }

    private boolean lastSentOn = true;

    public static void serverTick(Level level, BlockPos pos, BlockState state, ScreenBlockEntity be) {
        if (!be.isOrigin() || be.buffer == null) return;
        byte[] log = be.buffer.takeLog();
        boolean on = be.screenOn();
        if (log == null && on == be.lastSentOn) return;
        be.lastSentOn = on;
        if (log == null) log = new byte[0];
        else be.setChanged();
        boolean snapshot = log.length > MAX_LOG;
        byte[] data = snapshot ? be.buffer.snapshot() : log;
        PacketDistributor.sendToPlayersTrackingChunk((ServerLevel) level, new ChunkPos(pos), DisplayPayload.of(pos, data, snapshot, be.screenOn()));
    }

    /** Sends the whole display to one player (when they start watching it). */
    public void sendSnapshot(ServerPlayer player) {
        if (!isOrigin()) return;
        PacketDistributor.sendToPlayer(player, DisplayPayload.of(worldPosition, buffer().snapshot(), true, screenOn()));
    }

    /** The box covering the whole monitor, for render culling. */
    public net.minecraft.world.phys.AABB renderBox() {
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(worldPosition);
        for (BlockPos p : blocks()) box = box.minmax(new net.minecraft.world.phys.AABB(p));
        return box.inflate(0.1);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide) dev.computah.net.ClientHooks.displayRemoved(worldPosition);
    }

    // The display is saved to the world folder so a monitor keeps showing the same image after a reload.
    private int savedVersion = -1;

    private java.nio.file.Path displayPath() {
        return dev.computah.util.WorldFiles.path(level.getServer(), "screens", address + ".bin");
    }

    void deleteSavedDisplay() {
        if (level != null && !level.isClientSide) dev.computah.util.WorldFiles.delete(displayPath());
    }

    private void saveDisplay() {
        if (level == null || level.isClientSide || !isOrigin() || buffer == null || buffer.version() == savedVersion) return;
        savedVersion = buffer.version();
        dev.computah.util.WorldFiles.write(displayPath(), buffer.snapshot());
    }

    private void loadDisplay() {
        byte[] data = dev.computah.util.WorldFiles.read(displayPath());
        if (data == null) return;
        try {
            DisplayBuffer b = buffer();
            b.applyLog(data);
            b.clearLog();
            savedVersion = b.version();
        } catch (Exception e) {
            buffer = null;
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide && isOrigin()) loadDisplay();
        if (level != null && !level.isClientSide) ScreenFormation.schedule(level, worldPosition);
        else if (level != null && isOrigin()) dev.computah.net.ClientHooks.requestDisplay(worldPosition);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("address", address);
        tag.putBoolean("on", switchedOn());
        saveDisplay();
        writeClient(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("address")) address = tag.getString("address");
        if (tag.contains("on")) on = tag.getBoolean("on");
        readClient(tag);
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        tag.putInt("gx", gx);
        tag.putInt("gy", gy);
        tag.putInt("w", width);
        tag.putInt("h", height);
    }

    @Override
    protected void readClient(CompoundTag tag) {
        boolean wasOrigin = isOrigin();
        gx = tag.getInt("gx");
        gy = tag.getInt("gy");
        width = Math.max(1, tag.getInt("w"));
        height = Math.max(1, tag.getInt("h"));
        if (level != null && level.isClientSide && isOrigin() && !wasOrigin) dev.computah.net.ClientHooks.requestDisplay(worldPosition);
    }
}
