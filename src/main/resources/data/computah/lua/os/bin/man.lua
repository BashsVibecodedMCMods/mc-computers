-- man <topic>: shows a manual page from /usr/man.
local filesystem = require("filesystem")
local topic = ...
if not topic then
  io.write("usage: man <topic>\ntopics:")
  for name in filesystem.list("/usr/man") do io.write(" " .. name) end
  io.write("\n")
  return 1
end
local path = "/usr/man/" .. topic
if not filesystem.exists(path) then io.stderr:write("man: no page for " .. topic .. "\n") return 1 end
return require("shell").execute("more " .. path)
