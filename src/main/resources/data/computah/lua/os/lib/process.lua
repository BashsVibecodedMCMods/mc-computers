-- The scheduler. Every program, shell and background job runs as a thread (a coroutine). Threads wait for
-- signals with computer.pullSignal / event.pull; the scheduler waits for the next signal on behalf of all of
-- them and hands each signal to every waiting thread. A process is a thread plus its data: environment
-- variables, working directory and standard streams, inherited from the thread that started it.
local _, _, kernel = ...
local computer = computer
local rawPull = kernel.rawPull
local rawResume, rawYield, rawStatus, rawCreate = coroutine.resume, coroutine.yield, coroutine.status, coroutine.create
local pack, unpack = table.pack, table.unpack

local process = {hooks = {}}
local threads = {}
local nextId = 1
local current = nil
local PULL = setmetatable({}, {__tostring = function() return "pull" end})
local EMPTY = pack()

-- Yields that ask the scheduler for a signal pass through user coroutines, like system yields do.
local sandboxResume = coroutine.resume
function coroutine.resume(co, ...)
  local args = pack(...)
  while true do
    local r = pack(sandboxResume(co, unpack(args, 1, args.n)))
    if r[1] and r[2] == PULL and rawStatus(co) == "suspended" then
      args = pack(rawYield(unpack(r, 2, r.n)))
    else
      return unpack(r, 1, r.n)
    end
  end
end

function coroutine.wrap(f)
  local co = coroutine.create(f)
  return function(...)
    local r = pack(coroutine.resume(co, ...))
    if r[1] then return unpack(r, 2, r.n) end
    error(r[2], 0)
  end
end

--- Waits for a signal (or until `timeout` seconds pass). Inside a thread this lets other threads run.
function computer.pullSignal(timeout)
  checkArg(1, timeout, "number", "nil")
  if not current then return rawPull(timeout) end
  return rawYield(PULL, timeout)
end

local Thread = {}
Thread.__index = Thread

local function newData(parent)
  local pd = parent and parent.data
  local env = setmetatable({}, {__index = pd and pd.env or nil})
  if not pd then
    env.PATH = "/bin:/usr/bin:/home/bin:."
    env.HOME = "/home"
    env.PS1 = "$PWD# "
  end
  return {
    env = env,
    cwd = pd and pd.cwd or "/",
    io = {stdin = pd and pd.io.stdin, stdout = pd and pd.io.stdout, stderr = pd and pd.io.stderr},
  }
end

