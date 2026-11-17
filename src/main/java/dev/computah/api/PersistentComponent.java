package dev.computah.api;

import dev.computah.machine.Machine;
import net.minecraft.nbt.CompoundTag;

/**
 * A component with per-machine state that must survive saving a running computer (open files, the GPU's colours,
 * open modem ports, ...). Saved with the machine's Lua state and given back when it is restored.
 */
public interface PersistentComponent {
    CompoundTag saveState(Machine machine);

    void loadState(Machine machine, CompoundTag tag);
}
