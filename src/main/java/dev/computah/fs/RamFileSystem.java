package dev.computah.fs;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * A file system kept in memory. Used for each machine's tmpfs, for the read-only OS disk, and for floppy disks (which
 * are saved inside the item).
 */
public class RamFileSystem implements FileSystem {
    private static final int NODE_COST = 64;

    private static abstract class Node {
        long modified = System.currentTimeMillis();
    }

    private static final class Dir extends Node {
        final TreeMap<String, Node> children = new TreeMap<>();
    }

    private static final class File extends Node {
        byte[] data = new byte[0];
        int length;
    }

    private final Dir root = new Dir();
    private final long capacity;
    private boolean readOnly;
    private long used;
    private boolean dirty;

    public RamFileSystem(long capacity) {
        this.capacity = capacity;
    }

    /** A read-only file system holding the given files (path -> contents). */
    public static RamFileSystem readOnly(Map<String, byte[]> files) {
        RamFileSystem fs = new RamFileSystem(Long.MAX_VALUE);
        for (Map.Entry<String, byte[]> e : files.entrySet()) fs.put(e.getKey(), e.getValue());
        fs.readOnly = true;
        fs.dirty = false;
        return fs;
    }

    public synchronized void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    /** Whether anything changed since the last {@link #clearDirty()}. */
    public synchronized boolean isDirty() {
        return dirty;
    }

    public synchronized void clearDirty() {
        dirty = false;
    }

    /** Adds a file, creating parent directories, ignoring read-only and capacity. */
    public synchronized void put(String path, byte[] data) {
        String[] parts = split(path);
        Dir d = root;
        for (int i = 0; i < parts.length - 1; i++) {
            Node n = d.children.get(parts[i]);
            if (!(n instanceof Dir)) {
                Dir nd = new Dir();
                d.children.put(parts[i], nd);
                used += NODE_COST;
                n = nd;
            }
            d = (Dir) n;
        }
        File f = new File();
        f.data = data.clone();
        f.length = data.length;
        Node old = d.children.put(parts[parts.length - 1], f);
        if (old != null) used -= cost(old);
        used += cost(f);
        dirty = true;
    }

    private static String[] split(String path) {
        if (path.isEmpty()) return new String[0];
        return path.split("/");
    }

    private Node find(String path) {
        Node n = root;
        for (String p : split(path)) {
            if (!(n instanceof Dir d)) return null;
            n = d.children.get(p);
            if (n == null) return null;
        }
        return n;
    }

    private Dir parentOf(String path) {
        int i = path.lastIndexOf('/');
        Node n = i < 0 ? root : find(path.substring(0, i));
        return n instanceof Dir d ? d : null;
    }

    private static String nameOf(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static long cost(Node n) {
        if (n instanceof File f) return NODE_COST + f.length;
        long c = NODE_COST;
        for (Node x : ((Dir) n).children.values()) c += cost(x);
        return c;
    }

    @Override
    public synchronized boolean exists(String path) {
        return find(path) != null;
    }

    @Override
    public synchronized boolean isDirectory(String path) {
        return find(path) instanceof Dir;
    }

    @Override
    public synchronized long size(String path) {
        return find(path) instanceof File f ? f.length : 0;
    }

    @Override
    public synchronized long lastModified(String path) {
        Node n = find(path);
        return n == null ? 0 : n.modified;
    }

    @Override
    public synchronized List<String> list(String path) {
        if (!(find(path) instanceof Dir d)) return null;
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Node> e : d.children.entrySet()) out.add(e.getValue() instanceof Dir ? e.getKey() + "/" : e.getKey());
        return out;
    }

    @Override
    public synchronized boolean makeDirectory(String path) throws IOException {
        if (readOnly) throw new IOException("filesystem is read-only");
        if (path.isEmpty() || find(path) != null) return false;
        Dir parent = parentOf(path);
        if (parent == null) {
            int i = path.lastIndexOf('/');
            if (i < 0 || !makeDirectory(path.substring(0, i))) return false;
            parent = parentOf(path);
        }
        if (used + NODE_COST > capacity) throw new IOException("not enough space");
        parent.children.put(nameOf(path), new Dir());
        parent.modified = System.currentTimeMillis();
        used += NODE_COST;
        dirty = true;
        return true;
    }

    @Override
    public synchronized boolean remove(String path) throws IOException {
        if (readOnly) throw new IOException("filesystem is read-only");
        if (path.isEmpty()) return false;
        Dir parent = parentOf(path);
        if (parent == null) return false;
        Node n = parent.children.remove(nameOf(path));
        if (n == null) return false;
        used -= cost(n);
        parent.modified = System.currentTimeMillis();
        dirty = true;
        return true;
    }

    @Override
    public synchronized boolean rename(String from, String to) throws IOException {
        if (readOnly) throw new IOException("filesystem is read-only");
        if (from.isEmpty() || to.isEmpty() || find(to) != null || (to + "/").startsWith(from + "/")) return false;
        Dir src = parentOf(from), dst = parentOf(to);
        if (src == null || dst == null) return false;
        Node n = src.children.remove(nameOf(from));
        if (n == null) return false;
        dst.children.put(nameOf(to), n);
        dirty = true;
        return true;
    }

