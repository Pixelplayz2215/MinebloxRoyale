"""
The parked cars, made in Blender (v1.0.1, creator: "All Cars should be remade using Blender (including durability
models)"): a sedan, a sports car, a pickup, an SUV, a hatchback, a police car and a taxi, each new (stage 1), scuffed
(2), damaged (3) and nearly wrecked (4), every stage keeping the damage before it (CarBlockEntity.stage).

Block units, the nose along +z, the origin on the ground in the middle, +x the car's own left (the driver's side), the
same space the code-drawn cars used, so the invisible car blocks still fit and the engine smoke still rises over the
bonnet. Tint channel 1 is the paint (CarBlockEntity.color), channel 2 the trim (racing stripes and spoiler, the police
car's white doors).
"""
import math
import random

from brlib import apply_modifiers, box, cut, cyl, dent, lathe, mat, mesh_from, prism, quad, sphere, torus, tube

PAINT = lambda: mat("paint", "#F2F2F2", ch=1, rough=0.32, metal=0.15)
TRIM = lambda: mat("trim", "#F2F2F2", ch=2, rough=0.32, metal=0.15)
GLASS = lambda: mat("glass", "#2F4153", rough=0.08, metal=0.3)
GLINT = lambda: mat("glint", "#405A74", rough=0.1)
TIRE = lambda: mat("tire", "#202226", rough=0.92)
RIM = lambda: mat("rim", "#C9CED6", rough=0.25, metal=0.9)
DARK = lambda: mat("dark", "#1A1D22", rough=0.7)
GREY = lambda: mat("bumper", "#8C949F", rough=0.45, metal=0.4)
CHROME = lambda: mat("chrome", "#DDE3EA", rough=0.15, metal=1.0)
HEAD = lambda: mat("headlight", "#FFF3BF", glow=True)
DEAD = lambda: mat("dead_light", "#4A4F57", rough=0.5)
TAIL = lambda: mat("taillight", "#E0302E", rough=0.3)
PLATE = lambda: mat("plate", "#F2F4F8", rough=0.5)
ARCH = lambda: mat("arch", "#16181C", rough=0.9)
BARE = lambda: mat("bare_metal", "#B9C0C8", rough=0.3, metal=0.9)
INSIDE = lambda: mat("inside", "#14171C", rough=0.9)
SHARD = lambda: mat("shard", "#6F8AA6", rough=0.1, metal=0.3)
CRACK = lambda: mat("crack", "#E8F1F8", rough=0.4)
WHITE = lambda: mat("white", "#F2F4F8", rough=0.4)
RED_LIGHT = lambda: mat("red_glow", "#FF3030", glow=True)
BLUE_LIGHT = lambda: mat("blue_glow", "#3A7BFF", glow=True)
AMBER = lambda: mat("amber_glow", "#F5A623", glow=True)


class Car:
    """One car's measurements (the code-drawn cars' numbers)."""

    def __init__(self, cy, hx, hy, hz, cabin, front_y, back_y, zf, zb, wx, r, two_doors):
        self.cy, self.hx, self.hy, self.hz = cy, hx, hy, hz
        self.y0, self.y1, self.z0, self.z1, self.zt0, self.zt1, self.hx0, self.hx1 = cabin
        self.front_y, self.back_y = front_y, back_y
        self.zf, self.zb, self.wx, self.r = zf, zb, wx, r
        self.two_doors = two_doors

    @property
    def top(self):
        return self.cy + self.hy

    @property
    def bottom(self):
        return self.cy - self.hy

    def door_start(self):
        return self.z1 - (1.05 if self.two_doors else 1.25)


