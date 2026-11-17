-- String helpers.
local unicode = unicode
local text = {}

function text.trim(s)
  return (s:gsub("^%s+", ""):gsub("%s+$", ""))
end

--- Splits on a separator pattern (default whitespace runs). Empty parts are dropped unless keepEmpty.
function text.split(s, separator, keepEmpty)
  local parts = {}
  if not separator then
    for word in s:gmatch("%S+") do parts[#parts + 1] = word end
    return parts
  end
  local start = 1
  while true do
    local i, j = s:find(separator, start)
    if not i then break end
    local part = s:sub(start, i - 1)
    if keepEmpty or part ~= "" then parts[#parts + 1] = part end
    start = j + 1
  end
  local rest = s:sub(start)
  if keepEmpty or rest ~= "" then parts[#parts + 1] = rest end
  return parts
end

--- Splits a command line into words, honouring quotes.
function text.tokenize(s)
  local words, current, quote = {}, nil, nil
  for c in s:gmatch(".") do
    if quote then
      if c == quote then quote = nil else current = (current or "") .. c end
    elseif c == '"' or c == "'" then
      quote = c
      current = current or ""
    elseif c:match("%s") then
      if current then words[#words + 1] = current end
      current = nil
    else
      current = (current or "") .. c
    end
  end
  if current then words[#words + 1] = current end
  return words
end

function text.padRight(s, n)
  s = tostring(s)
  local len = unicode.len(s)
  return len < n and s .. string.rep(" ", n - len) or s
end

function text.padLeft(s, n)
  s = tostring(s)
  local len = unicode.len(s)
  return len < n and string.rep(" ", n - len) .. s or s
end

function text.detab(s, tab)
  tab = tab or 4
  return (s:gsub("([^\n]-)\t", function(before)
    return before .. string.rep(" ", tab - unicode.len(before) % tab)
  end))
end

--- Wraps text to a width; returns a list of lines.
function text.wrap(s, width)
  local lines = {}
  for raw in (s .. "\n"):gmatch("(.-)\n") do
    local line = ""
    for word in raw:gmatch("%S+") do
      if line == "" then
        line = word
      elseif unicode.len(line) + 1 + unicode.len(word) <= width then
        line = line .. " " .. word
      else
        lines[#lines + 1] = line
        line = word
      end
      while unicode.len(line) > width do
        lines[#lines + 1] = unicode.sub(line, 1, width)
        line = unicode.sub(line, width + 1)
      end
    end
    lines[#lines + 1] = line
  end
  return lines
end

--- Iterates over the lines of text wrapped to width (maxWidth is accepted for OpenOS programs and ignored).
function text.wrappedLines(value, width, maxWidth)
  local lines = text.wrap(value, width)
  local i = 0
  return function()
    i = i + 1
    return lines[i]
  end
end

--- Removes ANSI escape sequences (colours) from a string.
function text.removeEscapes(s)
  return (s:gsub("\27%[[%d;]*%a", ""))
end

--- Human-readable byte counts.
function text.size(n)
  if n >= 1024 * 1024 then return string.format("%.1fM", n / 1024 / 1024) end
  if n >= 1024 then return string.format("%.1fK", n / 1024) end
  return tostring(math.floor(n))
end

return text
