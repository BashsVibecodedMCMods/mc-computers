# Component reference

Generated from the `@Callback` documentation in the source. Regenerate it with `python3 tools/components_doc.py .` after changing a component; a unit test fails when a method is missing here.

Methods marked **(tick)** are indirect: they run on the server thread, and the calling program waits for the next tick. Others are direct and return immediately (some are limited per tick, see GRAPHICS.md for GPU budgets).

## computer

The computer itself (its own address).

- `isRunning():boolean` — Whether the computer is running.
- `beep([frequency:number|pattern:string[, duration:number]])` — Plays a tone, or a pattern of short (.) and long (-) beeps.
- `stop():boolean` **(tick)** — Stops the computer (when called on another computer).
- `start():boolean` **(tick)** — Starts this computer.

## eeprom

The BIOS chip. See EEPROM.md.

- `get():string` — The code stored on the EEPROM.
- `set(code:string)` **(tick)** — Replaces the code (up to 4096 bytes). Fails if read-only.
- `getData():string` — The data area (by convention the boot address).
- `setData(data:string)` **(tick)** — Replaces the data area (up to 256 bytes). Works even when read-only.
- `getLabel():string` — The label of the EEPROM.
- `setLabel(label:string):string` **(tick)** — Sets the label (up to 24 characters). Fails if read-only.
- `getSize():number` — The code capacity in bytes.
- `getDataSize():number` — The data capacity in bytes.
- `getChecksum():string` — CRC32 of the code, as 8 hex digits.
- `isReadOnly():boolean` — Whether the code and label are locked.
- `makeReadonly(checksum:string):boolean` **(tick)** — Locks the code and label forever. The checksum must match getChecksum().

## filesystem

Hard drives, floppies, the OS disk and the tmpfs. Paths use "/" and are relative to the drive.

- `spaceUsed():number` — The used storage space in bytes.
- `spaceTotal():number` — The total storage space in bytes.
- `isReadOnly():boolean` — Whether the file system is read-only.
- `getLabel():string` — The label of the drive.
- `setLabel(value:string):string` **(tick)** — Sets the label of the drive. Returns the new label.
- `exists(path:string):boolean` — Whether an object exists at the path.
- `isDirectory(path:string):boolean` — Whether the path is a directory.
- `size(path:string):number` — The size of the file in bytes (0 for directories).
- `lastModified(path:string):number` — When the object was last modified, in milliseconds since the epoch.
- `list(path:string):table` — The entries of a directory; directories end with '/'.
- `makeDirectory(path:string):boolean` — Creates a directory, and any missing parents.
- `remove(path:string):boolean` — Removes a file or a directory with everything in it.
- `rename(from:string, to:string):boolean` — Renames or moves an object.
- `open(path:string[, mode:string='r']):number` — Opens a file ('r', 'rb', 'w', 'wb', 'a', 'ab'). Returns a handle.
- `read(handle:number, count:number):string or nil` — Reads up to count bytes; nil at end of file.
- `write(handle:number, data:string):boolean` — Writes data to the file.
- `seek(handle:number, whence:string, offset:number):number` — Moves the position: 'set', 'cur' or 'end'.
- `close(handle:number)` — Closes the handle.

## gpu

Graphics card. Coordinates start at 1. See GRAPHICS.md.

