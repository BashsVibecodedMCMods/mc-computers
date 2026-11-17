-- ps: lists processes and threads.
local process = require("process")
io.write(string.format("%5s %6s %-10s %8s %s\n", "ID", "PARENT", "STATUS", "TIME", "NAME"))
for _, p in ipairs(process.list()) do
  io.write(string.format("%5d %6s %-10s %8.1f %s%s\n", p.id, p.parent or "-", p.status, p.uptime, p.name, p.detached and " (bg)" or ""))
end
