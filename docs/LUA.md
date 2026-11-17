# The Lua environment

Computers run **Lua 5.2** on Cobalt. Each computer has its own Lua state, created at boot and thrown away when it
stops.

## Globals

| Name | Notes |
|---|---|
| base functions | `assert error getmetatable ipairs next pairs pcall rawequal rawget rawlen rawset select setmetatable tonumber tostring type xpcall` |
| `load(chunk[, name[, mode[, env]]])` | Text only (mode is forced to `"t"`); `env` defaults to the global table. |
| `string table math coroutine bit32 utf8` | Standard (copies, so changing them affects only this computer). `string.dump` is removed. |
| `os` | `clock date time difftime`. `os.time()` is real time (UTC seconds). The OS adds more. |
| `debug` | `traceback` and `getinfo` only. |
| `checkArg(n, value, types...)` | Argument checking. |
| `component`, `computer`, `unicode` | See [EEPROM.md](EEPROM.md#what-bios-code-can-use). |

Not available: `io`, `print`, `require`, `dofile`, `loadfile`, `collectgarbage`, file or network access outside
components, precompiled bytecode. Computah OS provides `io`, `print`, `require` and more.

## Limits

| Limit | Value |
|---|---|
| Time without yielding | 5 s (config `machines.timeout`), then an error; 2 s later a hard stop. |
| Memory | Sum of installed RAM: 384 KiB, 768 KiB, 1.5 MiB or 3 MiB per module. Measured approximately. |
| Signal queue | 256 signals. |
| Components | CPU tier 1: 8, tier 2: 12, tier 3: 16 (besides the computer itself and its tmpfs). |
| Rate-limited calls | Per-tick budgets (CPU and GPU tiers); calls over budget wait a tick. |
| Open files | 16 per filesystem per computer. |

## Values crossing into components

Strings are byte strings. Component methods that take text decode it as UTF-8; methods that return text return
UTF-8. Tables passed to components are copied; functions and coroutines can't be passed. Numbers come back as
integers when they are whole.
