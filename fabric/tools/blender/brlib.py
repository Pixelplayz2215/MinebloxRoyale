"""
Mineblox Royale's Blender kit (v1.0.1, creator: "remade using Blender"): build a model from bevelled, rounded parts,
bake soft shadows into it, export it for the game, and render it.

Coordinates are Minecraft's (x right, y up, z towards the viewer); the kit turns them into Blender's (X, -Z, Y) and
back, so the model scripts read like the Java that draws them. Every part has a material from mat(): its colour, and
optionally a tint channel (1 = the item's main colour: a gun's rarity, a car's paint, a hat's first colour; 2 = its
second colour) or glow (drawn full bright: headlights, lenses).

The game's mesh file (.brm, gzip): "BRM1", int vertex count, then per vertex (three per triangle) x, y, z (float),
normal x, y, z (signed bytes), colour r, g, b (bytes: the material colour with the baked shadow), flags (byte: bits
0-1 the tint channel, bit 2 glow), little-endian. Read by gg.minebloxroyale.client.mesh.Mesh.
"""
import gzip
import json
import math
import os
import random
import struct

import bmesh
import bpy
from mathutils import Matrix, Vector
from mathutils.bvhtree import BVHTree


def P(x, y, z):
    """Minecraft coordinates -> Blender's."""
    return Vector((x, -z, y))


def hexrgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) / 255 for i in (0, 2, 4))


def to_linear(c):
    return tuple(v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4 for v in c)


# ---------------------------------------------------------------- scene and materials

def reset():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    s = bpy.context.scene
    s.render.engine = "BLENDER_EEVEE"
    s.render.film_transparent = True
    s.view_settings.view_transform = "Standard"
    s.render.image_settings.file_format = "PNG"
    s.render.image_settings.color_mode = "RGBA"
    return s


_mats = {}


def mat(name, color, ch=0, glow=False, rough=0.55, metal=0.0):
    """A material: colour '#rrggbb' (sRGB). ch: tint channel (the colour is then the shade under the tint: keep it
    light grey or white). glow: full bright in the game, emissive in renders."""
    key = name
    if key in _mats:
        return _mats[key]
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    m["br_col"] = color
    m["br_ch"] = ch
    m["br_glow"] = 1 if glow else 0
    b = m.node_tree.nodes.get("Principled BSDF")
    b.inputs["Roughness"].default_value = rough
    b.inputs["Metallic"].default_value = metal
    _mats[key] = m
    _set_preview(m, None)
    return m


def _set_preview(m, tints):
    """Render colours: a tinted material shows the given tint (sRGB hex) times its own shade."""
    c = hexrgb(m["br_col"])
    ch = m["br_ch"]
    if ch and tints and ch in tints:
        t = hexrgb(tints[ch])
        c = tuple(a * b for a, b in zip(c, t))
    lin = to_linear(c)
    b = m.node_tree.nodes.get("Principled BSDF")
    b.inputs["Base Color"].default_value = (*lin, 1)
    if m["br_glow"]:
        b.inputs["Emission Color"].default_value = (*lin, 1)
        b.inputs["Emission Strength"].default_value = 2.0
    else:
        b.inputs["Emission Strength"].default_value = 0.0


def tint_previews(tints):
    for m in _mats.values():
        _set_preview(m, tints)


# ---------------------------------------------------------------- parts

class Part:
    def __init__(self, obj):
        self.obj = obj

    def rot(self, axis, deg, pivot):
        """Turn about an axis ('x', 'y', 'z', Minecraft's) through pivot (x, y, z)."""
        ax = {"x": Vector((1, 0, 0)), "y": Vector((0, 0, 1)), "z": Vector((0, -1, 0))}[axis]
        p = P(*pivot)
        m = Matrix.Translation(p) @ Matrix.Rotation(math.radians(deg), 4, ax) @ Matrix.Translation(-p)
        self.obj.matrix_world = m @ self.obj.matrix_world
        return self

    def move(self, x, y, z):
        self.obj.matrix_world = Matrix.Translation(P(x, y, z)) @ self.obj.matrix_world
        return self

    def scale(self, sx, sy, sz, about=(0, 0, 0)):
        p = P(*about)
        s = Matrix.Diagonal((sx, sz, sy, 1))
        self.obj.matrix_world = Matrix.Translation(p) @ s @ Matrix.Translation(-p) @ self.obj.matrix_world
        return self


