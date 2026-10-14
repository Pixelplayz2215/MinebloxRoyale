"""
The Locker's hats, hair, face accessories and shoes, made in Blender (v1.0.1, creator: cosmetics like real Roblox catalog
items, rebuilt in Blender). Ported from the code-drawn ones line for line (shapes.S is the Java Smooth), so every piece
keeps its size and fit; each is one model per shape, in the item's two colours (tint channels 1 and 2).

Head joint space (studs, y up, the face towards -z), measured on the default Roblox head as the game measures it
(Cosmetics.bounds): the head spans x and z +-0.599, y 0 .. 1.2. Hair under a hat stops where that hat starts, so each
haircut is made for each kind of hat edge (hair_<shape>_<cut>); hats worn over an afro are made to fit round it
(hat_<shape>_afro). Shoes: a leg's joint space (x, z +-0.5, y -2 .. 0). Back bling: backbling.py.
"""
import math

from shapes import S, shade

# The default head (Cosmetics.Head from its bounds).
E = (-0.5989, -0.0012, -0.5989, 0.5989, 1.2012, 0.5989)


class Head:
    def __init__(self, cx, cy, cz, hw, hh, hd, top, bottom, front, back, eye, brow):
        self.cx, self.cy, self.cz, self.hw, self.hh, self.hd = cx, cy, cz, hw, hh, hd
        self.top, self.bottom, self.front, self.back, self.eye, self.brow = top, bottom, front, back, eye, brow

    @staticmethod
    def of(e):
        cy = (e[1] + e[4]) / 2
        return Head((e[0] + e[3]) / 2, cy, (e[2] + e[5]) / 2, (e[3] - e[0]) / 2, (e[4] - e[1]) / 2, (e[5] - e[2]) / 2, e[4], e[1], e[2], e[5], cy + 0.23, e[4] - 0.22)


HEAD = Head.of(E)


def shell(s, h, grow, y0, y1, rc, closed, a0, a1, color):
    p = []
    R, rr = grow, rc / h.hw
    if closed:
        p.append((0, y0))
        for i in range(6):
            t = math.radians(-90 + 90 * i / 5)
            p.append((R - rr + rr * math.cos(t), y0 + rc + rc * math.sin(t)))
    else:
        p.append((R, y0))
    for i in range(7):
        t = math.radians(90 * i / 6)
        p.append((R - rr + rr * math.cos(t), y1 - rc + rc * math.sin(t)))
    p.append((0, y1))
    s.at(h.cx, 0, h.cz).scale(h.hw, 1, h.hd).color(color).lathe([q[0] for q in p], [q[1] for q in p], 32, a0, a1)


def wrap(s, h, grow, y0, y1, a0, a1, color):
    def fn(u, v):
        ang = math.radians(a0 + (a1 - a0) * u)
        edge = math.sqrt(min(math.sin(math.pi * u), math.sin(math.pi * v)))
        r = 1.006 + (grow - 1.006) * edge
        return (h.cx + math.cos(ang) * h.hw * r, y0 + (y1 - y0) * v, h.cz + math.sin(ang) * h.hd * r)
    s.at(0, 0, 0).color(color).surface(fn, 32, 12)


# ---------------------------------------------------------------- hats

