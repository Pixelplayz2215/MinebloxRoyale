"""
Shirts and pants as 3D clothing (creator: "every shirt and pants put into Blender for smoothing and a better model"):
the painted design stays on the body (ClothingTextures), and over it go the parts that stand out in real clothes:
every shirt's collar, hem and sleeve cuffs; suit lapels, tie and buttons; overall straps, bib and buttons; vest edges
and buttons; belts with buckles; badges, name tags, pocket flaps and epaulettes; raised crosses, logos and sashes with
medals; a torn shirt's ragged hem. Pants: a waistband with belt loops, or a belt and buckle; jeans' rivets, back
pockets and rolled cuffs; cargo pockets, knee pads, side piping, ankle bands, a pressed crease.

Each piece sits exactly over its painted part: the designs are 48 pixels a stud, a face's picture running from its
-x edge (left of the picture) to its +x edge and from its top down (ClothingTextures, Cosmetics.wrap), so a design
pixel (px, py) on the torso's front is x = -1 + px / 48, y = 1 - py / 48.

Models (all in the item's two colours, tint channels 1 and 2):
  shirt_<shape>_torso (torso joint space: x +-1, y -1 .. 1, z +-0.5, the front -z), shirt_<shape>_rarm / _larm (arm
  joint space: x +-0.5, y -1.5 .. 0.5), pants_<shape>_hips (torso), pants_<shape>_rleg / _lleg (leg joint space:
  x +-0.5, y -2 .. 0).
"""
import math

from mathutils import Vector

from shapes import C, S, _mesh, material, shade

GOLD, DARK, WHITE, METAL, RED = 0xF2C230, 0x16161A, 0xF4F4F4, 0xC9CED6, 0xC0182A
F = -0.5  # the torso's (and each limb's) front face, z
LIFT = 0.03  # how far out from the body a piece's middle sits (the painted design is 0.012 out)


def px(p):
    return -1 + p / 48


def py(p):
    return 1 - p / 48


def rrect(hx, hz, r, y, cx=0.0, cz=0.0, n=5):
    """A rounded rectangle round the body at height y (closed loop)."""
    pts = []
    for qx, qz, a0 in ((1, 1, 0), (-1, 1, 90), (-1, -1, 180), (1, -1, 270)):
        ox, oz = cx + qx * (hx - r), cz + qz * (hz - r)
        for i in range(n + 1):
            a = math.radians(a0 + 90 * i / n)
            pts.append((ox + math.cos(a) * r, y, oz + math.sin(a) * r))
    return pts + [pts[0]]


def ring(s, hx, hz, y, tr, color, r=0.06, cx=0.0, cz=0.0):
    pts = rrect(hx + tr * 0.4, hz + tr * 0.4, r, y, cx, cz)
    s.at(0, 0, 0).color(color).tube(pts, [tr] * len(pts), caps=False, sides=8)


def ribbon(pts, normals, width, thick, color, round_ends=True):
    """A flat strip (a strap, a lapel's edge, a sash) along pts, lying with its flat side facing 'normals'."""
    P = [Vector(p) for p in pts]
    N = [Vector(n).normalized() for n in normals]
    verts, faces = [], []
    k = len(P)
    for i in range(k):
        t = (P[min(k - 1, i + 1)] - P[max(0, i - 1)]).normalized()
        n = (N[i] - t * N[i].dot(t)).normalized()
        side = t.cross(n).normalized()
        for sx, sn in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
            verts.append(tuple(P[i] + side * sx * width / 2 + n * sn * thick / 2))
    for i in range(k - 1):
        for j in range(4):
            j1 = (j + 1) % 4
            faces.append((i * 4 + j, (i + 1) * 4 + j, (i + 1) * 4 + j1, i * 4 + j1))
    faces.append((3, 2, 1, 0))
    faces.append(tuple((k - 1) * 4 + j for j in range(4)))
    return _mesh([tuple(C @ Vector(v)) for v in verts], faces, material(color))


def plate(s, x, y, z, w, h, d, color, normal=(0, 0, -1), round_=0.02):
    """A raised rounded plate (a pocket, a pad, a cross's arm) centred at (x, y, z), facing normal."""
    ax = abs(normal[0]) > 0.5
    hx, hz = (d, w) if ax else (w, d)
    s.at(x, y, z).color(color).scale(hx, 1, hz).rotY(45).lathe([0, 1.414 - round_ * 4, 1.414, 1.414, 1.414 - round_ * 4, 0], [-h, -h, -h + round_, h - round_, h, h], 4)


