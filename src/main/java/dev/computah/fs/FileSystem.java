package dev.computah.fs;

import java.io.IOException;
import java.util.List;

/**
 * A file tree behind a {@code filesystem} component. Paths are already normalized: relative to the root, separated
 * by "/", with no "." or ".." parts; the root is "". Implementations must be thread-safe.
 */
public interface FileSystem {
    enum Mode {READ, WRITE, APPEND}

    boolean exists(String path);

    boolean isDirectory(String path);

    long size(String path);

    /** Milliseconds since the epoch, or 0. */
    long lastModified(String path);

    /** Entries of a directory; sub-directories end with "/". Null if it isn't a directory. */
    List<String> list(String path);

    boolean makeDirectory(String path) throws IOException;

    boolean remove(String path) throws IOException;

    boolean rename(String from, String to) throws IOException;

    Handle open(String path, Mode mode) throws IOException;

    long spaceUsed();

    long spaceTotal();

    boolean isReadOnly();

    /** An open file. */
    interface Handle extends AutoCloseable {
        /** Reads up to n bytes; null at end of file. */
        byte[] read(int n) throws IOException;

        void write(byte[] data) throws IOException;

        /** whence is "set", "cur" or "end". Returns the new position. */
        long seek(String whence, long offset) throws IOException;

        @Override
        void close();
    }
}
