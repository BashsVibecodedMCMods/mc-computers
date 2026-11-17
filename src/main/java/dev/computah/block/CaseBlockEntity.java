package dev.computah.block;

import dev.computah.Config;
import dev.computah.host.HostCore;
import dev.computah.item.PartKind;
import dev.computah.machine.Machine;
import dev.computah.menu.HostMenu;
import dev.computah.network.ModemComponent;
import dev.computah.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A computer case: one machine, its parts, an optional FE buffer. */
public class CaseBlockEntity extends MemberBlockEntity implements HostCore.Owner, MenuProvider {
    private final int tier;
    private final HostCore core;
    private final EnergyStorage energy = new EnergyStorage(Integer.MAX_VALUE) {
        @Override
        public int receiveEnergy(int max, boolean simulate) {
            int room = Math.max(0, Config.get(Config.CASE_BUFFER) - this.energy);
            int r = Math.min(room, max);
            if (!simulate && r > 0) {
                this.energy += r;
                setChanged();
            }
            return r;
        }

        @Override
        public int getMaxEnergyStored() {
            return Config.get(Config.CASE_BUFFER);
        }
    };
    private double energyFraction;
    // Client-side copies for the GUI.
    private boolean clientRunning;
    private String clientError = "";

    public CaseBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CASE.get(), pos, state);
        this.tier = state.getBlock() instanceof CaseBlock c ? c.tier() : 1;
        this.core = new HostCore(this, PartKind.caseLayout(tier));
    }

    public HostCore core() {
        return core;
    }

    public int tier() {
        return tier;
    }

    public EnergyStorage energyStorage() {
        return energy;
    }

    public boolean clientRunning() {
        return clientRunning;
    }

    public String clientError() {
        return clientError;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CaseBlockEntity be) {
        be.core.tick();
    }

    @Override
    public Kind memberKind() {
        return Kind.HOST;
    }

    @Override
    public List<ModemComponent> wiredModems() {
        return core.wiredModems();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) core.shutdown();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && !level.isClientSide) core.shutdown();
    }

    // region HostCore.Owner

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
        setChanged();
    }

    @Override
    public boolean drawEnergy(double amount) {
        energyFraction += amount;
        int whole = (int) energyFraction;
        if (whole <= 0) return energy.getEnergyStored() > 0 || amount == 0;
        if (energy.getEnergyStored() < whole) return false;
        energy.extractEnergy(whole, false);
        energyFraction -= whole;
        return true;
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
        return Config.get(Config.COMPUTER_COST);
    }

    @Override
    public void onStateChanged(HostCore c) {
        if (level == null) return;
        BlockState s = getBlockState();
        boolean running = c.isRunning();
        if (s.hasProperty(CaseBlock.RUNNING) && s.getValue(CaseBlock.RUNNING) != running) {
            level.setBlock(worldPosition, s.setValue(CaseBlock.RUNNING, running), 3);
        }
        sync();
    }

    @Override
    public int redstoneInput(Direction side) {
        if (level == null) return 0;
        return level.getSignal(worldPosition.relative(side), side);
    }

    @Override
    public void redstoneChanged() {
        if (level != null) level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    }

    @Override
    public String describe() {
        return "computer at " + worldPosition.toShortString();
    }

    // endregion

    // region Saving and syncing

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        core.save(tag, registries);
        tag.putInt("energy", energy.getEnergyStored());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        core.load(tag, registries);
        energy.receiveEnergy(tag.getInt("energy"), false);
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        Machine m = core.machine();
        tag.putBoolean("running", m.isOn());
        String err = m.state() == Machine.State.CRASHED && m.crashMessage() != null ? m.crashMessage() : "";
        tag.putString("error", err.length() > 400 ? err.substring(0, 400) : err);
    }

    @Override
    protected void readClient(CompoundTag tag) {
        clientRunning = tag.getBoolean("running");
        clientError = tag.getString("error");
    }

    // endregion

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.computah.case" + tier);
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return HostMenu.forCase(id, inv, this);
    }
}