def button(s, x, y, z, r, color, normal=(0, 0, -1)):
    s.at(x, y, z).align(-normal[0], -normal[1], -normal[2]).rotX(180).color(color).lathe([0, r, r * 1.05, r * 0.9, 0], [0, 0, r * 0.25, r * 0.45, r * 0.5], 12)


def star(s, x, y, z, r, color):
    for k in range(5):
        s.at(x, y, z).rotZ(k * 72).move(0, r * 0.5, 0).rotX(90).color(color).lens(r * 0.22, r * 0.5, 0.018)
    s.at(x, y, z).rotX(90).color(color).lens(r * 0.4, r * 0.4, 0.02)


# ---------------------------------------------------------------- shirts

def collar(s, a, flaps=False):
    ring(s, 0.42, 0.34, 1.0, 0.05, shade(a, 0.86), r=0.14)
    if flaps:  # a shirt collar's two points, lying on the chest
        for side in (-1, 1):
            ribbon([(side * 0.05, 1.0, F - 0.03), (side * 0.2, 0.86, F - 0.03), (side * 0.3, 0.74, F - 0.025)], [(0, 0.3, -1)] * 3, 0.16, 0.025, shade(a, 0.9))


def shirt_torso(shape, a="a", b="b"):
    s = S()
    ring(s, 1.0, 0.5, -0.93, 0.045, shade(a, 0.84))  # the hem
    z = F - LIFT
    if shape in ("suit",):
        collar(s, a)
        for side in (-1, 1):  # lapels' raised edges, the tie, buttons, a pocket square
            ribbon([(side * 0.5, 1.0, F - 0.02), (side * 0.46, 0.62, F - 0.03), (side * 0.08, -0.17, F - 0.03)],
                   [(0, 0, -1)] * 3, 0.07, 0.03, shade(a, 0.7))
        s.at(0, py(7), z - 0.01).color(0xB01828).scale(0.09, 0.07, 0.04).sphere(-90, 90, 12)
        ribbon([(0, py(10), F - 0.03), (0, py(30), F - 0.035), (0, py(48), F - 0.03)], [(0, 0, -1)] * 3, 0.15, 0.03, RED)
        for y in (62, 76):
            button(s, 0, py(y), F - 0.005, 0.055, DARK)
        s.at(px(73), py(26), z).rotX(90).color(WHITE).lens(0.15, 0.05, 0.02)
    elif shape == "overalls":
        collar(s, a)
        ribbon([(-0.5, py(38), F - 0.02), (0.5, py(38), F - 0.02)], [(0, 0, -1)] * 2, 0.05, 0.03, shade(b, 0.8))  # the bib's top edge
        for x in (px(32), px(64)):  # straps over the shoulders, crossing down the back (as painted)
            xb = 0.625 * (1 if x > 0 else -1)
            ribbon([(x, py(42), F - 0.03), (x, 0.96, F - 0.03), (x, 1.03, -0.2), (xb, 1.03, 0.2), (xb, 0.96, 0.53), (-0.458 * (1 if x > 0 else -1), -0.54, 0.53)],
                   [(0, 0, -1), (0, 0.4, -1), (0, 1, 0), (0, 1, 0), (0, 0.4, 1), (0, 0, 1)], 0.13, 0.035, b)
            button(s, x, py(42), F - 0.06, 0.075, GOLD)
        plate(s, 0, py(52), F - 0.03, 0.19, 0.13, 0.025, shade(b, 0.85))  # the bib pocket
        for side in (-1, 1):
            button(s, side * 1.0, -0.55, 0, 0.06, GOLD, (side, 0, 0))
    elif shape == "vest":
        collar(s, a, flaps=True)
        for side in (-1, 1):  # the vest's front edges
            ribbon([(side * 0.21, 1.0, F - 0.025), (side * 0.0625, py(44), F - 0.03), (side * 0.125, -1.0, F - 0.025)], [(0, 0, -1)] * 3, 0.05, 0.03, shade(b, 0.72))
        for y in (52, 64, 76, 88):
            button(s, px(40), py(y), F - 0.01, 0.05, shade(b, 0.55))
    elif shape == "belt":
        collar(s, a)
        pts = rrect(1.04, 0.54, 0.06, py(71))
        s.at(0, 0, 0).color(b).tube(pts, [0.075] * len(pts), caps=False, sides=8)
        plate(s, 0, py(71), F - 0.06, 0.17, 0.17, 0.02, GOLD)
        plate(s, 0, py(71), F - 0.085, 0.09, 0.09, 0.02, b)
    elif shape == "badge":
        collar(s, a, flaps=True)
        star(s, px(68), py(34), F - 0.04, 0.21, GOLD)
        s.at(px(68), py(34), F - 0.06).color(0x6B4E12).blob(0.05, 0.05, 0.02)
        plate(s, px(27), py(31.5), F - 0.025, 0.23, 0.075, 0.02, WHITE)  # name tag
        plate(s, px(27), py(41), F - 0.035, 0.2, 0.06, 0.03, shade(a, 0.85))  # the pocket's flap
        button(s, px(27), py(42), F - 0.07, 0.035, shade(a, 0.6))
        for side in (-1, 1):  # epaulettes on the shoulders
            plate(s, side * 0.72, 1.01, 0, 0.14, 0.03, 0.4, a, (0, 1, 0))
            s.at(0, 0, 0).color(GOLD).tube(rrect(0.15, 0.41, 0.06, 1.03, side * 0.72, 0), [0.012] * 25, caps=False, sides=6)
            button(s, side * 0.6, 1.05, -0.3, 0.035, GOLD, (0, 1, 0))
    elif shape == "cross":
        collar(s, a)
        plate(s, 0, py(42), F - 0.03, 0.125, 0.375, 0.02, b)
        plate(s, 0, py(42), F - 0.031, 0.375, 0.125, 0.02, b)
        plate(s, 0, py(45), 0.53, 0.19, 0.56, 0.02, b, (0, 0, 1))
        plate(s, 0, py(45), 0.531, 0.56, 0.19, 0.02, b, (0, 0, 1))
    elif shape == "buttons":
        collar(s, a, flaps=True)
        for side in (-1, 1):
            ribbon([(side * 0.085, 1.0, F - 0.02), (side * 0.085, -0.93, F - 0.02)], [(0, 0, -1)] * 2, 0.025, 0.02, shade(a, 0.75))
        for k in range(6):
            button(s, 0, 0.85 - k * 0.33, F - 0.02, 0.058, b)
    elif shape == "torn":
        collar(s, shade(a, 0.8))
        for k in range(13):  # the ragged hem: shreds hanging all round the front and back
            x = -0.95 + k * 0.158
            for zz, n in ((F - 0.02, -1), (0.52, 1)):
                ln = 0.08 + 0.06 * ((k * 7) % 3)
                ribbon([(x, -0.93, zz), (x + 0.02, -0.93 - ln * 0.6, zz + n * 0.01), (x - 0.01, -0.93 - ln, zz + n * 0.02)], [(0, 0, n)] * 3, 0.11, 0.015, shade(a, 0.75))
    elif shape == "sash":
        collar(s, a)
        # As painted: over its left shoulder, down to its right hip, front and back.
        line = [(-0.81, 1.0), (0.0, 0.19), (0.81, -0.62), (1.0, -0.81)]
        ribbon([(x, y, F - 0.03) for x, y in line], [(0, 0, -1)] * 4, 0.27, 0.03, b)
        ribbon([(x, y, 0.53) for x, y in line], [(0, 0, 1)] * 4, 0.27, 0.03, b)
        ribbon([(-0.81, 1.0, F - 0.03), (-0.85, 1.04, 0), (-0.81, 1.0, 0.53)], [(0, 0, -1), (0, 1, 0), (0, 0, 1)], 0.27, 0.03, b)
        mx, my = px(34), py(30)
        s.at(mx, my, F - 0.07).rotX(90).color(GOLD).lathe([0, 0.12, 0.12, 0.09, 0], [0, 0, 0.02, 0.035, 0.035], 18)  # the medal
        star(s, mx, my, F - 0.1, 0.11, WHITE)
    elif shape in ("pockets", "camo"):
        collar(s, a, flaps=True)
        for side in (-1, 1):
            x = side * 0.48
            plate(s, x, 0.3, F - 0.025, 0.24, 0.22, 0.02, shade(a, 0.9))  # the pocket
            plate(s, x, 0.5, F - 0.05, 0.25, 0.07, 0.025, shade(a, 0.82))  # its flap
            button(s, x, 0.46, F - 0.08, 0.035, shade(b, 0.8) if shape == "pockets" else DARK)
    elif shape == "logo":
        collar(s, a)
        s.at(0, py(40), F - 0.015).rotX(90).color(shade(b, 0.7)).torus(0.355, 0.025, 0, 360)
        star(s, 0, py(41), F - 0.03, 0.23, WHITE)
    else:  # plain, stripe, dots: the collar and hem
        collar(s, a)


