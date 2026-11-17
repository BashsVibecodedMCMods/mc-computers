package dev.computah.block;

import dev.computah.Config;
import dev.computah.host.HostCore;
import dev.computah.item.PartKind;
import dev.computah.item.ServerItem;
import dev.computah.machine.Machine;
import dev.computah.menu.RackMenu;
import dev.computah.network.ModemComponent;
import dev.computah.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A server rack: four rack-mounted servers, each a full machine with its own parts (kept inside the server item).
 * All servers share the rack's network connection, redstone sides and energy buffer.
 */
public class RackBlockEntity extends MemberBlockEntity implements SimpleEntityBlock.Hooks, MenuProvider {
    public static final int SERVERS = 4;

    private final HostCore[] cores = new HostCore[SERVERS];
    private final boolean[] loading = new boolean[SERVERS];
    private final SimpleContainer servers = new SimpleContainer(SERVERS) {
        @Override
        public boolean canPlaceItem(int index, ItemStack stack) {
            return stack.getItem() instanceof ServerItem;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            syncServers();
        }
    };
    private final ItemStack[] mounted = new ItemStack[SERVERS];
    private final EnergyStorage energy = new EnergyStorage(Integer.MAX_VALUE) {
        @Override
        public int receiveEnergy(int max, boolean simulate) {
            int r = Math.min(Math.max(0, Config.get(Config.RACK_BUFFER) - this.energy), max);
            if (!simulate && r > 0) {
                this.energy += r;
                setChanged();
            }
            return r;
        }

        @Override
        public int getMaxEnergyStored() {
            return Config.get(Config.RACK_BUFFER);
        }
    };
    private double energyFraction;
    private final boolean[] clientRunning = new boolean[SERVERS];
    private final String[] clientError = {"", "", "", ""};

