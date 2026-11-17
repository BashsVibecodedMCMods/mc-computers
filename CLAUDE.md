# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is
Computah: a standalone NeoForge mod (Minecraft 1.21.1, NeoForge 21.1.249, Java 21, ModDevGradle) adding
OpenComputers-style modular Lua computers and rack servers. Mod id `computah`, package `dev.computah`. It does **not**
depend on CC: Tweaked. Lua runs on Cobalt (`cc.tweaked:cobalt`, Lua 5.2), repackaged by the `relocateCobalt` Gradle
task (Shadow) into `dev.computah.repack.cobalt` and shipped inside our jar: import it from there, never
`org.squiddev`. The docs in `docs/` are the spec; keep them in sync with behaviour.

## Commands
- Build + unit tests: `./gradlew build`. One test class: `./gradlew test --tests '*OsBootTest*'` (add `.methodName`
  for one method). Unit tests run through ModDevGradle's `unitTest`, which boots the mod, so registries and data
  components work. Results/messages: `build/test-results/test/*.xml`.
- Game tests (real computers in a world): `./gradlew runGameTestServer`; exit code = pass/fail. No per-test CLI
  filter: use `./gradlew runServer` and `/test run computah:<lowercased method name>`. Tests live in
  `src/main/java/dev/computah/test` (excluded from the jar with `data/computah/structure/**`).
- **Never run the client (`runClient`) or anything graphical in the cloud environment**: it can't render Minecraft.
  For hands-on testing, push, let CI build, and hand over the `computah-<version>` artifact of the run.
- Generators (run from the repo root, commit the output):
  - `python3 tools/components_doc.py .` → `docs/COMPONENTS.md` from `@Callback` docs. `DocsTest` fails if a method is missing.
  - `python3 tools/api_man.py .` → in-game man pages `os/usr/man/<library>` from `docs/API.md` sections. `DocsTest` checks them.
  - `python3 tools/assets.py .` → blockstates, block/item models (incl. the 16 connected-screen tile models),
    sounds.json, loot tables, pickaxe tag and all recipes; also composites `case_front_running.png`/`rack_front_full.png`.
    Change recipes/models there, not by hand. `AssetsTest` checks every block/item has a model, texture, name, recipe.
- CI: `.github/workflows/release.yml` builds and runs game tests on pushes to `main` and `claude/**`, PRs and manual
  runs, and uploads the jar as artifact `computah-<version>`. CI passes `-Pbuild_number=<run number>`, so CI jars
  are `<mod_version>+build.<n>` (also in `neoforge.mods.toml`); local builds are plain `mod_version`. Pushing `MC<mc>-<mod version>[-suffix]` (or a manual run with a
  `tag` input) publishes a release with `.github/release-notes.md`; the version in the tag must equal `mod_version`
  in `gradle.properties`; a suffix makes it a prerelease.

## Architecture

