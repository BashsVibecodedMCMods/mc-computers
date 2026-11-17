-- Pixel graphics on the primary GPU (see docs/GRAPHICS.md). Coordinates start at 1. Colours are pixel colour
-- indices: 0-15 are the palette, 16-255 a fixed colour cube; graphics.color(0xRRGGBB) finds the nearest.
-- Pixels show where text cells are transparent, so graphics.clear() also clears the text.
local component = component
local graphics = {}

function graphics.gpu()
  local ok, term = pcall(require, "term")
  local gpu = ok and term.gpu() or nil
  return gpu or component.getPrimary("gpu")
end

function graphics.size()
  return graphics.gpu().getPixelResolution()
end

function graphics.color(rgb)
  return graphics.gpu().findPixelColor(rgb)
end

function graphics.clear(color)
  local gpu = graphics.gpu()
  gpu.clearText()
  gpu.clearPixels(color or 0)
end

function graphics.pixel(x, y, color)
  return graphics.gpu().setPixel(x, y, color)
end

function graphics.get(x, y)
  return graphics.gpu().getPixel(x, y)
end

function graphics.line(x1, y1, x2, y2, color)
  return graphics.gpu().drawLine(x1, y1, x2, y2, color)
end

function graphics.rect(x, y, w, h, color, filled)
  local gpu = graphics.gpu()
  if filled then return gpu.fillRect(x, y, w, h, color) end
  return gpu.drawRect(x, y, w, h, color)
end

function graphics.circle(x, y, r, color, filled)
  return graphics.gpu().drawCircle(x, y, r, color, filled)
end

function graphics.text(x, y, str, color, scale)
  return graphics.gpu().drawText(x, y, str, color, scale or 1)
end

--- A sprite: a w*h block of colour indices (data is a string, one byte per pixel, row by row).
function graphics.sprite(w, h, data)
  checkArg(1, w, "number")
  checkArg(2, h, "number")
  data = data or string.rep("\0", w * h)
  return {width = w, height = h, data = data}
end

function graphics.draw(sprite, x, y)
  return graphics.gpu().blit(x, y, sprite.width, sprite.height, sprite.data)
end

--- Reads a rectangle of the screen into a sprite.
function graphics.capture(x, y, w, h)
  return graphics.sprite(w, h, graphics.gpu().readPixels(x, y, w, h))
end

--- Images (.cpi): "CPI1", width and height as 2-byte big-endian numbers, then one byte per pixel.
function graphics.loadImage(path)
  local f, reason = io.open(path, "rb")
  if not f then return nil, reason end
  local data = f:read("a")
  f:close()
  if data:sub(1, 4) ~= "CPI1" then return nil, "not a CPI image" end
  local w = data:byte(5) * 256 + data:byte(6)
  local h = data:byte(7) * 256 + data:byte(8)
  return graphics.sprite(w, h, data:sub(9, 8 + w * h))
end

function graphics.saveImage(path, sprite)
  local f, reason = io.open(path, "wb")
  if not f then return nil, reason end
  f:write("CPI1", string.char(math.floor(sprite.width / 256), sprite.width % 256,
    math.floor(sprite.height / 256), sprite.height % 256), sprite.data)
  f:close()
  return true
end

return graphics
