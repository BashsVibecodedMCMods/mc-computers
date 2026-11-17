-- lua [file [args...]]: runs a Lua file, or starts an interactive prompt. In the prompt, expressions print their
-- values; "=expr" works too. Exit with exit() or Ctrl+D.
local serialization = require("serialization")
local shell = require("shell")
local term = require("term")
local args = table.pack(...)

if args.n > 0 then
  local path, reason = shell.resolve(args[1], "lua")
  if not path then
    io.stderr:write("lua: " .. tostring(reason) .. "\n")
    return 1
  end
  local fn, err = shell.load(path)
  if not fn then
    io.stderr:write("lua: " .. tostring(err) .. "\n")
    return 1
  end
  return fn(table.unpack(args, 2, args.n))
end

local env = setmetatable({}, {__index = _G})
local running = true
env.exit = function() running = false end
io.write(_VERSION .. " on " .. _OSVERSION .. ". Type exit() or press Ctrl+D to leave.\n")
local history = {}
while running do
  io.write("lua> ")
  local line = term.isAvailable() and term.read({history = history}) or io.read("l")
  if not line then break end
  line = line:gsub("^=", "return ")
  local fn, reason = load("return " .. line, "=stdin", "t", env)
  if not fn then fn, reason = load(line, "=stdin", "t", env) end
  if not fn then
    io.stderr:write(tostring(reason) .. "\n")
  else
    local result = table.pack(xpcall(fn, debug.traceback))
    if not result[1] then
      io.stderr:write(tostring(result[2]) .. "\n")
    elseif result.n > 1 then
      local parts = {}
      for i = 2, result.n do
        local v = result[i]
        if type(v) == "table" then
          local ok, s = pcall(serialization.serialize, v, true)
          parts[#parts + 1] = ok and s or tostring(v)
        elseif type(v) == "string" then
          parts[#parts + 1] = string.format("%q", v)
        else
          parts[#parts + 1] = tostring(v)
        end
      end
      io.write(table.concat(parts, "\t") .. "\n")
    end
  end
end
