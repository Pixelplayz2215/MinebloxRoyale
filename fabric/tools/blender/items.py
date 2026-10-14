"""
Guns, heals and shields, and the pickaxe, made in Blender (v1.0.1, creator: "All Guns, Shields, and Health items should
be remade using Blender"; "Iron Pickaxe should look like a Fortnite Pickaxe").

Units are an item model's (0..16 = one block), x along the barrel towards the muzzle, y up, z across (8 = the middle),
the same space the old box models used, so the item sits in the hand where it did. A gun's body is tint channel 1:
drawn in its rarity colour (creator: rarity should stand out more than a stripe).
"""
import math

from brlib import box, cyl, lathe, mat, prism, sphere, surface, torus, tube

# Shared materials.
PAINT = lambda: mat("paint", "#EDEDED", ch=1, rough=0.45)
DARK = lambda: mat("metal_dark", "#2B2F36", rough=0.38, metal=0.6)
MID = lambda: mat("metal_mid", "#5E6570", rough=0.35, metal=0.7)
LIGHT_METAL = lambda: mat("metal_light", "#A9B0BA", rough=0.3, metal=0.8)
POLY = lambda: mat("polymer", "#22262C", rough=0.75)
RUBBER = lambda: mat("rubber", "#16181B", rough=0.9)
BORE = lambda: mat("bore", "#07080A", rough=0.9)
LENS = lambda: mat("lens", "#3E8CFF", glow=True)
WOOD = lambda: mat("wood", "#8A5530", rough=0.6)
WOOD_DARK = lambda: mat("wood_dark", "#5E3A22", rough=0.65)
GLASS = lambda: mat("glass_rim", "#D9F2FF", rough=0.15)
CAP_WHITE = lambda: mat("cap_white", "#F4F6FA", rough=0.4)


def zr(depth, mid=8.0):
    return mid - depth / 2, mid + depth / 2


def rail(x0, x1, y, z0, z1, teeth=6):
    """A Picatinny-style rail: a strip with raised teeth."""
    box(x0, y, z0, x1, y + 0.18, z1, DARK(), bevel=0.04)
    step = (x1 - x0) / teeth
    for i in range(teeth):
        a = x0 + i * step + step * 0.2
        box(a, y + 0.18, z0 + 0.05, a + step * 0.6, y + 0.36, z1 - 0.05, DARK(), bevel=0.03)


def muzzle(x, y, r, length=0.7):
    cyl(x + length / 2, y, 8, r, length, "x", DARK(), verts=16, bevel=0.04)
    cyl(x + length + 0.01, y, 8, r * 0.55, 0.04, "x", BORE(), verts=16)


def trigger(x, y):
    torus(x, y, 8, 0.6, 0.11, "z", POLY(), segs=18, rsegs=8, arc=180).rot("z", 180, (x, y, 8))
    box(x - 0.1, y - 0.5, 7.92, x + 0.1, y + 0.35, 8.08, MID(), bevel=0.03)


# ---------------------------------------------------------------- guns (x towards the muzzle)

