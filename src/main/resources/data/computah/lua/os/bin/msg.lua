-- msg send <host|address> <text...>   sends a text message (port 2)
-- msg listen                          shows messages as they arrive (Ctrl+C to stop)
local net = require("net")
local event = require("event")
local mode = ...
local PORT = 2
if mode == "send" then
  local target = select(2, ...)
  local message = table.concat({select(3, ...)}, " ")
  if not target or message == "" then io.write("usage: msg send <host> <text>\n") return 1 end
  local address = net.resolve(target, 1)
  if not address then io.stderr:write("msg: host not found: " .. target .. "\n") return 1 end
  net.send(address, PORT, "msg", os.hostname(), message)
  io.write("sent\n")
elseif mode == "listen" then
  net.open(PORT)
  io.write("listening for messages on port " .. PORT .. " (Ctrl+C to stop)\n")
  while true do
    local from, port, _, kind, sender, message = net.receive(nil, PORT)
    if kind == "msg" then io.write(string.format("[%s] %s: %s\n", os.date("!%H:%M"), tostring(sender or from:sub(1, 8)), tostring(message))) end
  end
else
  io.write("usage: msg send <host> <text> | msg listen\n")
  return 1
end
