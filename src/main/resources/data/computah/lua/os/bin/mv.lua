-- mv <from...> <to>: moves or renames files.
local filesystem = require("filesystem")
local args = {...}
if #args < 2 then
  io.write("usage: mv <from> [...] <to>\n")
  return 1
end
local target = filesystem.canonical(table.remove(args))
local exit = 0
for _, p in ipairs(args) do
  local from = filesystem.canonical(p)
  local to = filesystem.isDirectory(target) and filesystem.concat(target, filesystem.name(from)) or target
  local ok, reason = filesystem.rename(from, to)
  if not ok then
    io.stderr:write("mv: " .. p .. ": " .. tostring(reason or "failed") .. "\n")
    exit = 1
  end
end
return exit
