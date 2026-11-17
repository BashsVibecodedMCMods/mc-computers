-- The virtual file tree: every filesystem component is mounted somewhere under "/".
-- Paths may be absolute or relative to the shell's working directory.
local component = component
local filesystem = {}
local mounts = {} -- {path = "/mnt/abc", proxy = <filesystem proxy>}, longest path first
local links = {}  -- symbolic links: canonical link path -> target path (virtual, like OpenOS: gone after a reboot)

local function segments(path)
  local parts = {}
  for part in path:gmatch("[^/\\]+") do
    if part == ".." then
      parts[#parts] = nil
    elseif part ~= "." then
      parts[#parts + 1] = part
    end
  end
  return parts
end

function filesystem.segments(path)
  checkArg(1, path, "string")
  return segments(path)
end

--- Makes a path absolute (relative to the working directory) and removes "." and "..".
function filesystem.canonical(path)
  checkArg(1, path, "string")
  if path:sub(1, 1) ~= "/" then
    local shell = package.loaded.shell
    path = (shell and shell.getWorkingDirectory() or "/") .. "/" .. path
  end
  return "/" .. table.concat(segments(path), "/")
end

function filesystem.concat(...)
  local parts = table.pack(...)
  for i = 1, parts.n do checkArg(i, parts[i], "string") end
  local joined = table.concat(parts, "/", 1, parts.n)
  local absolute = joined:sub(1, 1) == "/"
  return (absolute and "/" or "") .. table.concat(segments(joined), "/")
end

--- The directory part of a path ("/a/b/c" -> "/a/b/").
function filesystem.path(path)
  local parts = segments(path)
  local result = table.concat(parts, "/", 1, #parts - 1) .. "/"
  return (path:sub(1, 1) == "/" and "/" or "") .. (result == "/" and "" or result)
end

--- The last part of a path ("/a/b/c" -> "c").
function filesystem.name(path)
  local parts = segments(path)
  return parts[#parts]
end

local function sortMounts()
  table.sort(mounts, function(a, b) return #a.path > #b.path end)
end

--- Mounts a filesystem (proxy or address) at a path.
function filesystem.mount(fs, path)
  checkArg(2, path, "string")
  if type(fs) == "string" then
    local proxy, reason = component.proxy(fs)
    if not proxy then return nil, reason end
    fs = proxy
  end
  if type(fs) ~= "table" or fs.type ~= "filesystem" then return nil, "not a filesystem" end
  path = filesystem.canonical(path)
  for _, m in ipairs(mounts) do
    if m.path == path then return nil, "another filesystem is already mounted here" end
  end
  mounts[#mounts + 1] = {path = path, proxy = fs}
  sortMounts()
  return true
end

--- Unmounts by path or by filesystem address (or a unique address prefix).
function filesystem.umount(target)
  checkArg(1, target, "string", "table")
  local removed = false
  for i = #mounts, 1, -1 do
    local m = mounts[i]
    if (type(target) == "table" and m.proxy == target) or (type(target) == "string" and
        (m.path == filesystem.canonical(target) or m.proxy.address:sub(1, #target) == target)) then
      table.remove(mounts, i)
      removed = true
    end
  end
  return removed
end

--- Iterates over mounts: proxy, path.
function filesystem.mounts()
  local i = 0
  return function()
    i = i + 1
    if mounts[i] then return mounts[i].proxy, mounts[i].path end
  end
end

--- Follows symbolic links in a canonical path.
local function resolveLinks(path)
  for _ = 1, 16 do
    local changed = false
    for lp, target in pairs(links) do
      if path == lp or path:sub(1, #lp + 1) == lp .. "/" then
        path = filesystem.canonical(target .. path:sub(#lp + 1))
        changed = true
        break
      end
    end
    if not changed then return path end
  end
  return path
end

--- The path with every symbolic link followed.
function filesystem.realPath(path)
  checkArg(1, path, "string")
  return resolveLinks(filesystem.canonical(path))
end

--- Creates a symbolic link at linkpath pointing to target. Links are virtual (not stored on the drive).
function filesystem.link(target, linkpath)
  checkArg(1, target, "string")
  checkArg(2, linkpath, "string")
  linkpath = filesystem.canonical(linkpath)
  if links[linkpath] or filesystem.exists(linkpath) then return nil, "file already exists" end
  links[linkpath] = target:sub(1, 1) == "/" and filesystem.canonical(target) or filesystem.concat(filesystem.path(linkpath), target)
  return true
end

--- Whether path is a symbolic link, and its target.
function filesystem.isLink(path)
  checkArg(1, path, "string")
  local target = links[filesystem.canonical(path)]
  return target ~= nil, target
end

--- The proxy of a mounted filesystem by address prefix or label, or nil.
function filesystem.proxy(filter)
  checkArg(1, filter, "string")
  for _, m in ipairs(mounts) do
    if m.proxy.address:sub(1, #filter) == filter or m.proxy.getLabel() == filter then return m.proxy end
  end
  local address = component.get(filter, "filesystem")
  if address then return component.proxy(address) end
  for a in component.list("filesystem", true) do
    if component.invoke(a, "getLabel") == filter then return component.proxy(a) end
  end
  return nil, "no such filesystem"
end

--- The filesystem proxy holding a path, and the path relative to it.
function filesystem.get(path)
  path = resolveLinks(filesystem.canonical(path))
  for _, m in ipairs(mounts) do
    if path == m.path or m.path == "/" or path:sub(1, #m.path + 1) == m.path .. "/" then
      local rel = m.path == "/" and path:sub(2) or path:sub(#m.path + 2)
      return m.proxy, rel, m.path
    end
  end
  return nil, "no filesystem mounted"
end

local function mountPointsIn(path)
  local result = {}
  path = resolveLinks(filesystem.canonical(path))
  local prefix = path == "/" and "/" or path .. "/"
  for _, m in ipairs(mounts) do
    if m.path ~= path and m.path:sub(1, #prefix) == prefix then
      local rest = m.path:sub(#prefix + 1)
      local first = rest:match("^[^/]+")
      if first then result[first] = true end
    end
  end
  return result
end

local function isMountPoint(path)
  path = resolveLinks(filesystem.canonical(path))
  for _, m in ipairs(mounts) do
    if m.path == path then return true end
  end
  return next(mountPointsIn(path)) ~= nil
end

function filesystem.exists(path)
  if isMountPoint(path) then return true end
  local fs, rel = filesystem.get(path)
  return fs ~= nil and fs.exists(rel)
end

function filesystem.isDirectory(path)
  if isMountPoint(path) then return true end
  local fs, rel = filesystem.get(path)
  return fs ~= nil and fs.isDirectory(rel)
end

function filesystem.size(path)
  local fs, rel = filesystem.get(path)
  return fs and fs.size(rel) or 0
end

function filesystem.lastModified(path)
  local fs, rel = filesystem.get(path)
  return fs and fs.lastModified(rel) or 0
end

function filesystem.isReadOnly(path)
  local fs = filesystem.get(path)
  return fs == nil or fs.isReadOnly()
end

--- Iterates over a directory's entries (directories end with "/"), including mount points.
function filesystem.list(path)
  local fs, rel = filesystem.get(path)
  local seen, result = {}, {}
  if fs and fs.isDirectory(rel) then
    for _, name in ipairs(fs.list(rel) or {}) do
      seen[name:gsub("/$", "")] = true
      result[#result + 1] = name
    end
  elseif not isMountPoint(path) then
    return nil, "no such directory"
  end
  for name in pairs(mountPointsIn(path)) do
    if not seen[name] then result[#result + 1] = name .. "/" end
  end
  local dir = filesystem.canonical(path)
  for lp, target in pairs(links) do
    local name = filesystem.name(lp)
    if name and filesystem.path(lp) == (dir == "/" and "/" or dir .. "/") and not seen[name] then
      result[#result + 1] = name .. (filesystem.isDirectory(target) and "/" or "")
    end
  end
  table.sort(result)
  local i = 0
  return function()
    i = i + 1
    return result[i]
  end
end

function filesystem.makeDirectory(path)
  local fs, rel = filesystem.get(path)
  if not fs then return nil, rel end
  return fs.makeDirectory(rel)
end

function filesystem.remove(path)
  local canonical = filesystem.canonical(path)
  if links[canonical] then
    links[canonical] = nil
    return true
  end
  local fs, rel = filesystem.get(path)
  if not fs then return nil, rel end
  return fs.remove(rel)
end

function filesystem.copy(from, to)
  local input, reason = io.open(from, "rb")
  if not input then return nil, reason end
  local output, reason2 = io.open(to, "wb")
  if not output then
    input:close()
    return nil, reason2
  end
  repeat
    local data = input:read(2048)
    if data then output:write(data) end
  until not data
  input:close()
  output:close()
  return true
end

function filesystem.rename(from, to)
  local fa, ra = filesystem.get(from)
  local fb, rb = filesystem.get(to)
  if not fa or not fb then return nil, "no filesystem mounted" end
  if fa == fb then return fa.rename(ra, rb) end
  if filesystem.isDirectory(from) then return nil, "cannot move directories between filesystems" end
  local ok, reason = filesystem.copy(from, to)
  if not ok then return nil, reason end
  return filesystem.remove(from)
end

--- Opens a file on its filesystem. Returns a raw handle table (use io.open for buffered streams).
function filesystem.open(path, mode)
  mode = mode or "r"
  local fs, rel = filesystem.get(path)
  if not fs then return nil, rel end
  local handle, reason = fs.open(rel, mode)
  if not handle then return nil, reason end
  return {fs = fs, handle = handle,
    read = function(self, n) return self.fs.read(self.handle, n) end,
    write = function(self, data) return self.fs.write(self.handle, data) end,
    seek = function(self, whence, offset) return self.fs.seek(self.handle, whence, offset) end,
    close = function(self) return self.fs.close(self.handle) end}
end

--- Reads a whole file. Returns its contents or nil, reason.
function filesystem.readAll(path)
  local f, reason = filesystem.open(path, "rb")
  if not f then return nil, reason end
  local parts = {}
  repeat
    local chunk = f:read(math.huge)
    parts[#parts + 1] = chunk
  until not chunk
  f:close()
  return table.concat(parts)
end

--- Writes a whole file.
function filesystem.writeAll(path, data)
  local f, reason = filesystem.open(path, "wb")
  if not f then return nil, reason end
  local ok, err = f:write(data)
  f:close()
  return ok, err
end

local function fsConfig()
  local data = filesystem.readAll("/etc/filesystem.cfg")
  local cfg = data and require("serialization").unserialize(data)
  return type(cfg) == "table" and cfg or {}
end

--- Whether /autorun.lua on newly inserted drives runs automatically (off by default).
function filesystem.isAutorunEnabled()
  return fsConfig().autorun == true
end

function filesystem.setAutorunEnabled(enabled)
  checkArg(1, enabled, "boolean")
  local cfg = fsConfig()
  cfg.autorun = enabled
  return filesystem.writeAll("/etc/filesystem.cfg", require("serialization").serialize(cfg, true) .. "\n")
end

--- Mounts every other filesystem at /mnt/<first 3 address characters>, and keeps doing so as drives come and go.
function filesystem.mountAll()
  local function mountOne(address)
    for proxy in filesystem.mounts() do
      if proxy.address == address then return end
    end
    filesystem.mount(address, "/mnt/" .. address:sub(1, 3))
  end
  for address in component.list("filesystem", true) do mountOne(address) end
  local event = require("event")
  event.listen("component_added", function(_, address, ctype)
    if ctype ~= "filesystem" then return end
    mountOne(address)
    local path = "/mnt/" .. address:sub(1, 3) .. "/autorun.lua"
    if filesystem.isAutorunEnabled() and filesystem.exists(path) then
      -- Run it as its own process; listeners must not block.
      require("process").spawn(function()
        local fn, reason = package.kernel.loadfile(path, setmetatable({}, {__index = _G}))
        if not fn then error(reason, 0) end
        return fn(path)
      end, "autorun", {detached = true})
    end
  end)
  event.listen("component_removed", function(_, address, ctype)
    if ctype == "filesystem" then filesystem.umount(address) end
  end)
end

return filesystem
