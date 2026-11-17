package dev.computah.registry;

import dev.computah.Computah;
import dev.computah.item.LinkerItem;
import dev.computah.item.PartItem;
import dev.computah.item.PartKind;
import dev.computah.item.ServerItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

public final class ModItems {
    public static final DeferredRegister.Items REGISTER = DeferredRegister.createItems(Computah.MOD_ID);
    /** Everything, in creative tab order. */
    public static final List<DeferredItem<? extends Item>> ALL = new ArrayList<>();

    private static <T extends Item> DeferredItem<T> add(DeferredItem<T> item) {
        ALL.add(item);
        return item;
    }

    private static DeferredItem<BlockItem> block(String name, java.util.function.Supplier<? extends net.minecraft.world.level.block.Block> block) {
        return add(REGISTER.register(name, () -> new BlockItem(block.get(), new Item.Properties())));
    }

    private static DeferredItem<PartItem> part(String name, PartKind kind, int tier) {
        return add(REGISTER.register(name, () -> new PartItem(new Item.Properties().stacksTo(kind == PartKind.CPU || kind == PartKind.RAM ? 64 : 1)
                .rarity(tier >= 3 ? Rarity.RARE : tier == 2 ? Rarity.UNCOMMON : Rarity.COMMON), kind, tier)));
    }

    public static final DeferredItem<BlockItem> CASE1 = block("case1", ModBlocks.CASE1);
    public static final DeferredItem<BlockItem> CASE2 = block("case2", ModBlocks.CASE2);
    public static final DeferredItem<BlockItem> CASE3 = block("case3", ModBlocks.CASE3);
    public static final DeferredItem<BlockItem> SCREEN1 = block("screen1", ModBlocks.SCREEN1);
    public static final DeferredItem<BlockItem> SCREEN2 = block("screen2", ModBlocks.SCREEN2);
    public static final DeferredItem<BlockItem> SCREEN3 = block("screen3", ModBlocks.SCREEN3);
    public static final DeferredItem<BlockItem> KEYBOARD = block("keyboard", ModBlocks.KEYBOARD);
    public static final DeferredItem<BlockItem> DISK_DRIVE = block("disk_drive", ModBlocks.DISK_DRIVE);
    public static final DeferredItem<BlockItem> CABLE = block("cable", ModBlocks.CABLE);
    public static final DeferredItem<BlockItem> RELAY = block("relay", ModBlocks.RELAY);
    public static final DeferredItem<BlockItem> RACK = block("rack", ModBlocks.RACK);
    public static final DeferredItem<BlockItem> ADAPTER = block("adapter", ModBlocks.ADAPTER);
    public static final DeferredItem<BlockItem> REDSTONE_IO = block("redstone_io", ModBlocks.REDSTONE_IO);

    public static final DeferredItem<PartItem> CPU1 = part("cpu1", PartKind.CPU, 1);
    public static final DeferredItem<PartItem> CPU2 = part("cpu2", PartKind.CPU, 2);
    public static final DeferredItem<PartItem> CPU3 = part("cpu3", PartKind.CPU, 3);
    public static final DeferredItem<PartItem> RAM1 = part("ram1", PartKind.RAM, 1);
    public static final DeferredItem<PartItem> RAM2 = part("ram2", PartKind.RAM, 2);
    public static final DeferredItem<PartItem> RAM3 = part("ram3", PartKind.RAM, 3);
    public static final DeferredItem<PartItem> RAM4 = part("ram4", PartKind.RAM, 4);
    public static final DeferredItem<PartItem> HDD1 = part("hdd1", PartKind.HDD, 1);
    public static final DeferredItem<PartItem> HDD2 = part("hdd2", PartKind.HDD, 2);
    public static final DeferredItem<PartItem> HDD3 = part("hdd3", PartKind.HDD, 3);
    public static final DeferredItem<PartItem> FLOPPY = part("floppy", PartKind.FLOPPY, 1);
    public static final DeferredItem<PartItem> OS_FLOPPY = part("os_floppy", PartKind.OS_FLOPPY, 1);
    public static final DeferredItem<PartItem> EEPROM = add(REGISTER.register("eeprom", () -> new PartItem(new Item.Properties().stacksTo(1)
            .component(ModComponents.EEPROM.get(), ModComponents.EepromData.ofCode(PartItem.biosCode(), "Lua BIOS")), PartKind.EEPROM, 1)));
    public static final DeferredItem<PartItem> GPU1 = part("gpu1", PartKind.GPU, 1);
    public static final DeferredItem<PartItem> GPU2 = part("gpu2", PartKind.GPU, 2);
    public static final DeferredItem<PartItem> GPU3 = part("gpu3", PartKind.GPU, 3);
    public static final DeferredItem<PartItem> NETWORK_CARD = part("network_card", PartKind.NETWORK_CARD, 1);
    public static final DeferredItem<PartItem> WIRELESS_CARD1 = part("wireless_card1", PartKind.WIRELESS_CARD, 1);
    public static final DeferredItem<PartItem> WIRELESS_CARD2 = part("wireless_card2", PartKind.WIRELESS_CARD, 2);
    public static final DeferredItem<PartItem> REDSTONE_CARD = part("redstone_card", PartKind.REDSTONE_CARD, 1);
    public static final DeferredItem<ServerItem> SERVER = add(REGISTER.register("server", () -> new ServerItem(new Item.Properties())));
    public static final DeferredItem<LinkerItem> LINKER = add(REGISTER.register("linker", () -> new LinkerItem(new Item.Properties())));

    private ModItems() {
    }
}
