package dev.computah.api;

import dev.computah.machine.Machine;

/** The machine making a call. */
public record Context(Machine machine) {
}