SEDAN = lambda: Car(0.64, 1.12, 0.30, 2.28, (0.90, 1.52, -1.25, 0.95, -0.95, 0.30, 1.00, 0.86), 0.66, 0.70, 1.45, -1.45, 1.02, 0.36, True)
KINDS = {
    "sedan": ("Sedan", SEDAN),
    "sports": ("Sports car", lambda: Car(0.52, 1.16, 0.27, 2.30, (0.76, 1.17, -1.35, 0.75, -0.95, 0.00, 0.92, 0.74), 0.52, 0.56, 1.45, -1.50, 1.06, 0.34, False)),
    "pickup": ("Pickup truck", lambda: Car(0.72, 1.15, 0.36, 2.35, (1.05, 1.80, -0.20, 1.20, -0.18, 0.78, 1.04, 0.96), 0.78, 0.85, 1.50, -1.50, 1.05, 0.42, False)),
    "suv": ("SUV", lambda: Car(0.74, 1.15, 0.36, 2.30, (1.07, 1.88, -2.05, 1.30, -1.95, 0.85, 1.06, 0.98), 0.80, 0.85, 1.50, -1.45, 1.05, 0.42, True)),
    "hatchback": ("Hatchback", lambda: Car(0.62, 1.08, 0.30, 1.95, (0.89, 1.53, -1.75, 0.95, -1.55, 0.35, 0.94, 0.82), 0.64, 0.72, 1.20, -1.20, 0.98, 0.34, True)),
    "police": ("Police car", SEDAN),
    "taxi": ("Taxi", SEDAN),
}
STAGES = {1: "New", 2: "Scuffed", 3: "Damaged", 4: "Nearly wrecked"}
# Colours for the renders (the game picks them per car: CustomStructures).
SAMPLE = {"sedan": ("#D8352C", "#F2F4F8"), "sports": ("#F2C230", "#22252B"), "pickup": ("#2F6BD8", "#F2F4F8"), "suv": ("#3BB84A", "#F2F4F8"),
          "hatchback": ("#F08A24", "#F2F4F8"), "police": ("#22252B", "#F2F4F8"), "taxi": ("#F2C230", "#22252B")}


# ---------------------------------------------------------------- the body

def body(c, kind):
    """The rounded body with its wheel arches cut out (lined dark), a gently sloping bonnet."""
    yb, yt, hz = c.bottom, c.top, c.hz
    nose_top = yt - (0.04 if kind == "pickup" or kind == "suv" else 0.08)
    pts = [(-hz, yb + 0.08), (-hz + 0.3, yb), (hz - 0.35, yb), (hz, yb + 0.1), (hz + 0.02, nose_top - 0.1), (hz - 0.25, nose_top),
           (c.z1 + 0.1, yt), (-hz + 0.3, yt), (-hz, yt - 0.06)]
    b = prism(pts, -c.hx, c.hx, PAINT(), bevel=0.17, seg=3, plane="zy")
    arch = ARCH()
    for z in (c.zf, c.zb):
        tool = cyl(0, c.r, z, c.r + 0.075, c.hx * 2 + 0.6, "x", arch, verts=18)
        cut(b, tool)
    for m in b.obj.modifiers:
        if m.type == "BOOLEAN" and hasattr(m, "material_mode"):
            m.material_mode = "TRANSFER"
    if arch.name not in [s.name for s in b.obj.data.materials]:
        b.obj.data.materials.append(arch)
    # The dark inside of each arch, so nothing shows through behind the wheel.
    for z in (c.zf, c.zb):
        for s in (-1, 1):
            torus(s * (c.hx - 0.16), c.r, z, c.r + 0.03, 0.05, "x", arch, segs=12, rsegs=4, arc=180).rot("x", 90, (s * (c.hx - 0.16), c.r, z))  # (the upper half)
    return b