def _finish(bm, name, material, bevel=0.0, seg=2, smooth=True, angle=35, solidify=0.0):
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    obj = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(obj)
    me.materials.append(material)
    for f in me.polygons:
        f.use_smooth = smooth
    if solidify > 0:
        s = obj.modifiers.new("solid", "SOLIDIFY")
        s.thickness = solidify
        s.offset = 0
    if bevel > 0:
        b = obj.modifiers.new("bevel", "BEVEL")
        b.width = bevel
        b.segments = seg
        b.limit_method = "ANGLE"
        b.angle_limit = math.radians(angle)
    if smooth:
        wn = obj.modifiers.new("weighted", "WEIGHTED_NORMAL")
        wn.keep_sharp = True
    return Part(obj)


def _normals_last(obj):
    names = [m.name for m in obj.modifiers]
    if "weighted" in names and names[-1] != "weighted":
        obj.modifiers.move(names.index("weighted"), len(names) - 1)


def box(x0, y0, z0, x1, y1, z1, material, bevel=0.0, seg=2):
    """An axis-aligned box from corner to corner (Minecraft coordinates), with rounded edges if bevel > 0."""
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1.0)
    cs = [P(x, y, z) for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)]
    lo = Vector(map(min, *cs))
    hi = Vector(map(max, *cs))
    c = (lo + hi) / 2
    s = hi - lo
    for v in bm.verts:
        v.co = Vector((v.co.x * s.x, v.co.y * s.y, v.co.z * s.z)) + c
    return _finish(bm, "box", material, bevel=min(bevel, min(s) * 0.45) if bevel else 0, seg=seg, smooth=bool(bevel))


def _axis_matrix(axis):
    # Blender's primitives run along Z; turn to the Minecraft axis asked for.
    if axis == "y":
        return Matrix.Identity(4)
    if axis == "x":
        return Matrix.Rotation(math.radians(90), 4, "Y")
    return Matrix.Rotation(math.radians(90), 4, "X")


def cyl(cx, cy, cz, r, length, axis, material, verts=16, r2=None, bevel=0.0, seg=2):
    """A cylinder (or a cone, r2 at the far end) centred at (cx, cy, cz) along a Minecraft axis."""
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=True, cap_tris=False, segments=verts, radius1=r, radius2=r if r2 is None else r2, depth=length)
    bmesh.ops.transform(bm, matrix=_axis_matrix(axis), verts=bm.verts)
    bmesh.ops.translate(bm, vec=P(cx, cy, cz), verts=bm.verts)
    return _finish(bm, "cyl", material, bevel=bevel, seg=seg, smooth=True)


def sphere(cx, cy, cz, rx, ry, rz, material, segs=16, rings=10):
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=segs, v_segments=rings, radius=1.0)
    for v in bm.verts:
        v.co = Vector((v.co.x * rx, v.co.y * rz, v.co.z * ry)) + P(cx, cy, cz)
    return _finish(bm, "sphere", material, smooth=True)


def torus(cx, cy, cz, R, r, axis, material, segs=24, rsegs=10, arc=360.0):
    """A ring of radius R (tube radius r) round a Minecraft axis; arc < 360 for part of it."""
    bm = bmesh.new()
    rings = []
    n = segs if arc >= 360 else segs + 1
    for i in range(n):
        a = math.radians(arc * i / segs)
        ring = []
        for j in range(rsegs):
            b = 2 * math.pi * j / rsegs
            x = (R + r * math.cos(b)) * math.cos(a)
            y = (R + r * math.cos(b)) * math.sin(a)
            z = r * math.sin(b)
            ring.append(bm.verts.new((x, y, z)))
        rings.append(ring)
    for i in range(n - 1 if arc < 360 else n):
        a, b = rings[i], rings[(i + 1) % n]
        for j in range(rsegs):
            bm.faces.new((a[j], a[(j + 1) % rsegs], b[(j + 1) % rsegs], b[j]))
    if arc < 360:
        bm.faces.new(list(reversed(rings[0])))
        bm.faces.new(rings[-1])
    bmesh.ops.transform(bm, matrix=_axis_matrix(axis), verts=bm.verts)
    bmesh.ops.translate(bm, vec=P(cx, cy, cz), verts=bm.verts)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    return _finish(bm, "torus", material, smooth=True)


