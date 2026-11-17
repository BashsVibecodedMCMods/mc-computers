package dev.computah.item;

import dev.computah.Config;
import dev.computah.block.CaseBlockEntity;
import dev.computah.block.RackBlockEntity;
import dev.computah.link.LinkSavedData;
import dev.computah.net.LinkViewPayload;
import dev.computah.registry.ModComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Links blocks to a computer or rack wirelessly. Right-click a case or rack to select it, then right-click blocks
 * (screens, disk drives, chests, tanks, ...) to link or unlink them. Sneak-right-click the air to clear the
 * selection. The host needs a wireless card to use its links.
 */
public class LinkerItem extends Item {
    public LinkerItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static boolean isHost(BlockEntity be) {
        return be instanceof CaseBlockEntity || be instanceof RackBlockEntity;
    }

    /** Runs before the clicked block's own interaction, so clicking a case selects it instead of opening its GUI. */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext ctx) {
        return useOn(ctx);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        ItemStack stack = ctx.getItemInHand();
        BlockPos pos = ctx.getClickedPos();
        if (level.isClientSide || player == null) return InteractionResult.SUCCESS;
        GlobalPos here = GlobalPos.of(level.dimension(), pos);
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null && isHost(be)) {
            stack.set(ModComponents.SELECTED_HOST.get(), here);
            player.displayClientMessage(Component.translatable("message.computah.linker.selected", pos.toShortString()), true);
            sendView((ServerPlayer) player, here);
            return InteractionResult.CONSUME;
        }
        GlobalPos host = stack.get(ModComponents.SELECTED_HOST.get());
        if (host == null) {
            player.displayClientMessage(Component.translatable("message.computah.linker.none"), true);
            return InteractionResult.CONSUME;
        }
        LinkSavedData data = LinkSavedData.get(level.getServer());
        boolean was = data.isLinked(host, here);
        boolean now = data.toggle(host, here, Config.get(Config.MAX_LINKS));
        if (!was && !now) {
            player.displayClientMessage(Component.translatable("message.computah.linker.full", Config.get(Config.MAX_LINKS)), true);
        } else {
            player.displayClientMessage(Component.translatable(now ? "message.computah.linker.linked" : "message.computah.linker.unlinked",
                    level.getBlockState(pos).getBlock().getName(), pos.toShortString()), true);
        }
        sendView((ServerPlayer) player, host);
        return InteractionResult.CONSUME;
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && stack.has(ModComponents.SELECTED_HOST.get())) {
            if (!level.isClientSide) {
                stack.remove(ModComponents.SELECTED_HOST.get());
                player.displayClientMessage(Component.translatable("message.computah.linker.cleared"), true);
                PacketDistributor.sendToPlayer((ServerPlayer) player, new LinkViewPayload(BlockPos.ZERO, List.of(), false));
            }
            return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return super.use(level, player, hand);
    }

    /** Tells the player's client which links to draw. */
    public static void sendView(ServerPlayer player, GlobalPos host) {
        List<BlockPos> targets = new ArrayList<>();
        if (host.dimension().equals(player.level().dimension())) {
            for (GlobalPos t : LinkSavedData.get(player.server).targets(host)) {
                if (t.dimension().equals(host.dimension())) targets.add(t.pos());
            }
        }
        PacketDistributor.sendToPlayer(player, new LinkViewPayload(host.pos(), targets, host.dimension().equals(player.level().dimension())));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        GlobalPos host = stack.get(ModComponents.SELECTED_HOST.get());
        if (host != null) {
            tooltip.add(Component.translatable("tooltip.computah.linker.host", host.pos().toShortString(), host.dimension().location().toString()).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.computah.linker.help").withStyle(ChatFormatting.GRAY));
        }
    }
}
