package dev.computah.host;

import dev.computah.Config;
import dev.computah.api.Component;
import dev.computah.component.*;
import dev.computah.fs.DirectoryFileSystem;
import dev.computah.fs.DriveTiming;
import dev.computah.fs.OsFiles;
import dev.computah.item.PartItem;
import dev.computah.item.PartKind;
import dev.computah.link.Links;
import dev.computah.machine.Machine;
import dev.computah.machine.MachineHost;
import dev.computah.machine.MachineSpec;
import dev.computah.network.ModemComponent;
import dev.computah.network.NetworkManager;
import dev.computah.registry.ModComponents;
import dev.computah.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Everything a computer needs besides its block: the part slots, the components built from them, network and link
 * discovery, and the {@link Machine}. Used by computer cases (one each) and racks (one per server).
 */
public final class HostCore implements MachineHost, ModemComponent.Location, RedstoneComponent.Host {
    /** The block entity holding this core. */
    public interface Owner {
        @Nullable
        Level level();

        BlockPos pos();

        /** Marks the block entity as changed (save, and for racks write back into the server item). */
        void markDirty();

        /** Takes energy; returns false if there isn't enough. Only called when power is enabled. */
        boolean drawEnergy(double amount);

        double energy();

        double maxEnergy();

        double runningCost();

        void onStateChanged(HostCore core);

        int redstoneInput(Direction side);

        void redstoneChanged();

        String describe();
    }

    private final Owner owner;
    private final HostInventory inventory;
    private final Component[] parts;
    private List<Component> external = List.of();
    private @Nullable Machine machine;
    private @Nullable String machineAddress;
    private boolean partsDirty = true;
    private boolean componentsDirty = true;
    private boolean autoStart;
    private long seenEpoch = -1, seenLinkEpoch = -1;
    private final AtomicBoolean hddActivity = new AtomicBoolean(), floppyActivity = new AtomicBoolean();
    private final AtomicReference<double[]> pendingBeep = new AtomicReference<>();
    private final java.util.concurrent.ConcurrentLinkedQueue<double[]> beepQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private long nextBeepTick;
    private long lastHddSound, lastFloppySound, ticks;
    private String hardwareSignature = "";
    // Saving running computers (see save/load and docs/ARCHITECTURE.md).
    private long stateSerial;
    private boolean pendingRestore;
    private boolean quiet;

    public HostCore(Owner owner, PartKind.SlotType[] layout) {
        this.owner = owner;
        this.inventory = new HostInventory(layout, this::onInventoryChanged);
        this.parts = new Component[layout.length];
    }

    public HostInventory inventory() {
        return inventory;
    }

    public Machine machine() {
        if (machine == null) {
            if (machineAddress == null) machineAddress = UUID.randomUUID().toString();
            machine = new Machine(this, machineAddress);
        }
        return machine;
    }

    public boolean isRunning() {
        return machine != null && machine.isOn();
    }

    private void onInventoryChanged() {
        partsDirty = true;
        owner.markDirty();
    }

    // region Ticking

    public void tick() {
        Level level = owner.level();
        if (!(level instanceof ServerLevel server)) return;
        ticks++;
        Machine m = machine();
        if (partsDirty) {
            partsDirty = false;
            rebuildParts(server);
            componentsDirty = true;
        }
        long e = NetworkManager.epoch(level), le = NetworkManager.linkEpoch();
        if ((e != seenEpoch || le != seenLinkEpoch) && (pendingRestore || NetworkManager.tryScan())) {
            seenEpoch = e;
            seenLinkEpoch = le;
            rescan(server);
            componentsDirty = true;
        }
        if (componentsDirty) {
            componentsDirty = false;
            m.setComponents(allComponents());
        }
        if (pendingRestore) {
            pendingRestore = false;
            if (!restore(server, m)) autoStart = true;
        }
        if (autoStart) {
            autoStart = false;
            m.start();
        }
        m.update();
        // Keep the chunk marked as changed while running, so autosaves keep the saved state fresh.
        if (m.isOn() && ticks % 100 == 0) owner.markDirty();
        double[] beep = pendingBeep.getAndSet(null);
        if (beep == null && ticks >= nextBeepTick) {
            double[] queued = beepQueue.poll();
            if (queued != null) {
                beep = queued;
                nextBeepTick = ticks + Math.round(queued[1] * 20) + 2;
            }
        }
        if (beep != null && beep[0] > 0) {
            BlockPos p = owner.pos();
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayersNear((net.minecraft.server.level.ServerLevel) level, null, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 32,
                    new dev.computah.net.BeepPayload(p, (float) beep[0], (float) beep[1]));
        }
        if (hddActivity.getAndSet(false) && ticks - lastHddSound > 12) {
            lastHddSound = ticks;
            level.playSound(null, owner.pos(), ModSounds.HDD_ACCESS.get(), SoundSource.BLOCKS, 0.3f, 1.0f);
        }
        if (floppyActivity.getAndSet(false) && ticks - lastFloppySound > 12) {
            lastFloppySound = ticks;
            level.playSound(null, owner.pos(), ModSounds.FLOPPY_ACCESS.get(), SoundSource.BLOCKS, 0.3f, 1.0f);
        }
    }