def cabin(c, kind):
    """The cabin in the paint colour: a roof on sloping sides, glass set into each face inside a frame of pillars."""
    y0, y1, z0, z1, zt0, zt1, a, b = c.y0, c.y1, c.z0, c.z1, c.zt0, c.zt1, c.hx0, c.hx1
    v = [(-a, y0, z1), (a, y0, z1), (a, y0, z0), (-a, y0, z0), (-b, y1, zt1), (b, y1, zt1), (b, y1, zt0), (-b, y1, zt0)]
    f = [(0, 1, 2, 3), (4, 5, 6, 7), (0, 1, 5, 4), (2, 3, 7, 6), (1, 2, 6, 5), (3, 0, 4, 7)]
    part = mesh_from(v, f, PAINT(), bevel=0.09, seg=3)
    centre = (0, (y0 + y1) / 2, (z0 + z1 + zt0 + zt1) / 4)
    panes = {
        "windscreen": ((-a, y0, z1), (a, y0, z1), (b, y1, zt1), (-b, y1, zt1), (0.07, 0.93, 0.12, 0.88)),
        "rear": ((a, y0, z0), (-a, y0, z0), (-b, y1, zt0), (b, y1, zt0), (0.09, 0.91, 0.14, 0.86)),
    }
    sides = {+1: ((a, y0, z1), (a, y0, z0), (b, y1, zt0), (b, y1, zt1)), -1: ((-a, y0, z0), (-a, y0, z1), (-b, y1, zt1), (-b, y1, zt0))}
    for s, face in sides.items():
        if c.two_doors:
            panes[f"side{s}a"] = (*face, (0.06, 0.47, 0.16, 0.84))
            panes[f"side{s}b"] = (*face, (0.53, 0.94, 0.16, 0.84))
        else:
            panes[f"side{s}a"] = (*face, (0.08, 0.92, 0.16, 0.84))
    return part, panes, centre


def lerp(p, q, t):
    return tuple(p[i] + (q[i] - p[i]) * t for i in range(3))


def pane_point(face, u, v):
    a, b, cc, d = face[:4]
    return lerp(lerp(a, b, u), lerp(d, cc, u), v)


def pane_corners(face, u0, u1, v0, v1):
    return [pane_point(face, u0, v0), pane_point(face, u1, v0), pane_point(face, u1, v1), pane_point(face, u0, v1)]


def window(face, centre, state, seed):
    """One window: glass (with a glint), cracked, or smashed (a dark hole with shards round the frame)."""
    u0, u1, v0, v1 = face[4]
    corners = pane_corners(face, u0, u1, v0, v1)
    if state == "smashed":
        quad(*corners, INSIDE(), lift=0.012, center=centre)
        rnd = random.Random(seed)
        for e in range(4):
            for k in range(3):
                t0, t1 = k / 3 + 0.05 * rnd.random(), (k + 1) / 3 - 0.05 * rnd.random()
                depth, tm = 0.12 + 0.2 * rnd.random(), (t0 + t1) / 2
                uv = [((t0, 0), (t1, 0), (tm, depth)), ((1, t0), (1, t1), (1 - depth, tm)), ((t1, 1), (t0, 1), (tm, 1 - depth)), ((0, t1), (0, t0), (depth, tm))][e]
                pts = [pane_point(face, u0 + (u1 - u0) * uu, v0 + (v1 - v0) * vv) for uu, vv in uv]
                quad(pts[0], pts[1], pts[2], pts[2], SHARD(), lift=0.022, center=centre)
        return
    quad(*corners, GLASS(), lift=0.012, center=centre)
    # A soft glint across the glass.
    g = pane_corners(face, u0 + (u1 - u0) * 0.15, u0 + (u1 - u0) * 0.3, v0 + (v1 - v0) * 0.15, v0 + (v1 - v0) * 0.85)
    quad(g[0], g[1], lerp(g[2], g[1], -0.3), lerp(g[3], g[0], -0.3), GLINT(), lift=0.016, center=centre)  # (subtle: GLINT is close to the glass)
    if state == "cracked":
        cracks(face, centre, seed)