def hat(s, shape, h, a="a", b="b"):
    hw, hd, cx, cz, top, cy, brow = h.hw, h.hd, h.cx, h.cz, h.top, h.cy, h.brow
    oval = hd / hw
    if shape == "tophat":
        s.at(cx, top - 0.08, cz).scale(hw, 1, hw * oval).color(a).lathe([0, 1.5, 1.56, 1.5, 0], [0, 0, 0.04, 0.08, 0.08], 32)
        s.at(cx, top - 0.04, cz).scale(hw, 1, hw * oval).color(a).lathe([0, 0.82, 0.85, 0.85, 0.81, 0], [0, 0, 0.04, 1.0, 1.04, 1.04], 32)
        s.at(cx, top - 0.04, cz).scale(hw, 1, hw * oval).color(b).lathe([0.862, 0.862], [0.1, 0.32], 32)
    elif shape in ("cap", "backcap"):
        d = 1 if shape == "cap" else -1
        shell(s, h, 1.07, brow, top + 0.08, 0.3, False, 0, 360, a)
        s.at(cx, brow + 0.02, cz - d * hd * 1.05).color(a).ellipsoid(0.85 * hw, 0.035, 0.5)
        s.at(cx, top + 0.09, cz).color(b).ellipsoid(0.07, 0.04, 0.07)
        s.at(cx, brow + 0.17, cz - d * hd * 1.06).color(b).ellipsoid(0.2, 0.1, 0.025)
    elif shape == "crown":
        base = top - 0.08
        s.at(cx, base, cz).scale(hw, 1, hw * oval).color(a).lathe([0.98, 1.06, 1.06, 0.98, 0.98], [0, 0, 0.3, 0.3, 0], 32)
        for i in range(6):
            ang = math.pi * 2 * i / 6 + math.pi / 2
            px, pz = cx + math.cos(ang) * 1.02 * hw, cz + math.sin(ang) * 1.02 * hd
            s.at(px, base + 0.29, pz).color(a).lathe([0, 0.09, 0.0], [0, 0, 0.24], 12)
            s.at(px, base + 0.55, pz).color(a).ellipsoid(0.045, 0.045, 0.045)
        s.at(cx, base + 0.15, cz - 1.06 * hd).color(b).ellipsoid(0.09, 0.09, 0.045)
        for side in (-1, 1):
            s.at(cx + side * 0.75 * hw, base + 0.15, cz - 0.8 * hd).color(0x2E7BE8).ellipsoid(0.06, 0.06, 0.04)
    elif shape == "tiara":
        y, r, dip = top - 0.18, 1.07, 0.22
        band = []
        for i in range(25):
            ang = math.radians(180 + 180 * i / 24)
            c2 = math.cos(ang) ** 2
            band.append((cx + math.cos(ang) * r * hw, y - dip * c2, cz + math.sin(ang) * r * hd))
        rad = [0.032] * len(band)
        rad[0] = rad[-1] = 0.02
        s.at(0, 0, 0).color(a).tube(band, rad)
        for deg, ht in ((270, 0.36), (247, 0.25), (293, 0.25), (224, 0.15), (316, 0.15)):
            ang = math.radians(deg)
            px, pz = cx + math.cos(ang) * r * hw, cz + math.sin(ang) * r * hd
            py = y - dip * math.cos(ang) ** 2
            s.at(px, py, pz).rotY(-deg + 270).scale(1, 1, 0.45).color(a).lathe([0, 0.08, 0], [0, 0, ht], 12)
            s.at(px, py + ht + 0.025, pz).color(b if deg == 270 else a).ellipsoid(0.04, 0.04, 0.04)
        s.at(cx, y + 0.06, cz - r * hd - 0.02).color(b).ellipsoid(0.065, 0.065, 0.03)
    elif shape == "cone":
        y0 = top - 0.1
        s.at(cx, y0, cz).scale(hw, 1, hw).color(a).lathe([0, 1.25, 1.27, 1.25, 0], [0, 0, 0.04, 0.08, 0.08], 32)
        s.at(cx, y0, cz).scale(hw, 1, hw).color(a).lathe([0, 1.0, 0.06, 0], [0.08, 0.08, 1.45, 1.47], 32)
        s.at(cx, y0, cz).scale(hw, 1, hw).color(b).lathe([0.725, 0.505], [0.5, 0.82], 32)
    elif shape == "headphones":
        s.at(cx, cy, cz).rotX(90).color(a).torus(hw + 0.13, 0.06, 180, 360)
        for side in (-1, 1):
            s.at(cx + side * (hw + 0.1), cy, cz).rotZ(90).color(b).lathe([0, 0.3, 0.33, 0.33, 0.3, 0], [-0.11, -0.11, -0.08, 0.08, 0.11, 0.11], 24)
            s.at(cx + side * (hw + 0.1), cy, cz).rotZ(90).color(a).torus(0.33, 0.035, 0, 360)
    elif shape == "viking":
        shell(s, h, 1.09, brow, top + 0.14, 0.45, False, 0, 360, a)
        s.at(cx, brow, cz).scale(1, 1, oval).color(a).torus(1.09 * hw, 0.06, 0, 360)
        s.at(cx, cy, cz).rotX(90).color(a).torus(1.1 * hw + 0.02, 0.045, 180, 360)
        for side in (-1, 1):
            x = cx + side * hw
            s.at(0, 0, 0).color(b).tube([(x - side * 0.05, brow + 0.2, cz), (x + side * 0.25, brow + 0.3, cz), (x + side * 0.42, brow + 0.55, cz),
                                         (x + side * 0.45, brow + 0.87, cz), (x + side * 0.36, brow + 1.1, cz)], [0.12, 0.1, 0.08, 0.05, 0])
    elif shape == "fedora":
        s.at(cx, brow + 0.04, cz).scale(hw, 1, hw * oval).color(a).lathe([0, 1.55, 1.66, 1.55, 0], [0, 0, 0.1, 0.05, 0.05], 32)
        s.at(cx, brow + 0.04, cz).scale(hw, 1, hw * oval).color(a).lathe([0, 1.03, 1.03, 0.88, 0.55, 0], [0.03, 0.03, 0.2, 0.66, 0.7, 0.56], 32)
        s.at(cx, brow + 0.04, cz).scale(hw, 1, hw * oval).color(b).lathe([1.045, 1.035], [0.07, 0.24], 32)
    elif shape == "beanie":
        shell(s, h, 1.08, brow + 0.05, top + 0.2, 0.5, False, 0, 360, a)
        s.at(cx, brow, cz).scale(hw, 1, hw * oval).color(a).lathe([1.04, 1.13, 1.15, 1.13, 1.04], [0, 0, 0.11, 0.22, 0.22], 32)
        s.at(cx, top + 0.3, cz).color(b).ellipsoid(0.18, 0.18, 0.18)
    elif shape == "cowboy":
        s.at(cx, brow + 0.04, cz).scale(hw, 1, hw * oval).color(a).lathe([0, 1.75, 1.86, 1.75, 0], [0, 0, 0.2, 0.08, 0.08], 32)
        s.at(cx, brow + 0.04, cz).scale(hw, 1, hw * oval).color(a).lathe([0, 1.03, 1.03, 0.86, 0.5, 0.33, 0], [0.04, 0.04, 0.3, 0.72, 0.76, 0.68, 0.68], 32)
        s.at(cx, brow + 0.04, cz).scale(hw, 1, hw * oval).color(b).lathe([1.045, 1.035], [0.1, 0.26], 32)
    elif shape == "halo":
        s.at(cx, top + 0.38, cz).rotX(8).color(a, glow=True).torus(0.55, 0.055, 0, 360)
    elif shape == "bunny":
        s.at(cx, cy, cz).rotX(90).color(a).torus(hw + 0.04, 0.035, 180, 360)
        for side in (-1, 1):
            s.at(cx + side * 0.3, top + 0.42, cz + 0.05).rotZ(-side * 12).color(a).ellipsoid(0.13, 0.5, 0.07)
            s.at(cx + side * 0.3, top + 0.42, cz - 0.02).rotZ(-side * 12).color(b).ellipsoid(0.07, 0.38, 0.03)
    elif shape == "cat":
        s.at(cx, cy, cz).rotX(90).color(a).torus(hw + 0.04, 0.035, 180, 360)
        for side in (-1, 1):
            s.at(cx + side * 0.62 * hw, top - 0.06, cz).rotZ(-side * 15).scale(1, 1, 0.45).color(a).lathe([0, 0.22, 0], [0, 0, 0.4], 16)
            s.at(cx + side * 0.62 * hw, top - 0.03, cz - 0.06).rotZ(-side * 15).scale(1, 1, 0.3).color(b).lathe([0, 0.13, 0], [0, 0, 0.27], 16)
    elif shape == "witch":
        s.at(cx, brow + 0.06, cz).scale(hw, 1, hw * oval).color(a).lathe([0, 1.7, 1.74, 1.7, 0], [0, 0, 0.03, 0.06, 0.06], 32)
        s.at(0, 0, 0).color(a).tube([(cx, brow + 0.08, cz), (cx, top + 0.38, cz), (cx, top + 0.82, cz + 0.06), (cx, top + 1.12, cz + 0.26), (cx, top + 1.26, cz + 0.56)],
                                    [1.03 * hw, 0.66 * hw, 0.38 * hw, 0.2 * hw, 0], caps=False, sides=24)
        s.at(cx, brow + 0.08, cz).scale(hw, 1, hw).color(b).lathe([1.045, 1.0], [0.02, 0.2], 32)
    elif shape == "party":
        s.at(cx + 0.08, top - 0.08, cz).rotZ(-10).color(a).lathe([0, 0.55 * hw, 0], [0, 0, 1.0], 24)
        for y in (0.25, 0.58):
            s.at(cx + 0.08, top - 0.08, cz).rotZ(-10).color(b).lathe([0.55 * hw * (1 - y) + 0.01, 0.55 * hw * (0.88 - y) + 0.01], [y, y + 0.12], 24)
        s.at(cx + 0.08, top - 0.08, cz).rotZ(-10).move(0, 1.02, 0).color(b).ellipsoid(0.12, 0.12, 0.12)
    elif shape == "chef":
        shell(s, h, 1.05, brow + 0.04, top + 0.2, 0.1, False, 0, 360, a)
        s.at(cx, top + 0.45, cz).color(a).ellipsoid(1.15 * hw, 0.36, 1.15 * hd)
        for i in range(6):
            ang = math.pi * 2 * i / 6
            s.at(cx + math.cos(ang) * 0.75 * hw, top + 0.45, cz + math.sin(ang) * 0.75 * hd).color(a).ellipsoid(0.32, 0.3, 0.32)
        s.at(cx, brow + 0.12, cz).scale(hw, 1, hw * oval).color(b).lathe([1.06, 1.06], [0, 0.06], 32)
    elif shape == "tricorne":
        s.at(cx, brow + 0.08, cz).rotY(90).scale(hw, 1, hw).color(a).lathe([0, 1.75, 1.85, 1.75, 0], [0, 0, 0.3, 0.14, 0.14], 3)
        shell(s, h, 1.04, brow + 0.1, top + 0.16, 0.4, False, 0, 360, a)
        s.at(cx, brow + 0.38, cz - hd * 1.05).color(b).ellipsoid(0.12, 0.12, 0.04)
        for side in (-1, 1):
            s.at(cx + side * 0.07, brow + 0.26, cz - hd * 1.05).rotZ(side * 40).color(b).ellipsoid(0.13, 0.03, 0.03)
    elif shape == "police":
        shell(s, h, 1.05, brow + 0.02, brow + 0.28, 0.02, False, 0, 360, a)
        s.at(cx, brow + 0.26, cz + 0.03).scale(hw, 1, hw * oval).color(a).lathe([0, 1.28, 1.32, 1.26, 0], [0, 0, 0.06, 0.14, 0.16], 32)
        s.at(cx, brow + 0.03, cz - hd * 1.0).rotX(-12).color(0x111114).ellipsoid(0.8 * hw, 0.035, 0.4)
        s.at(cx, brow + 0.17, cz - hd * 1.07).color(b).ellipsoid(0.1, 0.1, 0.03)
    elif shape == "space_helmet":
        s.at(cx, cy + 0.04, cz).color(a).ellipsoid(1.42 * hw, 1.3 * h.hh, 1.42 * hd)
        s.at(cx, cy + 0.04, cz).scale(1.43 * hw, 1.31 * h.hh, 1.43 * hd).color(b).sphere(-28, 42, 32, 215, 325)
        s.at(cx, h.bottom - 0.05, cz).scale(1, 1, oval).color(0xB8C4D6).torus(1.25 * hw, 0.08, 0, 360)
    elif shape == "knight":
        shell(s, h, 1.1, h.bottom - 0.04, top + 0.08, 0.4, False, 0, 360, a)
        s.at(cx, h.eye, cz - hd * 1.1).color(0x111114).ellipsoid(0.75 * hw, 0.05, 0.04)
        for i in range(4):
            s.at(cx + 0.18 * (i - 1.5) * hw, h.cy - 0.22, cz - hd * 1.1).color(0x111114).ellipsoid(0.03, 0.03, 0.03)
        s.at(0, 0, 0).color(b).tube([(cx, top + 0.06, cz - 0.3 * hd), (cx, top + 0.35, cz), (cx, top + 0.3, cz + 0.5 * hd), (cx, top - 0.05, cz + 1.0 * hd)], [0.06, 0.12, 0.12, 0.04])
    elif shape == "fire_helmet":
        shell(s, h, 1.09, brow + 0.02, top + 0.18, 0.45, False, 0, 360, a)
        s.at(cx, brow + 0.02, cz + 0.15).color(a).ellipsoid(1.4 * hw, 0.035, 1.4 * hd)
        s.at(0, 0, 0).color(a).tube([(cx, brow + 0.12, cz - hd * 1.05), (cx, top + 0.22, cz), (cx, brow + 0.25, cz + hd * 1.08)], [0.05, 0.06, 0.05])
        s.at(cx, brow + 0.25, cz - hd * 1.1).color(b).ellipsoid(0.17, 0.2, 0.04)
    elif shape == "army":
        shell(s, h, 1.14, brow, top + 0.16, 0.5, False, 0, 360, a)
        s.at(cx, brow, cz).scale(1, 1, oval).color(b).torus(1.14 * hw, 0.05, 0, 360)
    elif shape == "pith":
        shell(s, h, 1.05, brow + 0.04, top + 0.26, 0.5, False, 0, 360, a)
        s.at(cx, brow + 0.06, cz).scale(hw, 1, hw * oval).color(a).lathe([0, 1.5, 1.56, 1.5, 0], [-0.02, -0.12, -0.09, -0.04, 0.06], 32)
        s.at(cx, brow + 0.06, cz).scale(hw, 1, hw * oval).color(b).lathe([1.07, 1.06], [0.04, 0.16], 32)
    elif shape == "robot_helmet":
        y0, y1 = h.bottom - 0.06, top + 0.1
        H, R = y1 - y0, 1.66
        plate = R * 0.7071
        s.at(cx, y0, cz).rotY(45).scale(hw, 1, hd).color(a).lathe([0, R - 0.08, R, R, R - 0.08, 0], [0, 0, 0.06, H - 0.06, H, H], 4)
        s.at(cx, h.eye, cz - hd * (plate + 0.009)).color(b, glow=True).ellipsoid(0.75 * hw, 0.13, 0.03)
        s.at(0, 0, 0).color(a).tube([(cx + 0.25, y1, cz), (cx + 0.3, y1 + 0.4, cz)], [0.03, 0.03])
        s.at(cx + 0.3, y1 + 0.45, cz).color(b, glow=True).ellipsoid(0.08, 0.08, 0.08)
    elif shape in ("dominus", "dominus_horns"):
        shell(s, h, 1.2, h.bottom - 0.1, top + 0.28, 0.55, False, 0, 360, a)
        s.at(cx, cy - 0.45, cz + 0.45 * hd).color(a).ellipsoid(1.05 * hw, 0.55, 0.6 * hd)
        fz = cz - hd * 1.2
        s.at(cx, cy - 0.02, fz).color(0x07070B).ellipsoid(0.72 * hw, 0.66 * h.hh, 0.06)
        s.at(cx, cy - 0.02, fz).rotX(90).scale(1, 1, 0.92 * h.hh / hw).color(b).torus(0.74 * hw, 0.05, 0, 360)
        crest = []
        for i in range(7):
            t = math.radians(25 + 130 * i / 6)
            crest.append((cx, cy + 0.1 + math.sin(t) * (h.hh * 1.2 + 0.32), cz - math.cos(t) * hd * 1.24))
        s.at(0, 0, 0).color(b).tube(crest, [0.06] * len(crest))
        for side in (-1, 1):
            if shape == "dominus":
                for k in range(3):
                    s.at(cx + side * 1.18 * hw, cy + 0.12 + 0.14 * k, cz + 0.15).rotY(-side * 35).rotZ(side * (12 + 20 * k)).move(side * 0.36, 0, 0).color(b).ellipsoid(0.42 - 0.06 * k, 0.08, 0.035)
            else:
                x = cx + side * 1.15 * hw
                s.at(0, 0, 0).color(b).tube([(x, cy + 0.25, cz), (x + side * 0.25, cy + 0.42, cz + 0.15), (x + side * 0.3, cy + 0.8, cz + 0.32), (x + side * 0.18, cy + 1.1, cz + 0.45)],
                                            [0.12, 0.09, 0.05, 0])


