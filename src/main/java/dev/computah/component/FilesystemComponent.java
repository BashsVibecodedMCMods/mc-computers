package dev.computah.component;

import dev.computah.api.Arguments;
import dev.computah.api.Callback;
import dev.computah.api.Component;
import dev.computah.api.ComponentException;
import dev.computah.api.Context;
import dev.computah.fs.FileSystem;
import dev.computah.machine.Machine;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/** The {@code filesystem} component: hard drives, floppies, the OS disk and each machine's tmpfs. */
public class FilesystemComponent extends Component implements dev.computah.api.PersistentComponent {
    public static final int MAX_HANDLES = 16;
    public static final int MAX_READ = 2048;

    /** Where the label lives (usually the drive item). */
    public interface Label {
        @Nullable
        String get();

        /** Returns false if the label can't be changed. */
        boolean set(@Nullable String label);
    }

    public static Label fixedLabel(@Nullable String label) {
        return new Label() {
            @Override
            public String get() {
                return label;
            }

            @Override
            public boolean set(String l) {
                return false;
            }
        };
    }

    private record Open(Machine owner, FileSystem.Handle handle, String path, FileSystem.Mode mode) {
    }

    private final String address;
    private final FileSystem fs;
    private final Label label;
    private final Map<Integer, Open> handles = new HashMap<>();
    private int nextHandle = 1;
    private volatile @Nullable Consumer<Boolean> activityListener;

    public FilesystemComponent(FileSystem fs, String address, Label label) {
        this.fs = fs;
        this.address = address;
        this.label = label;
    }

    public FilesystemComponent(FileSystem fs, String label) {
        this(fs, UUID.randomUUID().toString(), fixedLabel(label));
    }

    @Override
    public String address() {
        return address;
    }

    @Override
    public String type() {
        return "filesystem";
    }

    public FileSystem fileSystem() {
        return fs;
    }

    /** Called with true for writes and false for reads, so the host can play drive sounds. */
    public void setActivityListener(@Nullable Consumer<Boolean> listener) {
        this.activityListener = listener;
    }

    private void activity(boolean write) {
        Consumer<Boolean> l = activityListener;
        if (l != null) l.accept(write);
    }

    @Override
    public void onDisconnect(Machine machine) {
        synchronized (handles) {
            handles.values().removeIf(o -> {
                if (o.owner() == machine) {
                    o.handle().close();
                    return true;
                }
                return false;
            });
        }
    }

    /** Closes every handle, e.g. when the drive is removed. */
    public void close() {
        synchronized (handles) {
            for (Open o : handles.values()) o.handle().close();
            handles.clear();
        }
    }

    @Override
    public net.minecraft.nbt.CompoundTag saveState(Machine machine) {
        net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        synchronized (handles) {
            for (Map.Entry<Integer, Open> e : handles.entrySet()) {
                Open o = e.getValue();
                if (o.owner() != machine) continue;
                net.minecraft.nbt.CompoundTag h = new net.minecraft.nbt.CompoundTag();
                h.putInt("id", e.getKey());
                h.putString("path", o.path());
                h.putString("mode", o.mode().name());
                try {
                    h.putLong("pos", o.handle().seek("cur", 0));
                } catch (IOException ex) {
                    continue;
                }
                list.add(h);
            }
            t.putInt("next", nextHandle);
        }
        t.put("handles", list);
        return t;
    }

