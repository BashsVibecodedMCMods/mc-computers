-- free: shows memory use (approximate).
local text = require("text")
local total, free = computer.totalMemory(), computer.freeMemory()
io.write(string.format("Total %s, used %s, free %s\n", text.size(total), text.size(total - free), text.size(free)))
