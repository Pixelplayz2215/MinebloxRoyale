"""
Mineblox Royale item models (creator: polish the gun, Med Kit, Bandages and shield item models).

Writes, for every gun, heal and shield item:
  models/item/<id>.json       a real 3D model made of boxes, used in the hand, on the ground and in item frames
  models/item/<id>_icon.json  the inventory / hotbar picture (2D), rendered from that same 3D model
  textures/item/<id>.png      the icon (32 x 32); guns also get <id>_rarity.png, the parts tinted in the gun's rarity
  textures/item/br_parts.png  one shared texture of small painted tiles (metal, polymer, wood, grip, glass, liquids...)
  items/<id>.json             picks the icon in the inventory and the 3D model everywhere else; guns tint their accent
                              parts with the rarity colour (Guns.make stores it as custom model data)

Coordinates are written in the old 32 px sprite's pixels (x right, y down, z = depth with 16 in the middle) so the 3D
models sit in the hand exactly where the old flat ones did. Run: python make_item_models.py <assets/minebloxroyale dir>
Also writes preview sheets next to this script (previews/).
"""
import json
import math
import os
import random
import sys

from PIL import Image, ImageDraw

ASSETS = sys.argv[1] if len(sys.argv) > 1 else "../../src/main/resources/assets/minebloxroyale"
HERE = os.path.dirname(os.path.abspath(__file__))
PREVIEW = os.path.join(HERE, "previews")

# ---------------------------------------------------------------- tiles (8 x 8 px each, 8 x 8 grid in a 64 px texture)

TILE = 8
GRID = 8
TILES = {}
_rnd = random.Random(7)


def hexc(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def shade(c, f):
    return (max(0, min(255, int(c[0] * f))), max(0, min(255, int(c[1] * f))), max(0, min(255, int(c[2] * f))), c[3])


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(4))


def tile(name):
    def deco(fn):
        img = Image.new("RGBA", (TILE, TILE))
        px = img.load()
        for y in range(TILE):
            for x in range(TILE):
                px[x, y] = fn(x, y)
        TILES[name] = (len(TILES), img)
        return fn
    return deco


def noise(c, amount):
    return shade(c, 1 + (_rnd.random() - 0.5) * amount)


@tile("metal_dark")
def _(x, y):
    c = hexc("#2D323A")
    return noise(shade(c, 1.18 if y == 0 else 1.0 - y * 0.012), 0.10)


@tile("metal_mid")
def _(x, y):
    c = hexc("#4E5562")
    return noise(shade(c, 1.15 if y == 0 else 1.0 - y * 0.015), 0.10)


@tile("metal_light")
def _(x, y):
    c = hexc("#9AA3B0")
    return noise(shade(c, 1.12 if y <= 1 else 1.0 - y * 0.02), 0.07)


@tile("polymer")
def _(x, y):
    return noise(hexc("#383C43"), 0.06)


@tile("polymer_light")
def _(x, y):
    return noise(hexc("#5A606B"), 0.06)


@tile("vents")
def _(x, y):
    base = hexc("#4A505A")
    return shade(base, 0.55) if (x % 3 == 1 and 1 <= y <= 6) else noise(base, 0.06)


@tile("grip")
def _(x, y):
    base = hexc("#22252B")
    return shade(base, 1.35) if (x + y) % 4 == 0 or (x - y) % 4 == 0 else noise(base, 0.04)


@tile("rubber")
def _(x, y):
    return noise(hexc("#151517"), 0.05)


@tile("bore")
def _(x, y):
    d = math.hypot(x - 3.5, y - 3.5)
    return hexc("#050505") if d < 2.2 else hexc("#3A3F47")


@tile("wood")
def _(x, y):
    base = hexc("#93602E")
    grain = math.sin((y * 1.7 + math.sin(x * 0.7) * 1.2)) * 0.10
    return noise(shade(base, 1 + grain), 0.05)


@tile("wood_dark")
def _(x, y):
    base = hexc("#6A4320")
    grain = math.sin((y * 1.9 + math.sin(x * 0.6) * 1.4)) * 0.10
    return noise(shade(base, 1 + grain), 0.05)


@tile("olive")
def _(x, y):
    return noise(hexc("#4F5B39"), 0.07)


@tile("glass")
def _(x, y):
    c = hexc("#9FD8F2")
    return (255, 255, 255, 255) if (x - y == 2 or x - y == 3) and y < 5 else noise(c, 0.04)


@tile("lens")
def _(x, y):
    d = math.hypot(x - 3.5, y - 3.5)
    if d > 3.6:
        return hexc("#1A1D22")
    if 1.0 < x < 3.2 and 1.0 < y < 3.2:
        return hexc("#E6F6FF")
    return mix(hexc("#2F6FCC"), hexc("#10306A"), d / 3.6)


@tile("white")
def _(x, y):
    return (255, 255, 255, 255)


@tile("accent")
def _(x, y):  # tinted with the rarity colour: light, with a brighter top edge
    return (255, 255, 255, 255) if y < 2 else (226, 226, 226, 255)


@tile("dark")
def _(x, y):
    return hexc("#0E0F12")


@tile("red")
def _(x, y):
    return noise(shade(hexc("#D8402F"), 1.1 if y == 0 else 1.0), 0.05)


@tile("red_dark")
def _(x, y):
    return noise(hexc("#A3291C"), 0.05)


@tile("cross")
def _(x, y):  # Med Kit face: a white cross on red
    if 3 <= x <= 4 and 1 <= y <= 6 or 3 <= y <= 4 and 1 <= x <= 6:
        return (250, 250, 250, 255)
    return noise(hexc("#D8402F"), 0.04)


@tile("latch")
def _(x, y):
    return noise(hexc("#C9CDD4"), 0.05)


