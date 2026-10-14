"""
The Java Smooth (gg.minebloxroyale.client.avatar.Smooth) in Blender: the same calls (at, move, rotX/Y/Z, scale, align,
color, then lathe, sphere, blob, lens, panel, cone, ellipsoid, torus, tube, surface) with the same conventions
(Minecraft-style space, y up; angles from +x towards +z; profiles listed bottom to top), so the cosmetics and back bling
port line for line and keep their exact sizes. Colours: an int 0xRRGGBB, 'a' / 'b' (the item's own colours, tint
channels 1 / 2), ('a', 0.8) a shade of one, or a material.
"""
import math

import bmesh
import bpy
from mathutils import Matrix, Quaternion, Vector

import brlib
from brlib import mat

C = Matrix(((1, 0, 0, 0), (0, 0, -1, 0), (0, 1, 0, 0), (0, 0, 0, 1)))  # Minecraft -> Blender
CI = C.inverted()


def material(c, glow=False):
    if isinstance(c, bpy.types.Material):
        return c
    if isinstance(c, tuple):
        ch, k = c
        g = max(0, min(255, round(255 * k)))
        return mat(f"{ch}_{g}{'_glow' if glow else ''}", f"#{g:02X}{g:02X}{g:02X}", ch=1 if ch == "a" else 2, glow=glow, rough=0.5)
    if c in ("a", "b"):
        return material((c, 1.0), glow)
    if isinstance(c, int):
        h = f"#{c & 0xFFFFFF:06X}"
        return mat(("g" if glow else "c") + h[1:], h, glow=glow, rough=0.5)
    return mat(("g" if glow else "c") + c.lstrip("#"), c, glow=glow, rough=0.5)


def shade(c, k):
    """A shade of a colour: of the item's own ('a', 'b'), or of a fixed one."""
    if c in ("a", "b"):
        return (c, k)
    if isinstance(c, tuple):
        return (c[0], c[1] * k)
    v = c & 0xFFFFFF
    r, g, b = (min(255, round((v >> s & 255) * k)) for s in (16, 8, 0))
    return r << 16 | g << 8 | b


def _mesh(verts, faces, m, smooth=True, two_sided=False):
    bm = bmesh.new()
    vs = [bm.verts.new(v) for v in verts]
    for f in faces:
        if len(set(f)) >= 3:
            try:
                bm.faces.new([vs[i] for i in f])
            except ValueError:
                pass
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-6)
    part = brlib._finish(bm, "s", m, smooth=smooth)
    if two_sided:
        part.obj["br_twosided"] = 1
    return part


