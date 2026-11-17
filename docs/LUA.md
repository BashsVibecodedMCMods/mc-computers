# The Lua environment (machine level)

Computers run **Lua 5.2** on Cobalt. Each computer has its own Lua state, created when it boots. This page documents
everything that exists **before any operating system is loaded** – what EEPROM (BIOS) code can use, and what every
program can rely on. Computah OS adds libraries on top ([API.md](API.md)); it never removes or replaces these
functions (except that inside the OS `computer.pullSignal` waits through the scheduler, so threads keep running).

Conventions: `name(a[, b])` – `b` is optional; `-> x` lists return values; "returns `nil, reason`" means the
function returns nil and a message instead of raising an error.

Contents: [Globals](#globals) · [component](#component) · [computer](#computer) · [unicode](#unicode) ·
[os](#os) · [Standard libraries](#standard-libraries) · [Limits](#limits) · [Values and components](#values-crossing-into-components) · [Saved state](#saved-state)

## Globals

### checkArg(n, value, type...)

Raises `bad argument #n (type1 or type2 expected, got X)` (pointing at the caller) unless `type(value)` is one of
the given type names.

```lua
local function greet(name, times)
  checkArg(1, name, "string")
  checkArg(2, times, "number", "nil")
end
```

### load(chunk[, name[, mode[, env]]]) -> function

Compiles Lua source. `chunk` is a string or a function returning pieces. Only text is accepted: `mode` is always
treated as `"t"`, so precompiled bytecode can't be loaded. `env` defaults to the global table. Returns
`nil, message` on a syntax error.

### Base functions

`assert error getmetatable ipairs next pairs pcall rawequal rawget rawlen rawset select setmetatable tonumber
tostring type xpcall _VERSION _G` work as in standard Lua 5.2. `pcall`/`xpcall` can't catch the hard stop that
follows "too long without yielding".

Not available: `print`, `require`, `dofile`, `loadfile`, `collectgarbage`, `io` (an operating system provides
`print`, `require` and `io`).

---

## component

Everything a computer can use is a component with an address (a UUID string) and a type.

### component.list([filter[, exact]]) -> table

The components the computer can see: a table `{[address] = type}`. It can also be called as an iterator, which
makes the common cases short:

- `filter` (string): only types containing this text (`"gpu"`), or equal to it when `exact` is true.

```lua
for address, ctype in component.list() do print(address, ctype) end
local gpu = component.list("gpu")()            -- first gpu, or nil
```

### component.invoke(address, method, ...) -> ...

Calls a component method and returns its results. Raises an error if the component or method doesn't exist or the
method fails. Methods marked **(tick)** in [COMPONENTS.md](COMPONENTS.md) run on the server thread: the call waits
for the next tick.

```lua
component.invoke(gpu, "set", 1, 1, "hello")
```

### component.proxy(address) -> proxy

A table with the component's methods as functions (`proxy.set(1, 1, "hi")`) and the fields `address`, `type` and
`slot`. Returns `nil, "no such component"` for an unknown address.

### component.type(address) -> string

The component's type, or `nil, "no such component"`.

### component.slot(address) -> number

The slot of the case or server the component is installed in (counting from 0), or -1 for components on the
network (screens, drives, ...). `nil, reason` for an unknown address.

### component.methods(address) -> table

`{[methodName] = isDirect}`: true for methods that return immediately, false for ones that take a tick.

### component.doc(address, method) -> string

The documentation of a method, like `"function(x:number, y:number, value:string[, vertical:boolean]):boolean --
Writes text..."`, or nil.

### component.get(abbreviation[, type]) -> address

The full address of a component from the start of its address (optionally of a type). Returns
`nil, "no such component"`.

```lua
local fs = component.get("3f2b")
```

### component.fields(address) -> table

Components have no fields, only methods; returns `{}` (kept for OpenComputers programs). `nil, reason` for an
unknown address.

### Component signals

| Signal | Values |
|---|---|
| `component_added` | address, type – a component became visible (part installed, block connected or linked, chunk loaded) |
| `component_removed` | address, type – it went away |

---

## computer

### computer.pullSignal([timeout]) -> name, ...

Waits for the next signal and returns its name and values, or nothing when `timeout` seconds pass without one.
Without a timeout it waits forever. This is how all code waits (and the only way to let other things happen: code
that never calls it is stopped after 5 seconds). `pullSignal(0)` returns a queued signal right away, or waits for
the next tick. Signals queue up (256 at most) while code runs.

```lua
while true do
  local name, a, b, c = computer.pullSignal()
  if name == "key_down" then ... end
end
```

### computer.pushSignal(name, ...) -> boolean

Adds a signal to this computer's queue. Values may be nil, booleans, numbers, strings and tables of those (copied).
Returns false when the queue is full.

### computer.uptime() -> number

Seconds since the computer started, in game time (20 ticks per second). Continues where it was after the computer is
saved and loaded.

### computer.address() -> string

This computer's address. The computer is also a component of type `computer` with that address.

### computer.tmpAddress() -> string

The address of the computer's temporary filesystem (64 KiB by default, empty at every boot; its contents survive being saved
and loaded).

### computer.freeMemory() -> number

Free memory in bytes (an estimate, refreshed every half second).

### computer.totalMemory() -> number

Installed memory in bytes (the sum of the RAM modules).

### computer.energy() -> number

Stored energy in FE. When power is disabled in the server config (the default), a very large number.

### computer.maxEnergy() -> number

Energy capacity in FE (a very large number when power is disabled).

### computer.getBootAddress() -> address

The address stored in the EEPROM's data area (by convention the boot drive), or nil.

### computer.setBootAddress([address])

Stores an address (or nothing) in the EEPROM's data area. Takes a tick.

### computer.beep([frequency[, duration]])

Plays a tone on the computer's buzzer, a square wave like a PC speaker: any `frequency` from 20 to 2000 Hz (default
440, not rounded to musical notes), `duration` up to 5 seconds (default 0.1). Non-blocking. Players within 32 blocks
hear it, fading with distance, at the Blocks volume.

