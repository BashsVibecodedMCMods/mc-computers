package dev.computah.assets;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.computah.registry.ModBlocks;
import dev.computah.registry.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Checks that every block and item has a blockstate/model/texture/name, and that recipes point at real items. */
public class AssetsTest {
    private static JsonObject json(String path) {
        try (InputStream in = AssetsTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            throw new AssertionError("bad json " + path, e);
        }
    }

    private static boolean exists(String path) {
        return AssetsTest.class.getResource(path) != null;
    }

    private static String file(String location, String folder, String ext) {
        ResourceLocation rl = ResourceLocation.parse(location);
        return "/assets/" + rl.getNamespace() + "/" + folder + "/" + rl.getPath() + ext;
    }

    /** Checks a model and its parents; textures in our namespace must exist. */
    private static void checkModel(String location, Set<String> seen) {
        if (!seen.add(location)) return;
        ResourceLocation rl = ResourceLocation.parse(location);
        if (!rl.getNamespace().equals("computah")) return;
        JsonObject model = json(file(location, "models", ".json"));
        if (model.has("parent")) checkModel(model.get("parent").getAsString(), seen);
        if (model.has("textures")) {
            for (Map.Entry<String, JsonElement> e : model.getAsJsonObject("textures").entrySet()) {
                String t = e.getValue().getAsString();
                if (t.startsWith("#")) continue;
                if (ResourceLocation.parse(t).getNamespace().equals("computah")) {
                    assertTrue(exists(file(t, "textures", ".png")), "texture " + t + " used by " + location);
                }
            }
        }
    }

    private static void collectModels(JsonElement e, List<String> out) {
        if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("model") && o.get("model").isJsonPrimitive()) out.add(o.get("model").getAsString());
            for (Map.Entry<String, JsonElement> x : o.entrySet()) collectModels(x.getValue(), out);
        } else if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) collectModels(x, out);
        }
    }

    @Test
    public void blocksAndItemsHaveAssets() {
        JsonObject lang = json("/assets/computah/lang/en_us.json");
        Set<String> seen = new HashSet<>();
        for (var holder : ModBlocks.REGISTER.getEntries()) {
            String name = holder.getId().getPath();
            JsonObject state = json("/assets/computah/blockstates/" + name + ".json");
            List<String> models = new ArrayList<>();
            collectModels(state, models);
            assertFalse(models.isEmpty(), "blockstate " + name + " has models");
            for (String m : models) checkModel(m, seen);
            assertTrue(lang.has("block.computah." + name), "lang for block " + name);
            assertTrue(exists("/data/computah/loot_table/blocks/" + name + ".json"), "loot table for " + name);
        }
        for (var item : ModItems.ALL) {
            String name = item.getId().getPath();
            checkModel("computah:item/" + name, seen);
            if (!(item.get() instanceof net.minecraft.world.item.BlockItem)) {
                assertTrue(lang.has("item.computah." + name), "lang for item " + name);
            }
        }
    }

    @Test
    public void recipesUseRealItems() throws Exception {
        Path dir = Path.of(AssetsTest.class.getResource("/data/computah/recipe").toURI());
        Set<String> crafted = new HashSet<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path p : (Iterable<Path>) files::iterator) {
                JsonObject r = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
                List<String> ids = new ArrayList<>();
                collectIds(r, ids);
                for (String id : ids) {
                    assertTrue(BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(id)), p.getFileName() + " uses unknown item " + id);
                }
                crafted.add(r.getAsJsonObject("result").get("id").getAsString());
            }
        }
        for (var item : ModItems.ALL) assertTrue(crafted.contains(item.getId().toString()), "no recipe for " + item.getId());
    }

    private static void collectIds(JsonElement e, List<String> out) {
        if (e.isJsonObject()) {
            for (Map.Entry<String, JsonElement> x : e.getAsJsonObject().entrySet()) {
                if ((x.getKey().equals("item") || x.getKey().equals("id")) && x.getValue().isJsonPrimitive()) out.add(x.getValue().getAsString());
                else collectIds(x.getValue(), out);
            }
        } else if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) collectIds(x, out);
        }
    }

    @Test
    public void soundsExist() {
        JsonObject sounds = json("/assets/computah/sounds.json");
        for (Map.Entry<String, JsonElement> e : sounds.entrySet()) {
            for (JsonElement s : e.getValue().getAsJsonObject().getAsJsonArray("sounds")) {
                assertTrue(exists(file(s.getAsString(), "sounds", ".ogg")), "sound " + s.getAsString());
            }
        }
    }
}