def shirt_arm(shape, side, a="a", b="b"):
    """A sleeve's cuff (and a suit's cuff buttons); side +1 = the right arm."""
    s = S()
    ring(s, 0.5, 0.5, -1.38, 0.045, shade(a, 0.84))
    if shape == "suit":
        for k in range(3):
            button(s, side * 0.5, -1.25 + k * 0.1, 0.25, 0.028, DARK, (side, 0, 0))
    elif shape == "torn":
        for k in range(4):
            x = -0.4 + k * 0.27
            ribbon([(x, -1.38, F - 0.02), (x + 0.02, -1.48, F - 0.02)], [(0, 0, -1)] * 2, 0.1, 0.015, shade(a, 0.75))


# ---------------------------------------------------------------- pants

def pants_hips(shape, a="a", b="b"):
    s = S()
    y = -0.92
    if shape == "belted":
        pts = rrect(1.04, 0.54, 0.06, y)
        s.at(0, 0, 0).color(b).tube(pts, [0.07] * len(pts), caps=False, sides=8)
        plate(s, 0, y, F - 0.07, 0.15, 0.12, 0.02, GOLD)
        plate(s, 0, y, F - 0.092, 0.08, 0.06, 0.02, b)
    else:
        ring(s, 1.0, 0.5, y, 0.05, shade(a, 0.84))  # the waistband
        for x in (-0.75, -0.3, 0.3, 0.75):  # belt loops
            ribbon([(x, y - 0.08, F - 0.05), (x, y + 0.08, F - 0.05)], [(0, 0, -1)] * 2, 0.05, 0.02, shade(a, 0.8))
        for x in (-0.6, 0.6):
            ribbon([(x, y - 0.08, 0.55), (x, y + 0.08, 0.55)], [(0, 0, 1)] * 2, 0.05, 0.02, shade(a, 0.8))
        if shape in ("jeans", "pinstripe", "plain"):
            button(s, 0, y, F - 0.07, 0.04, METAL if shape == "jeans" else shade(a, 0.6))


