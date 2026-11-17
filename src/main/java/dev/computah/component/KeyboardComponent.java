package dev.computah.component;

import dev.computah.api.Component;

import java.util.UUID;

/**
 * The {@code keyboard} component. It has no methods: it sends {@code key_down}, {@code key_up} and {@code clipboard}
 * signals (keyboard address, character, key code, player name) to every machine that sees it.
 */
public class KeyboardComponent extends Component {
    private final String address;

    public KeyboardComponent(String address) {
        this.address = address;
    }

    public KeyboardComponent() {
        this(UUID.randomUUID().toString());
    }

    @Override
    public String address() {
        return address;
    }

    @Override
    public String type() {
        return "keyboard";
    }
}
