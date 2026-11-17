-- Musical notes for computer.beep. Notes are names like "A4", "C#5" or "Bb3", or MIDI numbers (69 = A4 = 440 Hz).
local note = {}
local NAMES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"}
local OFFSETS = {C = 0, D = 2, E = 4, F = 5, G = 7, A = 9, B = 11}

--- The MIDI number of a note name or a frequency.
function note.midi(n)
  if type(n) == "number" then
    if n > 127 then return math.floor(69 + 12 * math.log(n / 440) / math.log(2) + 0.5) end
    return n
  end
  checkArg(1, n, "string", "number")
  local letter, accidental, octave = n:match("^([A-Ga-g])([#b]?)(%-?%d+)$")
  if not letter then error("bad note name: " .. n, 2) end
  local v = OFFSETS[letter:upper()] + (accidental == "#" and 1 or accidental == "b" and -1 or 0)
  return (tonumber(octave) + 1) * 12 + v
end

--- The frequency in Hz of a note name or MIDI number.
function note.freq(n)
  return 440 * 2 ^ ((note.midi(n) - 69) / 12)
end

--- The name of a MIDI number or note frequency ("A4").
function note.name(n)
  local m = note.midi(n)
  return NAMES[m % 12 + 1] .. tostring(math.floor(m / 12) - 1)
end

--- Plays a note for duration seconds (default 0.25).
function note.play(n, duration)
  computer.beep(math.max(20, math.min(2000, note.freq(n))), duration or 0.25)
end

return note
