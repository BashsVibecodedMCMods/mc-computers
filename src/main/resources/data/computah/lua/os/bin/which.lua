-- which <program>: shows where a program is.
local shell = require("shell")
local name = ...
if not name then io.write("usage: which <program>\n") return 1 end
if shell.builtins[name] then io.write(name .. ": shell builtin\n") return end
if shell.aliases[name] then io.write(name .. ": alias for " .. shell.aliases[name] .. "\n") return end
local path, reason = shell.resolve(name, "lua")
if not path then io.stderr:write(name .. ": " .. reason .. "\n") return 1 end
io.write(path .. "\n")