def gun_pistol():
    z0, z1 = zr(1.6)
    # Slide in the rarity colour, with grip serrations at the back and a dark ejection port.
    prism([(4.2, 9.8), (12.3, 9.8), (12.3, 11.4), (4.6, 11.4), (4.2, 11.0)], z0, z1, PAINT(), bevel=0.18, seg=3)
    for x in (4.7, 5.15, 5.6, 6.05):
        box(x, 10.1, z0 - 0.03, x + 0.22, 11.1, z1 + 0.03, DARK(), bevel=0.03)
    box(8.2, 10.55, z1 - 0.02, 9.9, 11.15, z1 + 0.02, BORE(), bevel=0.04)
    # Sights.
    box(11.5, 11.4, 7.75, 12.0, 11.8, 8.25, DARK(), bevel=0.05)
    box(4.8, 11.4, 7.5, 5.5, 11.85, 8.5, DARK(), bevel=0.05)
    # Barrel peeking out of the slide.
    cyl(12.45, 10.55, 8, 0.33, 0.3, "x", MID(), verts=14)
    cyl(12.61, 10.55, 8, 0.18, 0.04, "x", BORE(), verts=12)
    # Frame, trigger guard and the grip with its texture panels.
    f0, f1 = zr(1.4)
    prism([(5.0, 9.8), (11.8, 9.8), (11.8, 9.3), (7.4, 9.0), (7.2, 9.3)], f0, f1, POLY(), bevel=0.12)
    trigger(8.05, 8.95)
    g0, g1 = zr(1.55)
    prism([(5.0, 9.8), (7.2, 9.8), (6.6, 4.4), (4.4, 4.6)], g0, g1, POLY(), bevel=0.28, seg=3)
    for s in (-1, 1):
        prism([(5.2, 9.1), (6.75, 9.1), (6.3, 5.2), (4.75, 5.35)], 8 + s * 0.78, 8 + s * 0.84, RUBBER(), bevel=0.06)
    box(4.4, 4.3, g0 + 0.1, 6.65, 4.75, g1 - 0.1, MID(), bevel=0.1).rot("z", -6, (5.5, 4.5, 8))


def gun_smg():
    z0, z1 = zr(2.2)
    # Boxy receiver in the rarity colour.
    prism([(4.4, 7.9), (11.4, 7.9), (11.4, 10.4), (5.0, 10.4), (4.4, 9.8)], z0, z1, PAINT(), bevel=0.22, seg=3)
    box(5.2, 10.4, z0 + 0.3, 10.8, 10.75, z1 - 0.3, DARK(), bevel=0.1)
    rail(5.6, 10.4, 10.75, 7.35, 8.65, teeth=6)
    # Shroud with round vents, the barrel and a short muzzle.
    cyl(13.0, 9.15, 8, 0.85, 3.2, "x", DARK(), verts=20, bevel=0.06)
    for x in (11.9, 12.7, 13.5):
        cyl(x, 9.15, 8, 0.36, 1.78, "z", BORE(), verts=12)
    muzzle(14.6, 9.15, 0.5, 0.9)
    # Sights.
    box(13.6, 10.0, 7.7, 14.1, 10.7, 8.3, DARK(), bevel=0.05)
    box(5.5, 11.0, 7.5, 6.3, 11.7, 8.5, DARK(), bevel=0.08)
    # Folding wire stock.
    for z in (7.3, 8.7):
        tube([(4.5, 9.6, z), (1.2, 9.6, z), (0.9, 8.0, z), (1.2, 7.4, z), (4.5, 8.2, z)], 0.17, MID(), segs=8)
    box(0.55, 7.2, 7.0, 1.15, 9.9, 9.0, RUBBER(), bevel=0.15)
    # Grip and a long straight magazine in front of it.
    g0, g1 = zr(1.6)
    prism([(5.2, 8.0), (6.6, 8.0), (6.0, 4.3), (4.5, 4.45)], g0, g1, POLY(), bevel=0.25, seg=3)
    trigger(7.4, 7.6)
    box(8.7, 2.6, 7.3, 10.0, 7.95, 8.7, DARK(), bevel=0.14, seg=3)
    box(8.6, 2.4, 7.2, 10.1, 2.9, 8.8, MID(), bevel=0.1)
    # Vertical foregrip.
    cyl(11.6, 6.9, 8, 0.42, 2.2, "y", POLY(), verts=16, bevel=0.1)
    for y in (6.3, 6.9, 7.5):
        cyl(11.6, y, 8, 0.45, 0.18, "y", RUBBER(), verts=16)


