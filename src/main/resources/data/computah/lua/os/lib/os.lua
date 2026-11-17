-- OS additions to the os library.
local filesystem = require("filesystem")
local process = require("process")
local computer = computer

--- Waits `seconds` (other threads keep running). Signals are not consumed: every thread sees every signal.
function os.sleep(seconds)
  checkArg(1, seconds, "number", "nil")
  local deadline = computer.uptime() + (seconds or 0)
  repeat
    computer.pullSignal(deadline - computer.uptime())
    local t = process.current()
    if t and t.interrupted then
      t.interrupted = nil
      error("interrupted", 0)
    end
  until computer.uptime() >= deadline
end

function os.getenv(name)
  local env = process.data().env
  if name == nil then
    local all = {}
    local t = env
    while t do
      for k, v in pairs(t) do if all[k] == nil then all[k] = v end end
      local mt = getmetatable(t)
      t = mt and mt.__index
    end
    return all
  end
  if name == "PWD" then return process.data().cwd end
  return env[name]
end

function os.setenv(name, value)
  checkArg(1, name, "string")
  if name == "PWD" then
    process.data().cwd = value
    return value
  end
  process.data().env[name] = value ~= nil and tostring(value) or nil
  return value
end

--- Ends the running program (the shell reports the code).
function os.exit(code)
  error({exit = code == nil and 0 or code}, 0)
end

function os.remove(path)
  return filesystem.remove(filesystem.canonical(path))
end

function os.rename(from, to)
  return filesystem.rename(filesystem.canonical(from), filesystem.canonical(to))
end

function os.tmpname()
  local path
  repeat
    path = "/tmp/" .. string.format("%08x", math.random(0, 0x7FFFFFFF))
  until not filesystem.exists(path)
  return path
end

function os.execute(command)
  if not command then return true end
  return require("shell").execute(command)
end

function os.hostname()
  local name = os.getenv("HOSTNAME") or (filesystem.readAll("/etc/hostname") or ""):match("^%s*(.-)%s*$")
  if name == nil or name == "" then name = computer.address():sub(1, 8) end
  return name
end

return os
