-- The terminal: text output with a cursor, wrapping and scrolling on the primary GPU and screen, and line input
-- from the keyboards attached to that screen. term.write understands \n, \r, \t, \b and ANSI colour codes
-- (ESC[...m: 0 reset, 1 bold, 4 underline, 7 inverse, 30-37/90-97 foreground, 40-47/100-107 background).
local component, unicode, computer = component, unicode, computer
local event = require("event")
local keyboard = require("keyboard")

local term = {}
local gpu, screenAddress
local w, h = 50, 16
local cx, cy = 1, 1
local defaultFg, defaultBg = 0xFFFFFF, 0x000000
local style = {}
local cursorBlink = true

local ANSI = {[0] = 0x000000, 0xCC3333, 0x33CC33, 0xCCCC33, 0x3366FF, 0xCC33CC, 0x33CCCC, 0xCCCCCC}
local ANSI_BRIGHT = {[0] = 0x666666, 0xFF5555, 0x55FF55, 0xFFFF55, 0x6699FF, 0xFF55FF, 0x55FFFF, 0xFFFFFF}

--- Binds the terminal to a GPU and screen (by default the primary ones).
function term.bind(gpuAddress, screen)
  gpu, screenAddress = nil, nil
  gpuAddress = gpuAddress or component.list("gpu", true)()
  screen = screen or component.list("screen", true)()
  if not gpuAddress or not screen then return false end
  gpu = component.proxy(gpuAddress)
  screenAddress = screen
  gpu.bind(screen)
  w, h = gpu.maxResolution()
  gpu.setResolution(w, h)
  gpu.setForeground(defaultFg)
  gpu.setBackground(defaultBg)
  gpu.fill(1, 1, w, h, " ")
  cx, cy = 1, 1
  return true
end

function term.isAvailable()
  return gpu ~= nil and component.type(gpu.address) == "gpu" and component.type(screenAddress) == "screen"
end

function term.gpu()
  return gpu
end

function term.screen()
  return screenAddress
end

--- Keyboards attached to the terminal's screen.
function term.keyboards()
  if not screenAddress then return {} end
  local ok, list = pcall(component.invoke, screenAddress, "getKeyboards")
  return ok and list or {}
end

function term.getViewport()
  if gpu then w, h = gpu.getResolution() end
  return w, h
end

function term.getCursor()
  return cx, cy
end

function term.setCursor(x, y)
  checkArg(1, x, "number")
  checkArg(2, y, "number")
  cx, cy = math.floor(x), math.floor(y)
end

function term.setCursorBlink(enabled)
  cursorBlink = enabled and true or false
end

function term.clear()
  if term.isAvailable() then
    w, h = gpu.getResolution()
    gpu.fill(1, 1, w, h, " ")
  end
  cx, cy = 1, 1
end

function term.clearLine()
  if term.isAvailable() then gpu.fill(1, cy, w, 1, " ") end
  cx = 1
end

function term.scroll(n)
  n = n or 1
  if term.isAvailable() and n > 0 then
    if n < h then gpu.copy(1, 1 + n, w, h - n, 0, -n) end
    gpu.fill(1, math.max(1, h - n + 1), w, math.min(n, h), " ")
  end
end

local function newline()
  cx = 1
  cy = cy + 1
  if cy > h then
    term.scroll(cy - h)
    cy = h
  end
end

local function put(text, wrap)
  while #text > 0 do
    if cx > w then
      if not wrap then return end
      newline()
    end
    local room = w - cx + 1
    local n = unicode.len(text)
    if n <= room then
      if gpu then gpu.set(cx, cy, text) end
      cx = cx + n
      return
    end
    if gpu then gpu.set(cx, cy, unicode.sub(text, 1, room)) end
    cx = cx + room
    if not wrap then return end
    text = unicode.sub(text, room + 1)
  end
end

local function sgr(params)
  if not gpu then return end
  if params == "" then params = "0" end
  for code in params:gmatch("%d+") do
    code = tonumber(code)
    if code == 0 then
      gpu.setForeground(defaultFg)
      gpu.setBackground(defaultBg)
      style = {}
      gpu.setStyle(style)
    elseif code == 1 or code == 3 or code == 4 or code == 5 or code == 7 or code == 9 then
      local key = ({[1] = "bold", [3] = "italic", [4] = "underline", [5] = "blink", [7] = "inverse", [9] = "strikethrough"})[code]
      style[key] = true
      gpu.setStyle(style)
    elseif code >= 30 and code <= 37 then
      gpu.setForeground(ANSI[code - 30])
    elseif code >= 90 and code <= 97 then
      gpu.setForeground(ANSI_BRIGHT[code - 90])
    elseif code == 39 then
      gpu.setForeground(defaultFg)
    elseif code >= 40 and code <= 47 then
      gpu.setBackground(ANSI[code - 40])
    elseif code >= 100 and code <= 107 then
      gpu.setBackground(ANSI_BRIGHT[code - 100])
    elseif code == 49 then
      gpu.setBackground(defaultBg)
    end
  end
end

--- Writes text at the cursor, wrapping at the right edge unless wrap is false.
function term.write(value, wrap)
  value = tostring(value)
  if wrap == nil then wrap = true end
  if gpu and not term.isAvailable() then gpu = nil end
  w, h = term.getViewport()
  local i = 1
  local len = #value
  while i <= len do
    local s, e = value:find("[%c]", i)
    if not s then
      put(value:sub(i), wrap)
      break
    end
    if s > i then put(value:sub(i, s - 1), wrap) end
    local c = value:sub(s, s)
    if c == "\n" then
      newline()
    elseif c == "\r" then
      cx = 1
    elseif c == "\t" then
      local spaces = 4 - ((cx - 1) % 4)
      put(string.rep(" ", spaces), wrap)
    elseif c == "\b" then
      cx = math.max(1, cx - 1)
    elseif c == "\27" and value:sub(s + 1, s + 1) == "[" then
      local params, final, stop = value:match("^([%d;]*)(%a)()", s + 2)
      if final then
        if final == "m" then sgr(params) end
        e = stop - 1
      end
    end
    i = e + 1
  end
end

local function cell(x, y)
  if not gpu or x < 1 or y < 1 or x > w or y > h then return nil end
  local ok, ch, fg, bg = pcall(gpu.get, x, y)
  if not ok then return nil end
  return ch, fg, bg
end

-- The cell under the cursor before it was drawn, so hiding the cursor puts back exactly what was there.
local underCursor

local function drawCursor(on, x, y)
  if on then
    if underCursor then return end
    local ch, fg, bg = cell(x, y)
    if not ch then return end
    underCursor = {x = x, y = y, ch = ch, fg = fg, bg = bg}
    local ofg, obg = gpu.getForeground(), gpu.getBackground()
    gpu.setForeground(bg == -1 and 0 or bg)
    gpu.setBackground(fg)
    gpu.set(x, y, ch)
    gpu.setForeground(ofg)
    gpu.setBackground(obg)
  elseif underCursor then
    local c = underCursor
    underCursor = nil
    if not gpu then return end
    local ofg, obg = gpu.getForeground(), gpu.getBackground()
    gpu.setForeground(c.fg)
    gpu.setBackground(c.bg)
    gpu.set(c.x, c.y, c.ch)
    gpu.setForeground(ofg)
    gpu.setBackground(obg)
  end
end

local function fromOurKeyboard(address)
  local list = term.keyboards()
  if #list == 0 then return true end
  for _, k in ipairs(list) do
    if k == address then return true end
  end
  return false
end

--- Reads a line from the keyboard with editing (arrows, Home/End, Backspace/Delete), history (Up/Down) and
--- tab completion. options: history (table), complete (function(line, pos) -> list of completions),
--- pwchar (character to show instead of input), nobreak (don't write a newline at the end).
--- Returns the line, or nil on Ctrl+D with an empty line. Ctrl+C returns "" after showing ^C.
function term.read(options)
  options = type(options) == "table" and options or {}
  if not term.isAvailable() then
    return nil
  end
  local history = options.history or {}
  local histIndex = #history + 1
  local chars = {}
  local pos = 1
  local startX, startY = cx, cy
  local drawn = 0
  local cursorOn = false

  local function locate(index)
    local offset = startX - 1 + index - 1
    return offset % w + 1, startY + math.floor(offset / w)
  end

  local function text()
    if options.pwchar then return string.rep(options.pwchar, #chars) end
    return table.concat(chars)
  end

  local function redraw()
    local s = text()
    local n = math.max(#chars, drawn)
    local _, lastY = locate(n + 1)
    if lastY > h then
      term.scroll(lastY - h)
      startY = startY - (lastY - h)
    end
    cx, cy = startX, startY
    term.write(s .. string.rep(" ", math.max(0, drawn - #chars)))
    drawn = #chars
    cx, cy = locate(pos)
  end

  local function setLine(str)
    chars = {}
    for i = 1, unicode.len(str) do chars[i] = unicode.sub(str, i, i) end
    pos = #chars + 1
  end

  local function insert(str)
    for i = 1, unicode.len(str) do
      table.insert(chars, pos, unicode.sub(str, i, i))
      pos = pos + 1
    end
  end

  local completions, completionIndex
  while true do
    local cxp, cyp = locate(pos)
    if cursorBlink then
      cursorOn = not cursorOn
    else
      cursorOn = true
    end
    drawCursor(cursorOn, cxp, cyp)
    local name, address, char, code = event.pull(0.5)
    drawCursor(false, cxp, cyp)
    if name == "key_down" and fromOurKeyboard(address) then
      cursorOn = false
      if code ~= 258 then completions = nil end
      if char == 13 or code == 257 or code == 335 then
        cx, cy = locate(#chars + 1)
        if not options.nobreak then newline() end
        local line = table.concat(chars)
        if line ~= "" and line ~= history[#history] and options.history then
          history[#history + 1] = line
          if #history > 50 then table.remove(history, 1) end
        end
        return line
      elseif char == 3 then
        cx, cy = locate(#chars + 1)
        term.write("^C")
        newline()
        return ""
      elseif char == 4 and #chars == 0 then
        newline()
        return nil
      elseif code == 259 or char == 8 then
        if pos > 1 then
          table.remove(chars, pos - 1)
          pos = pos - 1
        end
      elseif code == 261 then
        if pos <= #chars then table.remove(chars, pos) end
      elseif code == 263 then
        pos = math.max(1, pos - 1)
      elseif code == 262 then
        pos = math.min(#chars + 1, pos + 1)
      elseif code == 268 or char == 1 then
        pos = 1
      elseif code == 269 or char == 5 then
        pos = #chars + 1
      elseif code == 265 then
        if histIndex > 1 then
          histIndex = histIndex - 1
          setLine(history[histIndex])
        end
      elseif code == 264 then
        if histIndex <= #history then
          histIndex = histIndex + 1
          setLine(history[histIndex] or "")
        end
      elseif code == 258 or char == 9 then
        if options.complete then
          if not completions then
            completions = options.complete(table.concat(chars), pos) or {}
            completionIndex = 0
            completions.base = table.concat(chars)
          end
          if #completions > 0 then
            completionIndex = completionIndex % #completions + 1
            setLine(completions[completionIndex])
          end
        end
      elseif char and char >= 32 and not keyboard.isControl(char) then
        insert(unicode.char(char))
      end
      redraw()
    elseif name == "clipboard" and fromOurKeyboard(address) then
      local str = tostring(char):gsub("\r", ""):match("^[^\n]*")
      insert(str)
      redraw()
    elseif name == "screen_resized" then
      w, h = term.getViewport()
    end
  end
end

-- Bind to the primary GPU and screen now, and again when they appear later.
term.bind()
event.listen("component_added", function(_, _, ctype)
  if (ctype == "gpu" or ctype == "screen") and not term.isAvailable() then term.bind() end
end)

return term
