"""
Haircuts sculpted from locks (creator: "smoothing and a better model"): every style is a darker base layer over the
scalp and dozens of tapered, slightly flattened locks laid over it, each following the head's shape from the crown out
to the hairline, then hanging, flicking or spiking as the style needs, each a shade of its own so the strands read.
The same sizes as before (the default Roblox head, cosmetics.HEAD), so hats still fit: under a hat a haircut is cut
level where that hat's edge sits (cosmetics.CUTS) and the hat covers the cut.
"""
import math
import random

import bmesh
import bpy
from mathutils import Vector

import brlib
from cosmetics import OVER_HAIR, hair_cap, hat_edge
from shapes import C, _mesh, material, shade


class Scalp:
    """The head's rounded box shape (a superellipse round its middle), a little outside it: where hair lies."""

    def __init__(self, h, grow=1.05):
        self.h = h
        self.a, self.d = h.hw * grow, h.hd * grow
        self.cy, self.b = h.cy, (h.top + 0.05 - h.cy)

    def at(self, th, lat, off=0.0):
        """The point at angle th round the head (3pi/2 = the front, -z) and latitude lat (degrees: 0 at mid-head, 90 the
        crown, below 0 down the sides), pushed off it by off; and the outward normal there."""
        la = math.radians(max(-89.0, min(89.9, lat)))
        cl, sl = math.cos(la), math.sin(la)
        r = math.copysign(abs(cl) ** 0.5, cl)
        y = math.copysign(abs(sl) ** 0.5, sl)
        c, s = math.cos(th), math.sin(th)
        p = Vector((self.h.cx + c * self.a * r, self.cy + self.b * y, self.h.cz + s * self.d * r))
        # The superellipse's normal: (r/a)^4 + (y/b)^4 = 1 -> (r^3/a, y^3/b) in its plane.
        nr, ny = r ** 3 / self.a, y ** 3 / self.b
        n = Vector((c * nr, ny, s * nr))
        n = n.normalized() if n.length > 1e-9 else Vector((0, 1, 0))
        return p + n * off, n

    def lat_of(self, y):
        """The latitude of height y (on the side)."""
        t = max(-1.0, min(1.0, (y - self.cy) / self.b))
        return math.degrees(math.asin(math.copysign(t * t, t)))


def spline(pts, n):
    """A smooth curve through the points (Catmull-Rom), n points along it."""
    P = [Vector(p) for p in pts]
    P = [P[0] * 2 - P[1]] + P + [P[-1] * 2 - P[-2]]
    out = []
    segs = len(P) - 3
    for i in range(n):
        u = i / (n - 1) * segs
        k = min(segs - 1, int(u))
        t = u - k
        p0, p1, p2, p3 = P[k], P[k + 1], P[k + 2], P[k + 3]
        out.append(0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t + (-p0 + 3 * p1 - 3 * p2 + p3) * t ** 3))
    return out


