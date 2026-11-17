-- install [target]: copies Computah OS from the disk it is running on to a hard drive, and makes it the boot drive.
local filesystem = require("filesystem")
local args = {...}
local source = filesystem.get("/")
local candidates = {}
for fs, path in filesystem.mounts() do
  if fs ~= source and not fs.isReadOnly() and fs.address ~= computer.tmpAddress() then
    candidates[#candidates + 1] = {fs = fs, path = path}
  end
end
if #candidates == 0 then
  io.stderr:write("install: no writable drive found (put a hard drive in the computer)\n")
  return 1
end
local target
if args[1] then
  for _, c in ipairs(candidates) do
    if c.path == filesystem.canonical(args[1]) or c.fs.address:sub(1, #args[1]) == args[1] then target = c end
  end
elseif #candidates == 1 then
  target = candidates[1]
else
  io.write("Install to which drive?\n")
  for i, c in ipairs(candidates) do
    io.write(string.format("  %d) %s %s (%s free)\n", i, c.fs.address:sub(1, 8), c.path, require("text").size(c.fs.spaceTotal() - c.fs.spaceUsed())))
  end
  io.write("number: ")
  target = candidates[tonumber(io.read("l") or "")]
end
if not target then io.stderr:write("install: no drive selected\n") return 1 end

io.write("Installing Computah OS to " .. target.fs.address:sub(1, 8) .. "... ")
local count = 0
local function copyTree(path)
  local fs, rel = filesystem.get(path)
  if fs ~= source then return end
  for name in filesystem.list(path) do
    local from = filesystem.concat(path, name)
    if filesystem.get(from) == source then
      local to = filesystem.concat(target.path, rel ~= "" and rel or "", name)
      if name:sub(-1) == "/" then
        filesystem.makeDirectory(to)
        copyTree(from)
      elseif not (from:match("^/etc/") and filesystem.exists(to)) then
        assert(filesystem.copy(from, to))
        count = count + 1
      end
    end
  end
end
copyTree("/")
pcall(target.fs.setLabel, "Computah OS")
computer.setBootAddress(target.fs.address)
io.write("done (" .. count .. " files).\nRemove the OS disk and reboot to boot from the hard drive.\n")
