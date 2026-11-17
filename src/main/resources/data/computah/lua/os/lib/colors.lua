-- Names for the 16 palette entries (use with gpu.setForeground(colors.red, true)) and common RGB colours.
local colors = {
  black = 0, darkgray = 1, gray = 3, silver = 6, lightgray = 7, white = 9,
  red = 10, green = 11, blue = 12, yellow = 13, purple = 14, cyan = 15,
  transparent = -1,
}
colors.rgb = {
  black = 0x000000, white = 0xFFFFFF, red = 0xFF5555, green = 0x55FF55, blue = 0x5555FF, yellow = 0xFFFF55,
  cyan = 0x55FFFF, magenta = 0xFF55FF, orange = 0xFFAA00, gray = 0x888888, darkgray = 0x444444,
}
return colors