def gun_assault_rifle():
    z0, z1 = zr(2.5)
    # Receiver: the main body in the rarity colour, a little taller at the back.
    prism([(3.4, 8.4), (10.6, 8.4), (10.6, 10.9), (4.2, 10.9), (3.4, 10.4)], z0, z1, PAINT(), bevel=0.22, seg=3)
    # Upper: a darker top cover with the rail.
    box(4.0, 10.9, z0 + 0.25, 10.6, 11.3, z1 - 0.25, DARK(), bevel=0.12)
    rail(4.6, 10.3, 11.3, 7.3, 8.7, teeth=8)
    # Handguard with vent slots, then the barrel and a muzzle brake.
    h0, h1 = zr(2.3)
    box(10.6, 8.7, h0, 14.1, 10.7, h1, POLY(), bevel=0.2, seg=3)
    for x in (11.2, 12.0, 12.8):
        box(x, 9.4, h0 - 0.02, x + 0.5, 10.1, h1 + 0.02, RUBBER(), bevel=0.05)
    cyl(14.8, 9.7, 8, 0.32, 1.6, "x", MID(), verts=16)
    muzzle(15.5, 9.7, 0.46, 0.75)
    # Sights: a hooded front post and a rear aperture on the rail.
    prism([(13.3, 10.7), (13.9, 10.7), (13.7, 11.9), (13.45, 11.9)], 7.55, 8.45, DARK(), bevel=0.06)
    box(4.9, 11.6, 7.45, 5.9, 12.4, 8.55, DARK(), bevel=0.1)
    cyl(5.4, 12.05, 8, 0.18, 1.2, "z", BORE(), verts=12)
    # Stock: an angled skeleton in the rarity colour with a rubber butt pad.
    s0, s1 = zr(2.0)
    prism([(0.5, 7.4), (3.6, 8.6), (3.6, 10.6), (0.5, 10.4)], s0, s1, PAINT(), bevel=0.25, seg=3)
    box(1.3, 8.4, s0 - 0.02, 2.8, 9.7, s1 + 0.02, POLY(), bevel=0.1)
    box(0.0, 7.2, s0 - 0.1, 0.6, 10.6, s1 + 0.1, RUBBER(), bevel=0.15)
    # Pistol grip, raked back.
    g0, g1 = zr(1.7)
    prism([(4.6, 8.5), (5.9, 8.5), (5.4, 4.9), (4.0, 5.0)], g0, g1, POLY(), bevel=0.25, seg=3)
    for y in (5.8, 6.6, 7.4):
        box(4.15, y, g0 - 0.03, 5.5, y + 0.3, g1 + 0.03, RUBBER(), bevel=0.06).rot("z", -8, (5, y, 8))
    trigger(6.6, 7.9)
    # Curved magazine.
    m0, m1 = zr(1.6)
    prism([(7.6, 8.45), (9.3, 8.45), (9.6, 6.2), (9.2, 4.3), (7.8, 4.5), (7.9, 6.3)], m0, m1, DARK(), bevel=0.18, seg=3)
    box(7.75, 4.25, m0 - 0.06, 9.35, 4.75, m1 + 0.06, MID(), bevel=0.1).rot("z", 8, (8.5, 4.5, 8))
    # Charging handle and ejection port.
    box(9.6, 10.3, z1, 10.3, 10.6, z1 + 0.45, MID(), bevel=0.08)
    box(6.4, 9.3, z1 - 0.02, 8.6, 10.0, z1 + 0.02, BORE(), bevel=0.05)


