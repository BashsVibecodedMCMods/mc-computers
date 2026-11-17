-- mount [address path]: lists mounts, or mounts a filesystem (address or prefix) at a path.
local filesystem = require("filesystem")
local args = {...}
if #args == 0 then
  for fs, path in filesystem.mounts() do io.write(fs.address:sub(1, 8) .. " on " .. path .. "\n") end
  return
end
if #args < 2 then
  io.write("usage: mount [<address> <path>]\n")
  return 1
end
local address = component.get(args[1], "filesystem")
if not address then
  io.stderr:write("mount: no such filesystem\n")
  return 1
end
local ok, reason = filesystem.mount(address, args[2])
if not ok then io.stderr:write("mount: " .. tostring(reason) .. "\n") return 1 end
