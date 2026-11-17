-- Threads: functions that run alongside the current program. A thread is attached to the process that created it
-- and is killed when that process ends, unless it is detached (then it keeps running in the background).
local process = require("process")
local computer = computer
local thread = {}

--- Starts fn(...) in a new thread and returns the thread object (t:status(), t:join([timeout]), t:kill(),
--- t:suspend(), t:resume(), t:detach(), t:attach()).
function thread.create(fn, ...)
  checkArg(1, fn, "function")
  local args = table.pack(...)
  local t = process.spawn(function() return fn(table.unpack(args, 1, args.n)) end, "thread")
  t.status_ = nil
  return setmetatable({}, {
    __index = function(_, key)
      if key == "status" then return function() return t:getStatus() end end
      if key == "id" then return t.id end
      if key == "raw" then return t end
      local m = t[key]
      if type(m) == "function" then return function(_, ...) return m(t, ...) end end
      return m
    end,
  })
end

--- The running thread (nil outside threads).
function thread.current()
  return process.current()
end

--- Waits until all threads end. Returns true, or false if the timeout passed first.
function thread.waitForAll(threads, timeout)
  checkArg(1, threads, "table")
  local deadline = timeout and computer.uptime() + timeout or math.huge
  for _, t in ipairs(threads) do
    if not t:join(math.max(0, deadline - computer.uptime())) then return false end
  end
  return true
end

--- Waits until any of the threads ends. Returns true, or false if the timeout passed first.
function thread.waitForAny(threads, timeout)
  checkArg(1, threads, "table")
  local deadline = timeout and computer.uptime() + timeout or math.huge
  while computer.uptime() < deadline do
    for _, t in ipairs(threads) do
      if t:status() == "dead" then return true end
    end
    computer.pullSignal(math.min(0.05, deadline - computer.uptime()))
  end
  return false
end

return thread