def lathe(profile, cx, cy, cz, material, segs=24, axis="y", sx=1.0, sz=1.0):
    """Turns a profile [(r, h), ...] (bottom to top) round a Minecraft axis through (cx, cy, cz)."""
    bm = bmesh.new()
    rings = []
    for r, h in profile:
        if r < 1e-5:
            rings.append([bm.verts.new((0, 0, h))])
            continue
        rings.append([bm.verts.new((r * sx * math.cos(2 * math.pi * i / segs), r * sz * math.sin(2 * math.pi * i / segs), h)) for i in range(segs)])
    for a, b in zip(rings, rings[1:]):
        if len(a) == 1 and len(b) == 1:
            continue
        if len(a) == 1:
            for i in range(segs):
                bm.faces.new((a[0], b[i], b[(i + 1) % segs]))
        elif len(b) == 1:
            for i in range(segs):
                bm.faces.new((a[i], b[0], a[(i + 1) % segs]))
        else:
            for i in range(segs):
                bm.faces.new((a[i], a[(i + 1) % segs], b[(i + 1) % segs], b[i]))
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-6)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bmesh.ops.transform(bm, matrix=_axis_matrix(axis), verts=bm.verts)
    bmesh.ops.translate(bm, vec=P(cx, cy, cz), verts=bm.verts)
    return _finish(bm, "lathe", material, smooth=True)


def prism(points, z0, z1, material, bevel=0.0, seg=2, plane="xy"):
    """A side outline [(a, b), ...] (counter-clockwise) pushed out from z0 to z1. plane 'xy': the outline is in
    Minecraft x/y and the depth runs along z (a gun's side profile); 'zy': outline in z/y, depth along x; 'xz': outline
    in x/z (a top view), depth along y."""
    bm = bmesh.new()
    lo = [bm.verts.new((0, 0, 0)) for _ in points]
    hi = [bm.verts.new((0, 0, 0)) for _ in points]
    for i, (a, b) in enumerate(points):
        if plane == "xy":
            lo[i].co, hi[i].co = P(a, b, z0), P(a, b, z1)
        elif plane == "zy":
            lo[i].co, hi[i].co = P(z0, b, a), P(z1, b, a)
        else:
            lo[i].co, hi[i].co = P(a, z0, b), P(a, z1, b)
    bm.faces.new(lo)
    bm.faces.new(list(reversed(hi)))
    n = len(points)
    for i in range(n):
        bm.faces.new((lo[i], lo[(i + 1) % n], hi[(i + 1) % n], hi[i]))
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    return _finish(bm, "prism", material, bevel=bevel, seg=seg, smooth=bool(bevel))


def tube(points, r, material, segs=10, caps=True):
    """A round tube through points [(x, y, z), ...] (Minecraft coordinates), radius r (a number or one per point)."""
    pts = [P(*p) for p in points]
    rs = r if isinstance(r, (list, tuple)) else [r] * len(pts)
    bm = bmesh.new()
    rings = []
    prev_n = None
    for i, p in enumerate(pts):
        d = (pts[min(i + 1, len(pts) - 1)] - pts[max(i - 1, 0)]).normalized()
        up = Vector((0, 0, 1)) if abs(d.z) < 0.9 else Vector((1, 0, 0))
        n1 = d.cross(up).normalized() if prev_n is None else (prev_n - d * prev_n.dot(d)).normalized()
        prev_n = n1
        n2 = d.cross(n1)
        rings.append([bm.verts.new(p + (n1 * math.cos(2 * math.pi * j / segs) + n2 * math.sin(2 * math.pi * j / segs)) * rs[i]) for j in range(segs)])
    for a, b in zip(rings, rings[1:]):
        for j in range(segs):
            bm.faces.new((a[j], a[(j + 1) % segs], b[(j + 1) % segs], b[j]))
    if caps:
        bm.faces.new(list(reversed(rings[0])))
        bm.faces.new(rings[-1])
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    return _finish(bm, "tube", material, smooth=True)