class S:
    """One Smooth: a current placement and colour; shapes are made at the placement."""

    def __init__(self):
        self.local = Matrix.Identity(4)
        self.col = 0xFFFFFF
        self.glow = False
        self.cap_min = 0.005
        self.detail = 1.0

    def at(self, x, y, z):
        self.local = Matrix.Translation((x, y, z))
        return self

    def move(self, x, y, z):
        self.local = self.local @ Matrix.Translation((x, y, z))
        return self

    def rotX(self, d):
        self.local = self.local @ Matrix.Rotation(math.radians(d), 4, "X")
        return self

    def rotY(self, d):
        self.local = self.local @ Matrix.Rotation(math.radians(d), 4, "Y")
        return self

    def rotZ(self, d):
        self.local = self.local @ Matrix.Rotation(math.radians(d), 4, "Z")
        return self

    def scale(self, x, y, z):
        self.local = self.local @ Matrix.Diagonal((x, y, z, 1))
        return self

    def align(self, nx, ny, nz):
        q = Vector((0, 1, 0)).rotation_difference(Vector((nx, ny, nz)).normalized())
        self.local = self.local @ q.to_matrix().to_4x4()
        return self

    def color(self, c, glow=False):
        self.col = c
        self.glow = glow
        return self

    def caps(self, m):
        self.cap_min = m
        return self

    # ---- shapes ----

    def _out(self, pts):
        """Placement (Minecraft) then into Blender."""
        return [tuple(C @ (self.local @ Vector(p))) for p in pts]

    def lathe(self, r, y, seg, a0=0.0, a1=360.0):
        full = abs(a1 - a0) >= 360
        cols = seg if full else seg + 1
        verts, faces = [], []
        m = len(r)
        for i in range(cols):
            a = math.radians(a0 + (a1 - a0) * i / seg)
            for j in range(m):
                verts.append((r[j] * math.cos(a), y[j], r[j] * math.sin(a)))
        for i in range(seg):
            i1 = (i + 1) % cols if full else i + 1
            for j in range(m - 1):
                faces.append((i * m + j, i1 * m + j, i1 * m + j + 1, i * m + j + 1))
        closed = r[0] < 1e-6 and r[-1] < 1e-6 and full
        return _mesh(self._out(verts), faces, material(self.col, self.glow), two_sided=not closed)

    def sphere(self, lat0, lat1, seg, a0=0.0, a1=360.0):
        rings = max(3, round((lat1 - lat0) / 180 * seg))
        r = [math.cos(math.radians(lat0 + (lat1 - lat0) * i / rings)) for i in range(rings + 1)]
        y = [math.sin(math.radians(lat0 + (lat1 - lat0) * i / rings)) for i in range(rings + 1)]
        return self.lathe(r, y, seg, a0, a1)

    def blob(self, rx, ry, rz):
        self.scale(rx, ry, rz)
        return self.sphere(-90, 90, 12)

    def lens(self, rx, rz, h):
        self.scale(rx, 1, rz)
        return self.lathe([0, 1, 0], [-h, 0, h], 14)

    def panel(self, lat0, lat1, rings, slices, a0, a1):
        rings = max(2, rings)
        r = [math.cos(math.radians(lat0 + (lat1 - lat0) * i / rings)) for i in range(rings + 1)]
        y = [math.sin(math.radians(lat0 + (lat1 - lat0) * i / rings)) for i in range(rings + 1)]
        return self.lathe(r, y, slices, a0, a1)

    def cone(self, r, h, seg):
        return self.lathe([0, r, 0], [0, 0, h], seg)

    def ellipsoid(self, rx, ry, rz):
        self.scale(rx, ry, rz)
        return self.sphere(-90, 90, 24)

    def torus(self, R, t, a0, a1):
        seg_u = max(6, round(abs(a1 - a0) / 360 * 32))
        seg_v = 10
        full = abs(a1 - a0) >= 360
        cols = seg_u if full else seg_u + 1
        verts, faces = [], []
        for i in range(cols):
            u = math.radians(a0 + (a1 - a0) * i / seg_u)
            for j in range(seg_v):
                v = 2 * math.pi * j / seg_v
                verts.append(((R + t * math.cos(v)) * math.cos(u), t * math.sin(v), (R + t * math.cos(v)) * math.sin(u)))
        for i in range(seg_u):
            i1 = (i + 1) % cols if full else i + 1
            for j in range(seg_v):
                faces.append((i * seg_v + j, i1 * seg_v + j, i1 * seg_v + (j + 1) % seg_v, i * seg_v + (j + 1) % seg_v))
        part = _mesh(self._out(verts), faces, material(self.col, self.glow), two_sided=not full)
        return part

    def tube(self, pts, radii, caps=True, sides=None):
        if not isinstance(radii, (list, tuple)):
            radii = [radii] * len(pts)
        thickest = max(radii)
        sides = sides or (6 if thickest < 0.03 else 10)
        n = len(pts)
        P_ = [Vector(p) for p in pts]
        t, nn, bb = [], [], []
        for i in range(n):
            d = (P_[min(n - 1, i + 1)] - P_[max(0, i - 1)]).normalized()
            ref = (Vector((0, 1, 0)) if abs(d.y) < 0.9 else Vector((1, 0, 0))) if i == 0 else nn[i - 1]
            nv = (ref - d * ref.dot(d)).normalized()
            t.append(d)
            nn.append(nv)
            bb.append(d.cross(nv).normalized())
        verts, faces = [], []
        for i in range(n):
            for j in range(sides):
                v = 2 * math.pi * j / sides
                dirn = nn[i] * math.cos(v) + bb[i] * math.sin(v)
                verts.append(tuple(P_[i] + dirn * max(radii[i], 1e-4)))
        for i in range(n - 1):
            for j in range(sides):
                faces.append((i * sides + j, (i + 1) * sides + j, (i + 1) * sides + (j + 1) % sides, i * sides + (j + 1) % sides))
        part = _mesh(self._out(verts), faces, material(self.col, self.glow), two_sided=True)
        if caps:
            keep = self.local.copy()
            for end in (0, n - 1):
                if radii[end] > self.cap_min:
                    self.local = keep @ Matrix.Translation(P_[end]) @ Matrix.Scale(radii[end], 4)
                    self.sphere(-90, 90, 10)
            self.local = keep
        return part

    def surface(self, fn, nu, nv):
        verts, faces = [], []
        for i in range(nu + 1):
            for j in range(nv + 1):
                verts.append(tuple(fn(i / nu, j / nv)))
        for i in range(nu):
            for j in range(nv):
                faces.append((i * (nv + 1) + j, (i + 1) * (nv + 1) + j, (i + 1) * (nv + 1) + j + 1, i * (nv + 1) + j + 1))
        return _mesh(self._out(verts), faces, material(self.col, self.glow), two_sided=True)
