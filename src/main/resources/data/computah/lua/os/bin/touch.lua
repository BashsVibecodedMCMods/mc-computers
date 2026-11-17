-- touch <files...>: creates empty files if they don't exist.
local filesystem = require("filesystem")
for _, p in ipairs({...}) do
  local path = filesystem.canonical(p)
  if not filesystem.exists(path) then
    local f, reason = io.open(path, "w")
    if f then f:close() else io.stderr:write("touch: " .. p .. ": " .. tostring(reason) .. "\n") end
  end
end