def surface(fn, nu, nv, material, closed_u=False, thickness=0.0):
    """A sheet from fn(u, v) -> (x, y, z) Minecraft coordinates over u, v in 0..1 (canopies, capes, hair locks).
    thickness > 0 gives it a back; a bare sheet is drawn on both sides."""
    bm = bmesh.new()
    cols = nu if closed_u else nu + 1
    grid = [[bm.verts.new(P(*fn(i / nu, j / nv))) for j in range(nv + 1)] for i in range(cols)]
    for i in range(nu):
        a, b = grid[i], grid[(i + 1) % cols]
        for j in range(nv):
            bm.faces.new((a[j], b[j], b[j + 1], a[j + 1]))
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-6)
    part = _finish(bm, "surface", material, smooth=True, solidify=thickness)
    if thickness <= 0:
        part.obj["br_twosided"] = 1
    return part


def subdivide(part, levels=1):
    m = part.obj.modifiers.new("subsurf", "SUBSURF")
    m.levels = levels
    m.render_levels = levels
    _normals_last(part.obj)
    return part


def mirror_x(part, about=0.0):
    """A copy mirrored left/right about x = about (Minecraft x)."""
    obj = part.obj.copy()
    obj.data = part.obj.data.copy()
    bpy.context.scene.collection.objects.link(obj)
    m = Matrix.Translation((about, 0, 0)) @ Matrix.Scale(-1, 4, (1, 0, 0)) @ Matrix.Translation((-about, 0, 0))
    obj.matrix_world = m @ obj.matrix_world
    return Part(obj)


def mirror_z(part, about=0.0):
    """A copy mirrored front/back about z = about (Minecraft z; Blender -Y)."""
    obj = part.obj.copy()
    obj.data = part.obj.data.copy()
    bpy.context.scene.collection.objects.link(obj)
    m = Matrix.Translation((0, -about, 0)) @ Matrix.Scale(-1, 4, (0, 1, 0)) @ Matrix.Translation((0, about, 0))
    obj.matrix_world = m @ obj.matrix_world
    return Part(obj)


# ---------------------------------------------------------------- triangles, shadows and the game's file

def triangles():
    """Every triangle of every object in the scene, world space: [(corners[3], normals[3], material, twosided)]."""
    dg = bpy.context.evaluated_depsgraph_get()
    out = []
    for obj in bpy.context.scene.objects:
        if obj.type != "MESH" or obj.hide_render:
            continue
        eo = obj.evaluated_get(dg)
        me = eo.to_mesh()
        me.calc_loop_triangles()
        mw = obj.matrix_world
        nm = mw.to_3x3().inverted_safe().transposed()
        flip = mw.to_3x3().determinant() < 0
        cn = me.corner_normals
        mats = [s.material for s in eo.material_slots]
        two = bool(obj.get("br_twosided"))
        for lt in me.loop_triangles:
            loops = list(lt.loops)
            if flip:
                loops = [loops[0], loops[2], loops[1]]
            cs = [mw @ me.vertices[me.loops[li].vertex_index].co for li in loops]
            ns = [(nm @ cn[li].vector).normalized() for li in loops]
            m = mats[lt.material_index] if lt.material_index < len(mats) else mats[0]
            out.append((cs, ns, m, two))
        eo.to_mesh_clear()
    return out


def bake_ao(tris, distance, rays=24, seed=3):
    """Soft shadow per corner (1 open .. 0 shut in), from rays into the half-space over it."""
    verts, polys = [], []
    for cs, _, _, _ in tris:
        i = len(verts)
        verts += [tuple(c) for c in cs]
        polys.append((i, i + 1, i + 2))
    bvh = BVHTree.FromPolygons(verts, polys, epsilon=0.0)
    rnd = random.Random(seed)
    dirs = []
    for _ in range(rays):  # cosine-weighted hemisphere samples about +Z
        u, v = rnd.random(), rnd.random()
        r, a = math.sqrt(u), 2 * math.pi * v
        dirs.append(Vector((r * math.cos(a), r * math.sin(a), math.sqrt(max(0.0, 1 - u)))))
    cache = {}
    out = []
    for cs, ns, _, _ in tris:
        vals = []
        for c, n in zip(cs, ns):
            key = (round(c.x, 4), round(c.y, 4), round(c.z, 4), round(n.x, 2), round(n.y, 2), round(n.z, 2))
            if key not in cache:
                rot = Vector((0, 0, 1)).rotation_difference(n).to_matrix()
                o = c + n * distance * 0.02
                hit = 0
                for d in dirs:
                    loc, _, _, _ = bvh.ray_cast(o, rot @ d, distance)
                    if loc is not None:
                        hit += 1
                cache[key] = 1.0 - hit / len(dirs)
            vals.append(cache[key])
        out.append(vals)
    return out


