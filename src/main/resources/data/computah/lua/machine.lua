-- Computah machine bootstrap. This runs before anything else on every computer: it builds the sandbox (the global
-- environment that the EEPROM and everything it loads will see), then loads the EEPROM's code and runs it.
-- Documentation: docs/LUA.md and docs/EEPROM.md.
local natives, SYS, SYNC = ...

local rawYield, rawResume, rawStatus = coroutine.yield, coroutine.resume, coroutine.status
local rawCreate, rawRunning = coroutine.create, coroutine.running
local pack, unpack = table.pack, table.unpack
local rawLoad, rawError, rawType, rawPairs, rawSelect = load, error, type, pairs, select
local traceback, getinfo = debug and debug.traceback, debug and debug.getinfo

-- A system yield goes straight to the Java driver, passing through any user coroutines on the way.
local function sysyield(...)
  return rawYield(SYS, ...)
end

local function checkArg(n, have, ...)
  have = rawType(have)
  local function check(want, ...)
    if not want then
      return false
    end
    return have == want or check(...)
  end
  if not check(...) then
    local msg = string.format("bad argument #%d (%s expected, got %s)", n, table.concat({...}, " or "), have)
    rawError(msg, 3)
  end
end

local function copy(t)
  local r = {}
  for k, v in rawPairs(t) do r[k] = v end
  return r
end

-------------------------------------------------------------------------------
-- coroutine: user coroutines forward system yields (pullSignal, indirect calls) to the driver transparently.

local co = {}

function co.create(f)
  checkArg(1, f, "function")
  return rawCreate(f)
end

function co.resume(c, ...)
  checkArg(1, c, "thread")
  local args = pack(...)
  while true do
    local r = pack(rawResume(c, unpack(args, 1, args.n)))
    if r[1] and r[2] == SYS then
      args = pack(rawYield(unpack(r, 2, r.n)))
    else
      return unpack(r, 1, r.n)
    end
  end
end

function co.yield(...)
  return rawYield(...)
end

co.status = rawStatus
co.running = rawRunning
co.isyieldable = coroutine.isyieldable

function co.wrap(f)
  local c = co.create(f)
  return function(...)
    local r = pack(co.resume(c, ...))
    if r[1] then
      return unpack(r, 2, r.n)
    end
    rawError(r[2], 0)
  end
end

-------------------------------------------------------------------------------
-- component

local component = {}

local function invoke(address, method, ...)
  checkArg(1, address, "string")
  checkArg(2, method, "string")
  local r = pack(natives.invoke(address, method, ...))
  while r[1] == SYNC do
    r = pack(sysyield("sync"))
  end
  if r[1] then
    return unpack(r, 2, r.n)
  end
  rawError(r[2], 0)
end
component.invoke = invoke

function component.list(filter, exact)
  checkArg(1, filter, "string", "nil")
  local all, result = natives.list(), {}
  for address, ctype in rawPairs(all) do
    if not filter or (exact and ctype == filter) or (not exact and string.find(ctype, filter, 1, true)) then
      result[address] = ctype
    end
  end
  local key = nil
  return setmetatable(result, {
    __call = function()
      key = next(result, key)
      if key then
        return key, result[key]
      end
    end
  })
end

function component.type(address)
  checkArg(1, address, "string")
  return natives.type(address)
end

function component.slot(address)
  checkArg(1, address, "string")
  return natives.slot(address)
end

function component.methods(address)
  checkArg(1, address, "string")
  local m, err = natives.methods(address)
  if not m then return nil, err end
  local r = {}
  for name, info in rawPairs(m) do r[name] = info.direct end
  return r
end

function component.doc(address, method)
  checkArg(1, address, "string")
  checkArg(2, method, "string")
  return natives.doc(address, method)
end

--- Components have no fields, only methods (kept for OpenComputers programs).
function component.fields(address)
  checkArg(1, address, "string")
  if not natives.type(address) then return nil, "no such component" end
  return {}
end

