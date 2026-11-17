-- An example service. Enable it with `rc example enable`, start it now with `rc example start`.
-- It runs a detached thread that beeps every `interval` seconds (set args in /etc/rc.cfg: example = {interval = 10}).
local thread = require("thread")
local worker

function start()
  local interval = (args and args.interval) or 30
  worker = thread.create(function()
    while true do
      os.sleep(interval)
      computer.beep(880, 0.05)
    end
  end)
  worker:detach()
end

function stop()
  if worker then worker:kill() end
  worker = nil
end

function status()
  return worker and worker:status() ~= "dead" and "running" or "stopped"
end
