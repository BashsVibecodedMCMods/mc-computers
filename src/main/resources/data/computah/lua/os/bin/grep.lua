-- grep [-i] [-v] [-n] <pattern> [files...]: prints lines matching a Lua pattern.
local shell = require("shell")
local args, options = shell.parse(...)
local pattern = table.remove(args, 1)
if not pattern then
  io.write("usage: grep [-i] [-v] [-n] <pattern> [file...]\n")
  return 1
end
if options.i then pattern = pattern:lower() end
local found = false
local function scan(lines, name)
  local n = 0
  for line in lines do
    n = n + 1
    local subject = options.i and line:lower() or line
    local ok, match = pcall(string.find, subject, pattern)
    if ok and ((match ~= nil) ~= (options.v == true)) then
      found = true
      io.write((name and (name .. ":") or "") .. (options.n and (n .. ":") or "") .. line .. "\n")
    end
  end
end
if #args == 0 then scan(io.lines()) else
  for _, f in ipairs(args) do scan(io.lines(f), #args > 1 and f or nil) end
end
return found and 0 or 1
