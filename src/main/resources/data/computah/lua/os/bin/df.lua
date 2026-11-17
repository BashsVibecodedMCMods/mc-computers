-- df: shows mounted filesystems and their space.
local filesystem = require("filesystem")
local text = require("text")
io.write(string.format("%-9s %8s %8s %4s  %s\n", "Address", "Used", "Total", "RO", "Mounted on"))
local rows = {}
for fs, path in filesystem.mounts() do rows[#rows + 1] = {fs, path} end
table.sort(rows, function(a, b) return a[2] < b[2] end)
for _, r in ipairs(rows) do
  local fs, path = r[1], r[2]
  local total = fs.spaceTotal()
  io.write(string.format("%-9s %8s %8s %4s  %s%s\n", fs.address:sub(1, 8), text.size(fs.spaceUsed()),
    total == math.huge and "-" or text.size(total), fs.isReadOnly() and "yes" or "no", path,
    fs.getLabel() and ("  (" .. fs.getLabel() .. ")") or ""))
end
