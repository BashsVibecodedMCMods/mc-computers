# Computah

Modular Lua computers and rack servers for Minecraft 1.21.1 (NeoForge), inspired by OpenComputers. Standalone: no
other mod needed.

- **Build computers from parts.** Cases (3 tiers) and rack-mounted servers take CPUs, memory, hard drives,
  floppies, an EEPROM and cards (graphics, network, wireless, redstone). Swap parts at any time; computers notice.
- **A real Lua system.** Lua 5.2 in a sandbox, with an EEPROM BIOS you can reprogram, and **Computah OS**: a
  multitasking OS with threads, background jobs, events and timers, services (rc), a shell with pipes and
  redirection, an editor, a Lua prompt and manual pages.
- **Screens with text and pixel graphics.** Each screen has a text layer (colours and styles) over a pixel layer
  (up to 640x400, 256 colours). Screens merge into monitors of up to 8x6 blocks, and the world shows exactly what
  the user sees in the screen GUI. Bezels connect into one frame.
- **Networking.** Cables connect computers to screens, keyboards, disk drives and more. Network and wireless cards
  send messages; relays join networks. A Linker connects blocks wirelessly (like CC: Wireless Peripherals), and
  adapters expose chests, tanks and batteries from any mod.
- **Server racks.** Four servers per rack, each a full computer, sharing the rack's network, redstone and power.
- **Computers keep running.** A running computer is saved with its whole Lua state and picks up exactly where it
  was after chunk unloads and server restarts.
- **Optional power.** Off by default; turn it on to use Forge Energy.
- Cheap, vanilla-material recipes in the spirit of ComputerCraft. Floppies can be dyed in the 16 vanilla colours.
- Drives take time like real hardware (configurable): floppies spin up, hard drives chug while you install.
- A PC-speaker buzzer that plays any frequency (`computer.beep`).
- Memory, drive and tmpfs sizes and drive speeds are server settings ([docs/CONFIG.md](docs/CONFIG.md)).

## Getting started

1. Craft a **Computer Case**, a **CPU**, **Memory**, an **EEPROM**, a **Graphics Card**, a **Screen**, a **Keyboard**
   and a **Floppy Disk**. EEPROMs and floppies are crafted blank: craft the EEPROM with a **book** to get the
   **Lua BIOS**, and the floppy with a **book** to get the **Computah OS floppy** (both are also in the creative tab
   and JEI). A tier 3 case has a floppy slot; for tier 1-2 cases add a **Disk Drive**.
2. Put the parts in the case, place the screen next to it (or connect it with cables) and the keyboard on the screen.
3. Press the power button in the case GUI (or sneak-right-click the case). Right-click the screen to type.
4. Type `help`. To keep files, add a hard drive and run `install`, then remove the floppy.

## Documentation

- [docs/EEPROM.md](docs/EEPROM.md) – the BIOS chip, boot sequence and writing your own firmware
- [docs/OS.md](docs/OS.md) – Computah OS: processes, threads, events, shell, services
- [docs/API.md](docs/API.md) – every Computah OS library function (OpenOS-compatible names)
- [docs/LUA.md](docs/LUA.md) – the machine-level API (what EEPROM code can use), the sandbox and limits
- [docs/GRAPHICS.md](docs/GRAPHICS.md) – text and pixel graphics, buffers, monitors
- [docs/NETWORKING.md](docs/NETWORKING.md) – cables, messages, wireless links, adapters
- [docs/COMPONENTS.md](docs/COMPONENTS.md) – every component method and signal
- [docs/CONFIG.md](docs/CONFIG.md) – every server setting (sizes, drive speeds, power, network)
- [docs/POWER.md](docs/POWER.md) – optional energy use
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) – how the mod is built

In game, `man <topic>` has short versions.

## Building

```bash
./gradlew build
```

```bash
./gradlew runGameTestServer
```

The jar is `build/libs/computah-<version>.jar`.

## Credits

Textures, GUI art, sounds and the screen font come from OpenComputers and others; see [CREDITS.md](CREDITS.md).
The hard drive sound is based on "hard_drive_noise (miroslawski.net).wav" by artykris
(https://freesound.org/s/117401/, Sampling+).

## Licence

MIT for the code. Assets keep their own licences (see CREDITS.md).