### Machines (`machine/`)
- `Machine` is one computer and also its own `computer` component. Lua runs only on `MachineExecutor` threads; the
  server thread calls `Machine.update()` each tick. A watchdog interrupts code that runs past the timeout ("too long
  without yielding", then a hard kill via `HardAbort`, an `Error` that pcall can't catch).
- The main coroutine yields to Java with a private marker table: `"pull", timeout` (`computer.pullSignal`), `"sync"`
  (an indirect or rate-limited call, performed on the server thread next tick) and `"shutdown", reboot`.
  `data/computah/lua/machine.lua` builds the sandbox (globals `component`, `computer`, `unicode`, restricted `load`,
  no bytecode) and wraps `coroutine.resume` so these system yields pass through user coroutines.
- `MachineApis` holds the Java natives handed to `machine.lua`; `HeapEstimator` estimates memory by walking the
  heap (Cobalt has no allocation hook).
- **Saving running computers** (`machine/persist`): `LuaPersistence` is an Eris-style serializer for Cobalt
  (reflection over Cobalt internals, records rebuilt through their constructors, objects allocated with `Unsafe`).
  `Perms` names library/native functions (walked from the globals and the natives, including functions captured
  inside library lambdas) so a fresh state is reconnected on restore. `Machine.saveState/restoreState` add the signal
  queue, pending call, uptime, tmpfs and `PersistentComponent` state (GPU, open files, modem ports, redstone).

### Components (`api/`, `component/`, `network/ModemComponent`)
- Components extend `api.Component`; Lua methods are `@Callback` methods `Object[] name(Context, Arguments)`, found
  by `CallbackRegistry`. `Arguments` indices are 0-based; Lua coordinates exposed to users are 1-based.
- Direct callbacks run on the machine thread; `direct = false` ones run on the server thread at the next tick.
  `limit` and `LimitReachedException` (GPU budget) delay a call to the next tick instead of failing.
  `DelayException(ticks)` makes a direct call wait several ticks (the machine yields like a sync call, other
  threads keep running), then runs it synchronously; synchronous calls must never delay again. Drive speed uses
  this: `fs/DriveTiming` (per drive, set by `HostCore`/`DiskDriveBlockEntity`; tmpfs has none) charges seeks and
  transfers from the `[drives]` config.
- Component state may be touched from both threads: keep it synchronized/volatile.

### Hosts, networks, links
- `host/HostCore` is shared by computer cases (one each) and racks (one per server): part slots (`HostInventory`,
  layouts in `item/PartKind`) → components, network discovery, links, the `Machine`, sounds, redstone, saving.
  Rack servers keep their parts inside the server item (`DataComponents.CONTAINER`), written back on every change.
- `network/NetworkManager`: BFS over cables (`NetworkConductor`) and `NetworkMember` block entities. NODE members
  expose components and conduct; HOST members (cases, racks) are endpoints; RELAY members pass messages but not
  components. Per-level epochs (bumped by `MemberBlockEntity` load/unload/removal and cable place/break) trigger
  rescans, limited per tick. Modem messages are queued and delivered at the end of the server tick.
- `link/`: wireless peripheral links (`LinkSavedData` on the overworld; `Links.resolve` turns targets into
  components, using `BlockCapabilityCache` invalidation to bump a global link epoch). Needs a wireless card.
- `component/AdapterComponents`: inventory/tank/energy components for adapters and linked non-Computah blocks.

### Items and recipes
- Parts are `PartItem`s; sizes come from `PartItem.ramSize/hddSize/floppySize/tmpSize` (server config `[parts]`),
  never constants.
- EEPROMs and floppies are crafted blank. Variants are **data on the same item id**, never new items: the Lua BIOS
  (EEPROM data component), the OS floppy (`os_disk`, mounted from `OsFiles`) and dyed floppies (`floppy_color`,
  vanilla `DyeColor`, tinted with `getTextureDiffuseColor`). `PartItem.biosEeprom()/osFloppy()/dyedFloppy()` build
  them; `ModTabs` lists them, and the optional JEI plugin (`compat/jei`, compileOnly API) registers subtype
  interpreters so JEI shows them separately.
- `item/DataShapelessRecipe` (serializers `computah:bios_eeprom`, `os_floppy`, `floppy_dye`) uses the vanilla
  shapeless JSON but transforms the input part (keeping its address/files) instead of making a fresh stack.

### Displays and client
- `display/DisplayBuffer` holds a text layer over a palette-indexed pixel layer and records every change as an
  operation. The screen origin block entity sends the op log (deflated) to players tracking the chunk; clients
  request a snapshot when they load the screen. The client applies the same ops and renders one texture
  (`display/Compositor`, unscii `font.hex`) used by both `client/ScreenRenderer` (world) and `client/MonitorScreen`
  (GUI), so the world shows exactly what the GUI shows.
- Multiblock monitors: `block/ScreenFormation` (max 8×6, greedy rectangles from the top-left), position stored in
  blockstate (`ScreenBlock.HORIZONTAL/VERTICAL`) for connected bezel textures. A screen is lit only while a
  running computer sees it (`ScreenComponent.powered`). Screen images are saved under `<world>/computah/screens/`.
- Client code lives in `client/` and is reached from common code only through `net/ClientHooks` behind a dist check.
- Beeps: `HostCore` sends `BeepPayload` to players within 32 blocks; `client/BuzzerSound` streams a generated
  `BuzzerWave` square wave through Minecraft's sound engine (sound event `computah:beep` only names a placeholder
  file marked `stream`).