    public RackBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RACK.get(), pos, state);
        for (int i = 0; i < SERVERS; i++) {
            final int slot = i;
            mounted[i] = ItemStack.EMPTY;
            cores[i] = new HostCore(new HostCore.Owner() {
                @Override
                public @Nullable Level level() {
                    return level;
                }

                @Override
                public BlockPos pos() {
                    return worldPosition;
                }

                @Override
                public void markDirty() {
                    writeBack(slot);
                    setChanged();
                }

                @Override
                public boolean drawEnergy(double amount) {
                    return RackBlockEntity.this.drawEnergy(amount);
                }

                @Override
                public double energy() {
                    return energy.getEnergyStored();
                }

                @Override
                public double maxEnergy() {
                    return energy.getMaxEnergyStored();
                }

                @Override
                public double runningCost() {
                    return Config.get(Config.SERVER_COST);
                }

                @Override
                public void onStateChanged(HostCore core) {
                    sync();
                }

                @Override
                public int redstoneInput(Direction side) {
                    return level == null ? 0 : level.getSignal(worldPosition.relative(side), side);
                }

                @Override
                public void redstoneChanged() {
                    if (level != null) level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
                }

                @Override
                public String describe() {
                    return "rack server " + (slot + 1) + " at " + worldPosition.toShortString();
                }
            }, PartKind.SERVER);
        }
    }

    public HostCore core(int i) {
        return cores[i];
    }

    public Container servers() {
        return servers;
    }

    public EnergyStorage energyStorage() {
        return energy;
    }

    public boolean clientRunning(int i) {
        return clientRunning[i];
    }

    public String clientError(int i) {
        return clientError[i];
    }

    private boolean drawEnergy(double amount) {
        energyFraction += amount;
        int whole = (int) energyFraction;
        if (whole <= 0) return energy.getEnergyStored() > 0 || amount == 0;
        if (energy.getEnergyStored() < whole) return false;
        energy.extractEnergy(whole, false);
        energyFraction -= whole;
        return true;
    }

    /** Copies parts between the mounted server items and the cores when servers are inserted or removed. */
    private void syncServers() {
        for (int i = 0; i < SERVERS; i++) {
            ItemStack now = servers.getItem(i);
            if (now == mounted[i]) continue;
            HostCore core = cores[i];
            core.shutdown();
            loading[i] = true;
            List<ItemStack> parts = now.isEmpty() ? List.of() : ServerItem.parts(now);
            for (int s = 0; s < core.inventory().getContainerSize(); s++) {
                core.inventory().setItem(s, s < parts.size() ? parts.get(s).copy() : ItemStack.EMPTY);
            }
            loading[i] = false;
            mounted[i] = now;
        }
        setChanged();
        sync();
    }

    private void writeBack(int i) {
        if (loading[i]) return;
        ItemStack server = servers.getItem(i);
        if (server.isEmpty()) return;
        List<ItemStack> parts = new ArrayList<>();
        for (int s = 0; s < cores[i].inventory().getContainerSize(); s++) parts.add(cores[i].inventory().getItem(s));
        ServerItem.setParts(server, parts);
    }

    @Override
    public void serverTick() {
        for (int i = 0; i < SERVERS; i++) if (!servers.getItem(i).isEmpty()) cores[i].tick();
    }

    @Override
    public void neighborChanged() {
        for (HostCore c : cores) c.onNeighborChanged();
    }

    @Override
    public int signal(Direction towards) {
        int v = 0;
        for (HostCore c : cores) v = Math.max(v, c.redstoneOutput(towards));
        return v;
    }

    @Override
    public @Nullable Container dropContainer() {
        for (int i = 0; i < SERVERS; i++) writeBack(i);
        return servers;
    }

    @Override
    public void removed() {
        for (HostCore c : cores) c.shutdown();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) for (HostCore c : cores) c.shutdown();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && !level.isClientSide) for (HostCore c : cores) c.shutdown();
    }

    @Override
    public Kind memberKind() {
        return Kind.HOST;
    }

    @Override
    public List<ModemComponent> wiredModems() {
        List<ModemComponent> out = new ArrayList<>();
        for (int i = 0; i < SERVERS; i++) if (!servers.getItem(i).isEmpty()) out.addAll(cores[i].wiredModems());
        return out;
    }

    // region Saving

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        for (int i = 0; i < SERVERS; i++) writeBack(i);
        NonNullList<ItemStack> items = NonNullList.withSize(SERVERS, ItemStack.EMPTY);
        for (int i = 0; i < SERVERS; i++) items.set(i, servers.getItem(i));
        ContainerHelper.saveAllItems(tag, items, registries);
        for (int i = 0; i < SERVERS; i++) {
            CompoundTag state = new CompoundTag();
            cores[i].save(state, registries);
            state.remove("Items");
            tag.put("server" + i, state);
        }
        tag.putInt("energy", energy.getEnergyStored());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        NonNullList<ItemStack> items = NonNullList.withSize(SERVERS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        for (int i = 0; i < SERVERS; i++) {
            ItemStack s = items.get(i);
            // Load the core's state (machine address, running) first, then its parts from the server item.
            CompoundTag state = tag.getCompound("server" + i);
            List<ItemStack> parts = s.isEmpty() ? List.of() : ServerItem.parts(s);
            NonNullList<ItemStack> partList = NonNullList.withSize(cores[i].inventory().getContainerSize(), ItemStack.EMPTY);
            for (int p = 0; p < partList.size() && p < parts.size(); p++) partList.set(p, parts.get(p).copy());
            ContainerHelper.saveAllItems(state, partList, registries);
            loading[i] = true;
            cores[i].load(state, registries);
            loading[i] = false;
            servers.getItems().set(i, s);
            mounted[i] = s;
        }
        energy.receiveEnergy(tag.getInt("energy"), false);
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        for (int i = 0; i < SERVERS; i++) {
            Machine m = cores[i].machine();
            tag.putBoolean("running" + i, m.isOn());
            String err = m.state() == Machine.State.CRASHED && m.crashMessage() != null ? m.crashMessage() : "";
            tag.putString("error" + i, err.length() > 200 ? err.substring(0, 200) : err);
        }
    }

    @Override
    protected void readClient(CompoundTag tag) {
        for (int i = 0; i < SERVERS; i++) {
            clientRunning[i] = tag.getBoolean("running" + i);
            clientError[i] = tag.getString("error" + i);
        }
    }

    // endregion

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.computah.rack");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new RackMenu(id, inv, servers, worldPosition);
    }
}