- `bind(address:string[, reset:boolean=true]):boolean` — Binds to a screen. reset restores its default resolution and colours.
- `getScreen():string` — The address of the bound screen, or nil.
- `setForeground(color:number[, isPaletteIndex:boolean]):number` — Sets the text foreground (0xRRGGBB or a palette index 0-15). Returns the old one.
- `setBackground(color:number[, isPaletteIndex:boolean]):number` — Sets the text background; -1 is transparent (shows the pixel layer). Returns the old one.
- `getForeground():number, number|nil` — The foreground colour, and its palette index if it was set from one.
- `getBackground():number, number|nil` — The background colour (-1 = transparent), and its palette index if it was set from one.
- `setStyle(style:table):table` — Sets the text style for following writes: {bold, italic, underline, strikethrough, inverse, blink}. Returns the old one.
- `getStyle():table` — The current text style.
- `getPaletteColor(index:number):number` — A palette colour (0-15).
- `setPaletteColor(index:number, color:number):number` — Changes a palette colour (0-15); also recolours pixels using it. Returns the old colour.
- `maxDepth():number` — The highest colour depth (1, 4 or 8 bits) of this GPU and screen.
- `getDepth():number` — The current colour depth.
- `setDepth(depth:number):string` — Sets the colour depth (1, 4 or 8). Returns the old one as a name.
- `maxResolution():number, number` — The largest text resolution of this GPU and screen.
- `getResolution():number, number` — The current text resolution.
- `setResolution(width:number, height:number):boolean` — Sets the text resolution.
- `getViewport():number, number` — Same as getResolution (kept for OpenComputers programs).
- `setViewport(width:number, height:number):boolean` — Same as setResolution.
- `get(x:number, y:number):string, number, number, nil, nil, table` — The character, foreground, background and style at a cell.
- `set(x:number, y:number, value:string[, vertical:boolean]):boolean` — Writes text with the current colours and style.
- `fill(x:number, y:number, width:number, height:number, char:string):boolean` — Fills a rectangle of cells.
- `copy(x:number, y:number, width:number, height:number, tx:number, ty:number):boolean` — Copies a rectangle of cells by an offset.
- `clearText()` — Makes every cell an empty, transparent space so only the pixel layer shows.
- `getPixelResolution():number, number` — The size of the pixel layer.
- `getPixelColors():number` — How many pixel colours are available (16 or 256).
- `getPixelColor(index:number):number` — The RGB colour of a pixel colour index (0-15 palette, 16-255 fixed).
- `findPixelColor(rgb:number):number` — The pixel colour index nearest to an RGB colour.
- `setPixel(x:number, y:number, color:number)` — Sets one pixel to a colour index.
- `getPixel(x:number, y:number):number` — The colour index of a pixel.
- `fillRect(x:number, y:number, width:number, height:number, color:number)` — Fills a rectangle of pixels.
- `drawLine(x1:number, y1:number, x2:number, y2:number, color:number)` — Draws a line.
- `drawRect(x:number, y:number, width:number, height:number, color:number)` — Draws a rectangle outline.
- `drawCircle(x:number, y:number, radius:number, color:number[, filled:boolean])` — Draws a circle.
- `drawText(x:number, y:number, text:string, color:number[, scale:number=1])` — Draws text into the pixel layer with the screen font (8x16 per character, times scale).
- `blit(x:number, y:number, width:number, height:number, data:string)` — Draws a block of pixels; data holds one colour index byte per pixel, row by row.
- `readPixels(x:number, y:number, width:number, height:number):string` — Reads a block of pixels in the format blit takes.
- `copyPixels(x:number, y:number, width:number, height:number, tx:number, ty:number)` — Copies a block of pixels by an offset.
- `clearPixels([color:number=0])` — Sets every pixel to a colour index.
- `allocateBuffer([width:number, height:number]):number` — Allocates an off-screen buffer (default: max resolution). Returns its index.
- `freeBuffer([index:number]):boolean` — Frees a buffer (default: the active one).
- `freeAllBuffers()` — Frees every buffer.
- `buffers():table` — Indices of all allocated buffers.
- `getActiveBuffer():number` — The active buffer (0 = the screen).
- `setActiveBuffer(index:number):number` — Makes drawing go to a buffer (0 = the screen). Returns the previous index.
- `getBufferSize(index:number):number, number` — The text size of a buffer.
- `totalMemory():number` — Total video memory for buffers, in bytes.
- `freeMemory():number` — Free video memory, in bytes.
- `bitblt([dst:number=0, col:number=1, row:number=1, width, height, src:number=active, fromCol:number=1, fromRow:number=1])` — Copies text cells between buffers (0 = the screen).
- `bitbltPixels(dst:number, x:number, y:number, width:number, height:number, src:number[, fromX:number=1, fromY:number=1])` — Copies pixels between buffers (0 = the screen).

## screen

A (multiblock) monitor.

