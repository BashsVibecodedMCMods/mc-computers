-- The interactive shell. Type commands; Tab completes program and file names; Up/Down browse history.
local shell = require("shell")
local term = require("term")
local filesystem = require("filesystem")
local text = require("text")

local args = table.pack(...)
if args.n > 0 then
  -- sh -c "command" or sh script.sh
  if args[1] == "-c" then return shell.execute(table.concat(args, " ", 2, args.n)) end
  local f, reason = io.open(args[1], "r")
  if not f then
    io.stderr:write("sh: " .. tostring(reason) .. "\n")
    return 1
  end
  for line in f:lines() do
    if line:match("%S") and not line:match("^%s*#") then shell.execute(line) end
  end
  f:close()
  return
end

local history = {}

local function complete(line, pos)
  local before = line:sub(1, pos - 1)
  local prefix, word = before:match("^(.-)(%S*)$")
  local results = {}
  local isCommand = not prefix:match("%S")
  if isCommand and not word:find("/") then
    local seen = {}
    for dir in (os.getenv("PATH") or "/bin"):gmatch("[^:]+") do
      local path = dir == "." and shell.getWorkingDirectory() or dir
      local list = filesystem.list(path)
      if list then
        for name in list do
          local n = name:match("^(.+)%.lua$")
          if n and n:sub(1, #word) == word and not seen[n] then
            seen[n] = true
            results[#results + 1] = prefix .. n .. " " .. line:sub(pos)
          end
        end
      end
    end
    for n in pairs(shell.builtins) do
      if n:sub(1, #word) == word and not seen[n] then results[#results + 1] = prefix .. n .. " " .. line:sub(pos) end
    end
  else
    local dir, partial = word:match("^(.-)([^/]*)$")
    local list = filesystem.list(filesystem.canonical(dir == "" and "." or dir))
    if list then
      for name in list do
        if name:sub(1, #partial) == partial then results[#results + 1] = prefix .. dir .. name .. line:sub(pos) end
      end
    end
  end
  table.sort(results)
  return results
end

while true do
  local gpu = term.gpu()
  local prompt = (os.getenv("PS1") or "$PWD# "):gsub("%$PWD", shell.getWorkingDirectory()):gsub("%$HOSTNAME", os.hostname())
  if gpu and term.isAvailable() then
    local x = term.getCursor()
    if x > 1 then io.write("\n") end
    local old = gpu.setForeground(0x55FF55)
    io.write(prompt)
    gpu.setForeground(old)
  else
    io.write(prompt)
  end
  local line = term.isAvailable() and term.read({history = history, complete = complete}) or io.read("l")
  if line == nil then
    if not term.isAvailable() then
      -- Headless: wait for a screen to appear instead of spinning.
      require("event").pull("component_available")
    else
      return
    end
  else
    line = text.trim(line)
    if line ~= "" then
      local ok, err = pcall(shell.execute, line)
      if not ok then
        if type(err) == "table" and err.exit ~= nil then return err.exit end
        io.stderr:write(tostring(err) .. "\n")
      end
    end
  end
end
