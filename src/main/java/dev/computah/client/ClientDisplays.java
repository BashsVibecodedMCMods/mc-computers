package dev.computah.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.computah.Computah;
import dev.computah.display.Compositor;
import dev.computah.display.DisplayBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** Client copies of monitor displays, and the textures they are drawn with. */
public final class ClientDisplays {
    private static final Map<BlockPos, Display> DISPLAYS = new HashMap<>();
    private static int nextId;

    private ClientDisplays() {
    }

    public static final class Display {
        public final DisplayBuffer buffer = new DisplayBuffer(DisplayBuffer.Tier.of(1));
        public boolean on = true;
        private @Nullable DynamicTexture texture;
        private @Nullable ResourceLocation location;
        private int width, height, version = -1;
        private boolean blink;
        private long lastCompose;
        private int[] pixels;

        /** The texture with the current contents, re-rendered when the display changed. */
        public ResourceLocation texture() {
            int w = Compositor.width(buffer), h = Compositor.height(buffer);
            boolean blinkNow = (System.currentTimeMillis() / 500) % 2 == 0;
            if (texture == null || w != width || h != height) {
                release();
                width = w;
                height = h;
                texture = new DynamicTexture(new NativeImage(w, h, false));
                location = ResourceLocation.fromNamespaceAndPath(Computah.MOD_ID, "display/" + nextId++);
                Minecraft.getInstance().getTextureManager().register(location, texture);
                version = -1;
            }
            int v = buffer.version();
            long now = System.currentTimeMillis();
            boolean blinkChanged = blinkNow != blink && buffer.hasBlink();
            if ((v != version || blinkChanged) && now - lastCompose >= 40) {
                lastCompose = now;
                version = v;
                blink = blinkNow;
                pixels = Compositor.render(buffer, pixels, blinkNow, 0xFFFFFF);
                NativeImage img = texture.getPixels();
                if (img != null) {
                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) {
                            int argb = pixels[y * w + x];
                            // NativeImage stores ABGR.
                            img.setPixelRGBA(x, y, (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16));
                        }
                    }
                    texture.upload();
                }
            }
            return location;
        }

        public int width() {
            return Compositor.width(buffer);
        }

        public int height() {
            return Compositor.height(buffer);
        }

        void release() {
            if (location != null) Minecraft.getInstance().getTextureManager().release(location);
            texture = null;
            location = null;
        }
    }

    public static @Nullable Display get(BlockPos pos) {
        return DISPLAYS.get(pos);
    }

    public static Display getOrCreate(BlockPos pos) {
        return DISPLAYS.computeIfAbsent(pos.immutable(), p -> new Display());
    }

    public static void remove(BlockPos pos) {
        Display d = DISPLAYS.remove(pos);
        if (d != null) d.release();
    }

    public static void clear() {
        for (Display d : DISPLAYS.values()) d.release();
        DISPLAYS.clear();
    }
}
