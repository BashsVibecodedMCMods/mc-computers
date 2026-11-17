package dev.computah.api;

import dev.computah.machine.Machine;

/**
 * The machine making a call.
 *
 * @param synchronous true when the call runs on the server thread (indirect calls, or direct calls that were delayed
 *                    to the next tick because a limit was reached).
 */
public record Context(Machine machine, boolean synchronous) {
}
