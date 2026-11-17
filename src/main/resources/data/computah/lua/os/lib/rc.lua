-- Background services ("daemons"). Each is a script in /etc/rc.d/NAME.lua defining start(), and optionally stop(),
-- status() and other commands. The ones listed in /etc/rc.cfg (enabled = {...}) start at boot.
-- The `rc` program controls them: rc NAME start|stop|restart|status|enable|disable, or rc to list.
local filesystem = require("filesystem")
local serialization = require("serialization")
local rc = {loaded = {}, running = {}}

local function config()
  local data = filesystem.readAll("/etc/rc.cfg")
  local cfg = data and serialization.unserialize(data) or nil
  if type(cfg) ~= "table" then cfg = {} end
  cfg.enabled = cfg.enabled or {}
  return cfg
end

local function save(cfg)
  return filesystem.writeAll("/etc/rc.cfg", serialization.serialize(cfg, true) .. "\n")
end

function rc.load(name)
  if rc.loaded[name] then return rc.loaded[name] end
  local path = "/etc/rc.d/" .. name .. ".lua"
  local env = setmetatable({args = (config()[name])}, {__index = _G})
  local fn, reason = package.kernel.loadfile(path, env)
  if not fn then return nil, reason end
  local ok, err = pcall(fn)
  if not ok then return nil, err end
  rc.loaded[name] = env
  return env
end

--- Runs a command of a service ("start", "stop", "restart", "status" or a custom one).
function rc.run(name, command, ...)
  local env, reason = rc.load(name)
  if not env then return nil, reason end
  if command == "restart" and not env.restart then
    rc.run(name, "stop")
    return rc.run(name, "start", ...)
  end
  if command == "status" and not env.status then
    return true, rc.running[name] and "running" or "stopped"
  end
  local fn = env[command]
  if type(fn) ~= "function" then return nil, "no such command: " .. tostring(command) end
  local ok, result = pcall(fn, ...)
  if not ok then return nil, result end
  if command == "start" then rc.running[name] = true end
  if command == "stop" then
    rc.running[name] = nil
    rc.loaded[name] = nil
  end
  return true, result
end

function rc.list()
  local names = {}
  local list = filesystem.list("/etc/rc.d")
  if list then
    for f in list do
      local n = f:match("^(.+)%.lua$")
      if n then names[#names + 1] = n end
    end
  end
  return names
end

function rc.isEnabled(name)
  for _, n in ipairs(config().enabled) do
    if n == name then return true end
  end
  return false
end

function rc.enable(name)
  local cfg = config()
  for _, n in ipairs(cfg.enabled) do
    if n == name then return true end
  end
  cfg.enabled[#cfg.enabled + 1] = name
  return save(cfg)
end

function rc.disable(name)
  local cfg = config()
  for i, n in ipairs(cfg.enabled) do
    if n == name then table.remove(cfg.enabled, i) end
  end
  return save(cfg)
end

--- Starts every enabled service (at boot).
function rc.startEnabled()
  for _, name in ipairs(config().enabled) do
    local ok, reason = rc.run(name, "start")
    if not ok then package.kernel.log("rc: " .. name .. ": " .. tostring(reason)) end
  end
end

return rc