def cracks(face, centre, seed, size=1.0):
    """A starburst of cracks round an impact point, with a broken ring round it."""
    u0, u1, v0, v1 = face[4]
    rnd = random.Random(seed)
    cu, cv = 0.3 + 0.4 * rnd.random(), 0.35 + 0.35 * rnd.random()

    def at(a, b):
        return pane_point(face, u0 + (u1 - u0) * max(0.02, min(0.98, a)), v0 + (v1 - v0) * max(0.02, min(0.98, b)))

    def line(a0, b0, a1, b1):
        p0, p1 = at(a0, b0), at(a1, b1)
        d = [p1[i] - p0[i] for i in range(3)]
        ln = math.sqrt(sum(x * x for x in d)) or 1
        # A thin strip across the pane: sideways = along the pane, at right angles to the line.
        n = face_normal(face, centre)
        side = cross(d, n)
        sl = math.sqrt(sum(x * x for x in side)) or 1
        w = [x / sl * 0.007 for x in side]
        quad(tuple(p0[i] - w[i] for i in range(3)), tuple(p1[i] - w[i] for i in range(3)), tuple(p1[i] + w[i] for i in range(3)),
             tuple(p0[i] + w[i] for i in range(3)), CRACK(), lift=0.02, center=centre)
        return ln

    for k in range(8):
        ang = k * math.pi * 2 / 8 + (rnd.random() * 2 - 1) * 0.3
        ln = size * (0.18 + 0.22 * rnd.random())
        mid = ln * (0.4 + 0.2 * rnd.random())
        kink = ang + (rnd.random() * 2 - 1) * 0.4
        x1, y1 = cu + math.cos(ang) * mid, cv + math.sin(ang) * mid * 1.4
        line(cu, cv, x1, y1)
        line(x1, y1, x1 + math.cos(kink) * (ln - mid), y1 + math.sin(kink) * (ln - mid) * 1.4)
    ring = size * 0.09
    for k in range(7):
        a0, a1 = k * math.pi * 2 / 8, (k + 1) * math.pi * 2 / 8
        line(cu + math.cos(a0) * ring, cv + math.sin(a0) * ring * 1.4, cu + math.cos(a1) * ring, cv + math.sin(a1) * ring * 1.4)


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def face_normal(face, centre):
    a, b, _, d = face[:4]
    n = cross([b[i] - a[i] for i in range(3)], [d[i] - a[i] for i in range(3)])
    ln = math.sqrt(sum(x * x for x in n)) or 1
    n = [x / ln for x in n]
    mid = pane_point(face, 0.5, 0.5)
    if sum(n[i] * (mid[i] - centre[i]) for i in range(3)) < 0:
        n = [-x for x in n]
    return n


# ---------------------------------------------------------------- wheels, lights, trim

def wheels(c, stage):
    """Four wheels: a rounded tyre with tread grooves, a five-spoke silver rim with a centre cap. Damaged, the front left
    one has lost its hubcap: a bare dark hub and its wheel nuts."""
    w = 0.15
    for z in (c.zf, c.zb):
        for s in (-1, 1):
            x = s * c.wx
            lathe([(0.0, -w), (c.r * 0.62, -w), (c.r * 0.9, -w), (c.r, -w * 0.55), (c.r, w * 0.55), (c.r * 0.9, w), (c.r * 0.62, w), (0.0, w)],
                  x, c.r, z, TIRE(), segs=20, axis="x")
            face = x + s * (w + 0.005)
            if stage >= 3 and s > 0 and z == c.zf:
                cyl(face, c.r, z, c.r * 0.42, 0.03, "x", DARK(), verts=20)
                for i in range(5):
                    a = i * math.pi * 2 / 5
                    cyl(face + s * 0.02, c.r + math.cos(a) * c.r * 0.25, z + math.sin(a) * c.r * 0.25, c.r * 0.05, 0.03, "x", GREY(), verts=8)
                continue
            cyl(face, c.r, z, c.r * 0.62, 0.035, "x", RIM(), verts=18)
            torus(face + s * 0.02, c.r, z, c.r * 0.6, 0.025, "x", RIM(), segs=18, rsegs=4)
            for i in range(5):
                a = i * math.pi * 2 / 5 + 0.3
                tube([(face + s * 0.025, c.r, z), (face + s * 0.025, c.r + math.cos(a) * c.r * 0.56, z + math.sin(a) * c.r * 0.56)], 0.035, RIM(), segs=6)
            cyl(face + s * 0.045, c.r, z, c.r * 0.17, 0.03, "x", DARK(), verts=14)