def gun_pump_shotgun():
    z0, z1 = zr(2.2)
    # Receiver in the rarity colour.
    prism([(5.0, 7.9), (9.6, 7.9), (9.6, 10.3), (5.4, 10.3), (5.0, 9.9)], z0, z1, PAINT(), bevel=0.22, seg=3)
    box(6.2, 8.7, z1 - 0.02, 8.4, 9.6, z1 + 0.02, BORE(), bevel=0.05)
    # Barrel over the tube magazine, with a bead sight.
    cyl(12.7, 9.55, 8, 0.42, 6.4, "x", MID(), verts=18)
    cyl(15.92, 9.55, 8, 0.24, 0.04, "x", BORE(), verts=12)
    cyl(12.0, 8.6, 8, 0.36, 5.0, "x", DARK(), verts=16)
    sphere(15.3, 10.05, 8, 0.12, 0.12, 0.12, LIGHT_METAL(), segs=10, rings=6)
    box(15.0, 8.4, 7.75, 15.6, 9.7, 8.25, DARK(), bevel=0.08)
    # The pump: chunky ribbed wood.
    p0, p1 = zr(2.4)
    box(9.8, 7.9, p0, 13.6, 9.2, p1, WOOD(), bevel=0.3, seg=3)
    for x in (10.3, 11.1, 11.9, 12.7):
        box(x, 7.85, p0 - 0.04, x + 0.3, 9.25, p1 + 0.04, WOOD_DARK(), bevel=0.06)
    # Wooden stock with a rubber pad.
    s0, s1 = zr(1.9)
    prism([(0.4, 6.2), (1.5, 6.0), (5.0, 7.9), (5.0, 9.9), (0.4, 9.7)], s0, s1, WOOD(), bevel=0.3, seg=3)
    box(-0.05, 6.0, s0 - 0.08, 0.5, 9.9, s1 + 0.08, RUBBER(), bevel=0.15)
    trigger(6.4, 7.6)


def gun_sniper():
    s0, s1 = zr(2.0)
    # Stock and fore-end in the rarity colour, the thumbhole showing through the stock.
    prism([(0.4, 6.4), (1.6, 6.2), (4.0, 7.4), (6.0, 7.6), (6.0, 9.4), (0.4, 9.6)], s0, s1, PAINT(), bevel=0.3, seg=3)
    box(2.3, 7.6, s0 - 0.05, 3.6, 8.5, s1 + 0.05, BORE(), bevel=0.25)
    box(-0.05, 6.1, s0 - 0.08, 0.5, 9.7, s1 + 0.08, RUBBER(), bevel=0.15)
    prism([(11.0, 7.9), (13.8, 8.1), (13.8, 9.4), (11.0, 9.4)], 7.1, 8.9, PAINT(), bevel=0.25, seg=3)
    # Receiver and a long barrel with a muzzle brake.
    z0, z1 = zr(1.8)
    box(6.0, 7.9, z0, 11.0, 9.8, z1, DARK(), bevel=0.2, seg=3)
    cyl(15.0, 8.95, 8, 0.3, 2.6, "x", MID(), verts=16)
    cyl(15.0, 8.95, 8, 0.36, 1.6, "x", DARK(), verts=8)
    muzzle(16.2, 8.95, 0.45, 0.7)
    # Bolt handle with a round knob.
    tube([(7.0, 9.3, 8.9), (7.0, 9.0, 9.6), (7.2, 8.6, 9.9)], 0.11, MID(), segs=8)
    sphere(7.2, 8.55, 9.95, 0.25, 0.25, 0.25, DARK(), segs=12, rings=8)
    # The scope: tube, bells with glowing lenses, turrets and rings.
    cyl(8.6, 11.0, 8, 0.48, 4.2, "x", DARK(), verts=20)
    lathe([(0.0, 0.0), (0.48, 0.0), (0.72, 0.6), (0.72, 1.3), (0.0, 1.3)], 10.6, 11.0, 8, DARK(), segs=20, axis="x")
    lathe([(0.0, 0.0), (0.62, 0.0), (0.62, 0.5), (0.48, 1.0), (0.0, 1.0)], 5.6, 11.0, 8, DARK(), segs=20, axis="x")
    cyl(11.95, 11.0, 8, 0.6, 0.06, "x", LENS(), verts=20)
    cyl(5.55, 11.0, 8, 0.5, 0.06, "x", LENS(), verts=20)
    cyl(8.6, 11.55, 8, 0.28, 0.4, "y", MID(), verts=14)
    cyl(8.6, 11.0, 8.55, 0.24, 0.4, "z", MID(), verts=14)
    for x in (7.3, 9.9):
        torus(x, 11.0, 8, 0.52, 0.1, "x", MID(), segs=18, rsegs=6)
        box(x - 0.2, 9.8, 7.7, x + 0.2, 10.5, 8.3, MID(), bevel=0.05)
    # Grip and trigger guard.
    g0, g1 = zr(1.5)
    prism([(5.0, 7.7), (6.4, 7.6), (6.1, 5.0), (4.9, 5.3)], g0, g1, POLY(), bevel=0.25, seg=3)
    trigger(7.4, 7.5)
    # Bipod legs folded under the fore-end.
    for z in (7.4, 8.6):
        tube([(13.0, 7.9, z), (15.4, 7.6, z)], 0.1, MID(), segs=6)


