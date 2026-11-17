-- OS additions to the computer library. The raw functions (computer.uptime, computer.beep, ...) are the machine's
-- own and stay as they are; inside the OS computer.pullSignal waits through the scheduler (see process.lua).
local computer = computer

--- The OS run level: always 1 once the OS has booted (OpenOS uses "S" during boot and 1 after).
function computer.runlevel()
  return 1
end

--- Where programs come from, for "program not found" hints. Computah OS has no program locations; returns {}.
function computer.getProgramLocations()
  return {}
end

return computer