class Hair:
    def __init__(self, h, seed, color="a"):
        self.h, self.sc, self.rnd, self.color = h, Scalp(h), random.Random(seed), color
        self.parts = []

    def shade(self):
        return (self.color, 0.86 + 0.2 * self.rnd.random()) if self.color in ("a", "b") else self.color

    def lock(self, pts, ups, width, thick, color=None, sides=6, n=12, taper=1.6, root_cap=True):
        """A lock along pts (a few points, smoothed), lying with its flat side on 'ups' (the outward directions at
        those points), width x thick at the root, tapering to a point at the tip."""
        path = spline(pts, n)
        upn = spline(ups, n)
        verts, faces = [], []
        for i, p in enumerate(path):
            f = i / (n - 1)
            t = (path[min(n - 1, i + 1)] - path[max(0, i - 1)]).normalized()
            up = upn[i] - t * upn[i].dot(t)
            up = up.normalized() if up.length > 1e-6 else Vector((0, 1, 0))
            side = t.cross(up).normalized()
            k = max(0.0, 1 - f ** taper)
            for j in range(sides):
                a = 2 * math.pi * j / sides
                verts.append(tuple(p + side * math.cos(a) * width * k + up * math.sin(a) * thick * k))
        for i in range(n - 1):
            for j in range(sides):
                j1 = (j + 1) % sides
                faces.append((i * sides + j, (i + 1) * sides + j, (i + 1) * sides + j1, i * sides + j1))
        if root_cap:
            c = len(verts)
            verts.append(tuple(path[0]))
            for j in range(sides):
                faces.append((c, (j + 1) % sides, j))
        part = _mesh([tuple(C @ Vector(v)) for v in verts], faces, material(color or self.shade()))
        self.parts.append(part)
        return part

    def on(self, th, lat, off):
        return self.sc.at(th, lat, off)

    def surface_lock(self, th0, lat0, th1, lat1, width, thick, lift=0.035, bulge=0.05, flick=0.0, steps=5, color=None, hang=None):
        """A lock lying on the head from (th0, lat0) to (th1, lat1); flick lifts its tip off; hang: (dy, out) to carry
        on straight down past its end (long hair)."""
        pts, ups = [], []
        for i in range(steps):
            f = i / (steps - 1)
            th = th0 + (th1 - th0) * f
            lat = lat0 + (lat1 - lat0) * f
            off = lift + bulge * math.sin(math.pi * f) + flick * f ** 3
            p, n = self.on(th, lat, off)
            pts.append(p)
            ups.append(n)
        if hang:
            dy, out = hang
            last, n = pts[-1], ups[-1]
            radial = Vector((n.x, 0, n.z)).normalized() if Vector((n.x, 0, n.z)).length > 1e-6 else Vector((0, 0, 1))
            for k in (0.35, 0.7, 1.0):
                pts.append(last + Vector((0, -dy * k, 0)) + radial * out * math.sin(k * math.pi / 2))
                ups.append(radial)
        return self.lock(pts, ups, width, thick, color, n=max(8, 3 * len(pts)))

    def front_lat(self):
        return self.sc.lat_of(self.h.top - 0.15)

    def line_lat(self, th, front_y, side_y, back_y):
        """The hairline's latitude at angle th: front_y over the forehead, side_y over the ears, back_y at the nape."""
        s = math.sin(th)  # -1 at the front, +1 at the back
        y = side_y + (front_y - side_y) * max(0.0, -s) ** 1.5 + (back_y - side_y) * max(0.0, s) ** 1.2
        return self.sc.lat_of(y)


TAU = math.pi * 2
FRONT = 1.5 * math.pi


def cover(hr, n, front_y, side_y, back_y, width=0.12, thick=0.045, swirl=0.35, flick=0.0, lift=0.03, crown=(88, 0.0)):
    """Locks from the crown out to the hairline all round (the swirl turns them a little, like hair grows)."""
    for i in range(n):
        th = TAU * i / n + hr.rnd.uniform(-0.06, 0.06)
        lat1 = hr.line_lat(th, front_y, side_y, back_y) + hr.rnd.uniform(-3, 2)
        hr.surface_lock(th + swirl, crown[0] + hr.rnd.uniform(-4, 1.5), th, lat1, width * hr.rnd.uniform(0.85, 1.15), thick, lift=lift,
                        bulge=0.03 + 0.03 * hr.rnd.random(), flick=flick * hr.rnd.uniform(0.6, 1.2))
    for i in range(10):  # the crown's whorl, over where they all start
        th = TAU * i / 10
        hr.surface_lock(th, 89.5, th + 1.1, 74, width * 0.9, thick, lift=lift + 0.012, bulge=0.02)


def base(hr, front_y, back_y, grow=1.035, y1=None):
    """The darker layer under the locks, so no scalp shows between them, and a tuft over the crown where the locks
    start."""
    s = __import__("shapes").S()
    y1 = y1 if y1 is not None else hr.h.top + 0.04
    hair_cap(s, hr.h, grow, front_y, back_y, y1, 0.4, (hr.color, 0.62))
    s.at(hr.h.cx, y1 + 0.035, hr.h.cz).color((hr.color, 0.9)).ellipsoid(0.2, 0.05, 0.2)


