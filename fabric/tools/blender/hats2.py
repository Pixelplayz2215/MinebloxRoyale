"""
Hats with the details real catalog hats have (creator: "smoothing and a better model"): each hat's shape and fit as
before (cosmetics.hat, so hair cut for its edge still fits under it), smoother (shapes.DETAIL), plus what it lacked:
panel seams, stitching, eyelets and a snap strap on caps; a ribbed cuff, knit rows and a fluffy pompom on beanies;
buckles, bands and edge binding on brimmed hats; cushions, pearls and gems on crowns; ear cushions on headphones;
rivets, straps and lights on helmets.
"""
import math
import random

from cosmetics import hat as base_hat
from shapes import S, shade

GOLD, PEARL, DARK, METAL, GLOW = 0xF2C230, 0xF4F1E8, 0x1C1C22, 0x9AA2AD, 0xFFF4C0
TAU = math.pi * 2


def ring_pts(cx, y, cz, rx, rz, n=40, a0=0.0, a1=TAU, dy=None):
    return [(cx + math.cos(a0 + (a1 - a0) * i / n) * rx, y + (dy(i / n) if dy else 0), cz + math.sin(a0 + (a1 - a0) * i / n) * rz) for i in range(n + 1)]


def shell_line(h, grow, y0, y1, rc, th, lift=0.01, n=14):
    """Points up a cosmetics.shell (a cap's crown) at angle th, from y0 to its top, just outside it."""
    rr = rc / h.hw
    out = []
    ytop_side = y1 - rc
    for i in range(n + 1):
        f = i / n
        if f < 0.6:
            r, y = grow, y0 + (ytop_side - y0) * f / 0.6
        else:
            q = (f - 0.6) / 0.4 * math.pi / 2 * 0.92
            r, y = grow - rr + rr * math.cos(q), ytop_side + rc * math.sin(q)
        out.append((h.cx + math.cos(th) * (h.hw * r + lift), y + lift * (f > 0.6), h.cz + math.sin(th) * (h.hd * r + lift)))
    return out


def shell_radius(h, grow, y1, rc, y):
    """How far out (in head widths) a cosmetics.shell reaches at height y (its top rounds in over the last rc)."""
    rr = rc / h.hw
    if y <= y1 - rc:
        return grow
    q = math.asin(min(1.0, (y - (y1 - rc)) / rc))
    return grow - rr + rr * math.cos(q)


def shell_point(h, grow, y1, rc, th, y, lift=0.004):
    """A point on a shell at angle th and height y, just outside it, and the outward direction there."""
    r = shell_radius(h, grow, y1, rc, y)
    r2 = shell_radius(h, grow, y1, rc, y + 0.01)
    up = max(0.0, (r - r2) * h.hw / 0.01)
    n = (math.cos(th), up, math.sin(th))
    k = math.sqrt(sum(v * v for v in n))
    n = tuple(v / k for v in n)
    return (h.cx + math.cos(th) * r * h.hw + n[0] * lift, y + n[1] * lift, h.cz + math.sin(th) * r * h.hd + n[2] * lift), n


def rivets(s, pts, color, r=0.022):
    for p in pts:
        s.at(*p).color(color).scale(r, r, r).sphere(-90, 90, 8)


def fluff(s, x, y, z, r, color, seed, n=26):
    """A fluffy pompom: a ball and soft tufts round it."""
    rnd = random.Random(seed)
    s.at(x, y, z).color(color).ellipsoid(r * 0.85, r * 0.85, r * 0.85)
    for _ in range(n):
        u, v = rnd.random(), rnd.random()
        th, el = TAU * u, math.asin(2 * v - 1)
        d = (math.cos(el) * math.cos(th), math.sin(el), math.cos(el) * math.sin(th))
        k = rnd.uniform(0.3, 0.45) * r
        s.at(x + d[0] * r * 0.62, y + d[1] * r * 0.62, z + d[2] * r * 0.62).color(shade(color, rnd.uniform(0.9, 1.05)) if color in ("a", "b") else color).scale(k, k, k).sphere(-90, 90, 8)


