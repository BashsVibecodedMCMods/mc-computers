-- Signals and timers. event.pull waits for a signal in the current thread; event.listen registers a callback that
-- the scheduler calls for every matching signal, whatever thread is running (callbacks must not block).
local _, _, kernel = ...
local process = require("process")
local computer = computer
local pack, unpack = table.pack, table.unpack

local event = {handlers = {}}
local timers = {}
local nextTimer = 1

--- Called with a message when an event listener or timer fails. Logs to dmesg; replace it to handle errors yourself.
function event.onError(message)
  kernel.log(tostring(message))
end

local function report(what, err)
  local ok = pcall(event.onError, what .. ": " .. tostring(err))
  if not ok then kernel.log(what .. ": " .. tostring(err)) end
end

--- Calls callback(name, ...) for every signal with this name. Returning false from the callback removes it.
function event.listen(name, callback)
  checkArg(1, name, "string")
  checkArg(2, callback, "function")
  local list = event.handlers[name]
  if not list then
    list = {}
    event.handlers[name] = list
  end
  for _, cb in ipairs(list) do
    if cb == callback then return false end
  end
  list[#list + 1] = callback
  return true
end

--- Removes a callback added with event.listen.
function event.ignore(name, callback)
  checkArg(1, name, "string")
  checkArg(2, callback, "function")
  local list = event.handlers[name]
  if not list then return false end
  for i, cb in ipairs(list) do
    if cb == callback then
      table.remove(list, i)
      return true
    end
  end
  return false
end

--- Calls callback after `interval` seconds, `times` times (default 1; math.huge = forever). Returns a timer id.
function event.timer(interval, callback, times)
  checkArg(1, interval, "number")
  checkArg(2, callback, "function")
  checkArg(3, times, "number", "nil")
  local id = nextTimer
  nextTimer = nextTimer + 1
  timers[id] = {interval = math.max(0, interval), callback = callback, times = times or 1, after = computer.uptime() + interval}
  return id
end

function event.cancel(id)
  checkArg(1, id, "number")
  if timers[id] then
    timers[id] = nil
    return true
  end
  return false
end

process.hooks.dispatch = function(signal)
  local list = event.handlers[signal[1]]
  if not list then return end
  for _, cb in ipairs({table.unpack(list)}) do
    local ok, result = xpcall(cb, debug.traceback, unpack(signal, 1, signal.n))
    if not ok then
      report("listener for " .. tostring(signal[1]), result)
    elseif result == false then
      event.ignore(signal[1], cb)
    end
  end
end

process.hooks.deadline = function()
  local d
  for _, t in pairs(timers) do
    if not d or t.after < d then d = t.after end
  end
  return d
end

process.hooks.timers = function(now)
  for id, t in pairs(timers) do
    if t.after <= now then
      t.times = t.times - 1
      if t.times <= 0 then timers[id] = nil else t.after = now + t.interval end
      local ok, err = xpcall(t.callback, debug.traceback)
      if not ok then report("timer " .. id, err) end
    end
  end
end

local function checkInterrupt()
  local t = process.current()
  if t and t.interrupted then
    t.interrupted = nil
    error("interrupted", 0)
  end
end

local function matches(signal, name, filters)
  if signal.n == 0 or signal[1] == nil then return false end
  if name then
    local ok, m = pcall(string.match, signal[1], "^" .. name .. "$")
    if not (ok and m) and signal[1] ~= name then return false end
  end
  for i = 1, filters.n do
    if filters[i] ~= nil and filters[i] ~= signal[i + 1] then return false end
  end
  return true
end

--- event.pull([timeout], [name], ...): waits for a signal whose name matches the pattern `name` and whose values
--- equal the given filters (nil matches anything). Returns the signal, or nil after the timeout.
function event.pull(...)
  local args = pack(...)
  local timeout, name, first
  if type(args[1]) == "number" or (args[1] == nil and args.n > 1) then
    timeout, name, first = args[1], args[2], 3
  else
    name, first = args[1], 2
  end
  checkArg(1, timeout, "number", "nil")
  local filters = pack(unpack(args, first, args.n))
  local deadline = timeout and computer.uptime() + timeout or math.huge
  repeat
    local signal = pack(computer.pullSignal(deadline - computer.uptime()))
    checkInterrupt()
    if matches(signal, name, filters) then return unpack(signal, 1, signal.n) end
  until computer.uptime() >= deadline
  return nil
end

--- Waits for a signal accepted by filter(name, ...).
function event.pullFiltered(timeout, filter)
  if type(timeout) == "function" then timeout, filter = nil, timeout end
  checkArg(1, timeout, "number", "nil")
  checkArg(2, filter, "function")
  local deadline = timeout and computer.uptime() + timeout or math.huge
  repeat
    local signal = pack(computer.pullSignal(deadline - computer.uptime()))
    checkInterrupt()
    if signal.n > 0 and signal[1] ~= nil and filter(unpack(signal, 1, signal.n)) then return unpack(signal, 1, signal.n) end
  until computer.uptime() >= deadline
  return nil
end

--- Waits for a signal with any of the given names.
function event.pullMultiple(...)
  local names = pack(...)
  local timeout
  if type(names[1]) == "number" then
    timeout = names[1]
    names = pack(unpack(names, 2, names.n))
  end
  return event.pullFiltered(timeout, function(name)
    for i = 1, names.n do
      if name == names[i] then return true end
    end
    return false
  end)
end

event.push = computer.pushSignal

return event
