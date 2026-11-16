package dev.computah;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(Computah.MOD_ID)
public final class Computah {
    public static final String MOD_ID = "computah";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    public Computah(IEventBus modBus, ModContainer container) {
    }
}
