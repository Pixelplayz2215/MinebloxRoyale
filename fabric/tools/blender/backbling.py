"""
Back bling, made in Blender (v1.0.1): ported line for line from the code-drawn BackBling (shapes.S is the Java Smooth),
in the torso's joint space (studs, y up, the front towards -z; the default torso spans x +-1, y -1 .. 1, z +-0.5). One
model per item (capes and packs differ by item, not only by shape), in its two colours (tint channels 1 and 2).
"""
import math

from shapes import shade

GOLD, METAL, DARK, LEATHER, WHITE, ROPE = 0xF2C230, 0x9AA2AD, 0x2A2A30, 0x5A3A22, 0xF4F6FA, 0xC9A66B
TORSO = (-1.0, -1.0, -0.5, 1.0, 1.0, 0.5)


class T:
    def __init__(self, e):
        self.cx, self.cy, self.bk, self.fr, self.top, self.hw = (e[0] + e[3]) / 2, (e[1] + e[4]) / 2, e[5], e[2], e[4], (e[3] - e[0]) / 2


def lerp(a, b, f):
    return a + (b - a) * f


def fill(n, v):
    return [v] * n


def draw(s, row, e=TORSO):
    t = T(e)
    a, b = "a", "b"
    i, sh = row["id"], row["shape"]
    if sh == "wings":
        if "butterfly" in i:
            butterfly(s, t, a, b)
        elif "vespertilio" in i:
            bat_wings(s, t, a, b)
        else:
            feather_wings(s, t, i, a, b)
    elif sh == "cape":
        cape(s, t, i, a, b)
    elif sh == "backpack":
        backpack(s, t, i, a, b)
    elif sh == "sword":
        sword(s, t, a, b)
    elif sh == "katana":
        katana(s, t, a, b)
    elif sh == "round_shield":
        round_shield(s, t, a, b)
    elif sh == "kite_shield":
        kite_shield(s, t, a, b)
    elif sh == "jetpack":
        jetpack(s, t, a, b)
    elif sh == "tank":
        battery(s, t, a, b) if "battery" in i else tank(s, t, i, a, b)
    elif sh == "guitar":
        guitar(s, t, a, b)
    elif sh == "skateboard":
        skateboard(s, t, a, b)
    elif sh == "moneybag":
        money_bag(s, t, a, b)
    elif sh == "violin":
        violin_case(s, t, a, b)
    elif sh == "lasso":
        lasso(s, t, a, b)
    elif sh == "staff":
        staff(s, t, a, b)
    elif sh == "pitchfork":
        pitchfork(s, t, a, b)
    elif sh == "balloons":
        balloons(s, t, a, b)
    elif sh == "pan":
        pan(s, t, a, b)


# ---------------------------------------------------------------- wings

def along(pts, f):
    g = max(0.0, min(0.9999, f)) * (len(pts) - 1)
    i = int(g)
    k = g - i
    return [lerp(pts[i][j], pts[i + 1][j], k) for j in range(3)]


def feather_wings(s, t, i, a, b):
    infernus = "infernus" in i
    arm = b if "empyreus" in i else shade(a, 0.9)
    tip = b if ("dark" in i or infernus) else None
    covert = shade(a, 1.6) if infernus else b
    for side in (-1, 1):
        bone = [(t.cx + side * 0.22, t.cy + 0.45, t.bk + 0.1), (t.cx + side * 0.85, t.cy + 1.05, t.bk + 0.3), (t.cx + side * 1.65, t.cy + 1.3, t.bk + 0.45),
                (t.cx + side * 2.25, t.cy + 1.0, t.bk + 0.52)]
        s.at(0, 0, 0).color(arm).tube(bone, [0.08, 0.075, 0.06, 0.035])
        for k in range(12):
            f = k / 11
            p = along(bone, 0.04 + 0.96 * f)
            feather(s, p[0], p[1], p[2] + 0.012 + 0.008 * (11 - k), side * (6 + 52 * f ** 1.3), 0.7 + 0.85 * f, 0.12 + 0.035 * f, a if k % 2 == 0 else shade(a, 0.93), tip)
        for k in range(9):
            f = k / 8
            p = along(bone, 0.02 + 0.86 * f)
            feather(s, p[0], p[1] - 0.04, p[2] + 0.11 + 0.006 * (8 - k), side * (4 + 42 * f), 0.42 + 0.22 * f, 0.12, covert if k % 2 == 0 else shade(covert, 0.92), None)
        for k in range(4):
            p = along(bone, 0.05 + 0.12 * k)
            feather(s, p[0], p[1] + 0.03, p[2] + 0.17 + 0.005 * (3 - k), side * (2 + 10 * k), 0.26, 0.1, shade(covert, 1.08), None)
        if infernus:
            for k in range(4):
                p = along(bone, 0.55 + 0.13 * k)
                s.at(p[0] + side * (0.35 + 0.15 * k), p[1] - 1.35 - 0.12 * (k % 2), p[2] + 0.1).color(0xFFD23A if k % 2 == 0 else 0xFF6A1F, glow=True).blob(0.04, 0.04, 0.04)


def feather(s, x, y, z, ang, ln, w, color, tip):
    r = math.radians(ang)
    dx, dy = math.sin(r), -math.cos(r)
    s.at(x + dx * ln / 2, y + dy * ln / 2, z).rotZ(ang).rotX(90).color(color).lens(w, ln / 2, 0.025)
    if tip is not None:
        s.at(x + dx * ln * 0.84, y + dy * ln * 0.84, z + 0.004).rotZ(ang).rotX(90).color(tip).lens(w * 0.8, ln * 0.17, 0.027)


def butterfly(s, t, a, b):
    z = t.bk + 0.16
    for side in (-1, 1):
        wing_lobe(s, t.cx + side * 0.78, t.cy + 0.62, z, side * -28, 0.72, 0.5, a, b)
        wing_lobe(s, t.cx + side * 0.6, t.cy - 0.12, z, side * 32, 0.48, 0.36, shade(a, 0.94), b)
        for v in ((1.3, 1.05), (1.38, 0.5), (0.95, -0.45)):
            s.at(0, 0, 0).color(shade(b, 0.8)).tube([(t.cx + side * 0.12, t.cy + 0.3, z + 0.035), (t.cx + side * v[0] * 0.55, t.cy + 0.3 + (v[1] - 0.3) * 0.5, z + 0.04),
                                                    (t.cx + side * v[0], t.cy + v[1], z + 0.035)], [0.018, 0.014, 0.008], caps=False, sides=6)
        for d in ((1.05, 0.85, 0.13), (0.68, 0.98, 0.08), (0.75, -0.22, 0.1)):
            s.at(t.cx + side * d[0], t.cy + d[1], z + 0.042).rotX(90).color(b).lens(d[2], d[2], 0.02)
            s.at(t.cx + side * d[0], t.cy + d[1], z + 0.052).rotX(90).color(WHITE).lens(d[2] * 0.45, d[2] * 0.45, 0.02)
    s.at(t.cx, t.cy + 0.3, z + 0.06).color(0x2A2230).ellipsoid(0.08, 0.55, 0.08)
    for side in (-1, 1):
        s.at(0, 0, 0).color(0x2A2230).tube([(t.cx + side * 0.03, t.cy + 0.82, z + 0.06), (t.cx + side * 0.15, t.cy + 1.15, z + 0.1), (t.cx + side * 0.3, t.cy + 1.3, z + 0.08)], [0.02, 0.015, 0.012])
        s.at(t.cx + side * 0.3, t.cy + 1.3, z + 0.08).color(b).blob(0.035, 0.035, 0.035)


def wing_lobe(s, x, y, z, ang, rx, ry, color, border):
    s.at(x, y, z - 0.014).rotZ(ang).rotX(90).color(border).scale(rx * 1.08, 1, ry * 1.1).lathe([0, 1, 0], [-0.02, 0, 0.02], 28)
    s.at(x, y, z).rotZ(ang).rotX(90).color(color).scale(rx, 1, ry).lathe([0, 1, 0], [-0.03, 0, 0.03], 28)


