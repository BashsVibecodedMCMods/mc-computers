# Server configuration

Computah's settings are in the world's `serverconfig/computah-server.toml` (single player: the save's `serverconfig`
folder). They are sent to joining players, so tooltips show the server's sizes. Most take effect immediately; sizes
apply to parts the next time they are installed or a computer starts.

## machines

| Key | Default | Meaning |
|---|---|---|
| `threads` | 2 | Threads that run Lua for all computers (restart needed). |
| `timeout` | 5.0 | Seconds a computer may run without yielding before "too long without yielding". |
| `maxNetworkScansPerTick` | 32 | Computers that may rescan their network per tick. |

## power

| Key | Default | Meaning |
|---|---|---|
| `enabled` | false | Whether computers and racks need Forge Energy. See [POWER.md](POWER.md). |
| `computerCost` | 4.0 | FE per tick for a running computer. |
| `serverCost` | 6.0 | FE per tick for each running rack server. |
| `caseBuffer` | 20000 | FE a computer case stores. |
| `rackBuffer` | 80000 | FE a rack stores. |

## network

| Key | Default | Meaning |
|---|---|---|
| `wirelessRange` | 400 | Range in blocks of a tier 1 wireless card (tier 2: unlimited, across dimensions). |
| `linkRange` | 64 | Range of wireless peripheral links through a tier 1 card (0 = unlimited). |
| `maxLinks` | 32 | Linked blocks per computer or rack. |

## drives

Like OpenComputers, drives take time: every filesystem call (open, list, exists, read, write, ...) costs a seek plus
the transfer at the drive's speed, and a floppy spins up after 3 seconds without use. A program waits for its call,
but the computer's other threads and every other computer keep running, and the drive sounds play while it works.
With the defaults, booting from the OS floppy takes a few seconds and installing the OS (about 175 KB in 98 files)
about half a minute.

| Key | Default | Meaning |
|---|---|---|
| `simulateSpeed` | true | Off = every drive is instant. |
| `speedMultiplier` | 1.0 | Multiplies every speed below (2 = twice as fast, 0.5 = half). |
| `floppyReadKiBps` | 24 | Floppy read speed, KiB/s. |
| `floppyWriteKiBps` | 12 | Floppy write speed, KiB/s. |
| `floppySeekMillis` | 25 | Time of every floppy operation before data moves. |
| `floppySpinUpMillis` | 750 | Spin-up after 3 s idle. |
| `hddReadKiBps` | 64 | Tier 1 hard drive read speed (tier 2: ×2, tier 3: ×4). |
| `hddWriteKiBps` | 48 | Tier 1 hard drive write speed (tier 2: ×2, tier 3: ×4). |
| `hddSeekMillis` | 5 | Time of every hard drive operation before data moves. |

The temporary filesystem (tmpfs) is always instant.

## parts

| Key | Default | Meaning |
|---|---|---|
| `ramKiB` | [512, 1024, 2048, 4096] | Memory of RAM tiers 1-4. |
| `hddKiB` | [1024, 2048, 4096] | Capacity of hard drive tiers 1-3. |
| `floppyKiB` | 512 | Floppy capacity. |
| `tmpfsKiB` | 64 | Each computer's temporary filesystem. |

Lowering a drive size keeps existing files; a drive that holds more than its new size is full until files are
removed.