# ---------------------------------------------------------------- heals and shields (upright; y up)

def medkit():
    z0, z1 = zr(5.6)
    red = mat("red", "#D93B2C", rough=0.45)
    red_dark = mat("red_dark", "#A82A1F", rough=0.5)
    white = mat("white", "#F7F7F7", rough=0.4)
    box(2.0, 2.6, z0, 14.0, 10.6, z1, red, bevel=0.6, seg=4)
    box(1.85, 6.2, z0 - 0.12, 14.15, 6.9, z1 + 0.12, red_dark, bevel=0.25)
    for s in (z0 - 0.06, z1 + 0.06):
        d = 1 if s > 8 else -1
        box(6.9, 4.1, s - d * 0.05, 9.1, 9.1, s + d * 0.1, white, bevel=0.15)
        box(5.5, 5.5, s - d * 0.05, 10.5, 7.7, s + d * 0.1, white, bevel=0.15)
    for x in (3.4, 11.8):
        for zz in (z0 - 0.25, z1 + 0.25):
            box(x, 5.8, zz - 0.2, x + 0.8, 7.3, zz + 0.2, LIGHT_METAL(), bevel=0.1)
    # Handle on top.
    tube([(6.0, 10.6, 8), (6.0, 12.0, 8), (10.0, 12.0, 8), (10.0, 10.6, 8)], 0.42, RUBBER(), segs=10)


def bandages():
    cloth = mat("bandage", "#F2EEE6", rough=0.85)
    stripe = mat("bandage_stripe", "#D83A30", rough=0.7)
    tape = mat("tape", "#C9C2B3", rough=0.8)
    for x0, x1 in ((2.0, 7.7), (8.3, 14.0)):
        cx = (x0 + x1) / 2
        cyl(cx, 6.5, 8, 3.0, x1 - x0, "x", cloth, verts=28, bevel=0.25, seg=3)
        for a in (0.9, 1.7, 2.4):
            torus(x0 + 0.02, 6.5, 8, a, 0.06, "x", tape, segs=24, rsegs=5)
            torus(x1 - 0.02, 6.5, 8, a, 0.06, "x", tape, segs=24, rsegs=5)
        cyl(x0 + 2.4, 6.5, 8, 3.08, 0.7, "x", stripe, verts=28)
    cyl(8.0, 6.5, 8, 3.18, 1.2, "x", tape, verts=28, bevel=0.1)
    # A loose end: a flat strip of the right-hand roll coming off round its front and hanging down, curling a little.
    path = [(6.5 + 3.03 * math.sin(math.radians(a)), 8 - 3.03 * math.cos(math.radians(a))) for a in (25, 10, -5, -20)]
    path += [(5.0, 4.75), (4.1, 4.85), (3.4, 5.15)]

    def strip(u, v):
        g = u * (len(path) - 1)
        i = min(len(path) - 2, int(g))
        f = g - i
        y = path[i][0] + (path[i + 1][0] - path[i][0]) * f
        z = path[i][1] + (path[i + 1][1] - path[i][1]) * f
        return (11.1 + 2.0 * v + 0.15 * u * math.sin(v * math.pi), y, z)
    surface(strip, 24, 4, cloth, thickness=0.12)


