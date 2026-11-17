package dev.computah.item;

import dev.computah.Computah;
import dev.computah.Config;
import dev.computah.registry.ModItems;
import dev.computah.component.EepromComponent;
import dev.computah.registry.ModComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** A computer part: CPU, RAM, drive, EEPROM or card. */
public class PartItem extends Item {
    /** Default sizes in KiB (the server config can change them: see {@link Config}). */
    public static final int[] DEFAULT_RAM_KIB = {512, 1024, 2048, 4096};
    public static final int[] DEFAULT_HDD_KIB = {1024, 2048, 4096};
    public static final int[] CPU_COMPONENTS = {0, 8, 12, 16};
    public static final double[] CPU_BUDGET = {0, 1.0, 1.5, 2.0};

    private final PartKind kind;
    private final int tier;

    public PartItem(Properties properties, PartKind kind, int tier) {
        super(properties);
        this.kind = kind;
        this.tier = tier;
    }

    public PartKind kind() {
        return kind;
    }

    public int tier() {
        return tier;
    }

    private static long kib(java.util.List<? extends Integer> list, int[] defaults, int tier) {
        int i = Math.max(1, tier) - 1;
        if (list != null && i < list.size() && list.get(i) != null) return list.get(i) * 1024L;
        return defaults[Math.min(i, defaults.length - 1)] * 1024L;
    }

    /** Memory of a RAM module of the given tier (1-4), in bytes. */
    public static long ramSize(int tier) {
        return kib(Config.get(Config.RAM_KIB), DEFAULT_RAM_KIB, tier);
    }

    /** Capacity of a hard drive of the given tier (1-3), in bytes. */
    public static long hddSize(int tier) {
        return kib(Config.get(Config.HDD_KIB), DEFAULT_HDD_KIB, tier);
    }

    /** Capacity of a floppy, in bytes. */
    public static long floppySize() {
        return Config.get(Config.FLOPPY_KIB) * 1024L;
    }

    /** Size of a computer's temporary filesystem, in bytes. */
    public static long tmpSize() {
        return Config.get(Config.TMPFS_KIB) * 1024L;
    }

    public static boolean isOsFloppy(ItemStack stack) {
        return stack.has(ModComponents.OS_DISK.get());
    }

    /** A floppy holding Computah OS (the same item as a blank floppy, marked with data). */
    public static ItemStack osFloppy() {
        ItemStack s = new ItemStack(ModItems.FLOPPY.get());
        s.set(ModComponents.OS_DISK.get(), net.minecraft.util.Unit.INSTANCE);
        return s;
    }

    /** A blank floppy in a dye colour. */
    public static ItemStack dyedFloppy(net.minecraft.world.item.DyeColor color) {
        ItemStack s = new ItemStack(ModItems.FLOPPY.get());
        s.set(ModComponents.FLOPPY_COLOR.get(), color);
        return s;
    }

    /** Writes the Lua BIOS into an EEPROM stack. */
    public static ItemStack withBios(ItemStack stack) {
        stack.set(ModComponents.EEPROM.get(), ModComponents.EepromData.ofCode(biosCode(), "Lua BIOS"));
        return stack;
    }

    /** An EEPROM holding the Lua BIOS (the same item as a blank EEPROM). */
    public static ItemStack biosEeprom() {
        return withBios(new ItemStack(ModItems.EEPROM.get()));
    }

    public static String biosCode() {
        try (InputStream in = PartItem.class.getResourceAsStream("/data/computah/lua/bios.lua")) {
            return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Computah.LOG.error("Failed to read bios.lua", e);
            return "";
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        if (kind == PartKind.FLOPPY && isOsFloppy(stack)) return Component.translatable("item.computah.floppy.os");
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ChatFormatting g = ChatFormatting.GRAY;
        switch (kind) {
            case CPU -> tooltip.add(Component.translatable("tooltip.computah.cpu", CPU_COMPONENTS[tier]).withStyle(g));
            case RAM -> tooltip.add(Component.translatable("tooltip.computah.ram", ramSize(tier) / 1024).withStyle(g));
            case HDD -> tooltip.add(Component.translatable("tooltip.computah.capacity", hddSize(tier) / 1024).withStyle(g));
            case FLOPPY -> {
                net.minecraft.world.item.DyeColor color = stack.get(ModComponents.FLOPPY_COLOR.get());
                if (color != null) {
                    tooltip.add(Component.translatable("color.minecraft." + color.getName()).withStyle(g));
                }
                if (isOsFloppy(stack)) tooltip.add(Component.translatable("tooltip.computah.os_floppy").withStyle(g));
                else tooltip.add(Component.translatable("tooltip.computah.capacity", floppySize() / 1024).withStyle(g));
            }
            case GPU -> tooltip.add(Component.translatable("tooltip.computah.gpu" + tier).withStyle(g));
            case WIRELESS_CARD -> tooltip.add(Component.translatable("tooltip.computah.wireless" + tier).withStyle(g));
            case NETWORK_CARD -> tooltip.add(Component.translatable("tooltip.computah.network").withStyle(g));
            case REDSTONE_CARD -> tooltip.add(Component.translatable("tooltip.computah.redstone").withStyle(g));
            case EEPROM -> {
                ModComponents.EepromData d = stack.get(ModComponents.EEPROM.get());
                if (d == null || d.contents().code().length == 0 && d.contents().data().length == 0) {
                    tooltip.add(Component.translatable("tooltip.computah.eeprom_empty").withStyle(g));
                } else {
                    EepromComponent.Contents c = d.contents();
                    tooltip.add(Component.literal(c.label()).withStyle(ChatFormatting.YELLOW));
                    tooltip.add(Component.translatable("tooltip.computah.eeprom", c.code().length, EepromComponent.CODE_SIZE).withStyle(g));
                    if (c.readOnly()) tooltip.add(Component.translatable("tooltip.computah.readonly").withStyle(ChatFormatting.RED));
                }
            }
        }
        String label = stack.get(ModComponents.LABEL.get());
        if (label != null) tooltip.add(Component.literal(label).withStyle(ChatFormatting.YELLOW));
        String address = stack.get(ModComponents.ADDRESS.get());
        if (address != null && flag.isAdvanced()) tooltip.add(Component.literal(address).withStyle(ChatFormatting.DARK_GRAY));
        else if (address != null) tooltip.add(Component.literal(address.substring(0, 8)).withStyle(ChatFormatting.DARK_GRAY));
    }
}
