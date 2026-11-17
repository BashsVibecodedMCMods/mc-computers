-- Computah OS kernel. The BIOS loads and runs this file from the boot filesystem.
-- It sets up require, mounts filesystems, starts the scheduler (threads and processes), runs the
-- enabled rc daemons and then the shell. See docs/OS.md.
local component, computer, unicode = component, computer, unicode
local invoke = component.invoke
local bootAddress = computer.getBootAddress()

_G._OSVERSION = "Computah OS 1.0"

-- Messages from boot and from crashed threads, shown by `dmesg`.
local kernel = {bootAddress = bootAddress, messages = {}, rawPull = computer.pullSignal}

function kernel.log(message)
  local list = kernel.messages
  list[#list + 1] = string.format("[%8.2f] %s", computer.uptime(), tostring(message))
  if #list > 200 then table.remove(list, 1) end
end

-- Before the filesystem library is ready, files are read straight from the boot drive.
local function rawRead(path)
  local handle, reason = invoke(bootAddress, "open", path)
  if not handle then return nil, reason end
  local parts = {}
  repeat
    local chunk = invoke(bootAddress, "read", handle, math.huge)
    parts[#parts + 1] = chunk
  until not chunk
  invoke(bootAddress, "close", handle)
  return table.concat(parts)
end

function kernel.loadfile(path, env)
  local code, reason = rawRead(path)
  if not code then return nil, reason end
  return load(code, "=" .. path, "t", env or _G)
end

local function boot(path, ...)
  local fn, reason = kernel.loadfile(path)
  if not fn then error("failed loading " .. path .. ": " .. tostring(reason), 0) end
  return fn(...)
end

kernel.log("booting " .. _OSVERSION .. " from " .. bootAddress:sub(1, 8))
boot("/lib/package.lua", kernel)

local filesystem = require("filesystem")
filesystem.mount(bootAddress, "/")
local tmp = computer.tmpAddress()
if tmp then filesystem.mount(tmp, "/tmp") end
-- From here on files are loaded through the filesystem library (so mounted drives work).
function kernel.loadfile(path, env)
  local code, reason = filesystem.readAll(path)
  if not code then return nil, reason end
  return load(code, "=" .. path, "t", env or _G)
end

local process = require("process")
require("event")
require("component")
require("term")
require("io")
require("os")

process.spawn(function()
  local shell = require("shell")
  filesystem.mountAll()
  require("rc").startEnabled()
  local motd = filesystem.readAll("/etc/motd")
  if motd then io.write(motd) end
  while true do
    local ok, reason = shell.run("sh")
    if not ok then
      io.stderr:write("shell exited: " .. tostring(reason) .. "\n")
      os.sleep(1)
    end
  end
end, "init")

process.run()