def front(c, stage, kind):
    """The nose: rounded glowing headlights (the left one out when nearly wrecked), a dark grille with chrome bars, a
    number plate and a grey bumper (hanging from one end once damaged)."""
    z, y, hx = c.hz, c.front_y, c.hx
    for s in (-1, 1):
        lightmat = DEAD() if stage >= 4 and s > 0 else HEAD()
        box(s * (hx - 0.48), y + 0.02, z - 0.06, s * (hx - 0.1), y + 0.17, z + 0.05, lightmat, bevel=0.05)
        box(s * (hx - 0.52), y - 0.01, z - 0.07, s * (hx - 0.06), y + 0.2, z + 0.035, DARK(), bevel=0.05)
    box(-0.42, y - 0.08, z - 0.05, 0.42, y + 0.12, z + 0.04, DARK(), bevel=0.04)
    for k in range(3):
        box(-0.38, y - 0.05 + k * 0.06, z + 0.03, 0.38, y - 0.03 + k * 0.06, z + 0.055, CHROME())
    bumper = box(-hx + 0.02, y - 0.3, z - 0.05, hx - 0.02, y - 0.13, z + 0.12, GREY(), bevel=0.06, seg=3)
    plate = box(-0.24, y - 0.26, z + 0.11, 0.24, y - 0.14, z + 0.135, PLATE(), bevel=0.01)
    if stage >= 3:  # hanging from its right end, the left end down
        for p in (bumper, plate):
            p.rot("z", -10, (-hx + 0.12, y - 0.2, z + 0.04))
    if kind == "police":
        for s in (-1, 1):
            box(s * 0.2, y - 0.45, z + 0.1, s * 0.28, y + 0.15, z + 0.3, DARK(), bevel=0.03)
        box(-0.55, y - 0.05, z + 0.25, 0.55, y + 0.05, z + 0.32, DARK(), bevel=0.03)
        box(-0.55, y - 0.35, z + 0.25, 0.55, y - 0.25, z + 0.32, DARK(), bevel=0.03)


def back(c):
    """The tail: red lights at the corners, a number plate and a grey bumper."""
    z, y, hx = -c.hz, c.back_y, c.hx
    for s in (-1, 1):
        box(s * (hx - 0.44), y - 0.02, z - 0.04, s * (hx - 0.08), y + 0.14, z + 0.06, TAIL(), bevel=0.04)
    box(-0.24, y - 0.12, z - 0.035, 0.24, y + 0.0, z - 0.01, PLATE(), bevel=0.01)
    box(-c.hx + 0.02, y - 0.32, z - 0.12, c.hx - 0.02, y - 0.15, z + 0.05, GREY(), bevel=0.06, seg=3)


def mirrors(c, stage):
    """Side mirrors at the foot of the front pillars: an arm, a housing in the paint colour and the mirror glass facing
    back. Damaged, the left one has snapped off at its arm."""
    y = c.y0 + 0.13
    t = (y - c.y0) / (c.y1 - c.y0)
    side = c.hx0 + (c.hx1 - c.hx0) * t
    z = c.z1 + (c.zt1 - c.z1) * t - 0.12
    for s in (-1, 1):
        if stage >= 3 and s > 0:
            box(s * side, y - 0.022, z - 0.022, s * (side + 0.08), y + 0.022, z + 0.022, DARK())
            continue
        box(s * side, y - 0.025, z - 0.025, s * (side + 0.15), y + 0.025, z + 0.025, DARK())
        box(s * (side + 0.12), y - 0.04, z - 0.05, s * (side + 0.27), y + 0.1, z + 0.05, PAINT(), bevel=0.03)
        box(s * (side + 0.135), y - 0.025, z - 0.055, s * (side + 0.255), y + 0.085, z - 0.045, mat("mirror", "#D3E6F5", rough=0.05, metal=1.0))


