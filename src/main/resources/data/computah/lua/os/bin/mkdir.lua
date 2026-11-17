-- mkdir <paths...>: creates directories (and missing parents).
local filesystem = require("filesystem")
local args = {...}
if #args == 0 then
  io.write("usage: mkdir <path> [...]\n")
  return 1
end
for _, p in ipairs(args) do
  local ok, reason = filesystem.makeDirectory(filesystem.canonical(p))
  if not ok then io.stderr:write("mkdir: " .. p .. ": " .. tostring(reason or "already exists") .. "\n") end
end
