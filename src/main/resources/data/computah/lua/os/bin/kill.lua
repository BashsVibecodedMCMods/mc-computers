-- kill <id...>: stops processes (see ps).
local process = require("process")
local args = {...}
if #args == 0 then
  io.write("usage: kill <id> [...]\n")
  return 1
end
for _, a in ipairs(args) do
  local t = process.byId(tonumber(a) or -1)
  if t then t:kill() else io.stderr:write("kill: no such process: " .. a .. "\n") end
end
