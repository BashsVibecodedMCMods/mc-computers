-- resolution [width height]: shows or sets the screen's text resolution.
local term = require("term")
local gpu = term.gpu()
if not gpu then io.stderr:write("no screen\n") return 1 end
local w, h = ...
if not w then
  local cw, ch = gpu.getResolution()
  local mw, mh = gpu.maxResolution()
  io.write(string.format("%dx%d (max %dx%d)\n", cw, ch, mw, mh))
  return
end
local ok, reason = pcall(gpu.setResolution, tonumber(w), tonumber(h))
if not ok then io.stderr:write(tostring(reason) .. "\n") return 1 end
term.clear()
