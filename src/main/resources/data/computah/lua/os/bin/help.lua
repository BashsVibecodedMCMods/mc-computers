-- help: lists the available programs.
local filesystem = require("filesystem")
local text = require("text")
local term = require("term")
local names = {}
for dir in (os.getenv("PATH") or "/bin"):gmatch("[^:]+") do
  if dir ~= "." and filesystem.isDirectory(dir) then
    for name in filesystem.list(dir) do
      local n = name:match("^(.+)%.lua$")
      if n then names[#names + 1] = n end
    end
  end
end
table.sort(names)
io.write("Programs (\"man <topic>\" for manuals; topics: " )
local topics = {}
for t in filesystem.list("/usr/man") do topics[#topics + 1] = t end
io.write(table.concat(topics, ", ") .. "):\n")
local w = term.isAvailable() and select(1, term.getViewport()) or 80
local line = ""
for _, n in ipairs(names) do
  local entry = text.padRight(n, 12)
  if #line + #entry > w then
    io.write(line .. "\n")
    line = ""
  end
  line = line .. entry
end
if line ~= "" then io.write(line .. "\n") end
io.write("Shell: a | b pipes, > and >> redirect, & runs in the background, Ctrl+C stops a program.\n")
