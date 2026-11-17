-- Simple messaging over the primary modem (network or wireless card), and host discovery. Hosts answer lookups
-- when the "beacon" rc service runs (it is enabled by default). See docs/NETWORKING.md.
local component = component
local event = require("event")
local net = {DISCOVERY_PORT = 1}

local function modem()
  local m = component.getPrimary("modem", true)
  if not m then error("no network card or wireless card installed", 3) end
  return m
end

function net.modem()
  return component.getPrimary("modem", true)
end

function net.open(port)
  return modem().open(port)
end

function net.close(port)
  return modem().close(port)
end

function net.send(address, port, ...)
  checkArg(1, address, "string")
  checkArg(2, port, "number")
  return modem().send(address, port, ...)
end

function net.broadcast(port, ...)
  checkArg(1, port, "number")
  return modem().broadcast(port, ...)
end

--- Waits for a message (on one port, or any). Returns sender, port, distance, values... or nil after the timeout.
function net.receive(timeout, port)
  local signal = table.pack(event.pull(timeout, "modem_message", nil, nil, port))
  if signal.n == 0 or signal[1] == nil then return nil end
  return table.unpack(signal, 3, signal.n)
end

function net.hostname()
  return os.hostname()
end

--- Broadcasts a lookup and collects answers for `timeout` seconds (default 1).
--- Returns a list of {address, hostname, distance}. With a name, only hosts with that hostname answer.
function net.discover(name, timeout)
  local m = modem()
  local wasOpen = m.isOpen(net.DISCOVERY_PORT)
  m.open(net.DISCOVERY_PORT)
  m.broadcast(net.DISCOVERY_PORT, "computah_lookup", name)
  local found = {}
  local deadline = computer.uptime() + (timeout or 1)
  while computer.uptime() < deadline do
    local _, _, from, port, distance, kind, hostname = event.pull(deadline - computer.uptime(), "modem_message")
    if port == net.DISCOVERY_PORT and kind == "computah_here" then
      found[#found + 1] = {address = from, hostname = hostname, distance = distance}
    end
  end
  if not wasOpen then m.close(net.DISCOVERY_PORT) end
  return found
end

--- The address of a host by hostname (or a full address, returned as is), or nil.
function net.resolve(name, timeout)
  checkArg(1, name, "string")
  if #name == 36 and name:match("^%x+%-") then return name end
  local hosts = net.discover(name, timeout)
  return hosts[1] and hosts[1].address
end

return net
