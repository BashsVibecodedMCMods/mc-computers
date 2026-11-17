package dev.computah.registry;

import dev.computah.Computah;
import dev.computah.menu.DiskDriveMenu;
import dev.computah.menu.HostMenu;
import dev.computah.menu.RackMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> REGISTER = DeferredRegister.create(Registries.MENU, Computah.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<HostMenu>> HOST = REGISTER.register("host", () -> IMenuTypeExtension.create(HostMenu::fromNetwork));
    public static final DeferredHolder<MenuType<?>, MenuType<RackMenu>> RACK = REGISTER.register("rack", () -> IMenuTypeExtension.create(RackMenu::fromNetwork));
    public static final DeferredHolder<MenuType<?>, MenuType<DiskDriveMenu>> DISK_DRIVE = REGISTER.register("disk_drive", () -> IMenuTypeExtension.create(DiskDriveMenu::fromNetwork));

    private ModMenus() {
    }
}
