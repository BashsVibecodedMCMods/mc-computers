-- hostname [name]: shows or sets this computer's name (stored in /etc/hostname).
local name = ...
if not name then io.write(os.hostname() .. "\n") return end
local ok, reason = require("filesystem").writeAll("/etc/hostname", name .. "\n")
if not ok then io.stderr:write("hostname: " .. tostring(reason) .. "\n") return 1 end