def bat_wings(s, t, a, b):
    skin = shade(b, 0.55)
    for side in (-1, 1):
        root, bottom = (t.cx + side * 0.25, t.cy + 0.55, t.bk + 0.1), (t.cx + side * 0.3, t.cy - 0.55, t.bk + 0.12)
        wrist = (t.cx + side * 1.25, t.cy + 1.35, t.bk + 0.38)
        tips = [(t.cx + side * 2.45, t.cy + 1.15, t.bk + 0.55), (t.cx + side * 2.3, t.cy + 0.2, t.bk + 0.5), (t.cx + side * 1.6, t.cy - 0.5, t.bk + 0.4)]
        for k in range(3):
            membrane(s, wrist, tips[k], wrist if k < 2 else root, tips[k + 1] if k < 2 else bottom, wrist, skin)
        s.at(0, 0, 0).color(a).tube([root, (t.cx + side * 0.75, t.cy + 1.1, t.bk + 0.25), wrist], [0.07, 0.065, 0.06])
        for p in tips:
            s.at(0, 0, 0).color(a).tube([wrist, ((wrist[0] + p[0]) / 2, (wrist[1] + p[1]) / 2 + 0.06, (wrist[2] + p[2]) / 2 + 0.02), p], [0.045, 0.035, 0.012])
        s.at(wrist[0], wrist[1] + 0.05, wrist[2]).rotZ(-side * 20).color(b).cone(0.04, 0.16, 8)


def membrane(s, p0, p1, q0, q1, inn, color):
    def fn(u, v):
        x = lerp(lerp(p0[0], p1[0], u), lerp(q0[0], q1[0], u), v)
        y = lerp(lerp(p0[1], p1[1], u), lerp(q0[1], q1[1], u), v)
        z = lerp(lerp(p0[2], p1[2], u), lerp(q0[2], q1[2], u), v)
        pull = 0.28 * math.sin(math.pi * v) * u * u
        return (x + (inn[0] - x) * pull, y + (inn[1] - y) * pull, z + (inn[2] - z) * pull)
    s.at(0, 0, 0).color(color).surface(fn, 10, 8)


# ---------------------------------------------------------------- capes

def cape(s, t, i, a, b):
    ermine = "royal" in i or "queen" in i

    def cloth(u, v, dz=0.0):
        x = (u - 0.5) * 2 * (t.hw * 0.98 + 0.32 * v)
        arch = 1 - (2 * u - 1) ** 2
        fold = 0.06 * v * math.sin(u * math.pi * 7)
        return (t.cx + x, t.top - 0.06 - v * 2.85, t.bk + 0.07 + 0.3 * v + 0.1 * arch * (0.3 + v) + fold + dz)

    s.at(0, 0, 0).color(a).surface(cloth, 28, 18)
    s.at(0, 0, 0).color(shade(a, 0.68)).surface(lambda u, v: cloth(u, v, -0.02), 20, 12)
    trim = WHITE if ermine else b
    r = 0.085 if ermine else 0.04
    edge = []
    edge += line(s, cloth, False, 0, r, trim)
    edge += line(s, cloth, True, 1, r * 1.15, trim)
    edge += line(s, cloth, False, 1, r, trim)
    if not ermine and "frigidus" not in i:
        line(s, cloth, True, 0.92, 0.018, trim)
    y, cr = t.top, (0.11 if ermine else 0.065)
    collar = [(t.cx - 0.55, y - 0.15, t.fr - 0.07), (t.cx - 0.74, y + 0.04, t.fr + 0.25), (t.cx - 0.86, y + 0.04, t.bk - 0.05), (t.cx - 0.45, y + 0.01, t.bk + 0.08),
              (t.cx, y + 0.02, t.bk + 0.1), (t.cx + 0.45, y + 0.01, t.bk + 0.08), (t.cx + 0.86, y + 0.04, t.bk - 0.05), (t.cx + 0.74, y + 0.04, t.fr + 0.25),
              (t.cx + 0.55, y - 0.15, t.fr - 0.07)]
    s.at(0, 0, 0).color(trim).tube(collar, fill(len(collar), cr))
    if ermine:
        for k in range(0, len(edge), 2):
            p = edge[k]
            s.at(p[0], p[1], p[2] + r * 0.95).rotX(90).color(0x16161A).lens(0.025, 0.045, 0.012)
        for k in range(2, len(collar) - 2):
            s.at(collar[k][0], collar[k][1] + 0.02, collar[k][2] + cr * 0.95).rotX(90).color(0x16161A).lens(0.025, 0.045, 0.012)
    for side in (-1, 1):
        s.at(t.cx + side * 0.55, y - 0.17, t.fr - 0.06).rotX(-90).color(GOLD).lathe([0, 0.11, 0.12, 0.09, 0], [0, 0, 0.012, 0.03, 0.03], 16)
        s.at(t.cx + side * 0.55, y - 0.17, t.fr - 0.09).color(0xD7263D if (ermine or "hero" in i) else b).blob(0.05, 0.05, 0.02)
    s.at(0, 0, 0).color(GOLD).tube([(t.cx - 0.45, y - 0.2, t.fr - 0.08), (t.cx, y - 0.34, t.fr - 0.06), (t.cx + 0.45, y - 0.2, t.fr - 0.08)], [0.016] * 3, caps=False, sides=6)
    ex, ey, ez = cloth(0.5, 0.3)
    ez += 0.035
    if "hero" in i:
        s.at(ex, ey, ez - 0.01).rotX(90).color(GOLD).torus(0.36, 0.035, 0, 360)
        star(s, ex, ey, ez, 0.32, GOLD)
    elif "frigidus" in i:
        for k in range(3):
            s.at(ex, ey, ez).rotZ(k * 60).rotX(90).color(b).lens(0.04, 0.34, 0.02)
        for k in range(6):
            ang = math.radians(k * 60 + 90)
            s.at(ex + math.cos(ang) * 0.24, ey + math.sin(ang) * 0.24, ez + 0.01).rotZ(k * 60).rotX(90).color(b).lens(0.1, 0.035, 0.02)
        for k in range(12):
            p = cloth((k + 0.5) / 12, 1)
            s.at(p[0], p[1] - 0.03, p[2]).rotX(180).color(0xE6F7FF if k % 2 == 0 else b).cone(0.045, 0.12 + 0.08 * (k % 3), 8)
    elif "aureus" in i:
        s.at(ex, ey, ez).rotX(90).color(b).lens(0.16, 0.16, 0.03)
        for k in range(12):
            s.at(ex, ey, ez).rotZ(k * 30).move(0, 0.27, 0).rotX(90).color(b if k % 2 == 0 else GOLD).lens(0.035, 0.09, 0.02)
    elif "rex" in i or "royal" in i:
        crown(s, ex, ey, ez, 1.6, b if "rex" in i else GOLD, GOLD if "rex" in i else 0xD7263D)
    elif "queen" in i:
        s.at(ex, ey + 0.13, ez).rotX(90).color(GOLD).lens(0.11, 0.32, 0.025)
        for side in (-1, 1):
            s.at(ex + side * 0.19, ey + 0.03, ez).rotZ(side * 40).rotX(90).color(GOLD).lens(0.085, 0.24, 0.022)
        s.at(ex, ey - 0.13, ez + 0.01).rotX(90).color(GOLD).lens(0.27, 0.055, 0.025)
        s.at(ex, ey - 0.27, ez).rotX(90).color(GOLD).lens(0.07, 0.12, 0.022)
    elif "purple" in i:
        for q in ((-0.25, 0.08), (0.25, 0.08), (0, -0.18)):
            star(s, ex + q[0], ey + q[1], ez, 0.12, b)


def line(s, f, along_u, fixed, r, color):
    pts = []
    for k in range(13):
        p = f(k / 12, fixed) if along_u else f(fixed, k / 12)
        pts.append((p[0], p[1], p[2] + 0.01))
    s.at(0, 0, 0).color(color).tube(pts, fill(13, r))
    return pts


def crown(s, x, y, z, k, color, gem):
    s.at(x, y - 0.08 * k, z).color(color)
    box(s, 0.24 * k, 0.06 * k, 0.015, 0.01, 0.01)
    for n in range(5):
        px = x + (-0.2 + n * 0.1) * k
        h = (0.24 if n == 2 else 0.16 if n % 2 == 0 else 0.2) * k
        s.at(px, y - 0.03 * k, z).color(color).cone(0.045 * k, h, 6)
        s.at(px, y - 0.03 * k + h, z).color(WHITE).blob(0.025 * k, 0.025 * k, 0.025)
    for n in range(3):
        s.at(x + (-0.12 + n * 0.12) * k, y - 0.08 * k, z + 0.02).color(gem).blob(0.028 * k, 0.028 * k, 0.015)