- `gpu.getFitResolution` uses `ScreenGeometry.fitResolution`; like OC, binding always resets to the max resolution
  (no saved or automatic resolutions).

### Lua tree (`src/main/resources/data/computah/lua`)
- `machine.lua` (sandbox bootstrap), `bios.lua` (default EEPROM contents, ≤ 4096 bytes), `os/**` (Computah OS,
  served as a read-only floppy by `fs/OsFiles` from resources, so data packs can override files).
- OS: `init.lua` (kernel) → `lib/process.lua` (scheduler: every program is a thread; each waiting thread gets every
  signal) → libraries in `lib/` (OpenOS-compatible names) → `/etc/rc.cfg` services → `bin/sh.lua`.
- `docs/API.md` documents every OS library function and `docs/LUA.md` every machine-level one, as
  `### lib.name(...)` headings; `OsBootTest.documentedFunctionsExist` fails when a documented function is missing.

## Rules
- Direct callbacks must not touch the world (Minecraft returns nothing off-thread); anything that does is
  `direct = false`.
- Anything a Lua value can reach must be persistable: Cobalt objects, Lua values or permanents. Don't put Java
  lambdas or other Java objects into the Lua heap (natives go in the natives table, which is permanent). Components
  with per-machine state implement `PersistentComponent`. Bumping the Cobalt version requires `PersistenceTest`,
  `OsBootTest.runningOsSurvivesSaveAndRestore` and game test `runningcomputersurvivesreload` to pass. Adding natives
  changes the `Perms` signature, so states saved by older builds reboot once (acceptable).
- Saved state lives in `<world>/computah/state/<machine address>.bin`, written from `HostCore.save` with a serial
  that must match the block's NBT; failed saves keep the previous pair. Breaking a computer calls `discardState`.
- The OS only *adds* to the sandbox's `component`/`computer` tables: `package.loaded` points at those globals, so
  `init.lua` runs `lib/component.lua` and `lib/computer.lua` explicitly. Never replace raw functions EEPROM code uses
  (only `computer.pullSignal` is wrapped by the scheduler, inside the OS).
- Lua strings are bytes: component text arguments are decoded as UTF-8 and text results returned as UTF-8.
- Lua files are Lua 5.2: no `//` or other 5.3 syntax; use `math.floor`.
- Keyboard codes are GLFW codes (`keyboard.keys`); paste in the screen GUI is Ctrl+Shift+V.
- Game tests change global config, so each sets every value it relies on in `configure()` and uses its own batch.
  `configure()` turns drive simulation off; only `driveSpeedIsSimulated` turns it on.
  The test world in `run/` persists between runs. Lua-driven game tests report through `/result.txt` on the HDD;
  parse it tolerantly (it can be read mid-write).
- In JUnit text blocks that hold Lua, escape Lua backslashes twice (`\\n`, `\\27`).
- After changing a `@Callback`, `docs/API.md`, or assets, rerun the matching generator in `tools/`.
- Assets come from OpenComputers (CC0, licence kept in `assets/computah/LICENSE-OC-ASSETS`). The HDD sounds need
  the Sampling+ attribution ("hard_drive_noise (miroslawski.net).wav by artykris -- https://freesound.org/s/117401/
  -- License: Sampling+") kept in `CREDITS.md`, the README and `assets/computah/sounds/CREDITS.txt`.
- Recipes stay cheap and vanilla-only (ComputerCraft style), defined in `tools/assets.py` (also the floppy models,
  the generated `floppy_tint.png` mask and the 16 dye recipes).
- Every server setting is documented in `docs/CONFIG.md`; add new keys there.
