Computah 0.1.5.

**Requires:** Minecraft 1.21.1, NeoForge 21.1.x (built against 21.1.249). No other mods needed (JEI is supported).

**Breaking:** EEPROMs and floppies are now crafted blank, and the separate OS floppy item is gone. Existing Lua BIOS
EEPROMs may come back blank and old OS floppies disappear: craft EEPROM + book and floppy + book again.

New in 0.1.5:
- **Blank EEPROMs and floppies.** EEPROM + book = Lua BIOS EEPROM; floppy + book = Computah OS floppy. The same items
  with data, so the creative tab and JEI list them as variants. Program blank EEPROMs while the OS runs by swapping
  them in: `flash -w file.lua` or `flash -w --bios` (docs/EEPROM.md).
- **Dyed floppies:** floppy + any of the 16 dyes, in vanilla dye colours; dyeing keeps the files.
- **Drive speeds like OpenComputers:** floppies spin up and hard drives chug while you install the OS. Speeds are
  configurable, or turn them off (docs/CONFIG.md, `[drives]`).
- **Buzzer:** `computer.beep` is now a PC-speaker square wave at the exact frequency (20-2000 Hz) instead of a pitched
  note block.
- **Signage:** `gpu.getFitResolution()` and `resolution fit` work out the resolution that fills a long or tall
  monitor without borders (docs/GRAPHICS.md).
- **Configurable sizes:** RAM tiers, hard drive tiers, floppy and tmpfs sizes in the server config (`[parts]`).
- CI builds carry the build number in the version (`0.1.5+build.N`).

Also in this build (from 0.1.0): computer cases (3 tiers) and server racks; computers saved with their whole Lua state
across chunk unloads and restarts; Lua 5.2 sandbox with a reprogrammable EEPROM BIOS; Computah OS with threads,
background jobs, events, rc services, a shell, `edit`, `man` pages and `install`; text and pixel screens up to
640x400 with multiblock monitors up to 8x6; networking, wireless cards and the Linker; adapters; optional Forge Energy.
Textures, GUI art and sounds from OpenComputers (CC0); hard drive sound based on "hard_drive_noise (miroslawski.net).wav"
by artykris (Sampling+). See CREDITS.md.

Automated unit and game tests pass. Not yet checked in a real client: the buzzer sound, dyed floppy textures, JEI
entries.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