def star(s, x, y, z, r, color):
    for k in range(5):
        s.at(x, y, z).rotZ(k * 72).move(0, r * 0.5, 0).rotX(90).color(color).lens(r * 0.2, r * 0.5, 0.02)
    s.at(x, y, z + 0.002).rotX(90).color(color).lens(r * 0.38, r * 0.38, 0.022)


# ---------------------------------------------------------------- packs

def backpack(s, t, i, a, b):
    satchel, army, explorer, med, police = "book" in i, "army" in i, "explorer" in i, "med" in i, "police" in i
    z, y0, y1, hz = t.bk + 0.28, t.cy - 0.86, t.cy + 0.62, 0.26
    face = z + hz
    s.at(t.cx, (y0 + y1) / 2, z).color(a)
    box(s, 0.62, (y1 - y0) / 2, hz, 0.09, 0.16)
    straps(s, t, y1 - 0.15, y0 + 0.12, z, shade(a, 0.75), METAL, False)
    s.at(t.cx, y1 + 0.01, z).rotX(90).color(shade(a, 0.75)).torus(0.12, 0.025, 180, 360)
    s.at(0, 0, 0).color(DARK).tube([(t.cx - 0.55, y1 - 0.24, face + 0.006), (t.cx + 0.55, y1 - 0.24, face + 0.006)], [0.014, 0.014], caps=False, sides=6)
    s.at(t.cx + 0.42, y1 - 0.3, face + 0.02).color(METAL)
    box(s, 0.025, 0.06, 0.012, 0.01, 0.01)
    for side in (-1, 1):
        s.at(t.cx + side * 0.68, y0 + 0.32, z).color(shade(a, 0.85))
        box(s, 0.07, 0.28, 0.18, 0.03, 0.03)
    if satchel:
        s.at(t.cx, y1 - 0.45, face + 0.03).color(b)
        box(s, 0.6, 0.42, 0.03, 0.02, 0.02)
        for side in (-1, 1):
            s.at(t.cx + side * 0.3, y1 - 0.5, face + 0.065).color(shade(b, 0.75))
            box(s, 0.05, 0.5, 0.01, 0.005, 0.005)
            s.at(t.cx + side * 0.3, y0 + 0.62, face + 0.08).color(GOLD)
            box(s, 0.07, 0.05, 0.012, 0.005, 0.005)
        return
    pocket = a if med else shade(a, 1.3) if police else shade(a, 0.85) if army else b
    s.at(t.cx, y0 + 0.36, face + 0.07).color(pocket)
    box(s, 0.44, 0.3, 0.08, 0.05, 0.07)
    s.at(0, 0, 0).color(DARK).tube([(t.cx - 0.36, y0 + 0.58, face + 0.152), (t.cx + 0.36, y0 + 0.58, face + 0.152)], [0.012, 0.012], caps=False, sides=6)
    s.at(t.cx + 0.3, y0 + 0.53, face + 0.16).color(METAL)
    box(s, 0.02, 0.05, 0.01, 0.008, 0.008)
    pz, py = face + 0.155, y0 + 0.33
    if police:
        star(s, t.cx, py, pz + 0.01, 0.17, GOLD)
        s.at(t.cx, t.cy + 0.05, z).color(0xD9DEE4)
        box(s, 0.625, 0.045, hz + 0.008, 0.005, 0.005)
    elif med:
        s.at(t.cx, py, pz).color(b)
        box(s, 0.06, 0.19, 0.012, 0.005, 0.005)
        s.at(t.cx, py, pz).color(b)
        box(s, 0.19, 0.06, 0.012, 0.005, 0.005)
        s.at(t.cx, y1 - 0.1, z).color(b)
        box(s, 0.625, 0.06, hz + 0.008, 0.005, 0.005)
    elif army:
        cols = [shade(a, 0.65), 0x5C4A2E, shade(a, 1.25)]
        for k in range(9):
            px, py2 = -0.45 + 0.9 * ((k * 0.618) % 1), y0 + 0.75 + 0.6 * ((k * 0.382 + 0.1) % 1)
            s.at(t.cx + px, py2, face + 0.008).rotZ(k * 40).rotX(90).color(cols[k % 3]).lens(0.12, 0.07, 0.01)
        for k in range(3):
            s.at(t.cx, py - 0.14 + k * 0.12, pz).color(shade(a, 0.6))
            box(s, 0.4, 0.018, 0.008, 0.004, 0.004)
        bedroll(s, t.cx, y1 + 0.17, z + 0.02, 0x4E6B3A)
    elif explorer:
        for side in (-1, 1):
            s.at(t.cx + side * 0.2, py + 0.05, pz).color(shade(b, 0.9))
            box(s, 0.04, 0.3, 0.008, 0.004, 0.004)
            s.at(t.cx + side * 0.2, py - 0.12, pz + 0.012).color(GOLD)
            box(s, 0.055, 0.04, 0.008, 0.004, 0.004)
        bedroll(s, t.cx, y1 + 0.17, z + 0.02, 0x8B2E2E)
        for k in range(3):
            s.at(t.cx - 0.76 - k * 0.015, y0 + 0.75, z + k * 0.02).rotZ(90).color(ROPE if k % 2 == 0 else shade(ROPE, 0.85)).torus(0.17, 0.028, 0, 360)
    else:
        s.at(t.cx + 0.7, y0 + 0.45, z).color(0x5AB4F2).lathe([0, 0.08, 0.09, 0.09, 0.06, 0.045, 0.045, 0], [0, 0, 0.03, 0.42, 0.5, 0.54, 0.6, 0.6], 14)
        s.at(t.cx + 0.7, y0 + 1.05, z).color(WHITE).lathe([0, 0.05, 0.05, 0], [0, 0, 0.06, 0.06], 12)
        s.at(0, 0, 0).color(METAL).tube([(t.cx + 0.42, y1 - 0.36, face + 0.03), (t.cx + 0.42, y1 - 0.52, face + 0.05)], [0.008, 0.008], caps=False, sides=4)
        star(s, t.cx + 0.42, y1 - 0.6, face + 0.06, 0.09, 0xFF4FD8)


def bedroll(s, x, y, z, color):
    s.at(x, y, z).rotZ(90).color(color).lathe([0, 0.17, 0.18, 0.18, 0.17, 0], [-0.62, -0.62, -0.6, 0.6, 0.62, 0.62], 18)
    s.at(x - 0.62, y, z).rotZ(90).color(shade(color, 0.7)).torus(0.11, 0.02, 0, 360)
    for side in (-1, 1):
        s.at(x + side * 0.4, y, z).rotZ(90).color(LEATHER).torus(0.185, 0.022, 0, 360)


def straps(s, t, top_y, bot_y, bag_z, color, metal, belt):
    for side in (-1, 1):
        x, ox = t.cx + side * 0.48, t.cx + side * (t.hw + 0.035)
        if belt:
            pts = [(t.cx + side * 0.34, top_y, bag_z - 0.08), (x + side * 0.02, t.top + 0.05, t.bk - 0.1), (x + side * 0.03, t.top + 0.05, (t.bk + t.fr) / 2),
                   (x + side * 0.03, t.top + 0.03, t.fr - 0.03), (x, t.cy + 0.55, t.fr - 0.06), (x, t.cy - 0.6, t.fr - 0.06)]
        else:
            pts = [(x, top_y, bag_z - 0.08), (x + side * 0.02, t.top + 0.05, t.bk - 0.1), (x + side * 0.03, t.top + 0.05, (t.bk + t.fr) / 2),
                   (x + side * 0.03, t.top + 0.03, t.fr - 0.03), (x, t.cy + 0.55, t.fr - 0.06), (x, t.cy - 0.45, t.fr - 0.06), (ox, t.cy - 0.55, t.fr - 0.04),
                   (ox, t.cy - 0.6, t.bk + 0.04), (t.cx + side * 0.6, bot_y, t.bk + 0.06)]
        s.at(0, 0, 0).color(color).tube(pts, fill(len(pts), 0.055))
        s.at(x, t.cy + 0.05, t.fr - 0.09).color(metal)
        box(s, 0.08, 0.05, 0.025, 0.01, 0.01)
    s.at(0, 0, 0).color(color).tube([(t.cx - 0.48, t.cy + 0.38, t.fr - 0.08), (t.cx + 0.48, t.cy + 0.38, t.fr - 0.08)], [0.03, 0.03])
    s.at(t.cx, t.cy + 0.38, t.fr - 0.1).color(DARK)
    box(s, 0.07, 0.045, 0.02, 0.01, 0.01)
    if not belt:
        return
    y, ox, fz, bz = t.cy - 0.64, t.hw + 0.045, t.fr - 0.045, t.bk + 0.045
    loop = [(t.cx, y, fz), (t.cx + ox, y, fz), (t.cx + ox, y, bz), (t.cx - ox, y, bz), (t.cx - ox, y, fz), (t.cx, y, fz)]
    s.at(0, 0, 0).color(color).tube(loop, fill(len(loop), 0.06))
    s.at(t.cx, y, fz - 0.05).color(metal)
    box(s, 0.1, 0.075, 0.025, 0.01, 0.01)


