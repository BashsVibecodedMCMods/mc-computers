-- Side numbers for redstone and other side-based methods. They match Minecraft's directions. The relative names
-- (front, back, left, right) are kept for OpenComputers programs and mean north, south, east and west.
local sides = {
  [0] = "bottom", [1] = "top", [2] = "back", [3] = "front", [4] = "right", [5] = "left",
  bottom = 0, down = 0, negy = 0,
  top = 1, up = 1, posy = 1,
  north = 2, back = 2, negz = 2,
  south = 3, front = 3, posz = 3, forward = 3,
  west = 4, right = 4, negx = 4,
  east = 5, left = 5, posx = 5,
}
return sides
