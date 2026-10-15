"""
Pickaxe skins (v2.0.0, creator: "Unlockable pickaxe skins in the Locker"): Blender models drawn in place of the
default pickaxe (items.py pickaxe) when a player picks one in the Locker (cosmetics sheet, slot pickaxe, shape = the
model's name after "pickaxe_"; the server puts it on the pickaxe item, MeshItemRenderer draws it).

Same space and size as the default pickaxe (item pixels, 1/16 block: the handle from the bottom left corner up to the
head at about (11.4, 11.4), lying in the z = 8 plane), so each is held exactly like it.
"""
import math

from brlib import box, cyl, mat, sphere, torus, tube

HX, HY, Z = 11.4, 11.4, 8


def _handle(stick, grip, bands=5, cap=None, step=0.75):
    tube([(2.2, 2.2, Z), (11.6, 11.6, Z)], 0.55, stick, segs=12)
    for i in range(bands):
        p = 2.9 + i * step
        tube([(p - 0.15, p - 0.15, Z), (p + 0.25, p + 0.25, Z)], 0.62, grip, segs=12)
    sphere(2.0, 2.0, Z, 0.75, 0.75, 0.75, cap or grip, segs=14, rings=8)


def _classic_head(head, edge, collar):
    """The default pickaxe's head: a long curved spike one way, a broad blade the other, a collar between."""
    cyl(HX, HY, Z, 0.95, 2.0, "z", collar, verts=16, bevel=0.15)
    tube([(HX, HY, Z), (HX - 2.6, HY + 2.2, Z), (HX - 5.0, HY + 3.3, Z), (HX - 6.9, HY + 3.5, Z)], [1.0, 0.85, 0.55, 0.1], head, segs=14)
    tube([(HX, HY, Z), (HX + 2.3, HY - 2.4, Z), (HX + 3.6, HY - 4.4, Z)], [1.0, 0.9, 0.72], head, segs=14)
    box(HX + 2.8, HY - 5.7, 7.2, HX + 4.6, HY - 4.1, 8.8, edge, bevel=0.35, seg=3).rot("z", -32, (HX + 3.7, HY - 4.9, Z))


def golden():
    gold = mat("pk_gold", "#F2C230", rough=0.22, metal=1.0)
    _handle(mat("pk_black", "#1A1A1F", rough=0.5), gold)
    _classic_head(gold, mat("pk_gold_edge", "#FFE58A", rough=0.15, metal=1.0), mat("pk_dark_gold", "#B8861B", rough=0.3, metal=0.9))


def candy():
    red, white = mat("pk_candy_red", "#E0263A", rough=0.3), mat("pk_candy_white", "#F7F4F0", rough=0.3)
    # a candy-cane handle: red and white turns all the way up
    for i in range(12):
        a, b = 2.2 + i * 0.78, 2.2 + (i + 1) * 0.78
        tube([(a, a, Z), (b, b, Z)], 0.58, red if i % 2 else white, segs=12)
    sphere(2.0, 2.0, Z, 0.8, 0.8, 0.8, red, segs=14, rings=8)
    # the head: a striped hook one way, a peppermint disc the other
    hook = [(HX, HY), (HX - 2.4, HY + 2.3), (HX - 4.7, HY + 3.1), (HX - 6.6, HY + 2.6)]
    for i in range(len(hook) - 1):
        (x0, y0), (x1, y1) = hook[i], hook[i + 1]
        tube([(x0, y0, Z), (x1, y1, Z)], [0.95 - 0.2 * i, 0.75 - 0.2 * i], red if i % 2 == 0 else white, segs=14)
    px, py = HX + 1.7, HY - 1.8  # (the peppermint sits right against the head's collar)
    cyl(px, py, Z, 1.8, 1.1, "z", white, verts=28, bevel=0.3)
    for k in range(6):  # the peppermint's red swirls
        box(px - 0.22, py, Z - 0.6, px + 0.22, py + 1.65, Z + 0.6, red, bevel=0.15).rot("z", k * 60 + 15, (px, py, Z))
    cyl(HX, HY, Z, 0.9, 1.6, "z", red, verts=16, bevel=0.2)


def diamond():
    """A blocky Minecraft-style diamond pickaxe (a nod to the game it's in)."""
    stick = mat("pk_stick", "#6B4A2B", rough=0.8)
    gem, gem_dark = mat("pk_diamond", "#4FE3E0", rough=0.2, metal=0.2), mat("pk_diamond_dark", "#1E9C9A", rough=0.3)
    for i in range(11):  # the stick, pixel by pixel up the diagonal, into the head
        box(2 + i, 2 + i, Z - 0.5, 3 + i, 3 + i, Z + 0.5, stick, bevel=0.06)
    # the head: an arc of pixels across the top
    pix = [(8, 13), (9, 14), (10, 14), (11, 14), (12, 14), (13, 13), (13, 12), (14, 11), (14, 10), (14, 9), (14, 8), (13, 13)]
    for i, (x, y) in enumerate(pix):
        box(x, y, Z - 0.55, x + 1, y + 1, Z + 0.55, gem if i % 3 else gem_dark, bevel=0.06)
    for x, y in ((7, 13), (13, 7), (12, 13)):
        box(x, y, Z - 0.55, x + 1, y + 1, Z + 0.55, gem_dark, bevel=0.06)