def sling(s, t, color, buckle):
    ox, fz, bz = t.hw + 0.035, t.fr - 0.035, t.bk + 0.035
    loop = [(t.cx + 0.62, t.top + 0.045, (fz + bz) / 2), (t.cx + 0.56, t.top + 0.02, fz), (t.cx - 0.78, t.cy - 0.72, fz), (t.cx - ox, t.cy - 0.8, fz),
            (t.cx - ox, t.cy - 0.82, (fz + bz) / 2), (t.cx - ox, t.cy - 0.8, bz), (t.cx - 0.78, t.cy - 0.72, bz), (t.cx + 0.56, t.top + 0.02, bz),
            (t.cx + 0.62, t.top + 0.045, (fz + bz) / 2)]
    s.at(0, 0, 0).color(color).tube(loop, fill(len(loop), 0.05))
    if buckle is None:
        return
    dx, dy = -1.34, t.cy - 0.72 - t.top - 0.02
    s.at(t.cx - 0.11, (t.top + 0.02 + t.cy - 0.72) / 2, fz - 0.035).rotZ(math.degrees(math.atan2(-dx, dy))).color(buckle)
    box(s, 0.085, 0.065, 0.022, 0.01, 0.01)


# ---------------------------------------------------------------- weapons and shields

class Line:
    def __init__(self, x, y, z, ux, uy, ln, ang):
        self.x, self.y, self.z, self.ux, self.uy, self.len, self.ang = x, y, z, ux, uy, ln, ang

    @staticmethod
    def of(x0, y0, x1, y1, z):
        dx, dy = x1 - x0, y1 - y0
        ln = math.hypot(dx, dy)
        ux, uy = dx / ln, dy / ln
        return Line(x0, y0, z, ux, uy, ln, math.degrees(math.atan2(-ux, uy)))

    @staticmethod
    def turned(x, y, z, ang):
        r = math.radians(ang)
        return Line(x, y, z, -math.sin(r), math.cos(r), 0, ang)

    def at(self, d, side=0.0, dz=0.0):
        return (self.x + self.ux * d + self.uy * side, self.y + self.uy * d - self.ux * side, self.z + dz)


def to(s, p):
    return s.at(p[0], p[1], p[2])


def box(s, hx, hy, hz, b0, b1):
    r = 1.4142
    m = min(hx, hz)
    k0, k1 = r * max(0.15, 1 - b0 / m), r * max(0.15, 1 - b1 / m)
    s.scale(hx, 1, hz).rotY(45).lathe([0, k0, r, r, k1, 0], [-hy, -hy, -hy + b0, hy - b1, hy, hy], 4)


def sword(s, t, a, b):
    sling(s, t, LEATHER, GOLD)
    l = Line.of(t.cx - 0.62, t.cy - 1.0, t.cx + 0.4, t.cy + 0.78, t.bk + 0.2)
    to(s, l.at(l.len / 2)).rotZ(l.ang).color(0x4A2E1A)
    box(s, 0.1, l.len / 2, 0.05, 0.03, 0.02)
    for d in (0.55, 1.45):
        to(s, l.at(d)).rotZ(l.ang).color(b)
        box(s, 0.112, 0.035, 0.06, 0.01, 0.01)
    to(s, l.at(0.095)).rotZ(l.ang).color(b)
    box(s, 0.112, 0.1, 0.062, 0.07, 0.01)
    to(s, l.at(l.len - 0.05)).rotZ(l.ang).color(b)
    box(s, 0.12, 0.06, 0.065, 0.02, 0.02)
    to(s, l.at(l.len + 0.03)).rotZ(l.ang).color(b)
    box(s, 0.32, 0.04, 0.06, 0.015, 0.015)
    for side in (-0.33, 0.33):
        to(s, l.at(l.len + 0.03, side, 0)).color(b).blob(0.06, 0.06, 0.06)
    s.at(0, 0, 0).color(a).tube([l.at(l.len + 0.07), l.at(l.len + 0.46)], [0.05, 0.05])
    for k in range(4):
        to(s, l.at(l.len + 0.12 + k * 0.095)).rotZ(l.ang).color(shade(a, 0.65)).torus(0.052, 0.014, 0, 360)
    to(s, l.at(l.len + 0.53)).color(b).ellipsoid(0.08, 0.08, 0.08)
    to(s, l.at(l.len + 0.53, 0, 0.075)).color(0xD7263D).blob(0.03, 0.03, 0.015)


def katana(s, t, a, b):
    sling(s, t, 0x2A1A14, GOLD)
    l = Line.of(t.cx - 0.66, t.cy - 1.02, t.cx + 0.36, t.cy + 0.74, t.bk + 0.2)
    saya, radii = [], []
    for k in range(7):
        f = k / 6
        saya.append(l.at(l.len * f, -0.09 * math.sin(math.pi * f), 0))
        radii.append(0.055 + 0.012 * f)
    s.at(0, 0, 0).color(a).tube(saya, radii)
    to(s, saya[0]).color(GOLD).ellipsoid(0.065, 0.065, 0.065)
    to(s, l.at(l.len - 0.03)).rotZ(l.ang).color(GOLD).torus(0.068, 0.016, 0, 360)
    for d in (0.32, 0.38):
        to(s, l.at(l.len - d)).rotZ(l.ang).color(b).torus(0.07, 0.014, 0, 360)
    to(s, l.at(l.len - 0.45, 0.07, 0)).color(a).blob(0.035, 0.035, 0.03)
    s.at(0, 0, 0).color(b).tube([l.at(l.len - 0.45, 0.09, 0.02), l.at(l.len - 0.7, 0.18, 0.05), l.at(l.len - 0.95, 0.12, 0.04)], [0.016, 0.016, 0.014])
    to(s, l.at(l.len + 0.02)).rotZ(l.ang).color(0x2B2B2B).lathe([0, 0.15, 0.15, 0], [-0.02, -0.02, 0.02, 0.02], 18)
    to(s, l.at(l.len + 0.02)).rotZ(l.ang).color(GOLD).torus(0.15, 0.012, 0, 360)
    s.at(0, 0, 0).color(b).tube([l.at(l.len + 0.05), l.at(l.len + 0.5)], [0.052, 0.052])
    for k in range(5):
        to(s, l.at(l.len + 0.1 + k * 0.085, 0, 0.048)).rotZ(l.ang).rotX(90).color(0xEDE6D6).scale(0.035, 1, 0.045).lathe([0, 1, 0], [-0.008, 0, 0.008], 4)
    to(s, l.at(l.len + 0.53)).rotZ(l.ang).color(GOLD)
    box(s, 0.055, 0.03, 0.055, 0.015, 0.02)


