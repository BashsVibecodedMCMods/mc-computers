First test build of Computah.

**Requires:** Minecraft 1.21.1, NeoForge 21.1.x (built against 21.1.249). No other mods needed.

- Computer cases (3 tiers) and server racks with rack-mounted servers, built from CPUs, memory, hard drives, floppies, an EEPROM and cards (graphics, network, wireless, redstone).
- Lua 5.2 sandbox with a reprogrammable EEPROM BIOS (`flash`; see docs/EEPROM.md).
- Computah OS on a craftable floppy: threads, background jobs (`&`), events and timers, rc services, a shell with pipes and redirection, `edit`, `lua`, manual pages, `install` to a hard drive.
- Screens with a text layer and a pixel layer (up to 640x400, 256 colours); screens merge into monitors up to 8x6 blocks; the world shows the same image as the screen GUI.
- Network cables, network and wireless cards, relays; the Linker links blocks wirelessly; adapters expose inventories, tanks and energy storage.
- Optional Forge Energy use (off by default).
- Textures, GUI art and sounds from OpenComputers (CC0); hard drive sound based on "hard_drive_noise (miroslawski.net).wav" by artykris (Sampling+). See CREDITS.md.

Automated unit and game tests pass. Not yet checked in a real client: rendering of monitors and GUIs, block models, input in the screen GUI, the Linker lines.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