def afro_headphones(s, h, a="a", b="b"):
    ax, ay, acy, z = 1.45 * h.hw + 0.1, 0.75 + 0.1, h.top + 0.1, h.cz + 0.1
    cup_x, cup_y = h.hw + 0.12, h.cy + 0.02
    for side in (-1, 1):
        s.at(h.cx + side * cup_x, cup_y, h.cz).rotZ(90).color(b).lathe([0, 0.3, 0.33, 0.33, 0.3, 0], [-0.11, -0.11, -0.08, 0.08, 0.11, 0.11], 24)
        s.at(h.cx + side * cup_x, cup_y, h.cz).rotZ(90).color(a).torus(0.33, 0.035, 0, 360)
    pts = [(h.cx - cup_x, cup_y + 0.3, z)]
    for i in range(17):
        t = math.pi * i / 16
        pts.append((h.cx - ax * math.cos(t), acy + ay * math.sin(t), z))
    pts.append((h.cx + cup_x, cup_y + 0.3, z))
    s.at(0, 0, 0).color(a).tube(pts, [0.06] * len(pts))


def over_afro(shape, h):
    """The head a hat measures itself against when it's worn over an afro (Cosmetics.draw)."""
    if shape == "tiara":
        return Head(h.cx, h.cy, h.cz + 0.25, h.hw * 1.38, h.hh, h.hd * 1.38, h.top + 0.3, h.bottom, h.front, h.back, h.eye, h.brow)
    return Head(h.cx, h.cy + 0.12, h.cz + 0.25, h.hw * 1.45, h.hh, h.hd * 1.45, h.top + 0.8, h.bottom, h.front, h.back, h.eye, h.brow)