def pants_leg(shape, side, a="a", b="b"):
    """One leg; side +1 = the right leg (its outer side is +x)."""
    s = S()
    out = side * 0.5
    if shape != "bands":
        ring(s, 0.5, 0.5, -1.88, 0.04, shade(a, 0.84))  # the hem
    if shape == "jeans":
        ring(s, 0.5, 0.5, -1.78, 0.05, shade(a, 1.1))  # rolled cuff
        for x, y in ((-0.38 * side, -0.12), (0.4 * side, -0.25)):  # rivets
            s.at(x, y, F - 0.02).color(METAL).blob(0.025, 0.025, 0.015)
        plate(s, 0, -0.38, 0.52, 0.28, 0.22, 0.02, shade(a, 0.92), (0, 0, 1))  # back pocket
        s.at(0, 0, 0).color(GOLD).tube([(-0.24, -0.3, 0.55), (0, -0.4, 0.555), (0.24, -0.3, 0.55)], [0.008] * 3, caps=False, sides=4)
    elif shape in ("cargo", "camo"):
        plate(s, out + side * 0.025, -1.0, 0, 0.3, 0.27, 0.025, shade(a, 0.9), (side, 0, 0))  # side pocket
        plate(s, out + side * 0.05, -0.78, 0, 0.31, 0.07, 0.028, shade(a, 0.82), (side, 0, 0))  # its flap
        button(s, out + side * 0.08, -0.82, 0, 0.035, shade(b, 0.8), (side, 0, 0))
    elif shape == "stripe":
        ribbon([(out + side * 0.02, -0.02, 0), (out + side * 0.02, -1.9, 0)], [(side, 0, 0)] * 2, 0.12, 0.03, b)  # side piping
    elif shape == "knee":
        plate(s, 0, -1.12, F - 0.04, 0.32, 0.2, 0.04, b)  # knee pad
        for y in (-0.98, -1.26):
            ribbon([(-0.5, y, F - 0.01), (0.5, y, F - 0.01), (0.52, y, 0.5)], [(0, 0, -1), (0, 0, -1), (side, 0, 0)], 0.05, 0.02, shade(b, 0.7))
    elif shape == "bands":
        for y in (-1.72, -1.86):
            ring(s, 0.5, 0.5, y, 0.05, b)
    elif shape == "pinstripe":
        ribbon([(0, -0.05, F - 0.012), (0, -1.88, F - 0.012)], [(0, 0, -1)] * 2, 0.02, 0.012, shade(a, 0.75))  # pressed crease


SHIRTS = ("plain", "stripe", "logo", "suit", "overalls", "vest", "belt", "badge", "cross", "buttons", "torn", "camo", "sash", "pockets", "dots")
PANTS = ("jeans", "belted", "cargo", "stripe", "knee", "bands", "pinstripe", "plaid", "camo", "plain")
