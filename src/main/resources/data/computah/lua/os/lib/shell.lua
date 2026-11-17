-- Running programs. shell.execute parses a command line: words with "quotes", 'quotes' and \escapes, $VARIABLES,
-- * globs, pipes (a | b), redirection (< file, > file, >> file, 2> file), sequences (;, &&, ||) and background jobs (&).
local filesystem = require("filesystem")
local process = require("process")
local event = require("event")
local text = require("text")
local kernel = package.kernel

local shell = {}
local aliasTable = {ll = "ls -l", la = "ls -a", dir = "ls", cls = "clear", ["?"] = "help"}
shell.builtins = {}

--- The command an alias stands for, or nil.
function shell.getAlias(name)
  checkArg(1, name, "string")
  return aliasTable[name]
end

--- Defines (or with nil removes) an alias: typing `name` runs `value` (extra words are passed on).
function shell.setAlias(name, value)
  checkArg(1, name, "string")
  checkArg(2, value, "string", "nil")
  aliasTable[name] = value
end

--- Iterates over aliases: for name, value in shell.aliases() do ... end
function shell.aliases()
  return next, aliasTable, nil
end

--- The program search path (the PATH variable).
function shell.getPath()
  return os.getenv("PATH")
end

function shell.setPath(value)
  checkArg(1, value, "string")
  os.setenv("PATH", value)
end

function shell.getWorkingDirectory()
  return process.data().cwd or "/"
end

function shell.setWorkingDirectory(path)
  checkArg(1, path, "string")
  path = filesystem.canonical(path)
  if not filesystem.isDirectory(path) then return nil, "not a directory" end
  process.data().cwd = path
  return true
end

function shell.resolve(path, ext)
  checkArg(1, path, "string")
  if path:find("/") then
    local p = filesystem.canonical(path)
    if filesystem.exists(p) and not filesystem.isDirectory(p) then return p end
    if ext and filesystem.exists(p .. "." .. ext) then return p .. "." .. ext end
    return nil, "file not found"
  end
  if not ext then return filesystem.canonical(path) end
  for dir in (os.getenv("PATH") or "/bin"):gmatch("[^:]+") do
    local base = filesystem.concat(dir == "." and shell.getWorkingDirectory() or dir, path)
    if base:sub(1, 1) ~= "/" then base = "/" .. base end
    for _, candidate in ipairs({base .. "." .. ext, base}) do
      if filesystem.exists(candidate) and not filesystem.isDirectory(candidate) then return candidate end
    end
  end
  return nil, "program not found"
end

