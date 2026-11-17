-- env: lists environment variables.
local vars = os.getenv()
local keys = {}
for k in pairs(vars) do keys[#keys + 1] = k end
table.sort(keys)
for _, k in ipairs(keys) do io.write(k .. "=" .. tostring(vars[k]) .. "\n") end
io.write("PWD=" .. os.getenv("PWD") .. "\n")
