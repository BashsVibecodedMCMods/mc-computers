-- Key codes (GLFW key codes, as sent in key_down/key_up signals) and modifier state.
local event = require("event")
local keyboard = {pressed = {}}

keyboard.keys = {
  space = 32, apostrophe = 39, comma = 44, minus = 45, period = 46, slash = 47,
  ["0"] = 48, ["1"] = 49, ["2"] = 50, ["3"] = 51, ["4"] = 52, ["5"] = 53, ["6"] = 54, ["7"] = 55, ["8"] = 56, ["9"] = 57,
  semicolon = 59, equals = 61,
  a = 65, b = 66, c = 67, d = 68, e = 69, f = 70, g = 71, h = 72, i = 73, j = 74, k = 75, l = 76, m = 77,
  n = 78, o = 79, p = 80, q = 81, r = 82, s = 83, t = 84, u = 85, v = 86, w = 87, x = 88, y = 89, z = 90,
  lbracket = 91, backslash = 92, rbracket = 93, grave = 96,
  escape = 256, enter = 257, tab = 258, back = 259, insert = 260, delete = 261,
  right = 262, left = 263, down = 264, up = 265, pageUp = 266, pageDown = 267, home = 268, ["end"] = 269,
  capital = 280, scroll = 281, numlock = 282, pause = 284,
  f1 = 290, f2 = 291, f3 = 292, f4 = 293, f5 = 294, f6 = 295, f7 = 296, f8 = 297, f9 = 298, f10 = 299, f11 = 300, f12 = 301,
  numpad0 = 320, numpad1 = 321, numpad2 = 322, numpad3 = 323, numpad4 = 324, numpad5 = 325, numpad6 = 326,
  numpad7 = 327, numpad8 = 328, numpad9 = 329, numpaddecimal = 330, numpaddiv = 331, numpadmul = 332,
  numpadsub = 333, numpadadd = 334, numpadenter = 335,
  lshift = 340, lcontrol = 341, lmenu = 342, rshift = 344, rcontrol = 345, rmenu = 346,
}
for name, code in pairs(keyboard.keys) do
  if type(name) == "string" and keyboard.keys[code] == nil then keyboard.keys[code] = name end
end

function keyboard.isKeyDown(code)
  if type(code) == "string" then code = keyboard.keys[code] end
  return keyboard.pressed[code] == true
end

function keyboard.isControlDown()
  return keyboard.isKeyDown(341) or keyboard.isKeyDown(345)
end

function keyboard.isShiftDown()
  return keyboard.isKeyDown(340) or keyboard.isKeyDown(344)
end

function keyboard.isAltDown()
  return keyboard.isKeyDown(342) or keyboard.isKeyDown(346)
end

--- Whether a character is printable (not a control character).
function keyboard.isControl(char)
  return type(char) == "number" and (char < 32 or (char >= 127 and char < 160))
end

event.listen("key_down", function(_, _, _, code)
  if code and code > 0 then keyboard.pressed[code] = true end
end)
event.listen("key_up", function(_, _, _, code)
  if code then keyboard.pressed[code] = nil end
end)

return keyboard