def seams(c):
    """Door seams (thin dark lines) and handles on both sides."""
    zs = [c.z1 - 0.05, c.door_start()] + ([c.door_start() - 1.0] if c.two_doors else [])
    ylo, yhi = c.bottom + 0.1, c.top - 0.06
    for s in (-1, 1):
        x = s * (c.hx + 0.004)
        for z in zs:
            box(x - 0.006, ylo, z - 0.008, x + 0.006, yhi, z + 0.008, DARK())
        for z0, z1 in zip(zs, zs[1:]):
            box(x - s * 0.01, c.cy + 0.08, z0 - 0.42, x + s * 0.03, c.cy + 0.12, z0 - 0.22, CHROME())


# ---------------------------------------------------------------- the extras each kind has

def extras(c, kind, stage):
    if kind == "sports":
        # Twin racing stripes nose to tail (bonnet, roof, boot), a spoiler on two posts, side vents.
        for s in (-0.22, 0.22):
            if stage < 4:  # (down the bonnet's slope)
                z0, z1, y0, y1 = c.z1 + 0.15, c.hz - 0.3, c.top + 0.007, c.top - 0.065
                ang = math.degrees(math.atan2(y1 - y0, z1 - z0))
                box(s - 0.1, y0 - 0.005, z0, s + 0.1, y0 + 0.005, z0 + math.hypot(z1 - z0, y1 - y0), TRIM()).rot("x", -ang, (0, y0, z0))
            box(s - 0.1, c.y1 + 0.002, c.zt0 + 0.05, s + 0.1, c.y1 + 0.012, c.zt1 - 0.05, TRIM())
            box(s - 0.1, c.top + 0.002, -c.hz + 0.12, s + 0.1, c.top + 0.012, c.z0 - 0.05, TRIM())
        for s in (-0.7, 0.7):
            box(s - 0.04, c.top - 0.02, -2.1, s + 0.04, c.top + 0.16, -2.0, DARK(), bevel=0.01)
        box(-1.08, c.top + 0.13, -2.3, 1.08, c.top + 0.19, -1.9, TRIM(), bevel=0.03).rot("x", -8, (0, c.top + 0.16, -2.1))
        for s in (-1, 1):
            for k in range(3):
                box(s * (c.hx - 0.01), c.cy - 0.04 + k * 0.06, 0.2, s * (c.hx + 0.012), c.cy - 0.01 + k * 0.06, 0.8, DARK())
    elif kind == "pickup":
        # The open bed behind the cab: a dark floor with low walls and a tailgate.
        box(-1.0, c.top - 0.02, -2.25, 1.0, c.top + 0.01, -0.25, mat("bed", "#3A3F47", rough=0.8))
        for s in (-1, 1):
            box(s * 1.0, c.top - 0.02, -2.3, s * 1.14, c.top + 0.3, -0.2, PAINT(), bevel=0.05)
        box(-1.14, c.top - 0.02, -2.34, 1.14, c.top + 0.3, -2.2, PAINT(), bevel=0.05)
        box(-1.08, c.top - 0.02, -0.28, 1.08, c.top + 0.3, -0.16, PAINT(), bevel=0.05)
    elif kind == "suv":
        # Roof rails, a spare wheel on the back door.
        for s in (-0.75, 0.75):
            tube([(s, c.y1 + 0.02, -1.8), (s, c.y1 + 0.1, -1.7), (s, c.y1 + 0.1, 0.6), (s, c.y1 + 0.02, 0.7)], 0.035, DARK(), segs=8)
        lathe([(0.0, -0.09), (0.25, -0.09), (0.36, -0.06), (0.36, 0.06), (0.25, 0.09), (0.0, 0.09)], 0, 1.25, -2.42, TIRE(), segs=24, axis="z")
        cyl(0, 1.25, -2.52, 0.2, 0.03, "z", RIM(), verts=20)
        cyl(0, 1.25, -2.545, 0.07, 0.02, "z", DARK(), verts=12)
    elif kind == "police":
        # White front doors (the trim colour), a light bar (red and blue) on the roof.
        d0, d1 = c.door_start(), c.z1 - 0.05
        for s in (-1, 1):
            if stage >= 4 and s > 0:
                continue  # (that door hangs open: its white goes with it)
            box(s * (c.hx - 0.01), c.cy - 0.18, d0 + 0.02, s * (c.hx + 0.012), c.top - 0.08, d1 - 0.02, TRIM())
        box(-0.62, c.y1 - 0.01, -0.3, 0.62, c.y1 + 0.08, 0.0, DARK(), bevel=0.03)
        box(-0.6, c.y1 + 0.07, -0.28, -0.04, c.y1 + 0.18, -0.02, RED_LIGHT(), bevel=0.04)
        box(0.04, c.y1 + 0.07, -0.28, 0.6, c.y1 + 0.18, -0.02, BLUE_LIGHT(), bevel=0.04)
    elif kind == "taxi":
        # A checker stripe along both sides and the lit roof sign.
        d0, d1 = c.door_start(), c.z1 - 0.05
        for s in (-1, 1):
            for i in range(12):
                z = -1.65 + i * 0.3
                if stage >= 4 and s > 0 and z + 0.15 > d0 and z - 0.15 < d1:
                    continue
                m = DARK() if i % 2 == 0 else WHITE()
                box(s * (c.hx - 0.01), 0.76, z - 0.15, s * (c.hx + 0.012), 0.88, z + 0.15, m)
        box(-0.4, c.y1 - 0.01, -0.3, 0.4, c.y1 + 0.24, 0.0, WHITE(), bevel=0.05)
        box(-0.32, c.y1 + 0.04, -0.33, 0.32, c.y1 + 0.19, 0.03, AMBER(), bevel=0.02)  # (its lit faces front and back)