# ---------------------------------------------------------------- hair

def hat_edge(shape, h):
    if shape in ("cap", "backcap", "viking", "beanie", "army", "tricorne"):
        return h.brow
    if shape in ("police", "fire_helmet"):
        return h.brow + 0.02
    if shape in ("chef", "pith", "fedora", "cowboy"):
        return h.brow + 0.04
    if shape == "witch":
        return h.brow + 0.06
    return h.top - 0.1


# The hat edges a haircut is made for (cut name -> a hat shape with that edge; None = no hat, or one that sits over hair).
CUTS = {"full": None, "b0": "cap", "b2": "police", "b4": "fedora", "b6": "witch", "top": "tophat"}
OVER_HAIR = ("headphones", "halo", "bunny", "cat", "tiara")


def hair_cap(s, h, grow, front_y, back_y, y1, rc, color):
    def fn(u, v):
        ang = u * math.pi * 2
        c, sn = math.cos(ang), math.sin(ang)
        t = (1 - sn) / 2
        t = t * t * (3 - 2 * t)
        y0 = min(y1 - 0.08, back_y + (front_y - back_y) * t)
        rr = min(rc, (y1 - y0) * 0.8) / h.hw
        if v < 0.1:
            k = v / 0.1
            r = 1.006 + (grow - 1.006) * math.sin(k * math.pi / 2)
            y = y0 + 0.05 * k
        elif v < 0.55:
            r = grow
            y = y0 + 0.05 + (y1 - rr * h.hw - y0 - 0.05) * (v - 0.1) / 0.45
        elif v < 0.85:
            q = (v - 0.55) / 0.3 * math.pi / 2
            r = grow - rr + rr * math.cos(q)
            y = y1 - rr * h.hw + rr * h.hw * math.sin(q)
        else:
            r = (grow - rr) * (1 - (v - 0.85) / 0.15)
            y = y1
        return (h.cx + c * h.hw * r, y, h.cz + sn * h.hd * r)
    s.at(0, 0, 0).color(color).surface(fn, 48, 24, one_sided=True)  # (the head fills it)


