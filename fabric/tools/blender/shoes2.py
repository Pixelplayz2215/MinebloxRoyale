"""
Shoes with the details real ones have (creator: "smoothing and a better model"): the same fit round the square Roblox
leg (cosmetics.shoe_shell, smoother with shapes.DETAIL), plus sneakers' midsole stripe, tread, toe bumper, crossed
laces with eyelets, tongue, padded collar, heel tab and side stripes; boots' lug sole and heel, laces up hooks, toe
cap seam and pull tab; loafers' moccasin seam, strap and tassels and low heel; clown shoes' giant laces and pompom.
Leg joint space: x, z +-0.5, y -2 .. 0, the toe towards -z.
"""
import math

from cosmetics import LEG, shoe_shell
from hats2 import fluff
import shapes
from shapes import S, shade

DARK = 0x1C1C22


def outline(y, y0, y1, toe, grow, out=0.01, n=40, e=LEG):
    """The shoe_shell's outline at height y (from y0 to y1, toe stretch), pushed out a little: points all round."""
    cx, cz = (e[0] + e[3]) / 2, (e[2] + e[5]) / 2
    hx, hz = (e[3] - e[0]) / 2 * grow, (e[5] - e[2]) / 2 * grow
    f = max(0.0, min(1.0, (y - y0) / (y1 - y0)))
    pts = []
    for i in range(n + 1):
        a = math.pi * 2 * i / n
        c, sn = math.cos(a), math.sin(a)
        px, pz = math.copysign(abs(c) ** 0.2, c), math.copysign(abs(sn) ** 0.2, sn)
        fr = 1 + (toe - 1) * (1 - f * f) if pz < 0 else 1
        pts.append((cx + px * (hx + out), y, cz + pz * (hz * fr + out)))
    return pts


def front_z(y, y0, y1, toe, grow, e=LEG):
    """Where the shoe's front is at height y (the toe sloping up to the ankle)."""
    hz = (e[5] - e[2]) / 2 * grow
    f = max(0.0, min(1.0, (y - y0) / (y1 - y0)))
    return (e[2] + e[5]) / 2 - hz * (1 + (toe - 1) * (1 - f * f))


def laces(s, y0, y1, toe, grow, ys, half, color, hooks=None):
    """Crossed laces over the front at heights ys, eyelets (or hooks) at their ends."""
    for i, y in enumerate(ys):
        z = front_z(y, y0, y1, toe, grow) - 0.012
        z2 = front_z(y + 0.05, y0, y1, toe, grow) - 0.012
        s.at(0, 0, 0).color(color).tube([(-half, y, z), (0, y + 0.025, (z + z2) / 2 - 0.01), (half, y + 0.05, z2)], [0.016] * 3, caps=False, sides=6)
        s.at(0, 0, 0).color(color).tube([(half, y, z), (0, y + 0.025, (z + z2) / 2 - 0.012), (-half, y + 0.05, z2)], [0.016] * 3, caps=False, sides=6)
        for x in (-half, half):
            for yy, zz in ((y, z), (y + 0.05, z2)):
                if hooks:
                    s.at(x * 1.08, yy, zz).rotX(90).color(hooks).torus(0.018, 0.007, 0, 360)
                else:
                    s.at(x * 1.05, yy, zz + 0.002).rotX(90).color(0xC9CED6).torus(0.014, 0.006, 0, 360)


def sneakers(s, a, b):
    bottom = LEG[1]
    y0, y1, toe = bottom + 0.05, bottom + 0.44, 1.36
    shoe_shell(s, LEG, 1.12, bottom - 0.02, bottom + 0.09, 1.4, b)
    shoe_shell(s, LEG, 1.08, y0, y1, toe, a)
    s.at(0, 0, 0).color(shade(b, 1.12)).tube(outline(bottom + 0.04, bottom - 0.02, bottom + 0.09, 1.4, 1.12, 0.012), [0.022] * 41, caps=False, sides=6)
    for p in outline(bottom + 0.005, bottom - 0.02, bottom + 0.09, 1.4, 1.12, 0.008, n=36)[::3]:  # tread
        s.at(*p).color(shade(b, 0.6)).scale(0.012, 0.025, 0.012).sphere(-90, 90, 6)
    zt = front_z(y0 + 0.06, y0, y1, toe, 1.08)
    s.at(0, y0 + 0.07, zt + 0.06).color(shade(b, 1.0)).ellipsoid(0.36, 0.07, 0.1)  # toe bumper
    laces(s, y0, y1, toe, 1.08, [y0 + 0.13 + 0.07 * i for i in range(3)], 0.17, shade(b, 1.0) if b != "a" else 0xF4F4F4)
    zf = front_z(y1, y0, y1, toe, 1.08)
    s.at(0, y1 + 0.06, zf + 0.03).rotX(-15).color(shade(a, 0.92)).ellipsoid(0.2, 0.12, 0.035)  # tongue
    s.at(0, 0, 0).color(shade(a, 0.85)).tube(outline(y1, y0, y1, toe, 1.08, 0.0), [0.03] * 41, caps=False, sides=8)  # padded collar
    s.at(0, y1 + 0.03, 0.56).color(b).ellipsoid(0.08, 0.08, 0.025)  # heel tab
    for side in (-1, 1):  # side stripes
        x = side * 0.545
        s.at(0, 0, 0).color(b).tube([(x, y0 + 0.05, 0.38), (x, y0 + 0.16, 0.05), (x, y0 + 0.28, -0.32)], [0.025, 0.03, 0.012], sides=8)


