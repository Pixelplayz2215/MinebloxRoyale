"""
Checks the Blender models (assets/minebloxroyale/meshes/*.brm) without the game, as ModelAudit did for the code-drawn
ones: every file reads and has sane numbers (the -low copies for far away too, lod.py); every model the game asks for is
there with its far copy (cosmetics in each form the avatar picks: hats over an afro, every haircut under every kind of
hat edge; gliders; cars at every damage stage); and no two surfaces of different colours lie on the same plane over each
other where they'd flicker, both in each model and between the head pieces worn together (every hat + hair, hat + face
and hair + face pair, as Cosmetics.submit picks them).

python tools/check/check_meshes.py [meshes dir] [design dir]      (from fabric/; numpy)
"""
import itertools
import json
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "blender"))
import brm  # noqa: E402

MESHES = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "minebloxroyale", "meshes")
DESIGN = sys.argv[2] if len(sys.argv) > 2 else os.path.join(HERE, "..", "..", "..", "design")
OVER_HAIR = ("headphones", "halo", "bunny", "cat", "tiara")
CUT = {**{s: "b0" for s in ("cap", "backcap", "viking", "beanie", "army", "tricorne")}, **{s: "b2" for s in ("police", "fire_helmet")},
       **{s: "b4" for s in ("chef", "pith", "fedora", "cowboy")}, "witch": "b6"}
problems, notes = [], []
cache = {}


def load(mid):
    """Corner positions (n, 3, 3) and each triangle's colour: r, g, b (its corners' mean) and tint channel."""
    if mid not in cache:
        c = brm.read(os.path.join(MESHES, mid + ".brm"))
        a = np.array([x[:3] for x in c], dtype=np.float64).reshape(-1, 3, 3)
        rgb = np.array([x[6:9] for x in c], dtype=np.float64).reshape(-1, 3, 3).mean(axis=1)
        ch = np.array([x[9] & 3 for x in c[0::3]], dtype=np.float64)
        cache[mid] = (a, np.column_stack([rgb, ch]))
    return cache[mid]


def same_colour(p, q):
    """The same material, maybe shaded differently by the baked shadows (one colour a little darker than the other)."""
    if p[3] != q[3]:
        return False
    a, b = p[:3] + 1, q[:3] + 1
    cos = a @ b / np.linalg.norm(a) / np.linalg.norm(b)
    ratio = max(a.sum(), b.sum()) / min(a.sum(), b.sum())
    return cos > 0.998 and ratio < 1.6


def hair_mesh(hair, hat):
    if hair == "curly" and hat == "tiara":
        return "hair_curly_tiara"
    return f"hair_{hair}_{'full' if hat is None or hat in OVER_HAIR else CUT.get(hat, 'top')}"


def overlap_2d(p, q, eps):
    """How far two triangles (3x2) overlap: the smallest push apart over every edge's axis (<= eps: just touching)."""
    depth = 1e9
    for tri in (p, q):
        for i in range(3):
            e = tri[(i + 1) % 3] - tri[i]
            ax = np.array((-e[1], e[0]))
            ln = np.hypot(*ax)
            if ln < 1e-12:
                return 0.0
            ax /= ln
            a, b = p @ ax, q @ ax
            depth = min(depth, min(a.max(), b.max()) - max(a.min(), b.min()))
            if depth <= eps:
                return 0.0
    return depth


def flicker(tris, cols, piece, cross_only, scale):
    """Pairs of triangles facing the same way on the same plane, overlapping, of different colours."""
    e1, e2 = tris[:, 1] - tris[:, 0], tris[:, 2] - tris[:, 0]
    n = np.cross(e1, e2)
    area = np.linalg.norm(n, axis=1)
    ok = area > 1e-12 * scale * scale
    n[ok] /= area[ok, None]
    d = np.einsum("ij,ij->i", n, tris[:, 0])
    tol = 0.0008 * scale
    key = np.concatenate([np.round(n * 50), np.round(d / tol)[:, None]], axis=1).astype(np.int64)
    idx = np.nonzero(ok)[0]
    order = idx[np.lexsort(key[idx].T[::-1])]
    hits = []
    start = 0
    ks = key[order]
    while start < len(order):
        end = start + 1
        while end < len(order) and (ks[end] == ks[start]).all():
            end += 1
        group = order[start:end]
        if 1 < len(group) <= 400:
            for i, j in itertools.combinations(group, 2):
                if (cross_only and piece[i] == piece[j]) or n[i] @ n[j] < 0.999 or (not cross_only and same_colour(cols[i], cols[j])):
                    continue
                ax = np.argmax(np.abs(n[i]))
                keep = [k for k in range(3) if k != ax]
                depth = overlap_2d(tris[i][:, keep], tris[j][:, keep], 1e-4 * scale)
                if depth > 0:
                    hits.append(depth)
        start = end
    return hits