def hair(s, shape, h, hat_shape=None, a="a", b="b"):
    under_hat = hat_shape is not None and hat_shape not in OVER_HAIR
    hw, hd, cx, cz, top, cy = h.hw, h.hd, h.cx, h.cz, h.top, h.cy
    front, back = h.top - 0.13, (cy - 0.05 if shape == "buzz" else cy - 0.32)
    edge = hat_edge(hat_shape, h) if under_hat else top
    hat_top = (top + 0.05) if not under_hat else (top if edge >= top - 0.1 else edge + 0.03)
    grow = 1.025 if shape == "buzz" or under_hat else 1.045
    brim = under_hat and hat_top < top
    hair_cap(s, h, grow, front, back, hat_top, 0.02 if brim else 0.3 if under_hat else 0.4, a)
    tuck = hat_top
    if shape == "long":
        s.at(cx, cy - 0.45, cz + 0.62 * hd).color(a).ellipsoid(1.06 * hw, 0.7, 0.6 * hd)
        for sd in (-1, 1):
            s.at(cx + sd * 1.0 * hw, cy - 0.2, cz + 0.12 * hd).color(a).ellipsoid(0.17, 0.6, 0.42 * hd)
    elif shape == "spiky":
        if not under_hat:
            for i in range(7):
                ang = math.pi * 2 * i / 7
                px, pz = cx + math.cos(ang) * 0.55 * hw, cz + math.sin(ang) * 0.55 * hd
                s.at(px, top + 0.02, pz).rotZ(-math.cos(ang) * 25).rotX(math.sin(ang) * 25).color(a).lathe([0, 0.17, 0], [0, 0, 0.42], 10)
    elif shape == "mohawk":
        if not under_hat:
            for i in range(6):
                s.at(cx, top + 0.02, cz - hd * 0.85 + i * hd * 0.34).rotX(-20 + i * 8).scale(0.4, 1, 1).color(a).lathe([0, 0.2, 0], [0, 0, 0.5 - abs(i - 2.5) * 0.05], 10)
    elif shape == "bun":
        if not under_hat:
            s.at(cx, top + 0.04, cz + 0.45 * hd).color(a).ellipsoid(0.26, 0.24, 0.26)
        else:
            s.at(cx, cy + 0.1, cz + 1.12 * hd).color(a).ellipsoid(0.24, 0.24, 0.2)
    elif shape == "ponytail":
        s.at(cx, top - 0.18, cz + 1.06 * hd).color(b).ellipsoid(0.1, 0.1, 0.08)
        s.at(0, 0, 0).color(a).tube([(cx, top - 0.18, cz + 1.08 * hd), (cx, top - 0.35, cz + 1.35 * hd), (cx, cy - 0.3, cz + 1.38 * hd), (cx, cy - 0.7, cz + 1.18 * hd)],
                                    [0.14, 0.13, 0.1, 0.02])
    elif shape == "afro":
        if not under_hat:
            s.at(cx, top + 0.1, cz + 0.25).color(a).ellipsoid(1.45 * hw, 0.75, 1.45 * hd)
    elif shape == "pigtails":
        for sd in (-1, 1):
            s.at(cx + sd * 1.3 * hw, cy + 0.05, cz + 0.2).color(a).ellipsoid(0.25, 0.28, 0.25)
            s.at(cx + sd * 1.08 * hw, cy + 0.08, cz + 0.15).color(b).ellipsoid(0.07, 0.09, 0.09)
    elif shape == "swoop":
        if not under_hat:
            s.at(cx + 0.12, h.brow + 0.1, cz - hd * 1.0).rotZ(-22).rotX(-10).color(a).ellipsoid(0.62 * hw, 0.13, 0.14)
    elif shape == "bob":
        for sd in (-1, 1):
            s.at(cx + sd * 1.0 * hw, cy - 0.12, cz + 0.1 * hd).color(a).ellipsoid(0.2, 0.45, 0.5 * hd)
        s.at(cx, cy - 0.3, cz + 0.5 * hd).color(a).ellipsoid(1.0 * hw, 0.4, 0.5 * hd)
    elif shape == "curly":
        tiara = hat_shape == "tiara"
        for i in range(12):
            ang = math.pi * 2 * i / 12
            if (under_hat or tiara) and math.sin(ang) < -0.2:
                continue
            yy = (min(tuck, top) - 0.25 - (i % 2) * 0.12) if under_hat else (top - 0.12 - (i % 2) * 0.2)
            s.at(cx + math.cos(ang) * 1.04 * hw, yy, cz + math.sin(ang) * 1.04 * hd).color(a).ellipsoid(0.17, 0.17, 0.17)
    elif shape == "messy":
        if not under_hat:
            for i in range(6):
                ang = math.pi * 2 * i / 6 + 0.4
                s.at(cx + math.cos(ang) * 0.6 * hw, top + 0.08, cz + math.sin(ang) * 0.6 * hd).rotZ(25 * (1 if i % 2 == 0 else -1)).rotX(20 * (i % 3 - 1)).color(a).ellipsoid(0.12, 0.24, 0.12)


