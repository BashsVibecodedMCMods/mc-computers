-- require() and package.* for Computah OS.
local kernel = ...

package = {
  path = "/lib/?.lua;/usr/lib/?.lua;/home/lib/?.lua;./?.lua;/lib/?/init.lua;/usr/lib/?/init.lua",
  loaded = {
    _G = _G, bit32 = bit32, coroutine = coroutine, math = math, string = string, table = table, utf8 = utf8,
    unicode = unicode, computer = computer, component = component,
  },
  preload = {},
  kernel = kernel,
}

local loading = {}

local function exists(path)
  local ok, fs = pcall(function() return package.loaded.filesystem end)
  if ok and fs then return fs.exists(path) and not fs.isDirectory(path) end
  return component.invoke(kernel.bootAddress, "exists", path) and not component.invoke(kernel.bootAddress, "isDirectory", path)
end

--- Finds a module file along a path string ("?" replaced by the name, dots by slashes).
function package.searchpath(name, path)
  checkArg(1, name, "string")
  checkArg(2, path, "string")
  local tried = {}
  local file = name:gsub("%.", "/")
  for template in path:gmatch("[^;]+") do
    local candidate = template:gsub("%?", file)
    if candidate:sub(1, 2) == "./" then
      local shell = package.loaded.shell
      candidate = (shell and shell.getWorkingDirectory() or "/") .. "/" .. candidate:sub(3)
    end
    if exists(candidate) then return candidate end
    tried[#tried + 1] = "\n\tno file '" .. candidate .. "'"
  end
  return nil, table.concat(tried)
end

function require(name)
  checkArg(1, name, "string")
  local value = package.loaded[name]
  if value ~= nil then return value end
  if loading[name] then error("circular require of '" .. name .. "'", 2) end
  local loader = package.preload[name]
  local where = ":preload:"
  if not loader then
    local path, reason = package.searchpath(name, package.path)
    if not path then error("module '" .. name .. "' not found:" .. reason, 2) end
    local fn, err = kernel.loadfile(path)
    if not fn then error("error loading module '" .. name .. "' from " .. path .. ":\n" .. tostring(err), 2) end
    loader, where = fn, path
  end
  loading[name] = true
  local ok, result = pcall(loader, name, where, kernel)
  loading[name] = nil
  if not ok then error(result, 2) end
  if result == nil then result = package.loaded[name] or true end
  package.loaded[name] = result
  return result
end

package.loaded.package = package
return package