@tile("bandage")
def _(x, y):
    c = hexc("#F3EEE1")
    return shade(c, 0.93) if x % 3 == 0 else noise(c, 0.02)


@tile("bandage_end")
def _(x, y):
    d = math.hypot(x - 3.5, y - 3.5)
    if d < 1.2:
        return hexc("#8A6A44")
    ring = int(d * 1.6) % 2
    return hexc("#E8E1CF") if ring else hexc("#CFC5AE")


@tile("bandage_stripe")
def _(x, y):
    return noise(hexc("#D8402F"), 0.05)


@tile("tape")
def _(x, y):
    return noise(hexc("#E9DFC6"), 0.05)


@tile("shield_liquid")
def _(x, y):
    t = y / 7
    c = mix(hexc("#7FD0FF"), hexc("#1F78E0"), t)
    return (230, 248, 255, 255) if (x == 1 and 1 <= y <= 4) else c


@tile("mini_liquid")
def _(x, y):
    t = y / 7
    c = mix(hexc("#A8E4FF"), hexc("#3AA0FF"), t)
    return (240, 252, 255, 255) if (x == 1 and 1 <= y <= 3) else c


@tile("glass_rim")
def _(x, y):
    return noise(hexc("#CFEAF7"), 0.04)


@tile("cap_white")
def _(x, y):
    return shade(hexc("#F2F4F8"), 0.88) if x % 2 == 0 else hexc("#F2F4F8")


@tile("cap_blue")
def _(x, y):
    return shade(hexc("#2F6BD8"), 0.85) if x % 2 == 0 else hexc("#2F6BD8")


@tile("label_shield")
def _(x, y):  # white label with a blue shield badge in the middle
    shield = {(2, 2), (3, 2), (4, 2), (5, 2), (2, 3), (3, 3), (4, 3), (5, 3), (2, 4), (3, 4), (4, 4), (5, 4), (3, 5), (4, 5)}
    if (x, y) in shield:
        return hexc("#A8E4FF") if (x, y) == (3, 3) else hexc("#2F6BD8")
    return hexc("#2F6BD8") if y in (1, 6) else hexc("#F4F7FB")


@tile("slurp")
def _(x, y):
    t = y / 7
    return mix(hexc("#B062F0"), hexc("#6A24B8"), t)


@tile("slurp_swirl")
def _(x, y):
    a = math.atan2(y - 3.5, x - 3.5)
    d = math.hypot(x - 3.5, y - 3.5)
    on = math.sin(a * 2 + d * 1.6) > 0.3
    return hexc("#FF8AE8") if on else hexc("#8A3FD1")


@tile("slurp_band")
def _(x, y):
    return noise(hexc("#FF6FCF"), 0.05) if 1 <= y <= 6 else hexc("#FFB0EA")


@tile("straw")
def _(x, y):
    return hexc("#FFFFFF") if (x + y) % 4 < 2 else hexc("#FF5FAE")


@tile("chug_liquid")
def _(x, y):
    t = y / 7
    c = mix(hexc("#8AF4FF"), hexc("#14B5DA"), t)
    return (235, 255, 255, 255) if (x in (1, 2) and 1 <= y <= 4 and x + y < 6) else c


@tile("chug_band")
def _(x, y):
    return noise(hexc("#FFD21F"), 0.05) if 1 <= y <= 6 else hexc("#FFF3B0")


def uv_of(name, w, h):
    """UV box (in 0..16 model UV) inside a tile: about one texel per face pixel, 1 texel margin."""
    i, _ = TILES[name]
    tx, ty = i % GRID, i // GRID
    unit = 16 / (TILE * GRID)  # UV units per texel
    tw, th = max(1, min(6, round(w))), max(1, min(6, round(h)))
    x0 = tx * TILE + 1 + (6 - tw) / 2
    y0 = ty * TILE + 1 + (6 - th) / 2
    return [round(x0 * unit, 4), round(y0 * unit, 4), round((x0 + tw) * unit, 4), round((y0 + th) * unit, 4)]


