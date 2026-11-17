-- wc [file]: counts lines, words and bytes.
local lines, words, bytes = 0, 0, 0
for line in io.lines((...)) do
  lines = lines + 1
  bytes = bytes + #line + 1
  for _ in line:gmatch("%S+") do words = words + 1 end
end
io.write(string.format("%d %d %d\n", lines, words, bytes))