def build(shape, h, color="a", tie="b", tiara=False):
    """A haircut, whole (no hat; tiara: worn with one, which sits over the front of the hair)."""
    hr = Hair(h, seed=shape)
    top, cy, brow = h.top, h.cy, h.brow
    if shape == "buzz":
        base(hr, top - 0.13, cy - 0.05, grow=1.02, y1=top + 0.02)
        for i in range(90):
            th = TAU * i / 90
            lat1 = hr.line_lat(th, top - 0.13, cy + 0.02, cy - 0.05)
            hr.surface_lock(th + 0.2, 84, th, lat1 + 4, 0.07, 0.014, lift=0.012, bulge=0.004)
        return hr
    if shape == "afro":
        base(hr, top - 0.13, cy - 0.32)
        s = __import__("shapes").S()
        s.at(h.cx, top + 0.1, h.cz + 0.25).color((color, 0.7)).ellipsoid(1.4 * h.hw, 0.72, 1.4 * h.hd)
        for i in range(85):  # big soft curls all over the outside, sunk into it
            u, v = hr.rnd.random(), hr.rnd.random()
            th, el = TAU * u, math.asin(2 * v - 1)
            x = h.cx + math.cos(el) * math.cos(th) * 1.34 * h.hw
            y = top + 0.1 + math.sin(el) * 0.68
            z = h.cz + 0.25 + math.cos(el) * math.sin(th) * 1.34 * h.hd
            if y < cy - 0.1 or (math.sin(th) < -0.55 and y < top - 0.05):  # (clear of the face)
                continue
            r = hr.rnd.uniform(0.14, 0.2)
            s.at(x, y, z).color(hr.shade()).scale(r, r, r).sphere(-90, 90, 8)
        return hr
    base(hr, top - 0.13, cy - 0.32)
    if shape == "short":
        cover(hr, 60, top - 0.12, cy + 0.02, cy - 0.3)
        for i in range(7):  # the fringe, swept to its left
            th = FRONT + (i - 3) * 0.16
            hr.surface_lock(th - 0.2, 70, th + 0.38, hr.sc.lat_of(brow + 0.1), 0.13, 0.05, bulge=0.05, flick=0.03)
    elif shape == "messy":
        cover(hr, 54, top - 0.1, cy + 0.02, cy - 0.3, flick=0.1, swirl=0.6)
        for i in range(14):  # tufts sticking out every way
            th = TAU * hr.rnd.random()
            lat = hr.rnd.uniform(40, 80)
            p, n = hr.on(th, lat, 0.02)
            tip = p + (n + Vector((hr.rnd.uniform(-0.6, 0.6), 0.4, hr.rnd.uniform(-0.6, 0.6)))).normalized() * hr.rnd.uniform(0.18, 0.3)
            hr.lock([p, p.lerp(tip, 0.5) + n * 0.04, tip], [n, n, n], 0.09, 0.05, n=8)
    elif shape == "spiky":
        cover(hr, 48, top - 0.12, cy + 0.02, cy - 0.28, width=0.11)
        for i in range(16):
            ring = 0 if i < 6 else 1
            th = TAU * (i if ring == 0 else i - 6) / (6 if ring == 0 else 10) + (0.3 if ring else 0)
            lat = 80 if ring == 0 else 58
            p, n = hr.on(th, lat, 0.0)
            d = (n * 0.6 + Vector((0, 1, 0)) + Vector((0, 0, 0.25))).normalized()
            ln = 0.42 if ring == 0 else 0.34
            hr.lock([p, p + d * ln * 0.45 + n * 0.02, p + d * ln], [n, n, n], 0.13, 0.08, n=8, taper=1.0)
    elif shape == "mohawk":
        for i in range(40):  # the shaved sides: very short locks
            th = TAU * i / 40
            if abs(math.cos(th)) < 0.25:
                continue
            hr.surface_lock(th, 70, th, hr.line_lat(th, top - 0.1, cy + 0.02, cy - 0.3), 0.08, 0.015, lift=0.012, bulge=0.004)
        for i in range(15):  # the crest, front to back along the middle of the head
            f = i / 14
            z = h.cz + (-0.92 + 1.84 * f) * h.hd
            rz = min(0.999, abs(z - h.cz) / hr.sc.d)
            p = Vector((h.cx, hr.sc.cy + hr.sc.b * (1 - rz ** 4) ** 0.25 - 0.02, z))
            ln = 0.34 + 0.2 * math.sin(math.pi * f)
            d = Vector((0, 1, -0.45 + 0.9 * f)).normalized()
            hr.lock([p, p + d * ln * 0.5, p + d * ln], [Vector((1, 0, 0))] * 3, 0.085, 0.19, n=8, taper=1.0)
    elif shape == "swoop":
        cover(hr, 56, top - 0.1, cy + 0.02, cy - 0.3)
        for i in range(6):  # the big side-swept fringe, right to left over the forehead
            f = i / 5
            p0, n0 = hr.on(FRONT + 0.7, 78 - 6 * f, 0.04)
            p1, n1 = hr.on(FRONT + 0.15, hr.sc.lat_of(top - 0.05 - 0.04 * f), 0.12)
            p2, n2 = hr.on(FRONT - 0.45, hr.sc.lat_of(brow + 0.12 - 0.05 * f), 0.08)
            p3, n3 = hr.on(FRONT - 0.85, hr.sc.lat_of(brow + 0.02 - 0.04 * f), 0.03)
            hr.lock([p0, p1, p2, p3], [n0, n1, n2, n3], 0.16, 0.06, n=14)
    elif shape == "bob":
        cover(hr, 50, top - 0.1, cy, cy - 0.2)
        for i in range(30):  # hanging to the chin all round but the face, the ends curling in
            th = TAU * i / 30 + 0.05
            if math.sin(th) < -0.6:
                continue
            hr.surface_lock(th + 0.15, 78, th, hr.sc.lat_of(cy - 0.05), 0.15, 0.055, hang=(0.42, 0.06))
        for i in range(9):  # a straight fringe
            th = FRONT + (i - 4) * 0.13
            hr.surface_lock(th, 78, th, hr.sc.lat_of(brow + 0.04), 0.13, 0.045, bulge=0.04)
    elif shape == "long":
        cover(hr, 54, top - 0.1, cy, cy - 0.25)
        for i in range(30):  # down the back and over the shoulders, framing the face
            th = TAU * i / 30
            if math.sin(th) < -0.65:
                continue
            hr.surface_lock(th + 0.1, 80, th, hr.sc.lat_of(cy - 0.15), 0.16, 0.06, hang=(0.95 + 0.25 * max(0.0, math.sin(th)), 0.12))
    elif shape in ("ponytail", "bun"):
        knot_y = top - 0.22 if shape == "ponytail" else top - 0.02
        knot = Vector((h.cx, knot_y, h.cz + h.hd * (1.08 if shape == "ponytail" else 0.62)))
        for i in range(52):  # sleek, every lock pulled back to the tie
            th = TAU * i / 52
            p0, n0 = hr.on(th, hr.line_lat(th, top - 0.12, cy + 0.02, cy - 0.28), 0.02)
            mid, nm = hr.on(th + (math.pi / 2 - th) * 0.4, 72, 0.06)
            hr.lock([p0, mid, knot + (p0 - knot) * 0.08], [n0, nm, nm], 0.11, 0.04, n=12, taper=4.0)
        s = __import__("shapes").S()
        if shape == "ponytail":
            s.at(knot.x, knot.y, knot.z + 0.02).rotX(70).color(tie).torus(0.09, 0.035, 0, 360)
            for i in range(8):  # the tail: a bundle of locks swinging out, then down
                a = TAU * i / 8
                o = Vector((math.cos(a) * 0.05, math.sin(a) * 0.04, 0))
                pts = [knot + o + Vector((0, 0, 0.04)), knot + o * 1.5 + Vector((0, -0.12, 0.2)), knot + o * 1.8 + Vector((0, -0.5, 0.3)),
                       knot + o * 1.2 + Vector((0, -0.85, 0.22))]
                hr.lock(pts, [Vector((0, 0.2, 1))] * 4, 0.07, 0.05, n=14)
        else:
            for k in range(3):  # the bun: a coil round itself
                pts, ups = [], []
                for j in range(13):
                    a = TAU * j / 12 + k * 0.7
                    rr = 0.2 - 0.05 * k
                    pts.append(knot + Vector((math.cos(a) * rr, 0.06 + 0.07 * k + 0.02 * math.sin(a * 2), math.sin(a) * rr * 0.9 + 0.08)))
                    ups.append(Vector((math.cos(a), 0.5, math.sin(a))).normalized())
                hr.lock(pts, ups, 0.1, 0.07, n=26, taper=6.0)
            s.at(knot.x, knot.y + 0.02, knot.z + 0.08).color(tie).torus(0.17, 0.03, 0, 360)
    elif shape == "pigtails":
        for i in range(50):  # parted down the middle, combed to each side
            th = TAU * i / 50
            side = 1 if math.cos(th) >= 0 else -1
            p0, n0 = hr.on(th, hr.line_lat(th, top - 0.12, cy + 0.02, cy - 0.28), 0.02)
            mid, nm = hr.on(th, 70, 0.06)
            part, npart = hr.on(math.pi / 2 if math.sin(th) > 0 else FRONT, 88, 0.04)
            part = Vector((h.cx + side * 0.02, part.y, mid.z))
            hr.lock([part, mid, p0], [npart, nm, n0], 0.11, 0.04, n=12)
        s = __import__("shapes").S()
        for side in (-1, 1):
            knot = Vector((h.cx + side * 1.12 * h.hw, cy + 0.12, h.cz + 0.15))
            s.at(knot.x, knot.y, knot.z).rotZ(90).color(tie).torus(0.09, 0.035, 0, 360)
            for i in range(7):
                a = TAU * i / 7
                o = Vector((0, math.cos(a) * 0.05, math.sin(a) * 0.05))
                pts = [knot + o, knot + o * 1.6 + Vector((side * 0.22, -0.1, 0)), knot + o * 1.4 + Vector((side * 0.32, -0.45, 0.02)),
                       knot + o + Vector((side * 0.26, -0.7, 0))]
                hr.lock(pts, [Vector((side, 0.3, 0)).normalized()] * 4, 0.07, 0.05, n=12)
    elif shape == "curly":
        cover(hr, 40, top - 0.1, cy + 0.02, cy - 0.28, width=0.13)
        for i in range(34):  # tight curls lying over the top
            th = TAU * hr.rnd.random()
            lat = hr.rnd.uniform(48, 86)
            if math.sin(th) < -0.6 and lat < 62 or tiara and math.sin(th) < -0.3:
                continue
            p, n = hr.on(th, lat, 0.05)
            side = n.cross(Vector((0, 1, 0)) if abs(n.y) < 0.9 else Vector((1, 0, 0))).normalized()
            fwd = n.cross(side).normalized()
            pts = [p + side * math.cos(TAU * j / 7) * 0.075 + fwd * math.sin(TAU * j / 7) * 0.075 + n * 0.012 * j for j in range(9)]
            hr.lock(pts, [n] * 9, 0.055, 0.05, n=18, taper=5.0)
        for i in range(22):  # ringlets: corkscrews hanging all round but the face
            th = TAU * i / 22 + 0.07
            if math.sin(th) < -0.55:
                continue
            p, n = hr.on(th, hr.line_lat(th, top - 0.1, cy + 0.05, cy - 0.2), 0.06)
            radial = Vector((n.x, 0, n.z)).normalized()
            tang = Vector((0, 1, 0)).cross(radial).normalized()
            ln = 0.45 + 0.2 * max(0.0, math.sin(th))
            pts = [p + radial * (0.04 + math.cos(TAU * j / 6) * 0.06) + tang * math.sin(TAU * j / 6) * 0.06 - Vector((0, ln * j / 15, 0)) for j in range(16)]
            hr.lock(pts, [radial] * 16, 0.05, 0.045, n=32, taper=4.0)
    return hr


