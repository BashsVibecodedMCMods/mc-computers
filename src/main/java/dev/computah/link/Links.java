package dev.computah.link;

import dev.computah.Config;
import dev.computah.api.Component;
import dev.computah.component.AdapterComponents;
import dev.computah.network.NetworkManager;
import dev.computah.network.NetworkMember;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Resolves a host's links to components (server thread). */
public final class Links {
    /** Capability caches for linked blocks: they bump the link epoch when the block changes or (un)loads. */
    private static final Map<GlobalPos, BlockCapabilityCache<?, ?>> WATCHED = new ConcurrentHashMap<>();

    private Links() {
    }

    public static void reset() {
        WATCHED.clear();
    }

    private static void watch(ServerLevel level, GlobalPos target) {
        WATCHED.computeIfAbsent(target, t -> BlockCapabilityCache.create(Capabilities.ItemHandler.BLOCK, level, t.pos(), null,
                () -> WATCHED.containsKey(t), NetworkManager::bumpLinks));
    }

    /**
     * Components of the blocks a host has linked, for a wireless card of the given tier (1 = limited range in the
     * same dimension, 2 = unlimited, any dimension). Unloaded or out-of-range targets are skipped.
     */
    public static List<Component> resolve(MinecraftServer server, GlobalPos host, int wirelessTier) {
        List<Component> out = new ArrayList<>();
        if (wirelessTier <= 0) return out;
        int range = Config.get(Config.LINK_RANGE);
        for (GlobalPos target : LinkSavedData.get(server).targets(host)) {
            ServerLevel level = server.getLevel(target.dimension());
            if (level == null) continue;
            if (wirelessTier < 2) {
                if (!target.dimension().equals(host.dimension())) continue;
                if (range > 0 && target.pos().distSqr(host.pos()) > (double) range * range) continue;
            }
            watch(level, target);
            if (!level.isLoaded(target.pos())) continue;
            BlockEntity be = level.getBlockEntity(target.pos());
            if (be instanceof NetworkMember m) {
                if (m.memberKind() == NetworkMember.Kind.NODE) out.addAll(m.networkComponents());
                continue;
            }
            out.addAll(AdapterComponents.forBlock(level, target.pos(), null));
        }
        return out;
    }
}
