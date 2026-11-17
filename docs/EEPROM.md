# EEPROM programming

The EEPROM is the computer's BIOS chip. It is the **first and only code a computer runs on its own**: everything
else (an operating system, a program, a network boot loader) is loaded by the EEPROM's code. This guide explains
exactly what happens at boot, what the code can use, and how to write, flash, test, lock and recover EEPROMs.

- [The chip](#the-chip)
- [The boot sequence](#the-boot-sequence)
- [What BIOS code can use](#what-bios-code-can-use)
- [The default Lua BIOS, line by line](#the-default-lua-bios-line-by-line)
- [Writing an EEPROM](#writing-an-eeprom)
- [Examples](#examples)
- [Rules and limits](#rules-and-limits)
- [Read-only EEPROMs and checksums](#read-only-eeproms-and-checksums)
- [Debugging and recovery](#debugging-and-recovery)
- [Reference](#reference)

## The chip

| Area | Size | Meaning |
|---|---|---|
| Code | 4096 bytes | Lua source run at boot. Plain text only: precompiled bytecode is rejected. |
| Data | 256 bytes | Free-form bytes. By convention the Lua BIOS stores the **boot drive address** here. |
| Label | 24 characters | The item name shown in tooltips. |
| Read-only flag | – | Once set, code and label can never change again (the data area stays writable). |

A crafted EEPROM is **blank**, like a new hard drive. Craft it with a **book** (shapeless) to put the **Lua BIOS**
on it (label "Lua BIOS"); the creative tab and JEI also have a ready Lua BIOS EEPROM (the same item, with data).
See [Blank EEPROMs and programming them](#blank-eeproms-and-programming-them). Each EEPROM item keeps its own
contents, so you can carry EEPROMs between computers, keep spares, or hand out "cartridges" with special
firmware. The contents live in the item (a data component), not in the world, and survive breaking the computer.

Every computer and rack server has exactly one EEPROM slot. Without an EEPROM the computer stops right away with
`no bios found; install a configured EEPROM`.

## The boot sequence

When you press the power button (or `computer.start()` is called by another computer, or a computer that was
running when its chunk was saved is loaded again):

1. **Hardware check.** The case needs a CPU and at least one memory module, otherwise it shows `no CPU` or
   `no memory`. With power enabled in the server config it also needs energy. The CPU tier limits how many
   components the computer can see (8/12/16); too many gives `too many components`.
2. **A fresh Lua state** is created with the memory limit of the installed RAM. Nothing survives from a previous
   run: no globals, no open files, no signals.
3. **The machine bootstrap** (`machine.lua`, part of the mod) builds the sandbox: the global environment your code
   sees, containing the Lua standard library plus `component`, `computer` and `unicode`.
4. **The EEPROM's code is read** with `eeprom.get()` and compiled as a text chunk named `=bios`. A syntax error stops
   the computer with `failed loading bios: ...`.
5. **The code runs** inside the machine's main coroutine. From here on it is in charge.
6. If the code **returns**, the computer stops with `computer halted`. If it **errors**, the computer stops with the
   error message and a stack trace, shown on the first screen it can find and in the case's GUI.

There is no hidden second stage: if your EEPROM code wants an operating system, it must load one itself.

## What BIOS code can use

The sandbox is the same for the BIOS and everything it loads (the OS adds libraries on top, but the BIOS runs
before any of that).

In short:

- **Lua standard library** without `io`, `print`, `require`, `dofile`, `loadfile` (those come from an operating
  system); `load` only accepts text; `debug` only has `traceback` and `getinfo`.
- **`component`**: `list`, `invoke`, `proxy`, `type`, `slot`, `methods`, `doc`, `get`, `fields`.
- **`computer`**: `pullSignal` (how code waits), `pushSignal`, `uptime`, `address`, `tmpAddress`, `freeMemory`,
  `totalMemory`, `energy`, `maxEnergy`, `getBootAddress`, `setBootAddress`, `beep` (tone or pattern), `shutdown`,
  `getDeviceInfo`, `getArchitecture(s)`, `isRobot`.
- **`unicode`**: character-based string functions.
- **`checkArg(n, value, types...)`** for argument checks.

Every one of these is documented with arguments, results and examples in **[LUA.md](LUA.md)**. An operating
system adds more on top (Computah OS: [API.md](API.md)) but never changes these, so BIOS code and the code it loads
see the same functions.

### Signals

Everything that happens to a computer arrives as a signal: `key_down`, `touch`, `modem_message`,
`component_added`, `redstone_changed` and so on (see [COMPONENTS.md](COMPONENTS.md#signals)). The queue holds up to
256 signals; extra ones are dropped. `computer.pullSignal` returns the oldest. If nothing arrives within
`timeout` seconds it returns nothing. `computer.pullSignal(0)` returns a signal if one is queued, or waits for the
next tick.

### Direct and indirect calls

Most component methods are **direct**: they run immediately on the computer's Lua thread. Methods that touch the
world (marked **(tick)** in [COMPONENTS.md](COMPONENTS.md)) are **indirect**: the computer pauses, the method runs
on the server thread at the next tick, and the computer continues with the result. `eeprom.set` and
`eeprom.setData` are indirect, so writing an EEPROM takes a tick.

## The default Lua BIOS, line by line

This is `data/computah/lua/bios.lua`, what the EEPROM + book recipe writes (about 1.9 KB). The OS floppy has a copy
as `/lib/bios.lua`:

```lua
local component, computer = component, computer
local invoke = component.invoke

local function tryBoot(address)
  local handle, reason = invoke(address, "open", "/init.lua")
  if not handle then
    return nil, reason
  end
  local code = ""
  repeat
    local chunk = invoke(address, "read", handle, math.huge)
    code = code .. (chunk or "")
  until not chunk
  invoke(address, "close", handle)
  return load(code, "=init")
end
```

`tryBoot` reads `/init.lua` from one filesystem. `read` returns at most 2048 bytes per call and `nil` at the end
of the file, so it loops. It returns the compiled function, or `nil` and a reason.

```lua
local init, reason
local saved = computer.getBootAddress()
if saved and component.type(saved) == "filesystem" then
  init, reason = tryBoot(saved)
end
```

First choice: the drive whose address is stored in the EEPROM's data area (if it is still installed).

```lua
if not init then
  for address in component.list("filesystem") do
    if address ~= computer.tmpAddress() then
      init, reason = tryBoot(address)
      if init then
        if address ~= saved then
          computer.setBootAddress(address)
        end
        break
      end
    end
  end
end
```

Otherwise, every other filesystem is tried in turn (skipping the empty tmpfs). The first one with an
`/init.lua` wins and its address is saved in the data area, so the next boot is faster and always picks the same
drive even when several have an OS. This is why `install` only has to call `computer.setBootAddress`.

```lua
if not init then
  status("No bootable medium found" .. ...)
  error("no bootable medium found" .. ..., 0)
end
status("Booting...")
computer.beep(1000, 0.1)
return init()
```

`status` (omitted above) writes a message on the first screen if there is a GPU and screen. Then the BIOS runs
`init.lua` as a tail call, so the OS replaces the BIOS on the call stack.

## Writing an EEPROM

From Computah OS:

```
edit /home/mybios.lua      write your code
flash /home/mybios.lua     check syntax and size, confirm, write it to the EEPROM
flash -l "My BIOS"         give it a name
flash -c                   size, checksum, read-only state
flash -r /home/backup.lua  save the current EEPROM code to a file first
```

`flash` refuses files over 4096 bytes and files with syntax errors. Writing does not affect the running OS; the
new code runs at the next boot (`reboot`).

From Lua directly:

```lua
local eeprom = component.proxy(component.list("eeprom")())
local f = io.open("/home/mybios.lua")
local code = f:read("a")
f:close()
assert(load(code))          -- syntax check
eeprom.set(code)            -- takes a tick
eeprom.setLabel("My BIOS")
```

To flash an EEPROM for **another** computer: put a second EEPROM in a computer whose BIOS you don't need right
now (or use a computer whose OS is already booted; swapping the EEPROM of a running computer is safe – it is only
read at boot), run `flash`, take it out and install it in the target.

**Size tips.** 4096 bytes is small. Use short local names, drop comments, and move logic into files loaded from a
drive or the network. A few hundred bytes are enough to fetch the rest of a program.

## Examples

### 1. Hello world, with a keyboard echo

```lua
local gpu = component.list("gpu")()
local screen = component.list("screen")()
component.invoke(gpu, "bind", screen)
component.invoke(gpu, "fill", 1, 1, 50, 16, " ")
component.invoke(gpu, "set", 1, 1, "Hello from my BIOS. Type something:")
local x = 1
while true do
  local name, _, char = computer.pullSignal()
  if name == "key_down" and char >= 32 then
    component.invoke(gpu, "set", x, 2, unicode.char(char))
    x = x + 1
  end
end
```

### 2. A headless controller (no OS at all)

A server that turns a door (redstone) on and off when it receives network messages, with a redstone card and a
network card. Rack servers are ideal for this: no screen needed.

```lua
local rs = component.list("redstone")()
local modem = component.list("modem")()
component.invoke(modem, "open", 10)
while true do
  local name, _, from, port, _, command = computer.pullSignal()
  if name == "modem_message" and port == 10 then
    local level = command == "open" and 15 or 0
    for side = 0, 5 do component.invoke(rs, "setOutput", side, level) end
    component.invoke(modem, "send", from, 10, "ok", command)
  end
end
```

### 3. Network boot

Boot code that asks the network for a program instead of reading a drive. Any computer that answers on port 99
with Lua source can serve it (for example a file server running a small rc service).

```lua
local modem = component.list("modem")()
component.invoke(modem, "open", 99)
local code
while not code do
  component.invoke(modem, "broadcast", 99, "netboot", computer.address())
  local deadline = computer.uptime() + 2
  repeat
    local name, _, _, port, _, kind, data = computer.pullSignal(deadline - computer.uptime())
    if name == "modem_message" and port == 99 and kind == "image" then code = data end
  until code or computer.uptime() >= deadline
end
local program = assert(load(code, "=netboot"))
return program()
```

The server side (in Computah OS):

```lua
local net = require("net")
local f = io.open("/srv/client.lua")
local image = f:read("a")
f:close()
net.open(99)
while true do
  local from, port, _, kind = net.receive(nil, 99)
  if kind == "netboot" then net.send(from, 99, "image", image) end
end
```

Messages are limited to 8192 bytes, so a network-booted program over that size should be sent in parts.

### 4. A boot menu

Pick which drive to boot when several have an OS. Shows the drives and their labels; press a number.

```lua
local gpu, screen = component.list("gpu")(), component.list("screen")()
component.invoke(gpu, "bind", screen)
local drives = {}
for a in component.list("filesystem") do
  if a ~= computer.tmpAddress() and component.invoke(a, "exists", "/init.lua") then drives[#drives + 1] = a end
end
for i, a in ipairs(drives) do
  component.invoke(gpu, "set", 1, i, i .. ") " .. (component.invoke(a, "getLabel") or a:sub(1, 8)))
end
local choice
repeat
  local name, _, char = computer.pullSignal()
  if name == "key_down" then choice = drives[char - 48] end
until choice
computer.setBootAddress(choice)
local h = component.invoke(choice, "open", "/init.lua")
local code = ""
repeat local c = component.invoke(choice, "read", h, math.huge) code = code .. (c or "") until not c
component.invoke(choice, "close", h)
return load(code, "=init")()
```

### 5. Watchdog: reboot on error

Wrap the real program so the computer restarts instead of stopping when something goes wrong.

```lua
local ok, err = pcall(function()
  -- load and run your program here
end)
computer.beep(200, 0.5)
computer.shutdown(true)
```

## Rules and limits

- **Yield regularly.** Code that runs for 5 seconds (configurable) without calling `computer.pullSignal` gets a
  `too long without yielding` error. If it catches that error and keeps going, it is killed two seconds later.
  In long loops call `computer.pullSignal(0)` now and then.
- **Memory.** All of a computer's Lua data must fit in its RAM. When it does not, the computer stops with
  `not enough memory`.
- **Calls per tick.** Some methods (mostly GPU drawing) have a per-tick budget; when it runs out the call waits for
  the next tick instead of failing.
- **Coroutines work normally.** A `computer.pullSignal` inside a coroutine still reaches the machine; the
  coroutine's `resume` just sees it as a normal wait.
- **Computers keep running across reloads.** When a chunk unloads or the server stops, a running computer is
  saved with its whole Lua state and continues where it was when it loads again – your BIOS doesn't run again. Only
  if the state couldn't be saved (rare; see ARCHITECTURE.md) does it boot from the EEPROM again.
- **Strings are bytes.** `#s` counts bytes; use `unicode.len` for characters.

## Read-only EEPROMs and checksums

`eeprom.getChecksum()` returns the CRC32 of the code as 8 hex digits. `eeprom.makeReadonly(checksum)` locks the
code and label **forever** – but only when you pass the current checksum, which protects against locking the
wrong contents by accident:

```lua
local e = component.proxy(component.list("eeprom")())
e.makeReadonly(e.getChecksum())
```

or `flash --lock`. A read-only EEPROM still has a writable data area, so the Lua BIOS can still remember its boot
drive. Use this to hand out tamper-proof firmware.

## Blank EEPROMs and programming them

A blank EEPROM can't boot a computer (`the EEPROM is empty`). There are three ways to fill one:

1. **Crafting:** blank EEPROM + book = Lua BIOS EEPROM. (This also resets an EEPROM to the Lua BIOS, unless it is
   read-only.)
2. **Hot swap:** EEPROMs can be swapped while a computer runs (only CPU and memory changes stop it). Boot Computah OS
   with a Lua BIOS EEPROM, then:
   ```
   flash -w mybios.lua    -- waits for you to swap in the blank EEPROM, then writes the file to it
   flash -w --bios        -- the same with the stock Lua BIOS (/lib/bios.lua)
   ```
   `flash -w` waits for the `component_added` signal of the new EEPROM and makes it the primary one. Without `-w`,
   swap first and then run `flash mybios.lua`. Put the Lua BIOS EEPROM back before you reboot.
3. **From your own code:** `component.eeprom.set(code)` on whatever EEPROM is installed (see the reference).

Label the result with `flash -l "My firmware"`; the label is the item's tooltip name.

## Debugging and recovery

- **The error is on the screen** (if there is one) and in the case GUI: hover over "Crashed".
- **Test before flashing.** Run your BIOS file as a normal program first: `lua mybios.lua`. Inside the OS the same
  `component` and `computer` functions exist, so most BIOS code behaves the same.
- **Bricked EEPROM** (it boots into a loop, crashes, or never yields): take it out of the computer. Any EEPROM can
  be rewritten from another computer with `flash`, or crafted with a book to get the Lua BIOS back.
- **Lost boot drive:** if the saved boot address is no longer installed, the Lua BIOS simply tries the other drives.
  `flash -d -` clears the data area.
- **Read-only and broken:** craft a new EEPROM; read-only chips can't be changed.

## Reference

See [COMPONENTS.md](COMPONENTS.md#eeprom) for every `eeprom` method, [LUA.md](LUA.md) for everything BIOS code can
call, and
`man eeprom` in-game for a short version of this guide.
