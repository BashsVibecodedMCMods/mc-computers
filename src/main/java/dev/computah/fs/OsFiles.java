package dev.computah.fs;

import dev.computah.Computah;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * The files of the Computah OS disk: {@code data/computah/lua/os/**} from the mod and data packs (so packs can add or
 * replace programs). Loaded when the server starts and on /reload.
 */
public final class OsFiles {
    private static final String ROOT = "lua/os";
    private static volatile Map<String, byte[]> files = Map.of();

    private OsFiles() {
    }

    public static Map<String, byte[]> files() {
        return files;
    }

    public static RamFileSystem fileSystem() {
        return RamFileSystem.readOnly(files);
    }

    public static void load(ResourceManager manager) {
        Map<String, byte[]> out = new TreeMap<>();
        for (Map.Entry<ResourceLocation, Resource> e : manager.listResources(ROOT, p -> true).entrySet()) {
            if (!e.getKey().getNamespace().equals(Computah.MOD_ID)) continue;
            String path = e.getKey().getPath().substring(ROOT.length() + 1);
            try (InputStream in = e.getValue().open()) {
                out.put(path, in.readAllBytes());
            } catch (IOException ex) {
                Computah.LOG.warn("Failed to read OS file {}", e.getKey(), ex);
            }
        }
        addBios(out);
        files = Map.copyOf(out);
        Computah.LOG.info("Loaded {} Computah OS files", out.size());
    }

    /** Loads straight from a directory (unit tests). */
    public static void loadDirectory(Path dir) throws IOException {
        Map<String, byte[]> out = new TreeMap<>();
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                if (Files.isRegularFile(p)) out.put(dir.relativize(p).toString().replace('\\', '/'), Files.readAllBytes(p));
            }
        }
        addBios(out);
        files = Map.copyOf(out);
    }

    /** The stock BIOS is on the disk as /lib/bios.lua (for `flash --bios`), from the same file the BIOS recipe uses. */
    private static void addBios(Map<String, byte[]> out) {
        out.putIfAbsent("lib/bios.lua", dev.computah.item.PartItem.biosCode().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
