"""Makes the Fortnite-style loot chests (creator): textures, block models (closed and open) and blockstates.

loot_chest: warm wooden planks with gold bands, corners, rim and lock, glowing gold inside when open.
rare_chest: the blue special chest: blue painted planks with silver trim, glowing pale blue inside.
Front faces north (-Z) in the models; the blockstate turns them to face the chest's FACING.

Run: python fabric/tools/models/make_chest_models.py   (writes into fabric/src/main/resources/assets/minebloxroyale)
"""
import json
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources", "assets", "minebloxroyale")

# Texture layout (32 x 32 px; model UVs are 0..16, so 1 UV unit = 2 px):
REG = {
    "wood": (0, 0, 8, 8),
    "trim": (8, 0, 16, 4),
    "inside": (8, 4, 16, 8),
    "dark": (0, 8, 8, 16),
    "lock": (8, 8, 16, 12),
    "glow": (8, 12, 16, 16),
}

PALETTES = {
    "loot_chest": {
        "plank": (156, 98, 46), "plank_hi": (184, 122, 62), "seam": (84, 50, 22), "grain": (128, 78, 34),
        "trim_top": (255, 226, 110), "trim_mid": (238, 182, 46), "trim_low": (178, 118, 22), "rivet": (255, 246, 190),
        "inside": (44, 26, 12), "inside_glow": (255, 196, 70),
        "dark": (96, 58, 26), "dark_seam": (62, 36, 14),
        "lock": (230, 170, 40), "lock_edge": (150, 96, 16),
        "glow_mid": (255, 244, 170), "glow_edge": (255, 186, 60),
    },
    "rare_chest": {
        "plank": (44, 112, 214), "plank_hi": (88, 158, 242), "seam": (18, 54, 128), "grain": (34, 92, 186),
        "trim_top": (244, 250, 255), "trim_mid": (200, 214, 230), "trim_low": (128, 146, 170), "rivet": (255, 255, 255),
        "inside": (12, 22, 48), "inside_glow": (90, 200, 255),
        "dark": (22, 48, 104), "dark_seam": (12, 28, 66),
        "lock": (214, 226, 240), "lock_edge": (118, 136, 160),
        "glow_mid": (210, 245, 255), "glow_edge": (80, 180, 255),
    },
}


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def texture(p):
    rnd = random.Random(7)
    img = Image.new("RGBA", (32, 32))
    px = img.load()
    # Planks: four boards 4 px tall, a dark seam under each, grain specks, a lighter top edge.
    for y in range(16):
        for x in range(16):
            row = y % 4
            c = p["seam"] if row == 3 else p["plank_hi"] if row == 0 else p["plank"]
            if row in (1, 2) and rnd.random() < 0.18:
                c = p["grain"]
            if (x + (y // 4) * 5) % 16 == 0 and row != 3:  # board ends
                c = p["seam"]
            px[x, y] = c + (255,)
    # Trim (16..32 x 0..8): bright top, a bevel, a darker base, rivets.
    for y in range(8):
        for x in range(16, 32):
            t = y / 7
            c = mix(p["trim_top"], p["trim_mid"], t * 2) if t < 0.5 else mix(p["trim_mid"], p["trim_low"], (t - 0.5) * 2)
            if y in (3, 4) and x % 5 == 2:
                c = p["rivet"]
            px[x, y] = c + (255,)
    # Inside (16..32 x 8..16): dark, glowing towards the middle.
    for y in range(8, 16):
        for x in range(16, 32):
            d = ((x - 23.5) ** 2 / 64 + (y - 11.5) ** 2 / 16) ** 0.5
            px[x, y] = mix(p["inside_glow"], p["inside"], min(1, d)) + (255,)
    # Dark wood (0..16 x 16..32).
    for y in range(16, 32):
        for x in range(16):
            px[x, y] = (p["dark_seam"] if y % 4 == 3 else p["dark"]) + (255,)
    # Lock plate (16..32 x 16..24): plate with a darker rim.
    for y in range(16, 24):
        for x in range(16, 32):
            edge = x in (16, 31) or y in (16, 23)
            px[x, y] = (p["lock_edge"] if edge else mix(p["lock"], p["trim_top"], 0.3 if y < 19 else 0)) + (255,)
    # Glow (16..32 x 24..32).
    for y in range(24, 32):
        for x in range(16, 32):
            d = ((x - 23.5) ** 2 / 64 + (y - 27.5) ** 2 / 16) ** 0.5
            px[x, y] = mix(p["glow_mid"], p["glow_edge"], min(1, d)) + (255,)
    return img


def box(frm, to, reg, faces=None, rot=None, light=0, regs=None):
    """A cuboid; every face shows region `reg` (regs: per-face overrides)."""
    e = {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to], "faces": {}}
    for f in faces or ("north", "south", "east", "west", "up", "down"):
        r = (regs or {}).get(f, reg)
        e["faces"][f] = {"uv": list(REG[r]), "texture": "#chest"}
    if rot:
        e["rotation"] = rot
    if light:
        e["light_emission"] = light
    return e


def chest(open_):
    BODY_Z0, BODY_Z1 = 3.5, 12.5
    hinge = {"origin": [8, 8, BODY_Z1], "axis": "x", "angle": 105} if open_ else None
    el = []
    # The body: wooden box on a dark plinth, gold posts at its corners. Open, its top shows the glowing inside.
    el.append(box((1, 1, BODY_Z0), (15, 8, BODY_Z1), "wood", regs={"up": "inside"}))
    el.append(box((0.6, 0, BODY_Z0 - 0.4), (15.4, 1, BODY_Z1 + 0.4), "dark"))
    # (Faces never share a plane with another face: two at the same depth flicker as the camera moves, creator's video
    # of the open lid. So the posts stop just under the body's top and have no underside (they stand on the plinth,
    # level with the body's underside), and the lid's rim and lock have no underside.)
    for x0 in (0.7, 14.5):
        for z0 in (BODY_Z0 - 0.3, BODY_Z1 - 0.5):
            el.append(box((x0, 1, z0), (x0 + 0.8, 7.94, z0 + 0.8), "trim", faces=("north", "south", "east", "west", "up")))
    # Bands round the body near each end.
    for x0 in (2.2, 12.2):
        el.append(box((x0, 1, BODY_Z0 - 0.25), (x0 + 1.6, 8, BODY_Z1 + 0.25), "trim", faces=("north", "south", "east", "west")))
    # The lower half of the lock (on the body).
    el.append(box((6.6, 5.2, BODY_Z0 - 0.5), (9.4, 8, BODY_Z0), "lock"))
    el.append(box((7.6, 6.0, BODY_Z0 - 0.6), (8.4, 7.6, BODY_Z0 - 0.5), "inside"))
    if open_:
        # Treasure glowing on the floor of the open chest (lights it up from inside).
        el.append(box((2, 8.06, BODY_Z0 + 1), (14, 8.2, BODY_Z1 - 1), "glow", faces=("north", "south", "east", "west", "up"), light=14))
    # The lid: a slab with a rounded top, gold rim and bands, the top of the lock. Open, it's flipped back on its hinge.
    lid = [
        box((1, 8, BODY_Z0), (15, 10.6, BODY_Z1), "wood", regs={"down": "inside"}),
        box((1, 10.6, BODY_Z0 + 0.7), (15, 11.7, BODY_Z1 - 0.7), "wood"),
        box((1, 11.7, BODY_Z0 + 2.0), (15, 12.3, BODY_Z1 - 2.0), "wood"),
        box((0.8, 8, BODY_Z0 - 0.25), (15.2, 8.7, BODY_Z1 + 0.25), "trim", faces=("north", "south", "east", "west", "up")),
        box((6.6, 8.02, BODY_Z0 - 0.5), (9.4, 9.7, BODY_Z0), "lock", faces=("north", "south", "east", "west", "up")),
    ]
    for x0 in (2.2, 12.2):
        lid.append(box((x0, 8.7, BODY_Z0 - 0.25), (x0 + 1.6, 10.7, BODY_Z1 + 0.25), "trim", faces=("north", "south", "east", "west", "up")))
        lid.append(box((x0, 10.7, BODY_Z0 + 0.45), (x0 + 1.6, 11.8, BODY_Z1 - 0.45), "trim", faces=("north", "south", "east", "west", "up")))
        lid.append(box((x0, 11.8, BODY_Z0 + 1.75), (x0 + 1.6, 12.45, BODY_Z1 - 1.75), "trim"))
    if hinge:
        for e in lid:
            e["rotation"] = dict(hinge)
    el += lid
    return {"parent": "minecraft:block/block", "textures": {"particle": "#chest"}, "elements": el}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=1)


def main():
    for state in ("closed", "open"):
        write(os.path.join(ROOT, "models", "block", f"chest_{state}.json"), chest(state == "open"))
    for name, pal in PALETTES.items():
        os.makedirs(os.path.join(ROOT, "textures", "block"), exist_ok=True)
        texture(pal).save(os.path.join(ROOT, "textures", "block", name + ".png"))
        tex = f"minebloxroyale:block/{name}"
        for state in ("closed", "open"):
            write(os.path.join(ROOT, "models", "block", f"{name}_{state}.json"),
                  {"parent": f"minebloxroyale:block/chest_{state}", "textures": {"chest": tex, "particle": tex}})
        variants = {}
        for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            for state, open_ in (("closed", "false"), ("open", "true")):
                v = {"model": f"minebloxroyale:block/{name}_{state}"}
                if y:
                    v["y"] = y
                variants[f"facing={facing},open={open_}"] = v
        write(os.path.join(ROOT, "blockstates", name + ".json"), {"variants": variants})
    print("chest models written")


if __name__ == "__main__":
    main()
