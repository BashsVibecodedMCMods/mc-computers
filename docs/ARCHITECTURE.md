# Architecture

Computah is a standalone NeoForge mod (it does not need CC: Tweaked). Lua runs in **Cobalt**, the pure-Java Lua 5.2
VM, which is repackaged into `dev.computah.repack` and shipped inside the jar (so the exact version we save
states with is always the one running, whatever other mods bring along).

## Packages (`dev.computah`)

| Package | What it holds |
|---|---|
| `machine` | `Machine` (one running computer), `MachineExecutor` (Lua threads + watchdog), `MachineApis` (Java functions for `machine.lua`), `LuaConverter`, `HeapEstimator`. Plain Java, unit tested without a world. |
| `api` | `Component`, `@Callback`, `Arguments`, `Context`, `ComponentException`, `LimitReachedException`. |
| `component` | Component implementations: EEPROM, filesystem, GPU, screen, keyboard, redstone, adapter components. |
| `fs` | `FileSystem` and its implementations: `RamFileSystem` (tmpfs, OS disk), `DirectoryFileSystem` (drives), `OsFiles`. |
| `display` | `DisplayBuffer` (text + pixel layers, operation log), `Compositor` (buffer to image), `Font` (unscii), `Palette`, `ScreenGeometry`. Shared by server and client. |
| `host` | `HostCore`: parts, components, network/link discovery and the `Machine` of one case or rack server. |
| `network` | `NetworkManager` (cable discovery, message delivery), `NetworkMember`, `ModemComponent`. |
| `link` | Wireless peripheral links (`LinkSavedData`, `Links`). |
| `block`, `item`, `menu`, `registry` | Minecraft content. |
| `net` | Packets (`DisplayPayload`, `ScreenInputPayload`, `LinkViewPayload`) and server handlers. |
| `client` | Client only: monitor renderer, screen GUI, case/rack/drive GUIs, link lines. |
| `test` | Game tests (not shipped in the jar). |

Lua code is in `src/main/resources/data/computah/lua`: `machine.lua` (sandbox bootstrap), `bios.lua` (default
EEPROM) and `os/` (Computah OS, served as the read-only OS floppy and overridable by data packs).

## Threads

- **Server thread:** world access, ticking (`HostCore.tick` → `Machine.update`), indirect component calls, network
  delivery, screen formation and display sync.
- **Machine threads** (`MachineExecutor`, `machines.threads` in the config): run Lua. A machine runs on at most one
  thread at a time. Direct callbacks run here, so components are thread-safe and never touch the world from them.
- **Watchdog:** interrupts Lua that runs too long (`too long without yielding`, then a hard kill).

## A machine's life

`Machine.start()` checks the host's `MachineSpec` (CPU, RAM, energy), attaches its components, and schedules the
first run, which creates a Cobalt state, loads `machine.lua` and resumes its main coroutine. The coroutine yields
to Java with a private marker for three reasons:

- `"pull", timeout` – `computer.pullSignal`. Java resumes it with the next signal or after the timeout.
- `"sync"` – an indirect call (or a rate-limited direct call). Java runs it on the server thread next tick.
- `"shutdown", reboot`.

User coroutines pass these yields through (`machine.lua` wraps `coroutine.resume`). Memory is measured by walking
the Lua heap every 10 ticks (`HeapEstimator`); Cobalt has no allocation hook, so this is an estimate.

## Components and visibility

A machine sees: its own `computer` component, its tmpfs, the components of its parts (`HostCore.rebuildParts`), and
external components found by `NetworkManager.scanComponents` (through cables and NODE blocks) plus linked blocks
(`Links.resolve`, only with a wireless card). Each level has a network epoch that changes when network blocks are
placed, removed, loaded or unloaded; hosts rescan when it changes, with a per-tick budget. Links have their own
epoch, bumped by link edits and by capability-cache invalidation of linked blocks.

## Saving running computers

A running computer survives chunk unloads and server restarts: it continues exactly where it was, mid-program,
with its threads, open files and queued signals. Nothing reboots.

- **What is saved.** `Machine.saveState` writes the whole Lua heap with `machine/persist/LuaPersistence` (an
  Eris-like serializer for Cobalt): every table, string, closure, prototype, upvalue and coroutine, including
  suspended call frames inside `pcall`, `coroutine.resume` and metamethods. Library functions and the machine's
  native functions are *permanents* (`Perms`), stored by name and reconnected to the fresh state on restore. It also
  writes the signal queue, a pending indirect call, the uptime, the tmpfs contents and per-component state from
  components implementing `PersistentComponent` (GPU colours/binding/buffers, open file handles, modem ports,
  redstone outputs).
- **When.** From the block entity's save (`HostCore.save` → `persist`), i.e. on autosaves, chunk unloads and server
  stop. A running computer marks its chunk changed every 5 seconds so autosaves keep its state fresh. The state goes
  to `<world>/computah/state/<computer address>.bin` with a serial number that is also stored in the block's NBT;
  on load the two must match, so a world and a state file from different saves are never mixed.
- **If Lua is running** at that moment, saving waits up to a second for it to yield. If it can't save (busy, or
  something unsavable is reachable – e.g. a Java object captured by a third-party library), the previous save is
  kept and a warning is logged; if no save matches on load, the computer boots normally.
- **Restore** (`Machine.restoreState`) builds a fresh Cobalt state, reconnects permanents, restores the heap,
  re-attaches components, and sends `component_added`/`component_removed` for anything that changed meanwhile.
- **Screens** save their display image to `<world>/computah/screens/<screen address>.bin`, so monitors show the
  same picture after a reload.

## Displays

The GPU writes to the screen's `DisplayBuffer`. Each change is an operation applied locally and appended to a log.
Every tick the screen origin sends the log (deflated) to players tracking the chunk; a player who starts watching
asks for a snapshot. The client applies the same operations to its own `DisplayBuffer`, renders it with the
`Compositor` into one texture, and draws that texture both on the block face (`ScreenRenderer`) and in the screen
GUI (`MonitorScreen`) – so the world shows exactly what the GUI shows.