# ---------------------------------------------------------------- damage

def scratch(points, s):
    """A scratch down to bare metal along the side (thin, just off the paint)."""
    tube([(s * (0.0 + p[0]), p[1], p[2]) for p in points], 0.008, BARE(), segs=4, caps=False)


def open_door(c, kind):
    """Nearly wrecked: the driver's door hangs wide open on its front hinge, a dark doorway where it was."""
    d0, d1 = c.door_start(), c.z1 - 0.05
    ylo, yhi = c.bottom + 0.12, c.y0 + 0.02
    box(c.hx - 0.02, ylo, d0, c.hx + 0.016, yhi, d1, INSIDE())
    door = [box(c.hx + 0.02, ylo, d0, c.hx + 0.09, yhi, d1, TRIM() if kind == "police" else PAINT(), bevel=0.03)]
    door.append(box(c.hx + 0.09, c.cy + 0.08, d0 + 0.1, c.hx + 0.11, c.cy + 0.12, d0 + 0.3, CHROME(), bevel=0.01))
    door.append(box(c.hx + 0.0, ylo, d0, c.hx + 0.02, yhi, d1, INSIDE()))
    if kind == "taxi":
        for i in range(12):
            z = -1.65 + i * 0.3
            lo, hi = max(z - 0.15, d0), min(z + 0.15, d1)
            if hi > lo:
                door.append(box(c.hx + 0.09, 0.76, lo, c.hx + 0.1, 0.88, hi, DARK() if i % 2 == 0 else WHITE()))
    for p in door:
        p.rot("y", -65, (c.hx + 0.02, 0, d1))


