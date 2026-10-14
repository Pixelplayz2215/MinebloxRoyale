"""
The 46 gliders, made in Blender (v1.0.1, creator: "Gliders should be polished and more scaled using Blender"): the nine
shapes of the gliders sheet (wing, umbrella, parachute, kite, delta, bat, hot-air balloon, leaf, Dominus wings) with
each glider's own decoration (its "deco"), every canopy SCALE times the old size over the same frame.

Roblox root space (studs, y up from the hips, the avatar facing -z), as RobloxAvatarRenderer draws it: the grips sit in
the raised hands, the crossbar over the head; the canopy above. Tint channel 1 is the glider's colour, channel 2 its
second colour (gliders sheet color / color2); a darker shade of either is that channel on a grey.
"""
import math

from mathutils import Matrix, Vector

import brlib
from brlib import mat

SCALE = 1.45  # (creator: "more scaled")
Y = 5.3  # the canopy's height before scaling, studs above the hips
GRIP_X, GRIP_Y, BAR_X, BAR_Y, BAR_Z = 1.11, 2.95, 1.0, 3.7, -0.22
FIXED = {"DARK": "#2E3138", "METAL": "#9AA2AD", "GRIP": "#18181C", "WOOD": "#6B4423", "LINE": "#E6E1D3", "GOLD": "#F2C230", "WHITE": "#F4F6FA",
         "STAR": "#FFF3B0", "RED": "#D7263D", "BLUE": "#2F6BD8", "ORANGE": "#FF8A1F", "YELLOW": "#FFD23A", "BLACK": "#1C1C22"}

C = Matrix(((1, 0, 0, 0), (0, 0, -1, 0), (0, 1, 0, 0), (0, 0, 0, 1)))  # Minecraft -> Blender
CI = C.inverted()


# ---------------------------------------------------------------- colours

def col(c):
    """'a' / 'b' (the glider's colours), ('a', k) a shade of one, '#rrggbb', or a FIXED name."""
    if isinstance(c, tuple):
        ch, k = c
        g = max(0, min(255, round(255 * k)))
        return mat(f"{ch}_{g}", f"#{g:02X}{g:02X}{g:02X}", ch=1 if ch == "a" else 2, rough=0.5)
    if c in ("a", "b"):
        return col((c, 1.0))
    h = FIXED.get(c, c)
    return mat("c" + h.lstrip("#"), h, rough=0.5, metal=0.6 if c in ("METAL", "GOLD") else 0.0)


def glow(h):
    return mat("g" + h.lstrip("#"), h, glow=True)


def tint_glow(ch):
    """One of the glider's own colours, glowing (neon)."""
    return mat(ch + "_glow", "#FFFFFF", ch=1 if ch == "a" else 2, glow=True)


# ---------------------------------------------------------------- Smooth-like placing