- `isOn():boolean` — Whether the screen is on.
- `turnOn():boolean` — Turns the screen on. Returns whether it changed.
- `turnOff():boolean` — Turns the screen off (it shows nothing). Returns whether it changed.
- `getAspectRatio():number, number` — The size of the monitor in blocks (width, height).
- `getKeyboards():table` **(tick)** — Addresses of the keyboards attached to this monitor.
- `setPrecise(enabled:boolean):boolean` — Precise mode: touch events report fractional cell coordinates.
- `isPrecise():boolean` — Whether precise mode is on.

## keyboard

No methods; sends key_down, key_up and clipboard signals.

(no methods)

## modem

Network card (wired) or wireless card. See NETWORKING.md.

- `open(port:number):boolean` — Opens a port (1-65535) to receive messages on it. Returns whether it was closed before.
- `close([port:number]):boolean` — Closes a port, or all ports. Returns whether anything was closed.
- `isOpen(port:number):boolean` — Whether a port is open.
- `isWireless():boolean` — Whether this is a wireless card.
- `isWired():boolean` — Whether this card uses cables.
- `maxPacketSize():number` — The largest message, in bytes (each value costs its size plus 2).
- `getStrength():number` — Wireless range in blocks (tier 1).
- `setStrength(range:number):number` — Sets the wireless range (tier 1, up to the configured maximum). Returns the new range.
- `send(address:string, port:number, ...):boolean` — Sends a message to one card. Values may be nil, booleans, numbers or strings.
- `broadcast(port:number, ...):boolean` — Sends a message to every reachable card with the port open.

## redstone

Redstone card (the case or rack sides) or redstone I/O block. Sides: 0 down, 1 up, 2 north, 3 south, 4 west, 5 east.

- `getInput([side:number]):number|table` **(tick)** — The redstone input on a side, or a table of all sides.
- `getOutput([side:number]):number|table` — The redstone output on a side, or a table of all sides.
- `setOutput(side:number|table, [value:number]):number` **(tick)** — Sets the output (0-15) on a side, or several sides from a table. Returns the old value.

## disk_drive

A disk drive block. Its floppy appears as a separate filesystem component.

- `isEmpty():boolean` — Whether the drive is empty.
- `media():string` — The address of the floppy's filesystem, or nil.
- `eject():boolean` — Ejects the floppy. Returns whether there was one.

## inventory / tank / energy

Blocks reached through an adapter or a wireless link. Every method takes a tick.

- `getBlockName():string` **(tick)** — The block's registry name.
- `getPosition():number, number, number` — The block's coordinates.
- `getInventorySize():number` **(tick)** — The number of slots.
- `getStackInSlot(slot:number):table` **(tick)** — The item in a slot (1-based): {name, label, count, maxCount, damage, maxDamage}, or nil.
- `list():table` **(tick)** — All slots: {[slot] = item}.
- `pushItems(target:string, fromSlot:number[, count:number[, toSlot:number]]):number` **(tick)** — Moves items into another inventory component. Returns how many moved.
- `getTanks():table` **(tick)** — Every tank: {{name, amount, capacity}, ...} (amounts in millibuckets).
- `getEnergyStored():number` **(tick)** — Stored energy (FE).
- `getMaxEnergyStored():number` **(tick)** — Energy capacity (FE).

## Signals

| Signal | Values | Sent by |
|---|---|---|
| `component_added` | address, type | a component became visible |
| `component_removed` | address, type | a component went away |
| `key_down` / `key_up` | keyboard, char, code, player | keyboard (codes are GLFW key codes, see `keyboard.keys`) |
| `clipboard` | keyboard, text, player | Ctrl+Shift+V in the screen GUI |
| `touch` / `drag` / `drop` | screen, x, y, button, player | screen GUI clicks; right-click on tier 2+ screens in the world |
| `scroll` | screen, x, y, direction, player | mouse wheel in the screen GUI |
| `screen_resized` | screen, width, height | gpu.setResolution |
| `modem_message` | receiver, sender, port, distance, values... | modem |
| `redstone_changed` | address, side, old, new | redstone card / redstone I/O |