def round_shield(s, t, a, b):
    sling(s, t, LEATHER, b)
    x, y, z0 = t.cx, t.cy, t.bk + 0.08
    face = z0 + 0.12
    s.at(x, y, z0).rotX(90).color(a).lathe([0, 1.0, 1.04, 1.0, 0], [0, 0, 0.06, 0.12, 0.12], 40)
    for a0 in (0, 180):
        s.at(x, y, face).rotX(90).color(0xA8322D).lathe([0, 0.97, 0.97, 0], [0, 0, 0.006, 0.006], 10, a0, a0 + 90)
    for px in (-0.6, -0.3, 0, 0.3, 0.6):
        h = math.sqrt(1 - px * px) * 0.97
        s.at(0, 0, 0).color(shade(a, 0.55)).tube([(x + px, y - h, face + 0.01), (x + px, y + h, face + 0.01)], [0.012, 0.012], caps=False, sides=4)
    s.at(x, y, z0 + 0.065).rotX(90).color(b).torus(1.02, 0.06, 0, 360)
    s.at(x, y, face).rotX(90).color(b).lathe([0, 0.3, 0.3, 0.22, 0.1, 0], [0, 0, 0.03, 0.12, 0.18, 0.19], 22)
    s.at(x, y, face + 0.015).rotX(90).color(shade(b, 0.8)).torus(0.32, 0.025, 0, 360)
    for k in range(10):
        ang = k * math.pi / 5
        s.at(x + math.cos(ang) * 0.88, y + math.sin(ang) * 0.88, face + 0.02).color(b).blob(0.035, 0.035, 0.025)
    for k in range(6):
        ang = k * math.pi / 3 + 0.5
        s.at(x + math.cos(ang) * 0.42, y + math.sin(ang) * 0.42, face + 0.02).color(b).blob(0.03, 0.03, 0.02)


def kite_shield(s, t, a, b):
    sling(s, t, LEATHER, METAL)

    def sh(u, v, dz=0.0):
        uu = u * 2 - 1
        w = 0.8 * (1 if v < 0.35 else (1 - (v - 0.35) / 0.65) ** 0.85)
        y = t.cy + 1.0 - v * 2.15 + 0.14 * (1 - uu * uu) * (1 - v) ** 6
        return (t.cx + uu * w, y, t.bk + 0.12 + 0.16 * (1 - uu * uu) * (1 - 0.5 * v) + dz)

    s.at(0, 0, 0).color(a).surface(sh, 18, 20)
    rim = []
    for k in range(12, -1, -1):
        p = sh(0, k / 12)
        rim.append((p[0], p[1], p[2] + 0.01))
    for k in range(1, 9):
        p = sh(k / 8, 0)
        rim.append((p[0], p[1], p[2] + 0.01))
    for k in range(1, 13):
        p = sh(1, k / 12)
        rim.append((p[0], p[1], p[2] + 0.01))
    s.at(0, 0, 0).color(METAL).tube(rim, fill(len(rim), 0.045))
    decal(s, sh, 0.44, 0.56, 0.06, 0.84, 0.012, b)
    decal(s, sh, 0.12, 0.88, 0.2, 0.31, 0.016, b)
    for q in ((0.1, 0.1), (0.9, 0.1), (0.12, 0.5), (0.88, 0.5), (0.5, 0.94)):
        p = sh(q[0], q[1])
        s.at(p[0], p[1], p[2] + 0.02).color(METAL).blob(0.035, 0.035, 0.025)


def decal(s, f, u0, u1, v0, v1, lift, color):
    s.at(0, 0, 0).color(color).surface(lambda u, v: f(u0 + (u1 - u0) * u, v0 + (v1 - v0) * v, lift), 4, 10)


# ---------------------------------------------------------------- gear

def jetpack(s, t, a, b):
    z = t.bk + 0.36
    straps(s, t, t.cy + 0.6, t.cy - 0.7, t.bk + 0.15, DARK, METAL, True)
    s.at(t.cx, t.cy + 0.05, t.bk + 0.12).color(0x3A3F48)
    box(s, 0.55, 0.75, 0.07, 0.05, 0.05)
    for side in (-1, 1):
        x, y = t.cx + side * 0.36, t.cy - 0.55
        s.at(x, y, z).color(a).lathe([0, 0.24, 0.27, 0.27, 0.24, 0.13, 0], [0, 0.01, 0.08, 1.0, 1.12, 1.26, 1.32], 24)
        for yy in (0.22, 0.86):
            s.at(x, y + yy, z).color(b).torus(0.275, 0.03, 0, 360)
        s.at(x, y + 0.54, z).color(b).lathe([0.27, 0.278, 0.278, 0.27], [-0.08, -0.08, 0.08, 0.08], 24)
        s.at(x, y - 0.24, z).color(0x55595F).lathe([0, 0.2, 0.12, 0], [0, 0, 0.24, 0.24], 16)
        s.at(x, y - 0.25, z).rotX(180).color(0xFF8A1F, glow=True).cone(0.15, 0.5, 12)
        s.at(x, y - 0.25, z).rotX(180).color(0xFFE45A, glow=True).cone(0.08, 0.32, 10)
        s.at(x + side * 0.29, y + 0.25, z).rotZ(side * 12).color(b)
        box(s, 0.025, 0.24, 0.12, 0.1, 0.02)
    s.at(0, 0, 0).color(METAL).tube([(t.cx - 0.36, t.cy + 0.3, z), (t.cx + 0.36, t.cy + 0.3, z)], [0.05, 0.05])
    s.at(t.cx, t.cy + 0.3, z + 0.08).rotX(90).color(b).torus(0.09, 0.018, 0, 360)
    gauge(s, t.cx, t.cy + 0.62, t.bk + 0.2)


def gauge(s, x, y, z):
    s.at(x, y, z).rotX(90).color(WHITE).lathe([0, 0.11, 0.11, 0], [0, 0, 0.03, 0.03], 16)
    s.at(x, y, z + 0.02).rotX(90).color(DARK).torus(0.11, 0.018, 0, 360)
    s.at(x, y, z + 0.035).rotZ(-40).move(0, 0.04, 0).color(0xD7263D)
    box(s, 0.008, 0.05, 0.005, 0.002, 0.002)


def tank(s, t, i, a, b):
    chem = "chem" in i
    x, y, z = t.cx, t.cy - 0.65, t.bk + 0.4
    vy = y + 1.34
    straps(s, t, t.cy + 0.5, t.cy - 0.6, t.bk + 0.15, DARK, METAL, True)
    s.at(x, y, z).color(a).lathe([0, 0.34, 0.37, 0.37, 0.33, 0.14, 0], [0, 0.01, 0.1, 1.08, 1.22, 1.32, 1.34], 26)
    for yy in (0.3, 0.85):
        s.at(x, y + yy, z).color(b).torus(0.372, 0.03, 0, 360)
    s.at(0, 0, 0).color(METAL).tube([(x, vy - 0.02, z), (x, vy + 0.18, z)], [0.05, 0.05])
    s.at(x, vy + 0.2, z).color(b).torus(0.11, 0.022, 0, 360)
    for k in range(2):
        s.at(0, 0, 0).color(b).tube([(x - (0.11 if k == 0 else 0), vy + 0.2, z - (0.11 if k == 1 else 0)), (x + (0.11 if k == 0 else 0), vy + 0.2, z + (0.11 if k == 1 else 0))],
                                    [0.015, 0.015], caps=False, sides=6)
    s.at(0, 0, 0).color(DARK).tube([(x + 0.05, vy + 0.08, z), (x + 0.3, vy + 0.12, z - 0.12), (t.cx + 0.62, t.top + 0.12, (t.bk + t.fr) / 2 + 0.1),
                                    (t.cx + 0.6, t.top + 0.08, t.fr - 0.02), (t.cx + 0.4, t.cy + 0.45, t.fr - 0.09)], [0.04] * 5)
    s.at(t.cx + 0.4, t.cy + 0.42, t.fr - 0.1).color(DARK).ellipsoid(0.07, 0.06, 0.06)
    if not chem:
        gauge(s, x, y + 0.98, z + 0.37)
        return
    s.at(x, y + 0.58, z + 0.36).rotX(90).color(b, glow=True).lens(0.12, 0.3, 0.02)
    for k in range(4):
        s.at(x - 0.04 + 0.06 * (k % 2), y + 0.38 + k * 0.12, z + 0.385).color(WHITE).blob(0.022, 0.022, 0.012)
    s.at(x, y + 1.0, z + 0.33).rotX(90).color(GOLD).lathe([0, 0.13, 0.13, 0], [0, 0, 0.02, 0.02], 16)
    for k in range(3):
        s.at(x, y + 1.0, z + 0.355).rotZ(k * 120).move(0, 0.065, 0).rotX(90).color(0x16161A).lens(0.045, 0.05, 0.01)
    s.at(x, y + 1.0, z + 0.36).rotX(90).color(0x16161A).lens(0.02, 0.02, 0.01)