def small_shield():
    liquid = mat("mini_liquid", "#4FB8FF", rough=0.15)
    lathe([(0, 1.9), (2.4, 1.9), (2.8, 2.4), (2.8, 6.2), (2.2, 7.0), (1.3, 7.4), (0, 7.4)], 8, 0, 8, liquid, segs=28)
    torus(8, 4.6, 8, 2.82, 0.2, "y", GLASS(), segs=28, rsegs=6)
    lathe([(0, 7.2), (1.2, 7.2), (1.2, 8.6), (0, 8.6)], 8, 0, 8, GLASS(), segs=20)
    lathe([(0, 8.5), (1.45, 8.5), (1.5, 9.0), (1.45, 9.9), (0, 10.1)], 8, 0, 8, CAP_WHITE(), segs=20)


def shield_potion():
    liquid = mat("shield_liquid", "#3D8BFF", rough=0.15)
    label = mat("label", "#F4F6FA", rough=0.6)
    badge = mat("badge", "#2B63D9", rough=0.5)
    lathe([(0, 1.8), (3.2, 1.8), (3.6, 2.4), (3.6, 7.6), (3.0, 8.6), (1.6, 9.4), (0, 9.4)], 8, 0, 8, liquid, segs=32)
    cyl(8, 5.2, 8, 3.66, 3.2, "y", label, verts=32)
    lathe([(0, 9.2), (1.4, 9.2), (1.4, 10.6), (0, 10.6)], 8, 0, 8, GLASS(), segs=20)
    lathe([(0, 10.5), (1.85, 10.5), (1.9, 11.1), (1.85, 12.3), (0, 12.5)], 8, 0, 8, mat("cap_blue", "#2B63D9", rough=0.4), segs=20)
    for zz, d in ((8 - 3.68, -1), (8 + 3.68, 1)):
        prism([(7.0, 4.3), (9.0, 4.3), (9.0, 5.7), (8.0, 6.3), (7.0, 5.7)], zz, zz + d * 0.08, badge, bevel=0.02)


def slurp_juice():
    liquid = mat("slurp", "#9B45E0", rough=0.2)
    band = mat("slurp_band", "#FF79C9", rough=0.4)
    swirl = mat("swirl", "#E2B8FF", rough=0.3)
    lathe([(0, 1.8), (3.0, 1.8), (3.5, 2.6), (3.5, 7.6), (3.2, 8.4), (0, 8.4)], 8, 0, 8, liquid, segs=32)
    for y in (3.2, 6.8):
        torus(8, y, 8, 3.52, 0.22, "y", band, segs=32, rsegs=6)
    for start in (0.0, math.pi):
        pts = []
        for i in range(16):
            t = i / 15
            a = start + t * 2.2
            pts.append((8 + 3.56 * math.cos(a), 3.7 + t * 2.6, 8 + 3.56 * math.sin(a)))
        tube(pts, 0.13, swirl, segs=6)
    lathe([(0, 8.3), (3.0, 8.3), (3.1, 8.9), (2.6, 9.5), (0, 9.5)], 8, 0, 8, CAP_WHITE(), segs=28)
    straw = mat("straw", "#FF5FA2", rough=0.4)
    tube([(8.6, 9.2, 8), (8.6, 12.6, 8), (8.9, 13.4, 8), (10.6, 14.4, 8), (11.4, 14.4, 8)], 0.28, straw, segs=10)


def chug_jug():
    liquid = mat("chug_liquid", "#38D8FF", rough=0.15)
    band = mat("chug_band", "#F5C629", rough=0.35, metal=0.3)
    lathe([(0, 1.4), (3.6, 1.4), (4.1, 2.2), (4.1, 7.6), (3.5, 8.6), (2.0, 9.2), (0, 9.2)], 7.4, 0, 8, liquid, segs=32)
    torus(7.4, 5.1, 8, 4.12, 0.42, "y", band, segs=32, rsegs=8)
    lathe([(0, 9.0), (1.6, 9.0), (1.6, 10.4), (0, 10.4)], 7.4, 0, 8, GLASS(), segs=20)
    lathe([(0, 10.3), (2.0, 10.3), (2.05, 11.0), (1.95, 12.2), (0, 12.4)], 7.4, 0, 8, CAP_WHITE(), segs=20)
    tube([(10.8, 7.4, 8), (13.0, 7.2, 8), (13.4, 5.1, 8), (13.0, 3.0, 8), (10.9, 2.8, 8)], 0.55, CAP_WHITE(), segs=12)


