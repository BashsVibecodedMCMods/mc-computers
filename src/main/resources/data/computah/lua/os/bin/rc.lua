-- rc [name command]: controls background services in /etc/rc.d.
--   rc                       list services
--   rc <name> start|stop|restart|status
--   rc <name> enable|disable start (or not) at boot
local rc = require("rc")
local name, command = ...
if not name then
  for _, n in ipairs(rc.list()) do
    io.write(string.format("%-16s %-8s %s\n", n, rc.running[n] and "running" or "stopped", rc.isEnabled(n) and "enabled" or ""))
  end
  return
end
if command == "enable" then
  rc.enable(name)
  io.write(name .. " will start at boot\n")
  return
elseif command == "disable" then
  rc.disable(name)
  io.write(name .. " won't start at boot\n")
  return
end
local ok, result = rc.run(name, command or "status", select(3, ...))
if not ok then io.stderr:write("rc: " .. tostring(result) .. "\n") return 1 end
if result ~= nil then io.write(tostring(result) .. "\n") end
