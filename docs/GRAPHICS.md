# Graphics

Every screen shows two layers:

- the **text layer**: a grid of cells, each with a character, foreground colour, background colour and style;
- the **pixel layer** underneath: a grid of palette-indexed pixels.

A text cell whose background is **transparent (-1)** shows the pixels below it. After `gpu.bind`, cells are
transparent spaces until something writes to them with an opaque background; `gpu.clearText()` makes them all
transparent again.

What a program draws is shown on the monitor block in the world **and** in the screen GUI (they render the same
image), so anyone nearby sees what the user sees, including the editor and shell.

## Capabilities

The usable capability is the lower of the GPU's and the screen's tier.

| Tier | Text | Text colours | Pixels | Pixel colours |
|---|---|---|---|---|
| 1 | 50 x 16 | 1-bit (monochrome) | 160 x 100 | 16 (palette) |
| 2 | 80 x 25 | 4-bit (16-colour palette) | 320 x 200 | 256 |
| 3 | 160 x 50 | 24-bit RGB | 640 x 400 | 256 |

Pixel colours 0-15 are the adjustable palette (`gpu.setPaletteColor`); 16-255 are a fixed 6x8x5 RGB cube.
`gpu.findPixelColor(0xRRGGBB)` gives the nearest index; `gpu.getPixelColor(i)` the RGB value.

## Text API

```lua
gpu.setForeground(0xFFAA00)            -- or (index, true) for a palette colour
gpu.setBackground(0x000080)            -- -1 = transparent
gpu.setStyle({bold = true, underline = true})   -- also italic, strikethrough, inverse, blink
gpu.set(1, 1, "Hello")                 -- x, y start at 1; set(x, y, text, true) writes vertically
gpu.fill(1, 2, 20, 3, "#")
gpu.copy(1, 1, 20, 5, 0, 10)           -- move a block down by 10
local char, fg, bg, _, _, style = gpu.get(1, 1)
gpu.setResolution(80, 25)              -- up to maxResolution(); changes the cell size on the monitor
gpu.setDepth(4)                        -- 1, 4 or 8 (up to maxDepth())
```

Programs using Computah OS can also write ANSI colour codes through `term.write`/`io.write`:
`"\27[31mred\27[0m"`.

## Pixel API

```lua
gpu.clearPixels(0)
gpu.setPixel(10, 10, 12)               -- x, y, colour index
gpu.getPixel(10, 10)
gpu.fillRect(1, 1, 50, 20, 4)
gpu.drawRect(1, 1, 50, 20, 9)          -- outline
gpu.drawLine(1, 1, 160, 100, 10)
gpu.drawCircle(80, 50, 30, 13, true)   -- filled
gpu.drawText(4, 4, "Score: 10", 9, 2)  -- screen font, scale 2
gpu.blit(20, 20, 8, 8, data)           -- data: 64 bytes, one colour index per pixel, row by row
local data = gpu.readPixels(20, 20, 8, 8)
gpu.copyPixels(1, 1, 100, 100, 5, 0)   -- scroll a region
```

## Buffers (double buffering)

Draw off-screen, then copy in one go to avoid flicker:

```lua
local buf = gpu.allocateBuffer()       -- returns an index; default size = max text resolution
gpu.setActiveBuffer(buf)               -- all drawing now goes to the buffer
-- ... draw text and pixels ...
gpu.setActiveBuffer(0)                 -- back to the screen
gpu.bitblt(0, 1, 1, nil, nil, buf)     -- copy text cells
local pw, ph = gpu.getPixelResolution()
gpu.bitbltPixels(0, 1, 1, pw, ph, buf) -- copy pixels
gpu.freeBuffer(buf)
```

Video memory: 64 KiB (tier 1), 384 KiB (tier 2), 1.5 MiB (tier 3); `gpu.freeMemory()`.

## Budgets

Each GPU has a drawing budget per tick (tier 1: 96, tier 2: 192, tier 3: 384 units). `set` costs about 1,
`fill`/`copy` 1 plus area/512, `setPixel` 0.125, `fillRect` 0.5 plus area/16384, `blit` 0.5 plus area/4096, lines
and rects 0.5, circles 1. When the budget is used up the call waits for the next tick – the program slows down,
it doesn't fail. Use `blit` and buffers for large updates.

## The graphics library

```lua
local graphics = require("graphics")
graphics.clear(graphics.color(0x101020))
local w, h = graphics.size()
graphics.line(1, 1, w, h, graphics.color(0xFF0000))
graphics.circle(math.floor(w / 2), math.floor(h / 2), 20, 13, true)
graphics.text(2, 2, "Hi", 9, 2)
local s = graphics.sprite(8, 8, data)  graphics.draw(s, 10, 10)
local img = graphics.loadImage("/home/logo.cpi")   -- "CPI1", u16 width, u16 height, then 1 byte per pixel
graphics.saveImage("/home/shot.cpi", graphics.capture(1, 1, 64, 64))
```

`gfxdemo` shows all of this.

## Monitors

Screens of the same tier and facing placed next to each other merge into one monitor, up to **8 blocks wide and
6 blocks high**; larger arrangements split into several monitors. The resolution does not change with size – a
bigger monitor shows the same image larger. Attach a **keyboard** to any block of the monitor (place it on the
screen) to type: right-click the monitor or keyboard to open the screen GUI. On tier 2 and 3 monitors, clicks in
the GUI and right-clicks on the monitor face (sneak, or without a keyboard) send `touch` signals with the cell
coordinates; `screen.setPrecise(true)` gives fractional coordinates.
