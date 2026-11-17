-- cat [files...]: prints files (or standard input) to standard output.
local args = {...}
if #args == 0 then
  for line in io.lines() do io.write(line, "\n") end
  return
end
local exit = 0
for _, path in ipairs(args) do
  local f, reason = io.open(path, "r")
  if not f then
    io.stderr:write("cat: " .. path .. ": " .. tostring(reason) .. "\n")
    exit = 1
  else
    repeat
      local chunk = f:read(2048)
      if chunk then io.write(chunk) end
    until not chunk
    f:close()
  end
end
return exit
