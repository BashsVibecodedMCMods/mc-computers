package dev.computah.machine;

/**
 * Hardware limits of a machine, taken from its parts when it starts.
 *
 * @param memory         Lua memory budget in bytes (sum of RAM).
 * @param maxComponents  How many components the CPU can address.
 * @param callBudget     Per-tick budget for rate-limited direct calls (see {@link dev.computah.api.Callback#limit()}).
 * @param timeoutSeconds How long Lua may run without yielding before "too long without yielding".
 * @param tmpSize        Size of the machine's temporary filesystem in bytes (0 = none).
 */
public record MachineSpec(long memory, int maxComponents, double callBudget, double timeoutSeconds, long tmpSize) {
}
