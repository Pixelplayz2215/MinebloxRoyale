"""
Lighter copies of the game's models for far away (2.0.0-test3, creator: "we need to reduce the lag"): every model gets
<id>-low.brm (a dash, which no model's id has, so it can't clash with one: face_shades_low is a model), and <id>_far.brm
where it has none (the items), cut down in Blender (Decimate, collapse) from the game's own file: the -low copy to LOW
of its triangles, at most a number for its kind. The renderer picks the copy by how big the model looks (Mesh.lod).

- Each tint channel and the glowing parts are cut down on their own, so they keep their edges; within one, each
  material (told apart by its hue: baked shadows only darken a colour) keeps its own colour, and the corners where two
  materials meet are kept where they are (a stripe stays a stripe).
- Thin sheets (a side with its other side a hair behind it, brlib.export) are cut down by one side alone and given the
  other again after, so the two can't cross.
- Each new corner takes its normal from the old corner nearby facing most nearly its triangle's way (shaded as before:
  smooth where it was smooth, edges kept sharp), and its colour, baked shadows and all, from the brightest old corner
  of its material nearby facing its way (a cut-down surface sits partly down in the old one's shaded creases: their
  dark corners would darken it); a vertex's corners of one material facing within SMOOTH of each other share their
  mean, so the new edges don't show.
- Parts too small to cut (under MIN_PART triangles) are kept whole. Each cut is checked: if the new shape strays from
  the old (or leaves part of it out) by more than TOLERANCE of the model's size anywhere much, that part is cut again
  less hard, up to keeping FAR of it, else it's kept whole.
- Models under SMALL triangles get no lighter copies: they're cheap to draw, and cutting them breaks them (a shield
  potion's label crumbles into its bottle).

blender --background --factory-startup --python lod.py -- <meshes dir> [ids...]
"""
import math
import os
import sys

import bmesh
import bpy
from mathutils import Vector
from mathutils.bvhtree import BVHTree
from mathutils.kdtree import KDTree

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import brm  # noqa: E402

# The -low copy keeps LOW of the triangles, at most a number by the model's id (first prefix that fits); the _far copy
# (made here for the items only) keeps FAR of them.
CAP = (("hair_", 2500), ("hat_", 1500), ("face_", 800), ("back_", 1500), ("shoes_", 600), ("shirt_", 300), ("pants_", 300),
       ("glider_", 1500), ("car_", 1500), ("chest_", 500), ("battle_bus", 4000), ("reboot_van", 1200), ("", 400))
LOW, FAR = 0.1, 0.35
SHEET = 0.0025  # (how far behind one side of a sheet its other side is, brlib.export)
TOLERANCE = 0.015
SMOOTH = math.cos(math.radians(50))
MIN_PART = 24
SMALL = 1500


def budget(mid, n):
    for prefix, cap in CAP:
        if mid.startswith(prefix):
            return min(cap, round(n * LOW))
    return round(n * LOW)


def material(tri):
    """Which material a triangle is: the hue of its brightest corner (baked shadows only darken it); near-black apart."""
    c = max(tri, key=lambda c: c[6] + c[7] + c[8])
    m = max(c[6], c[7], c[8])
    if m < 40:
        return (0, 0, 0)
    return tuple(round(v / m * 10) for v in c[6:9])


def decimate(corners, ratio):
    """The triangles (three corners each, as brm.read gives them) cut down to about `ratio` of them."""
    tris = [corners[t:t + 3] for t in range(0, len(corners), 3)]
    lo = [min(c[k] for c in corners) for k in range(3)]
    hi = [max(c[k] for c in corners) for k in range(3)]
    tolerance = TOLERANCE * math.dist(lo, hi)
    others = sheet_sides(tris)
    groups, every = {}, {}
    for i, tri in enumerate(tris):
        every.setdefault(tri[0][9], []).append(tri)
        if i not in others:
            groups.setdefault((tri[0][9], i in others.values()), []).append(tri)
    out = []
    for (flags, sheet), group in groups.items():
        if len(group) < MIN_PART or ratio >= 0.999:
            out.extend(c for tri in group for c in tri)
            if sheet:
                out.extend(c for i, tri in enumerate(tris) if i in others and tri[0][9] == flags for c in tri)
            continue
        r = max(ratio, MIN_PART / 2 / len(group))
        mats = sorted({material(tri) for tri in group})
        while True:
            shape = _cut(group, r, mats)
            if strays(group, [s[0] for s in shape]) <= tolerance:
                break
            if r >= FAR:  # (no cut keeps its shape: kept whole)
                shape = [([c[:3] for c in tri], material(tri)) for tri in group]
                break
            r = min(FAR, r * 1.6)
        ref = Reference(every[flags])
        front = ref.shade(shape, flags)
        out.extend(front)
        if sheet:  # the other side again, a hair behind, wound the other way, shaded from the old other sides
            back = []
            for t, (_, mat) in enumerate(shape):
                tri = front[t * 3:t * 3 + 3]
                back.append(([tuple(Vector(c[:3]) - Vector(c[3:6]).normalized() * SHEET) for c in (tri[0], tri[2], tri[1])], mat))
            out.extend(ref.shade(back, flags))
    return out