class T:
    """A placement in Minecraft space: at(), then move / turn / scale, like the Java Smooth."""

    def __init__(self, x=0.0, y=0.0, z=0.0):
        self.m = Matrix.Translation((x, y, z))

    def move(self, x, y, z):
        self.m = self.m @ Matrix.Translation((x, y, z))
        return self

    def rotX(self, d):
        self.m = self.m @ Matrix.Rotation(math.radians(d), 4, "X")
        return self

    def rotY(self, d):
        self.m = self.m @ Matrix.Rotation(math.radians(d), 4, "Y")
        return self

    def rotZ(self, d):
        self.m = self.m @ Matrix.Rotation(math.radians(d), 4, "Z")
        return self

    def scale(self, x, y, z):
        self.m = self.m @ Matrix.Diagonal((x, y, z, 1))
        return self

    def align(self, n):
        """Turns so the piece's own y points along n (Minecraft)."""
        q = Vector((0, 1, 0)).rotation_difference(Vector(n).normalized())
        self.m = self.m @ q.to_matrix().to_4x4()
        return self

    def put(self, part):
        part.obj.matrix_world = C @ self.m @ CI @ part.obj.matrix_world
        return part

    def pt(self, x, y, z):
        return tuple(self.m @ Vector((x, y, z)))

    # Shapes, each made at the origin and placed.
    def blob(self, rx, ry, rz, c, segs=14):
        return self.put(brlib.sphere(0, 0, 0, rx, ry, rz, c if not isinstance(c, (str, tuple)) else col(c), segs=segs, rings=max(6, segs // 2)))

    def lens(self, a, b, t, c):
        return self.put(brlib.sphere(0, 0, 0, a, max(t * 0.5, 0.006), b, c if not isinstance(c, (str, tuple)) else col(c), segs=10, rings=5))

    def cone(self, r, h, c, n=10):
        return self.put(brlib.cyl(0, h / 2, 0, r, h, "y", c if not isinstance(c, (str, tuple)) else col(c), verts=n, r2=0.0))

    def torus(self, R, r, c, segs=28):
        return self.put(brlib.torus(0, 0, 0, R, r, "y", c if not isinstance(c, (str, tuple)) else col(c), segs=segs, rsegs=6))

    def lathe(self, rs, ys, c, segs=18):
        return self.put(brlib.lathe(list(zip(rs, ys)), 0, 0, 0, c if not isinstance(c, (str, tuple)) else col(c), segs=segs))


def tube(pts, radii, c, segs=8):
    rs = [max(r, 0.004) for r in radii] if isinstance(radii, (list, tuple)) else radii
    return brlib.tube(pts, rs, c if not isinstance(c, (str, tuple)) else col(c), segs=segs)


def sheet(fn, nu, nv, c, thick=0.0):
    """A canopy or decoration sheet from fn(u, v) over 0..1 (thick > 0: a solid skin)."""
    return brlib.surface(fn, nu, nv, c if not isinstance(c, (str, tuple)) else col(c), thickness=thick)


def panel(m, lat0, lat1, nlat, nlon, lon0, lon1, c):
    """Part of a unit sphere (latitudes, longitudes in degrees) placed by matrix m: dome canopies' gores."""
    def fn(u, v):
        lon, lat = math.radians(lon0 + (lon1 - lon0) * u), math.radians(lat1 + (lat0 - lat1) * v)
        return tuple(m @ Vector((math.cos(lat) * math.cos(lon), math.sin(lat), math.cos(lat) * math.sin(lon))))
    return sheet(fn, nlon, nlat, c, thick=0.03)


# Frame work that joins the (scaled) canopy to the crossbar: done once the canopy is scaled.
LATER = []


def SC(p):
    """A canopy point after scaling."""
    x, y, z = p
    return (x * SCALE, BAR_Y + (y - BAR_Y) * SCALE, BAR_Z + (z - BAR_Z) * SCALE)


def later_tube(canopy_pts, frame_pts, radii, c, segs=6):
    LATER.append(lambda: tube([SC(p) for p in canopy_pts] + list(frame_pts), radii, c, segs))


def struts(spread, y, c):
    for side in (-1, 1):
        later_tube([(side * spread, y, 0)], [(side * BAR_X, BAR_Y, BAR_Z)], [0.04, 0.035], c)


def handles():
    for side in (-1, 1):
        tube([(side * GRIP_X, GRIP_Y - 0.25, BAR_Z), (side * (GRIP_X + BAR_X) / 2, BAR_Y - 0.25, BAR_Z), (side * BAR_X, BAR_Y, BAR_Z)], 0.045, "METAL")
        tube([(side * GRIP_X, GRIP_Y - 0.3, BAR_Z), (side * GRIP_X, GRIP_Y + 0.25, BAR_Z)], 0.08, "GRIP", segs=10)
    tube([(-BAR_X, BAR_Y, BAR_Z), (BAR_X, BAR_Y, BAR_Z)], 0.045, "METAL")


# ---------------------------------------------------------------- canopy helpers

def point(top, u, v):
    return Vector(top(u, v))


def normal(top, u, v):
    e = 0.03
    du = point(top, u + e, v) - point(top, u - e, v)
    dv = point(top, u, v + e) - point(top, u, v - e)
    n = dv.cross(du)
    if n.length < 1e-9:
        n = Vector((0, 1, 0))
    n.normalize()
    return -n if n.y < 0 else n


def spot(top, u, v, lift, under):
    """A placement on the canopy at (u, v), lifted along its normal, its own y along that normal (under: the
    underside)."""
    p, n = point(top, u, v), normal(top, u, v)
    if under:
        n = -n
    lt = lift + 0.05 if under else lift
    q = p + n * lt
    return T(q.x, q.y, q.z).align(n)


def edge(top, v, r, c):
    pts = [tuple(point(top, -1 + i / 6, v) - Vector((0, 0.035, 0))) for i in range(13)]
    radii = [r * (1 - 0.35 * abs(-1 + i / 6)) for i in range(13)]
    tube(pts, radii, c)


def rib(top, u, r, c, line=None):
    """A rib along the chord at u, just above the skin; line: a glowing line along its top (neon)."""
    pts = [tuple(point(top, u, -1 + i / 3) + Vector((0, 0.02, 0))) for i in range(7)]
    tube(pts, r, c, segs=6)
    if line:
        tube([(p[0], p[1] + r * 0.9, p[2]) for p in pts], r * 0.55, line, segs=6)


def dome(u, v, cy, R, H, rim_lat):
    r = min(1.0, math.sqrt(u * u + v * v))
    lat, lon = math.radians(90 - r * (88 - rim_lat)), math.atan2(v, u)
    return (R * math.cos(lat) * math.cos(lon), cy + H * math.sin(lat), R * math.cos(lat) * math.sin(lon))


# ---------------------------------------------------------------- the nine shapes

def wing(deco):
    neon = deco == "neon"
    skin, trim = ("b", "a") if neon else ("a", "b")

    def top(u, v):
        au, x = abs(u), u * 2.25
        zl, zt = -0.8 + 0.45 * au * au, 0.8 - 0.2 * au
        t = (v + 1) / 2
        return (x, Y + 0.18 * au * au + 0.14 * math.sin(math.pi * t) * (1 - 0.5 * au), zl + (zt - zl) * t)

    sheet(lambda u, v: top(u * 2 - 1, v * 2 - 1), 28, 10, skin, thick=0.06)
    edge(top, -1, 0.075, trim)
    edge(top, 1, 0.035, (trim, 0.85))
    for u in (-0.75, -0.4, 0.4, 0.75):
        rib(top, u, 0.025, (skin, 1.0 if neon else 0.72), tint_glow("a") if neon else None)
    rib(top, 0, 0.05, trim)
    for side in (-1, 1):
        p0, p1 = point(top, side, -1), point(top, side, 1)
        sheet(lambda u, v, p0=p0, p1=p1: (p0.x, p0.y + (p1.y - p0.y) * u + 0.3 * v * math.sin(math.pi * (0.3 + 0.7 * u)), p0.z + (p1.z - p0.z) * u), 8, 4, trim, thick=0.03)
    T(0, Y - 0.1, 0.05).blob(0.24, 0.14, 0.78, trim)
    T(0, Y - 0.12, -0.68).blob(0.12, 0.08, 0.12, (trim, 0.7))
    struts(0.7, Y - 0.05, "DARK")
    return top


def umbrella(deco):
    cy, R, H = Y - 0.4, 1.75, 0.85
    ornate = deco in ("ornate", "crown")
    rib_col = "GOLD" if ornate else "METAL"
    m = Matrix.Translation((0, cy, 0)) @ Matrix.Diagonal((R, H, R, 1))
    for i in range(8):
        panel(m, 6, 90, 8, 4, i * 45, (i + 1) * 45, "a" if i % 2 == 0 else "b")
    rim_y, rim_r = cy + H * math.sin(math.radians(6)), R * math.cos(math.radians(6))
    for i in range(8):
        ang, mid = math.radians(i * 45), math.radians(i * 45 + 22.5)
        ribpts = []
        for k in range(6):
            lat = math.radians(90 - k * 16.8)
            r, y = R * 1.015 * math.cos(lat), cy + H * 1.015 * math.sin(lat)
            ribpts.append((math.cos(ang) * r, y, math.sin(ang) * r))
        tube(ribpts, [0.02, 0.022, 0.022, 0.022, 0.022, 0.02], rib_col, segs=6)
        T(math.cos(ang) * (rim_r + 0.06), rim_y - 0.04, math.sin(ang) * (rim_r + 0.06)).blob(0.06, 0.06, 0.06, "GOLD" if ornate else "b", segs=10)
        T(math.cos(mid) * rim_r * 0.97, rim_y - 0.07, math.sin(mid) * rim_r * 0.97).rotY(-math.degrees(mid) - 90).blob(0.62, 0.06, 0.1, "a" if i % 2 == 0 else "b")
        tube([(0, cy - 0.55, 0), (math.cos(ang) * R * 0.55, cy + H * 0.8, math.sin(ang) * R * 0.55)], 0.014, "METAL", segs=5)
    T(0, cy + H, 0).cone(0.07, 0.3, "GOLD" if ornate else "b")
    T(0, cy + H + 0.32, 0).blob(0.08, 0.08, 0.08, "RED" if ornate else "b", segs=10)
    T(0, cy - 0.55, 0).blob(0.09, 0.12, 0.09, "DARK", segs=10)
    later_tube([(0, cy + H, 0), (0, cy - 0.55, 0)], [(0, BAR_Y + 0.35, BAR_Z * 0.5)], [0.045, 0.045, 0.04], "METAL")
    for side in (-1, 1):
        tube([(0, BAR_Y + 0.35, BAR_Z * 0.5), (side * 0.45, BAR_Y + 0.12, BAR_Z), (side * BAR_X, BAR_Y, BAR_Z)], [0.055, 0.05, 0.045], "GOLD" if ornate else "WOOD")
    return lambda u, v: dome(u, v, cy, R, H, 6)


def parachute(deco):
    cy, R, H, rim_lat = Y + 0.5, 2.45, 1.05, 14
    rainbow = ["#D7263D", "#F28C28", "#F2E23A", "#2EB84B", "#2F6BD8", "#7A4FC9"]
    m = Matrix.Translation((0, cy, 0)) @ Matrix.Diagonal((R, H, R, 1))
    for i in range(12):
        panel(m, rim_lat, 84, 8, 3, i * 30, (i + 1) * 30, rainbow[i % 6] if deco == "rainbow" else ("a" if i % 2 == 0 else "b"))
    seam = ("b", 0.7) if deco != "rainbow" else "#B4B4B4"
    rim_y, rim_r = cy + H * math.sin(math.radians(rim_lat)), R * math.cos(math.radians(rim_lat))
    for i in range(12):
        ang = math.radians(i * 30)
        pts = []
        for k in range(6):
            lat = math.radians(84 - k * (84 - rim_lat) / 5)
            pts.append((R * 1.012 * math.cos(lat) * math.cos(ang), cy + H * 1.012 * math.sin(lat), R * 1.012 * math.cos(lat) * math.sin(ang)))
        tube(pts, 0.016, seam, segs=5)
        lx, lz = math.cos(ang) * rim_r, math.sin(ang) * rim_r
        side = 1 if lx >= 0 else -1
        later_tube([(lx, rim_y - 0.02, lz)], [(side * 0.85, BAR_Y + 0.9, BAR_Z * 0.5)], [0.012, 0.012], "LINE", segs=4)
    T(0, rim_y - 0.01, 0).torus(rim_r, 0.045, ("b", 0.85) if deco != "rainbow" else "WHITE", segs=40)
    vent_y, vent_r = cy + H * math.sin(math.radians(84)), R * math.cos(math.radians(84))
    T(0, vent_y, 0).torus(vent_r, 0.035, seam, segs=16)
    T(0, vent_y - 0.05, 0).lens(vent_r, vent_r, 0.02, "DARK")
    for side in (-1, 1):
        tube([(side * 0.85, BAR_Y + 0.9, BAR_Z * 0.5), (side * BAR_X, BAR_Y, BAR_Z)], [0.05, 0.04], "DARK")
        T(side * 0.85, BAR_Y + 0.9, BAR_Z * 0.5).torus(0.07, 0.02, "METAL", segs=12)
    return lambda u, v: dome(u, v, cy, R, H, rim_lat)


def kite(deco):
    n, l, r, t = Vector((0, Y + 0.05, -1.8)), Vector((-1.4, Y + 0.2, -0.3)), Vector((1.4, Y + 0.2, -0.3)), Vector((0, Y - 0.05, 1.8))

    def square(fu, fv):
        ab, dc = n.lerp(r, fu), l.lerp(t, fu)
        p = ab.lerp(dc, fv)
        return (p.x, p.y + 0.14 * math.sin(math.pi * fu) * math.sin(math.pi * fv), p.z)

    def sail(u, v):
        return square(max(0, min(1, (1 + u + v) / 2)), max(0, min(1, (1 - u + v) / 2)))

    sheet(square, 14, 14, "a", thick=0.03)
    sheet(lambda u, v: tuple(Vector(square(0.275 + 0.45 * u, 0.275 + 0.45 * v)) + Vector((0, 0.022, 0))), 6, 6, "b")
    corners = [n, r, t, l, n]
    for i in range(4):
        p0, p1 = corners[i], corners[i + 1]
        tube([tuple(p0), tuple((p0 + p1) / 2 + Vector((0, 0.06, 0))), tuple(p1)], 0.035, "b", segs=6)
    tube([(n.x, n.y + 0.03, n.z), (0, Y + 0.2, 0), (t.x, t.y + 0.03, t.z)], 0.03, "WOOD", segs=6)
    tube([(l.x, l.y + 0.03, l.z), (0, Y + 0.24, -0.3), (r.x, r.y + 0.03, r.z)], 0.03, "WOOD", segs=6)
    tow = (0, Y - 0.85, -0.1)
    for p in (n, Vector((0, Y + 0.15, -0.3)), t):
        tube([tuple(p), tow], 0.01, "LINE", segs=4)
    T(*tow).torus(0.07, 0.02, "METAL", segs=12)
    for side in (-1, 1):
        later_tube([tow], [(side * BAR_X * 0.8, BAR_Y, BAR_Z)], [0.012, 0.012], "LINE", segs=4)
    tail = [(math.sin(i * 1.1) * 0.22, t.y - 0.06 * i, t.z + 0.5 * i) for i in range(8)]
    tube(tail, [0.02] * 7 + [0.015], "b", segs=6)
    for k in range(1, 8, 2):
        p = tail[k]
        c = "a" if k % 4 == 1 else "b"
        T(p[0] - 0.12, p[1], p[2]).rotY(20).blob(0.14, 0.04, 0.08, c, segs=10)
        T(p[0] + 0.12, p[1], p[2]).rotY(-20).blob(0.14, 0.04, 0.08, c, segs=10)
        T(*p).blob(0.05, 0.05, 0.05, (c, 0.8), segs=8)
    return sail


def delta(deco):
    paper, cracks = deco == "paper", deco == "cracks"
    skin, trim = ("b", "a") if cracks else ("a", "b")

    def top(u, v):
        t = (v + 1) / 2
        z, w = -2.1 + 3.1 * t, 1.95 * t
        return (u * w, Y - 0.06 + 0.18 * abs(u) * t, z)

    sheet(lambda u, v: top(u * 2 - 1, v * 2 - 1), 14, 12, skin, thick=0.04)
    sheet(lambda u, v: (0, Y - 0.06 - 0.42 * u * math.sin(math.pi * min(1, v * 1.1)), -1.6 + 2.4 * v), 3, 10, (skin, 0.75), thick=0.03)
    for side in (-1, 1):
        p0, p1, p2 = point(top, 0, -1), point(top, side, 0), point(top, side, 1)
        tube([tuple(p0), tuple(p1), tuple(p2)], [0.02, 0.025 if paper else 0.04, 0.045], trim, segs=6)
        if not paper:
            fx = side * 0.75
            sheet(lambda u, v, fx=fx: (fx, Y + 0.02 + 0.5 * u * v, 0.1 + 0.85 * v), 4, 6, trim, thick=0.03)
    if paper:
        for u in (-0.55, 0.55):
            rib(top, u, 0.012, (skin, 0.82))
    struts(0.6, Y - 0.05, ("a", 0.7) if paper else "DARK")
    return top


def bat(deco):
    bone, skin = ("a", 0.55), "a"
    eyes = glow("#FF2A2A") if deco == "fangs" else glow("#7FD8FF") if deco == "frost" else col("b")
    for side in (-1, 1):
        shoulder, wrist = Vector((side * 0.25, Y + 0.02, -0.3)), Vector((side * 1.3, Y + 0.25, -0.4))
        tips = [Vector((side * 2.5, Y + 0.1, -0.55)), Vector((side * 2.6, Y - 0.02, 0.3)), Vector((side * 1.95, Y - 0.06, 1.0)), Vector((side * 0.3, Y, 0.65))]
        tube([tuple(shoulder), tuple(wrist)], [0.06, 0.05], bone)
        for k in range(3):
            tube([tuple(wrist), tuple((wrist + tips[k]) / 2 + Vector((0, 0.06, 0))), tuple(tips[k])], [0.04, 0.03, 0.012], bone)
        T(wrist.x, wrist.y + 0.05, wrist.z).cone(0.05, 0.2, bone, n=8)
        for k in range(3):
            p0, p1 = tips[k], tips[k + 1]
            w = Vector((side * 0.35, Y + 0.02, -0.2)) if k == 2 else wrist

            def membrane(u, v, p0=p0, p1=p1, w=w):
                e = p0.lerp(p1, v)
                e = e.lerp(w, 0.32 * math.sin(math.pi * v))
                o = w.lerp(e, u)
                return (o.x, o.y - 0.14 * u * math.sin(math.pi * v), o.z)
            sheet(membrane, 7, 7, skin, thick=0.025)
        t0 = tips[0]
        sheet(lambda u, v, t0=t0, shoulder=shoulder, wrist=wrist: tuple(shoulder.lerp(wrist, u).lerp(wrist.lerp(t0, u), v * 0.5)), 4, 3, (skin, 0.9), thick=0.02)
        T(side * 0.13, Y + 0.2, -0.82).rotZ(-side * 12).cone(0.09, 0.26, skin, n=8)
        T(side * 0.09, Y + 0.08, -1.0).blob(0.045, 0.045, 0.03, eyes, segs=8)
    T(0, Y, -0.05).blob(0.3, 0.22, 0.62, (skin, 0.85))
    T(0, Y + 0.04, -0.78).blob(0.2, 0.18, 0.22, (skin, 0.9))
    T(0, Y, -0.98).blob(0.07, 0.05, 0.05, (skin, 0.7), segs=8)
    struts(0.35, Y - 0.1, "DARK")
    return lambda u, v: (u * 2.2, Y - 0.04 * abs(u), v * 0.8 + 0.1)


def balloon(deco):
    cy, RX, RY, lat0 = Y + 1.45, 1.45, 1.7, -58
    m = Matrix.Translation((0, cy, 0)) @ Matrix.Diagonal((RX, RY, RX, 1))
    for i in range(12):
        panel(m, lat0, 86, 12, 3, i * 30, (i + 1) * 30, "a" if i % 2 == 0 else "b")
    for i in range(12):
        ang = math.radians(i * 30)
        tape = []
        for k in range(8):
            lat = math.radians(86 - k * (86 - lat0) / 7)
            tape.append((RX * 1.012 * math.cos(lat) * math.cos(ang), cy + RY * 1.012 * math.sin(lat), RX * 1.012 * math.cos(lat) * math.sin(ang)))
        tube(tape, 0.016, ("b", 0.7), segs=5)
    top_y, top_r = cy + RY * math.sin(math.radians(86)), RX * math.cos(math.radians(86))
    T(0, top_y, 0).lens(top_r * 1.4, top_r * 1.4, 0.06, ("b", 0.8))
    mouth_y, mouth_r = cy + RY * math.sin(math.radians(lat0)), RX * math.cos(math.radians(lat0))
    T().lathe([mouth_r * 0.98, mouth_r * 0.8], [mouth_y, mouth_y - 0.42], ("b", 0.9))
    T(0, mouth_y - 0.42, 0).torus(mouth_r * 0.8, 0.03, ("b", 0.7), segs=20)
    by = mouth_y - 0.62
    T().lathe([0, 0.2, 0.22, 0.16, 0], [by - 0.12, by - 0.12, by, by + 0.08, by + 0.08], "METAL", segs=14)
    T(0, by + 0.25, 0).blob(0.12, 0.24, 0.12, glow("#FF8A1F"), segs=10)
    T(0, by + 0.2, 0).blob(0.07, 0.15, 0.07, glow("#FFE45A"), segs=8)
    for side in (-1, 1):
        for fz in (-1, 1):
            rx, rz = side * mouth_r * 0.78, fz * mouth_r * 0.55
            later_tube([(rx, mouth_y - 0.4, rz)], [(side * BAR_X, BAR_Y, BAR_Z)], [0.018, 0.018], "#C9A66B", segs=4)
            tube([(rx, mouth_y - 0.4, rz), (side * 0.22, by, 0)], 0.012, "#C9A66B", segs=4)

    def surf(u, v):
        lon, lat = math.atan2(v, u), math.radians(70 - 120 * min(1, math.sqrt(u * u + v * v)))
        return (RX * math.cos(lat) * math.cos(lon), cy + RY * math.sin(lat), RX * math.cos(lat) * math.sin(lon))
    return surf


def leaf(deco):
    maple = deco == "maple"

    def top(u, v):
        t = (v + 1) / 2
        w = math.sin(math.pi * t ** 0.8) ** 0.85
        if maple:
            w *= 1 + 0.32 * max(0, math.sin(t * math.pi * 5 - 0.6)) - 0.12
        return (u * w, Y + 0.22 * u * u - 0.32 * (1 - t) ** 2 + 0.05 * math.sin(math.pi * t), -2.1 + 4.0 * t)

    sheet(lambda u, v: top(u * 2 - 1, v * 2 - 1), 12, 34 if maple else 18, "a", thick=0.04)
    rib(top, 0, 0.04, "b")
    for k in range(1, 6):
        v0 = -0.75 + k * 0.27
        for side in (-1, 1):
            p0, p1, p2 = point(top, 0, v0), point(top, side * 0.5, v0 - 0.12), point(top, side * 0.92, v0 - 0.2)
            tube([(p0.x, p0.y + 0.02, p0.z), (p1.x, p1.y + 0.02, p1.z), (p2.x, p2.y + 0.015, p2.z)], [0.022, 0.016, 0.008], "b", segs=5)
    tube([(0, Y + 0.05, 1.9), (0, Y - 0.02, 2.35), (0.12, Y - 0.14, 2.6), (0.08, Y - 0.3, 2.68)], [0.06, 0.05, 0.04, 0.03], ("b", 0.9))
    struts(0.55, Y - 0.05, "DARK")
    return top


def dominus(deco):
    def top(u, v):
        au, t = abs(u), (v + 1) / 2
        zl, zt = -0.5 + 0.3 * au, 0.3 + 0.05 * au
        return (u * 2.35, Y + 0.42 * au * math.sqrt(au) + 0.07 * math.sin(math.pi * t), zl + (zt - zl) * t)

    sheet(lambda u, v: top(u * 2 - 1, v * 2 - 1), 24, 6, "a", thick=0.06)
    edge(top, -1, 0.08, "b")
    tip = {"frigidus": "WHITE", "infernus": "#FF8A1F", "vespertilio": "#9B4DFF"}.get(deco)
    for side in (-1, 1):
        for i in range(12):
            au = 0.1 + 0.9 * i / 11
            feather(point(top, side * au, 0.8), side * (6 + 40 * au * au), 0.5 + 0.6 * au * au, 0.13 + 0.03 * au, "a" if i % 2 == 0 else ("a", 0.9), tip, ("b", 0.85))
        for i in range(11):
            au = 0.08 + 0.84 * i / 10
            p = point(top, side * au, 0.3) + Vector((0, 0.02, 0))
            feather(p, side * (4 + 30 * au * au), 0.4 + 0.12 * au, 0.14, "b" if i % 2 == 0 else ("b", 0.9), None, None)
    T(0, Y - 0.08, 0).blob(0.2, 0.1, 0.5, ("a", 0.85))
    hy, hz = Y + 0.3, -0.42
    fz = hz - 0.39
    T(0, hy, hz).blob(0.4, 0.46, 0.4, "a")
    T(0, hy - 0.18, hz + 0.3).blob(0.34, 0.3, 0.42, "a")
    T(0, hy - 0.02, fz).rotX(90).lens(0.25, 0.31, 0.04, "#07070B")
    T(0, hy - 0.02, fz - 0.005).rotX(90).scale(1, 1, 1.24).torus(0.27, 0.035, "b", segs=24)
    crest = [(0, hy + 0.05 + math.sin(math.radians(25 + 130 * i / 6)) * 0.47, hz - math.cos(math.radians(25 + 130 * i / 6)) * 0.42) for i in range(7)]
    tube(crest, [0.045, 0.05, 0.055, 0.055, 0.05, 0.045, 0.04], "b")
    horns = deco in ("infernus", "rex")
    for side in (-1, 1):
        x = side * 0.36
        if horns:
            tube([(x, hy + 0.1, hz), (x + side * 0.2, hy + 0.28, hz + 0.12), (x + side * 0.24, hy + 0.6, hz + 0.28), (x + side * 0.14, hy + 0.85, hz + 0.4)], [0.1, 0.08, 0.045, 0.006], "b")
        else:
            for k in range(3):
                T(x, hy + 0.08 + 0.12 * k, hz + 0.12).rotY(-side * 35).rotZ(side * (12 + 20 * k)).move(side * 0.3, 0, 0).rotX(90).lens(0.36 - 0.05 * k, 0.07, 0.03, "b")
    eyes = {"infernus": "#FF8A1F", "vespertilio": "#B46BFF"}.get(deco)
    if eyes:
        for side in (-1, 1):
            T(side * 0.09, hy + 0.03, fz - 0.04).rotX(90).lens(0.055, 0.028, 0.02, glow(eyes))
    if deco == "empyreus":
        T(0, hy + 0.72, hz + 0.08).rotX(-15).torus(0.28, 0.03, "GOLD", segs=24)
        for side in (-1, 1):
            sparkle(top, side * 0.5, -0.3, 0.13, "#FFF6C8")
            sparkle(top, side * 0.82, -0.1, 0.1, "#FFF6C8")
    elif deco == "frigidus":
        for i in range(10):
            u = -0.9 + i * 0.2
            if abs(u) < 0.25:
                continue
            p = point(top, u, -0.95)
            T(p.x, p.y - 0.06, p.z).rotX(180).cone(0.04, 0.14 + 0.1 * (i % 3), "#E6F7FF", n=8)
        for side in (-1, 1):
            for under in (False, True):
                for k in range(3):
                    spot(top, side * 0.6, -0.4, 0.03, under).rotY(k * 60).lens(0.18, 0.025, 0.02, "WHITE")
    elif deco == "aureus":
        for i in range(6):
            p = point(top, (-1 if i < 3 else 1) * (0.3 + 0.25 * (i % 3)), -0.7)
            T(p.x, p.y + 0.05, p.z).blob(0.06, 0.05, 0.06, "RED" if i % 2 == 0 else "BLUE", segs=10)
        for i in range(4):
            sparkle(top, -0.75 + i * 0.5, -0.2, 0.12, "WHITE")
    elif deco == "infernus":
        for i in range(8):
            u = (-1 if i % 2 == 0 else 1) * (0.3 + 0.18 * (i // 2))
            p = point(top, u, 1)
            T(p.x + u * 0.25, p.y - 0.1 - 0.05 * (i % 3), p.z + 0.75 + 0.12 * (i % 3)).blob(0.045, 0.045, 0.045, glow("#FFD23A" if i % 3 == 0 else "#FF6A1F"), segs=8)
    elif deco == "rex":
        T(0, hy + 0.36, fz + 0.1).blob(0.07, 0.08, 0.06, "b", segs=10)
        T(0, hy + 0.36, fz + 0.1).rotX(70).torus(0.085, 0.022, "GOLD", segs=16)
        for side in (-1, 1):
            star(top, side * 0.6, -0.3, 0.16, "STAR")
    elif deco == "vespertilio":
        for side in (-1, 1):
            stripe(top, side * 0.15, -0.6, side * 0.85, 0.2, 0.025, "b")
            stripe(top, side * 0.35, -0.65, side * 0.55, 0.45, 0.02, "b")
    struts(0.55, Y - 0.04, ("b", 0.8))
    return top


def feather(root, yaw, ln, w, c, tip, shaft):
    r = math.radians(yaw)
    dx, dz = math.sin(r), math.cos(r)
    centre = (root.x + dx * ln * 0.5, root.y - 0.015, root.z + dz * ln * 0.5)
    T(*centre).rotY(yaw).rotX(7).lens(w, ln * 0.5, 0.022, c)
    if tip:
        T(*centre).rotY(yaw).rotX(7).move(0, 0.006, ln * 0.36).lens(w * 0.75, ln * 0.15, 0.024, tip)
    if shaft:
        t = T(*centre).rotY(yaw).rotX(7)
        tube([t.pt(0, 0.02, -ln * 0.5), t.pt(0, 0.02, ln * 0.42)], [0.014, 0.008], shaft, segs=4)


# ---------------------------------------------------------------- decorations

def disc(top, u, v, r, c):
    for under in (False, True):
        spot(top, u, v, 0.02, under).lens(r, r, 0.02, c)


def sparkle(top, u, v, r, c):
    for under in (False, True):
        spot(top, u, v, 0.03, under).lens(r, r * 0.22, 0.02, c)
        spot(top, u, v, 0.03, under).rotY(90).lens(r, r * 0.22, 0.02, c)
        spot(top, u, v, 0.035, under).lens(r * 0.25, r * 0.25, 0.025, "WHITE")


def star(top, u, v, r, c):
    for under in (False, True):
        for k in range(5):
            spot(top, u, v, 0.03, under).rotY(k * 72).move(0, 0, -r * 0.5).lens(r * 0.2, r * 0.55, 0.02, c)
        spot(top, u, v, 0.032, under).lens(r * 0.4, r * 0.4, 0.022, c)


def stripe(top, u0, v0, u1, v1, r, c):
    for under in (False, True):
        pts = []
        for i in range(9):
            t = i / 8
            p = point(top, u0 + (u1 - u0) * t, v0 + (v1 - v0) * t)
            lift = -0.1 if under else max(0.03, r * 0.35 + 0.008)
            pts.append((p.x, p.y + lift, p.z))
        part = tube(pts, r, c, segs=6)
        # flattened onto the canopy (a third of its height)
        mid = sum((Vector(p) for p in pts), Vector()) / len(pts)
        part.scale(1, 0.35, 1, about=tuple(mid))


def skull(top, u, v, d):
    for under in (False, True):
        for side in (-1, 1):
            spot(top, u, v, 0.025, under).rotY(side * 45).lens(0.7, 0.07, 0.02, "WHITE")
            for end in (-1, 1):
                spot(top, u, v, 0.03, under).rotY(side * 45).move(end * 0.68, 0, 0).lens(0.1, 0.1, 0.025, "WHITE")
        spot(top, u, v - 0.06 * d, 0.05, under).lens(0.36, 0.32, 0.03, "WHITE")
        spot(top, u, v + 0.17 * d, 0.05, under).lens(0.22, 0.14, 0.03, "WHITE")
        for side in (-1, 1):
            spot(top, u + side * 0.06, v - 0.06 * d, 0.075, under).lens(0.09, 0.1, 0.02, "BLACK")
        spot(top, u, v + 0.05 * d, 0.075, under).lens(0.04, 0.05, 0.02, "BLACK")


def crown(top):
    p = point(top, 0, 0.05)
    y = p.y + 0.05
    T(p.x, y, p.z).lathe([0.3, 0.34, 0.34, 0.3], [0, 0.02, 0.16, 0.18], "GOLD", segs=20)
    for k in range(5):
        ang = math.radians(k * 72)
        x, z = p.x + math.cos(ang) * 0.3, p.z + math.sin(ang) * 0.3
        T(x, y + 0.16, z).cone(0.07, 0.2, "GOLD", n=8)
        T(x, y + 0.38, z).lens(0.035, 0.035, 0.035, "WHITE")
        T(p.x + math.cos(ang + 0.63) * 0.335, y + 0.09, p.z + math.sin(ang + 0.63) * 0.335).blob(0.04, 0.04, 0.03, "RED" if k % 2 == 0 else "BLUE", segs=8)
    for k in range(3):
        spot(top, -0.18 + k * 0.18, 0.05, 0.02, True).lens(0.07, 0.18, 0.02, "GOLD")
    spot(top, 0, 0.18, 0.025, True).lens(0.3, 0.06, 0.02, "GOLD")


def dragon(top):
    nose = point(top, 0, -1)
    T(nose.x, nose.y + 0.12, nose.z - 0.15).blob(0.26, 0.2, 0.32, "a")
    T(nose.x, nose.y + 0.06, nose.z - 0.48).blob(0.16, 0.12, 0.22, ("a", 0.9))
    for side in (-1, 1):
        tube([(side * 0.14, nose.y + 0.25, nose.z - 0.05), (side * 0.24, nose.y + 0.45, nose.z + 0.15), (side * 0.22, nose.y + 0.55, nose.z + 0.4)], [0.05, 0.03, 0.006], "b")
        T(side * 0.13, nose.y + 0.2, nose.z - 0.36).blob(0.05, 0.04, 0.04, glow("#FFD23A"), segs=8)
        T(side * 0.06, nose.y + 0.08, nose.z - 0.69).lens(0.025, 0.02, 0.02, "BLACK")
    for k in range(6):
        p = point(top, 0, -0.6 + k * 0.3)
        T(p.x, p.y + 0.03, p.z).rotX(-25).cone(0.07, 0.2 - 0.02 * k, "b", n=6)


def engine(x, y):
    T(x, y, 0.1).rotX(90).lathe([0, 0.15, 0.18, 0.18, 0.14, 0], [-0.62, -0.62, -0.5, 0.45, 0.6, 0.6], "METAL", segs=14)
    T(x, y, -0.53).rotX(90).lathe([0, 0.13, 0], [-0.02, 0, 0.02], "#16161A", segs=14)
    T(x, y, 0.72).blob(0.11, 0.11, 0.14, glow("#FF8A1F"), segs=10)
    T(x, y, 0.82).blob(0.06, 0.06, 0.1, glow("#FFE45A"), segs=8)
    T(x, y + 0.08, 0.3).blob(0.04, 0.16, 0.25, "b", segs=8)


def decorate(top, deco):
    if deco == "chevron":
        for side in (-1, 1):
            stripe(top, 0, -0.3, side * 0.7, 0.45, 0.07, "b")
            stripe(top, 0, 0.15, side * 0.55, 0.8, 0.045, "b")
    elif deco == "stripes":
        for u in (-0.62, -0.52, 0.52, 0.62):
            stripe(top, u, -0.9, u, 0.9, 0.04, "b")
    elif deco == "dots":
        for p in ((-0.8, -0.2), (-0.55, 0.4), (-0.3, -0.5), (0, 0.3), (0.3, -0.5), (0.55, 0.4), (0.8, -0.2), (-0.15, -0.05), (0.15, -0.05)):
            disc(top, p[0], p[1], 0.12, "b")
    elif deco == "stars":
        at = ((-0.7, 0), (-0.35, -0.4), (0, 0.15), (0.35, -0.4), (0.7, 0), (-0.45, 0.5), (0.45, 0.5))
        for i, p in enumerate(at):
            star(top, p[0], p[1], 0.22 if i % 2 == 0 else 0.15, "STAR")
    elif deco == "flames":
        for i in range(7):
            u = -0.75 + i * 0.25
            p = point(top, u, 1)
            ln = 0.45 + 0.2 * abs(math.sin(i * 1.7))
            tube([(p.x, p.y - 0.03, p.z), (p.x + 0.06, p.y - 0.06, p.z + ln * 0.5), (p.x - 0.03, p.y - 0.08, p.z + ln)], [0.07, 0.05, 0.006], glow("#FF6A1F"))
            tube([(p.x, p.y - 0.03, p.z + 0.02), (p.x + 0.03, p.y - 0.05, p.z + ln * 0.45)], [0.035, 0.006], glow("#FFD23A"))
        for side in (-1, 1):
            for k in range(3):
                stripe(top, side * (0.2 + 0.22 * k), 0.55, side * (0.3 + 0.25 * k), -0.35 + 0.12 * k, 0.06, "YELLOW" if k == 1 else "b")
    elif deco == "bolt":
        for side in (-1, 1):
            zig = ((side * 0.25, -0.55), (side * 0.45, -0.2), (side * 0.32, -0.15), (side * 0.55, 0.35))
            for k in range(3):
                stripe(top, zig[k][0], zig[k][1], zig[k + 1][0], zig[k + 1][1], 0.06, glow("#FFE45A"))
    elif deco == "skull":
        skull(top, 0, 0.55, 1)
        skull(top, 0, -0.55, -1)
    elif deco == "crown":
        crown(top)
    elif deco == "ornate":
        for i in range(8):
            ang = math.radians(i * 45)
            x, z, y = math.cos(ang) * 1.8, math.sin(ang) * 1.8, Y - 0.4 + 0.85 * math.sin(math.radians(6)) - 0.08
            tube([(x, y, z), (x, y - 0.28, z)], 0.012, "GOLD", segs=4)
            T(x, y - 0.32, z).rotX(180).cone(0.05, 0.12, "RED", n=8)
        for i in range(6):
            sparkle(top, math.cos(i * 1.05) * 0.55, math.sin(i * 1.05) * 0.55, 0.16, "#FFF6C8")
    elif deco == "sprinkles":
        cols = ["#FF4FD8", "#29E3FF", "#FFD21F", "#7BF24D", "#FF8A1F", "#F4F6FA"]
        for i in range(18):
            r, ang = 0.15 + 0.75 * ((i * 0.618) % 1), i * 2.4
            for under in (False, True):
                spot(top, math.cos(ang) * r, math.sin(ang) * r, 0.03, under).rotY(i * 47).lens(0.11, 0.035, 0.025, cols[i % len(cols)])
    elif deco == "frost":
        for p in ((-0.6, 0), (0, -0.3), (0.6, 0), (-0.25, 0.45), (0.3, 0.45)):
            for under in (False, True):
                for k in range(3):
                    spot(top, p[0], p[1], 0.03, under).rotY(k * 60).lens(0.22, 0.03, 0.02, "WHITE")
        for i in range(9):
            p = point(top, -0.85 + i * 0.21, -0.95)
            T(p.x, p.y - 0.02, p.z).rotX(180).cone(0.045, 0.16 + 0.12 * (i % 3), "#CFF0FF", n=8)
    elif deco == "bee":
        for v in (-0.45, -0.05, 0.35):
            stripe(top, -0.95 + abs(v) * 0.6, v, 0.95 - abs(v) * 0.6, v, 0.09, "b")
        nose = point(top, 0, -1)
        for side in (-1, 1):
            tube([tuple(nose), (side * 0.15, nose.y + 0.3, nose.z - 0.2), (side * 0.3, nose.y + 0.45, nose.z - 0.15)], [0.025, 0.02, 0.02], "b", segs=5)
            T(side * 0.3, nose.y + 0.47, nose.z - 0.15).blob(0.06, 0.06, 0.06, "b", segs=8)
            for under in (False, True):
                spot(top, side * 0.45, 0.1, 0.04, under).rotY(side * 20).lens(0.32, 0.16, 0.02, "#EAF6FF")
    elif deco == "dragon":
        dragon(top)
    elif deco == "jet":
        for side in (-1, 1):
            engine(side * 0.78, Y - 0.24)
    elif deco == "camo":
        cols = [("a", 0.65), ("a", 1.0), "#5C4A2E"]
        for i in range(14):
            r, ang = 0.2 + 0.7 * ((i * 0.618) % 1), i * 2.4
            for under in (False, True):
                spot(top, math.cos(ang) * r, math.sin(ang) * r, 0.025, under).rotY(i * 33).lens(0.32, 0.17, 0.018, cols[i % 3])
    elif deco == "clouds":
        for i in range(5):
            ang = math.radians(i * 72 + 20)
            x, z, y = math.cos(ang) * 1.55, math.sin(ang) * 1.55, Y + 0.6 + 0.25 * (i % 2)
            for k in range(4):
                T(x + (k - 1.5) * 0.2 * -math.sin(ang), y + (k % 2) * 0.1, z + (k - 1.5) * 0.2 * math.cos(ang)).blob(0.22, 0.17, 0.22, "WHITE", segs=10)
    elif deco == "party":
        cols = ["#FFD21F", "#29E3FF", "#7BF24D", "#F4F6FA", "#FF8A1F"]
        for i in range(16):
            lon, lat = i * 2.4, math.radians(-30 + 90 * ((i * 0.618) % 1))
            x, y, z = 1.47 * math.cos(lat) * math.cos(lon), Y + 1.45 + 1.72 * math.sin(lat), 1.47 * math.cos(lat) * math.sin(lon)
            T(x, y, z).align((x / 1.45, (y - Y - 1.45) / 1.7, z / 1.45)).lens(0.13, 0.13, 0.02, cols[i % len(cols)])
        for i in range(4):
            ang = math.radians(45 + i * 90)
            x, z, y = math.cos(ang) * 0.7, math.sin(ang) * 0.7, Y - 0.4
            tube([(x, y, z), (x * 1.1 + 0.1, y - 0.35, z * 1.1), (x * 1.2 - 0.1, y - 0.7, z * 1.2)], [0.03, 0.025, 0.015], cols[i], segs=5)
    elif deco == "banner":
        T(0, Y + 1.45, 0).torus(1.47, 0.07, "GOLD", segs=40)
    elif deco == "neon":
        for side in (-1, 1):
            stripe(top, side * 0.12, -0.7, side * 0.9, 0.2, 0.035, tint_glow("a"))
        stripe(top, -0.9, 0.55, 0.9, 0.55, 0.03, tint_glow("a"))
    elif deco == "fangs":
        for side in (-1, 1):
            T(side * 0.07, Y - 0.1, -0.95).rotX(180).cone(0.035, 0.14, "WHITE", n=8)
        sheet(lambda u, v: ((u * 2 - 1) * 0.42, Y + 0.05 + 0.4 * v, -0.55 + 0.15 * v - 0.2 * (u * 2 - 1) ** 2), 6, 3, ("a", 1.0), thick=0.03)
    elif deco == "waves":
        for i in range(12):
            ang = i * math.pi / 6
            for under in (False, True):
                spot(top, math.cos(ang) * 0.72, math.sin(ang) * 0.72, 0.025, under).rotY(-math.degrees(ang)).lens(0.08, 0.3, 0.02, "b")
    elif deco == "face":
        for under in (False, True):
            for side in (-1, 1):
                spot(top, side * 0.18, -0.15, 0.03, under).lens(0.11, 0.16, 0.025, "BLACK")
            spot(top, 0, 0.35, 0.03, under).lens(0.09, 0.11, 0.025, "BLACK")
        for i in range(3):
            p = point(top, -0.4 + 0.4 * i, 1)
            tube([(p.x, p.y - 0.03, p.z), (p.x + 0.12, p.y - 0.1, p.z + 0.45), (p.x - 0.1, p.y - 0.18, p.z + 0.9)], [0.09, 0.06, 0.006], ("a", 0.95))
    elif deco == "cracks":
        lines = ((-0.6, -0.2, -0.25, 0.1), (-0.25, 0.1, -0.4, 0.6), (0.1, -0.6, 0.35, 0), (0.35, 0, 0.6, 0.55), (-0.05, 0.3, 0.2, 0.8))
        for k, l in enumerate(lines):
            stripe(top, *l, 0.045, glow("#FFD23A") if k % 2 == 0 else "a")
    elif deco == "panels":
        for v in (-0.3, 0.3):
            stripe(top, -0.9, v, 0.9, v, 0.015, "b")
        for u in (-0.5, 0.5):
            stripe(top, u, -0.6, u, 0.95, 0.015, "b")
        for side in (-1, 1):
            disc(top, side * 0.3, -0.55, 0.08, "#101012")
    elif deco == "facets":
        for p in ((-0.55, 0.4), (0, -0.2), (0.55, 0.4), (-0.25, 0.6), (0.25, 0.6)):
            for under in (False, True):
                spot(top, p[0], p[1], 0.02, under).lathe([0, 0.14, 0], [0, 0.06, 0.22], "b", segs=6)
        for i in range(4):
            sparkle(top, -0.7 + i * 0.47, 0.1, 0.12, "WHITE")
    elif deco == "galaxy":
        for i in range(10):
            u, v = -0.85 + 1.7 * ((i * 0.618) % 1), -0.8 + 1.6 * ((i * 0.382 + 0.2) % 1)
            if i % 3 == 0:
                star(top, u, v, 0.14, "STAR")
            else:
                sparkle(top, u, v, 0.1, "b" if i % 2 == 0 else "WHITE")
        p = point(top, 0, 0.1)
        T(p.x, p.y + 0.3, p.z).blob(0.2, 0.2, 0.2, "b")
        T(p.x, p.y + 0.3, p.z).rotZ(18).torus(0.33, 0.025, "STAR", segs=24)
    elif deco == "legendary":
        for side in (-1, 1):
            stripe(top, 0, -0.35, side * 0.75, 0.4, 0.08, "b")
            stripe(top, 0, 0.05, side * 0.6, 0.75, 0.05, "WHITE")
        for i in range(6):
            sparkle(top, -0.8 + i * 0.32, -0.55 if i % 2 == 0 else 0.65, 0.13, "#FFF6C8")
        star(top, 0, -0.55, 0.22, "WHITE")
    elif deco == "drips":
        for i in range(8):
            p = point(top, (-0.6 if i % 2 == 0 else 0.6) * (0.5 + 0.5 * (i % 3) / 2), -0.7 + i * 0.2)
            ln = 0.15 + 0.1 * (i % 3)
            c = "b" if i % 2 == 0 else "#9BF24D"
            tube([(p.x, p.y - 0.04, p.z), (p.x, p.y - 0.04 - ln, p.z)], [0.02, 0.035], c, segs=6)
            T(p.x, p.y - 0.08 - ln, p.z).blob(0.055, 0.075, 0.055, c, segs=8)
        for i in range(5):
            disc(top, -0.5 + i * 0.25, 0.3 if i % 2 == 0 else -0.35, 0.08, "#9BF24D")


SHAPES = {"wing": wing, "umbrella": umbrella, "parachute": parachute, "kite": kite, "delta": delta, "bat": bat, "balloon": balloon, "leaf": leaf,
          "dominus": dominus}


def build(shape, deco):
    LATER.clear()
    top = SHAPES.get(shape, wing)(deco)
    decorate(top, deco)
    # Everything so far is the canopy: scale it up over the frame.
    import bpy
    pivot = Vector((0, BAR_Y, BAR_Z))
    m = Matrix.Translation(pivot) @ Matrix.Scale(SCALE, 4) @ Matrix.Translation(-pivot)
    for o in bpy.context.scene.objects:
        if o.type == "MESH":
            o.matrix_world = C @ m @ CI @ o.matrix_world
    for job in LATER:
        job()
    handles()


# ---------------------------------------------------------------- the gliders sheet

def _sheet():
    import json
    import os
    here = os.path.dirname(os.path.abspath(__file__))
    path = os.path.join(here, "..", "..", "..", "design", "gliders.json")
    return json.load(open(path, encoding="utf-8"))["rows"]


UNLOCK = {"kills": "Get {n} eliminations", "wins": "Win {n} Victory Royales", "range": "Score {n} in one shooting range round",
          "bullseyes": "Hit {n} shooting range bullseyes", "obby": "Finish the obby in the lobby", "victory": "Win a Victory Royale"}


def unlock_text(row):
    u = row.get("unlock", "default")
    return None if u == "default" else UNLOCK.get(u, u).format(n=row.get("need", 0))


def entry(row):
    return (row["name"], lambda: build(row["shape"], row.get("deco", "none")),
            {"ao": 0.6, "folder": "", "unlock": unlock_text(row), "lod": 0.35, "scale": 1.0,
             "variants": {"": {1: row["color"], 2: row["color2"]}}, "views": {"Front": 180, "Left": 270, "Back": 0, "Right": 90},
             "avatar": "gliding"})


ITEMS = {"glider_" + r["id"].removeprefix("glider_"): entry(r) for r in _sheet()}
