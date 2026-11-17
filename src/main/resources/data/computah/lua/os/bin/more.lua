-- more [file]: shows text a screenful at a time (Enter/Space for more, q to quit).
local term = require("term")
local event = require("event")
local text = require("text")
local _, h = term.getViewport()
local shown = 0
for line in io.lines((...)) do
  for _, l in ipairs(text.wrap(line, (select(1, term.getViewport())))) do
    io.write(l, "\n")
    shown = shown + 1
    if shown >= h - 1 and term.isAvailable() then
      io.write("\27[7m-- more --\27[0m")
      local _, _, ch = event.pull("key_down")
      term.clearLine()
      if ch == ("q"):byte() then return end
      shown = ch == 13 and h - 2 or 0
    end
  end
end
