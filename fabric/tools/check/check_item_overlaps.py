"""Overlapping textures in the mod's JSON models (guns, heals, shields, chests), checked the way the game draws them.

Builds every face exactly as Minecraft bakes it (FaceInfo corner order, CuboidFace UV corners, element rotation about
its origin), then draws each model from 60 directions with a depth buffer twice: once letting the first of two
surfaces at the same depth win, once the last. A pixel that changes colour between the two is a place where two
textures lie on top of each other (in the game it flickers as the camera moves, like the first open chest lid did).
Writes a picture of every model that has any, with those pixels in magenta.

  python check_item_overlaps.py <assets/minebloxroyale dir> <output dir> [depth tolerance in 1/16 block, default 0.01]
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

ASSETS, OUT = sys.argv[1], sys.argv[2]
EPS = float(sys.argv[3]) if len(sys.argv) > 3 else 0.01
os.makedirs(OUT, exist_ok=True)

# FaceInfo: each face's 4 corners as (x, y, z) picks from (from, to): 0 = min, 1 = max.
FACE = {
    "down": [(0, 0, 1), (0, 0, 0), (1, 0, 0), (1, 0, 1)],
    "up": [(0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)],
    "north": [(1, 1, 0), (1, 0, 0), (0, 0, 0), (0, 1, 0)],
    "south": [(0, 1, 1), (0, 0, 1), (1, 0, 1), (1, 1, 1)],
    "west": [(0, 1, 0), (0, 0, 0), (0, 0, 1), (0, 1, 1)],
    "east": [(1, 1, 1), (1, 0, 1), (1, 0, 0), (1, 1, 0)],
}


def corner_uv(uv, rot, i):
    """CuboidFace.getU/getV: corner i (turned by the face's rotation) -> (u, v) in 0..16."""
    j = (i + rot // 90) % 4
    return (uv[0] if j in (0, 1) else uv[2], uv[1] if j in (0, 3) else uv[3])


def axis_rot(axis, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    return {"x": np.array([[1, 0, 0], [0, c, -s], [0, s, c]]), "y": np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]]),
            "z": np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])}[axis]


def load_model(ref):
    ns, p = ref.split(":") if ":" in ref else ("minecraft", ref)
    if ns != "minebloxroyale":
        return None
    return json.load(open(os.path.join(ASSETS, "models", p + ".json"), encoding="utf-8"))


def resolve(model):
    """Textures and elements through the parent chain."""
    chain, m = [], model
    while m is not None:
        chain.append(m)
        m = load_model(m["parent"]) if "parent" in m else None
    tex = {}
    for c in reversed(chain):
        tex.update(c.get("textures", {}))
    elements = next((c["elements"] for c in chain if "elements" in c), [])
    return tex, elements


TEXCACHE = {}


def texture(ref):
    ns, p = ref.split(":")
    if ref not in TEXCACHE:
        TEXCACHE[ref] = np.asarray(Image.open(os.path.join(ASSETS, "textures", p + ".png")).convert("RGBA"), dtype=np.int32)
    return TEXCACHE[ref]


def quads(model):
    """Every face: 4 corners (1/16 block units), their (u, v), the texture and whether it is tinted."""
    tex, elements = resolve(model)
    out = []
    for ei, e in enumerate(elements):
        f, t = np.array(e["from"], float), np.array(e["to"], float)
        R, o = np.eye(3), np.zeros(3)
        if "rotation" in e:
            r = e["rotation"]
            R, o = axis_rot(r["axis"], r["angle"]), np.array(r["origin"], float)
        for name, face in e["faces"].items():
            ref = face["texture"]
            while ref.startswith("#"):
                if ref[1:] not in tex:
                    return None  # (a parent model whose children name the texture: checked through them)
                ref = tex[ref[1:]]
            pts = np.array([[(f, t)[k[0]][0], (f, t)[k[1]][1], (f, t)[k[2]][2]] for k in FACE[name]])
            pts = (pts - o) @ R.T + o
            uvs = [corner_uv(face.get("uv", [0, 0, 16, 16]), face.get("rotation", 0), i) for i in range(4)]
            out.append((pts, uvs, ref, "tintindex" in face, ei))
    return out


def views():
    for pitch in (-60, -30, 0, 30, 60):
        for yaw in range(0, 360, 30):
            yield yaw, pitch


