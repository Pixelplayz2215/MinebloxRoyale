"""
The game's item files for the Blender items (after build.py items): python make_items.py <manifest_items.json> <resources dir>

  assets/minebloxroyale/items/<id>.json         the Blender mesh everywhere, the hotbar and inventory too (2.0.0-test2,
                                                creator: "let's show the 3d icons rather than these icons"; before, a
                                                flat 32 x 32 picture there)
  assets/minebloxroyale/models/item/<id>.json   the mesh's hold in each hand and its pose in a slot (display), no geometry
                                                of its own
  assets/minebloxroyale/textures/item/<id>.png  Blender's inventory render at 32 x 32 with a dark outline (the item's
                                                particle picture)
  assets/minecraft/items/iron_pickaxe.json      the harvesting pickaxe (an iron pickaxe) as the Blender pickaxe
(2.0.0-test3: the ammo, the materials and the reboot card are Blender items too; before, flat pictures.)

python make_items.py --gui <resources dir>: just the slot poses and the item files, from the meshes already there (their
bounds), without Blender.
"""
import gzip
import json
import math
import os
import struct
import sys

from PIL import Image

GUN_DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [0, 1.5, 2], "scale": [0.9, 0.9, 0.9]},
    "thirdperson_lefthand": {"rotation": [0, -90, 0], "translation": [0, 1.5, 2], "scale": [0.9, 0.9, 0.9]},
    "firstperson_righthand": {"rotation": [0, -90, 5], "translation": [1.13, 3.2, 1.13], "scale": [0.8, 0.8, 0.8]},
    "firstperson_lefthand": {"rotation": [0, 90, -5], "translation": [1.13, 3.2, 1.13], "scale": [0.8, 0.8, 0.8]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "fixed": {"rotation": [0, 180, 0], "scale": [1, 1, 1]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    "gui": {"rotation": [0, 0, 0], "scale": [1, 1, 1]},
}
# Heals and shields: the same hold as Minecraft's flat items (item/generated), so they sit in the hand as before.
ITEM_DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "fixed": {"rotation": [0, 180, 0], "scale": [1, 1, 1]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    "gui": {"rotation": [0, 0, 0], "scale": [1, 1, 1]},
}
COMMON = 0xB0B0B0  # a gun's rarity colour when it has none (common)
# How items.py's icon camera looked at each kind (turned yaw, tilted pitch): a slot shows the model the same way.
GUN_VIEW, ITEM_VIEW, PICKAXE_VIEW = (24, 12), (-28, 18), (0, 0)


def view_of(iid):
    return PICKAXE_VIEW if iid == "pickaxe" else GUN_VIEW if iid.startswith("gun_") else ITEM_VIEW


def gui_display(lo, hi, yaw, pitch, fill=0.94):
    """
    The "gui" display that shows a model (bounds lo..hi, in blocks) as items.py's icon camera did: the view direction
    (yaw, pitch) turned to face the screen (rotation [pitch, -yaw, 0]: Minecraft turns about Y, then X), centred, and
    scaled so the model's box fills that much of the slot.
    """
    a, e = math.radians(yaw), math.radians(pitch)

    def turn(v):
        x, y, z = v
        x, z = math.cos(a) * x - math.sin(a) * z, math.sin(a) * x + math.cos(a) * z  # about Y by -yaw
        y, z = math.cos(e) * y - math.sin(e) * z, math.sin(e) * y + math.cos(e) * z  # about X by pitch
        return x, y, z

    c = [(l + h) / 2 for l, h in zip(lo, hi)]
    pts = [turn((x - c[0], y - c[1], z - c[2])) for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])]
    k = fill / 2 / max(max(abs(p[0]) for p in pts), max(abs(p[1]) for p in pts))
    t = turn((c[0] - 0.5, c[1] - 0.5, c[2] - 0.5))
    return {"rotation": [pitch, -yaw, 0], "translation": [round(-k * v * 16, 3) for v in t], "scale": [round(k, 4)] * 3}


def mesh_bounds(path):
    """A .brm mesh's bounds (BRM2 keeps them in its header): (lo, hi)."""
    with gzip.open(path, "rb") as f:
        head = f.read(36)
    assert head[:4] == b"BRM2", path
    v = struct.unpack("<6f", head[12:36])
    return v[:3], v[3:]


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, separators=(",", ":"))


