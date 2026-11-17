-- OS additions to the component library: primary components. component.gpu, component.modem, ... return the
-- primary component of that type (the first one found, unless set with component.setPrimary).
local component = component
local event = require("event")
local primaries = {}

function component.isAvailable(ctype)
  checkArg(1, ctype, "string")
  return component.getPrimary(ctype, true) ~= nil
end

function component.getPrimary(ctype, quiet)
  checkArg(1, ctype, "string")
  local p = primaries[ctype]
  if p and component.type(p.address) == ctype then return p end
  primaries[ctype] = nil
  local address = component.list(ctype, true)()
  if address then
    primaries[ctype] = component.proxy(address)
    return primaries[ctype]
  end
  if quiet then return nil end
  error("no primary '" .. ctype .. "' available", 2)
end

--- Whether this component is the primary one of its type.
function component.isPrimary(address)
  checkArg(1, address, "string")
  local ctype = component.type(address)
  if not ctype then return false end
  local p = component.getPrimary(ctype, true)
  return p ~= nil and p.address == address
end

function component.setPrimary(ctype, address)
  checkArg(1, ctype, "string")
  checkArg(2, address, "string", "nil")
  if address then
    address = component.get(address, ctype) or address
    primaries[ctype] = component.proxy(address)
  else
    primaries[ctype] = nil
  end
  computer.pushSignal("component_available", ctype)
end

event.listen("component_removed", function(_, address, ctype)
  local p = primaries[ctype]
  if p and p.address == address then
    primaries[ctype] = nil
    computer.pushSignal("component_unavailable", ctype)
  end
end)

event.listen("component_added", function(_, address, ctype)
  if not primaries[ctype] then computer.pushSignal("component_available", ctype) end
end)

setmetatable(component, {__index = function(_, key)
  return component.getPrimary(key, true)
end})

return component
