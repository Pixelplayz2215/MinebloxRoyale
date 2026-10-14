"""
The game's item files for the Blender items (after build.py items): python make_items.py <manifest_items.json> <resources dir>

  assets/minebloxroyale/items/<id>.json         the inventory picture in the inventory, the Blender mesh everywhere else
  assets/minebloxroyale/models/item/<id>.json   the mesh's hold in each hand (display), no geometry of its own
  assets/minebloxroyale/models/item/<id>_icon.json, textures/item/<id>.png
                                                the inventory picture: Blender's render at 32 x 32 with a dark outline;
                                                guns also get <id>_rarity.png, their body in the rarity colour
  assets/minecraft/items/iron_pickaxe.json      the harvesting pickaxe (an iron pickaxe) as the Blender pickaxe
Also the reboot card's flat picture.
"""
import json
import os
import sys

from PIL import Image, ImageDraw

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


def reboot_card(assets):
    """The reboot card: a light blue card with a portrait and a chip (32 x 32), held flat like a normal item."""
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((5, 3, 26, 28), radius=3, fill="#101418")
    d.rounded_rectangle((6, 4, 25, 27), radius=2, fill="#5CE1FF")
    d.rectangle((6, 4, 25, 8), fill="#2F9FD8")
    d.rectangle((9, 11, 22, 22), fill="#F4F8FF")
    d.ellipse((13, 12, 18, 17), fill="#2A3442")
    d.rectangle((11, 18, 20, 22), fill="#2A3442")
    d.rectangle((9, 24, 13, 26), fill="#FFD21F")
    d.rectangle((15, 24, 22, 25), fill="#2F9FD8")
    d.line((7, 5, 12, 5), fill="#C8F4FF")
    img.save(os.path.join(assets, "textures/item/reboot_card.png"))
    write_json(os.path.join(assets, "models/item/reboot_card.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "minebloxroyale:item/reboot_card"}})
    write_json(os.path.join(assets, "items/reboot_card.json"), {"model": {"type": "minecraft:model", "model": "minebloxroyale:item/reboot_card"}})


def main():
    manifest = json.load(open(sys.argv[1], encoding="utf-8"))
    res = sys.argv[2]
    assets = os.path.join(res, "assets", "minebloxroyale")
    for e in manifest:
        iid = e["id"]
        gun = iid.startswith("gun_")
        pick = iid == "pickaxe"
        icon, box, s = small(e["icon"])
        icon = outline(icon)
        icon.save(os.path.join(assets, "textures/item", iid + ".png"))
        layers = {"layer0": "minebloxroyale:item/" + iid}
        if gun and e.get("icon_mask"):
            # The rarity layer: the icon's own shading where the body shows, tinted in the game.
            m = Image.open(e["icon_mask"]).convert("RGBA").crop(box)
            m = m.resize((max(1, round(m.width * s)), max(1, round(m.height * s))), Image.LANCZOS)
            mask = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
            mask.paste(m, ((32 - m.width) // 2, (32 - m.height) // 2))
            mp, ip = mask.load(), icon.load()
            for y in range(32):
                for x in range(32):
                    r = mp[x, y][0]
                    if mp[x, y][3] > 110 and r > 128 and ip[x, y][3]:
                        v = max(ip[x, y][:3])
                        mp[x, y] = (v, v, v, 255)
                    else:
                        mp[x, y] = (0, 0, 0, 0)
            mask.save(os.path.join(assets, "textures/item", iid + "_rarity.png"))
            layers["layer1"] = "minebloxroyale:item/" + iid + "_rarity"
        write_json(os.path.join(assets, "models/item", iid + "_icon.json"), {"parent": "minecraft:item/generated", "textures": layers})
        icon_model = {"type": "minecraft:model", "model": "minebloxroyale:item/" + iid + "_icon"}
        mesh = {"type": "minecraft:special", "base": "minebloxroyale:item/" + iid, "model": {"type": "minebloxroyale:mesh", "mesh": iid}}
        if gun:
            icon_model["tints"] = [{"type": "minecraft:constant", "value": 0xFFFFFF}, {"type": "minecraft:custom_model_data", "index": 0, "default": COMMON}]
            mesh["model"]["tint"] = COMMON
        if pick:
            write_json(os.path.join(assets, "models/item/pickaxe.json"), {"parent": "minecraft:item/handheld", "textures": {"layer0": "minebloxroyale:item/pickaxe"}})
        else:
            write_json(os.path.join(assets, "models/item", iid + ".json"),
                       {"textures": {"particle": "minebloxroyale:item/" + iid}, "display": GUN_DISPLAY if gun else ITEM_DISPLAY})
        item = {"model": {"type": "minecraft:select", "property": "minecraft:display_context", "cases": [{"when": ["gui"], "model": icon_model}], "fallback": mesh}}
        if pick:
            write_json(os.path.join(res, "assets", "minecraft", "items", "iron_pickaxe.json"), item)
        else:
            write_json(os.path.join(assets, "items", iid + ".json"), item)
    reboot_card(assets)
    print("wrote", len(manifest), "items")


if __name__ == "__main__":
    main()
