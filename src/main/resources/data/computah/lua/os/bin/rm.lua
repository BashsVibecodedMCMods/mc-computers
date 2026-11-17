-- rm [-r] [-f] <paths...>: removes files (directories need -r).
local shell = require("shell")
local filesystem = require("filesystem")
local args, options = shell.parse(...)
if #args == 0 then
  io.write("usage: rm [-r] [-f] <path> [...]\n")
  return 1
end
local exit = 0
for _, p in ipairs(args) do
  local path = filesystem.canonical(p)
  if not filesystem.exists(path) then
    if not options.f then io.stderr:write("rm: " .. p .. ": no such file or directory\n") exit = 1 end
  elseif filesystem.isDirectory(path) and not options.r then
    io.stderr:write("rm: " .. p .. ": is a directory (use -r)\n")
    exit = 1
  else
    local ok, reason = filesystem.remove(path)
    if not ok then io.stderr:write("rm: " .. p .. ": " .. tostring(reason) .. "\n") exit = 1 end
  end
end
return exit