    @Override
    public synchronized Handle open(String path, Mode mode) throws IOException {
        Node n = find(path);
        if (mode == Mode.READ) {
            if (!(n instanceof File f)) throw new IOException(n == null ? path + ": file not found" : path + ": is a directory");
            return new RamHandle(f, false, 0);
        }
        if (readOnly) throw new IOException("filesystem is read-only");
        if (n instanceof Dir) throw new IOException(path + ": is a directory");
        File f = (File) n;
        if (f == null) {
            Dir parent = parentOf(path);
            if (parent == null || path.isEmpty()) throw new IOException(path + ": no such directory");
            if (used + NODE_COST > capacity) throw new IOException("not enough space");
            f = new File();
            parent.children.put(nameOf(path), f);
            used += NODE_COST;
        } else if (mode == Mode.WRITE) {
            used -= f.length;
            f.length = 0;
            f.data = new byte[0];
        }
        f.modified = System.currentTimeMillis();
        dirty = true;
        return new RamHandle(f, true, mode == Mode.APPEND ? f.length : 0);
    }

    private final class RamHandle implements Handle {
        private final File file;
        private final boolean writable;
        private long pos;
        private boolean closed;

        RamHandle(File file, boolean writable, long pos) {
            this.file = file;
            this.writable = writable;
            this.pos = pos;
        }

        @Override
        public byte[] read(int n) throws IOException {
            synchronized (RamFileSystem.this) {
                if (closed) throw new IOException("handle is closed");
                if (writable) throw new IOException("file is not open for reading");
                if (pos >= file.length) return null;
                int len = (int) Math.min(n, file.length - pos);
                byte[] out = Arrays.copyOfRange(file.data, (int) pos, (int) pos + len);
                pos += len;
                return out;
            }
        }

        @Override
        public void write(byte[] data) throws IOException {
            synchronized (RamFileSystem.this) {
                if (closed) throw new IOException("handle is closed");
                if (!writable) throw new IOException("file is not open for writing");
                long end = pos + data.length;
                long growth = Math.max(0, end - file.length);
                if (used + growth > capacity) throw new IOException("not enough space");
                if (end > Integer.MAX_VALUE - 8) throw new IOException("file too large");
                if (end > file.data.length) file.data = Arrays.copyOf(file.data, (int) Math.max(end, Math.min(Integer.MAX_VALUE - 8, file.data.length * 2L)));
                System.arraycopy(data, 0, file.data, (int) pos, data.length);
                pos = end;
                if (end > file.length) file.length = (int) end;
                used += growth;
                file.modified = System.currentTimeMillis();
                dirty = true;
            }
        }

        @Override
        public long seek(String whence, long offset) throws IOException {
            synchronized (RamFileSystem.this) {
                if (closed) throw new IOException("handle is closed");
                long base = switch (whence) {
                    case "set" -> 0;
                    case "cur" -> pos;
                    case "end" -> file.length;
                    default -> throw new IOException("invalid mode");
                };
                long np = base + offset;
                if (np < 0) throw new IOException("invalid offset");
                pos = np;
                return pos;
            }
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    @Override
    public synchronized long spaceUsed() {
        return used;
    }

    @Override
    public long spaceTotal() {
        return capacity;
    }

    @Override
    public synchronized boolean isReadOnly() {
        return readOnly;
    }

    // region Serialization (floppies keep their contents in the item)

    public synchronized byte[] save() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            writeDir(out, root);
            out.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void writeDir(DataOutputStream out, Dir d) throws IOException {
        out.writeInt(d.children.size());
        for (Map.Entry<String, Node> e : d.children.entrySet()) {
            out.writeUTF(e.getKey());
            out.writeLong(e.getValue().modified);
            if (e.getValue() instanceof File f) {
                out.writeBoolean(false);
                out.writeInt(f.length);
                out.write(f.data, 0, f.length);
            } else {
                out.writeBoolean(true);
                writeDir(out, (Dir) e.getValue());
            }
        }
    }

    public synchronized void load(byte[] data) throws IOException {
        root.children.clear();
        DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(data));
        readDir(in, root, 0);
        used = cost(root) - NODE_COST;
        dirty = false;
    }

    private static void readDir(DataInputStream in, Dir d, int depth) throws IOException {
        if (depth > 64) throw new IOException("too deep");
        int n = in.readInt();
        for (int i = 0; i < n; i++) {
            String name = in.readUTF();
            long modified = in.readLong();
            Node node;
            if (in.readBoolean()) {
                Dir sub = new Dir();
                readDir(in, sub, depth + 1);
                node = sub;
            } else {
                File f = new File();
                f.length = in.readInt();
                f.data = new byte[f.length];
                in.readFully(f.data);
                node = f;
            }
            node.modified = modified;
            d.children.put(name, node);
        }
    }

    // endregion
}
