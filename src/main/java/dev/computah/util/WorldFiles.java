package dev.computah.util;

import dev.computah.Computah;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Files Computah keeps in the world folder ({@code <world>/computah/<folder>/<name>}). */
public final class WorldFiles {
    private WorldFiles() {
    }

    public static Path path(MinecraftServer server, String folder, String name) {
        return server.getWorldPath(LevelResource.ROOT).resolve("computah").resolve(folder).resolve(name);
    }

    /** Writes a file so that a crash never leaves half of it behind. */
    public static boolean write(Path path, byte[] data) {
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.write(tmp, data);
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            Computah.LOG.error("Failed to write {}", path, e);
            return false;
        }
    }

    public static @Nullable byte[] read(Path path) {
        try {
            return Files.exists(path) ? Files.readAllBytes(path) : null;
        } catch (IOException e) {
            Computah.LOG.error("Failed to read {}", path, e);
            return null;
        }
    }

    public static void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            Computah.LOG.warn("Failed to delete {}", path, e);
        }
    }
}
