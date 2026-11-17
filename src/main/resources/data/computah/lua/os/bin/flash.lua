-- flash: reads and writes the EEPROM (the BIOS chip). See `man eeprom` and docs/EEPROM.md.
--   flash <file>          write a Lua file to the EEPROM (asks for confirmation; -q skips it)
--   flash -r <file>       save the EEPROM's code to a file
--   flash -l [label]      show or set the label
--   flash -d [data]       show or set the data area (by convention the boot address)
--   flash -c              show size, checksum and whether it is read-only
--   flash -e              erase the code
--   flash --lock          make the EEPROM read-only forever (code and label)
--   flash --bios          write the stock Lua BIOS (/lib/bios.lua) to the EEPROM
--   -w                    first wait until an EEPROM is swapped in (to program blank EEPROMs while the OS runs)
local shell = require("shell")
local args, options = shell.parse(...)
if options.w then
  local event = require("event")
  io.write("swap the EEPROM now (Ctrl+C to cancel)...\n")
  local address = event.pull("component_added", nil, "eeprom")
  if not address then return 1 end
  component.setPrimary("eeprom", address)
end
local eeprom = component.getPrimary("eeprom", true)
if not eeprom then
  io.stderr:write("flash: no EEPROM installed\n")
  return 1
end

local function confirm(question)
  if options.q then return true end
  io.write(question .. " [y/N] ")
  local answer = io.read("l")
  return answer and answer:lower():sub(1, 1) == "y"
end

if options.r then
  local path = args[1]
  if not path then io.write("usage: flash -r <file>\n") return 1 end
  local f, reason = io.open(path, "wb")
  if not f then io.stderr:write("flash: " .. tostring(reason) .. "\n") return 1 end
  f:write(eeprom.get())
  f:close()
  io.write("saved " .. #eeprom.get() .. " bytes to " .. path .. "\n")
elseif options.l then
  if args[1] then
    local ok, label = pcall(eeprom.setLabel, table.concat(args, " "))
    if not ok then io.stderr:write("flash: " .. tostring(label) .. "\n") return 1 end
    io.write("label set to " .. label .. "\n")
  else
    io.write(eeprom.getLabel() .. "\n")
  end
elseif options.d then
  if args[1] then
    eeprom.setData(args[1] == "-" and "" or args[1])
  else
    io.write(eeprom.getData() .. "\n")
  end
elseif options.c then
  io.write(string.format("label:    %s\ncode:     %d / %d bytes\ndata:     %d / %d bytes\nchecksum: %s\nreadonly: %s\n",
    eeprom.getLabel(), #eeprom.get(), eeprom.getSize(), #eeprom.getData(), eeprom.getDataSize(), eeprom.getChecksum(),
    tostring(eeprom.isReadOnly())))
elseif options.e then
  if confirm("Erase the EEPROM? The computer won't boot until you flash a new BIOS.") then
    eeprom.set("")
    io.write("erased\n")
  end
elseif options.lock then
  if confirm("Make the EEPROM read-only FOREVER?") then
    eeprom.makeReadonly(eeprom.getChecksum())
    io.write("the EEPROM is now read-only\n")
  end
else
  local path = args[1]
  if options.bios then path = "/lib/bios.lua" end
  if not path then
    io.write("usage: flash [-qw] <file> | --bios | -r <file> | -l [label] | -d [data] | -c | -e | --lock\n")
    return 1
  end
  local f, reason = io.open(path, "rb")
  if not f then io.stderr:write("flash: " .. tostring(reason) .. "\n") return 1 end
  local code = f:read("a")
  f:close()
  if #code > eeprom.getSize() then
    io.stderr:write(string.format("flash: %s is %d bytes, the EEPROM holds %d\n", path, #code, eeprom.getSize()))
    return 1
  end
  local fn, err = load(code, "=" .. path)
  if not fn then
    io.stderr:write("flash: " .. path .. " has a syntax error: " .. tostring(err) .. "\n")
    return 1
  end
  if not confirm("Write " .. #code .. " bytes to the EEPROM?") then return end
  local ok, e = pcall(eeprom.set, code)
  if not ok then io.stderr:write("flash: " .. tostring(e) .. "\n") return 1 end
  if options.bios then eeprom.setLabel("Lua BIOS")
  elseif options.l and args[2] then eeprom.setLabel(args[2]) end
  io.write("flashed " .. #code .. " bytes (checksum " .. eeprom.getChecksum() .. ")\n")
end
