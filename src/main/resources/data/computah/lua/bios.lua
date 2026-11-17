-- Computah Lua BIOS. Lives on the EEPROM (max 4096 bytes) and runs when the computer starts.
-- It boots /init.lua from the filesystem whose address is stored in the EEPROM's data area,
-- or else from the first filesystem that has one (and remembers that address).
-- See docs/EEPROM.md for how this works and how to write your own.
local component, computer = component, computer
local invoke = component.invoke

local function tryBoot(address)
  local handle, reason = invoke(address, "open", "/init.lua")
  if not handle then
    return nil, reason
  end
  local code = ""
  repeat
    local chunk = invoke(address, "read", handle, math.huge)
    code = code .. (chunk or "")
  until not chunk
  invoke(address, "close", handle)
  return load(code, "=init")
end

local function status(message)
  local gpu = component.list("gpu")()
  local screen = component.list("screen")()
  if gpu and screen then
    invoke(gpu, "bind", screen)
    local w, h = invoke(gpu, "getResolution")
    invoke(gpu, "setBackground", 0x000000)
    invoke(gpu, "setForeground", 0xFFFFFF)
    invoke(gpu, "fill", 1, 1, w, h, " ")
    invoke(gpu, "set", 1, 1, message)
  end
end

local init, reason
local saved = computer.getBootAddress()
if saved and component.type(saved) == "filesystem" then
  init, reason = tryBoot(saved)
end
if not init then
  for address in component.list("filesystem") do
    if address ~= computer.tmpAddress() then
      init, reason = tryBoot(address)
      if init then
        if address ~= saved then
          computer.setBootAddress(address)
        end
        break
      end
    end
  end
end
if not init then
  status("No bootable medium found" .. (reason and (": " .. tostring(reason)) or ""))
  error("no bootable medium found" .. (reason and (": " .. tostring(reason)) or ""), 0)
end
status("Booting...")
computer.beep(1000, 0.1)
return init()