    private List<Component> allComponents() {
        List<Component> all = new ArrayList<>();
        for (Component c : parts) if (c != null) all.add(c);
        all.addAll(external);
        return all;
    }

    private void rescan(ServerLevel level) {
        Map<String, Component> found = new LinkedHashMap<>();
        for (Component c : NetworkManager.scanComponents(level, owner.pos())) found.putIfAbsent(c.address(), c);
        int tier = wirelessTier();
        if (tier > 0) {
            for (Component c : Links.resolve(level.getServer(), GlobalPos.of(level.dimension(), owner.pos()), tier)) {
                found.putIfAbsent(c.address(), c);
            }
        }
        // Adapter components are rebuilt on every scan: keep the old objects so programs don't see them change.
        Map<String, Component> old = new HashMap<>();
        for (Component c : external) old.put(c.address(), c);
        List<Component> next = new ArrayList<>();
        for (Component c : found.values()) {
            Component prev = old.get(c.address());
            next.add(prev != null && prev.getClass() == c.getClass() && c instanceof AdapterComponents.Base ? prev : c);
        }
        external = next;
    }

    /** Forces a network rescan next tick (e.g. after linking). */
    public void invalidateNetwork() {
        seenEpoch = -1;
    }

    // endregion

    // region Parts

    private static String signature(HostInventory inv) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).getItem() instanceof PartItem p && (p.kind() == PartKind.CPU || p.kind() == PartKind.RAM)) {
                sb.append(i).append(p.kind()).append(p.tier()).append(';');
            }
        }
        return sb.toString();
    }

    private void rebuildParts(ServerLevel level) {
        String sig = signature(inventory);
        if (machine != null && machine.isOn() && !sig.equals(hardwareSignature)) {
            machine.crash("hardware changed");
        }
        hardwareSignature = sig;
        for (int i = 0; i < parts.length; i++) {
            ItemStack stack = inventory.getItem(i);
            Component current = parts[i];
            if (!(stack.getItem() instanceof PartItem part)) {
                if (current != null) closePart(current);
                parts[i] = null;
                continue;
            }
            String address = stack.get(ModComponents.ADDRESS.get());
            if (address == null) {
                address = UUID.randomUUID().toString();
                stack.set(ModComponents.ADDRESS.get(), address);
                owner.markDirty();
            }
            if (current != null && current.address().equals(address) && kindOf(current) == part.kind()) continue;
            if (current != null) closePart(current);
            Component c = createPart(level, i, part, stack, address);
            if (c != null) c.setSlot(i);
            parts[i] = c;
        }
    }

    private final Map<Component, PartKind> kinds = new IdentityHashMap<>();

    private PartKind kindOf(Component c) {
        return kinds.get(c);
    }

    private void closePart(Component c) {
        kinds.remove(c);
        if (c instanceof FilesystemComponent fs) fs.close();
        if (c instanceof ModemComponent m) NetworkManager.unregisterWireless(m);
    }

    private @Nullable Component createPart(ServerLevel level, int slot, PartItem part, ItemStack stack, String address) {
        Component c = switch (part.kind()) {
            case CPU, RAM -> null;
            case EEPROM -> {
                ModComponents.EepromData data = stack.get(ModComponents.EEPROM.get());
                EepromComponent.Contents contents = data == null ? EepromComponent.Contents.EMPTY : data.contents();
                yield new EepromComponent(address, contents, saved -> {
                    ItemStack s = inventory.getItem(slot);
                    if (address.equals(s.get(ModComponents.ADDRESS.get()))) {
                        s.set(ModComponents.EEPROM.get(), ModComponents.EepromData.of(saved));
                        owner.markDirty();
                    }
                });
            }
            case HDD, FLOPPY -> {
                boolean floppy = part.kind() == PartKind.FLOPPY;
                FilesystemComponent fs;
                if (floppy && PartItem.isOsFloppy(stack)) {
                    fs = new FilesystemComponent(OsFiles.fileSystem(), address, FilesystemComponent.fixedLabel("Computah OS"));
                } else {
                    long size = floppy ? PartItem.floppySize() : PartItem.hddSize(part.tier());
                    fs = new FilesystemComponent(new DirectoryFileSystem(fsRoot(level).resolve(address), size), address, label(slot, address));
                }
                fs.setTiming(floppy ? DriveTiming.floppy() : DriveTiming.hdd(part.tier()));
                fs.setActivityListener(write -> (floppy ? floppyActivity : hddActivity).set(true));
                yield fs;
            }
            case GPU -> new GpuComponent(address, part.tier());
            case NETWORK_CARD -> new ModemComponent(address, 0, this);
            case WIRELESS_CARD -> new ModemComponent(address, part.tier(), this);
            case REDSTONE_CARD -> new RedstoneComponent(address, this);
        };
        if (c != null) kinds.put(c, part.kind());
        return c;
    }

    private FilesystemComponent.Label label(int slot, String address) {
        return new FilesystemComponent.Label() {
            @Override
            public String get() {
                ItemStack s = inventory.getItem(slot);
                return address.equals(s.get(ModComponents.ADDRESS.get())) ? s.get(ModComponents.LABEL.get()) : null;
            }

            @Override
            public boolean set(String label) {
                ItemStack s = inventory.getItem(slot);
                if (!address.equals(s.get(ModComponents.ADDRESS.get()))) return false;
                if (label == null) s.remove(ModComponents.LABEL.get());
                else s.set(ModComponents.LABEL.get(), label);
                owner.markDirty();
                return true;
            }
        };
    }

    public static Path fsRoot(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve("computah").resolve("fs");
    }

    public int wirelessTier() {
        int t = 0;
        for (Component c : parts) if (c instanceof ModemComponent m && m.isWireless()) t = Math.max(t, m.wirelessTier());
        return t;
    }

    public List<ModemComponent> wiredModems() {
        List<ModemComponent> out = new ArrayList<>();
        for (Component c : parts) if (c instanceof ModemComponent m && !m.isWireless()) out.add(m);
        return out;
    }

    public int redstoneOutput(Direction side) {
        int v = 0;
        for (Component c : parts) if (c instanceof RedstoneComponent r) v = Math.max(v, r.output(side));
        return v;
    }

    /** Called when a neighbouring block changes, to send redstone_changed signals. */
    public void onNeighborChanged() {
        for (Component c : parts) if (c instanceof RedstoneComponent r) r.updateInputs();
    }

    // endregion

    // region Lifecycle

    public void start() {
        machine().start();
    }

    public void stop() {
        if (machine != null) machine.stop();
    }

    public void toggle() {
        if (isRunning()) stop();
        else start();
    }

    private java.nio.file.Path statePath(ServerLevel level) {
        return dev.computah.util.WorldFiles.path(level.getServer(), "state", machine().address() + ".bin");
    }

    private boolean restore(ServerLevel level, Machine m) {
        byte[] file = dev.computah.util.WorldFiles.read(statePath(level));
        if (file == null || file.length < 8) return false;
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(file);
        if (buf.getLong() != stateSerial) {
            dev.computah.Computah.LOG.info("Saved state of {} doesn't match the world; rebooting it", describe());
            return false;
        }
        byte[] data = java.util.Arrays.copyOfRange(file, 8, file.length);
        return m.restoreState(data);
    }

    /** Forgets the saved state (the computer was broken or taken out). */
    public void discardState() {
        if (owner.level() instanceof ServerLevel level && machineAddress != null) dev.computah.util.WorldFiles.delete(statePath(level));
        stateSerial = 0;
        pendingRestore = false;
    }

    /** Saves the running machine to the world folder. Keeps the previous save if it can't be saved right now. */
    private void persist() {
        if (!(owner.level() instanceof ServerLevel level) || machine == null) return;
        if (!machine.isOn()) {
            if (stateSerial != 0) discardState();
            return;
        }
        try {
            byte[] data = machine.saveState(1000);
            if (data == null) return;
            long serial = stateSerial + 1;
            java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(8 + data.length);
            buf.putLong(serial).put(data);
            if (dev.computah.util.WorldFiles.write(statePath(level), buf.array())) stateSerial = serial;
        } catch (dev.computah.machine.persist.LuaPersistence.NotPersistableException ex) {
            dev.computah.Computah.LOG.warn("Couldn't save the running state of {} ({}); it will reboot when loaded unless a later save works",
                    describe(), ex.getMessage());
        }
    }

    /** The block was removed or unloaded: stop the machine without touching the world. */
    public void shutdown() {
        quiet = true;
        if (machine != null) machine.stop();
        quiet = false;
        for (Component c : parts) if (c != null) closePart(c);
        Arrays.fill(parts, null);
        partsDirty = true;
    }

    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        NonNullList<ItemStack> items = NonNullList.withSize(inventory.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < items.size(); i++) items.set(i, inventory.getItem(i));
        ContainerHelper.saveAllItems(tag, items, registries);
        persist();
        if (machineAddress != null) tag.putString("machine", machineAddress);
        tag.putBoolean("running", isRunning());
        tag.putLong("stateSerial", isRunning() ? stateSerial : 0);
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        NonNullList<ItemStack> items = NonNullList.withSize(inventory.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        for (int i = 0; i < items.size(); i++) inventory.setItem(i, items.get(i));
        if (tag.contains("machine")) machineAddress = tag.getString("machine");
        // A computer that was running continues from its saved state, or reboots if there is none.
        stateSerial = tag.getLong("stateSerial");
        boolean running = tag.getBoolean("running");
        pendingRestore = running && stateSerial != 0;
        autoStart = running && !pendingRestore;
        partsDirty = true;
    }

    // endregion

    // region MachineHost

    @Override
    public @Nullable MachineSpec spec() {
        int cpu = 0;
        long memory = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).getItem() instanceof PartItem p) {
                if (p.kind() == PartKind.CPU && cpu == 0) cpu = p.tier();
                if (p.kind() == PartKind.RAM) memory += PartItem.ramSize(p.tier());
            }
        }
        if (cpu == 0 || memory == 0) return null;
        return new MachineSpec(memory, PartItem.CPU_COMPONENTS[cpu], PartItem.CPU_BUDGET[cpu], Config.get(Config.TIMEOUT), PartItem.tmpSize());
    }

    @Override
    public String bootProblem() {
        boolean cpu = false, ram = false;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).getItem() instanceof PartItem p) {
                cpu |= p.kind() == PartKind.CPU;
                ram |= p.kind() == PartKind.RAM;
            }
        }
        return !cpu ? "no CPU" : !ram ? "no memory" : "missing hardware";
    }

    @Override
    public boolean consumeEnergy(double amount) {
        if (!Config.get(Config.POWER_ENABLED)) return true;
        if (amount == 0) return owner.energy() > 0;
        return owner.drawEnergy(owner.runningCost() * amount);
    }

    @Override
    public double energy() {
        return Config.get(Config.POWER_ENABLED) ? owner.energy() : Double.POSITIVE_INFINITY;
    }

    @Override
    public double maxEnergy() {
        return Config.get(Config.POWER_ENABLED) ? owner.maxEnergy() : Double.POSITIVE_INFINITY;
    }

    @Override
    public void onStateChanged(Machine m) {
        if (quiet) return;
        if (m.state() == Machine.State.CRASHED && m.crashMessage() != null) {
            for (Component c : external) {
                if (c instanceof ScreenComponent s) {
                    GpuComponent.showCrash(s.buffer(), m.crashMessage());
                    s.setShowingCrash(true);
                    break;
                }
            }
        }
        if (m.state() != Machine.State.RUNNING) {
            boolean had = false;
            for (Component c : parts) {
                if (c instanceof RedstoneComponent r) {
                    r.resetOutputs();
                    had = true;
                }
            }
            if (had) owner.redstoneChanged();
        }
        owner.onStateChanged(this);
    }

    @Override
    public void beep(double frequency, double duration) {
        pendingBeep.set(new double[]{frequency, duration});
    }

    @Override
    public void beepPattern(String pattern) {
        if (beepQueue.size() > 64) return;
        for (char c : pattern.toCharArray()) {
            if (c == '.') beepQueue.add(new double[]{1000, 0.1});
            else if (c == '-') beepQueue.add(new double[]{1000, 0.4});
            else if (c == ' ') beepQueue.add(new double[]{0, 0.2});
        }
    }

    @Override
    public String describe() {
        return owner.describe();
    }

    // endregion

    // region Locations

    @Override
    public @Nullable Level level() {
        return owner.level();
    }

    @Override
    public BlockPos pos() {
        return owner.pos();
    }

    @Override
    public int input(Direction side) {
        return owner.redstoneInput(side);
    }

    @Override
    public void outputsChanged() {
        owner.redstoneChanged();
    }

    // endregion
}
