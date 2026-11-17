-- gfxdemo: draws with the pixel layer (lines, circles, text and a sprite). Press any key to exit.
local graphics = require("graphics")
local term = require("term")
local event = require("event")
if not term.isAvailable() then io.stderr:write("gfxdemo needs a screen\n") return 1 end
local w, h = graphics.size()
graphics.clear(graphics.color(0x101020))
for i = 0, 15 do
  graphics.rect(1 + i * math.floor(w / 16), h - 10, math.floor(w / 16), 10, i, true)
end
for i = 1, 12 do
  graphics.line(1, 1, w, math.floor(h * i / 12), graphics.color(0x3060FF))
end
graphics.circle(math.floor(w / 2), math.floor(h / 2), math.floor(h / 4), graphics.color(0xFFCC00), true)
graphics.circle(math.floor(w / 2), math.floor(h / 2), math.floor(h / 4) + 4, graphics.color(0xFFFFFF))
graphics.text(4, 4, "Computah graphics", graphics.color(0xFFFFFF), w >= 320 and 2 or 1)
local s = graphics.sprite(8, 8)
local data = {}
for y = 0, 7 do
  for x = 0, 7 do data[#data + 1] = string.char(((x + y) % 2 == 0) and 11 or 13) end
end
s.data = table.concat(data)
for i = 0, 5 do graphics.draw(s, w - 12 - i * 10, 20 + i * 10) end
event.pull("key_down")
term.clear()