class Reference:
    """The old corners of a part, to shade new triangles like the old surface: for normals all of them, for colours
    those of each material."""

    def __init__(self, tris):
        self.old = [c for tri in tris for c in tri]
        self.kd = self._tree(range(len(self.old)))
        by_mat = {}
        for t, tri in enumerate(tris):
            by_mat.setdefault(material(tri), []).extend(range(t * 3, t * 3 + 3))
        self.mats = {m: (self._tree(ix), ix) for m, ix in by_mat.items()}

    def _tree(self, ix):
        kd = KDTree(len(ix))
        for j, i in enumerate(ix):
            kd.insert(self.old[i][:3], j)
        kd.balance()
        return kd

    def shade(self, shape, flags):
        """Corners for triangles given as (three positions, material)."""
        picked = []
        for tri, mat in shape:
            a, b, c = (Vector(p) for p in tri)
            face = (b - a).cross(c - a)
            face = face.normalized() if face.length > 1e-12 else Vector((0, 1, 0))
            mkd, mix = self.mats.get(mat, (self.kd, range(len(self.old))))
            for p in (a, b, c):
                best, n, rgb = -2.0, face, None
                for _, i, _ in self.kd.find_n(p, 12):
                    m = Vector(self.old[i][3:6])
                    d = m.normalized().dot(face) if m.length else -1
                    if d > best:
                        best, n = d, m.normalized()
                for _, j, _ in mkd.find_n(p, 12):
                    o = self.old[mix[j]]
                    m = Vector(o[3:6])
                    if (m.normalized().dot(face) if m.length else -1) > 0 and (rgb is None or sum(o[6:9]) > sum(rgb)):
                        rgb = o[6:9]
                if best < 0.3:
                    n = face
                if rgb is None:
                    rgb = self.old[mix[mkd.find(p)[1]]][6:9]
                picked.append((p.to_tuple(6), n, rgb, mat))
        by_vertex = {}
        for k, (p, _, _, mat) in enumerate(picked):
            by_vertex.setdefault((p, mat), []).append(k)
        out = []
        for p, n, _, mat in picked:
            mates = [picked[k] for k in by_vertex[(p, mat)] if picked[k][1].dot(n) > SMOOTH]
            mean = sum((m[1] for m in mates), Vector()).normalized() * 127
            col = [round(sum(m[2][c] for m in mates) / len(mates)) for c in range(3)]
            out.append((*p, round(mean.x), round(mean.y), round(mean.z), *col, flags))
        return out


