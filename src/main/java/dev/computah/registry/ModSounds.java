package dev.computah.registry;

import dev.computah.Computah;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Sounds from OpenComputers (see CREDITS.md; the HDD sound is based on a Sampling+ licensed freesound.org clip). */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> REGISTER = DeferredRegister.create(Registries.SOUND_EVENT, Computah.MOD_ID);

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return REGISTER.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Computah.MOD_ID, name)));
    }

    public static final DeferredHolder<SoundEvent, SoundEvent> HDD_ACCESS = sound("hdd_access");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLOPPY_ACCESS = sound("floppy_access");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLOPPY_INSERT = sound("floppy_insert");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLOPPY_EJECT = sound("floppy_eject");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMPUTER_RUNNING = sound("computer_running");

    private ModSounds() {
    }
}