def ice():
    ice_m = mat("pk_ice", "#BFEAFF", rough=0.05, metal=0.1)
    deep = mat("pk_ice_deep", "#5FB8F0", rough=0.1)
    _handle(mat("pk_frost", "#E8F6FF", rough=0.4), deep, cap=ice_m)
    cyl(HX, HY, Z, 1.0, 2.0, "z", deep, verts=8, bevel=0.1)
    # crystal shards for the spike and the blade
    for (dx, dy, ln, r, rot) in ((-1.6, 1.6, 5.8, 1.0, 135), (1.6, -1.8, 4.6, 1.15, -45), (-0.6, 2.2, 3.0, 0.6, 105), (2.2, -0.4, 2.6, 0.55, -15)):
        c = cyl(HX + dx, HY + dy, Z, r, ln, "y", ice_m if r > 0.8 else deep, verts=6, r2=0.05)
        c.rot("z", rot - 90, (HX + dx, HY + dy, Z))


def lava():
    rock = mat("pk_rock", "#4A3A34", rough=0.9)
    glow = mat("pk_lava", "#FF7A1A", glow=True)
    _handle(mat("pk_char", "#141012", rough=0.7), mat("pk_ember", "#E8461A", rough=0.5), cap=rock)
    _classic_head(rock, rock, rock)
    # glowing cracks along the spike and the blade
    # (on the faces of the spike and the blade: their half-thickness there, and a touch more)
    for side in (1, -1):
        tube([(HX - 1.0, HY + 1.0, Z + side * 1.0), (HX - 3.3, HY + 2.6, Z + side * 0.8), (HX - 5.4, HY + 3.2, Z + side * 0.42)], 0.18, glow, segs=6)
        tube([(HX + 0.8, HY - 0.8, Z + side * 1.02), (HX + 2.2, HY - 2.6, Z + side * 0.94), (HX + 3.3, HY - 4.0, Z + side * 0.78)], 0.18, glow, segs=6)
    sphere(HX, HY, Z, 0.55, 0.55, 1.15, glow, segs=12, rings=6)


def lollipop():
    stick = mat("pk_lolly_stick", "#F4F1EA", rough=0.5)
    _handle(stick, mat("pk_lolly_grip", "#FF8FC8", rough=0.5), bands=3)
    cols = [mat(f"pk_lolly{i}", c, rough=0.15) for i, c in enumerate(("#FF4FA3", "#FFD23F", "#4FD1FF", "#9B5DE5"))]
    # a big swirl on the head (rings of colour), a little blade the other way
    cyl(HX - 2.2, HY + 2.2, Z, 3.0, 1.2, "z", cols[0], verts=32, bevel=0.35)
    for i in range(4):
        torus(HX - 2.2, HY + 2.2, Z + 0.62, 0.6 + i * 0.62, 0.17, "z", cols[(i + 1) % 4], segs=28, rsegs=6)
        torus(HX - 2.2, HY + 2.2, Z - 0.62, 0.6 + i * 0.62, 0.17, "z", cols[(i + 1) % 4], segs=28, rsegs=6)
    tube([(HX, HY, Z), (HX + 2.0, HY - 2.1, Z), (HX + 3.0, HY - 3.6, Z)], [0.8, 0.7, 0.5], cols[1], segs=12)


def neon():
    dark = mat("pk_neon_body", "#1C1F2A", rough=0.35, metal=0.6)
    lit = mat("pk_neon_glow", "#3FF2FF", glow=True)
    _handle(dark, lit, bands=4)
    _classic_head(dark, lit, dark)
    tube([(HX - 0.6, HY + 1.4, Z + 0.7), (HX - 2.9, HY + 2.9, Z + 0.55), (HX - 5.2, HY + 3.7, Z + 0.3)], 0.13, lit, segs=6)
    tube([(HX - 0.6, HY + 1.4, Z - 0.7), (HX - 2.9, HY + 2.9, Z - 0.55), (HX - 5.2, HY + 3.7, Z - 0.3)], 0.13, lit, segs=6)


VIEWS = {"Front": 0, "Right": 270, "Back": 180, "Left": 90}


def _rows():
    import json, os
    here = os.path.dirname(os.path.abspath(__file__))
    rows = json.load(open(os.path.join(here, "..", "..", "..", "design", "cosmetics.json"), encoding="utf-8"))["rows"]
    return {r["id"]: r for r in rows if r["slot"] == "pickaxe"}


def _entry(rid, fn):
    """The Locker pictures' way (creator: every render named, with what unlocks it or its price): its name and unlock
    from the cosmetics sheet, into Locker Items v2.0.0/New Pickaxes (compose.py into Locker Screenshots)."""
    from gliders import unlock_text
    row = _rows()[rid]
    return (row["name"], fn, {"icon": (0, 0), "ao": 1.5, "folder": "New Pickaxes", "views": VIEWS, "unlock": unlock_text(row)})


ITEMS = {
    "pickaxe_golden": _entry("pickaxe_golden", golden),
    "pickaxe_candy": _entry("pickaxe_candy", candy),
    "pickaxe_diamond": _entry("pickaxe_diamond", diamond),
    "pickaxe_ice": _entry("pickaxe_ice", ice),
    "pickaxe_lava": _entry("pickaxe_lava", lava),
    "pickaxe_lollipop": _entry("pickaxe_lollipop", lollipop),
    "pickaxe_neon": _entry("pickaxe_neon", neon),
}
