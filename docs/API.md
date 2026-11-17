# Computah OS library reference

Every library that Computah OS provides, function by function. Load a library with `require("name")`; `component`,
`computer`, `unicode` and `os` are also globals. The machine-level functions that exist before any OS is loaded
(and that EEPROM code uses) are documented in [LUA.md](LUA.md); the methods of components (gpu, filesystem,
modem, ...) in [COMPONENTS.md](COMPONENTS.md).

Conventions:

- `name(a[, b])` – `b` is optional. `-> x, y` lists return values.
- "Returns `nil, reason`" means the function returns `nil` and an error message instead of raising an error.
- Paths may be absolute or relative to the working directory (`shell.getWorkingDirectory()`).
- Names follow OpenOS (the OpenComputers OS) where a function exists there; differences are noted.

The same text is available in game as `man <library>` (generated from this file).

Contents: [component](#component) · [computer](#computer) · [event](#event) · [filesystem](#filesystem) ·
[io](#io) · [keyboard](#keyboard) · [net](#net) · [graphics](#graphics) · [note](#note) · [os](#os) ·
[process](#process) · [thread](#thread) · [rc](#rc) · [serialization](#serialization) · [shell](#shell) ·
[sides](#sides) · [colors](#colors) · [term](#term) · [text](#text) · [uuid](#uuid) · [package](#package)

---

## component

The OS adds *primary components* to the machine's `component` library: for each type, one component is the
default (the first found, or the one chosen with `setPrimary`). All the raw functions (`list`, `invoke`, `proxy`,
`type`, `slot`, `methods`, `doc`, `get`, `fields`) are unchanged; see [LUA.md](LUA.md#component).

### component.isAvailable(type) -> boolean

Whether a component of this type is available (that is, there is a primary one).

- `type` (string): a component type such as `"gpu"` or `"modem"`.

```lua
if component.isAvailable("modem") then print("networking available") end
```

### component.getPrimary(type[, quiet]) -> proxy

The proxy of the primary component of a type. The first call picks the first component of that type found; when
it is removed, the next call picks another.

- `type` (string): component type.
- `quiet` (boolean): return `nil` instead of raising an error when there is none.
- Errors: `no primary '<type>' available` (unless `quiet`).

```lua
local gpu = component.getPrimary("gpu")
gpu.set(1, 1, "hello")
```

### component.setPrimary(type, address)

Makes a component the primary one of its type, or with `address = nil` forgets the choice (the next
`getPrimary` picks the first one found again). Pushes a `component_available` signal.

- `address` (string or nil): full address or a unique prefix.

### component.isPrimary(address) -> boolean

Whether the component with this address is the primary one of its type.

### Primary components as fields

`component.<type>` is shorthand for `component.getPrimary("<type>", true)`: `component.gpu`, `component.modem`,
`component.eeprom`, `component.redstone`, ... It is `nil` when there is no component of that type.

```lua
component.gpu.setForeground(0xFF0000)
```

### Signals

| Signal | Values | When |
|---|---|---|
| `component_available` | type | a component of a type without a primary appears, or `setPrimary` was called |
| `component_unavailable` | type | the primary component of a type is removed |

---

## computer

The machine's `computer` library ([LUA.md](LUA.md#computer)) plus:

### computer.runlevel() -> number

Always `1` once the OS has booted (OpenOS reports `"S"` while booting).

### computer.getProgramLocations() -> table

Where programs that aren't installed could be found (OpenOS uses this for hints). Computah OS has none: returns `{}`.

Inside the OS, `computer.pullSignal(timeout)` waits through the scheduler, so other threads keep running while one
waits; see [process](#process). Use `event.pull` in programs.

---

## event

Signals and timers. Every waiting thread receives every signal, so threads never steal events from each other.

### event.pull([timeout,] [name,] ...) -> name, ...

Waits for a signal and returns its name and values, or `nil` after `timeout` seconds.

- `timeout` (number): seconds to wait at most. Without it, waits forever.
- `name` (string): a Lua pattern the signal name must match fully (`"key_.*"`), or an exact name.
- `...`: filters; each non-nil filter must equal the signal's value at that position.
- Errors: `interrupted` when the user presses Ctrl+C while this program is in the foreground.

```lua
local _, _, char, code = event.pull("key_down")             -- any key
local _, addr, x, y = event.pull(5, "touch")                -- a touch within 5 s
local _, _, from, port, _, msg = event.pull("modem_message", nil, nil, 42)  -- port 42 only
```

### event.pullFiltered([timeout,] filter) -> name, ...

Waits for a signal for which `filter(name, ...)` returns true.

```lua
local ev = {event.pullFiltered(function(name, _, char) return name == "key_down" and char == 13 end)}
```

### event.pullMultiple([timeout,] name, ...) -> name, ...

Waits for a signal with any of the given names (exact names).

### event.listen(name, callback) -> boolean

Calls `callback(name, ...)` for every signal with this exact name, whatever thread is running. Returns false if
that callback was already registered. If the callback returns `false` it is removed.

Callbacks run between threads and **must not block**: no `event.pull`, `os.sleep` or `computer.pullSignal` inside
them. Errors are passed to `event.onError`.

```lua
event.listen("redstone_changed", function(_, address, side, old, new)
  print("side " .. side .. " is now " .. new)
end)
```

### event.ignore(name, callback) -> boolean

Removes a callback added with `event.listen`. Returns whether it was registered.

### event.timer(interval, callback[, times]) -> id

Calls `callback()` after `interval` seconds, `times` times (default 1; `math.huge` repeats forever). Timers run
like listeners (must not block). Returns an id for `event.cancel`.

```lua
local id = event.timer(1, function() computer.beep() end, math.huge)
```

### event.cancel(id) -> boolean

Stops a timer. Returns whether it existed.

### event.push(name, ...) -> boolean

Queues a signal for this computer (same as `computer.pushSignal`). Values may be nil, booleans, numbers, strings
and simple tables (copied). Returns false if the queue (256 signals) is full.

### event.onError(message)

Called with a message when a listener or timer raises an error. By default it writes the message to the kernel log
(see `dmesg`). Assign your own function to handle errors differently.

```lua
event.onError = function(msg) io.stderr:write(msg .. "\n") end
```

### event.handlers

Table `{[name] = {callbacks...}}` of the registered listeners (read it, don't change it; use listen/ignore).

---

## filesystem

The virtual file tree. Every filesystem component is mounted somewhere under `/`: the boot drive at `/`, the
temporary filesystem at `/tmp`, other drives at `/mnt/<first 3 address characters>`.

### filesystem.canonical(path) -> string

The absolute form of a path, with `.` and `..` resolved (`..` never goes above `/`). Relative paths are taken from
the working directory. Doesn't follow links (see `realPath`).

### filesystem.concat(path, ...) -> string

Joins path parts with `/` and resolves `.`/`..`. Absolute if the first part is.

```lua
filesystem.concat("/home", "user", "../x.lua")  --> "/home/x.lua"
```

### filesystem.segments(path) -> table

The parts of a path: `segments("/a/b/../c")` → `{"a", "c"}`.

### filesystem.path(path) -> string

The directory part of a path, ending in `/`: `path("/a/b/c")` → `"/a/b/"`.

### filesystem.name(path) -> string

The last part of a path: `name("/a/b/c.lua")` → `"c.lua"`.

### filesystem.mount(fs, path) -> boolean

Mounts a filesystem at a path.

- `fs`: a filesystem proxy or a component address.
- Returns `nil, reason` if it isn't a filesystem or something is already mounted there.

### filesystem.umount(target) -> boolean

Unmounts by mount path, by proxy, or by component address (or unique prefix). Returns whether anything was unmounted.

### filesystem.mounts() -> iterator

Iterates over mounts: `for proxy, path in filesystem.mounts() do ... end`.

### filesystem.get(path) -> proxy, relative, mountPath

The filesystem proxy holding a path, the path relative to that filesystem, and where it is mounted. Follows links.
Returns `nil, reason` if nothing is mounted there.

### filesystem.proxy(filter) -> proxy

The proxy of a filesystem by address prefix or by label (mounted or not). Returns `nil, reason` if none matches.

```lua
local disk = filesystem.proxy("Computah OS")
```

### filesystem.exists(path) -> boolean

Whether a file, directory, mount point or link exists at the path.

### filesystem.isDirectory(path) -> boolean

Whether the path is a directory (or a mount point, or a link to a directory).

### filesystem.size(path) -> number

Size of a file in bytes (0 for directories and missing files).

### filesystem.lastModified(path) -> number

When a file was last changed, in milliseconds since the epoch (0 if unknown).

### filesystem.isReadOnly(path) -> boolean

Whether the filesystem holding the path is read-only.

### filesystem.list(path) -> iterator

Iterates over a directory's entries, sorted; directories end with `/`. Includes mount points and links in that
directory. Returns `nil, "no such directory"` if it isn't one.

```lua
for name in filesystem.list("/home") do print(name) end
```

### filesystem.makeDirectory(path) -> boolean

Creates a directory and any missing parent directories. Returns false if it exists, `nil, reason` on failure
(read-only, no space).

### filesystem.remove(path) -> boolean

Removes a file, or a directory with everything in it. For a link, removes the link (not its target).

### filesystem.rename(from, to) -> boolean

Moves or renames. Across filesystems, files are copied then removed (directories can't be moved across filesystems).

### filesystem.copy(from, to) -> boolean

Copies a file. Returns `nil, reason` on failure.

### filesystem.link(target, linkpath) -> boolean

Creates a symbolic link: `linkpath` then refers to `target` (a file or directory, relative targets are taken from
the link's directory). Links are virtual, like in OpenOS: they live in memory and disappear at reboot (they do
survive the computer being saved and loaded). Returns `nil, "file already exists"` if `linkpath` exists.

```lua
filesystem.link("/mnt/a1b", "/data")
```

### filesystem.isLink(path) -> boolean, target

Whether the path is a link, and where it points.

### filesystem.realPath(path) -> string

The canonical path with every link followed.

### filesystem.open(path[, mode]) -> handle

Opens a file without buffering; normally use `io.open`. `mode` is `"r"` (default), `"w"`, `"a"`, optionally with
`"b"`. Returns a handle with `:read(n)`, `:write(data)`, `:seek(whence, offset)` and `:close()`, or `nil, reason`.

### filesystem.readAll(path) -> string

The whole contents of a file, or `nil, reason`.

### filesystem.writeAll(path, data) -> boolean

Replaces a file's contents. Returns `nil, reason` on failure.

### filesystem.mountAll()

Mounts every filesystem that isn't mounted yet at `/mnt/<prefix>`, and keeps doing so as drives are added and
removed. The OS calls this at boot.

### filesystem.isAutorunEnabled() -> boolean

Whether `/autorun.lua` on a newly added drive (for example a floppy put in a disk drive) runs automatically. Off by
default; stored in `/etc/filesystem.cfg`.

### filesystem.setAutorunEnabled(enabled)

Turns autorun on or off. The autorun file runs as a background process with its path as the argument.

---

## io

Streams: files, the terminal and pipes. `io.stdin`, `io.stdout` and `io.stderr` belong to the running process, so
the shell can redirect them per program. `print` writes to `io.stdout`.

### io.open(path[, mode]) -> stream

Opens a file as a buffered stream. `mode`: `"r"` (default), `"w"` (truncate), `"a"` (append), optionally with
`"b"` (no difference: all files are bytes). Returns `nil, reason` if it can't be opened.

```lua
local f = assert(io.open("/home/notes.txt", "w"))
f:write("line 1\n", 42, "\n")
f:close()
for line in io.lines("/home/notes.txt") do print(line) end
```

### io.lines([path[, format...]]) -> iterator

Iterates over the lines of a file (closing it at the end), or of `io.input()` without a path. Formats as for
`stream:read`.

### io.read(format...) -> ...

`io.input():read(...)`. From the terminal this reads a line typed by the user.

### io.write(value...) -> stream

`io.output():write(...)`.

### io.input([file]) -> stream

The current process's standard input; with a stream or a path, replaces it first.

### io.output([file]) -> stream

The current process's standard output; with a stream or a path (opened for writing), replaces it first.

### io.error([file]) -> stream

The current process's standard error stream; with a stream, replaces it first. On the terminal it prints in red.

### io.close([stream])

Closes a stream (default: the standard output).

### io.type(value) -> string

`"file"` for an open stream, `"closed file"` for a closed one, otherwise nil.

### io.popen(command[, mode]) -> stream

Runs a shell command in the background and connects to it. Mode `"r"` (default): returns a stream reading what the
command writes. Mode `"w"`: returns a stream whose data becomes the command's input; close it to finish.

```lua
local p = io.popen("ls /bin")
for line in p:lines() do print("program: " .. line) end
```

### io.tmpfile() -> stream

Creates a new empty file in `/tmp` and returns it open for writing.

### io.pipe() -> writer, reader

A connected pair of streams: what is written to `writer` can be read from `reader` (reads wait for data; they end
when the writer is closed). Used by the shell for `a | b`.

### io.stream(backend) -> stream

Makes a buffered stream from a table with any of `readChunk(self) -> string|nil`, `writeChunk(self, data)`,
`seekTo(self, whence, offset)` and `closeIt(self)`. For writing your own stream types.

### io.isTerminal(stream) -> boolean

Whether a stream is the terminal (programs format output differently for files and pipes, like `ls`).

### Stream methods

| Method | Description |
|---|---|
| `stream:read(format...)` | `"l"` a line without the newline (default), `"L"` with it, `"n"` a number, `"a"` everything left, or a number of bytes. Returns nil at the end. |
| `stream:lines([format])` | Iterator over reads. |
| `stream:write(value...)` | Writes strings and numbers; returns the stream, or `nil, reason`. |
| `stream:seek([whence[, offset]])` | `"set"`, `"cur"` (default) or `"end"`; returns the new position. Files only. |
| `stream:close()` | Closes it. |
| `stream:flush()`, `stream:setvbuf()` | No-ops, for compatibility. |

---

## keyboard

Key codes and modifier state. Key codes in `key_down`/`key_up` signals are **GLFW key codes** (not the LWJGL 2
codes OpenComputers uses); use `keyboard.keys` instead of numbers.

### keyboard.keys

Table mapping names to codes and codes back to names: letters `a`–`z`, digits `"0"`–`"9"`, `enter`, `back`
(backspace), `tab`, `space`, `escape`, `delete`, `insert`, `up`, `down`, `left`, `right`, `home`, `end`, `pageUp`,
`pageDown`, `f1`–`f12`, `lshift`, `rshift`, `lcontrol`, `rcontrol`, `lmenu`, `rmenu` (alt), `numpad0`–`numpad9`,
`numpadenter` and punctuation names (`minus`, `equals`, `comma`, `period`, `slash`, `semicolon`, `apostrophe`,
`lbracket`, `rbracket`, `backslash`, `grave`).

```lua
local _, _, char, code = event.pull("key_down")
if code == keyboard.keys.up then ... end
```

### keyboard.isKeyDown(code) -> boolean

Whether a key (code or name) is held, according to the key_down/key_up signals seen so far.

### keyboard.isControlDown() -> boolean

Whether either Control key is held.

### keyboard.isShiftDown() -> boolean

Whether either Shift key is held.

### keyboard.isAltDown() -> boolean

Whether either Alt key is held.

### keyboard.isControl(char) -> boolean

Whether a character code is a control character (below 32, or 127–159).

---

## net

Messages over the primary modem (network or wireless card), and host names. See also
[NETWORKING.md](NETWORKING.md).

### net.modem() -> proxy

The primary modem, or nil if there is no network or wireless card.

### net.open(port) -> boolean

Opens a port on the primary modem (1–65535). Errors if there is no modem.

### net.close([port]) -> boolean

Closes a port, or all ports.

### net.send(address, port, ...) -> boolean

Sends a message to one modem. Values: nil, booleans, numbers, strings (8 values, 8 KiB in total).

### net.broadcast(port, ...) -> boolean

Sends a message to every modem in reach.

### net.receive([timeout[, port]]) -> from, port, distance, ...

Waits for a message (on one port, or any open port). Returns the sender's address, the port, the distance and the
values, or nil after the timeout.

```lua
net.open(5)
local from, port, distance, text = net.receive(10, 5)
```

### net.hostname() -> string

This computer's host name (`os.hostname()`).

### net.discover([hostname[, timeout]]) -> table

Broadcasts a lookup on port 1 and collects answers for `timeout` seconds (default 1). Returns a list of
`{address, hostname, distance}`. Only computers running the `beacon` service (enabled by default) answer; with a
host name, only that host answers.

### net.resolve(name[, timeout]) -> address

The address of a host by name (a full address is returned as is), or nil.

---

## graphics

Pixel drawing on the primary GPU (see [GRAPHICS.md](GRAPHICS.md)). Coordinates start at 1; colours are pixel
colour indices (0–15 palette, 16–255 fixed cube). Pixels show where text cells are transparent, which
`graphics.clear` arranges.

### graphics.gpu() -> proxy

The GPU the terminal uses, or the primary GPU.

### graphics.size() -> width, height

The size of the pixel layer.

### graphics.color(rgb) -> index

The pixel colour index closest to an RGB colour.

### graphics.clear([color])

Makes all text cells transparent and fills the pixel layer with a colour (default 0).

### graphics.pixel(x, y, color)

Sets one pixel.

### graphics.get(x, y) -> index

The colour index of a pixel.

### graphics.line(x1, y1, x2, y2, color)

Draws a line.

### graphics.rect(x, y, width, height, color[, filled])

Draws a rectangle outline, or a filled rectangle.

### graphics.circle(x, y, radius, color[, filled])

Draws a circle outline, or a filled circle.

### graphics.text(x, y, text, color[, scale])

Draws text into the pixel layer with the screen font (8×16 per character, times `scale`).

### graphics.sprite(width, height[, data]) -> sprite

A sprite table `{width, height, data}`; `data` holds one colour index byte per pixel, row by row (default all 0).

### graphics.draw(sprite, x, y)

Draws a sprite.

### graphics.capture(x, y, width, height) -> sprite

Reads a rectangle of the screen into a sprite.

### graphics.loadImage(path) -> sprite

Loads a `.cpi` image (`"CPI1"`, width and height as 2-byte big-endian numbers, then the pixels). Returns
`nil, reason` on failure.

### graphics.saveImage(path, sprite) -> boolean

Saves a sprite as a `.cpi` image.

---

## note

Musical notes for `computer.beep`. A note is a name (`"A4"`, `"C#5"`, `"Bb3"`), a MIDI number (69 = A4) or a
frequency in Hz (above 127).

### note.midi(note) -> number

The MIDI number of a note.

### note.freq(note) -> number

The frequency of a note in Hz: `note.freq("A4")` → 440.

### note.name(note) -> string

The name of a note: `note.name(60)` → `"C4"`.

### note.play(note[, duration])

Plays a note for `duration` seconds (default 0.25). Frequencies are limited to 20–2000 Hz.

```lua
for _, n in ipairs({"C4", "E4", "G4", "C5"}) do note.play(n, 0.2) os.sleep(0.25) end
```

---

## os

The standard `os` functions (`clock`, `date`, `time`, `difftime`; see [LUA.md](LUA.md#os)) plus:

### os.sleep([seconds])

Waits; other threads keep running and no signals are lost (every thread sees every signal). `os.sleep(0)` waits
one tick. Errors with `interrupted` on Ctrl+C.

### os.getenv([name]) -> value

An environment variable of the current process (inherited from its parent), or all of them as a table without a
name. `PWD` is the working directory.

### os.setenv(name, value) -> value

Sets an environment variable of the current process (`nil` removes it). Setting `PWD` changes the working directory.

### os.exit([code])

Ends the running program. The shell treats `0`, `true` or nothing as success.

### os.remove(path) -> boolean

Removes a file or directory (`filesystem.remove`).

### os.rename(from, to) -> boolean

Moves or renames (`filesystem.rename`).

### os.tmpname() -> string

A path in `/tmp` that doesn't exist yet.

### os.execute([command]) -> boolean, code

Runs a shell command line and waits for it.

### os.hostname() -> string

This computer's name: `$HOSTNAME`, else `/etc/hostname`, else the first 8 characters of its address.

---

## process

The scheduler. Every program runs as a **thread** (a coroutine); the scheduler waits for signals on behalf of all
threads and gives every waiting thread every signal. A **process** is a thread together with its data: environment
variables, working directory and standard streams, inherited from the thread that started it. Most programs use
[thread](#thread), [event](#event) and [shell](#shell) instead.

### process.spawn(fn[, name[, options]]) -> thread

Starts `fn` in a new thread, a child of the running one. Options: `detached` (keep running after the parent ends),
`args` (packed arguments for `fn`), `data` (process data to use instead of inheriting). Returns the thread record
(methods below).

### process.current() -> thread

The running thread, or nil inside event listeners and timers.

### process.byId(id) -> thread

A thread by id, or nil.

### process.list() -> table

All threads: `{id, name, status, parent, detached, uptime}` each.

### process.info([thread]) -> table

OpenOS-style information about a process (default: the running one; also accepts an id): `{id, path, command,
env, data, status, parent}`. `path` is the program file, `command` its name. Returns `nil, reason` if there is none.

### process.running() -> path, env, command

The running process's program path, environment and name.

### process.data() -> table

The running process's data: `{env, cwd, io = {stdin, stdout, stderr}, path}`.

### process.load(program[, env[, init[, name]]]) -> coroutine

Loads a program file (or wraps a function) as a coroutine, like OpenOS. Resume it to run it with arguments.
`env` is the program's environment (default: a new one based on the globals); `init` is called inside the
coroutine first. Returns `nil, reason` if the file can't be loaded. Waiting inside it works normally.

```lua
local co = process.load("/home/job.lua")
coroutine.resume(co, "arg1")
```

### process.interrupt(thread)

Makes the thread's current or next wait raise `interrupted` (what Ctrl+C does to the foreground program).

### process.run()

Runs the scheduler forever. Only the kernel calls this.

### Thread methods

The records returned by `process.spawn` (and wrapped by `thread.create`):

| Method | Description |
|---|---|
| `t:getStatus()` | `"running"` (the caller), `"ready"`, `"waiting"`, `"suspended"` or `"dead"`. |
| `t:join([timeout])` | Waits until it ends; true, or false on timeout. |
| `t:kill()` | Ends it (and its attached children). |
| `t:suspend()` / `t:resume()` | Pause it (it gets no signals) / let it continue. |
| `t:detach()` / `t:attach([parent])` | Survive / don't survive the parent ending. |
| `t:results()` | What its function returned, or `nil, error`. |
| `t.id`, `t.name`, `t.data`, `t.error` | Fields. |

---

## thread

Threads for programs: functions that run alongside the current program. A thread is attached to the process that
created it and is killed when that process ends, unless detached.

### thread.create(fn, ...) -> thread

Starts `fn(...)` in a new thread and returns a thread object:

| Method | Description |
|---|---|
| `t:status()` | `"running"`, `"ready"`, `"waiting"`, `"suspended"` or `"dead"` |
| `t:join([timeout])` | wait for it to end; true, or false on timeout |
| `t:kill()`, `t:suspend()`, `t:resume()` | stop / pause / continue |
| `t:detach()`, `t:attach()` | keep running in the background / stop with the parent |
| `t.id` | its process id (see `ps`) |

```lua
local t = thread.create(function(n)
  for i = 1, n do print("tick " .. i) os.sleep(1) end
end, 3)
t:join()
```

### thread.current() -> thread

The running thread record, or nil.

### thread.waitForAll(threads[, timeout]) -> boolean

Waits until every thread in the list ends. False if the timeout passed first.

### thread.waitForAny(threads[, timeout]) -> boolean

Waits until any thread in the list ends. False if the timeout passed first.

---

## rc

Background services ("daemons"). A service is `/etc/rc.d/NAME.lua` defining `start()` and optionally `stop()`,
`status()` and other commands. `/etc/rc.cfg` lists services started at boot (`enabled = {...}`) and settings per
service (`NAME = {...}`, visible to the script as the global `args`). The `rc` program wraps this library.

### rc.run(name, command, ...) -> ok, result

Runs a command of a service: `"start"`, `"stop"`, `"restart"`, `"status"` or any function the script defines.
Loads the script on first use. Returns `nil, reason` on failure.

### rc.runCommand(name, command, ...) -> ok, result

The OpenOS name for `rc.run`.

### rc.allRunCommand(command, ...) -> table

Runs a command on every running service: `{[name] = {ok, result}}`.

### rc.load(name) -> environment

Loads a service script and returns its environment (the table with its functions).

### rc.list() -> table

Names of all services in `/etc/rc.d`.

### rc.isEnabled(name) -> boolean

Whether a service starts at boot.

### rc.enable(name) / rc.disable(name)

Adds a service to / removes it from the boot list in `/etc/rc.cfg`.

### rc.startEnabled()

Starts every enabled service. The OS calls this at boot.

### rc.loaded, rc.running

Tables of loaded service environments and of running services (`{[name] = true}`).

---

## serialization

### serialization.serialize(value[, pretty]) -> string

Turns a value (nil, boolean, number, string, or tables of those) into Lua source text. Tables with cycles raise an
error. `pretty = true` adds line breaks and indentation, and shows other values (functions) as names instead of
failing.

### serialization.unserialize(text) -> value

Reads text made by `serialize` back into a value. Returns `nil, reason` for invalid text. The text can't run code.

```lua
filesystem.writeAll("/home/cfg", serialization.serialize({volume = 3, names = {"a", "b"}}))
local cfg = serialization.unserialize(filesystem.readAll("/home/cfg"))
```

---

## shell

Running programs and command lines. Syntax: see `man sh` or [OS.md](OS.md#shell).

### shell.execute(command) -> ok, code

Runs a command line (pipes, redirection, `;`, `&&`, `||`, `&`, variables, globs) and waits for it, except for
background jobs. Returns success and the exit code or error.

### shell.run(program, ...) -> ok, code

Runs one program by name with arguments and waits for it.

### shell.spawn(program, args[, streams[, detached]]) -> thread

Starts a program as a new process without waiting. `args` is a list; `streams` may set `stdin`, `stdout`,
`stderr`. Returns the thread, or `nil, reason`.

### shell.resolve(name[, extension]) -> path

Finds a program: with an extension (`"lua"`), searches `$PATH`; names containing `/` are resolved directly.
Returns `nil, reason` if not found.

### shell.load(path) -> function, env

Loads a program file with a fresh environment based on the globals.

### shell.parse(...) -> args, options

Splits arguments into plain arguments and options: `-abc` sets `a`, `b` and `c`; `--name=value` sets `name`;
`--` ends options.

```lua
local args, opts = shell.parse(...)
if opts.v then print("verbose") end
```

### shell.parseLine(command) -> entries

Parses a command line into `{pipeline = {commands}, background, op}` entries without running it.

### shell.getWorkingDirectory() -> path

The current process's working directory.

### shell.setWorkingDirectory(path) -> boolean

Changes the working directory. Returns `nil, "not a directory"` if it isn't one.

### shell.getAlias(name) -> string

What an alias stands for, or nil.

### shell.setAlias(name, value)

Defines an alias (or removes it with `nil`): typing `name` runs `value`, with further words passed on. Defaults:
`ll`, `la`, `dir`, `cls`, `?`.

### shell.aliases() -> iterator

Iterates over aliases: `for name, value in shell.aliases() do ... end`.

### shell.getPath() -> string

The program search path (`$PATH`).

### shell.setPath(path)

Sets the program search path.

### shell.builtins

Table of shell builtins (`cd`, `pwd`, `set`, `unset`, `alias`, `unalias`, `exit`); add functions to make your own.

---

## sides

Side numbers for redstone and side-based methods; they match Minecraft's directions. Names map to numbers and
numbers to names.

| Number | Names |
|---|---|
| 0 | `bottom`, `down`, `negy` |
| 1 | `top`, `up`, `posy` |
| 2 | `north`, `back`, `negz` |
| 3 | `south`, `front`, `forward`, `posz` |
| 4 | `west`, `right`, `negx` |
| 5 | `east`, `left`, `posx` |

The relative names (`front`, `back`, `left`, `right`) exist for OpenComputers programs; computers have no facing,
so they always mean the compass directions above.

---

## colors

Names for the 16 palette entries, for `gpu.setForeground(colors.red, true)`: `black` 0, `darkgray` 1, `gray` 3,
`silver` 6, `lightgray` 7, `white` 9, `red` 10, `green` 11, `blue` 12, `yellow` 13, `purple` 14, `cyan` 15, and
`transparent` (-1, for backgrounds). `colors.rgb` holds common RGB values (`colors.rgb.orange` → `0xFFAA00`).
These differ from OpenComputers' colour numbers.

---

## term

The terminal: text with a cursor, wrapping and scrolling on the primary GPU and screen, and line input from the
keyboards attached to that screen.

### term.write(value[, wrap])

Writes text at the cursor and moves it. `wrap` (default true) wraps at the right edge. Understands `\n`, `\r`,
`\t`, `\b` and ANSI colour codes `ESC[...m` (0 reset, 1 bold, 3 italic, 4 underline, 5 blink, 7 inverse,
9 strikethrough, 30–37/90–97 foreground, 40–47/100–107 background, 39/49 default colours).

```lua
term.write("\27[31mError:\27[0m something failed\n")
```

### term.read([options]) -> string

Reads a line with editing: arrows, Home/End, Backspace/Delete, Up/Down for history, Tab for completion,
Ctrl+Shift+V paste. Returns the line without the newline, `""` after Ctrl+C, or nil after Ctrl+D on an empty line
(and when there is no screen). Options table:

- `history` (table): previous lines; the new line is appended.
- `complete` (function(line, cursorPos) -> list): completions cycled with Tab.
- `pwchar` (string): shown instead of each typed character.
- `nobreak` (boolean): don't move to a new line at the end.

### term.pull([timeout,] [name,] ...) -> name, ...

`event.pull` with a blinking cursor shown at the cursor position while waiting.

### term.clear()

Clears the screen and puts the cursor at the top left.

### term.clearLine()

Clears the cursor's line and moves the cursor to its start.

### term.getCursor() -> x, y

The cursor position.

### term.setCursor(x, y)

Moves the cursor.

### term.getCursorBlink() -> boolean

Whether the cursor blinks while reading input.

### term.setCursorBlink(enabled)

Turns cursor blinking on or off.

### term.getViewport() -> width, height

The terminal's size in characters.

### term.getGlobalArea() -> x, y, width, height

The terminal's area on the screen (always the whole screen: `1, 1, width, height`).

### term.scroll([lines])

Scrolls the text up (default one line).

### term.isAvailable() -> boolean

Whether the terminal has a GPU and screen.

### term.gpu() -> proxy

The terminal's GPU.

### term.screen() -> address

The terminal's screen.

### term.keyboard() -> address

The first keyboard attached to the terminal's screen, or nil.

### term.keyboards() -> table

All keyboards attached to the terminal's screen.

### term.bind([gpu[, screen]]) -> boolean

Binds the terminal to a GPU and screen (addresses; default the primary ones), sets the maximum resolution and
clears the screen. Happens automatically at boot and when a GPU or screen appears.

---

## text

String helpers.

### text.trim(s) -> string

Removes leading and trailing whitespace.

### text.split(s[, separator[, keepEmpty]]) -> table

Splits on a Lua pattern (default: runs of whitespace). Empty parts are dropped unless `keepEmpty`.

### text.tokenize(s) -> table

Splits a command line into words, keeping quoted parts together.

### text.padRight(s, width) / text.padLeft(s, width) -> string

Pads with spaces to a width (in characters).

### text.detab(s[, tabWidth]) -> string

Replaces tabs with spaces (default width 4).

### text.wrap(s, width) -> table

Wraps text to a width; returns the lines.

### text.wrappedLines(s, width[, maxWidth]) -> iterator

Iterates over wrapped lines (`maxWidth` is accepted for OpenOS programs and ignored).

### text.removeEscapes(s) -> string

Removes ANSI colour codes.

### text.size(bytes) -> string

A human-readable size: `text.size(2048)` → `"2.0K"`.

---

## uuid

### uuid.next() -> string

A random version 4 UUID, like `"3f2b9c1e-8d4a-4f6b-9a2c-1e5d7f3a9b0c"`.

---

## package

`require` and module search.

### require(name) -> module

Loads a library once and returns it. Searches `package.path`
(`/lib/?.lua;/usr/lib/?.lua;/home/lib/?.lua;./?.lua;/lib/?/init.lua;/usr/lib/?/init.lua`). Errors if it isn't
found or fails to load.

### package.searchpath(name, path) -> filename

Finds a module file along a path string (`?` replaced by the name with dots as slashes). Returns
`nil, triedPaths` if not found.

### package.loaded, package.preload, package.path

Loaded modules, loader functions to use instead of files, and the search path.

---

## Globals

### print(...)

Writes its arguments, converted with `tostring` and separated by tabs, plus a newline, to `io.stdout`.

### _OSVERSION

`"Computah OS 1.0"`.