def boots(s, a, b):
    bottom = LEG[1]
    y0, y1, toe = bottom + 0.06, bottom + 0.7, 1.28
    shoe_shell(s, LEG, 1.13, bottom - 0.04, bottom + 0.1, 1.32, shade(b, 0.8))  # lug sole
    shoe_shell(s, LEG, 1.08, y0, y1, toe, a)
    shoe_shell(s, LEG, 1.13, bottom + 0.58, y1, 1.0, b)  # the cuff
    s.at(0, bottom + 0.035, 0.34).color(shade(b, 0.7)).scale(0.5, 1, 0.19).rotY(45).lathe([0, 1.41, 1.41, 0], [-0.075, -0.075, 0.065, 0.065], 4)  # heel block (in the sole: the ground is at its bottom)
    for p in outline(bottom - 0.02, bottom - 0.04, bottom + 0.1, 1.32, 1.13, 0.01, n=36)[::3]:  # lugs
        s.at(*p).color(shade(b, 0.55)).scale(0.03, 0.03, 0.03).sphere(-90, 90, 6)
    s.at(0, 0, 0).color(shade(a, 0.75)).tube(outline(y0 + 0.16, y0, y1, toe, 1.08, 0.006)[25:36], [0.01] * 11, caps=False, sides=4)  # toe cap seam
    laces(s, y0, y1, toe, 1.08, [y0 + 0.16 + 0.085 * i for i in range(4)], 0.16, shade(b, 0.9), hooks=0xC9CED6)
    s.at(0, y1 + 0.05, 0.6).color(shade(b, 0.9)).rotX(15).scale(0.1, 0.1, 0.02).sphere(-90, 90, 12)  # pull tab


def loafers(s, a, b):
    bottom = LEG[1]
    y0, y1, toe = bottom + 0.04, bottom + 0.24, 1.36
    shoe_shell(s, LEG, 1.12, bottom - 0.02, bottom + 0.07, 1.4, DARK)
    shoe_shell(s, LEG, 1.08, y0, y1, toe, a)
    s.at(0, bottom + 0.02, 0.37).color(DARK).scale(0.48, 1, 0.15).rotY(45).lathe([0, 1.41, 1.41, 0], [-0.04, -0.04, 0.05, 0.05], 4)  # low heel (in the sole)
    seam = [p for p in outline(y0 + 0.13, y0, y1, toe, 1.08, 0.006, n=40) if p[2] < -0.2]
    s.at(0, 0, 0).color(shade(a, 0.7)).tube(seam, [0.01] * len(seam), caps=False, sides=4)  # moccasin seam
    zf = front_z(y1 - 0.02, y0, y1, toe, 1.08)
    s.at(0, 0, 0).color(b).tube([(-0.4, y1 - 0.02, zf + 0.12), (0, y1 + 0.005, zf - 0.005), (0.4, y1 - 0.02, zf + 0.12)], [0.03] * 3, sides=8)  # strap
    for side in (-1, 1):  # tassels
        x = side * 0.08
        s.at(x, y1 + 0.005, zf - 0.02).color(b).blob(0.03, 0.03, 0.03)
        s.at(0, 0, 0).color(b).tube([(x, y1, zf - 0.03), (x + side * 0.02, y1 - 0.05, zf - 0.06), (x + side * 0.03, y1 - 0.1, zf - 0.07)], [0.014, 0.02, 0.026], sides=8)


def clown(s, a, b):
    bottom = LEG[1]
    shoe_shell(s, LEG, 1.2, bottom - 0.02, bottom + 0.12, 2.3, b)
    shoe_shell(s, LEG, 1.14, bottom + 0.08, bottom + 0.42, 2.15, a)
    s.at(0, bottom + 0.3, -0.95).color(a).ellipsoid(0.42, 0.24, 0.36)  # the ball toe
    laces(s, bottom + 0.08, bottom + 0.42, 2.15, 1.14, [bottom + 0.24, bottom + 0.31], 0.2, 0xF4F4F4)
    fluff(s, 0, bottom + 0.52, -0.8, 0.12, b, "clown", n=16)


def shoes(shape):
    detail, shapes.DETAIL = shapes.DETAIL, 1.0  # (seen small: no extra pieces)
    try:
        {"boots": boots, "loafers": loafers, "clown": clown}.get(shape, sneakers)(S(), "a", "b")
    finally:
        shapes.DETAIL = detail
