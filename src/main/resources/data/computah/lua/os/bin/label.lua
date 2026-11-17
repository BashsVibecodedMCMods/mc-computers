-- label [-a] <path or address> [new label]: shows or sets a drive's label (-a: by component address).
local shell = require("shell")
local filesystem = require("filesystem")
local args, options = shell.parse(...)
if #args == 0 then io.write("usage: label [-a] <path|address> [label]\n") return 1 end
local proxy
if options.a then
  local address = component.get(args[1], "filesystem")
  proxy = address and component.proxy(address)
else
  proxy = filesystem.get(args[1])
end
if not proxy then io.stderr:write("label: no such filesystem\n") return 1 end
if args[2] then
  local ok, reason = pcall(proxy.setLabel, table.concat(args, " ", 2))
  if not ok then io.stderr:write("label: " .. tostring(reason) .. "\n") return 1 end
else
  io.write((proxy.getLabel() or "(no label)") .. "\n")
end
