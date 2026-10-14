"""The Mineblox Royale icon (creator: a brand-new icon for the creative tab, also used in the store pictures when it's
published): a gold crown (the Victory Royale) over a grass block, on a purple storm badge.

Drawn from one set of shapes in a 32 x 32 grid:
  - large smooth copies for the store pictures (Branding\\Mineblox Royale icon <size>.png);
  - the item texture: the same shrunk to 32 x 32 with hard edges.

  python fabric/tools/branding/make_icon.py
"""
import math
import os

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
TEXTURE = os.path.join(ROOT, "fabric", "src", "main", "resources", "assets", "minebloxroyale", "textures", "item", "icon.png")
BRANDING = os.path.join(ROOT, "Branding")

# Colours.
RING_DARK = (34, 10, 66)
STORM_IN = (150, 72, 235)
STORM_OUT = (74, 22, 148)
GRASS_TOP = (104, 196, 64)
GRASS_LIGHT = (150, 226, 96)
GRASS_SIDE = (74, 158, 46)
DIRT_LEFT = (150, 98, 52)
DIRT_RIGHT = (112, 70, 36)
DIRT_DARK = (86, 52, 26)
OUTLINE = (24, 16, 30)
GOLD = (255, 200, 40)
GOLD_LIGHT = (255, 236, 140)
GOLD_DARK = (196, 128, 16)
RUBY = (230, 40, 60)
SAPPHIRE = (40, 150, 255)

# Shapes, in the 32 x 32 grid (x right, y down).
CUBE_TOP = [(16, 11), (25, 15.5), (16, 20), (7, 15.5)]
CUBE_LEFT = [(7, 15.5), (16, 20), (16, 29), (7, 24.5)]
CUBE_RIGHT = [(16, 20), (25, 15.5), (25, 24.5), (16, 29)]


def grass_band(x0, y0, slope):
    """The grass hanging over one side's dirt: the side's top edge, then a jagged bottom edge (drips)."""
    top = [(x0, y0), (x0 + 9, y0 + 9 * slope)]
    bottom = []
    for i in range(10):
        x = x0 + 9 - i
        bottom.append((x, y0 + (x - x0) * slope + (3.4 if i % 3 == 1 else 2.2)))
    return top + bottom + [(x0, y0 + 2.2)]


GRASS_LEFT = grass_band(7, 15.5, 0.5)
GRASS_RIGHT = grass_band(16, 20, -0.5)
CROWN = [(8, 11), (8, 4.5), (11.5, 8), (16, 2), (20.5, 8), (24, 4.5), (24, 11)]
CROWN_BAND = [(8, 9), (24, 9), (24, 11), (8, 11)]
JEWELS = [((16, 7), RUBY), ((11, 9.9), SAPPHIRE), ((21, 9.9), SAPPHIRE)]


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def pixel_icon():
    """32 x 32 for the game: the emblem drawn for that size and shrunk, with hard edges (every pixel solid or clear),
    like the other item icons drawn from their 3D models."""
    small = smooth_icon(256, small=True).resize((32, 32), Image.BOX)
    px = small.load()
    for y in range(32):
        for x in range(32):
            r, g, b, a = px[x, y]
            px[x, y] = (r, g, b, 255) if a >= 110 else (0, 0, 0, 0)
    return small


