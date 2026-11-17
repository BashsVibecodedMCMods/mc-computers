package dev.computah.net;

import dev.computah.block.KeyboardBlockEntity;
import dev.computah.block.ScreenBlockEntity;
import dev.computah.component.ScreenComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-side handling of client payloads. */
public final class ServerHandlers {
    private static final double MAX_DISTANCE_SQ = 24 * 24;

    private ServerHandlers() {
    }

    public static void screenInput(ScreenInputPayload p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        if (!player.level().isLoaded(p.pos())) return;
        if (!(player.level().getBlockEntity(p.pos()) instanceof ScreenBlockEntity be)) return;
        ScreenBlockEntity origin = be.origin();
        if (origin == null) return;
        if (p.kind() == ScreenInputPayload.REQUEST) {
            if (player.distanceToSqr(p.pos().getCenter()) < 128 * 128) origin.sendSnapshot(player);
            return;
        }
        if (player.distanceToSqr(p.pos().getCenter()) > MAX_DISTANCE_SQ) return;
        String name = player.getGameProfile().getName();
        switch (p.kind()) {
            case ScreenInputPayload.KEY_DOWN, ScreenInputPayload.KEY_UP -> {
                String signal = p.kind() == ScreenInputPayload.KEY_DOWN ? "key_down" : "key_up";
                for (KeyboardBlockEntity k : origin.keyboardEntities()) {
                    k.component().signalAll(signal, k.component().address(), p.a(), p.b(), name);
                }
            }
            case ScreenInputPayload.CLIPBOARD -> {
                for (KeyboardBlockEntity k : origin.keyboardEntities()) {
                    k.component().signalAll("clipboard", k.component().address(), p.text(), name);
                }
            }
            case ScreenInputPayload.TOUCH, ScreenInputPayload.DRAG, ScreenInputPayload.DROP, ScreenInputPayload.SCROLL -> {
                ScreenComponent c = origin.component();
                if (c == null || origin.tier() < 2) return;
                String signal = switch (p.kind()) {
                    case ScreenInputPayload.TOUCH -> "touch";
                    case ScreenInputPayload.DRAG -> "drag";
                    case ScreenInputPayload.DROP -> "drop";
                    default -> "scroll";
                };
                Object x = c.precise() ? p.x() : (Object) (int) Math.floor(p.x());
                Object y = c.precise() ? p.y() : (Object) (int) Math.floor(p.y());
                c.signalAll(signal, c.address(), x, y, p.a(), name);
            }
            default -> {
            }
        }
    }
}
