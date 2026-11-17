-- cp [-r] <from...> <to>: copies files (and directories with -r).
local shell = require("shell")
local filesystem = require("filesystem")
local args, options = shell.parse(...)
if #args < 2 then
  io.write("usage: cp [-r] <from> [...] <to>\n")
  return 1
end
local target = filesystem.canonical(table.remove(args))

local function copy(from, to)
  if filesystem.isDirectory(from) then
    if not options.r then return nil, "is a directory (use -r)" end
    filesystem.makeDirectory(to)
    for name in filesystem.list(from) do
      local ok, reason = copy(filesystem.concat(from, name), filesystem.concat(to, name))
      if not ok then return nil, reason end
    end
    return true
  end
  return filesystem.copy(from, to)
end

local exit = 0
for _, p in ipairs(args) do
  local from = filesystem.canonical(p)
  local to = target
  if filesystem.isDirectory(target) then to = filesystem.concat(target, filesystem.name(from)) end
  local ok, reason = copy(from, to)
  if not ok then
    io.stderr:write("cp: " .. p .. ": " .. tostring(reason) .. "\n")
    exit = 1
  end
end
return exit
