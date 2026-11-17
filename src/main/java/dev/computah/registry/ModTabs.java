package dev.computah.registry;

import dev.computah.Computah;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> REGISTER = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Computah.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = REGISTER.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.computah"))
            .icon(() -> new ItemStack(ModItems.CASE3.get()))
            .displayItems((params, out) -> {
                for (var item : ModItems.ALL) {
                    out.accept(item.get());
                    // Ready-made variants share their item id with the blank part (no extra items).
                    if (item == ModItems.FLOPPY) {
                        out.accept(dev.computah.item.PartItem.osFloppy());
                        for (net.minecraft.world.item.DyeColor c : net.minecraft.world.item.DyeColor.values()) {
                            out.accept(dev.computah.item.PartItem.dyedFloppy(c));
                        }
                    } else if (item == ModItems.EEPROM) {
                        out.accept(dev.computah.item.PartItem.biosEeprom());
                    }
                }
            })
            .build());

    private ModTabs() {
    }
}