def main():
    files = sorted(f[:-4] for f in os.listdir(MESHES) if f.endswith(".brm"))
    have = set(files)
    for mid in files:
        try:
            a, _ = load(mid)
        except Exception as e:  # noqa: BLE001
            problems.append(f"{mid}: doesn't read ({e})")
            continue
        if not np.isfinite(a).all():
            problems.append(f"{mid}: broken numbers")
        if len(a) == 0:
            problems.append(f"{mid}: empty")
    rows = json.load(open(os.path.join(DESIGN, "cosmetics.json"), encoding="utf-8"))["rows"]
    shapes = {slot: sorted({r["shape"] for r in rows if r["slot"] == slot}) for slot in ("hat", "hair", "face", "shoes")}
    hides = {r["shape"] for r in rows if r["slot"] in ("hat", "face") and r.get("hideHair")}
    want = ["battle_bus", "reboot_van"]
    for h in shapes["hat"]:
        want += ["hat_" + h] + (["hat_" + h + "_afro"] if h in OVER_HAIR else [])
    for r in shapes["hair"]:
        want += [f"hair_{r}_{c}" for c in ("full", "b0", "b2", "b4", "b6", "top")]
    want += ["hair_curly_tiara"] + ["face_" + f for f in shapes["face"]] + ["shoes_" + s for s in shapes["shoes"]]
    want += [r["id"] for r in rows if r["slot"] == "back"]
    for sh in sorted({r["shape"] for r in rows if r["slot"] == "shirt"}):
        want += [f"shirt_{sh}_{p}" for p in ("torso", "rarm", "larm")]
    for sh in sorted({r["shape"] for r in rows if r["slot"] == "pants"}):
        want += [f"pants_{sh}_{p}" for p in ("hips", "rleg", "lleg")]
    want += [g["id"] for g in json.load(open(os.path.join(DESIGN, "gliders.json"), encoding="utf-8"))["rows"]]
    for kind in sorted({f.split("_")[1] for f in files if f.startswith("car_")}):
        want += [f"car_{kind}_{s}" for s in (1, 2, 3, 4)]
    far = [w for w in want if w not in ("battle_bus", "reboot_van")]
    missing = [w for w in want if w not in have] + [w + "_far" for w in far if w + "_far" not in have]
    for m in missing:
        problems.append(f"{m}: missing")
    # Every model by itself (items are in blocks, the rest in studs or blocks of their own size: scale by its size).
    for mid in files:
        if mid in missing or mid.endswith(("_far", "-low")):
            continue
        a, cols = load(mid)
        scale = float(np.ptp(a.reshape(-1, 3), axis=0).max()) or 1.0
        hits = flicker(a, cols, None, False, scale / 4)
        if hits:
            notes.append((max(hits), f"{mid}: {len(hits)} overlapping surface pair(s) of different colours (biggest {max(hits):.4f})"))
    # Head pieces worn together.
    combos = []
    for h in shapes["hat"]:
        for r in shapes["hair"]:
            if h not in hides:
                combos.append(("hat_" + h + ("_afro" if r == "afro" and h in OVER_HAIR else ""), hair_mesh(r, h)))
        for f in shapes["face"]:
            combos.append(("hat_" + h, "face_" + f))
    for r in shapes["hair"]:
        for f in shapes["face"]:
            if f not in hides:
                combos.append((hair_mesh(r, None), "face_" + f))
    for pa, pb in combos:
        if pa not in have or pb not in have:
            continue
        a, ca = load(pa)
        b, cb = load(pb)
        tris = np.concatenate([a, b])
        cols = np.concatenate([ca, cb])  # (two pieces worn together always count: they're drawn separately)
        piece = np.concatenate([np.zeros(len(a), int), np.ones(len(b), int)])
        hits = flicker(tris, cols, piece, True, 1.0)
        if hits:
            notes.append((max(hits), f"{pa} + {pb}: {len(hits)} overlapping surface pair(s) (biggest {max(hits):.4f} studs)"))
    print(f"{len(files)} models, {len(combos)} head combinations")
    for p in problems:
        print("PROBLEM", p)
    for _, n in sorted(notes, reverse=True)[:40]:
        print("flicker", n)
    print(f"{len(problems)} problems, {len(notes)} with overlapping surfaces")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