# ---------------------------------------------------------------- face accessories

def glasses_frame(s, h, y, color, reach):
    cx, front, hw = h.cx, h.front, h.hw
    s.at(0, 0, 0).color(color).tube([(cx - 0.07, y + 0.03, front - 0.04), (cx, y + 0.06, front - 0.05), (cx + 0.07, y + 0.03, front - 0.04)], [0.022] * 3)
    for sd in (-1, 1):
        s.at(0, 0, 0).color(color).tube([(h.cx + sd * reach, y + 0.03, front - 0.03), (h.cx + sd * (hw - 0.02), y + 0.04, front + 0.08), (h.cx + sd * (hw + 0.01), y + 0.03, h.cz + 0.15)], [0.022] * 3)


def face(s, shape, h, a="a", b="b"):
    hw, hd, cx, cz, cy, front, ey = h.hw, h.hd, h.cx, h.cz, h.cy, h.front, h.eye
    if shape in ("shades", "shades_low"):
        y = cy + 0.1 if shape == "shades_low" else ey
        for sd in (-1, 1):
            s.at(cx + sd * 0.22, y, front - 0.035).color(b).ellipsoid(0.19, 0.12, 0.035)
        glasses_frame(s, h, y, a, 0.4)
    elif shape == "aviators":
        for sd in (-1, 1):
            s.at(cx + sd * 0.22, ey - 0.02, front - 0.035).rotZ(sd * 12).color(b).ellipsoid(0.17, 0.14, 0.035)
        glasses_frame(s, h, ey + 0.06, a, 0.38)
    elif shape in ("glasses", "nerd"):
        t = 0.045 if shape == "nerd" else 0.026
        for sd in (-1, 1):
            s.at(cx + sd * 0.2, ey, front - 0.035).rotX(90).scale(1, 1, 0.8 if shape == "nerd" else 1).color(a).torus(0.15, t, 0, 360)
        glasses_frame(s, h, ey, a, 0.35)
    elif shape == "goggles":
        for sd in (-1, 1):
            s.at(cx + sd * 0.21, ey, front - 0.02).rotX(-90).color(a).lathe([0, 0.17, 0.17, 0], [0, 0, 0.08, 0.08], 20)
            s.at(cx + sd * 0.21, ey, front - 0.105).color(b).ellipsoid(0.15, 0.15, 0.01)
        s.at(cx, ey, cz).scale(1, 1, hd / hw).color(a).torus(hw * 1.04, 0.04, 0, 360)
    elif shape == "monocle":
        s.at(cx + 0.2, ey, front - 0.035).rotX(90).color(a).torus(0.15, 0.025, 0, 360)
        s.at(0, 0, 0).color(a).tube([(cx + 0.33, ey - 0.06, front - 0.03), (cx + 0.4, cy - 0.25, front - 0.03), (cx + 0.45, h.bottom - 0.05, front + 0.05)], [0.012] * 3)
    elif shape == "mustache":
        for sd in (-1, 1):
            s.at(cx + sd * 0.13, cy - 0.04, front - 0.025).rotZ(sd * 15).color(a).ellipsoid(0.15, 0.06, 0.045)
            s.at(cx + sd * 0.28, cy + 0.01, front - 0.015).color(a).ellipsoid(0.055, 0.055, 0.04)
    elif shape in ("beard", "long_beard"):
        wrap(s, h, 1.1, h.bottom + 0.04, cy - 0.04, 175, 365, a)
        s.at(cx, h.bottom + 0.06, front + 0.1).color(a).ellipsoid(0.55 * hw, 0.18, 0.16)
        for sd in (-1, 1):
            s.at(cx + sd * 0.13, cy - 0.04, front - 0.03).rotZ(sd * 12).color(a).ellipsoid(0.16, 0.06, 0.05)
        if shape == "long_beard":
            s.at(cx, h.bottom - 0.45, front + 0.1).color(a).ellipsoid(0.38 * hw, 0.55, 0.16)
    elif shape == "eyepatch":
        s.at(cx + 0.2, ey, front - 0.03).color(a).ellipsoid(0.15, 0.13, 0.03)
        s.at(cx, ey + 0.08, cz).rotZ(-14).scale(1, 1, hd / hw).color(a).torus(hw * 1.03, 0.018, 0, 360)
    elif shape in ("ski_mask", "ninja_mask"):
        shell(s, h, 1.05, h.bottom - 0.04, h.top + 0.04, 0.3, True, 0, 360, a)
        if shape == "ninja_mask":
            s.at(cx, ey, front - 0.035).color(b).ellipsoid(0.72 * hw, 0.12, 0.03)
        for sd in (-1, 1):
            if shape == "ski_mask":
                s.at(cx + sd * 0.2, ey, front - 0.035).color(b).ellipsoid(0.11, 0.09, 0.025)
            s.at(cx + sd * 0.2, ey, front - 0.055).color(0x111114).ellipsoid(0.045, 0.06, 0.015)
    elif shape == "hockey_mask":
        wrap(s, h, 1.09, h.bottom + 0.04, h.top - 0.06, 190, 350, a)
        for sd in (-1, 1):
            s.at(cx + sd * 0.2, ey, front - 0.055).color(0x111114).ellipsoid(0.12, 0.08, 0.02)
            s.at(cx + sd * 0.26, ey + 0.2, front - 0.05).rotZ(sd * 30).color(b).ellipsoid(0.12, 0.025, 0.02)
        for i in range(6):
            s.at(cx + (i % 3 - 1) * 0.12, cy - 0.2 - (i // 3) * 0.1, front - 0.055).color(0x111114).ellipsoid(0.025, 0.025, 0.015)
    elif shape in ("surgical", "bandana"):
        wrap(s, h, 1.06, h.bottom + 0.06, cy + 0.04, 180, 360, a)
        if shape == "surgical":
            for sd in (-1, 1):
                s.at(0, 0, 0).color(b).tube([(cx + sd * hw * 1.0, cy, front + 0.25), (cx + sd * hw * 1.05, ey, cz + 0.1)], [0.015, 0.015])
        else:
            s.at(cx, h.bottom - 0.02, front + 0.02).color(a).ellipsoid(0.25, 0.16, 0.08)
            for i in range(5):
                s.at(cx + (i - 2) * 0.13, cy - 0.2 + (i % 2) * 0.08, front - 0.05).color(b).ellipsoid(0.025, 0.025, 0.01)
    elif shape == "domino":
        s.at(cx, ey, cz).scale(hw, 1, hd).color(a).lathe([1.04, 1.04], [-0.1, 0.1], 24, 200, 340)
        for sd in (-1, 1):
            s.at(cx + sd * 0.2, ey, front - 0.045).color(0xF4F4F4).ellipsoid(0.08, 0.06, 0.012)
    elif shape == "nose":
        s.at(cx, cy + 0.05, front - 0.07).color(a).ellipsoid(0.11, 0.11, 0.11)
    elif shape == "visor":
        s.at(cx, ey, cz).scale(hw, 1, hd).color(a).lathe([1.05, 1.05], [-0.09, 0.09], 24, 180, 360)
        s.at(cx, ey + 0.02, front - 0.055).color(b).ellipsoid(0.55 * hw, 0.02, 0.01)
    elif shape == "blush":
        for sd in (-1, 1):
            s.at(cx + sd * 0.33, cy - 0.05, front - 0.012).color(a).ellipsoid(0.11, 0.06, 0.015)
    elif shape == "freckles":
        for d in ((-0.36, 0.02), (-0.28, -0.05), (-0.4, -0.08), (0.36, 0.02), (0.28, -0.05), (0.4, -0.08)):
            s.at(cx + d[0], cy + d[1], front - 0.012).color(a).ellipsoid(0.025, 0.025, 0.01)


# ---------------------------------------------------------------- shoes (a leg's joint space)

LEG = (-0.5, -2.0, -0.5, 0.5, 0.0, 0.5)


def shoe_shell(s, e, grow, y0, y1, toe, color):
    cx, cz = (e[0] + e[3]) / 2, (e[2] + e[5]) / 2
    hx, hz = (e[3] - e[0]) / 2 * grow, (e[5] - e[2]) / 2 * grow
    k = min(0.08, (y1 - y0) * 0.4)

    def fn(u, v):
        ang = u * math.pi * 2
        c, sn = math.cos(ang), math.sin(ang)
        px = math.copysign(abs(c) ** 0.2, c)
        pz = math.copysign(abs(sn) ** 0.2, sn)
        if v < 0.2:
            r, y = v / 0.2 * (1 - k / hx), y0
        elif v < 0.45:
            t = (v - 0.2) / 0.25 * math.pi / 2
            r, y = 1 - k / hx + k / hx * math.sin(t), y0 + k - k * math.cos(t)
        else:
            r, y = 1, y0 + k + (y1 - y0 - k) * (v - 0.45) / 0.55
        f = (y - y0) / (y1 - y0)
        fr = 1 + (toe - 1) * (1 - f * f) if pz < 0 else 1
        return (cx + px * hx * r, y, cz + pz * hz * r * fr)
    s.at(0, 0, 0).color(color).surface(fn, 40, 14, one_sided=True)  # (the leg fills it)


def shoes(s, shape, e=LEG, a="a", b="b"):
    lcx, lcz, bottom = (e[0] + e[3]) / 2, (e[2] + e[5]) / 2, e[1]
    hw, hd = (e[3] - e[0]) / 2, (e[5] - e[2]) / 2
    if shape == "boots":
        shoe_shell(s, e, 1.12, bottom - 0.02, bottom + 0.1, 1.32, b)
        shoe_shell(s, e, 1.08, bottom + 0.06, bottom + 0.7, 1.28, a)
        shoe_shell(s, e, 1.13, bottom + 0.58, bottom + 0.7, 1.0, b)
    elif shape == "loafers":
        shoe_shell(s, e, 1.12, bottom - 0.02, bottom + 0.07, 1.4, 0x1C1C22)
        shoe_shell(s, e, 1.08, bottom + 0.04, bottom + 0.24, 1.36, a)
        s.at(lcx, bottom + 0.2, lcz - hd * 1.08 - 0.12).color(b).ellipsoid(hw * 0.45, 0.03, 0.05)
    elif shape == "clown":
        shoe_shell(s, e, 1.2, bottom - 0.02, bottom + 0.12, 2.3, b)
        shoe_shell(s, e, 1.14, bottom + 0.08, bottom + 0.42, 2.15, a)
        s.at(lcx, bottom + 0.36, lcz - hd * 1.14 - 0.3).color(b).ellipsoid(0.14, 0.12, 0.12)
    else:  # sneakers
        y0, y1, toe = bottom + 0.05, bottom + 0.44, 1.36
        shoe_shell(s, e, 1.12, bottom - 0.02, bottom + 0.09, 1.4, b)
        shoe_shell(s, e, 1.08, y0, y1, toe, a)
        for i in range(3):
            y = y1 - 0.06 - i * 0.07
            f = (y - y0) / (y1 - y0)
            s.at(lcx, y, lcz - hd * 1.08 * (1 + (toe - 1) * (1 - f * f)) + 0.005).color(b).ellipsoid(hw * 0.5, 0.022, 0.02)
