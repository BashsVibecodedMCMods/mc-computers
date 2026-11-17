-- head [-n count] [file]: prints the first lines (default 10).
local args = {...}
local n = 10
if args[1] == "-n" then
  n = tonumber(args[2]) or 10
  table.remove(args, 1)
  table.remove(args, 1)
end
local i = 0
for line in io.lines(args[1]) do
  i = i + 1
  if i > n then break end
  io.write(line, "\n")
end