--- Starts a thread running fn. opts: detached (survives its parent), data (process data to use).
function process.spawn(fn, name, opts)
  checkArg(1, fn, "function")
  opts = opts or {}
  local t = setmetatable({
    id = nextId,
    name = name or "thread",
    co = rawCreate(fn),
    parent = current,
    status = "ready",
    args = opts.args or EMPTY,
    detached = opts.detached or false,
    children = {},
    started = computer.uptime(),
  }, Thread)
  t.data = opts.data or newData(current)
  nextId = nextId + 1
  if current then current.children[t] = true end
  threads[#threads + 1] = t
  return t
end

local function finish(t)
  t.status = "dead"
  t.ended = computer.uptime()
  for child in pairs(t.children) do
    if not child.detached and child.status ~= "dead" then child:kill() end
  end
  for _, other in ipairs(threads) do
    if other.waitingOn == t and other.status == "waiting" then
      other.status = "ready"
      other.args = EMPTY
    end
  end
  if t.error ~= nil and process.hooks.error then process.hooks.error(t, t.error) end
end

local function step(t, args)
  current = t
  local r = pack(rawResume(t.co, unpack(args, 1, args.n)))
  current = nil
  if t.status == "dead" then return end
  if not r[1] then
    local err = r[2]
    if type(err) == "table" and err.exit ~= nil then
      t.result = pack(err.exit)
    else
      t.error = err == nil and "error" or err
      kernel.log("thread " .. t.id .. " (" .. t.name .. "): " .. tostring(t.error))
    end
    finish(t)
  elseif rawStatus(t.co) == "dead" then
    t.result = pack(unpack(r, 2, r.n))
    finish(t)
  elseif r[2] == PULL then
    t.status = t.suspendRequested and "suspended" or "waiting"
    t.deadline = computer.uptime() + (r[3] or math.huge)
  else
    -- A plain coroutine.yield at the top of a thread just gives other threads a turn.
    t.status = "waiting"
    t.deadline = computer.uptime()
  end
end

--- Runs the scheduler forever (the kernel calls this last).
function process.run()
  while true do
    local passes = 0
    repeat
      local ran = false
      local snapshot = {table.unpack(threads)}
      for _, t in ipairs(snapshot) do
        if t.status == "ready" then
          ran = true
          local args = t.args or EMPTY
          t.args = nil
          step(t, args)
        end
      end
      passes = passes + 1
    until not ran or passes >= 50
    for i = #threads, 1, -1 do
      if threads[i].status == "dead" then
        local t = threads[i]
        if t.parent then t.parent.children[t] = nil end
        table.remove(threads, i)
      end
    end
    if #threads == 0 then computer.shutdown() end

    local now = computer.uptime()
    local deadline = math.huge
    local anyReady = false
    for _, t in ipairs(threads) do
      if t.status == "waiting" and t.deadline < deadline then deadline = t.deadline end
      if t.status == "ready" then anyReady = true end
    end
    if process.hooks.deadline then
      local d = process.hooks.deadline()
      if d and d < deadline then deadline = d end
    end
    local timeout = anyReady and 0 or math.max(0, deadline - now)
    local signal = pack(rawPull(timeout ~= math.huge and timeout or nil))

    if signal.n > 0 and signal[1] ~= nil then
      if process.hooks.dispatch then process.hooks.dispatch(signal) end
      for _, t in ipairs(threads) do
        if t.status == "waiting" then
          t.status = "ready"
          t.args = signal
        end
      end
    end
    now = computer.uptime()
    for _, t in ipairs(threads) do
      if t.status == "waiting" and t.deadline <= now then
        t.status = "ready"
        t.args = EMPTY
      end
    end
    if process.hooks.timers then process.hooks.timers(now) end
  end
end

--- The running thread (nil inside event listeners).
function process.current()
  return current
end

function process.byId(id)
  for _, t in ipairs(threads) do
    if t.id == id then return t end
  end
end

--- Information about every thread: {id, name, status, parent, detached, uptime}.
function process.list()
  local out = {}
  local now = computer.uptime()
  for _, t in ipairs(threads) do
    out[#out + 1] = {id = t.id, name = t.name, status = t.status, parent = t.parent and t.parent.id,
      detached = t.detached, uptime = now - t.started}
  end
  return out
end

--- The process data of the running thread (or of the root process inside listeners).
function process.data()
  local t = current
  if t then return t.data end
  process.rootData = process.rootData or newData(nil)
  return process.rootData
end

--- OpenOS-style information about a process (default: the running one): {path, command, env, data, parent, id, status}.
function process.info(t)
  if type(t) == "number" then t = process.byId(t) end
  t = t or current
  if not t then return nil, "no such process" end
  return {
    id = t.id, path = t.data.path, command = t.name, env = t.data.env, data = t.data, status = t.status,
    parent = t.parent and t.parent.id,
  }
end

--- The path, environment and name of the running process.
function process.running()
  local t = current
  if not t then return nil end
  return t.data.path, t.data.env, t.name
end

--- Loads a program (path or function) as a coroutine, like OpenOS: resume it to run it. `env` defaults to a new
--- environment based on the globals; `init` is called first inside the coroutine.
function process.load(path, env, init, name)
  checkArg(1, path, "string", "function")
  checkArg(3, init, "function", "nil")
  local fn, reason
  if type(path) == "function" then
    fn = path
  else
    fn, reason = kernel.loadfile(path, env or setmetatable({}, {__index = _G}))
    if not fn then return nil, reason end
  end
  return coroutine.create(function(...)
    if init then init() end
    return fn(...)
  end)
end

--- Makes the thread's current or next wait fail with "interrupted" (Ctrl+C).
function process.interrupt(t)
  if t and t.status ~= "dead" then
    t.interrupted = true
    if t.status == "waiting" then
      t.status = "ready"
      t.args = EMPTY
    end
  end
end

-- Thread methods ------------------------------------------------------------

function Thread:resume()
  if self.status == "suspended" then
    self.suspendRequested = false
    self.status = "ready"
    self.args = EMPTY
    return true
  end
  self.suspendRequested = false
  return false
end

function Thread:suspend()
  if self.status == "dead" then return false end
  if self == current then
    self.suspendRequested = true
    rawYield(PULL, 0)
    return true
  end
  self.status = "suspended"
  return true
end

function Thread:kill()
  if self.status == "dead" then return false end
  finish(self)
  if self == current then
    while true do rawYield(PULL) end
  end
  return true
end

function Thread:detach()
  self.detached = true
  return self
end

function Thread:attach(parent)
  self.detached = false
  parent = parent or current
  if parent then parent.children[self] = true end
  self.parent = parent
  return self
end

--- "running" (the caller), "ready", "waiting", "suspended" or "dead".
function Thread:getStatus()
  if self == current then return "running" end
  return self.status
end

--- Waits until the thread ends (or timeout passes). Returns true, or false if it timed out.
function Thread:join(timeout)
  local deadline = timeout and computer.uptime() + timeout or math.huge
  while self.status ~= "dead" do
    if computer.uptime() >= deadline then return false end
    if current then current.waitingOn = self end
    computer.pullSignal(deadline - computer.uptime())
    if current then current.waitingOn = nil end
    if current and current.interrupted then
      current.interrupted = nil
      error("interrupted", 0)
    end
  end
  return true
end

--- The values the thread's function returned, or nil, error if it failed.
function Thread:results()
  if self.error ~= nil then return nil, self.error end
  if self.result then return table.unpack(self.result, 1, self.result.n) end
end

process.Thread = Thread
return process
