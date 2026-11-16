package dev.computah.machine;

/** Thrown inside the Lua VM to kill a machine. Extends Error so that pcall can't catch it. */
final class HardAbort extends Error {
    HardAbort(String message) {
        super(message, null, false, false);
    }
}