def draw(qs, yaw, pitch, size, first_wins):
    """Orthographic view; returns the colour of every pixel (texel RGBA + tint flag), -1 where empty."""
    cy, sy, cp, sp = math.cos(math.radians(yaw)), math.sin(math.radians(yaw)), math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    V = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    V = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]]) @ V
    scale = size / 24.0
    depth = np.full((size, size), -1e9)
    col = np.full((size, size), -1, dtype=np.int64)
    fid = np.full((size, size), -1, dtype=np.int64)
    for qi, (pts, uvs, ref, tinted, _) in enumerate(qs):
        v = (pts - 8) @ V.T
        # A face seen edge-on covers no area in the game: leave it out (it only makes hairlines here).
        n = np.cross(v[1] - v[0], v[2] - v[0])
        if abs(n[2]) < 0.08 * (np.linalg.norm(n) or 1):
            continue
        # Faces seen from behind still draw (both pairs of a fight are checked from the side that shows them).
        img = texture(ref)
        th, tw = img.shape[0], img.shape[1]
        sx, sy2, sz = v[:, 0] * scale + size / 2, size / 2 - v[:, 1] * scale, v[:, 2] / 16.0  # depth in blocks
        for a, b, c in ((0, 1, 2), (0, 2, 3)):
            x0, y0, x1, y1, x2, y2 = sx[a], sy2[a], sx[b], sy2[b], sx[c], sy2[c]
            area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
            if abs(area) < 1e-9:
                continue
            bx0, bx1 = max(0, int(min(x0, x1, x2))), min(size - 1, int(max(x0, x1, x2)) + 1)
            by0, by1 = max(0, int(min(y0, y1, y2))), min(size - 1, int(max(y0, y1, y2)) + 1)
            if bx0 > bx1 or by0 > by1:
                continue
            gx, gy = np.meshgrid(np.arange(bx0, bx1 + 1) + 0.5, np.arange(by0, by1 + 1) + 0.5)
            w0 = ((x1 - gx) * (y2 - gy) - (x2 - gx) * (y1 - gy)) / area
            w1 = ((x2 - gx) * (y0 - gy) - (x0 - gx) * (y2 - gy)) / area
            w2 = 1 - w0 - w1
            inside = (w0 >= -1e-9) & (w1 >= -1e-9) & (w2 >= -1e-9)
            if not inside.any():
                continue
            z = w0 * sz[a] + w1 * sz[b] + w2 * sz[c]
            u = w0 * uvs[a][0] + w1 * uvs[b][0] + w2 * uvs[c][0]
            vv = w0 * uvs[a][1] + w1 * uvs[b][1] + w2 * uvs[c][1]
            px = np.clip((u / 16 * tw).astype(int), 0, tw - 1)
            py = np.clip((vv / 16 * th).astype(int), 0, th - 1)
            texel = img[py, px]
            if (texel[..., 3] == 0).all():
                continue
            key = (texel[..., 0].astype(np.int64) << 32) | (texel[..., 1].astype(np.int64) << 24) | (texel[..., 2].astype(np.int64) << 16) | (texel[..., 3].astype(np.int64) << 8) | int(tinted)
            dsub = depth[by0:by1 + 1, bx0:bx1 + 1]
            csub = col[by0:by1 + 1, bx0:bx1 + 1]
            eps = EPS / 16.0
            # (a cutout texel with alpha 0 is a hole: it never draws)
            ok = inside & (texel[..., 3] > 0)
            win = ok & ((z > dsub + eps) if first_wins else (z > dsub - eps))
            dsub[win] = np.maximum(dsub[win], z[win]) if first_wins else z[win]
            csub[win] = key[win]
            fid[by0:by1 + 1, bx0:bx1 + 1][win] = qi
    return col, fid


def normal(pts):
    n = np.cross(pts[1] - pts[0], pts[2] - pts[0])
    return n / (np.linalg.norm(n) or 1)


def check(name, model, size=360):
    qs = quads(model)
    if qs is None:
        print(f"parent  {name}: shared shape, checked through the models that use it")
        return 0
    normals = np.array([normal(q[0]) for q in qs])
    element = np.array([q[4] for q in qs])
    worst, worst_view, total = 0, None, 0
    for yaw, pitch in views():
        a, fa = draw(qs, yaw, pitch, size, True)
        b, fb = draw(qs, yaw, pitch, size, False)
        # Two different textures on the same plane: where the two faces that win the pixel are parallel. (Where
        # faces meet at an edge or cut through each other at an angle, they are only level along a line: no flicker.)
        par = np.zeros_like(a, dtype=bool)
        both = (fa >= 0) & (fb >= 0) & (fa != fb)
        # Two different pieces (a piece can't fight itself: its parallel faces are its two opposite sides), facing
        # the same way (two pieces back to back are hidden inside each other).
        if both.any():
            par[both] = (np.einsum("ij,ij->i", normals[fa[both]], normals[fb[both]]) > 0.999) & (element[fa[both]] != element[fb[both]])
        diff = (a != b) & (a >= 0) & (b >= 0) & par
        # Real overlaps are patches: keep pixels that are part of a 2 x 2 block of them.
        blk = diff[:-1, :-1] & diff[1:, :-1] & diff[:-1, 1:] & diff[1:, 1:]
        keep = np.zeros_like(diff)
        keep[:-1, :-1] |= blk
        keep[1:, :-1] |= blk
        keep[:-1, 1:] |= blk
        keep[1:, 1:] |= blk
        diff = keep
        n = int(diff.sum())
        total += n
        if n > worst:
            worst, worst_view = n, (yaw, pitch, a, diff)
    if worst_view:
        yaw, pitch, a, diff = worst_view
        rgb = np.zeros((size, size, 3), np.uint8) + 40
        filled = a >= 0
        rgb[filled, 0] = (a[filled] >> 32) & 255
        rgb[filled, 1] = (a[filled] >> 24) & 255
        rgb[filled, 2] = (a[filled] >> 16) & 255
        rgb[diff] = (255, 0, 255)
        Image.fromarray(rgb).resize((size * 2, size * 2), Image.NEAREST).save(os.path.join(OUT, f"{name}_overlaps.png"))
    print(f"{'OVERLAP' if total else 'ok     '} {name}: {total} pixels in {len(list(views()))} views" + (f", worst {worst} at yaw {worst_view[0]} pitch {worst_view[1]}" if worst_view else ""))
    return total


bad = 0
items = sorted(f[:-5] for f in os.listdir(os.path.join(ASSETS, "models", "item")) if not f.endswith("_icon.json"))
for it in items:
    m = load_model("minebloxroyale:item/" + it)
    if "elements" not in m and resolve(m)[1] == []:
        print(f"flat    {it}: a 2D sprite item ({m.get('parent')}), nothing to overlap")
        continue
    bad += check("item_" + it, m) > 0
for blk in sorted(f[:-5] for f in os.listdir(os.path.join(ASSETS, "models", "block"))):
    m = load_model("minebloxroyale:block/" + blk)
    if not resolve(m)[1]:
        continue
    bad += check("block_" + blk, m) > 0
print(f"\n{bad} models with overlapping textures (depth tolerance {EPS} / 16 block)")
