package dev.computah.item;

import dev.computah.menu.HostMenu;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** A rack-mounted server. Right-click it in hand to install parts; put it in a rack to run it. */
public class ServerItem extends Item {
    public ServerItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static List<ItemStack> parts(ItemStack server) {
        ItemContainerContents c = server.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        NonNullList<ItemStack> list = NonNullList.withSize(PartKind.SERVER.length, ItemStack.EMPTY);
        c.copyInto(list);
        return list;
    }

    public static void setParts(ItemStack server, List<ItemStack> parts) {
        List<ItemStack> copy = new ArrayList<>();
        for (ItemStack s : parts) copy.add(s.copy());
        server.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(copy));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            player.openMenu(new SimpleMenuProvider((id, inv, p) -> HostMenu.forItem(id, inv, hand), stack.getHoverName()),
                    buf -> HostMenu.writeItem(buf, hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        long n = parts(stack).stream().filter(s -> !s.isEmpty()).count();
        tooltip.add(Component.translatable("tooltip.computah.server", n).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
