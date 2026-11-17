package dev.computah.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.computah.item.LinkerItem;
import dev.computah.net.LinkViewPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;

/** Draws lines from the linker's selected host to its linked blocks while the linker is held. */
public final class LinkLineRenderer {
    private static @Nullable LinkViewPayload view;

    private LinkLineRenderer() {
    }

    static void set(LinkViewPayload p) {
        view = p.visible() ? p : null;
    }

    public static void clear() {
        view = null;
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || view == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!(mc.player.getMainHandItem().getItem() instanceof LinkerItem) && !(mc.player.getOffhandItem().getItem() instanceof LinkerItem)) return;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Vec3 from = Vec3.atCenterOf(view.host());
        for (BlockPos t : view.targets()) {
            Vec3 to = Vec3.atCenterOf(t);
            Vec3 n = to.subtract(from).normalize();
            var last = pose.last();
            vc.addVertex(last, (float) from.x, (float) from.y, (float) from.z).setColor(80, 220, 255, 255).setNormal(last, (float) n.x, (float) n.y, (float) n.z);
            vc.addVertex(last, (float) to.x, (float) to.y, (float) to.z).setColor(80, 220, 255, 255).setNormal(last, (float) n.x, (float) n.y, (float) n.z);
        }
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }
}