def battery(s, t, a, b):
    z = t.bk + 0.3
    face = z + 0.24
    straps(s, t, t.cy + 0.45, t.cy - 0.7, t.bk + 0.12, DARK, METAL, True)
    s.at(t.cx, t.cy - 0.1, z).color(a)
    box(s, 0.55, 0.72, 0.24, 0.06, 0.06)
    for yy in (0.45, -0.7):
        s.at(t.cx, t.cy + yy, z).color(DARK)
        box(s, 0.555, 0.04, 0.245, 0.01, 0.01)
    for side in (-1, 1):
        s.at(t.cx + side * 0.28, t.cy + 0.62, z).color(0xD7263D if side > 0 else 0x1C1C22).lathe([0, 0.1, 0.1, 0.07, 0], [0, 0, 0.12, 0.15, 0.15], 14)
    for k in range(4):
        s.at(t.cx - 0.15, t.cy - 0.55 + k * 0.22, face + 0.01).color(b if k < 3 else shade(b, 0.35), glow=k < 3)
        box(s, 0.22, 0.07, 0.015, 0.01, 0.01)
    bolt = ((0.36, 0.3), (0.24, 0.02), (0.34, 0.0), (0.22, -0.35))
    for k in range(3):
        s.at(0, 0, 0).color(b, glow=True).tube([(t.cx + bolt[k][0], t.cy + bolt[k][1], face + 0.015), (t.cx + bolt[k + 1][0], t.cy + bolt[k + 1][1], face + 0.015)], [0.03, 0.03])
    s.at(0, 0, 0).color(0xD7263D).tube([(t.cx + 0.28, t.cy + 0.77, z), (t.cx + 0.45, t.cy + 0.95, z - 0.05), (t.cx + 0.62, t.top + 0.1, (t.bk + t.fr) / 2),
                                        (t.cx + 0.58, t.top + 0.06, t.fr - 0.03), (t.cx + 0.42, t.cy + 0.5, t.fr - 0.07)], [0.03] * 5)
    s.at(t.cx + 0.42, t.cy + 0.47, t.fr - 0.08).color(METAL).blob(0.05, 0.05, 0.04)


# ---------------------------------------------------------------- music, boards and odds and ends

def polar(pts, cx, cy):
    out = [(math.degrees(math.atan2(p[1] - cy, p[0] - cx)), math.hypot(p[0] - cx, p[1] - cy)) for p in pts]
    return sorted(out)


BODY = polar([(0, -0.56), (0.3, -0.52), (0.47, -0.38), (0.52, -0.18), (0.47, 0.02), (0.38, 0.15), (0.4, 0.3), (0.44, 0.45), (0.38, 0.6), (0.28, 0.63), (0.2, 0.52),
              (0.1, 0.42), (-0.1, 0.42), (-0.2, 0.58), (-0.3, 0.74), (-0.4, 0.7), (-0.46, 0.52), (-0.42, 0.32), (-0.4, 0.15), (-0.48, 0), (-0.53, -0.2), (-0.47, -0.4),
              (-0.3, -0.53)], 0, 0.05)
HEAD = polar([(-0.07, -0.22), (0.07, -0.22), (0.09, -0.08), (0.13, 0.06), (0.15, 0.17), (0.1, 0.25), (-0.02, 0.25), (-0.08, 0.12), (-0.09, 0)], 0.02, 0)


def radius(o, deg):
    n = len(o)
    for k in range(n - 1):
        if o[k][0] <= deg <= o[k + 1][0]:
            return lerp(o[k][1], o[k + 1][1], (deg - o[k][0]) / (o[k + 1][0] - o[k][0]))
    a0, a1 = o[-1][0], o[0][0] + 360
    x = deg + 360 if deg < o[0][0] else deg
    return lerp(o[-1][1], o[0][1], (x - a0) / (a1 - a0))


def slab(s, l, lx, ly, lz, o, k, d, rnd, nu, color):
    def fn(u, v):
        deg = u * 360 - 180
        r = radius(o, deg) * k
        inner = max(0, r - rnd)
        if v < 0.25:
            rr, z = inner * v / 0.25, d
        elif v > 0.75:
            rr, z = inner * (1 - v) / 0.25, -d
        else:
            ang = math.pi * (v - 0.25) / 0.5
            rr, z = inner + (r - inner) * math.sin(ang), d * math.cos(ang)
        th = math.radians(deg)
        return l.at(ly + rr * math.sin(th), lx + rr * math.cos(th), lz + z)
    s.at(0, 0, 0).color(color).surface(fn, nu, 14)


def guitar(s, t, a, b):
    sling(s, t, 0x1C1C22, METAL)
    l = Line.turned(t.cx - 0.28, t.cy - 0.45, t.bk + 0.2, -35)
    d = 0.085
    top = d + 0.005
    slab(s, l, 0, 0.05, 0, BODY, 1, d, 0.05, 72, a)
    slab(s, l, -0.06, 0.06, top + 0.006, BODY, 0.6, 0.007, 0.006, 48, WHITE)
    for q in ((0, 0.33, 0), (0, 0.13, 0), (0, -0.09, 8)):
        to(s, l.at(q[1], q[0], top + 0.022)).rotZ(l.ang - q[2]).color(0x1C1C22)
        box(s, 0.15, 0.042, 0.012, 0.008, 0.008)
        for k in range(6):
            to(s, l.at(q[1] + (k - 2.5) * 0.05 * math.sin(math.radians(q[2])), (k - 2.5) * 0.05, top + 0.035)).rotX(90).color(METAL).lens(0.012, 0.012, 0.004)
    to(s, l.at(-0.29, 0, top + 0.016)).rotZ(l.ang).color(METAL)
    box(s, 0.17, 0.075, 0.01, 0.006, 0.006)
    for k in range(6):
        to(s, l.at(-0.27, (k - 2.5) * 0.052, top + 0.03)).rotZ(l.ang).color(0xC9CED6)
        box(s, 0.02, 0.03, 0.01, 0.004, 0.004)
    for q in ((0.24, -0.2), (0.29, -0.33), (0.32, -0.46)):
        to(s, l.at(q[1], q[0], top + 0.012)).rotZ(l.ang).rotX(90).color(b).lathe([0, 0.045, 0.045, 0.035, 0], [0, 0, 0.035, 0.045, 0.045], 14)
    to(s, l.at(-0.04, 0.24, top + 0.03)).color(b).blob(0.02, 0.02, 0.03)
    nut, bridge = 1.8, -0.27
    scl = nut - bridge
    to(s, l.at(1.1, 0, d)).rotZ(l.ang).color(0xE0C08A)
    box(s, 0.07, 0.72, 0.035, 0.01, 0.01)
    to(s, l.at(1.12, 0, d + 0.041)).rotZ(l.ang).color(0x3B2412)
    box(s, 0.068, 0.68, 0.006, 0.003, 0.003)
    for n in range(1, 18):
        y = nut - scl * (1 - 2 ** (-n / 12))
        to(s, l.at(y, 0, d + 0.049)).rotZ(l.ang).color(0xC9CED6)
        box(s, 0.062, 0.005, 0.004, 0.002, 0.002)
        if n in (3, 5, 7, 9, 15):
            mid = nut - scl * (1 - 2 ** (-(n - 0.5) / 12))
            to(s, l.at(mid, 0, d + 0.048)).rotX(90).color(0xF0EAD8).lens(0.017, 0.017, 0.004)
        elif n == 12:
            for side in (-0.035, 0.035):
                mid = nut - scl * (1 - 2 ** (-11.5 / 12))
                to(s, l.at(mid, side, d + 0.048)).rotX(90).color(0xF0EAD8).lens(0.015, 0.015, 0.004)
    to(s, l.at(nut, 0, d + 0.05)).rotZ(l.ang).color(0xF0EAD8)
    box(s, 0.07, 0.012, 0.01, 0.003, 0.003)
    slab(s, l, 0, nut + 0.24, d - 0.005, HEAD, 1, 0.025, 0.02, 40, a)
    for k in range(6):
        y, x = nut + 0.06 + k * 0.07, -0.04 + k * 0.012
        to(s, l.at(y, x, d + 0.03)).rotZ(l.ang).rotX(90).color(METAL).lathe([0, 0.018, 0.018, 0], [0, 0, 0.03, 0.03], 8)
        to(s, l.at(y, -0.13, d - 0.005)).rotZ(l.ang).color(METAL)
        box(s, 0.035, 0.018, 0.012, 0.005, 0.005)
    for k in range(6):
        s.at(0, 0, 0).color(0xE6E9EE).tube([l.at(bridge, (k - 2.5) * 0.052, top + 0.045), l.at(nut, (k - 2.5) * 0.022, d + 0.062),
                                            l.at(nut + 0.06 + (5 - k) * 0.07, -0.04 + (5 - k) * 0.012, d + 0.055)], [0.005] * 3, caps=False, sides=4)
    for q in ((-0.56, 0), (0.79, -0.3)):
        to(s, l.at(q[0], q[1], 0)).color(METAL).blob(0.03, 0.03, 0.03)