# ---------------------------------------------------------------- the pickaxe (held like a tool: handle corner to corner)

def pickaxe():
    head = mat("pick_head", "#8C98A8", rough=0.35, metal=0.75)
    edge = mat("pick_edge", "#D8DEE6", rough=0.25, metal=0.9)
    handle = mat("pick_handle", "#2A2D33", rough=0.6)
    wrap = mat("pick_wrap", "#2F7BFF", rough=0.7)
    collar = mat("pick_collar", "#F2B33D", rough=0.4, metal=0.5)
    # Handle from the bottom left corner up to the head, wrapped in blue tape near the end.
    tube([(2.2, 2.2, 8), (11.6, 11.6, 8)], 0.55, handle, segs=12)
    for i in range(5):
        p = 2.9 + i * 0.75
        tube([(p - 0.15, p - 0.15, 8), (p + 0.25, p + 0.25, 8)], 0.62, wrap, segs=12)
    sphere(2.0, 2.0, 8, 0.75, 0.75, 0.75, collar, segs=14, rings=8)
    # The head across the handle's end: a long curved spike one way, a broad blade the other, a gold collar between.
    hx, hy = 11.4, 11.4
    cyl(hx, hy, 8, 0.95, 2.0, "z", collar, verts=16, bevel=0.15)
    tube([(hx, hy, 8), (hx - 2.6, hy + 2.2, 8), (hx - 5.0, hy + 3.3, 8), (hx - 6.9, hy + 3.5, 8)], [1.0, 0.85, 0.55, 0.1], head, segs=14)
    tube([(hx, hy, 8), (hx + 2.3, hy - 2.4, 8), (hx + 3.6, hy - 4.4, 8)], [1.0, 0.9, 0.72], head, segs=14)
    box(hx + 2.8, hy - 5.7, 7.2, hx + 4.6, hy - 4.1, 8.8, edge, bevel=0.35, seg=3).rot("z", -32, (hx + 3.7, hy - 4.9, 8))


# ---------------------------------------------------------------- what build.py makes

RARITIES = {"Common": "#B0B0B0", "Uncommon": "#5DBB2C", "Rare": "#3C9BE8", "Epic": "#B44CE8", "Legendary": "#F0A626", "Mythic": "#FFD84A"}


def gun(name, fn):
    return (name, fn, {"variants": {r: {1: c} for r, c in RARITIES.items()}, "icon": (24, 12), "tinted": True, "ao": 1.2,
                       "folder": "Guns", "variant_first": True})


HEAL = {"icon": (-28, 18), "ao": 1.5, "folder": "Heals and Shields", "views": {"Front": 0, "Right": 270, "Back": 180, "Left": 90}}

ITEMS = {
    "gun_pistol": gun("Pistol", gun_pistol),
    "gun_smg": gun("SMG", gun_smg),
    "gun_assault_rifle": gun("Assault Rifle", gun_assault_rifle),
    "gun_pump_shotgun": gun("Pump Shotgun", gun_pump_shotgun),
    "gun_sniper": gun("Bolt-Action Sniper", gun_sniper),
    "bandages": ("Bandages", bandages, HEAL),
    "medkit": ("Med Kit", medkit, HEAL),
    "small_shield": ("Mini Shield", small_shield, HEAL),
    "shield_potion": ("Shield Potion", shield_potion, HEAL),
    "slurp_juice": ("Slurp Juice", slurp_juice, HEAL),
    "chug_jug": ("Chug Jug", chug_jug, HEAL),
    "pickaxe": ("Pickaxe", pickaxe, {"icon": (0, 0), "ao": 1.5, "folder": "Pickaxe", "views": {"Front": 0, "Right": 270, "Back": 180, "Left": 90}}),
}
