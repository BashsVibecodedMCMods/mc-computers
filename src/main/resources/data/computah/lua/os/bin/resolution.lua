-- resolution [width height | fit]: shows or sets the screen's text resolution.
--   resolution          current, maximum and fitted resolution
--   resolution W H      set it (until the screen is bound again, e.g. at the next boot)
--   resolution fit      use the resolution that fills the monitor's shape (gpu.getFitResolution)
local term = require("term")
local gpu = term.gpu()
if not gpu then io.stderr:write("no screen\n") return 1 end
local w, h = ...
if not w then
  local cw, ch = gpu.getResolution()
  local mw, mh = gpu.maxResolution()
  local fw, fh = gpu.getFitResolution()
  io.write(string.format("%dx%d (max %dx%d, fit %dx%d)\n", cw, ch, mw, mh, fw, fh))
  return
end
if w == "fit" then
  w, h = gpu.getFitResolution()
end
local ok, reason = pcall(gpu.setResolution, tonumber(w), tonumber(h))
if not ok then io.stderr:write(tostring(reason) .. "\n") return 1 end
term.clear()