    @Override
    public void loadState(Machine machine, net.minecraft.nbt.CompoundTag t) {
        synchronized (handles) {
            nextHandle = Math.max(nextHandle, t.getInt("next"));
            for (net.minecraft.nbt.Tag x : t.getList("handles", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                net.minecraft.nbt.CompoundTag h = (net.minecraft.nbt.CompoundTag) x;
                FileSystem.Mode mode;
                try {
                    mode = FileSystem.Mode.valueOf(h.getString("mode"));
                } catch (IllegalArgumentException e) {
                    continue;
                }
                String path = h.getString("path");
                try {
                    // Reopening for writing must not truncate the file again.
                    FileSystem.Handle handle = fs.open(path, mode == FileSystem.Mode.READ ? FileSystem.Mode.READ : FileSystem.Mode.APPEND);
                    handle.seek("set", h.getLong("pos"));
                    handles.put(h.getInt("id"), new Open(machine, handle, path, mode));
                } catch (IOException ignored) {
                    // The file is gone; the program gets "bad file descriptor".
                }
            }
        }
    }

    /** Normalizes a Lua path: "/a/./b/../c" -> "a/c". ".." never goes above the root. */
    public static String normalize(String path) throws ComponentException {
        if (path.indexOf('\0') >= 0) throw new ComponentException("invalid path");
        Deque<String> parts = new ArrayDeque<>();
        for (String p : path.replace('\\', '/').split("/")) {
            if (p.isEmpty() || p.equals(".")) continue;
            if (p.equals("..")) {
                if (!parts.isEmpty()) parts.removeLast();
                continue;
            }
            if (p.length() > 255) throw new ComponentException("name too long");
            parts.addLast(p);
        }
        if (parts.size() > 64) throw new ComponentException("path too deep");
        return String.join("/", parts);
    }

    private String path(Arguments args, int i) throws ComponentException {
        return normalize(args.checkString(i));
    }

    private Open handle(Context ctx, Arguments args) throws ComponentException {
        int id = args.checkInteger(0);
        synchronized (handles) {
            Open o = handles.get(id);
            if (o == null || o.owner() != ctx.machine()) throw new ComponentException("bad file descriptor");
            return o;
        }
    }

    @Callback(doc = "function():number -- The used storage space in bytes.")
    public Object[] spaceUsed(Context ctx, Arguments args) {
        return new Object[]{fs.spaceUsed()};
    }

    @Callback(doc = "function():number -- The total storage space in bytes.")
    public Object[] spaceTotal(Context ctx, Arguments args) {
        long t = fs.spaceTotal();
        return new Object[]{t == Long.MAX_VALUE ? Double.POSITIVE_INFINITY : (double) t};
    }

    @Callback(doc = "function():boolean -- Whether the file system is read-only.")
    public Object[] isReadOnly(Context ctx, Arguments args) {
        return new Object[]{fs.isReadOnly()};
    }

    @Callback(doc = "function():string -- The label of the drive.")
    public Object[] getLabel(Context ctx, Arguments args) {
        return new Object[]{label.get()};
    }

    @Callback(direct = false, doc = "function(value:string):string -- Sets the label of the drive. Returns the new label.")
    public Object[] setLabel(Context ctx, Arguments args) throws ComponentException {
        String l = args.optString(0, null);
        if (l != null && l.length() > 32) l = l.substring(0, 32);
        if (!label.set(l == null || l.isEmpty() ? null : l)) throw new ComponentException("label is read only");
        return new Object[]{label.get()};
    }

    @Callback(doc = "function(path:string):boolean -- Whether an object exists at the path.")
    public Object[] exists(Context ctx, Arguments args) throws ComponentException {
        return new Object[]{fs.exists(path(args, 0))};
    }

    @Callback(doc = "function(path:string):boolean -- Whether the path is a directory.")
    public Object[] isDirectory(Context ctx, Arguments args) throws ComponentException {
        return new Object[]{fs.isDirectory(path(args, 0))};
    }

    @Callback(doc = "function(path:string):number -- The size of the file in bytes (0 for directories).")
    public Object[] size(Context ctx, Arguments args) throws ComponentException {
        return new Object[]{fs.size(path(args, 0))};
    }

    @Callback(doc = "function(path:string):number -- When the object was last modified, in milliseconds since the epoch.")
    public Object[] lastModified(Context ctx, Arguments args) throws ComponentException {
        return new Object[]{fs.lastModified(path(args, 0))};
    }

    @Callback(doc = "function(path:string):table -- The entries of a directory; directories end with '/'.")
    public Object[] list(Context ctx, Arguments args) throws ComponentException {
        List<String> l = fs.list(path(args, 0));
        if (l == null) return new Object[]{null, "no such directory"};
        activity(false);
        return new Object[]{l};
    }

    @Callback(doc = "function(path:string):boolean -- Creates a directory, and any missing parents.")
    public Object[] makeDirectory(Context ctx, Arguments args) throws ComponentException {
        try {
            activity(true);
            return new Object[]{fs.makeDirectory(path(args, 0))};
        } catch (IOException e) {
            throw new ComponentException(e.getMessage());
        }
    }

    @Callback(doc = "function(path:string):boolean -- Removes a file or a directory with everything in it.")
    public Object[] remove(Context ctx, Arguments args) throws ComponentException {
        try {
            activity(true);
            return new Object[]{fs.remove(path(args, 0))};
        } catch (IOException e) {
            throw new ComponentException(e.getMessage());
        }
    }

    @Callback(doc = "function(from:string, to:string):boolean -- Renames or moves an object.")
    public Object[] rename(Context ctx, Arguments args) throws ComponentException {
        try {
            activity(true);
            return new Object[]{fs.rename(path(args, 0), path(args, 1))};
        } catch (IOException e) {
            throw new ComponentException(e.getMessage());
        }
    }

    @Callback(doc = "function(path:string[, mode:string='r']):number -- Opens a file ('r', 'rb', 'w', 'wb', 'a', 'ab'). Returns a handle.")
    public Object[] open(Context ctx, Arguments args) throws ComponentException {
        String p = path(args, 0);
        String m = args.optString(1, "r");
        FileSystem.Mode mode = switch (m.replace("b", "")) {
            case "r" -> FileSystem.Mode.READ;
            case "w" -> FileSystem.Mode.WRITE;
            case "a" -> FileSystem.Mode.APPEND;
            default -> throw new ComponentException("unsupported mode");
        };
        synchronized (handles) {
            long mine = handles.values().stream().filter(o -> o.owner() == ctx.machine()).count();
            if (mine >= MAX_HANDLES) throw new ComponentException("too many open handles");
        }
        try {
            FileSystem.Handle h = fs.open(p, mode);
            activity(mode != FileSystem.Mode.READ);
            synchronized (handles) {
                int id = nextHandle++;
                handles.put(id, new Open(ctx.machine(), h, p, mode));
                return new Object[]{id};
            }
        } catch (IOException e) {
            return new Object[]{null, e.getMessage()};
        }
    }

    @Callback(doc = "function(handle:number, count:number):string or nil -- Reads up to count bytes; nil at end of file.")
    public Object[] read(Context ctx, Arguments args) throws ComponentException {
        Open o = handle(ctx, args);
        int n = Math.max(0, Math.min(MAX_READ, args.checkInteger(1)));
        try {
            activity(false);
            byte[] data = o.handle().read(n);
            return new Object[]{data};
        } catch (IOException e) {
            throw new ComponentException(e.getMessage());
        }
    }

    @Callback(doc = "function(handle:number, data:string):boolean -- Writes data to the file.")
    public Object[] write(Context ctx, Arguments args) throws ComponentException {
        Open o = handle(ctx, args);
        try {
            activity(true);
            o.handle().write(args.checkBytes(1));
            return new Object[]{true};
        } catch (IOException e) {
            return new Object[]{null, e.getMessage()};
        }
    }

    @Callback(doc = "function(handle:number, whence:string, offset:number):number -- Moves the position: 'set', 'cur' or 'end'.")
    public Object[] seek(Context ctx, Arguments args) throws ComponentException {
        Open o = handle(ctx, args);
        try {
            return new Object[]{o.handle().seek(args.optString(1, "cur"), (long) args.optDouble(2, 0))};
        } catch (IOException e) {
            throw new ComponentException(e.getMessage());
        }
    }

    @Callback(doc = "function(handle:number) -- Closes the handle.")
    public Object[] close(Context ctx, Arguments args) throws ComponentException {
        Open o = handle(ctx, args);
        synchronized (handles) {
            handles.values().remove(o);
        }
        o.handle().close();
        return new Object[0];
    }

    @Override
    public Map<String, String> deviceInfo() {
        Map<String, String> m = super.deviceInfo();
        m.put("description", fs.isReadOnly() ? "Read-only disk" : "Disk");
        long total = fs.spaceTotal();
        if (total != Long.MAX_VALUE) m.put("capacity", String.valueOf(total));
        m.put("size", String.valueOf(fs.spaceUsed()));
        return m;
    }
}
