# Computah OS

Computah OS ships on the **Computah OS floppy** (craft a floppy with a book) and can be copied to a hard drive with
`install`. It boots from the Lua BIOS: put the floppy in the case's floppy slot (tier 3) or in a disk drive on the
computer's network.

Its files are `data/computah/lua/os/**` in the mod; a data pack can add or replace files under the same path.

## Boot

`/init.lua` (the kernel) sets up `require`, mounts the boot drive at `/` and the tmpfs at `/tmp`, loads the core
libraries, then starts the `init` process, which mounts every other drive at `/mnt/<first 3 address characters>`,
starts the enabled rc services, prints `/etc/motd` and runs the shell (restarting it if it exits). Kernel messages
are shown by `dmesg`.

## Processes and threads

Everything runs as a **thread** (a coroutine) under the scheduler in `/lib/process.lua`. The scheduler waits for the
next signal on behalf of all threads and gives each waiting thread every signal, so threads never steal events from
each other. `computer.pullSignal` inside a thread yields to the scheduler, not to the machine.

A **process** is a thread with its own data: environment variables, working directory and standard streams
(inherited from its parent). Programs started from the shell are processes; their threads are killed when the
program ends unless detached.

```lua
local thread = require("thread")
local t = thread.create(function(n) for i = 1, n do print(i) os.sleep(1) end end, 5)
t:status() t:join([timeout]) t:suspend() t:resume() t:kill() t:detach()
thread.waitForAll({t1, t2}, timeout)  thread.waitForAny({...}, timeout)
```

`process.list()` (and the `ps` program) show all threads; `kill <id>` stops one. Ctrl+C interrupts the foreground
program: its next wait raises `interrupted`.

## Events

```lua
local event = require("event")
event.pull([timeout], [name], [filters...])   -- name is a Lua pattern; filters must equal the values
event.pullFiltered([timeout], function(name, ...) return ... end)
event.pullMultiple("key_down", "touch")
event.listen(name, callback) / event.ignore(name, callback)   -- return false from the callback to stop
event.timer(interval, callback[, times]) / event.cancel(id)  -- times = math.huge repeats forever
event.push(name, ...)
```

Listener and timer callbacks run in the scheduler between threads; they must not block. Errors in them go to `dmesg`.

## Libraries

Computah OS provides `component` (primary components), `computer`, `event`, `filesystem`, `io`, `keyboard`, `net`,
`graphics`, `note`, `os`, `process`, `thread`, `rc`, `serialization`, `shell`, `sides`, `colors`, `term`, `text`,
`uuid` and `require`/`package`. Every function is documented in **[API.md](API.md)** (and in game with
`man <library>`); the machine-level functions underneath (`component.invoke`, `computer.pullSignal`, ...) are in
[LUA.md](LUA.md). Names follow OpenOS, so most OpenComputers programs need few changes; see API.md for the
differences (key codes, colours, sides).

## Shell

Pipes `a | b`, redirection `< > >> 2>`, `;`, `&&`, `||`, background `&`, `$VAR`/`${VAR}`, `*`/`?` globs, quotes
and `\` escapes. Builtins: `cd pwd set unset alias unalias exit`. `PATH` defaults to `/bin:/usr/bin:/home/bin:.`.
`sh script` runs a file of commands; `sh -c "cmd"` runs one line.

## Programs

`beep cat cd clear components cp date df dmesg echo edit env flash free gfxdemo grep head help hostname install
kill label ls lua man mkdir more mount msg mv ping ps rc reboot resolution rm sh shutdown sleep touch umount uptime
wc which`. `help` lists them; `man <topic>` has manuals for every library in API.md plus `eeprom sh threads network gpu edit
components lua`.

## Services (rc)

A service is `/etc/rc.d/NAME.lua` defining `start()` and optionally `stop()`, `status()` or any other command.
`/etc/rc.cfg` lists the services started at boot (`enabled = {...}`) and per-service settings (`NAME = {...}`,
visible to the script as `args`).

```
rc                      list
rc beacon status        start / stop / restart / status / any custom command
rc example enable       start at boot (disable to undo)
```

`beacon` (enabled by default) answers host lookups on port 1. `example` shows a detached worker thread.
