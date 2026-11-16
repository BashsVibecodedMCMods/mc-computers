package dev.computah.fs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * A writable file system stored in a real directory, used for hard drives and floppies
 * ({@code <world>/computah/fs/<address>}). Space is limited to the drive's capacity.
 */
public class DirectoryFileSystem implements FileSystem {
    private static final long ENTRY_COST = 512;

    private final Path root;
    private final long capacity;
    private long used = -1;

    public DirectoryFileSystem(Path root, long capacity) {
        this.root = root.toAbsolutePath().normalize();
        this.capacity = capacity;
    }

    private Path resolve(String path) throws IOException {
        Path p = root.resolve(path).normalize();
        if (!p.startsWith(root)) throw new IOException("invalid path");
        return p;
    }

    private Path resolveQuiet(String path) {
        try {
            return resolve(path);
        } catch (IOException e) {
            return null;
        }
    }

    private synchronized long used() {
        if (used < 0) {
            used = 0;
            if (Files.isDirectory(root)) {
                try (Stream<Path> s = Files.walk(root)) {
                    s.forEach(p -> {
                        if (p.equals(root)) return;
                        used += ENTRY_COST;
                        if (Files.isRegularFile(p)) {
                            try {
                                used += Files.size(p);
                            } catch (IOException ignored) {
                            }
                        }
                    });
                } catch (IOException ignored) {
                }
            }
        }
        return used;
    }

    private synchronized void reserve(long bytes) throws IOException {
        if (bytes > 0 && used() + bytes > capacity) throw new IOException("not enough space");
        used = used() + bytes;
    }

    private void ensureRoot() throws IOException {
        Files.createDirectories(root);
    }

    @Override
    public boolean exists(String path) {
        Path p = resolveQuiet(path);
        return p != null && (path.isEmpty() || Files.exists(p));
    }

    @Override
    public boolean isDirectory(String path) {
        Path p = resolveQuiet(path);
        return p != null && (path.isEmpty() || Files.isDirectory(p));
    }

    @Override
    public long size(String path) {
        Path p = resolveQuiet(path);
        try {
            return p != null && Files.isRegularFile(p) ? Files.size(p) : 0;
        } catch (IOException e) {
            return 0;
        }
    }

    @Override
    public long lastModified(String path) {
        Path p = resolveQuiet(path);
        try {
            return p != null && Files.exists(p) ? Files.getLastModifiedTime(p).toMillis() : 0;
        } catch (IOException e) {
            return 0;
        }
    }

    @Override
    public List<String> list(String path) {
        Path p = resolveQuiet(path);
        if (p == null) return null;
        if (path.isEmpty() && !Files.exists(p)) return new ArrayList<>();
        if (!Files.isDirectory(p)) return null;
        List<String> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(p)) {
            s.sorted().forEach(c -> out.add(c.getFileName().toString() + (Files.isDirectory(c) ? "/" : "")));
        } catch (IOException e) {
            return null;
        }
        return out;
    }

    @Override
    public synchronized boolean makeDirectory(String path) throws IOException {
        Path p = resolve(path);
        if (path.isEmpty() || Files.exists(p)) return false;
        ensureRoot();
        Path parent = p.getParent();
        if (!Files.isDirectory(parent) && (parent.equals(root) || !makeDirectory(root.relativize(parent).toString().replace('\\', '/')))) {
            return false;
        }
        reserve(ENTRY_COST);
        Files.createDirectory(p);
        return true;
    }

    @Override
    public synchronized boolean remove(String path) throws IOException {
        Path p = resolve(path);
        if (path.isEmpty() || !Files.exists(p)) return false;
        long[] freed = {0};
        Files.walkFileTree(p, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                freed[0] += ENTRY_COST + attrs.size();
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                freed[0] += ENTRY_COST;
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
        used = Math.max(0, used() - freed[0]);
        return true;
    }

    @Override
    public synchronized boolean rename(String from, String to) throws IOException {
        Path a = resolve(from), b = resolve(to);
        if (from.isEmpty() || to.isEmpty() || !Files.exists(a) || Files.exists(b) || b.startsWith(a)) return false;
        if (!Files.isDirectory(b.getParent())) return false;
        Files.move(a, b);
        return true;
    }

    @Override
    public synchronized Handle open(String path, Mode mode) throws IOException {
        Path p = resolve(path);
        if (path.isEmpty() || Files.isDirectory(p)) throw new IOException(path + ": is a directory");
        if (mode == Mode.READ) {
            if (!Files.isRegularFile(p)) throw new IOException(path + ": file not found");
            return new ChannelHandle(FileChannel.open(p, StandardOpenOption.READ), false);
        }
        ensureRoot();
        if (!Files.isDirectory(p.getParent())) throw new IOException(path + ": no such directory");
        if (!Files.exists(p)) reserve(ENTRY_COST);
        else if (mode == Mode.WRITE) used = Math.max(0, used() - Files.size(p));
        FileChannel ch = mode == Mode.WRITE
                ? FileChannel.open(p, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
                : FileChannel.open(p, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        if (mode == Mode.APPEND) ch.position(ch.size());
        return new ChannelHandle(ch, true);
    }

    private final class ChannelHandle implements Handle {
        private final FileChannel channel;
        private final boolean writable;

        ChannelHandle(FileChannel channel, boolean writable) {
            this.channel = channel;
            this.writable = writable;
        }

        @Override
        public byte[] read(int n) throws IOException {
            if (writable) throw new IOException("file is not open for reading");
            ByteBuffer buf = ByteBuffer.allocate(Math.max(0, n));
            int r = channel.read(buf);
            if (r < 0) return null;
            byte[] out = new byte[r];
            buf.flip();
            buf.get(out);
            return out;
        }

        @Override
        public void write(byte[] data) throws IOException {
            if (!writable) throw new IOException("file is not open for writing");
            synchronized (DirectoryFileSystem.this) {
                long growth = Math.max(0, channel.position() + data.length - channel.size());
                reserve(growth);
                channel.write(ByteBuffer.wrap(data));
            }
        }

        @Override
        public long seek(String whence, long offset) throws IOException {
            long base = switch (whence) {
                case "set" -> 0;
                case "cur" -> channel.position();
                case "end" -> channel.size();
                default -> throw new IOException("invalid mode");
            };
            long np = base + offset;
            if (np < 0) throw new IOException("invalid offset");
            channel.position(np);
            return np;
        }

        @Override
        public void close() {
            try {
                channel.close();
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public long spaceUsed() {
        return used();
    }

    @Override
    public long spaceTotal() {
        return capacity;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }
}
