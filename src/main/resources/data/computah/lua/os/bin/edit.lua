-- edit [-r] <file>: a full-screen text editor.
-- Keys: arrows, Home/End, PageUp/PageDown move; Ctrl+S saves; Ctrl+Q quits (twice if unsaved);
-- Ctrl+F finds (Ctrl+F again for the next match); Ctrl+G goes to a line; Ctrl+K deletes the line;
-- Ctrl+D duplicates it. Paste with Ctrl+Shift+V.
local shell = require("shell")
local term = require("term")
local event = require("event")
local keyboard = require("keyboard")
local text = require("text")
local filesystem = require("filesystem")
local unicode = unicode

local args, options = shell.parse(...)
if #args == 0 then
  io.write("usage: edit [-r] <file>\n")
  return 1
end
if not term.isAvailable() then
  io.stderr:write("edit needs a screen\n")
  return 1
end
local path = filesystem.canonical(args[1])
local readonly = options.r or filesystem.isReadOnly(path)
if filesystem.isDirectory(path) then
  io.stderr:write("edit: " .. args[1] .. " is a directory\n")
  return 1
end

local lines = {}
if filesystem.exists(path) then
  local f = io.open(path, "r")
  for line in f:lines() do lines[#lines + 1] = text.detab(line, 2) end
  f:close()
end
if #lines == 0 then lines[1] = "" end

local gpu = term.gpu()
local w, h = term.getViewport()
local rows = h - 1
local cx, cy = 1, 1          -- cursor: character column and line number
local sx, sy = 0, 0          -- scroll offsets
local dirty = false
local status = readonly and "read-only" or "Ctrl+S save  Ctrl+Q quit  Ctrl+F find  Ctrl+G go to line"
local quitArmed = false
local lastFind
local running = true

local function len(i)
  return unicode.len(lines[i])
end

local function setColors(fg, bg)
  gpu.setForeground(fg)
  gpu.setBackground(bg)
end

local function drawLine(row)
  local i = sy + row
  local s = lines[i]
  setColors(0xFFFFFF, 0x000000)
  if s then
    gpu.set(1, row, text.padRight(unicode.sub(s, sx + 1, sx + w), w))
  else
    gpu.set(1, row, text.padRight("~", w))
  end
end

local function drawStatus()
  setColors(0x000000, 0xCCCCCC)
  local info = string.format(" %s%s  %d:%d ", filesystem.name(path) or path, dirty and " [+]" or "", cy, cx)
  local left = " " .. status
  local room = w - unicode.len(info)
  gpu.set(1, h, text.padRight(unicode.sub(left, 1, math.max(0, room)), room) .. info)
  setColors(0xFFFFFF, 0x000000)
end

local function drawAll()
  for row = 1, rows do drawLine(row) end
  drawStatus()
end

local function cursorCell(on)
  local x, y = cx - sx, cy - sy
  if x < 1 or x > w or y < 1 or y > rows then return end
  local ch = unicode.sub(lines[cy], cx, cx)
  if ch == "" then ch = " " end
  if on then setColors(0x000000, 0xFFFFFF) else setColors(0xFFFFFF, 0x000000) end
  gpu.set(x, y, ch)
  setColors(0xFFFFFF, 0x000000)
end

--- Keeps the cursor on screen; returns true if the view scrolled.
local function scrollToCursor()
  cy = math.max(1, math.min(#lines, cy))
  cx = math.max(1, math.min(len(cy) + 1, cx))
  local moved = false
  if cy - sy < 1 then sy = cy - 1 moved = true end
  if cy - sy > rows then sy = cy - rows moved = true end
  if cx - sx < 1 then sx = math.max(0, cx - 8) moved = true end
  if cx - sx > w then sx = cx - w + 8 moved = true end
  return moved
end

local function save()
  if readonly then
    status = "file is read-only"
    return
  end
  local f, reason = io.open(path, "w")
  if not f then
    status = "save failed: " .. tostring(reason)
    return
  end
  for i, line in ipairs(lines) do
    f:write(line)
    if i < #lines then f:write("\n") end
  end
  f:write("\n")
  f:close()
  dirty = false
  status = "saved " .. #lines .. " lines"
end

local function prompt(label)
  setColors(0x000000, 0xCCCCCC)
  gpu.set(1, h, text.padRight(label, w))
  term.setCursor(unicode.len(label) + 1, h)
  local value = term.read({nobreak = true})
  setColors(0xFFFFFF, 0x000000)
  return value
end

local function find(needle)
  if not needle or needle == "" then return end
  lastFind = needle
  for n = 0, #lines do
    local i = (cy - 1 + n) % #lines + 1
    local start = (n == 0) and cx + 1 or 1
    local a = lines[i]:find(needle, start, true)
    if a then
      cy = i
      cx = unicode.len(lines[i]:sub(1, a - 1)) + 1
      status = "found '" .. needle .. "'"
      return
    end
  end
  status = "'" .. needle .. "' not found"
end

local function insertText(str)
  if readonly then return end
  local first = true
  for part in (str .. "\n"):gmatch("([^\n]*)\n") do
    if not first then
      local rest = unicode.sub(lines[cy], cx)
      lines[cy] = unicode.sub(lines[cy], 1, cx - 1)
      table.insert(lines, cy + 1, rest)
      cy, cx = cy + 1, 1
    end
    first = false
    local line = lines[cy]
    lines[cy] = unicode.sub(line, 1, cx - 1) .. part .. unicode.sub(line, cx)
    cx = cx + unicode.len(part)
  end
  dirty = true
end

local keys = keyboard.keys
local function onKey(char, code)
  local full = false
  local ctrl = keyboard.isControlDown()
  if code ~= keys.q then quitArmed = false end
  if ctrl and code == keys.s then
    save()
  elseif ctrl and code == keys.q then
    if dirty and not quitArmed then
      status = "unsaved changes! Ctrl+Q again to quit"
      quitArmed = true
    else
      running = false
      return
    end
  elseif ctrl and code == keys.f then
    if lastFind and status:match("^found") then find(lastFind) else find(prompt("Find: ")) end
    full = true
  elseif ctrl and code == keys.g then
    local n = tonumber(prompt("Go to line: ") or "")
    if n then cy, cx = n, 1 end
    full = true
  elseif ctrl and code == keys.k and not readonly then
    if #lines > 1 then table.remove(lines, cy) else lines[1] = "" end
    dirty, full = true, true
  elseif ctrl and code == keys.d and not readonly then
    table.insert(lines, cy + 1, lines[cy])
    cy = cy + 1
    dirty, full = true, true
  elseif code == keys.up then
    cy = cy - 1
  elseif code == keys.down then
    cy = cy + 1
  elseif code == keys.left then
    if cx > 1 then cx = cx - 1 elseif cy > 1 then cy, cx = cy - 1, math.huge end
  elseif code == keys.right then
    if cx <= len(cy) then cx = cx + 1 elseif cy < #lines then cy, cx = cy + 1, 1 end
  elseif code == keys.home then
    cx = 1
  elseif code == keys["end"] then
    cx = len(cy) + 1
  elseif code == keys.pageUp then
    cy = cy - rows
  elseif code == keys.pageDown then
    cy = cy + rows
  elseif readonly then
    status = "file is read-only"
  elseif code == keys.enter or char == 13 or code == keys.numpadenter then
    local line = lines[cy]
    local indent = line:match("^%s*")
    lines[cy] = unicode.sub(line, 1, cx - 1)
    table.insert(lines, cy + 1, indent .. unicode.sub(line, cx))
    cy, cx = cy + 1, unicode.len(indent) + 1
    dirty, full = true, true
  elseif code == keys.back or char == 8 then
    if cx > 1 then
      local line = lines[cy]
      lines[cy] = unicode.sub(line, 1, cx - 2) .. unicode.sub(line, cx)
      cx = cx - 1
      dirty = true
    elseif cy > 1 then
      cx = len(cy - 1) + 1
      lines[cy - 1] = lines[cy - 1] .. lines[cy]
      table.remove(lines, cy)
      cy = cy - 1
      dirty, full = true, true
    end
  elseif code == keys.delete then
    local line = lines[cy]
    if cx <= len(cy) then
      lines[cy] = unicode.sub(line, 1, cx - 1) .. unicode.sub(line, cx + 1)
      dirty = true
    elseif cy < #lines then
      lines[cy] = line .. lines[cy + 1]
      table.remove(lines, cy + 1)
      dirty, full = true, true
    end
  elseif code == keys.tab or char == 9 then
    insertText("  ")
  elseif char and char >= 32 and not keyboard.isControl(char) then
    insertText(unicode.char(char))
  end
  return full
end

term.clear()
drawAll()
while running do
  cursorCell(true)
  local name, address, char, code, extra = event.pull()
  cursorCell(false)
  local oldY = cy
  local full = false
  if name == "key_down" then
    full = onKey(char, code)
  elseif name == "clipboard" then
    insertText(tostring(char):gsub("\r", ""))
    full = true
  elseif name == "touch" and code then
    cy, cx = sy + math.floor(code), sx + math.floor(char)
  elseif name == "scroll" then
    sy = math.max(0, math.min(#lines - 1, sy - (extra or 0) * 3))
    full = true
  elseif name == "interrupted" then
    break
  end
  if not running then break end
  if scrollToCursor() or full then
    drawAll()
  else
    if oldY ~= cy and oldY - sy >= 1 and oldY - sy <= rows then drawLine(oldY - sy) end
    drawLine(cy - sy)
    drawStatus()
  end
end
term.clear()