def atlas():
    img = Image.new("RGBA", (TILE * GRID, TILE * GRID), (0, 0, 0, 0))
    for name, (i, t) in TILES.items():
        img.paste(t, ((i % GRID) * TILE, (i // GRID) * TILE))
    return img


# ---------------------------------------------------------------- models (boxes in old-sprite pixels)

class Box:
    def __init__(self, x0, y0, z0, x1, y1, z1, t, faces=None, tint=False, glow=0, rot=None):
        self.p0 = (min(x0, x1), min(y0, y1), min(z0, z1))
        self.p1 = (max(x0, x1), max(y0, y1), max(z0, z1))
        self.t = t
        self.faces = faces or {}
        self.tint = tint
        self.glow = glow
        self.rot = rot  # (axis, degrees, (ox, oy, oz) in sprite pixels), model-space angle


def box(*a, **k):
    return Box(*a, **k)


def cyl(cx, cz, r, y0, y1, t, n=3, axis="y", **k):
    """A round body from n crossed boxes (a stepped cylinder), along y (or x: cx is then the y centre)."""
    out = []
    for i in range(n):
        a = math.radians((i + 0.5) * 90 / n)
        hu, hv = r * math.cos(a), r * math.sin(a)
        e = i * 0.06  # ends a hair apart, so their flat faces never fight
        if axis == "y":
            out.append(box(cx - hu, y0 + e, cz - hv, cx + hu, y1 - e, cz + hv, t, **k))
        else:
            out.append(box(y0 + e, cx - hu, cz - hv, y1 - e, cx + hu, cz + hv, t, **k))
    return out


def zc(depth, mid=16.0):
    """z range of something `depth` pixels thick, centred."""
    return mid - depth / 2, mid + depth / 2


def gun_pistol():
    b = []
    z0, z1 = zc(3)
    b += [box(8, 9, z0, 24, 13, z1, "metal_light", faces={"down": "metal_mid"})]  # slide
    b += [box(8.5, 9.6, z0 - 0.05, 12, 12.4, z0, "vents"), box(8.5, 9.6, z1, 12, 12.4, z1 + 0.05, "vents")]  # serrations
    b += [box(22, 8, 15.4, 23, 9, 16.6, "dark"), box(9, 8, 14.9, 10.5, 9, 17.1, "dark")]  # sights
    b += [box(24, 10, 15, 25, 12.5, 17, "bore", faces={"north": "metal_dark", "south": "metal_dark", "up": "metal_dark", "down": "metal_dark"})]
    zz0, zz1 = zc(2.6)
    b += [box(8, 13, zz0, 22, 15, zz1, "polymer")]  # frame
    b += [box(13, 11.4, z0 - 0.08, 21, 12.2, z0, "accent", tint=True, glow=6), box(13, 11.4, z1, 21, 12.2, z1 + 0.08, "accent", tint=True, glow=6)]
    b += [box(15, 15, 15.5, 19, 16, 16.5, "polymer"), box(18, 13.5, 15.5, 19, 16, 16.5, "polymer"), box(15.6, 14.6, 15.7, 16.4, 16, 16.3, "metal_mid")]
    gz0, gz1 = zc(3.2)
    b += [box(8.4, 14, gz0, 14, 23.5, gz1, "grip", faces={"up": "polymer", "down": "rubber"}, rot=("z", -18, (11, 14.5, 16)))]
    b += [box(8.6, 23, gz0 + 0.2, 13.8, 24.3, gz1 - 0.2, "metal_mid", rot=("z", -18, (11, 14.5, 16)))]
    return b


def gun_smg():
    b = []
    z0, z1 = zc(5)
    b += [box(9, 11, z0, 22, 16, z1, "metal_dark")]  # receiver
    b += [box(11, 9.6, 14.8, 19, 11, 17.2, "metal_mid"), box(12, 8, 15.4, 13, 9.6, 16.6, "dark"), box(17.5, 8.4, 15.4, 18.5, 9.6, 16.6, "dark")]
    s0, s1 = zc(3.4)
    b += [box(22, 12, s0, 29.5, 15, s1, "vents", faces={"up": "metal_mid", "down": "metal_mid", "east": "metal_mid", "west": "metal_mid"})]
    b += [box(29.5, 12.4, 15, 32, 14.6, 17, "bore", faces={"north": "metal_dark", "south": "metal_dark", "up": "metal_dark", "down": "metal_dark"})]
    # Folding wire stock.
    b += [box(3, 11.4, 15.2, 9, 12.4, 16.8, "metal_mid"), box(3, 14.6, 15.2, 9, 15.6, 16.8, "metal_mid"), box(2, 11, 14.6, 3.2, 16, 17.4, "rubber")]
    b += [box(12, 13.2, z0 - 0.08, 21, 14, z0, "accent", tint=True, glow=6), box(12, 13.2, z1, 21, 14, z1 + 0.08, "accent", tint=True, glow=6)]
    gz0, gz1 = zc(3.2)
    b += [box(10, 15.5, gz0, 13.4, 22.5, gz1, "grip", faces={"down": "rubber"}, rot=("z", -15, (11.7, 16, 16)))]
    b += [box(15, 15.8, 15, 18.2, 25, 17, "metal_mid", faces={"down": "dark"})]  # magazine
    b += [box(13.3, 16, 15.6, 14.9, 18.4, 16.4, "polymer"), box(13.3, 18, 15.6, 15, 18.8, 16.4, "polymer")]
    return b


def gun_assault_rifle():
    b = []
    z0, z1 = zc(5)
    b += [box(7, 11, z0, 21, 16, z1, "metal_dark")]  # receiver
    b += [box(9, 9.4, 14.6, 18, 11, 17.4, "metal_mid")]  # top rail
    for x in range(10, 18, 2):
        b += [box(x, 9.0, 14.8, x + 1, 9.4, 17.2, "metal_dark")]
    b += [box(10, 7, 15.2, 11.4, 9.4, 16.8, "polymer"), box(16, 7.2, 15.2, 17.2, 9.4, 16.8, "polymer")]  # sights
    h0, h1 = zc(4.4)
    b += [box(21, 11.4, h0, 27.5, 15.6, h1, "vents", faces={"up": "polymer_light", "down": "polymer_light", "east": "polymer"})]
    b += [box(27.5, 12.5, 15.2, 31, 14.1, 16.8, "metal_mid")]
    b += [box(31, 12, 14.8, 32.4, 14.6, 17.2, "bore", faces={"north": "metal_dark", "south": "metal_dark", "up": "metal_dark", "down": "metal_dark"})]
    s0, s1 = zc(4)
    b += [box(1, 11.8, s0, 7.2, 14.4, s1, "polymer"), box(1, 14.4, s0 + 0.2, 5.4, 17, s1 - 0.2, "polymer"), box(0, 11.4, s0 - 0.2, 1.2, 17.4, s1 + 0.2, "rubber")]
    b += [box(8, 13.4, z0 - 0.08, 20, 14.2, z0, "accent", tint=True, glow=6), box(8, 13.4, z1, 20, 14.2, z1 + 0.08, "accent", tint=True, glow=6)]
    b += [box(1.6, 12.6, s0 - 0.08, 6.6, 13.2, s0, "accent", tint=True, glow=4), box(1.6, 12.6, s1, 6.6, 13.2, s1 + 0.08, "accent", tint=True, glow=4)]
    gz0, gz1 = zc(3.2)
    b += [box(9, 15.5, gz0, 12.4, 22.5, gz1, "grip", faces={"down": "rubber"}, rot=("z", -15, (10.7, 16, 16)))]
    m0, m1 = zc(3)
    b += [box(13, 15.8, m0, 17, 20.2, m1, "metal_mid"), box(13.4, 19.6, m0 + 0.1, 17.2, 24.4, m1 - 0.1, "metal_mid", faces={"down": "dark"}, rot=("z", 18, (15.3, 20, 16)))]
    b += [box(12.2, 16, 15.6, 13.4, 18.2, 16.4, "polymer"), box(12.2, 18, 15.6, 14.4, 18.8, 16.4, "polymer")]
    return b


def gun_pump_shotgun():
    b = []
    z0, z1 = zc(4.4)
    b += [box(8, 11.6, z0, 17.5, 16, z1, "metal_dark")]  # receiver
    b += [box(17.5, 11, 14.9, 31, 13, 17.1, "metal_mid")]  # barrel
    b += [box(31, 11, 14.9, 31.6, 13, 17.1, "bore", faces={"north": "metal_dark", "south": "metal_dark", "up": "metal_dark", "down": "metal_dark"})]
    b += [box(17.5, 13, 15.1, 28, 14.4, 16.9, "metal_dark")]  # tube magazine
    b += [box(29.6, 10.2, 15.6, 30.6, 11, 16.4, "latch")]  # bead sight
    p0, p1 = zc(4.6)
    b += [box(18.5, 12.6, p0, 26, 15.6, p1, "wood", faces={"up": "wood_dark", "down": "wood_dark"})]  # pump
    for x in (19.5, 21.5, 23.5):
        b += [box(x, 13.2, p0 - 0.06, x + 1, 15, p0, "wood_dark"), box(x, 13.2, p1, x + 1, 15, p1 + 0.06, "wood_dark")]
    s0, s1 = zc(3.6)
    b += [box(1, 12, s0, 8.4, 15.2, s1, "wood"), box(1, 15.2, s0 + 0.2, 6, 17.4, s1 - 0.2, "wood"), box(0, 11.6, s0 - 0.2, 1.2, 17.8, s1 + 0.2, "rubber")]
    b += [box(7.4, 15, s0 + 0.3, 11.4, 19.4, s1 - 0.3, "wood_dark", rot=("z", -22.5, (9.4, 15.5, 16)))]  # grip wrist
    b += [box(9, 13.3, z0 - 0.08, 17, 14.1, z0, "accent", tint=True, glow=6), box(9, 13.3, z1, 17, 14.1, z1 + 0.08, "accent", tint=True, glow=6)]
    b += [box(11, 16, 15.6, 15.4, 16.8, 16.4, "metal_mid"), box(14.6, 16, 15.6, 15.4, 17.6, 16.4, "metal_mid")]
    return b


def gun_sniper():
    b = []
    s0, s1 = zc(4)
    b += [box(1, 12.2, s0, 11, 15.4, s1, "olive"), box(1, 15.4, s0 + 0.3, 6.4, 17.6, s1 - 0.3, "olive"), box(0, 11.8, s0 - 0.2, 1.2, 18, s1 + 0.2, "rubber")]
    b += [box(2, 13.2, s0 - 0.08, 10, 14, s0, "accent", tint=True, glow=4), box(2, 13.2, s1, 10, 14, s1 + 0.08, "accent", tint=True, glow=4)]
    z0, z1 = zc(4.4)
    b += [box(10, 12, z0, 22, 16, z1, "metal_dark")]  # receiver
    b += [box(22, 12.6, z0 + 0.6, 26, 15.4, z1 - 0.6, "olive")]  # fore-end
    b += [box(26, 12.9, 15.25, 31, 14.2, 16.75, "metal_mid")]
    b += [box(31, 12.5, 14.9, 32.6, 14.6, 17.1, "bore", faces={"north": "metal_dark", "south": "metal_dark", "up": "metal_dark", "down": "metal_dark"})]
    # Scope: tube, bells with lenses, mounts.
    b += [box(11, 8.2, 14.9, 21, 10.4, 17.1, "metal_dark")]
    b += [box(20, 7.2, 14.4, 23, 11.2, 17.6, "metal_dark", faces={"east": "lens"}), box(9, 7.6, 14.6, 11.2, 10.8, 17.4, "metal_dark", faces={"west": "lens"})]
    b += [box(12.5, 10.4, 15.2, 13.7, 12, 16.8, "metal_mid"), box(18, 10.4, 15.2, 19.2, 12, 16.8, "metal_mid")]
    b += [box(15.5, 8.6, 14.7, 16.5, 10, 17.3, "polymer")]  # turret
    b += [box(18.6, 14.8, z1, 19.6, 15.8, 19.6, "metal_light"), box(18.2, 14.6, 19.2, 20, 16.2, 20.4, "metal_light")]  # bolt handle
    gz0, gz1 = zc(3.2)
    b += [box(11, 15.6, gz0, 14.2, 21, gz1, "olive", faces={"down": "rubber"}, rot=("z", -15, (12.6, 16, 16)))]
    b += [box(15, 15.8, 15.2, 18, 17.8, 16.8, "metal_mid"), box(14.2, 16, 15.6, 14.9, 18.6, 16.4, "polymer"), box(14.2, 18, 15.6, 15.8, 18.7, 16.4, "polymer")]
    return b


def medkit():
    b = []
    z0, z1 = zc(11)
    b += [box(4, 11, z0, 28, 27, z1, "red", faces={"north": "cross", "south": "cross", "up": "red", "down": "red_dark"})]
    # Darker trim round the lid seam, latches, a handle on top.
    b += [box(3.8, 16.4, z0 - 0.2, 28.2, 17.6, z1 + 0.2, "red_dark")]
    b += [box(7, 15.4, z1 + 0.15, 9, 18.6, z1 + 0.6, "latch"), box(23, 15.4, z1 + 0.15, 25, 18.6, z1 + 0.6, "latch")]
    b += [box(7, 15.4, z0 - 0.6, 9, 18.6, z0 - 0.15, "latch"), box(23, 15.4, z0 - 0.6, 25, 18.6, z0 - 0.15, "latch")]
    b += [box(12, 7, 14.8, 20, 8.6, 17.2, "rubber"), box(12, 8.6, 14.8, 13.6, 11, 17.2, "rubber"), box(18.4, 8.6, 14.8, 20, 11, 17.2, "rubber")]
    b += [box(4.6, 26.6, z0 + 0.6, 7, 27.6, z0 + 2.4, "rubber"), box(25, 26.6, z0 + 0.6, 27.4, 27.6, z0 + 2.4, "rubber"),
          box(4.6, 26.6, z1 - 2.4, 7, 27.6, z1 - 0.6, "rubber"), box(25, 26.6, z1 - 2.4, 27.4, 27.6, z1 - 0.6, "rubber")]
    return b


def bandages():
    b = []
    # Two rolls side by side (along x), round, the ends showing the wound layers, a red stripe round each.
    for x0, x1 in ((4, 15.4), (16.6, 28)):
        b += cyl(19, 16, 6, x0, x1, "bandage", axis="x", faces={"east": "bandage_end", "west": "bandage_end"})
        b += cyl(19, 16, 6.25, x0 + 3.5, x0 + 5, "bandage_stripe", axis="x")
    # A strip of tape holding them, and a loose end.
    b += cyl(19, 16, 6.4, 14.8, 17.2, "tape", axis="x")
    b += [box(24, 24.6, 13, 30, 25.4, 19, "bandage", rot=("z", -22.5, (24, 25, 16)))]
    return b


def small_shield():
    b = []
    # A little round flask of blue (Fortnite's Mini Shield).
    b += cyl(16, 16, 5.6, 17, 27.4, "mini_liquid", glow=9)
    b += cyl(16, 16, 4.4, 14.6, 17, "mini_liquid", glow=9)
    b += cyl(16, 16, 4.6, 27.4, 28.2, "glass_rim", n=2)
    b += cyl(16, 16, 5.85, 21, 22.4, "glass_rim")  # a band round the middle
    b += cyl(16, 16, 2.3, 10.4, 14.6, "glass", faces={"up": "glass_rim"}, n=2)
    b += cyl(16, 16, 2.9, 7.4, 10.4, "cap_white", faces={"up": "white"}, n=2)
    return b


def shield_potion():
    b = []
    # A big round bottle of blue with a white label (Fortnite's Shield Potion).
    b += cyl(16, 16, 7.2, 13.4, 28.4, "shield_liquid", glow=9)
    b += cyl(16, 16, 5.4, 11.2, 13.4, "shield_liquid", glow=9)
    b += cyl(16, 16, 2.9, 7, 11.2, "glass", faces={"up": "glass_rim"}, n=2)
    b += cyl(16, 16, 3.7, 3.6, 7, "cap_blue", faces={"up": "white"}, n=2)
    for z0, z1 in ((22.9, 23.3), (8.7, 9.1)):
        b += [box(12, 17, z0, 20, 24.4, z1, "label_shield", faces={"up": "white", "down": "white", "east": "white", "west": "white"})]
    return b


def slurp_juice():
    b = []
    # A round purple jug with a swirl and pink bands, a lid and a bendy straw (Fortnite's Slurp Juice).
    b += cyl(16, 16, 7, 10.6, 28.4, "slurp", glow=7, faces={"north": "slurp_swirl", "south": "slurp_swirl"})
    b += cyl(16, 16, 7.2, 13, 15, "slurp_band")
    b += cyl(16, 16, 7.2, 24, 26, "slurp_band")
    b += cyl(16, 16, 6, 7.6, 10.6, "cap_white", n=2)
    b += [box(17, 1.5, 15.2, 18.6, 7.6, 16.8, "straw"), box(17, 1.5, 15.2, 23, 3.1, 16.8, "straw", rot=("z", 22.5, (17.8, 2.3, 16)))]
    return b


def chug_jug():
    b = []
    # A big round jug of glowing cyan with a yellow band, a white cap and a handle (Fortnite's Chug Jug).
    b += cyl(15, 16, 8, 12.4, 26.6, "chug_liquid", glow=10)
    b += cyl(15, 16, 6.6, 10.2, 12.4, "chug_liquid", glow=10)
    b += cyl(15, 16, 6.8, 26.6, 28.6, "chug_liquid", glow=10)
    b += cyl(15, 16, 8.25, 16.6, 18.8, "chug_band")
    b += cyl(15, 16, 3, 6.6, 10.2, "glass", faces={"up": "glass_rim"}, n=2)
    b += cyl(15, 16, 3.8, 3, 6.6, "cap_white", faces={"up": "white"}, n=2)
    b += [box(22.4, 12.6, 14.8, 27.4, 14.6, 17.2, "cap_white"), box(22.4, 22.4, 14.8, 27.4, 24.4, 17.2, "cap_white"), box(25.6, 12.6, 14.8, 27.8, 24.4, 17.2, "cap_white")]
    return b


# ---------------------------------------------------------------- model JSON

GUN_DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [0, 1.5, 2], "scale": [0.9, 0.9, 0.9]},
    "thirdperson_lefthand": {"rotation": [0, -90, 0], "translation": [0, 1.5, 2], "scale": [0.9, 0.9, 0.9]},
    "firstperson_righthand": {"rotation": [0, -90, 5], "translation": [1.13, 3.2, 1.13], "scale": [0.8, 0.8, 0.8]},
    "firstperson_lefthand": {"rotation": [0, 90, -5], "translation": [1.13, 3.2, 1.13], "scale": [0.8, 0.8, 0.8]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "fixed": {"rotation": [0, 180, 0], "scale": [1, 1, 1]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    "gui": {"rotation": [0, 0, 0], "scale": [1, 1, 1]},
}
# Heals and shields: the same hold as Minecraft's flat items (item/generated), so they sit in the hand as before.
ITEM_DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "fixed": {"rotation": [0, 180, 0], "scale": [1, 1, 1]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    "gui": {"rotation": [0, 0, 0], "scale": [1, 1, 1]},
}

FACES = {  # face -> (size axes in sprite pixels)
    "north": (0, 1), "south": (0, 1), "east": (2, 1), "west": (2, 1), "up": (0, 2), "down": (0, 2),
}


def to_model(p):
    """Sprite pixels (y down) -> model units (0..16, y up)."""
    return [round(p[0] / 2, 4), round(16 - p[1] / 2, 4), round(p[2] / 2, 4)]


def element(bx):
    a = to_model((bx.p0[0], bx.p1[1], bx.p0[2]))
    c = to_model((bx.p1[0], bx.p0[1], bx.p1[2]))
    size = [bx.p1[i] - bx.p0[i] for i in range(3)]
    faces = {}
    for f, (u, v) in FACES.items():
        t = bx.faces.get(f, bx.t)
        face = {"uv": uv_of(t, size[u], size[v]), "texture": "#parts"}
        if bx.tint:
            face["tintindex"] = 0
        faces[f] = face
    e = {"from": a, "to": c, "faces": faces}
    if bx.glow:
        e["light_emission"] = bx.glow
    if bx.rot:
        axis, ang, o = bx.rot
        e["rotation"] = {"origin": to_model(o), "axis": axis, "angle": ang}
    return e


# ---------------------------------------------------------------- a small renderer for icons and previews

SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}


def rot_point(p, axis, ang, o):
    a = math.radians(ang)
    x, y, z = p[0] - o[0], p[1] - o[1], p[2] - o[2]
    ca, sa = math.cos(a), math.sin(a)
    if axis == "x":
        y, z = y * ca - z * sa, y * sa + z * ca
    elif axis == "y":
        x, z = x * ca + z * sa, -x * sa + z * ca
    else:
        x, y = x * ca - y * sa, x * sa + y * ca
    return (x + o[0], y + o[1], z + o[2])


def face_quads(bx, tex):
    """Each face of a box in model units, split into small cells with the colour of the tile under each."""
    lo = to_model((bx.p0[0], bx.p1[1], bx.p0[2]))
    hi = to_model((bx.p1[0], bx.p0[1], bx.p1[2]))
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    corners = {
        "north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }
    normals = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}
    out = []
    size = [bx.p1[i] - bx.p0[i] for i in range(3)]
    for f, cs in corners.items():
        t = bx.faces.get(f, bx.t)
        u0, v0, u1, v1 = uv_of(t, size[FACES[f][0]], size[FACES[f][1]])
        n = 6
        for i in range(n):
            for j in range(n):
                def lerp(a, b, s):
                    return tuple(a[k] + (b[k] - a[k]) * s for k in range(3))

                def pt(s, r):
                    top = lerp(cs[0], cs[1], s)
                    bot = lerp(cs[3], cs[2], s)
                    return lerp(top, bot, r)
                quad = [pt(i / n, j / n), pt((i + 1) / n, j / n), pt((i + 1) / n, (j + 1) / n), pt(i / n, (j + 1) / n)]
                tu = (u0 + (u1 - u0) * (i + 0.5) / n) / 16 * tex.width
                tv = (v0 + (v1 - v0) * (j + 0.5) / n) / 16 * tex.height
                col = tex.getpixel((min(tex.width - 1, int(tu)), min(tex.height - 1, int(tv))))
                nrm = normals[f]
                if bx.rot:
                    axis, ang, o = bx.rot
                    om = to_model(o)
                    quad = [rot_point(q, axis, ang, om) for q in quad]
                    nrm = rot_point(nrm, axis, ang, (0, 0, 0))
                out.append((quad, col, f, nrm, bx.tint, bx.glow))
    return out


def render(boxes, tex, size, yaw, pitch, tint=None, mask=False, ss=6, pad=1):
    """
    Orthographic view of the model (yaw about y, then pitch about x), fitted into size x size, with a depth buffer
    (so overlapping boxes cut each other cleanly, as in the game). mask=True: white where the rarity-tinted parts show.
    """
    polys = []
    for bx in boxes:
        polys += face_quads(bx, tex)
    cy_, sy_ = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def view(p, centre=True):
        x, y, z = (p[0] - 8, p[1] - 8, p[2] - 8) if centre else p
        x, z = x * cy_ + z * sy_, -x * sy_ + z * cy_
        y, z = y * cp - z * sp, y * sp + z * cp
        return (x, y, z)

    vpolys = []
    for quad, col, f, nrm, tinted, glow in polys:
        vn = view(nrm, False)
        if vn[2] <= 0.001:
            continue
        vpolys.append(([view(q) for q in quad], col, f, nrm, tinted, glow))
    xs = [q[0] for p in vpolys for q in p[0]]
    ys = [q[1] for p in vpolys for q in p[0]]
    w, h = max(xs) - min(xs), max(ys) - min(ys)
    W = size * ss
    scale = (size - 2 * pad) * ss / max(w, h)
    ox = (W - w * scale) / 2 - min(xs) * scale
    oy = (W - h * scale) / 2 + max(ys) * scale
    zbuf = [-1e9] * (W * W)
    cbuf = [None] * (W * W)
    for vq, col, f, nrm, tinted, glow in vpolys:
        if glow:
            k = 0.9 + 0.1 * SHADE[f]
        else:
            # Minecraft's side shading, plus a soft light from the upper left front.
            k = SHADE[f] * 0.75 + 0.25 * (0.6 + 0.4 * max(0, nrm[1] * 0.8 + nrm[2] * 0.5 - nrm[0] * 0.3))
        c = col
        if tinted and tint is not None:
            c = (c[0] * tint[0] // 255, c[1] * tint[1] // 255, c[2] * tint[2] // 255, 255)
        c = shade(c, k)
        if mask:
            c = (255, 255, 255, 255) if tinted else (0, 0, 0, 0)
        pts = [(q[0] * scale + ox, oy - q[1] * scale, q[2]) for q in vq]
        for tri in ((pts[0], pts[1], pts[2]), (pts[0], pts[2], pts[3])):
            (x0, y0, z0), (x1, y1, z1), (x2, y2, z2) = tri
            area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
            if abs(area) < 1e-9:
                continue
            minx, maxx = max(0, int(min(x0, x1, x2))), min(W - 1, int(max(x0, x1, x2)) + 1)
            miny, maxy = max(0, int(min(y0, y1, y2))), min(W - 1, int(max(y0, y1, y2)) + 1)
            for py in range(miny, maxy + 1):
                fy = py + 0.5
                for px in range(minx, maxx + 1):
                    fx = px + 0.5
                    w0 = ((x1 - fx) * (y2 - fy) - (x2 - fx) * (y1 - fy)) / area
                    w1 = ((x2 - fx) * (y0 - fy) - (x0 - fx) * (y2 - fy)) / area
                    w2 = 1 - w0 - w1
                    if w0 < -1e-6 or w1 < -1e-6 or w2 < -1e-6:
                        continue
                    z = w0 * z0 + w1 * z1 + w2 * z2
                    i = py * W + px
                    if z > zbuf[i] + 1e-4:
                        zbuf[i] = z
                        cbuf[i] = c
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out = img.load()
    for y in range(size):
        for x in range(size):
            r = g = b_ = n = cov = 0
            for sy in range(ss):
                for sx in range(ss):
                    c = cbuf[(y * ss + sy) * W + x * ss + sx]
                    if c is None:
                        continue
                    cov += 1
                    if c[3]:
                        r += c[0]
                        g += c[1]
                        b_ += c[2]
                        n += 1
            if cov * 2 > ss * ss and n * 2 > cov:
                out[x, y] = (r // n, g // n, b_ // n, 255)
    return img


def outline(img, color=(16, 16, 20, 255)):
    """A 1 px dark outline round the icon, like the old pictures."""
    w, h = img.size
    src = img.load()
    out = img.copy()
    o = out.load()
    for y in range(h):
        for x in range(w):
            if src[x, y][3]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h and src[nx, ny][3]:
                    o[x, y] = color
                    break
    return out


# ---------------------------------------------------------------- write everything

ITEMS = [
    ("gun_pistol", gun_pistol, True),
    ("gun_smg", gun_smg, True),
    ("gun_assault_rifle", gun_assault_rifle, True),
    ("gun_pump_shotgun", gun_pump_shotgun, True),
    ("gun_sniper", gun_sniper, True),
    ("medkit", medkit, False),
    ("bandages", bandages, False),
    ("small_shield", small_shield, False),
    ("shield_potion", shield_potion, False),
    ("slurp_juice", slurp_juice, False),
    ("chug_jug", chug_jug, False),
]
COMMON = (200, 200, 205)  # untinted preview / default accent (common rarity grey)


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, separators=(",", ":"))


def reboot_card():
    """The reboot card: a light blue card with a portrait and a chip (32 x 32), held flat like a normal item."""
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((5, 3, 26, 28), radius=3, fill=hexc("#101418"))
    d.rounded_rectangle((6, 4, 25, 27), radius=2, fill=hexc("#5CE1FF"))
    d.rectangle((6, 4, 25, 8), fill=hexc("#2F9FD8"))
    d.rectangle((9, 11, 22, 22), fill=hexc("#F4F8FF"))
    d.ellipse((13, 12, 18, 17), fill=hexc("#2A3442"))  # head
    d.rectangle((11, 18, 20, 22), fill=hexc("#2A3442"))  # shoulders
    d.rectangle((9, 24, 13, 26), fill=hexc("#FFD21F"))  # chip
    d.rectangle((15, 24, 22, 25), fill=hexc("#2F9FD8"))
    d.line((7, 5, 12, 5), fill=hexc("#C8F4FF"))
    img.save(os.path.join(ASSETS, "textures/item/reboot_card.png"))
    write_json(os.path.join(ASSETS, "models/item/reboot_card.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "minebloxroyale:item/reboot_card"}})
    write_json(os.path.join(ASSETS, "items/reboot_card.json"), {"model": {"type": "minecraft:model", "model": "minebloxroyale:item/reboot_card"}})
    return img


SEP = 0.06  # sprite pixels: how far a face that would share a plane with another is pulled back into its own box


def separate(boxes):
    """No two faces of different boxes on the same plane, facing the same way, over each other: two faces at the same
    depth flicker as the camera moves (as the chest lid did). Where they would, the smaller face is pulled back into its
    own box by SEP, behind the bigger one. Works in model space, so rotated boxes count too."""
    import itertools

    import numpy as np

    def faces_of(bx):
        e = element(bx)
        f, t = np.array(e["from"], float), np.array(e["to"], float)
        R, o = np.eye(3), np.zeros(3)
        if "rotation" in e:
            r = e["rotation"]
            c, s = math.cos(math.radians(r["angle"])), math.sin(math.radians(r["angle"]))
            R = {"x": np.array([[1, 0, 0], [0, c, -s], [0, s, c]]), "y": np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]]),
                 "z": np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])}[r["axis"]]
            o = np.array(r["origin"], float)
        out = []
        for ax in range(3):
            for side in (0, 1):
                others = [a for a in range(3) if a != ax]
                corners = []
                for u, v in itertools.product((0, 1), (0, 1)):
                    p = np.zeros(3)
                    p[ax] = (f, t)[side][ax]
                    p[others[0]] = (f, t)[u][others[0]]
                    p[others[1]] = (f, t)[v][others[1]]
                    corners.append((p - o) @ R.T + o)
                n = np.zeros(3)
                n[ax] = 1 if side else -1
                area = (t[others[0]] - f[others[0]]) * (t[others[1]] - f[others[1]])
                out.append((ax, side, np.array(corners), R @ n, area))
        return out

    def pull(bx, ax, side):
        """Model axis/side -> the sprite coordinate, moved into the box (model y is sprite y flipped)."""
        p0, p1 = list(bx.p0), list(bx.p1)
        sprite_max = side == 1 if ax != 1 else side == 0
        if sprite_max:
            p1[ax] -= SEP
        else:
            p0[ax] += SEP
        bx.p0, bx.p1 = tuple(p0), tuple(p1)

    for _ in range(200):
        moved = False
        allf = [(i, fc) for i, bx in enumerate(boxes) for fc in faces_of(bx)]
        for (i, (axa, sa, ca, na, aa)), (j, (axb, sb, cb, nb, ab)) in itertools.combinations(allf, 2):
            if i == j or na @ nb < 0.9999 or abs((cb[0] - ca[0]) @ na) > 0.01:
                continue
            u = ca[1] - ca[0]
            u = u / np.linalg.norm(u)
            v = np.cross(na, u)
            pa, pb = ca @ np.array([u, v]).T, cb @ np.array([u, v]).T
            ox = min(pa[:, 0].max(), pb[:, 0].max()) - max(pa[:, 0].min(), pb[:, 0].min())
            oy = min(pa[:, 1].max(), pb[:, 1].max()) - max(pa[:, 1].min(), pb[:, 1].min())
            if ox <= 0.005 or oy <= 0.005:
                continue
            k, ax, side = (i, axa, sa) if aa <= ab else (j, axb, sb)
            if boxes[k].p1[ax] - boxes[k].p0[ax] <= 3 * SEP:  # too thin to pull back: pull the other one
                k, ax, side = (j, axb, sb) if k == i else (i, axa, sa)
            pull(boxes[k], ax, side)
            moved = True
            break
        if not moved:
            return boxes
    raise RuntimeError("faces still share planes after 200 fixes")