--- Finds the full address of a component from an abbreviated one (a prefix), optionally of a given type.
function component.get(abbreviated, ctype)
  checkArg(1, abbreviated, "string")
  checkArg(2, ctype, "string", "nil")
  for address, t in rawPairs(natives.list()) do
    if address:sub(1, #abbreviated) == abbreviated and (ctype == nil or t == ctype) then
      return address
    end
  end
  return nil, "no such component"
end

local proxyMeta = {
  __tostring = function(self) return self.type .. ":" .. self.address end
}

function component.proxy(address)
  checkArg(1, address, "string")
  local ctype, err = natives.type(address)
  if not ctype then return nil, err end
  local p = setmetatable({address = address, type = ctype, slot = natives.slot(address)}, proxyMeta)
  for name in rawPairs(natives.methods(address)) do
    p[name] = function(...) return invoke(address, name, ...) end
  end
  return p
end

-------------------------------------------------------------------------------
-- computer

local computer = {
  address = natives.address,
  tmpAddress = natives.tmpAddress,
  uptime = natives.uptime,
  energy = natives.energy,
  maxEnergy = natives.maxEnergy,
  freeMemory = natives.freeMemory,
  totalMemory = natives.totalMemory,
  pushSignal = natives.pushSignal,
  beep = natives.beep,
}

computer.getDeviceInfo = natives.deviceInfo

function computer.getArchitecture()
  return "Lua 5.2"
end

function computer.getArchitectures()
  return {"Lua 5.2"}
end

--- Only "Lua 5.2" exists. Returns false (no change needed), or nil and an error for anything else.
function computer.setArchitecture(architecture)
  checkArg(1, architecture, "string")
  if architecture ~= "Lua 5.2" then return nil, "unknown architecture" end
  return false
end

function computer.isRobot()
  return false
end

--- Computers have no user list (anyone who can reach a computer can use it).
function computer.users()
end

function computer.addUser(name)
  checkArg(1, name, "string")
  return nil, "user lists are not supported"
end

function computer.removeUser(name)
  checkArg(1, name, "string")
  return false
end

--- Waits for the next signal, or until `timeout` seconds pass. Returns the signal name and its values, or nothing.
function computer.pullSignal(timeout)
  checkArg(1, timeout, "number", "nil")
  return sysyield("pull", timeout)
end

function computer.shutdown(reboot)
  sysyield("shutdown", not not reboot)
  -- The driver never resumes us after a shutdown.
  while true do sysyield("pull") end
end

local function firstEeprom()
  return (component.list("eeprom", true)())
end

--- The boot address lives in the EEPROM's data area, as in OpenComputers.
function computer.getBootAddress()
  local eeprom = firstEeprom()
  if eeprom then
    local data = invoke(eeprom, "getData")
    if data and #data > 0 then return data end
  end
  return nil
end

function computer.setBootAddress(address)
  checkArg(1, address, "string", "nil")
  local eeprom = firstEeprom()
  if eeprom then
    return invoke(eeprom, "setData", address or "")
  end
end

-------------------------------------------------------------------------------
-- unicode

local unicode = {
  char = natives.uchar,
  len = natives.ulen,
  sub = natives.usub,
  upper = natives.uupper,
  lower = natives.ulower,
  reverse = natives.ureverse,
  codepoints = natives.ucodepoints,
  wlen = natives.ulen,
}
function unicode.charWidth() return 1 end
function unicode.isWide() return false end
function unicode.wtrunc(s, n) return natives.usub(s, 1, n - 1) end

-------------------------------------------------------------------------------
-- os

local floor = math.floor
local function daysFromCivil(y, m, d)
  y = m <= 2 and y - 1 or y
  local era = floor((y >= 0 and y or y - 399) / 400)
  local yoe = y - era * 400
  local doy = floor((153 * (m + (m > 2 and -3 or 9)) + 2) / 5) + d - 1
  local doe = yoe * 365 + floor(yoe / 4) - floor(yoe / 100) + doy
  return era * 146097 + doe - 719468
end

local os = {
  clock = natives.clock,
  date = natives.date,
}

--- Seconds since the Unix epoch (real time, UTC). With a table, converts that UTC date to seconds.
function os.time(t)
  checkArg(1, t, "table", "nil")
  if not t then return math.floor(natives.realTime()) end
  local days = daysFromCivil(t.year, t.month, t.day)
  return days * 86400 + (t.hour or 12) * 3600 + (t.min or 0) * 60 + (t.sec or 0)
end

function os.difftime(a, b) return a - (b or 0) end

-------------------------------------------------------------------------------
-- The sandbox.

local sandbox
sandbox = {
  _VERSION = _VERSION,
  assert = assert, error = rawError, getmetatable = getmetatable, ipairs = ipairs, next = next, pairs = rawPairs,
  pcall = pcall, rawequal = rawequal, rawget = rawget, rawlen = rawlen, rawset = rawset, select = rawSelect,
  setmetatable = setmetatable, tonumber = tonumber, tostring = tostring, type = rawType, xpcall = xpcall,
  load = function(ld, source, mode, env)
    -- Only text chunks: precompiled bytecode is never accepted.
    return rawLoad(ld, source, "t", env or sandbox)
  end,
  bit32 = bit32 and copy(bit32),
  coroutine = co,
  math = copy(math),
  string = copy(string),
  table = copy(table),
  utf8 = utf8 and copy(utf8),
  os = os,
  debug = {traceback = traceback, getinfo = getinfo},
  checkArg = checkArg,
  computer = computer,
  component = component,
  unicode = unicode,
}
sandbox._G = sandbox
sandbox.string.dump = nil
getmetatable("").__index = sandbox.string

-------------------------------------------------------------------------------
-- Boot: run the EEPROM.

local eeprom = firstEeprom()
if not eeprom then
  rawError("no bios found; install a configured EEPROM", 0)
end
local code = invoke(eeprom, "get")
if not code or #code == 0 then
  rawError("the EEPROM is empty", 0)
end
local bios, reason = rawLoad(code, "=bios", "t", sandbox)
if not bios then
  rawError("failed loading bios: " .. tostring(reason), 0)
end
local ok, err = xpcall(bios, function(msg)
  if traceback and rawType(msg) == "string" then return traceback(msg, 2) end
  return msg
end)
if not ok then
  rawError(err, 0)
end