--- Splits arguments into plain ones and options: -abc sets a, b and c; --name=value sets name.
function shell.parse(...)
  local params = table.pack(...)
  local args, options = {}, {}
  local done = false
  for i = 1, params.n do
    local p = params[i]
    if not done and type(p) == "string" and p == "--" then
      done = true
    elseif not done and type(p) == "string" and p:sub(1, 2) == "--" and #p > 2 then
      local key, value = p:match("^%-%-([^=]+)=(.*)$")
      if key then options[key] = value else options[p:sub(3)] = true end
    elseif not done and type(p) == "string" and p:sub(1, 1) == "-" and #p > 1 and not tonumber(p) then
      for c in p:sub(2):gmatch(".") do options[c] = true end
    else
      args[#args + 1] = p
    end
  end
  return args, options
end

-- Tokenizer ---------------------------------------------------------------------

local function expandVar(name)
  if name == "?" then return tostring(shell.lastExit or 0) end
  return os.getenv(name) or ""
end

local function tokenize(line)
  local tokens = {}
  local current, quoted, glob = nil, false, false
  local i, n = 1, #line
  local function push()
    if current then tokens[#tokens + 1] = {text = current, quoted = quoted, glob = glob} end
    current, quoted, glob = nil, false, false
  end
  while i <= n do
    local c = line:sub(i, i)
    if c:match("%s") then
      push()
      i = i + 1
    elseif c == "\\" then
      current = (current or "") .. line:sub(i + 1, i + 1)
      i = i + 2
    elseif c == "'" then
      local j = line:find("'", i + 1, true)
      if not j then return nil, "unclosed quote" end
      current = (current or "") .. line:sub(i + 1, j - 1)
      quoted = true
      i = j + 1
    elseif c == '"' then
      local j = i + 1
      local buf = {}
      while j <= n and line:sub(j, j) ~= '"' do
        local d = line:sub(j, j)
        if d == "\\" and j < n then
          buf[#buf + 1] = line:sub(j + 1, j + 1)
          j = j + 2
        elseif d == "$" then
          local name, stop = line:match("^{([%w_]+)}()", j + 1)
          if not name then name, stop = line:match("^([%w_?]+)()", j + 1) end
          if name then
            buf[#buf + 1] = expandVar(name)
            j = stop
          else
            buf[#buf + 1] = "$"
            j = j + 1
          end
        else
          buf[#buf + 1] = d
          j = j + 1
        end
      end
      if j > n then return nil, "unclosed quote" end
      current = (current or "") .. table.concat(buf)
      quoted = true
      i = j + 1
    elseif c == "$" then
      local name, stop = line:match("^{([%w_]+)}()", i + 1)
      if not name then name, stop = line:match("^([%w_?]+)()", i + 1) end
      if name then
        current = (current or "") .. expandVar(name)
        i = stop
      else
        current = (current or "") .. "$"
        i = i + 1
      end
    elseif c == "|" or c == ";" or c == "&" or c == "<" or c == ">" then
      push()
      local two = line:sub(i, i + 1)
      if two == "||" or two == "&&" or two == ">>" then
        tokens[#tokens + 1] = {op = two}
        i = i + 2
      else
        tokens[#tokens + 1] = {op = c}
        i = i + 1
      end
    elseif c == "2" and line:sub(i + 1, i + 1) == ">" and current == nil then
      tokens[#tokens + 1] = {op = "2>"}
      i = i + 2
    else
      if c == "*" or c == "?" then glob = true end
      current = (current or "") .. c
      i = i + 1
    end
  end
  push()
  return tokens
end

local function expandGlob(word)
  local dir, pattern = word:match("^(.-)([^/]*)$")
  if pattern == "" then return {word} end
  local lua = "^" .. pattern:gsub("[%.%+%-%^%$%(%)%%]", "%%%0"):gsub("%*", ".*"):gsub("%?", ".") .. "$"
  local results = {}
  local list = filesystem.list(filesystem.canonical(dir == "" and "." or dir))
  if list then
    for name in list do
      name = name:gsub("/$", "")
      if name:match(lua) and (name:sub(1, 1) ~= "." or pattern:sub(1, 1) == ".") then results[#results + 1] = dir .. name end
    end
  end
  table.sort(results)
  if #results == 0 then return {word} end
  return results
end

--- Parses a command line into a list of {pipeline = {commands}, background, op} entries.
function shell.parseLine(line)
  local tokens, reason = tokenize(line)
  if not tokens then return nil, reason end
  local entries, pipeline, command = {}, {}, {args = {}}
  local function endCommand()
    if #command.args > 0 or command.stdin or command.stdout then pipeline[#pipeline + 1] = command end
    command = {args = {}}
  end
  local function endEntry(op, background)
    endCommand()
    if #pipeline > 0 then entries[#entries + 1] = {pipeline = pipeline, background = background, op = op} end
    pipeline = {}
  end
  local i = 1
  local pendingOp = nil
  while i <= #tokens do
    local t = tokens[i]
    if t.op == "|" then
      endCommand()
    elseif t.op == ";" or t.op == "&&" or t.op == "||" then
      endEntry(pendingOp, false)
      pendingOp = t.op ~= ";" and t.op or nil
    elseif t.op == "&" then
      endEntry(pendingOp, true)
      pendingOp = nil
    elseif t.op == "<" or t.op == ">" or t.op == ">>" or t.op == "2>" then
      local target = tokens[i + 1]
      if not target or target.op then return nil, "missing file name after " .. t.op end
      if t.op == "<" then command.stdin = target.text
      elseif t.op == "2>" then command.stderr = target.text
      else
        command.stdout = target.text
        command.append = t.op == ">>"
      end
      i = i + 1
    else
      if t.glob and not t.quoted then
        for _, w in ipairs(expandGlob(t.text)) do command.args[#command.args + 1] = w end
      else
        command.args[#command.args + 1] = t.text
      end
    end
    i = i + 1
  end
  endEntry(pendingOp, false)
  return entries
end

-- Running -------------------------------------------------------------------------

--- Loads a program file. Returns a function taking its arguments.
function shell.load(path)
  local env = setmetatable({}, {__index = _G})
  local fn, reason = kernel.loadfile(path, env)
  if not fn then return nil, reason end
  return fn, env
end

--- Starts a program as a new process. Returns the process (a thread) or nil, reason.
function shell.spawn(name, args, streams, detached)
  local alias = aliasTable[name]
  if alias then
    local parts = text.tokenize(alias)
    name = table.remove(parts, 1)
    for i = #parts, 1, -1 do table.insert(args, 1, parts[i]) end
  end
  local builtin = shell.builtins[name]
  local fn, reason
  local path
  if builtin then
    fn = function(...) return builtin(...) end
  else
    path, reason = shell.resolve(name, "lua")
    if not path then return nil, name .. ": " .. reason end
    fn, reason = shell.load(path)
    if not fn then return nil, reason end
  end
  local t = process.spawn(function(...)
    local result = fn(...)
    return result
  end, name, {args = table.pack(table.unpack(args)), detached = detached})
  t.data.path = path or name
  streams = streams or {}
  for k, v in pairs(streams) do t.data.io[k] = v end
  return t
end

local function exitCode(t)
  if t.error ~= nil then return false, t.error end
  local r = t.result and t.result[1]
  if r == nil or r == true or r == 0 then return true, 0 end
  if r == false then return false, 1 end
  return type(r) ~= "number", r
end

local function runPipeline(entry)
  local commands = entry.pipeline
  if #commands == 1 and not entry.background then
    local cmd = commands[1]
    local builtin = shell.builtins[cmd.args[1]]
    if builtin and not cmd.stdin and not cmd.stdout and not cmd.stderr then
      local ok, r = pcall(builtin, table.unpack(cmd.args, 2))
      if not ok then
        if type(r) == "table" and r.exit ~= nil then error(r, 0) end
        return false, r
      end
      return r == nil or r == 0, r or 0
    end
  end
  local threads = {}
  local toClose = {}
  local previousReader
  for i, cmd in ipairs(commands) do
    local streams = {}
    if previousReader then streams.stdin = previousReader end
    if cmd.stdin then
      local f, reason = io.open(cmd.stdin, "r")
      if not f then return false, cmd.stdin .. ": " .. tostring(reason) end
      streams.stdin = f
      toClose[#toClose + 1] = f
    end
    if i < #commands then
      local writer, reader = io.pipe()
      streams.stdout = writer
      previousReader = reader
      toClose[#toClose + 1] = writer
    end
    if cmd.stdout then
      local f, reason = io.open(cmd.stdout, cmd.append and "a" or "w")
      if not f then return false, cmd.stdout .. ": " .. tostring(reason) end
      streams.stdout = f
      toClose[#toClose + 1] = f
    end
    if cmd.stderr then
      local f, reason = io.open(cmd.stderr, "w")
      if not f then return false, cmd.stderr .. ": " .. tostring(reason) end
      streams.stderr = f
      toClose[#toClose + 1] = f
    end
    if #cmd.args == 0 then return false, "missing command" end
    local name = table.remove(cmd.args, 1)
    local t, reason = shell.spawn(name, cmd.args, streams, entry.background)
    if not t then
      for _, f in ipairs(toClose) do f:close() end
      return false, reason
    end
    threads[#threads + 1] = {thread = t, stdout = i < #commands and streams.stdout or nil}
  end
  if entry.background then
    for _, info in ipairs(threads) do
      io.write("[" .. info.thread.id .. "] " .. info.thread.name .. "\n")
    end
    return true, 0
  end
  local ok, code = true, 0
  local last = threads[#threads].thread
  shell.foreground = last
  for _, info in ipairs(threads) do
    local joined, err = pcall(info.thread.join, info.thread)
    if not joined then
      for _, other in ipairs(threads) do other.thread:kill() end
      ok, code = false, err
      break
    end
    if info.stdout then info.stdout:close() end
  end
  shell.foreground = nil
  for _, f in ipairs(toClose) do pcall(f.close, f) end
  if ok then ok, code = exitCode(last) end
  return ok, code
end

--- Runs a command line and waits for it (except background jobs). Returns success, exit code or error.
function shell.execute(line)
  checkArg(1, line, "string")
  local entries, reason = shell.parseLine(line)
  if not entries then return false, reason end
  local ok, code = true, 0
  for _, entry in ipairs(entries) do
    if entry.op == "&&" and not ok or entry.op == "||" and ok then
      -- skipped
    else
      ok, code = runPipeline(entry)
      shell.lastExit = ok and 0 or (type(code) == "number" and code or 1)
      if not ok and type(code) ~= "number" and code ~= nil then
        io.stderr:write(tostring(code) .. "\n")
      end
    end
  end
  return ok, code
end

--- Runs a program by name with arguments and waits for it.
function shell.run(name, ...)
  local t, reason = shell.spawn(name, table.pack(...), nil, false)
  if not t then return false, reason end
  shell.foreground = t
  local joined, err = pcall(t.join, t)
  shell.foreground = nil
  if not joined then
    t:kill()
    return false, err
  end
  return exitCode(t)
end

-- Builtins run inside the shell's own process, so they can change its directory and variables.
function shell.builtins.cd(dir)
  local ok, reason = shell.setWorkingDirectory(dir or os.getenv("HOME") or "/")
  if not ok then
    io.stderr:write("cd: " .. tostring(dir) .. ": " .. reason .. "\n")
    return 1
  end
end

function shell.builtins.exit(code)
  os.exit(tonumber(code) or 0)
end

function shell.builtins.set(assignment)
  if not assignment then
    for k, v in pairs(os.getenv()) do io.write(k .. "=" .. tostring(v) .. "\n") end
    return
  end
  local k, v = assignment:match("^([%w_]+)=(.*)$")
  if not k then
    io.stderr:write("usage: set NAME=value\n")
    return 1
  end
  os.setenv(k, v)
end

function shell.builtins.unset(name)
  if name then os.setenv(name, nil) end
end

function shell.builtins.alias(name, value)
  if not name then
    for k, v in shell.aliases() do io.write(k .. " = " .. v .. "\n") end
  elseif value then
    shell.setAlias(name, value)
  else
    io.write(name .. " = " .. tostring(shell.getAlias(name)) .. "\n")
  end
end

function shell.builtins.unalias(name)
  if name then shell.setAlias(name, nil) end
end

function shell.builtins.pwd()
  io.write(shell.getWorkingDirectory() .. "\n")
end

-- Ctrl+C interrupts the foreground program.
event.listen("key_down", function(_, _, char)
  if char == 3 and shell.foreground then process.interrupt(shell.foreground) end
end)

return shell
