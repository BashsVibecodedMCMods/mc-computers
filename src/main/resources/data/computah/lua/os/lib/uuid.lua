-- Random UUIDs (version 4), e.g. for message ids.
local uuid = {}

function uuid.next()
  local hex = "0123456789abcdef"
  local out = {}
  for i = 1, 36 do
    if i == 9 or i == 14 or i == 19 or i == 24 then
      out[i] = "-"
    elseif i == 15 then
      out[i] = "4"
    elseif i == 20 then
      local v = math.random(8, 11)
      out[i] = hex:sub(v + 1, v + 1)
    else
      local v = math.random(0, 15)
      out[i] = hex:sub(v + 1, v + 1)
    end
  end
  return table.concat(out)
end

return uuid