### computer.beep(pattern)

Plays a pattern of beeps one after another: `"."` short, `"-"` long, `" "` a pause, at most 64 characters.

```lua
computer.beep("..--..")
```

### computer.shutdown([reboot])

Turns the computer off, or restarts it when `reboot` is true. Doesn't return.

### computer.getDeviceInfo() -> table

`{[address] = info}` for every component, where `info` has `class` (`"display"`, `"volume"`, `"network"`,
`"input"`, `"system"`, `"generic"`), `description`, `vendor`, `product`, and where it applies `capacity` (bytes,
packet size or pixel size), `size`, `width`.

### computer.getArchitecture() -> string

`"Lua 5.2"`.

### computer.getArchitectures() -> table

`{"Lua 5.2"}`.

### computer.setArchitecture(name) -> boolean

Only `"Lua 5.2"` exists: returns false (nothing to change), or `nil, "unknown architecture"`.

### computer.isRobot() -> boolean

Always false (there are no robots yet).

### computer.users() -> ...

Returns nothing: computers have no user list (anyone who can reach a computer can use it).

### computer.addUser(name) / computer.removeUser(name)

Not supported: `addUser` returns `nil, "user lists are not supported"`, `removeUser` returns false.

---

## unicode

UTF-8 aware string functions (the `string` library works on bytes).

### unicode.char(codepoint...) -> string

The UTF-8 string for the given code points.

### unicode.len(s) -> number

Length in characters.

### unicode.sub(s, i[, j]) -> string

Characters `i` to `j` (negative values count from the end), like `string.sub`.

### unicode.upper(s) / unicode.lower(s) -> string

Upper/lower case.

### unicode.reverse(s) -> string

The characters in reverse order.

### unicode.codepoints(s) -> table

The code points of a string, as a list.

### unicode.wlen(s) -> number

Width in screen cells; every character is one cell wide, so the same as `len`.

### unicode.wtrunc(s, width) -> string

The string cut to fit `width - 1` cells.

### unicode.charWidth(char) -> number

Always 1.

### unicode.isWide(char) -> boolean

Always false.

---

## os

### os.time([table]) -> number

Without arguments, real time in seconds since the Unix epoch (UTC). With a table `{year, month, day[, hour, min,
sec]}`, that UTC date in seconds (hour defaults to 12).

### os.date([format[, time]]) -> string|table

Formats a time (default now). A leading `!` means UTC. `"*t"` returns a table `{year, month, day, hour, min, sec,
wday, yday, isdst}`. Supported specifiers: `%Y %y %m %d %H %I %M %S %p %A %a %B %b %j %c %x %X %%`.

```lua
os.date("!%Y-%m-%d %H:%M")
```

### os.clock() -> number

Seconds the computer has been running (same as `computer.uptime()`).

### os.difftime(t2[, t1]) -> number

`t2 - t1`.

---

## Standard libraries

- `string` – all of Lua 5.2's except `string.dump`. Strings have the `string` functions as methods (`s:upper()`).
- `table`, `math`, `coroutine`, `bit32`, `utf8` – as in Lua 5.2 (`utf8` from Cobalt).
- `debug` – only `debug.traceback` and `debug.getinfo`.

Coroutines work normally, and a `computer.pullSignal` (or a tick-synced component call) inside a coroutine still
reaches the machine: the coroutine's `resume` simply sees it as waiting.

## Limits

| Limit | Value |
|---|---|
| Time without yielding | 5 s (config `machines.timeout`), then the error "too long without yielding"; 2 s later a hard stop. |
| Memory | Sum of installed RAM: by default 512 KiB, 1 MiB, 2 MiB or 4 MiB per module ([configurable](CONFIG.md#parts)). Measured approximately. |
| Drive speed | Filesystem calls on floppies and hard drives take time ([configurable](CONFIG.md#drives)); other threads keep running meanwhile. |
| Signal queue | 256 signals. |
| Components | CPU tier 1: 8, tier 2: 12, tier 3: 16 (besides the computer itself and its tmpfs). |
| Rate-limited calls | Per-tick budgets (CPU and GPU tiers); calls over budget wait a tick. |
| Open files | 16 per filesystem per computer. |
| EEPROM | 4096 bytes of code, 256 bytes of data. |

## Values crossing into components

Strings are byte strings. Component methods that take text decode it as UTF-8; methods that return text return
UTF-8. Tables passed to components are copied; functions and coroutines can't be passed. Numbers come back as
integers when they are whole.

## Saved state

A running computer is saved with its entire Lua state when its chunk unloads or the world saves, and continues
from the same point when loaded: variables, coroutines, open files and queued signals all survive.
`computer.uptime()` carries on from where it was. See [ARCHITECTURE.md](ARCHITECTURE.md#saving-running-computers).
