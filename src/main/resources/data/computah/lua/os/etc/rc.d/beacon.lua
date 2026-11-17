-- Answers host lookups on port 1 (used by `ping`, `net.discover` and `net.resolve`) with this computer's hostname.
local event = require("event")
local component = component

local function handler(_, localAddress, from, port, _, kind, name)
  if port ~= 1 or kind ~= "computah_lookup" then return end
  local hostname = os.hostname()
  if name == nil or name == hostname then
    pcall(component.invoke, localAddress, "send", from, 1, "computah_here", hostname)
  end
end

local function openAll()
  for address in component.list("modem", true) do pcall(component.invoke, address, "open", 1) end
end

local function onAdded(_, _, ctype)
  if ctype == "modem" then openAll() end
end

function start()
  openAll()
  event.listen("modem_message", handler)
  event.listen("component_added", onAdded)
end

function stop()
  event.ignore("modem_message", handler)
  event.ignore("component_added", onAdded)
end