def outline(img, color=(16, 16, 20, 255)):
    w, h = img.size
    src = img.load()
    out = img.copy()
    o = out.load()
    for y in range(h):
        for x in range(w):
            if src[x, y][3]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h and src[nx, ny][3]:
                    o[x, y] = color
                    break
    return out


def small(path, size=32, pad=1):
    """A Blender render squeezed to the inventory size: crisp edges (no half-see-through pixels), a pixel's margin."""
    big = Image.open(path).convert("RGBA")
    box = big.getbbox()
    big = big.crop(box)
    w, h = big.size
    s = (size - 2 * pad) / max(w, h)
    img = big.resize((max(1, round(w * s)), max(1, round(h * s))), Image.LANCZOS)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(img, ((size - img.width) // 2, (size - img.height) // 2))
    px = out.load()
    for y in range(size):
        for x in range(size):
            r, g, b, a = px[x, y]
            px[x, y] = (min(255, r * 255 // a), min(255, g * 255 // a), min(255, b * 255 // a), 255) if a > 110 else (0, 0, 0, 0)
    return out, box, s


def main():
    manifest = json.load(open(sys.argv[1], encoding="utf-8"))
    res = sys.argv[2]
    assets = os.path.join(res, "assets", "minebloxroyale")
    for e in manifest:
        iid = e["id"]
        icon, _, _ = small(e["icon"])
        outline(icon).save(os.path.join(assets, "textures/item", iid + ".png"))
        write_files(res, iid, e["bounds"][0], e["bounds"][1])
    print("wrote", len(manifest), "items")


def write_files(res, iid, lo, hi):
    """An item's base model (its holds, and its pose in a slot) and its item file: the mesh in every view."""
    assets = os.path.join(res, "assets", "minebloxroyale")
    gun, pick = iid.startswith("gun_"), iid == "pickaxe"
    gui = gui_display(lo, hi, *view_of(iid))
    mesh = {"type": "minecraft:special", "base": "minebloxroyale:item/" + iid, "model": {"type": "minebloxroyale:mesh", "mesh": iid}}
    if gun:
        mesh["model"]["tint"] = COMMON
    if pick:
        write_json(os.path.join(assets, "models/item/pickaxe.json"),
                   {"parent": "minecraft:item/handheld", "textures": {"layer0": "minebloxroyale:item/pickaxe"}, "display": {"gui": gui}})
        write_json(os.path.join(res, "assets", "minecraft", "items", "iron_pickaxe.json"), {"model": mesh})
    else:
        write_json(os.path.join(assets, "models/item", iid + ".json"),
                   {"textures": {"particle": "minebloxroyale:item/" + iid}, "display": {**(GUN_DISPLAY if gun else ITEM_DISPLAY), "gui": gui}})
        write_json(os.path.join(assets, "items", iid + ".json"), {"model": mesh})
    # (the old flat slot picture's model and a gun's rarity layer for it: nothing uses them now)
    for old in (os.path.join(assets, "models/item", iid + "_icon.json"), os.path.join(assets, "textures/item", iid + "_rarity.png")):
        if os.path.exists(old):
            os.remove(old)
    return gui


def gui_only(res):
    """--gui: every Blender item already in the game (its items/*.json draws a mesh), from the mesh's own bounds."""
    assets = os.path.join(res, "assets", "minebloxroyale")
    ids = ["pickaxe"]
    for f in sorted(os.listdir(os.path.join(assets, "items"))):
        if '"minebloxroyale:mesh"' in open(os.path.join(assets, "items", f), encoding="utf-8").read():
            ids.append(f[:-5])
    for iid in ids:
        lo, hi = mesh_bounds(os.path.join(assets, "meshes", iid + ".brm"))
        gui = write_files(res, iid, lo, hi)
        print(f"{iid}: rotation {gui['rotation']}, scale {gui['scale'][0]}, translation {gui['translation']}")


if __name__ == "__main__":
    if sys.argv[1] == "--gui":
        gui_only(sys.argv[2])
    else:
        main()
