package dev.computah.network;

import dev.computah.api.Component;
import net.minecraft.core.Direction;

import java.util.List;

/**
 * A block entity that takes part in wired networks. NODE members (screens, keyboards, disk drives, adapters, redstone
 * blocks) expose components to every computer on the same network and pass the connection on. HOST members
 * (cases, racks) are endpoints: their parts stay private, but they send and receive network messages. RELAY members
 * pass messages between the networks on their sides but not components.
 */
public interface NetworkMember {
    enum Kind {NODE, HOST, RELAY}

    default Kind memberKind() {
        return Kind.NODE;
    }

    /** Components visible to computers on this network. */
    default List<Component> networkComponents() {
        return List.of();
    }

    /** Whether this member connects to the network through the given side. */
    default boolean connects(Direction side) {
        return true;
    }

    /** For HOST members: the modems that receive wired messages. */
    default List<ModemComponent> wiredModems() {
        return List.of();
    }
}
