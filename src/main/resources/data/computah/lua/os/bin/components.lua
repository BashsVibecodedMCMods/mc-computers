-- components [-l] [type]: lists components (address, type). -l also lists their methods.
local shell = require("shell")
local args, options = shell.parse(...)
local list = {}
for address, ctype in component.list(args[1]) do list[#list + 1] = {address, ctype} end
table.sort(list, function(a, b) return a[2] < b[2] or (a[2] == b[2] and a[1] < b[1]) end)
for _, c in ipairs(list) do
  io.write(c[1] .. "  " .. c[2] .. "\n")
  if options.l then
    local methods = {}
    for name, direct in pairs(component.methods(c[1])) do methods[#methods + 1] = name .. (direct and "" or "*") end
    table.sort(methods)
    io.write("    " .. table.concat(methods, ", ") .. "\n")
    if options.d then
      for _, name in ipairs(methods) do
        local doc = component.doc(c[1], (name:gsub("%*$", "")))
        if doc then io.write("    " .. name .. ": " .. doc .. "\n") end
      end
    end
  end
end
if options.l then io.write("(* = runs on the server thread, one call per tick)\n") end