def smooth_icon(size, small=False):
    """The emblem, smooth and shaded, for the store pictures. small: drawn for shrinking to 32 x 32 (one-pixel
    outlines, no fine shading that would only blur)."""
    ss = 8 if small else 4
    n = size * ss
    k = n / 32

    def pts(poly, dx=0.0, dy=0.0):
        return [((x + dx) * k, (y + dy) * k) for x, y in poly]

    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    # Badge: radial shading, a dark rim and a faint inner ring.
    badge = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    bp = badge.load()
    c = n / 2
    for y in range(n):
        for x in range(n):
            d = math.hypot(x - c, y - c) / k
            if d <= 15.6:
                if d > 14.4:
                    bp[x, y] = (*RING_DARK, 255)
                else:
                    t = min(1, d / 14.4)
                    col = lerp(STORM_IN, STORM_OUT, t ** 1.3)
                    swirl = 0 if small else 0.06 * math.sin(math.atan2(y - c, x - c) * 3 + d * 0.9)  # storm swirl
                    bp[x, y] = (*lerp(col, (255, 255, 255), max(0, swirl)), 255)
    img.alpha_composite(badge)
    d = ImageDraw.Draw(img)
    if not small:
        d.ellipse((c - 13.6 * k, c - 13.6 * k, c + 13.6 * k, c + 13.6 * k), outline=(190, 140, 255, 90), width=max(1, round(0.35 * k)))
    # Shadow under the emblem.
    shadow = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    for poly in (CUBE_TOP, CUBE_LEFT, CUBE_RIGHT, CROWN):
        sd.polygon(pts(poly, 0.6, 0.8), fill=(10, 0, 30, 140))
    if not small:
        img.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(0.8 * k)))
    d = ImageDraw.Draw(img)
    ow = round(1.0 * k) if small else max(2, round(0.55 * k))
    for poly, colour in ((CUBE_LEFT, DIRT_LEFT), (CUBE_RIGHT, DIRT_RIGHT), (GRASS_LEFT, GRASS_SIDE), (GRASS_RIGHT, GRASS_SIDE), (CUBE_TOP, GRASS_TOP)):
        d.polygon(pts(poly), fill=colour)
    # Specks in the dirt, a lit edge on top.
    for x, y in ((9, 21), (12, 25), (10, 24), (19, 22), (22, 20), (21, 25), (13.5, 22.5), (23, 23)):
        d.rectangle((x * k, y * k, (x + 0.9) * k, (y + 0.9) * k), fill=DIRT_DARK)
    d.line(pts([(7.4, 15.5), (16, 11.2), (24.6, 15.5)]), fill=GRASS_LIGHT, width=max(1, round(0.5 * k)))
    for poly in (CUBE_TOP, CUBE_LEFT, CUBE_RIGHT):
        d.polygon(pts(poly), outline=OUTLINE, width=ow)
    # Crown: gold with a lit left side, a darker band, jewels with a glint.
    d.polygon(pts(CROWN), fill=GOLD, outline=OUTLINE, width=ow)
    d.polygon(pts([(8.6, 10.4), (8.6, 6), (11.5, 8.9), (16, 3.4), (16, 9)]), fill=GOLD_LIGHT)
    d.polygon(pts(CROWN_BAND), fill=GOLD_DARK)
    d.polygon(pts(CROWN), outline=OUTLINE, width=ow)
    for (x, y), colour in JEWELS:
        r = 1.15 if colour == RUBY else 0.85
        d.ellipse(((x + 0.5 - r) * k, (y + 0.5 - r) * k, (x + 0.5 + r) * k, (y + 0.5 + r) * k), fill=colour, outline=OUTLINE, width=max(1, ow // 2))
        d.ellipse(((x + 0.2) * k, (y + 0.1) * k, (x + 0.55) * k, (y + 0.45) * k), fill=(255, 255, 255, 220))
    return img.resize((size, size), Image.LANCZOS)


def main():
    os.makedirs(os.path.dirname(TEXTURE), exist_ok=True)
    os.makedirs(BRANDING, exist_ok=True)
    pix = pixel_icon()
    pix.save(TEXTURE)
    pix.resize((512, 512), Image.NEAREST).save(os.path.join(BRANDING, "Mineblox Royale icon (pixel, 512).png"))
    for size in (1024, 512, 256):
        smooth_icon(size).save(os.path.join(BRANDING, f"Mineblox Royale icon {size}.png"))
    print("icon written:", TEXTURE, "and", BRANDING)


if __name__ == "__main__":
    main()