def export(path, scale=1.0, origin=(0, 0, 0), ao_distance=1.0, ao_strength=0.5):
    """Writes the scene for the game: Minecraft coordinates, minus origin, times scale (an item model's 0..16 units
    -> blocks: scale 1/16). Returns the triangle count."""
    tris = triangles()
    ao = bake_ao(tris, ao_distance)
    ox, oy, oz = origin
    buf = bytearray()
    count = 0
    lo = [1e9] * 3
    hi = [-1e9] * 3
    for (cs, ns, m, two), shade in zip(tris, ao):
        col = hexrgb(m["br_col"])
        flags = (m["br_ch"] & 3) | (4 if m["br_glow"] else 0)
        sides = [(cs, ns, shade)]
        if two:
            sides.append(([cs[0], cs[2], cs[1]], [-ns[0], -ns[2], -ns[1]], [shade[0], shade[2], shade[1]]))
        for corners, normals, sh in sides:
            for c, n, a in zip(corners, normals, sh):
                x, y, z = (c.x - ox) * scale, (c.z - oy) * scale, (-c.y - oz) * scale
                k = 1.0 if m["br_glow"] else (1 - ao_strength) + ao_strength * a
                r, g, b = (max(0, min(255, round(v * 255 * k))) for v in col)
                nx, ny, nz = n.x, n.z, -n.y
                buf += struct.pack("<fffbbbBBBB", x, y, z, round(nx * 127), round(ny * 127), round(nz * 127), r, g, b, flags)
                for i, v in enumerate((x, y, z)):
                    lo[i] = min(lo[i], v)
                    hi[i] = max(hi[i], v)
            count += 1
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with gzip.open(path, "wb", compresslevel=9) as f:
        f.write(b"BRM1" + struct.pack("<i", count * 3) + bytes(buf))
    return count, lo, hi


# ---------------------------------------------------------------- renders

def bounds():
    lo = Vector((1e9, 1e9, 1e9))
    hi = Vector((-1e9, -1e9, -1e9))
    for obj in bpy.context.scene.objects:
        if obj.type != "MESH":
            continue
        for c in obj.bound_box:
            w = obj.matrix_world @ Vector(c)
            lo = Vector(map(min, lo, w))
            hi = Vector(map(max, hi, w))
    return lo, hi


def _lights():
    s = bpy.context.scene
    for name, energy, rot in (("key", 3.2, (50, 0, -35)), ("fill", 1.2, (60, 0, 140)), ("rim", 1.6, (-20, 0, 200))):
        ld = bpy.data.lights.new(name, "SUN")
        ld.energy = energy
        ld.angle = math.radians(8)
        lo = bpy.data.objects.new(name, ld)
        lo.rotation_euler = tuple(math.radians(v) for v in rot)
        s.collection.objects.link(lo)
    w = bpy.data.worlds.new("world")
    w.use_nodes = True
    w.node_tree.nodes["Background"].inputs[0].default_value = (0.62, 0.65, 0.7, 1)
    w.node_tree.nodes["Background"].inputs[1].default_value = 0.7
    s.world = w