def skateboard(s, t, a, b):
    sling(s, t, DARK, METAL)
    z = t.bk + 0.16

    def deck(u, v, dz=0.0):
        uu, vv = u * 2 - 1, v * 2 - 1
        end = max(0, abs(vv) - 0.72) / 0.28
        w = 0.42 * math.sqrt(max(0, 1 - end * end))
        return (t.cx + uu * w, t.cy + vv * 1.3, z + 0.18 * end * end + 0.025 * uu * uu + dz)

    s.at(0, 0, 0).color(0x1E1E22).surface(deck, 8, 28)
    s.at(0, 0, 0).color(a).surface(lambda u, v: deck(u, v, 0.03), 8, 28)
    decal(s, deck, 0.38, 0.62, 0.12, 0.88, 0.036, b)
    s.at(t.cx, t.cy, z + 0.075).rotX(90).color(WHITE).lens(0.15, 0.15, 0.012)
    s.at(t.cx, t.cy, z + 0.085).rotX(90).color(b).lens(0.08, 0.08, 0.012)
    for vv in (-0.62, 0.62):
        y = t.cy + vv * 1.3
        s.at(t.cx, y, z + 0.07).color(METAL)
        box(s, 0.12, 0.09, 0.025, 0.01, 0.01)
        s.at(0, 0, 0).color(0xB8BEC6).tube([(t.cx - 0.36, y, z + 0.14), (t.cx + 0.36, y, z + 0.14)], [0.035, 0.035])
        s.at(t.cx, y, z + 0.12).color(0xB8BEC6).blob(0.07, 0.05, 0.05)
        for side in (-1, 1):
            s.at(t.cx + side * 0.36, y, z + 0.14).rotZ(90).color(0xF4E8C8).lathe([0, 0.11, 0.125, 0.125, 0.11, 0], [-0.07, -0.07, -0.055, 0.055, 0.07, 0.07], 18)
            s.at(t.cx + side * 0.43, y, z + 0.14).rotZ(-side * 90).color(b).lathe([0, 0.05, 0.05, 0], [0, 0, 0.008, 0.008], 12)


def money_bag(s, t, a, b):
    sling(s, t, ROPE, None)
    x, y, z = t.cx, t.cy - 0.9, t.bk + 0.5
    pr, py = [0, 0.45, 0.62, 0.6, 0.42, 0.17, 0.15], [0, 0.02, 0.35, 0.75, 1.0, 1.15, 1.2]
    s.at(x, y, z).scale(1, 1, 0.75).color(a).lathe(pr, py, 28)
    s.at(x, y, z).scale(1, 1, 0.75).color(shade(a, 1.08)).lathe([0.15, 0.22, 0.3, 0.27], [1.2, 1.28, 1.38, 1.42], 14)
    s.at(x, y + 1.17, z).scale(1, 1, 0.75).color(ROPE).torus(0.165, 0.035, 0, 360)
    for side in (-1, 1):
        s.at(0, 0, 0).color(ROPE).tube([(x + side * 0.1, y + 1.15, z + 0.12), (x + side * 0.16, y + 0.95, z + 0.2), (x + side * 0.12, y + 0.8, z + 0.22)], [0.025, 0.025, 0.02])
    for k in range(3):
        s.at(x - 0.1 + k * 0.1, y + 1.4 + 0.03 * (k % 2), z + 0.02 * k).rotX(60 - 25 * k).rotZ(15 * k - 15).color(GOLD).lathe([0, 0.1, 0.1, 0], [0, 0, 0.025, 0.025], 14)

    def sack_z(lx, ly):
        r = pr[-1]
        for k in range(len(py) - 1):
            if py[k] <= ly <= py[k + 1]:
                r = lerp(pr[k], pr[k + 1], (ly - py[k]) / (py[k + 1] - py[k]))
        return 0.75 * math.sqrt(max(0, r * r - lx * lx))

    path = ((0.13, 0.17), (0.02, 0.24), (-0.12, 0.17), (-0.1, 0.04), (0.08, -0.03), (0.13, -0.15), (0.0, -0.24), (-0.13, -0.17))
    cy = 0.6
    dollar = [(x + p[0], y + cy + p[1], z + sack_z(p[0], cy + p[1]) + 0.025) for p in path]
    s.at(0, 0, 0).color(b).tube(dollar, fill(len(dollar), 0.035))
    s.at(0, 0, 0).color(b).tube([(x, y + cy + 0.33, z + sack_z(0, cy + 0.33) + 0.025), (x, y + cy, z + sack_z(0, cy) + 0.035), (x, y + cy - 0.33, z + sack_z(0, cy - 0.33) + 0.025)],
                                [0.025] * 3)
    s.at(x - 0.32, y + 0.3, z + sack_z(-0.32, 0.3) - 0.005).rotY(-28).rotX(90).color(shade(a, 1.18)).lens(0.12, 0.1, 0.015)


def violin_case(s, t, a, b):
    sling(s, t, 0x1C1C22, b)
    l = Line.turned(t.cx + 0.05, t.cy - 0.35, t.bk + 0.24, -30)
    py = [-0.48, -0.45, -0.34, -0.1, 0.15, 0.3, 0.42, 0.6, 0.8, 0.98, 1.1, 1.3, 1.62, 1.74, 1.76]
    pr = [0, 0.18, 0.36, 0.45, 0.42, 0.33, 0.31, 0.36, 0.36, 0.27, 0.19, 0.17, 0.16, 0.09, 0]
    to(s, l.at(0, 0, 0)).rotZ(l.ang).scale(1, 1, 0.36).color(a).lathe(pr, py, 28)
    zip_ = [l.at(py[k], pr[k] + 0.004, 0) for k in range(len(py))] + [l.at(py[k], -pr[k] - 0.004, 0) for k in range(len(py) - 2, -1, -1)]
    s.at(0, 0, 0).color(b).tube(zip_, fill(len(zip_), 0.016), caps=False, sides=6)
    for q in ((0.0, 0.45), (0.7, 0.37), (1.2, 0.18)):
        to(s, l.at(q[0], q[1], 0)).rotZ(l.ang).color(b)
        box(s, 0.035, 0.06, 0.06, 0.01, 0.01)
    s.at(0, 0, 0).color(DARK).tube([l.at(0.25, 0.33, 0), l.at(0.3, 0.45, 0), l.at(0.4, 0.48, 0), l.at(0.5, 0.45, 0), l.at(0.55, 0.34, 0)], [0.03] * 5)
    s.at(0, 0, 0).color(0xE8E0C8).tube([l.at(0.4, 0.48, 0), l.at(0.33, 0.52, 0.06), l.at(0.27, 0.5, 0.12)], [0.008] * 3, caps=False, sides=4)
    to(s, l.at(0.22, 0.5, 0.14)).rotZ(l.ang + 20).color(0xE8E0C8)
    box(s, 0.06, 0.04, 0.008, 0.004, 0.004)
    to(s, l.at(0.05, 0, 0.165)).rotZ(l.ang).rotX(90).color(b).lens(0.11, 0.055, 0.01)

    def rad(y):
        for k in range(len(py) - 1):
            if py[k] <= y <= py[k + 1]:
                return lerp(pr[k], pr[k + 1], (y - py[k]) / (py[k + 1] - py[k]))
        return 0
    for q in ((-0.3, -0.2), (-0.3, 0.2), (0.7, -0.2), (0.7, 0.2), (1.45, 0)):
        to(s, l.at(q[0], q[1], 0.36 * math.sqrt(max(0, rad(q[0]) ** 2 - q[1] ** 2)) + 0.008)).color(b).blob(0.028, 0.028, 0.016)