def cut(y):
    """Cuts every hair part level at height y (a hat's edge), keeping what's below."""
    for obj in list(bpy.context.scene.objects):
        if obj.type != "MESH" or obj.get("br_preview"):
            continue
        bm = bmesh.new()
        bm.from_mesh(obj.data)
        co = obj.matrix_world.inverted() @ Vector((0, 0, y))
        bmesh.ops.bisect_plane(bm, geom=bm.verts[:] + bm.edges[:] + bm.faces[:], plane_co=co, plane_no=(0, 0, 1), clear_outer=True)
        if not bm.faces:
            bm.free()
            bpy.data.objects.remove(obj)
            continue
        bm.to_mesh(obj.data)
        bm.free()
        obj["br_twosided"] = 1  # (open where it's cut: the hat covers it)


def hair(shape, h, hat_shape=None):
    """The haircut for a hat with this edge (cosmetics.CUTS), as the game picks it (Cosmetics.hairMesh)."""
    under = hat_shape is not None and hat_shape not in OVER_HAIR
    if not under and not (shape == "curly" and hat_shape == "tiara"):
        build(shape, h)
        return
    if shape == "curly" and hat_shape == "tiara":
        build(shape, h, tiara=True)  # (no curls over the front, where the tiara's band goes)
        return
    edge = hat_edge(hat_shape, h)
    y = h.top if edge >= h.top - 0.1 else edge + 0.03
    build(shape, h)
    cut(y)
    if shape == "bun":  # under a hat the bun sits low at the back
        s = __import__("shapes").S()
        s.at(h.cx, h.cy + 0.1, h.cz + 1.12 * h.hd).color(("a", 0.95)).ellipsoid(0.24, 0.24, 0.2)
