-- umount <path or address>: unmounts a filesystem.
local args = {...}
if #args == 0 then
  io.write("usage: umount <path or address>\n")
  return 1
end
if not require("filesystem").umount(args[1]) then io.stderr:write("umount: nothing mounted there\n") return 1 end