def buckled_bonnet(c):
    """Nearly wrecked: the bonnet buckled up into a ridge across it, the dark engine bay under its sides."""
    top, za, zb = c.top, c.z1 + 0.12, c.hz - 0.12
    zm = za + (zb - za) * 0.55
    rise, w = 0.2, c.hx - 0.18
    for p0, p1 in (((za, top), (zm, top + rise)), ((zm, top + rise), (zb, top))):
        mz, my = (p0[0] + p1[0]) / 2, (p0[1] + p1[1]) / 2 + 0.02
        ln = math.hypot(p1[0] - p0[0], p1[1] - p0[1])
        ang = math.degrees(math.atan2(p1[1] - p0[1], p1[0] - p0[0]))
        box(-w, my - 0.022, mz - ln / 2 - 0.02, w, my + 0.022, mz + ln / 2 + 0.02, PAINT(), bevel=0.015).rot("x", ang, (0, my, mz))
    for s in (-1, 1):
        x = s * (w - 0.012)
        mesh_from([(x, top + 0.01, za), (x, top + 0.01, zb), (x, top + rise, zm)], [(0, 1, 2)], INSIDE(), smooth=False)


def damage(c, b, stage, kind):
    if stage < 2:
        return
    s_left = 1
    if stage < 4:  # (nearly wrecked, the door hangs open and the bonnet is buckled: these go with them)
        dent(b, (c.hx, c.cy + 0.02, c.z1 - 0.55), 0.26, 0.06, (-1, 0, 0), seed=1)
        dent(b, (0.35, c.top, c.z1 + 0.6), 0.26, 0.05, (0, -1, 0), seed=2)
        for k, (zs, ys) in enumerate(((c.z1 - 1.0, c.cy - 0.02), (c.z1 - 0.7, c.cy + 0.06), (c.z1 - 0.2, c.cy - 0.06))):
            ys += 0.03 * k
            scratch([(c.hx + 0.004, ys, zs), (c.hx + 0.004, ys - 0.02, zs + 0.35), (c.hx + 0.004, ys - 0.05, zs + 0.7)], s_left)
    if stage >= 3:
        dent(b, (-c.hx, c.cy, c.z0 + 0.4), 0.28, 0.06, (1, 0, 0), seed=3)
        dent(b, (c.hx, c.cy - 0.04, c.hz - 0.55), 0.22, 0.05, (-1, 0, 0), seed=4)
        if stage < 4:
            dent(b, (-0.4, c.top, c.hz - 0.5), 0.22, 0.05, (0, -1, 0), seed=5)
        for zs, ys in ((c.z0 + 0.2, c.cy + 0.02), (c.z0 + 0.6, c.cy - 0.04)):
            scratch([(c.hx + 0.004, ys, zs), (c.hx + 0.004, ys - 0.03, zs + 0.4), (c.hx + 0.004, ys - 0.07, zs + 0.8)], -1)
    if stage >= 4:
        open_door(c, kind)
        buckled_bonnet(c)


def build(kind, stage):
    c = KINDS[kind][1]()
    b = body(c, kind)
    cab, panes, centre = cabin(c, kind)
    if stage >= 3:
        dent(cab, (0.15, c.y1, (c.zt0 + c.zt1) / 2 + 0.1), 0.26, 0.05, (0, -1, 0), seed=6)
    # Windows: the windscreen cracks first, then a window on each side and the back; nearly wrecked, all smashed.
    for i, (name, face) in enumerate(panes.items()):
        if stage >= 4:
            state = "smashed"
        elif name == "windscreen" and stage >= 2 or stage >= 3 and name in ("rear", "side1a", "side-1b" if c.two_doors else "side-1a"):
            state = "cracked"
        else:
            state = "glass"
        window(face, centre, state, 7 + i)
    wheels(c, stage)
    front(c, stage, kind)
    back(c)
    mirrors(c, stage)
    seams(c)
    extras(c, kind, stage)
    damage(c, b, stage, kind)


def entry(kind, stage):
    name = KINDS[kind][0]
    paint, trim = SAMPLE[kind]
    return (f"{name} - stage {stage} {STAGES[stage]}", lambda: build(kind, stage),
            {"scale": 1.0, "ao": 0.7, "folder": name, "lod": 0.3, "variants": {"": {1: paint, 2: trim}},
             "views": {"Front": 0, "Left": 90, "Back": 180, "Right": 270}})


ITEMS = {f"car_{k}_{s}": entry(k, s) for k in KINDS for s in STAGES}
