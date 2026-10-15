"""
The loot chests, made in Blender (v1.0.1, creator: "both types of chest open and closed to have a smoother texture
built in Blender. And the chest should have an opening animation"): Fortnite's wooden chest with gold bands, and the
rare one, deep blue with silver trim. Each is two models the game puts together (ChestRenderer): the body, hollow with
a glowing floor that shows once it's open, and the rounded lid, made on its hinge so the game can swing it open.

Block units, the front along +z, the origin on the ground in the middle of the block (the lid's on its hinge, at the
back of the body's top). Planks are separate rounded boards, so the grooves between them are real (no painted lines
lying on another surface, which would flicker).
"""
import math
import random

from brlib import box, mat, prism

W, D, H = 0.42, 0.29, 0.46  # half width, half depth, body height
WALL = 0.035
HINGE = (0.0, H, -D)

STYLE = {
    "loot": dict(wood=("#9A6234", "#8A5530", "#A86C3A", "#93592F"), inner="#4A2E19", trim="#E8B53A", trim_dark="#B9862A",
                 glow="#FFD54A", lock="#F2C94C"),
    "rare": dict(wood=("#2E5AA8", "#2A529A", "#3463B4", "#2B559F"), inner="#16284A", trim="#C9DDF2", trim_dark="#8FA9C6",
                 glow="#7FE3FF", lock="#DCEBFA"),
}


def _mats(kind):
    st = STYLE[kind]
    return dict(
        wood=[mat(f"{kind}_wood{i}", c, rough=0.75) for i, c in enumerate(st["wood"])],
        inner=mat(f"{kind}_inner", st["inner"], rough=0.85),
        trim=mat(f"{kind}_trim", st["trim"], rough=0.28, metal=0.9),
        trim_dark=mat(f"{kind}_trim_dark", st["trim_dark"], rough=0.35, metal=0.9),
        glow=mat(f"{kind}_glow", st["glow"], glow=True),
        lock=mat(f"{kind}_lock", st["lock"], rough=0.22, metal=0.95),
        hole=mat(f"{kind}_hole", "#141010", rough=0.9),
    )


def body(kind):
    m = _mats(kind)
    rnd = random.Random(3 if kind == "loot" else 5)
    made = []
    # the floor, and the glow inside (seen once the lid is up)
    made.append(box(-W + WALL, 0, -D + WALL, W - WALL, 0.05, D - WALL, m["inner"], bevel=0.008))
    made.append(box(-W + 0.07, 0.05, -D + 0.07, W - 0.07, 0.058, D - 0.07, m["glow"]))
    # four walls of horizontal planks (rounded, so the seams between them are real grooves)
    rows = 4
    h = H / rows
    for r in range(rows):
        y0, y1 = r * h, (r + 1) * h
        for x0, z0, x1, z1 in ((-W, D - WALL, W, D), (-W, -D, W, -D + WALL), (-W, -D + WALL, -W + WALL, D - WALL), (W - WALL, -D + WALL, W, D - WALL)):
            made.append(box(x0, y0, z0, x1, y1, z1, rnd.choice(m["wood"]), bevel=0.012))
    t = 0.014  # how far the metal stands out from the wood
    # corner brackets, an L round each upright edge
    for sx in (-1, 1):
        for sz in (-1, 1):
            xa, za = sx * W, sz * D
            made.append(box(xa - sx * 0.075, 0, za, xa + sx * t, H, za + sz * t, m["trim"], bevel=0.006))
            made.append(box(xa, 0, za - sz * 0.075, xa + sx * t, H, za + sz * (t - 0.0005), m["trim"], bevel=0.006))
    # a band along the bottom and round the top of the body
    for y0, y1 in ((0.0, 0.05), (H - 0.045, H)):
        made.append(box(-W + 0.07, y0, D, W - 0.07, y1, D + t * 0.8, m["trim_dark"], bevel=0.005))
        made.append(box(-W + 0.07, y0, -D - t * 0.8, W - 0.07, y1, -D, m["trim_dark"], bevel=0.005))
        made.append(box(-W - t * 0.8, y0, -D + 0.07, -W, y1, D - 0.07, m["trim_dark"], bevel=0.005))
        made.append(box(W, y0, -D + 0.07, W + t * 0.8, y1, D - 0.07, m["trim_dark"], bevel=0.005))
    # the lock: a plate on the front, its keyhole
    made.append(box(-0.075, H - 0.15, D + t * 0.8, 0.075, H - 0.02, D + t * 0.8 + 0.016, m["lock"], bevel=0.008))
    made.append(box(-0.012, H - 0.12, D + t * 0.8 + 0.016, 0.012, H - 0.075, D + t * 0.8 + 0.022, m["hole"], bevel=0.003))
    return made


def _arc(r, n=14, yc=None):
    """The lid's curve, front (+z) over to the back, radius r round the lid's middle line."""
    sag = 0.19
    R = (D * D + sag * sag) / (2 * sag)
    yc = H + sag - R if yc is None else yc
    a0 = math.asin(min(1.0, D / R))
    return [(r * math.sin(a), yc + r * math.cos(a)) for a in (a0 - 2 * a0 * i / n for i in range(n + 1))], R, yc


