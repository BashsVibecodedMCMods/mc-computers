-- uptime: how long the computer has been running.
local t = computer.uptime()
io.write(string.format("up %dd %02d:%02d:%02d\n", math.floor(t / 86400), math.floor(t / 3600) % 24, math.floor(t / 60) % 60, math.floor(t) % 60))
