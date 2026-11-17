package dev.computah.link;

import dev.computah.network.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/**
 * Wireless peripheral links: which blocks each host (computer case or rack) has linked. Stored once per server on
 * the overworld. A host uses its links only while it has a wireless card; see docs/NETWORKING.md.
 */
public class LinkSavedData extends SavedData {
    private static final String NAME = "computah_links";
    private final Map<GlobalPos, LinkedHashSet<GlobalPos>> links = new HashMap<>();

    public static LinkSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(LinkSavedData::new, LinkSavedData::load, null), NAME);
    }

    public Set<GlobalPos> targets(GlobalPos host) {
        Set<GlobalPos> s = links.get(host);
        return s == null ? Set.of() : Collections.unmodifiableSet(s);
    }

    public boolean isLinked(GlobalPos host, GlobalPos target) {
        Set<GlobalPos> s = links.get(host);
        return s != null && s.contains(target);
    }

    /** Adds the link if missing, removes it if present. Returns true if it is now linked. */
    public boolean toggle(GlobalPos host, GlobalPos target, int max) {
        LinkedHashSet<GlobalPos> s = links.computeIfAbsent(host, k -> new LinkedHashSet<>());
        boolean linked;
        if (s.remove(target)) {
            linked = false;
        } else {
            if (s.size() >= max) return false;
            s.add(target);
            linked = true;
        }
        if (s.isEmpty()) links.remove(host);
        setDirty();
        NetworkManager.bumpLinks();
        return linked;
    }

    public void clear(GlobalPos host) {
        if (links.remove(host) != null) {
            setDirty();
            NetworkManager.bumpLinks();
        }
    }

    private static CompoundTag write(GlobalPos p) {
        CompoundTag t = new CompoundTag();
        t.putString("dim", p.dimension().location().toString());
        t.putLong("pos", p.pos().asLong());
        return t;
    }

    private static GlobalPos read(CompoundTag t) {
        ResourceLocation dim = ResourceLocation.tryParse(t.getString("dim"));
        if (dim == null) return null;
        return GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(t.getLong("pos")));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<GlobalPos, LinkedHashSet<GlobalPos>> e : links.entrySet()) {
            CompoundTag h = write(e.getKey());
            ListTag targets = new ListTag();
            for (GlobalPos t : e.getValue()) targets.add(write(t));
            h.put("targets", targets);
            list.add(h);
        }
        tag.put("hosts", list);
        return tag;
    }

    private static LinkSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        LinkSavedData d = new LinkSavedData();
        for (Tag t : tag.getList("hosts", Tag.TAG_COMPOUND)) {
            CompoundTag h = (CompoundTag) t;
            GlobalPos host = read(h);
            if (host == null) continue;
            LinkedHashSet<GlobalPos> set = new LinkedHashSet<>();
            for (Tag x : h.getList("targets", Tag.TAG_COMPOUND)) {
                GlobalPos p = read((CompoundTag) x);
                if (p != null) set.add(p);
            }
            if (!set.isEmpty()) d.links.put(host, set);
        }
        return d;
    }
}