def buckle(s, x, y, z, w, hgt, color):
    pts = [(x - w, y - hgt, z), (x + w, y - hgt, z), (x + w, y + hgt, z), (x - w, y + hgt, z), (x - w, y - hgt, z)]
    s.at(0, 0, 0).color(color).tube(pts, [0.016] * 5, caps=False, sides=6)
    s.at(0, 0, 0).color(color).tube([(x, y - hgt, z - 0.004), (x, y + hgt * 0.4, z - 0.004)], [0.01, 0.01], caps=False, sides=6)


def details(s, shape, h, a="a", b="b"):
    hw, hd, cx, cz, top, cy, brow = h.hw, h.hd, h.cx, h.cz, h.top, h.cy, h.brow
    oval = hd / hw
    if shape == "tophat":
        y = top + 0.17
        buckle(s, cx, y, cz - 0.865 * hw * oval - 0.012, 0.1, 0.085, GOLD)
        s.at(0, 0, 0).color(shade(a, 0.7)).tube(ring_pts(cx, top - 0.04, cz, 1.56 * hw, 1.56 * hw * oval, 48), [0.02] * 49, caps=False)
        s.at(0, 0, 0).color(shade(b, 0.8)).tube(ring_pts(cx, top + 0.06, cz, 0.866 * hw, 0.866 * hw * oval, 40), [0.012] * 41, caps=False, sides=6)
        s.at(0, 0, 0).color(shade(b, 0.8)).tube(ring_pts(cx, top + 0.28, cz, 0.866 * hw, 0.866 * hw * oval, 40), [0.012] * 41, caps=False, sides=6)
    elif shape in ("cap", "backcap"):
        y0, y1 = brow, top + 0.08
        for k in range(6):  # panel seams up to the button
            th = TAU * k / 6 + TAU / 12
            s.at(0, 0, 0).color(shade(a, 0.72)).tube(shell_line(h, 1.07, y0 + 0.02, y1, 0.3, th, 0.006), [0.011] * 15, caps=False, sides=6)
        for k in range(4):  # vent eyelets
            th = TAU * k / 4 + TAU / 8
            p = shell_line(h, 1.07, y0, y1, 0.3, th, 0.004, n=10)[8]
            s.at(*p).align(math.cos(th), 0.7, math.sin(th)).color(shade(a, 0.6)).torus(0.025, 0.008, 0, 360)
        d = 1 if shape == "cap" else -1
        bz = cz - d * hd * 1.05
        rx, rz, ry = 0.85 * hw, 0.5, 0.035  # the bill (cosmetics.hat: an ellipsoid at brow + 0.02)
        for k in range(3):  # stitching rows lying on the bill's top, following its curve
            f = 0.45 + 0.17 * k
            pts = []
            for i in range(17):
                t = math.pi * (0.15 + 0.7 * i / 16)
                x, z = math.cos(t) * rx * f, math.sin(t) * rz * f
                y = brow + 0.02 + ry * math.sqrt(max(0.0, 1 - (x / rx) ** 2 - (z / rz) ** 2)) + 0.004
                pts.append((cx + x, y, bz - d * z))
            s.at(0, 0, 0).color(shade(a, 0.78)).tube(pts, [0.007] * 17, caps=False, sides=4)
        s.at(0, 0, 0).color(shade(a, 0.75)).tube(ring_pts(cx, brow + 0.035, cz, 1.072 * hw, 1.072 * hd, 40), [0.014] * 41, caps=False, sides=6)
        if shape == "backcap":  # the snap strap and its opening, now at the front
            fz = cz - hd * 1.07
            s.at(cx, brow + 0.12, fz + 0.01).rotX(90).color(DARK).lathe([0, 0.16, 0.16, 0], [0, 0, 0.01, 0.01], 20, 180, 360)
            s.at(0, 0, 0).color(b).tube([(cx - 0.22, brow + 0.06, fz - 0.006), (cx + 0.22, brow + 0.06, fz - 0.006)], [0.03, 0.03], sides=8)
            for k in range(5):
                s.at(cx - 0.16 + 0.08 * k, brow + 0.06, fz - 0.035).rotX(90).color(shade(b, 0.6)).torus(0.012, 0.005, 0, 360)
    elif shape == "crown":
        base = top - 0.08
        s.at(cx, base + 0.18, cz).color(b).ellipsoid(0.9 * hw, 0.3, 0.9 * hd)  # the velvet cushion inside
        s.at(0, 0, 0).color(PEARL).tube(ring_pts(cx, base + 0.005, cz, 1.07 * hw, 1.07 * hd, 48), [0.035] * 49, caps=False)
        for k in range(18):  # pearls round the top of the band
            t = TAU * k / 18
            s.at(cx + math.cos(t) * 1.06 * hw, base + 0.3, cz + math.sin(t) * 1.06 * hd).color(PEARL).blob(0.026, 0.026, 0.026)
        for k in range(6):  # gems all round the band
            t = TAU * k / 6 + math.pi / 2 + TAU / 12
            s.at(cx + math.cos(t) * 1.075 * hw, base + 0.15, cz + math.sin(t) * 1.075 * hd).align(math.cos(t), 0, math.sin(t)).color(0x2E7BE8 if k % 2 else b).lens(0.05, 0.065, 0.025)
        s.at(cx, base + 0.62, cz).color(GOLD).ellipsoid(0.06, 0.06, 0.06)
        s.at(0, 0, 0).color(GOLD).tube([(cx, base + 0.66, cz), (cx, base + 0.86, cz)], [0.02, 0.02])
        s.at(0, 0, 0).color(GOLD).tube([(cx - 0.07, base + 0.79, cz), (cx + 0.07, base + 0.79, cz)], [0.02, 0.02])
    elif shape == "tiara":
        y, r, dip = top - 0.18, 1.07, 0.22
        for k in range(9):
            ang = math.radians(198 + 144 * k / 8)
            c2 = math.cos(ang) ** 2
            s.at(cx + math.cos(ang) * (r * hw + 0.03), y - dip * c2 + 0.005, cz + math.sin(ang) * (r * hd + 0.03)).color(PEARL if k % 2 else b).blob(0.022, 0.022, 0.022)
    elif shape == "cone":
        y0 = top - 0.1
        s.at(cx, y0, cz).scale(hw, 1, hw).color(b).lathe([0.88, 0.7], [0.16, 0.38], 32)  # a second reflective band
        s.at(cx, y0 + 1.45, cz).color(shade(a, 0.85)).ellipsoid(0.07, 0.04, 0.07)
        for k in range(4):  # the square foot's corners
            t = TAU * k / 4 + TAU / 8
            s.at(cx + math.cos(t) * 1.1 * hw, y0 + 0.04, cz + math.sin(t) * 1.1 * hw).color(shade(a, 0.9)).ellipsoid(0.16, 0.045, 0.16)
    elif shape == "headphones":
        for side in (-1, 1):
            x = cx + side * (hw + 0.1)
            s.at(x - side * 0.1, cy, cz).rotZ(90).color(DARK).torus(0.24, 0.07, 0, 360)  # the ear cushion
            s.at(x + side * 0.115, cy, cz).rotZ(90).color(b).lathe([0, 0.18, 0.18, 0], [0, 0, 0.012, 0.012], 24)
            s.at(x, cy + 0.36, cz).color(METAL)
            s.at(0, 0, 0).color(METAL).tube([(x, cy + 0.3, cz), (x, cy + 0.5, cz)], [0.03, 0.03])
        pts = [(cx + math.cos(t) * (hw + 0.13), cy + math.sin(t) * (hw + 0.13) + 0.02, cz) for t in [math.pi * (0.3 + 0.4 * i / 12) for i in range(13)]]
        s.at(0, 0, 0).color(DARK).tube(pts, [0.085] * 13)  # the padded top of the band
    elif shape == "viking":
        rivets(s, [(cx + math.cos(t) * 1.13 * hw, brow + 0.005, cz + math.sin(t) * 1.13 * hd) for t in [TAU * k / 14 for k in range(14)]], shade(a, 1.15))
        rivets(s, [(cx, cy + math.sin(t) * (1.1 * hw + 0.07), cz + math.cos(t) * (1.1 * hw + 0.07)) for t in [math.pi * (0.25 + 0.5 * k / 6) for k in range(7)]],
               shade(a, 1.15), 0.02)
        fz = cz - hd * 1.1
        s.at(0, 0, 0).color(a).tube([(cx, brow + 0.02, fz), (cx, h.eye - 0.12, fz - 0.03), (cx, h.eye - 0.32, fz - 0.05)], [0.05, 0.045, 0.035])  # nose guard
        for side in (-1, 1):
            x = cx + side * hw
            s.at(x + side * 0.12, brow + 0.27, cz).rotZ(side * 70).color(shade(a, 1.1)).torus(0.11, 0.02, 0, 360)
    elif shape in ("fedora", "cowboy"):
        rb = 1.66 if shape == "fedora" else 1.86
        yb = brow + 0.04 + (0.1 if shape == "fedora" else 0.2)
        s.at(0, 0, 0).color(shade(a, 0.72)).tube(ring_pts(cx, yb - 0.005, cz, rb * hw, rb * hw * oval, 56), [0.018] * 57, caps=False)
        ytop = brow + 0.04 + (0.66 if shape == "fedora" else 0.72)
        s.at(0, 0, 0).color(shade(a, 0.7)).tube([(cx, ytop - 0.05, cz - 0.42 * hw), (cx, ytop - 0.07, cz), (cx, ytop - 0.05, cz + 0.45 * hw)], [0.03] * 3)  # the crease
        for side in (-1, 1):  # pinches at the front of the crown
            s.at(cx + side * 0.2, ytop - 0.16, cz - 0.62 * hw * oval).rotZ(side * 20).color(shade(a, 0.82)).ellipsoid(0.07, 0.13, 0.03)
        bandy = brow + 0.04 + (0.15 if shape == "fedora" else 0.18)
        if shape == "fedora":  # a bow on the band
            bx, bz = cx - 1.04 * hw, cz
            for k in (-1, 1):
                s.at(bx - 0.01, bandy, bz + k * 0.07).rotX(k * 25).color(b).ellipsoid(0.02, 0.05, 0.08)
            s.at(bx - 0.02, bandy, bz).color(shade(b, 0.8)).blob(0.03, 0.04, 0.03)
        else:  # studs and a buckle on the band, the brim's sides turned up
            for k in range(10):
                t = TAU * k / 10 + 0.3
                s.at(cx + math.cos(t) * 1.055 * hw, bandy, cz + math.sin(t) * 1.055 * hd).color(0xC9CED6).blob(0.022, 0.022, 0.022)
            buckle(s, cx, bandy, cz - 1.05 * hd - 0.01, 0.06, 0.06, 0xC9CED6)
            for side in (-1, 1):  # the brim's edge rolled up at each side
                pts = []
                for i in range(15):
                    t = -0.7 + 1.4 * i / 14
                    pts.append((cx + side * rb * hw * math.cos(t) * 0.99, brow + 0.24 + 0.12 * math.cos(t / 0.7 * math.pi / 2) ** 2, cz + rb * hw * oval * math.sin(t)))
                s.at(0, 0, 0).color(a).tube(pts, [0.035] * 15)
    elif shape == "beanie":
        yc = brow
        for k in range(36):  # the ribbed cuff
            t = TAU * k / 36
            s.at(0, 0, 0).color(shade(a, 0.9)).tube([(cx + math.cos(t) * 1.15 * hw, yc + 0.015, cz + math.sin(t) * 1.15 * hd),
                                                      (cx + math.cos(t) * 1.155 * hw, yc + 0.205, cz + math.sin(t) * 1.155 * hd)], [0.022, 0.022], caps=False, sides=6)
        for k in range(4):  # knit rows round the crown, lying on it
            y = brow + 0.3 + (top + 0.08 - brow - 0.3) * k / 3
            rr = shell_radius(h, 1.08, top + 0.2, 0.5, y)
            s.at(0, 0, 0).color(shade(a, 0.82)).tube(ring_pts(cx, y, cz, rr * hw + 0.004, rr * hd + 0.004, 40), [0.012] * 41, caps=False, sides=6)
        fluff(s, cx, top + 0.3, cz, 0.18, b, "pompom", n=34)
    elif shape == "halo":
        s.at(cx, top + 0.38, cz).rotX(8).color(GLOW, glow=True).torus(0.55, 0.03, 0, 360)  # (a glowing inner ring)
    elif shape in ("bunny", "cat"):
        for side in (-1, 1):  # fluff where each ear meets the band
            fluff(s, cx + side * (0.3 if shape == "bunny" else 0.62 * hw), top + (0.0 if shape == "bunny" else -0.02), cz + 0.02, 0.07, a, f"{shape}{side}", n=10)
        # (the headband is the hat's own: cosmetics.hat)
    elif shape == "witch":
        buckle(s, cx, brow + 0.17, cz - 1.04 * hw - 0.012, 0.09, 0.075, GOLD)
        s.at(0, 0, 0).color(shade(a, 0.75)).tube(ring_pts(cx, brow + 0.07, cz, 1.73 * hw, 1.73 * hw * oval, 56), [0.016] * 57, caps=False)
        s.at(cx, top + 1.26, cz + 0.56).color(b).ellipsoid(0.05, 0.05, 0.05)
    elif shape == "party":
        rnd = random.Random("party")
        x0, y0, tilt = cx + 0.08, top - 0.08, math.radians(-10)
        rb = 0.55 * hw

        def on_cone(f, t, out=0.004):  # a point on the cone's side (height share f, angle t), and its outward normal
            lx, ly, lz = math.cos(t) * (rb * (1 - f) + out), f, math.sin(t) * (rb * (1 - f) + out)
            nx, ny, nz = math.cos(t), rb, math.sin(t)
            rot = lambda x, y: (x * math.cos(tilt) - y * math.sin(tilt), x * math.sin(tilt) + y * math.cos(tilt))
            px, py = rot(lx, ly)
            qx, qy = rot(nx, ny)
            return (x0 + px, y0 + py, cz + lz), (qx, qy, nz)
        for k in range(16):  # polka dots on the cone
            p, n = on_cone(rnd.uniform(0.12, 0.72), TAU * rnd.random())
            s.at(*p).align(*n).color(b).lens(0.035, 0.035, 0.008)
        tip, _ = on_cone(1.02, 0, 0)
        fluff(s, *tip, 0.11, b, "party", n=16)
    elif shape == "chef":
        for k in range(24):  # pleats round the band
            t = TAU * k / 24
            s.at(0, 0, 0).color(shade(a, 0.9)).tube([(cx + math.cos(t) * 1.06 * hw, brow + 0.2, cz + math.sin(t) * 1.06 * hd),
                                                      (cx + math.cos(t) * 1.065 * hw, top + 0.15, cz + math.sin(t) * 1.065 * hd)], [0.02, 0.02], caps=False, sides=6)
    elif shape == "tricorne":
        r = 1.85 * hw  # gold trim along the brim's three edges (its corners: front, back left, back right)
        y = brow + 0.38
        corners = [(cx, y, cz - r), (cx + 0.866 * r, y, cz + 0.5 * r), (cx - 0.866 * r, y, cz + 0.5 * r), (cx, y, cz - r)]
        s.at(0, 0, 0).color(GOLD).tube(corners, [0.02] * 4, sides=6)
        for k in range(8):  # the cockade
            s.at(cx, brow + 0.38, cz - hd * 1.07).rotZ(k * 45).move(0, 0.06, 0).rotX(90).color(shade(b, 0.9)).lens(0.035, 0.06, 0.012)
    elif shape == "police":
        fz = cz - hd * 1.0
        s.at(0, 0, 0).color(GOLD).tube([(cx - 0.85 * hw, brow + 0.07, fz + 0.12), (cx, brow + 0.08, fz - 0.06), (cx + 0.85 * hw, brow + 0.07, fz + 0.12)], [0.016] * 3, sides=6)
        for side in (-1, 1):
            s.at(cx + side * 0.85 * hw, brow + 0.07, fz + 0.12).color(GOLD).blob(0.03, 0.03, 0.03)
        for k in range(5):  # the badge's star
            s.at(cx, brow + 0.17, fz - 0.1).rotZ(k * 72).move(0, 0.045, 0).rotX(90).color(GOLD).lens(0.02, 0.045, 0.01)
    elif shape == "space_helmet":
        rivets(s, [(cx + math.cos(t) * 1.25 * hw, h.bottom - 0.05 + 0.08, cz + math.sin(t) * 1.25 * hd) for t in [TAU * k / 16 for k in range(16)]], 0xC9CED6, 0.03)
        for side in (-1, 1):
            s.at(cx + side * 1.38 * hw, cy + 0.1, cz).rotZ(90).color(0x5AFF8A if side > 0 else 0xFF5A5A, glow=True).lathe([0, 0.07, 0.07, 0], [0, 0, 0.03, 0.03], 16)
        s.at(0, 0, 0).color(0xC9CED6).tube([(cx + 0.3, cy + 1.3 * h.hh + 0.0, cz + 0.2), (cx + 0.36, cy + 1.3 * h.hh + 0.3, cz + 0.25)], [0.02, 0.015])
        s.at(cx + 0.36, cy + 1.3 * h.hh + 0.33, cz + 0.25).color(0xFF5A5A, glow=True).blob(0.04, 0.04, 0.04)
    elif shape in ("knight", "fire_helmet", "army", "pith", "robot_helmet"):
        if shape == "knight":
            rivets(s, [(cx + math.cos(t) * 1.11 * hw, h.eye + 0.17, cz + math.sin(t) * 1.11 * hd) for t in [TAU * k / 18 for k in range(18)]], shade(a, 1.2))
            for k in range(6):
                s.at(cx + 0.11 * (k - 2.5) * hw, h.cy - 0.38, cz - hd * 1.1).color(0x111114).ellipsoid(0.025, 0.025, 0.02)
        elif shape == "fire_helmet":
            rivets(s, [(cx + math.cos(t) * 1.1 * hw, brow + 0.13, cz + math.sin(t) * 1.1 * hd) for t in [TAU * k / 14 for k in range(14)]], shade(a, 1.2))
            s.at(cx, brow + 0.25, cz - hd * 1.13).rotX(90).color(GOLD).lens(0.08, 0.08, 0.015)
        elif shape == "army":
            rnd = random.Random("army")
            spots = []
            for _ in range(400):  # camo patches painted on, apart from each other (overlapping decals flicker)
                t0, y0 = TAU * rnd.random(), rnd.uniform(brow + 0.16, top + 0.04)
                if all(min(abs(t0 - t1), TAU - abs(t0 - t1)) * 0.75 + abs(y0 - y1) * 2.2 > 0.42 for t1, y1 in spots):
                    spots.append((t0, y0))
                if len(spots) == 13:
                    break
            for k, (t0, y0) in enumerate(spots):  # each a blob-shaped decal following the helmet's curve
                dt, dy = 0.15 / (1.14 * hw), 0.085
                wob = [rnd.uniform(0, TAU) for _ in range(2)]

                def patch(u, v, t0=t0, y0=y0, dt=dt, dy=dy, wob=wob, lift=0.006 + 0.0012 * k):
                    ph = TAU * u
                    r = v * (1 + 0.22 * math.sin(3 * ph + wob[0]) + 0.12 * math.sin(5 * ph + wob[1]))
                    return shell_point(h, 1.14, top + 0.16, 0.5, t0 + r * math.cos(ph) * dt, y0 + r * math.sin(ph) * dy, lift)[0]
                s.at(0, 0, 0).color(shade(a, (0.7, 0.55)[k % 2])).surface(patch, 16, 3)
        elif shape == "pith":
            s.at(cx, top + 0.28, cz).color(shade(a, 0.85)).lathe([0, 0.09, 0.07, 0], [0, 0, 0.05, 0.05], 16)
            s.at(0, 0, 0).color(shade(a, 0.75)).tube(ring_pts(cx, brow - 0.06, cz, 1.55 * hw, 1.55 * hw * oval, 48), [0.018] * 49, caps=False)
        else:
            y0 = h.bottom - 0.06
            for k in range(4):
                t = TAU * k / 4 + TAU / 8
                s.at(cx + math.cos(t) * 1.17 * hw, h.cy + 0.6, cz + math.sin(t) * 1.17 * hd).color(0xC9CED6).blob(0.035, 0.035, 0.035)
            for side in (-1, 1):
                s.at(cx + side * 1.18 * hw, h.eye - 0.1, cz).rotZ(90).color(METAL).lathe([0, 0.14, 0.14, 0], [0, 0, 0.06, 0.06], 20)
                s.at(cx + side * (1.18 * hw + 0.065), h.eye - 0.1, cz).rotZ(90).color(b, glow=True).lathe([0, 0.06, 0.06, 0], [0, 0, 0.01, 0.01], 16)


def hat(shape, h):
    s = S()
    base_hat(s, shape, h)
    details(S(), shape, h)