def strays(old, shape):
    """How far the new shape (triangles as three positions) strays from the old triangles, or leaves them out (the
    larger, at its 99th percentile)."""
    if not shape:
        return float("inf")
    old_pts = [[c[:3] for c in tri] for tri in old]
    worst = 0.0
    for a, b in ((shape, old_pts), (old_pts, shape)):
        verts = [p for tri in b for p in tri]
        tree = BVHTree.FromPolygons(verts, [(i, i + 1, i + 2) for i in range(0, len(verts), 3)], all_triangles=True)
        pts = []
        for tri in a:
            pts.append(tuple(sum(p[k] for p in tri) / 3 for k in range(3)))
            pts.extend(tri)
        step = max(1, len(pts) // 6000)
        d = sorted((tree.find_nearest(Vector(p))[3] or 0.0) for p in pts[::step])
        worst = max(worst, d[min(len(d) - 1, int(len(d) * 0.99))])
    return worst


def sheet_sides(tris):
    """Thin sheets: {one side's triangle: the other side's}. Each side is the other moved SHEET against its normals,
    wound the other way, its normals turned round."""
    def centre(tri, shift):
        out = [0.0, 0.0, 0.0]
        for c in tri:
            n = math.sqrt(c[3] ** 2 + c[4] ** 2 + c[5] ** 2) or 1
            for k in range(3):
                out[k] += (c[k] + shift * c[3 + k] / n) / 3
        return out

    grid = {}
    for i, tri in enumerate(tris):
        grid.setdefault(tuple(round(v / 0.002) for v in centre(tri, 0)), []).append(i)
    other, taken = {}, set()
    for j, tri in enumerate(tris):
        if j in taken:
            continue
        g = tuple(round(v / 0.002) for v in centre(tri, -SHEET))
        match = next((i for dx in (-1, 0, 1) for dy in (-1, 0, 1) for dz in (-1, 0, 1)
                      for i in grid.get((g[0] + dx, g[1] + dy, g[2] + dz), ())
                      if i != j and i not in taken and _behind(tri, tris[i])), None)
        if match is not None:
            other[j] = match
            taken.update((j, match))
    return other


def _behind(side, other):
    for b in side:
        n = math.sqrt(b[3] ** 2 + b[4] ** 2 + b[5] ** 2) or 1
        p = [b[k] - SHEET * b[3 + k] / n for k in range(3)]
        if not any(sum((p[k] - f[k]) ** 2 for k in range(3)) < 2.5e-6 and b[3] * f[3] + b[4] * f[4] + b[5] * f[5] < 0 for f in other):
            return False
    return True


def _cut(tris, ratio, mats):
    """The triangles' shape cut down to about `ratio` of them: triangles as (three positions, material), the corners where
    two materials meet kept."""
    bm = bmesh.new()
    verts = {}
    index = {m: i for i, m in enumerate(mats)}
    for tri in tris:
        vs = []
        for c in tri:
            key = (c[0], c[1], c[2])
            v = verts.get(key)
            if v is None:
                v = verts[key] = bm.verts.new(key)
            vs.append(v)
        if len({id(v) for v in vs}) < 3:
            continue
        try:
            f = bm.faces.new(vs)
        except ValueError:  # (the same triangle twice: kept once)
            continue
        f.material_index = index[material(tri)]
    keep = set()
    for e in bm.edges:
        if len({f.material_index for f in e.link_faces}) > 1:
            keep.update(e.verts)
    bm.verts.index_update()
    keep = [v.index for v in keep]
    me = bpy.data.meshes.new("lod")
    bm.to_mesh(me)
    bm.free()
    for _ in mats:
        me.materials.append(None)
    obj = bpy.data.objects.new("lod", me)
    bpy.context.scene.collection.objects.link(obj)
    group = obj.vertex_groups.new(name="keep")
    if keep:
        group.add(keep, 1.0, "REPLACE")
    mod = obj.modifiers.new("lod", "DECIMATE")
    mod.ratio = ratio
    mod.use_collapse_triangulate = True
    mod.vertex_group = "keep"  # (a collapse costs more the less its corners weigh: the group turned round, borders weigh nothing)
    mod.invert_vertex_group = True
    mod.vertex_group_factor = 1000.0
    cut = bpy.data.meshes.new_from_object(obj.evaluated_get(bpy.context.evaluated_depsgraph_get()))
    cut.calc_loop_triangles()
    shape = [([cut.vertices[v].co.to_tuple() for v in lt.vertices], mats[min(lt.material_index, len(mats) - 1)]) for lt in cut.loop_triangles]
    bpy.data.objects.remove(obj)
    bpy.data.meshes.remove(me)
    bpy.data.meshes.remove(cut)
    return shape


def main():
    args = sys.argv[sys.argv.index("--") + 1:]
    folder, only = args[0], set(args[1:])
    ids = sorted(f[:-4] for f in os.listdir(folder) if f.endswith(".brm") and not f.endswith(("_far.brm", "-low.brm")))
    for mid in ids:
        if only and mid not in only:
            continue
        corners = brm.read(os.path.join(folder, mid + ".brm"))
        n = len(corners) // 3
        made = []
        if n < SMALL:
            print(f"LOD {mid}: {n} triangles (light enough)", flush=True)
            continue
        if not os.path.exists(os.path.join(folder, mid + "_far.brm")):
            far = decimate(corners, FAR)
            brm.write(os.path.join(folder, mid + "_far.brm"), far)
            made.append(f"far {len(far) // 3}")
        want = budget(mid, n)
        low_path = os.path.join(folder, mid + "-low.brm")
        if n > want * 1.25:
            low = decimate(corners, want / n)
            brm.write(low_path, low)
            made.append(f"low {len(low) // 3}")
        elif os.path.exists(low_path):
            os.remove(low_path)
        print(f"LOD {mid}: {n} triangles" + (": " + ", ".join(made) if made else " (light enough)"), flush=True)


main()
