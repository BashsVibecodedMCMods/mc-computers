package dev.computah.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.computah.block.ScreenBlockEntity;
import dev.computah.display.ScreenGeometry;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws a monitor's display on its front face: the origin block renders one quad across the whole multiblock, using
 * the same texture as the screen GUI, so what a player sees in the GUI shows in the world too.
 */
public class ScreenRenderer implements BlockEntityRenderer<ScreenBlockEntity> {
    public ScreenRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(ScreenBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!be.isOrigin()) return;
        ClientDisplays.Display d = ClientDisplays.get(be.getBlockPos());
        if (d == null || !d.on) return;
        ResourceLocation tex = d.texture();
        Direction facing = be.facing();
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.translate(-0.5, -0.5, -0.5);
        double[] r = ScreenGeometry.displayRect(be.width(), be.height(), d.width(), d.height());
        float x0 = (float) r[0], x1 = (float) (r[0] + r[2]);
        float y0 = (float) (1 - r[1]), y1 = (float) (1 - r[1] - r[3]);
        float z = 1.002f;
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = buffers.getBuffer(RenderType.text(tex));
        int full = LightTexture.FULL_BRIGHT;
        vc.addVertex(m, x0, y0, z).setColor(255, 255, 255, 255).setUv(0, 0).setLight(full);
        vc.addVertex(m, x0, y1, z).setColor(255, 255, 255, 255).setUv(0, 1).setLight(full);
        vc.addVertex(m, x1, y1, z).setColor(255, 255, 255, 255).setUv(1, 1).setLight(full);
        vc.addVertex(m, x1, y0, z).setColor(255, 255, 255, 255).setUv(1, 0).setLight(full);
        pose.popPose();
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(ScreenBlockEntity be) {
        return be.renderBox();
    }

    @Override
    public boolean shouldRenderOffScreen(ScreenBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public boolean shouldRender(ScreenBlockEntity be, Vec3 camera) {
        return Vec3.atCenterOf(be.getBlockPos()).closerThan(camera, getViewDistance());
    }
}
