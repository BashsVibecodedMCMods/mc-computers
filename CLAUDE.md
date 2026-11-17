# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is
Computah: a standalone NeoForge mod (Minecraft 1.21.1, NeoForge 21.1.249, Java 21) adding OpenComputers-style
modular Lua computers and rack servers. Mod id `computah`, package `dev.computah`. It does **not** depend on CC:
Tweaked. Lua runs on Cobalt (`cc.tweaked:cobalt`), repackaged by the `relocateCobalt` Gradle task into
`dev.computah.repack.cobalt` and shipped inside our jar (import it from there, never `org.squiddev`). The design docs in `docs/` are the spec;
keep them in sync with behaviour. `docs/COMPONENTS.md` is generated: run `python3 tools/components_doc.py .` after
changing any `@Callback` (a unit test fails otherwise).

## Commands
- Build and run unit tests: `./gradlew build`. JUnit tests in `src/test` run through ModDevGradle's `unitTest`
  (the mod is booted, so registries work). `OsBootTest` boots the real BIOS + OS headless and types commands.
- Game tests (real computers in a world): `./gradlew runGameTestServer`; the exit code reflects pass/fail. Test
  names are lowercased method names. `dev/computah/test/**` and `data/computah/structure/**` are excluded from the jar.
- **Never run the client (`runClient`) or any graphical test in the cloud environment**: it can't render Minecraft.
  For hands-on testing, push and run the CI workflow, then hand over the `computah` jar artifact.
- CI is `.github/workflows/release.yml` (copied from the sibling mods). It builds and runs game tests on pushes to `claude/**` branches and
  `main`, PRs and manual runs. Pushing `MC<mc>-<mod version>[-suffix]` publishes a release with
  `.github/release-notes.md`; a manual run with a `tag` input does the same. The mod version in the tag must equal
  `mod_version`.

## Architecture
- `machine/Machine`: one computer. Lua runs on `MachineExecutor` threads, never the server thread. The main
  coroutine yields to Java with a private marker for `pull` (pullSignal), `sync` (indirect call, run next tick on
  the server thread) and `shutdown`. `machine.lua` builds the sandbox and passes these yields through user
  coroutines. Memory is estimated by `HeapEstimator` (Cobalt has no allocation hook).
- Components extend `api/Component`; Lua methods are `@Callback` methods `Object[] name(Context, Arguments)`.
  Direct callbacks run on the machine thread and **must not touch the world** (Minecraft returns nothing
  off-thread); use `direct = false` for anything that does. `LimitReachedException` delays a call to the next tick.
- `host/HostCore` is shared by cases and rack servers: part slots → components, network scan
  (`network/NetworkManager`, per-level epochs, per-tick scan budget) and links (`link/Links`, global link epoch).
- Displays: `display/DisplayBuffer` (text + pixel layers) records operations; the screen origin syncs them to
  clients (`net/DisplayPayload`), and the client renders one texture used by both `ScreenRenderer` (world) and
  `MonitorScreen` (GUI). Multiblock monitors: `block/ScreenFormation`, max 8x6.
- Lua files: `data/computah/lua/machine.lua` (sandbox), `bios.lua` (default EEPROM), `os/**` (Computah OS, loaded
  by `fs/OsFiles` from resources so data packs can override files).
- Client code is in `client/` and is reached from common code only through `net/ClientHooks` behind a dist check.
- **Running computers are saved and restored** (`machine/persist/LuaPersistence`, Eris-like, reflection over
  Cobalt internals; `Perms` for library/native functions). `HostCore.save` persists to
  `<world>/computah/state/<address>.bin` with a serial that must match the NBT. Consequences: anything a Lua value
  can reach must be a Cobalt object, a Lua value or a permanent (no Java lambdas except the natives table);
  components with per-machine state implement `PersistentComponent`; bumping the Cobalt version needs the
  persistence tests (`PersistenceTest`, `OsBootTest.runningOsSurvivesSaveAndRestore`, game test
  `runningcomputersurvivesreload`) to pass.

## Rules
- Lua strings are bytes: component text arguments are decoded as UTF-8 and returned as UTF-8.
- Lua 5.2: no `//` integer division or `goto`-only tricks in Lua files; use `math.floor`.
- Game tests change global config, so each sets every value it relies on in `configure()` and uses its own batch.
  The test world in `run/` persists between runs.
- Assets from OpenComputers are CC0; the HDD sound needs the Sampling+ attribution kept in `CREDITS.md`, the README
  and `assets/computah/sounds/CREDITS.txt`.