def lasso(s, t, a, b):
    sling(s, t, b, METAL)
    x, y, z = t.cx + 0.42, t.cy - 0.35, t.bk + 0.1
    for k in range(5):
        s.at(x + k * 0.012, y - k * 0.015, z + 0.03 + k * 0.03).rotX(90).color(a if k % 2 == 0 else shade(a, 0.88)).torus(0.42 - k * 0.025, 0.036, 0, 360)
    s.at(x - 0.05, y + 0.4, z + 0.2).rotX(90).color(shade(a, 0.8)).torus(0.07, 0.025, 0, 360)
    for side in (-1, 1):
        s.at(x + side * 0.39, y, z + 0.09).color(b).torus(0.1, 0.03, 0, 360)
    end = (x - 0.42, y - 1.0, z + 0.16)
    s.at(0, 0, 0).color(a).tube([(x - 0.3, y - 0.25, z + 0.16), (x - 0.42, y - 0.6, z + 0.15), (x - 0.35, y - 0.85, z + 0.18), end], [0.035, 0.035, 0.035, 0.03])
    for k in (-1, 0, 1):
        s.at(0, 0, 0).color(shade(a, 1.1)).tube([end, (end[0] + k * 0.05, end[1] - 0.12, end[2] + 0.02)], [0.012, 0.004], caps=False, sides=4)


def staff(s, t, a, b):
    sling(s, t, 0x4B2A6A, GOLD)
    l = Line.of(t.cx - 0.55, t.cy - 1.05, t.cx + 0.48, t.cy + 1.15, t.bk + 0.2)
    pts = [l.at(l.len * k / 8, 0.03 * math.sin(k * 1.9), 0.02 * math.cos(k * 1.3)) for k in range(9)]
    radii = [0.062 if k % 3 == 1 else 0.048 for k in range(9)]
    s.at(0, 0, 0).color(a).tube(pts, radii)
    for k in range(3):
        to(s, l.at(l.len * 0.45 + k * 0.07)).rotZ(l.ang).color(LEATHER).torus(0.058, 0.016, 0, 360)
    for d in (0.15, 0.27):
        to(s, l.at(l.len - d)).rotZ(l.ang).color(GOLD).torus(0.055, 0.016, 0, 360)
    c = l.at(l.len + 0.3)
    for k in range(3):
        ang = math.radians(k * 120)
        ox, oz = math.cos(ang) * 0.16, math.sin(ang) * 0.16
        s.at(0, 0, 0).color(shade(a, 1.1)).tube([l.at(l.len - 0.03), l.at(l.len + 0.12, ox, oz), l.at(l.len + 0.42, ox * 0.9, oz * 0.9), l.at(l.len + 0.58, ox * 0.25, oz * 0.25)],
                                                [0.04, 0.035, 0.028, 0.012])
    to(s, c).rotZ(l.ang).color(b, glow=True).lathe([0, 0.13, 0.16, 0], [-0.22, -0.06, 0.12, 0.36], 6)
    for k in range(4):
        ang = math.radians(k * 90 + 30)
        to(s, l.at(l.len + 0.3 + 0.12 * (k % 2), math.cos(ang) * 0.32, math.sin(ang) * 0.32)).color(b if k % 2 == 0 else WHITE, glow=True).blob(0.035, 0.035, 0.035)


def pitchfork(s, t, a, b):
    sling(s, t, 0x3B5E8C, METAL)
    l = Line.of(t.cx - 0.5, t.cy - 1.05, t.cx + 0.42, t.cy + 0.95, t.bk + 0.2)
    s.at(0, 0, 0).color(a).tube([l.at(0), l.at(l.len * 0.5, 0.01, 0), l.at(l.len)], [0.045, 0.047, 0.045])
    to(s, l.at(-0.03)).color(shade(a, 0.8)).ellipsoid(0.065, 0.065, 0.065)
    s.at(0, 0, 0).color(b).tube([l.at(l.len - 0.02), l.at(l.len + 0.2)], [0.058, 0.05])
    for side in (-1, 1):
        to(s, l.at(l.len + 0.08, side * 0.05, 0.03)).color(shade(b, 0.7)).blob(0.016, 0.016, 0.012)
    s.at(0, 0, 0).color(b).tube([l.at(l.len + 0.22, -0.25, 0), l.at(l.len + 0.19, 0, 0), l.at(l.len + 0.22, 0.25, 0)], [0.035, 0.04, 0.035])
    for w in (-0.24, -0.08, 0.08, 0.24):
        s.at(0, 0, 0).color(b).tube([l.at(l.len + 0.21, w, 0), l.at(l.len + 0.45, w * 1.05, 0.02), l.at(l.len + 0.75, w, 0.07), l.at(l.len + 0.95, w * 0.95, 0.13)],
                                    [0.03, 0.028, 0.022, 0])


def balloons(s, t, a, b):
    tx, ty, tz = t.cx + 0.6, t.top - 0.12, t.bk + 0.1
    s.at(0, 0, 0).color(0xD7263D).tube([(tx, ty, tz), (t.cx + 0.62, t.top + 0.04, (t.bk + t.fr) / 2), (t.cx + 0.58, t.top - 0.12, t.fr - 0.04)], [0.03] * 3)
    at = ((0.25, 1.75, 0.35), (0.85, 2.05, 0.25), (1.3, 1.6, 0.5), (0.6, 1.45, 0.75), (1.05, 2.4, 0.6))
    colors = [a, b, 0xF2C230, 0x3BB273, 0xB46BFF]
    for k, p in enumerate(at):
        bx, by, bz = t.cx + p[0], t.top + p[1], t.bk + p[2]
        s.at(bx, by, bz).color(colors[k]).lathe([0, 0.09, 0.27, 0.33, 0.3, 0.18, 0], [-0.42, -0.4, -0.22, 0.02, 0.24, 0.38, 0.42], 22)
        s.at(bx, by - 0.4, bz).rotX(180).color(colors[k]).cone(0.05, 0.07, 8)
        for side in (-1, 1):
            s.at(bx - 0.12, by + 0.16, bz + side * 0.26).color(WHITE).blob(0.05, 0.08, 0.03)
        string = [(lerp(bx, tx, i / 5) + (0.04 if i % 2 == 0 else -0.04) * (1 - i / 5), lerp(by - 0.47, ty, i / 5), lerp(bz, tz, i / 5)) for i in range(6)]
        s.at(0, 0, 0).color(0xE6E6EA).tube(string, fill(6, 0.008), caps=False, sides=4)
    for side in (-1, 1):
        s.at(tx + side * 0.07, ty, tz + 0.04).rotZ(side * 30).rotX(90).color(0xD7263D).lens(0.07, 0.04, 0.02)
    s.at(tx, ty, tz + 0.05).color(0xA81E2E).blob(0.03, 0.03, 0.025)


def pan(s, t, a, b):
    sling(s, t, LEATHER, METAL)
    x, y, z = t.cx, t.cy + 0.15, t.bk + 0.12
    s.at(x, y, z).rotX(90).color(a).lathe([0, 0.55, 0.6, 0.63, 0.6, 0.56, 0], [0, 0, 0.03, 0.18, 0.2, 0.07, 0.07], 34)
    s.at(x, y, z + 0.072).rotX(90).color(shade(a, 1.35)).lathe([0, 0.54, 0], [0, 0, 0.003], 34)
    s.at(x + 0.08, y + 0.06, z + 0.085).rotX(90).color(WHITE).lens(0.26, 0.21, 0.012)
    s.at(x - 0.08, y - 0.05, z + 0.085).rotX(90).color(WHITE).lens(0.18, 0.16, 0.012)
    s.at(x + 0.05, y + 0.03, z + 0.1).color(0xFFC21F).blob(0.09, 0.09, 0.045)
    s.at(0, 0, 0).color(a).tube([(x, y - 0.6, z + 0.1), (x, y - 0.82, z + 0.12)], [0.05, 0.05])
    s.at(x, y - 1.12, z + 0.12).color(b)
    box(s, 0.065, 0.3, 0.045, 0.04, 0.02)
    for d in (0.9, 1.0):
        s.at(x, y - d, z + 0.165).color(METAL).blob(0.018, 0.018, 0.01)
    s.at(x, y - 1.32, z + 0.168).rotX(90).color(0x1C1C22).torus(0.035, 0.012, 0, 360)
