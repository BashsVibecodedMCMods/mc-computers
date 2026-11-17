-- Converting Lua values to text and back (for saving tables or sending them over the network).
local serialization = {}

local keywords = {}
for k in ("and break do else elseif end false for function goto if in local nil not or repeat return then true until while"):gmatch("%a+") do
  keywords[k] = true
end

local function serialize(value, pretty, indent, seen)
  local t = type(value)
  if t == "number" then
    if value ~= value then return "0/0" end
    if value == math.huge then return "math.huge" end
    if value == -math.huge then return "-math.huge" end
    return tostring(value)
  elseif t == "string" then
    return string.format("%q", value):gsub("\\\n", "\\n")
  elseif t == "boolean" or t == "nil" then
    return tostring(value)
  elseif t == "table" then
    if seen[value] then error("tables with cycles are not supported", 0) end
    seen[value] = true
    local parts = {}
    local n = #value
    for i = 1, n do parts[#parts + 1] = serialize(value[i], pretty, indent .. "  ", seen) end
    local keys = {}
    for k in pairs(value) do
      if not (type(k) == "number" and k >= 1 and k <= n and k % 1 == 0) then keys[#keys + 1] = k end
    end
    table.sort(keys, function(a, b) return tostring(a) < tostring(b) end)
    for _, k in ipairs(keys) do
      local key
      if type(k) == "string" and k:match("^[%a_][%w_]*$") and not keywords[k] then
        key = k
      else
        key = "[" .. serialize(k, pretty, indent, seen) .. "]"
      end
      parts[#parts + 1] = key .. (pretty and " = " or "=") .. serialize(value[k], pretty, indent .. "  ", seen)
    end
    seen[value] = nil
    if pretty and #parts > 0 then
      return "{\n" .. indent .. "  " .. table.concat(parts, ",\n" .. indent .. "  ") .. "\n" .. indent .. "}"
    end
    return "{" .. table.concat(parts, ",") .. "}"
  end
  if pretty then return tostring(value) end
  error("cannot serialize a " .. t, 0)
end

--- Converts a value to a string. pretty = true adds line breaks and shows functions as names instead of failing.
function serialization.serialize(value, pretty)
  return serialize(value, pretty, "", {})
end

--- Converts a string made by serialize back to a value.
function serialization.unserialize(data)
  checkArg(1, data, "string")
  local fn, reason = load("return " .. data, "=data", "t", {math = {huge = math.huge}})
  if not fn then return nil, reason end
  local ok, result = pcall(fn)
  if not ok then return nil, result end
  return result
end

return serialization
