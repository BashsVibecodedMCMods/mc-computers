package dev.computah.compat.jei;

import dev.computah.Computah;
import dev.computah.component.EepromComponent;
import dev.computah.item.PartItem;
import dev.computah.registry.ModComponents;
import dev.computah.registry.ModItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Optional JEI integration (loaded by JEI only). The OS floppy, dyed floppies and the Lua BIOS EEPROM are data on the
 * floppy and EEPROM items, so JEI is told which data makes a different entry; it then lists the creative tab's
 * variants separately and shows their recipes.
 */
@JeiPlugin
public final class ComputahJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(Computah.MOD_ID, "jei");
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        registration.registerSubtypeInterpreter(ModItems.FLOPPY.get(), new ISubtypeInterpreter<>() {
            @Override
            public @Nullable Object getSubtypeData(ItemStack stack, UidContext context) {
                return floppyKey(stack);
            }

            @Override
            public String getLegacyStringSubtypeInfo(ItemStack stack, UidContext context) {
                String key = floppyKey(stack);
                return key == null ? "" : key;
            }
        });
        registration.registerSubtypeInterpreter(ModItems.EEPROM.get(), new ISubtypeInterpreter<>() {
            @Override
            public @Nullable Object getSubtypeData(ItemStack stack, UidContext context) {
                return eepromKey(stack);
            }

            @Override
            public String getLegacyStringSubtypeInfo(ItemStack stack, UidContext context) {
                String key = eepromKey(stack);
                return key == null ? "" : key;
            }
        });
    }

    /** "os", a colour name, "os_<colour>", or null for a plain floppy. */
    static @Nullable String floppyKey(ItemStack stack) {
        DyeColor c = stack.get(ModComponents.FLOPPY_COLOR.get());
        boolean os = PartItem.isOsFloppy(stack);
        if (c == null) return os ? "os" : null;
        return os ? "os_" + c.getName() : c.getName();
    }

    /** "bios" for the stock Lua BIOS, null for blank EEPROMs (other code is not a separate entry). */
    static @Nullable String eepromKey(ItemStack stack) {
        ModComponents.EepromData d = stack.get(ModComponents.EEPROM.get());
        if (d == null) return null;
        EepromComponent.Contents c = d.contents();
        return "Lua BIOS".equals(c.label()) && c.code().length > 0 ? "bios" : null;
    }
}
