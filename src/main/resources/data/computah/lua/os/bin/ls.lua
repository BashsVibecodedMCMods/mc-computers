-- ls [-a] [-l] [paths...]: lists directory contents. Directories are blue, Lua files green.
local shell = require("shell")
local filesystem = require("filesystem")
local text = require("text")
local term = require("term")

local args, options = shell.parse(...)
if #args == 0 then args = {"."} end
local exit = 0

local function show(entries, dir)
  if options.l then
    for _, name in ipairs(entries) do
      local path = filesystem.concat(dir, name)
      local isDir = name:sub(-1) == "/"
      local size = isDir and "-" or text.size(filesystem.size(path))
      local date = os.date("%Y-%m-%d %H:%M", math.floor(filesystem.lastModified(path) / 1000))
      io.write(string.format("%s %8s %s %s\n", isDir and "d" or "f", size, date, name))
    end
    return
  end
  if not io.isTerminal(io.output()) then
    for _, name in ipairs(entries) do io.write(name, "\n") end
    return
  end
  local w = term.isAvailable() and select(1, term.getViewport()) or 80
  local width = 0
  for _, name in ipairs(entries) do width = math.max(width, unicode.len(name) + 2) end
  local perLine = math.max(1, math.floor(w / math.max(1, width)))
  for i, name in ipairs(entries) do
    local isDir = name:sub(-1) == "/"
    local code = isDir and "\27[94m" or name:match("%.lua$") and "\27[92m" or ""
    io.write(code .. text.padRight(name, (i % perLine == 0 or i == #entries) and 0 or width) .. (code ~= "" and "\27[0m" or ""))
    if i % perLine == 0 or i == #entries then io.write("\n") end
  end
end

for i, arg in ipairs(args) do
  local path = filesystem.canonical(arg)
  if not filesystem.exists(path) then
    io.stderr:write("ls: " .. arg .. ": no such file or directory\n")
    exit = 1
  elseif filesystem.isDirectory(path) then
    if #args > 1 then io.write(arg .. ":\n") end
    local entries = {}
    for name in filesystem.list(path) do
      if options.a or name:sub(1, 1) ~= "." then entries[#entries + 1] = name end
    end
    show(entries, path)
    if #args > 1 and i < #args then io.write("\n") end
  else
    show({filesystem.name(path)}, filesystem.path(path))
  end
end
return exit
