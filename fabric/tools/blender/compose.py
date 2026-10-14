"""
Labels the Blender renders (creator: "save images of all 4 sides, one item per image, with its name; if it needs to be
unlocked, its unlock condition too"). python compose.py <manifest.json> <out dir> [sub-folder per variant: yes|no]

Each picture: the name (and the variant, a rarity or a damage stage) on top, the unlock condition under it when the
entry has one, and the four sides in a 2 x 2 grid, each labelled.
"""
import json
import os
import sys

from PIL import Image, ImageDraw, ImageFont

FONT = "C:/Windows/Fonts/bahnschrift.ttf"
BG, PANEL, TEXT, SUB, LABEL = (34, 37, 46), (48, 52, 64), (245, 246, 250), (255, 205, 90), (170, 176, 190)
ORDER = ["Front", "Back", "Left", "Right"]


def font(size):
    try:
        return ImageFont.truetype(FONT, size)
    except OSError:
        return ImageFont.load_default()


def sheet(title, unlock, views, out):
    if len(views) == 1:  # one view (back bling: only its back): one big picture
        label, path = next(iter(views.items()))
        cell, pad = 900, 24
        top = 120 + (56 if unlock else 0)
        img = Image.new("RGB", (cell + 2 * pad, top + cell + 44 + pad), BG)
        d = ImageDraw.Draw(img)
        d.text((pad, 28), title, font=font(64), fill=TEXT)
        if unlock:
            d.text((pad, 112), "Unlock: " + unlock, font=font(40), fill=SUB)
        d.rounded_rectangle((pad, top, pad + cell, top + cell + 44), radius=18, fill=PANEL)
        v = Image.open(path).convert("RGBA").resize((cell, cell), Image.LANCZOS)
        img.paste(v, (pad, top), v)
        d.text((pad + cell / 2, top + cell + 22), label, font=font(30), fill=LABEL, anchor="mm")
        os.makedirs(os.path.dirname(out), exist_ok=True)
        img.save(out, optimize=True)
        return
    cell = 640
    pad = 24
    top = 120 + (56 if unlock else 0)
    w = pad + 2 * (cell + pad)
    h = top + 2 * (cell + 44 + pad)
    img = Image.new("RGB", (w, h), BG)
    d = ImageDraw.Draw(img)
    d.text((pad, 28), title, font=font(64), fill=TEXT)
    if unlock:
        d.text((pad, 112), "Unlock: " + unlock, font=font(40), fill=SUB)
    for i, label in enumerate(ORDER):
        if label not in views:
            continue
        x = pad + (i % 2) * (cell + pad)
        y = top + (i // 2) * (cell + 44 + pad)
        d.rounded_rectangle((x, y, x + cell, y + cell + 44), radius=18, fill=PANEL)
        v = Image.open(views[label]).convert("RGBA").resize((cell, cell), Image.LANCZOS)
        img.paste(v, (x, y), v)
        d.text((x + cell / 2, y + cell + 22), label, font=font(30), fill=LABEL, anchor="mm")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    img.save(out, optimize=True)


def safe(name):
    return "".join(c for c in name if c not in '<>:"/\\|?*').strip()


def main():
    manifest = json.load(open(sys.argv[1], encoding="utf-8"))
    out_dir = sys.argv[2]
    per_variant = len(sys.argv) > 3 and sys.argv[3] == "yes"
    n = 0
    for e in manifest:
        for v in e["variants"]:
            variant = v["variant"]
            title = f"{variant} {e['name']}" if e.get("variant_first") and variant else (f"{e['name']} - {variant}" if variant else e["name"])
            folder = os.path.join(out_dir, *[safe(p) for p in e.get("folder", "").split("/") if p], safe(e["name"]) if per_variant and len(e["variants"]) > 1 else "")
            sheet(title, e.get("unlock"), v["views"], os.path.join(folder, safe(title) + ".png"))
            n += 1
    print("wrote", n, "pictures to", out_dir)


if __name__ == "__main__":
    main()