def lid(kind):
    m = _mats(kind)
    rnd = random.Random(7 if kind == "loot" else 9)
    made = []
    pts, R, yc = _arc((0.29 * 0.29 + 0.19 * 0.19) / (2 * 0.19))
    # the core: the curved top down to the flat underside (dark, the inside of the lid)
    core = [(z, max(H, y - 0.025)) for z, y in _arc(R - 0.025)[0]]
    made.append(prism([(D - 0.004, H)] + core[1:-1] + [(-D + 0.004, H)], -W + 0.01, W - 0.01, m["inner"], bevel=0.006, plane="zy"))
    # boards along the curve, each its own rounded strip
    n = 4
    a0 = math.asin(D / R)
    for i in range(n):
        aa, ab = a0 - 2 * a0 * i / n, a0 - 2 * a0 * (i + 1) / n
        outer = [(R * math.sin(a), yc + R * math.cos(a)) for a in (aa + (ab - aa) * k / 5 for k in range(6))]
        inner = [(z * (R - 0.032) / R, yc + (y - yc) * (R - 0.032) / R) for z, y in reversed(outer)]
        made.append(prism(outer + inner, -W, W, rnd.choice(m["wood"]), bevel=0.01, plane="zy"))
    # gold ends over the boards' ends, and a bar across the middle
    t = 0.016
    arc = [a0 - 2 * a0 * k / 16 for k in range(17)]
    trim_arc = [(a0 - 0.02) - 2 * (a0 - 0.02) * k / 16 for k in range(17)]  # (metal ends short of the boards: no end face on another)
    for sx in (-1, 1):
        # the end: wood (the boards' ends tucked in under it), with a metal rim along its curve
        # (a touch bigger than the boards and above the lid's underside: no face of it lies on another)
        cap = [((R + 0.006) * math.sin(a), yc + (R + 0.006) * math.cos(a)) for a in arc]
        made.append(prism([(D + 0.004, H + 0.004)] + cap + [(-D - 0.004, H + 0.004)], sx * (W - 0.05), sx * (W + 0.006), rnd.choice(m["wood"]),
                          bevel=0.006, plane="zy"))
        outer = [((R + t) * math.sin(a), yc + (R + t) * math.cos(a)) for a in trim_arc]
        inner = [((R - 0.045) * math.sin(a), yc + (R - 0.045) * math.cos(a)) for a in reversed(trim_arc)]
        made.append(prism(outer + inner, sx * (W - 0.055), sx * (W + t * 0.8), m["trim"], bevel=0.005, plane="zy"))
    band = [((R + t * 0.7) * math.sin(a), yc + (R + t * 0.7) * math.cos(a)) for a in trim_arc]
    inner_band = [(z * (R - 0.01) / (R + t * 0.7), yc + (y - yc) * (R - 0.01) / (R + t * 0.7)) for z, y in reversed(band)]
    made.append(prism(band + inner_band, -0.05, 0.05, m["trim_dark"], bevel=0.005, plane="zy"))
    # a lip along the front edge, and the hasp hanging down over the lock
    made.append(box(-W + 0.06, H - 0.004, D, W - 0.06, H + 0.03, D + t, m["trim_dark"], bevel=0.005))
    made.append(box(-0.05, H - 0.05, D + t * 0.8 + 0.016, 0.05, H + 0.07, D + t * 0.8 + 0.03, m["lock"], bevel=0.008))
    return made


def opened(parts, deg=105):
    """Swings the lid's parts open about the hinge (for the pictures)."""
    for p in parts:
        p.rot("x", -deg, HINGE)


VIEWS = {"Front": 0, "Left": 90, "Back": 180, "Right": 270}


def _entry(name, fn, export=True, origin=(0, 0, 0)):
    opts = {"scale": 1.0, "ao": 0.35, "folder": "Chest Models v1.0.1", "views": VIEWS, "export": export, "origin": origin}
    if export:
        opts["lod"] = 0.4
    return (name, fn, opts)


ITEMS = {
    "chest_loot_body": _entry("Loot chest - body", lambda: body("loot") and None),
    "chest_loot_lid": _entry("Loot chest - lid", lambda: lid("loot") and None, origin=HINGE),
    "chest_rare_body": _entry("Rare chest - body", lambda: body("rare") and None),
    "chest_rare_lid": _entry("Rare chest - lid", lambda: lid("rare") and None, origin=HINGE),
    "chest_loot_closed": _entry("Loot chest (closed)", lambda: (body("loot"), lid("loot")) and None, export=False),
    "chest_loot_open": _entry("Loot chest (open)", lambda: (body("loot"), opened(lid("loot"))) and None, export=False),
    "chest_rare_closed": _entry("Rare chest (closed)", lambda: (body("rare"), lid("rare")) and None, export=False),
    "chest_rare_open": _entry("Rare chest (open)", lambda: (body("rare"), opened(lid("rare"))) and None, export=False),
}