def main():
    card = reboot_card()
    tex = atlas()
    tex.save(os.path.join(ASSETS, "textures/item/br_parts.png"))
    os.makedirs(PREVIEW, exist_ok=True)
    sheet = Image.new("RGBA", (len(ITEMS) * 136, 3 * 136), (58, 62, 70, 255))
    for i, (iid, fn, gun) in enumerate(ITEMS):
        boxes = separate(fn())
        model = {"textures": {"parts": "minebloxroyale:item/br_parts", "particle": "minebloxroyale:item/" + iid},
                 "display": GUN_DISPLAY if gun else ITEM_DISPLAY,
                 "elements": [element(b) for b in boxes]}
        write_json(os.path.join(ASSETS, "models/item/" + iid + ".json"), model)
        yaw, pitch = (-22, 14) if gun else (-28, 18)
        icon = outline(render(boxes, tex, 32, yaw, pitch, tint=(225, 225, 230) if gun else None))
        icon.save(os.path.join(ASSETS, "textures/item/" + iid + ".png"))
        layers = {"layer0": "minebloxroyale:item/" + iid}
        if gun:
            m = render(boxes, tex, 32, yaw, pitch, mask=True)
            # The mask keeps the accent's own shading from the main picture.
            mp, ip = m.load(), icon.load()
            for y in range(32):
                for x in range(32):
                    if mp[x, y][3]:
                        r, g, b, _ = ip[x, y]
                        v = max(r, g, b)
                        mp[x, y] = (v, v, v, 255)
            m.save(os.path.join(ASSETS, "textures/item/" + iid + "_rarity.png"))
            layers["layer1"] = "minebloxroyale:item/" + iid + "_rarity"
        write_json(os.path.join(ASSETS, "models/item/" + iid + "_icon.json"), {"parent": "minecraft:item/generated", "textures": layers})
        icon_model = {"type": "minecraft:model", "model": "minebloxroyale:item/" + iid + "_icon"}
        full = {"type": "minecraft:model", "model": "minebloxroyale:item/" + iid}
        if gun:
            # Accent parts in the gun's rarity colour (custom model data colour 0, set by Guns.make); common grey if none.
            icon_model["tints"] = [{"type": "minecraft:constant", "value": 0xFFFFFF}, {"type": "minecraft:custom_model_data", "index": 0, "default": 0xC8C8CD}]
            full["tints"] = [{"type": "minecraft:custom_model_data", "index": 0, "default": 0xC8C8CD}]
        write_json(os.path.join(ASSETS, "items/" + iid + ".json"),
                   {"model": {"type": "minecraft:select", "property": "minecraft:display_context",
                              "cases": [{"when": ["gui"], "model": icon_model}], "fallback": full}})
        # Previews: the icon big, and two 3D views.
        sheet.paste(icon.resize((128, 128), Image.NEAREST), (i * 136 + 4, 4), icon.resize((128, 128), Image.NEAREST))
        v1 = render(boxes, tex, 128, -35, 20, tint=(255, 160, 40) if gun else None)
        v2 = render(boxes, tex, 128, 150, 25, tint=(180, 90, 255) if gun else None)
        sheet.paste(v1, (i * 136 + 4, 140), v1)
        sheet.paste(v2, (i * 136 + 4, 276), v2)
    sheet.save(os.path.join(PREVIEW, "item_models.png"))
    card.resize((128, 128), Image.NEAREST).save(os.path.join(PREVIEW, "reboot_card.png"))
    print("wrote", len(ITEMS), "items;", len(TILES), "tiles")


if __name__ == "__main__":
    main()
