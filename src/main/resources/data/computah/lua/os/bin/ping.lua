-- ping [hostname]: finds computers on the network (those running the beacon service) and shows their distance.
local net = require("net")
if not net.modem() then io.stderr:write("ping: no network or wireless card\n") return 1 end
local name = ...
local hosts = net.discover(name, 2)
if #hosts == 0 then
  io.write("no answer\n")
  return 1
end
for _, h in ipairs(hosts) do
  io.write(string.format("%-16s %s  distance %.1f\n", tostring(h.hostname), h.address, h.distance or 0))
end