def render_views(out_dir, stem, views, size=640, elevation=12.0, margin=1.12):
    """Orthographic renders of the scene from compass directions (degrees round the vertical axis, 0 = looking from
    +z, Minecraft south, towards the model; 90 = from +x). views: {label: yaw}. Returns {label: path}."""
    s = bpy.context.scene
    if not any(o.type == "LIGHT" for o in s.objects):
        _lights()
    s.render.resolution_x = s.render.resolution_y = size
    lo, hi = bounds()
    c = (lo + hi) / 2
    radius = (hi - lo).length / 2
    cam_data = bpy.data.cameras.new("cam")
    cam_data.type = "ORTHO"
    cam = bpy.data.objects.new("cam", cam_data)
    s.collection.objects.link(cam)
    s.camera = cam
    corners = [Vector((x, y, z)) for x in (lo.x, hi.x) for y in (lo.y, hi.y) for z in (lo.z, hi.z)]
    paths = {}
    for label, yaw in views.items():
        a, e = math.radians(yaw), math.radians(elevation)
        # Minecraft direction (sin yaw, 0, cos yaw) -> Blender (sin, -cos, 0), lifted by the elevation.
        d = Vector((math.sin(a) * math.cos(e), -math.cos(a) * math.cos(e), math.sin(e)))
        rot = (-d).to_track_quat("-Z", "Y")
        cam.rotation_euler = rot.to_euler()
        # Fit this view: the model's box as the camera sees it, centred.
        inv = rot.to_matrix().inverted()
        pts = [inv @ (p - c) for p in corners]
        mx = (max(p.x for p in pts) + min(p.x for p in pts)) / 2
        my = (max(p.y for p in pts) + min(p.y for p in pts)) / 2
        cam_data.ortho_scale = max(max(p.x for p in pts) - min(p.x for p in pts), max(p.y for p in pts) - min(p.y for p in pts)) * margin
        cam.location = c + d * radius * 4 + rot.to_matrix() @ Vector((mx, my, 0))
        cam_data.clip_end = radius * 10
        p = os.path.join(out_dir, f"{stem}_{label}.png")
        s.render.filepath = p
        bpy.ops.render.render(write_still=True)
        paths[label] = p
    bpy.data.objects.remove(cam)
    return paths


def render_icon(path, yaw, pitch, size=256, mask=False):
    """The inventory picture's view (or, mask=True, white where tint channel 1 shows and black elsewhere)."""
    s = bpy.context.scene
    if not any(o.type == "LIGHT" for o in s.objects):
        _lights()
    saved = {}
    if mask:
        for m in _mats.values():
            b = m.node_tree.nodes.get("Principled BSDF")
            saved[m.name] = (tuple(b.inputs["Base Color"].default_value), tuple(b.inputs["Emission Color"].default_value), b.inputs["Emission Strength"].default_value)
            v = 1.0 if m["br_ch"] == 1 else 0.0
            b.inputs["Base Color"].default_value = (0, 0, 0, 1)
            b.inputs["Emission Color"].default_value = (v, v, v, 1)
            b.inputs["Emission Strength"].default_value = 1.0
    s.render.resolution_x = s.render.resolution_y = size
    lo, hi = bounds()
    c = (lo + hi) / 2
    radius = (hi - lo).length / 2
    cam_data = bpy.data.cameras.new("icon")
    cam_data.type = "ORTHO"
    cam = bpy.data.objects.new("icon", cam_data)
    s.collection.objects.link(cam)
    s.camera = cam
    a, e = math.radians(yaw), math.radians(pitch)
    d = Vector((math.sin(a) * math.cos(e), -math.cos(a) * math.cos(e), math.sin(e)))
    cam.location = c + d * radius * 4
    cam.rotation_euler = (-d).to_track_quat("-Z", "Y").to_euler()
    cam_data.clip_end = radius * 10
    # Fit the model's projected corners.
    rot = cam.rotation_euler.to_matrix().inverted()
    pts = [rot @ (Vector((x, y, z)) - c) for x in (lo.x, hi.x) for y in (lo.y, hi.y) for z in (lo.z, hi.z)]
    w = max(max(abs(p.x) for p in pts), max(abs(p.y) for p in pts)) * 2
    cam_data.ortho_scale = w * 1.02
    s.render.filepath = path
    bpy.ops.render.render(write_still=True)
    bpy.data.objects.remove(cam)
    if mask:
        for m in _mats.values():
            b = m.node_tree.nodes.get("Principled BSDF")
            bc, ec, es = saved[m.name]
            b.inputs["Base Color"].default_value = bc
            b.inputs["Emission Color"].default_value = ec
            b.inputs["Emission Strength"].default_value = es


def write_manifest(path, entries):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(entries, f, indent=1)
