package dev.computah.item;

import dev.computah.Computah;
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
    public static final long[] RAM_SIZES = {0, 512 * 1024, 1024 * 1024, 2048 * 1024, 4096 * 1024};
    public static final long[] HDD_SIZES = {0, 1024 * 1024, 2048 * 1024, 4096 * 1024};
    public static final long FLOPPY_SIZE = 512 * 1024;
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

    public static String biosCode() {
        try (InputStream in = PartItem.class.getResourceAsStream("/data/computah/lua/bios.lua")) {
            return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Computah.LOG.error("Failed to read bios.lua", e);
            return "";
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ChatFormatting g = ChatFormatting.GRAY;
        switch (kind) {
            case CPU -> tooltip.add(Component.translatable("tooltip.computah.cpu", CPU_COMPONENTS[tier]).withStyle(g));
            case RAM -> tooltip.add(Component.translatable("tooltip.computah.ram", RAM_SIZES[tier] / 1024).withStyle(g));
            case HDD -> tooltip.add(Component.translatable("tooltip.computah.capacity", HDD_SIZES[tier] / 1024).withStyle(g));
            case FLOPPY -> tooltip.add(Component.translatable("tooltip.computah.capacity", FLOPPY_SIZE / 1024).withStyle(g));
            case OS_FLOPPY -> tooltip.add(Component.translatable("tooltip.computah.os_floppy").withStyle(g));
            case GPU -> tooltip.add(Component.translatable("tooltip.computah.gpu" + tier).withStyle(g));
            case WIRELESS_CARD -> tooltip.add(Component.translatable("tooltip.computah.wireless" + tier).withStyle(g));
            case NETWORK_CARD -> tooltip.add(Component.translatable("tooltip.computah.network").withStyle(g));
            case REDSTONE_CARD -> tooltip.add(Component.translatable("tooltip.computah.redstone").withStyle(g));
            case EEPROM -> {
                ModComponents.EepromData d = stack.get(ModComponents.EEPROM.get());
                if (d != null) {
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
