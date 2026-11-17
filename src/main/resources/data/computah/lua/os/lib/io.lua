-- Streams: files (io.open), the terminal (default stdin/stdout/stderr) and pipes. io.stdin/io.stdout/io.stderr
-- belong to the running process, so the shell can redirect them per program.
local filesystem = require("filesystem")
local process = require("process")
local event = require("event")

local io = {}
local Stream = {}
Stream.__index = Stream

--- Wraps an object with readChunk()/writeChunk(data)/seekTo(whence, offset)/closeIt() into a buffered stream.
function io.stream(backend)
  return setmetatable({backend = backend, buffer = "", closed = false}, Stream)
end

function Stream:fill()
  if self.eof then return false end
  local chunk = self.backend.readChunk and self.backend:readChunk() or nil
  if not chunk then
    self.eof = true
    return false
  end
  self.buffer = self.buffer .. chunk
  return true
end

local function readLine(self, keep)
  while true do
    local i = self.buffer:find("\n", 1, true)
    if i then
      local line = self.buffer:sub(1, keep and i or i - 1)
      self.buffer = self.buffer:sub(i + 1)
      return line
    end
    if not self:fill() then
      if self.buffer == "" then return nil end
      local line = self.buffer
      self.buffer = ""
      return line
    end
  end
end

function Stream:read(...)
  if self.closed then return nil, "stream is closed" end
  local formats = table.pack(...)
  if formats.n == 0 then formats = {n = 1, "l"} end
  local results = {}
  for i = 1, formats.n do
    local f = formats[i]
    local value
    if type(f) == "number" then
      while #self.buffer < f and self:fill() do end
      if #self.buffer == 0 and f > 0 then
        value = nil
      else
        value = self.buffer:sub(1, f)
        self.buffer = self.buffer:sub(f + 1)
      end
    else
      f = tostring(f):gsub("^%*", "")
      local kind = f:sub(1, 1)
      if kind == "l" then
        value = readLine(self, false)
      elseif kind == "L" then
        value = readLine(self, true)
      elseif kind == "a" then
        while self:fill() do end
        value = self.buffer
        self.buffer = ""
      elseif kind == "n" then
        local line = readLine(self, false)
        value = line and tonumber(line:match("^%s*(%S+)"))
      else
        error("bad argument #" .. i .. " (invalid format)", 2)
      end
    end
    results[i] = value
    if value == nil then return table.unpack(results, 1, i) end
  end
  return table.unpack(results, 1, formats.n)
end

function Stream:lines(...)
  local formats = table.pack(...)
  return function()
    local line = self:read(table.unpack(formats, 1, formats.n))
    return line
  end
end

function Stream:write(...)
  if self.closed then return nil, "stream is closed" end
  local args = table.pack(...)
  for i = 1, args.n do
    local v = args[i]
    if type(v) == "number" then v = tostring(v) end
    if type(v) ~= "string" then error("bad argument #" .. i .. " (string expected, got " .. type(v) .. ")", 2) end
    local ok, reason = self.backend:writeChunk(v)
    if ok == nil and reason then return nil, reason end
  end
  return self
end

function Stream:seek(whence, offset)
  whence = whence or "cur"
  offset = offset or 0
  if not self.backend.seekTo then return nil, "not seekable" end
  if whence == "cur" then offset = offset - #self.buffer end
  self.buffer = ""
  self.eof = false
  return self.backend:seekTo(whence, offset)
end

function Stream:flush()
  return self
end

function Stream:setvbuf()
  return true
end

function Stream:close()
  if self.closed then return nil, "already closed" end
  self.closed = true
  if self.backend.closeIt then self.backend:closeIt() end
  return true
end

-- Files -----------------------------------------------------------------------

function io.open(path, mode)
  checkArg(1, path, "string")
  mode = mode or "r"
  local f, reason = filesystem.open(filesystem.canonical(path), mode)
  if not f then return nil, reason end
  return io.stream({
    readChunk = function() return f:read(2048) end,
    writeChunk = function(_, data)
      for i = 1, #data, 2048 do
        local ok, err = f:write(data:sub(i, i + 2047))
        if not ok then return nil, err end
      end
      return true
    end,
    seekTo = function(_, whence, offset) return f:seek(whence, offset) end,
    closeIt = function() f:close() end,
  })
end

-- Terminal --------------------------------------------------------------------

local history = {}
local function termStream(isError)
  local term = require("term")
  local s = io.stream({
    readChunk = function()
      local line = term.read({history = history})
      return line and line .. "\n" or nil
    end,
    writeChunk = function(_, data)
      if isError and term.gpu() and term.isAvailable() then
        local gpu = term.gpu()
        local old = gpu.setForeground(0xFF5555)
        term.write(data)
        gpu.setForeground(old)
      else
        term.write(data)
      end
      return true
    end,
  })
  s.tty = true
  return s
end

--- Whether a stream is the terminal (programs can format differently for files and pipes).
function io.isTerminal(stream)
  return type(stream) == "table" and stream.tty == true
end

local root = process.data()
root.io.stdin = termStream(false)
root.io.stdout = termStream(false)
root.io.stderr = termStream(true)
-- Threads started before io loaded (the kernel's) share the root streams.
process.hooks.error = function(t, err)
  local s = t.data.io.stderr or root.io.stderr
  if type(err) ~= "string" or err ~= "interrupted" then
    pcall(s.write, s, tostring(err) .. "\n")
  end
end

-- Pipes -----------------------------------------------------------------------

--- A connected pair of streams: what is written to the first can be read from the second.
function io.pipe()
  local queue, closed = {}, false
  local id = tostring(queue):sub(8)
  local reader = io.stream({
    readChunk = function()
      while #queue == 0 do
        if closed then return nil end
        event.pull(1, "pipe", id)
      end
      return table.remove(queue, 1)
    end,
  })
  local writer = io.stream({
    writeChunk = function(_, data)
      queue[#queue + 1] = data
      computer.pushSignal("pipe", id)
      return true
    end,
    closeIt = function()
      closed = true
      computer.pushSignal("pipe", id)
    end,
  })
  return writer, reader
end

-- The io library ----------------------------------------------------------------

local function data()
  return process.data()
end

function io.input(file)
  if type(file) == "string" then file = assert(io.open(file, "r")) end
  if file then data().io.stdin = file end
  return data().io.stdin or root.io.stdin
end

function io.output(file)
  if type(file) == "string" then file = assert(io.open(file, "w")) end
  if file then data().io.stdout = file end
  return data().io.stdout or root.io.stdout
end

function io.error(file)
  if file then data().io.stderr = file end
  return data().io.stderr or root.io.stderr
end

function io.read(...)
  return io.input():read(...)
end

function io.write(...)
  return io.output():write(...)
end

function io.lines(path, ...)
  if path then
    local f = assert(io.open(path, "r"))
    local formats = table.pack(...)
    return function()
      local line = f:read(table.unpack(formats, 1, formats.n))
      if not line then f:close() end
      return line
    end
  end
  return io.input():lines(...)
end

function io.close(file)
  return (file or io.output()):close()
end

function io.type(obj)
  if getmetatable(obj) == Stream then return obj.closed and "closed file" or "file" end
  return nil
end

setmetatable(io, {__index = function(_, key)
  if key == "stdin" then return io.input() end
  if key == "stdout" then return io.output() end
  if key == "stderr" then return io.error() end
end})

function print(...)
  local args = table.pack(...)
  local parts = {}
  for i = 1, args.n do parts[i] = tostring(args[i]) end
  io.output():write(table.concat(parts, "\t") .. "\n")
end

_G.io = io
return io
