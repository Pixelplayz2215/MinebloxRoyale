"""Glider picture cards (Model Renders\\Locker Screenshots\\10 Gliders): one picture per glider, four angles, with its name and what
unlocks it (creator: every glider picture says what's needed to unlock it).

  python glider_cards.py header <folder>            redraws the header (name and unlock badge) of the rendered_<name>.png
                                                    and in_game_<name>.png pictures in <folder>
  python glider_cards.py in_game <audit> <folder>   makes in_game_<name>.png from the game's Locker audit screenshots

<audit> is the game's minebloxroyale/audit folder after a glider run (minebloxroyale/audit_locker.txt saying "glider",
LockerAudit): 0000_glider_<id>_1front.png, _2left, _3right, _4back, and done.txt with the GUI scale.
"""
import json
import os
import re
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
GLIDERS = json.load(open(os.path.join(ROOT, "design", "gliders.json"), encoding="utf-8"))["rows"]
FONTS = "C:/Windows/Fonts/"
TITLE = ImageFont.truetype(FONTS + "segoeuib.ttf", 40)
BADGE = ImageFont.truetype(FONTS + "segoeuib.ttf", 21)
LABEL = ImageFont.truetype(FONTS + "segoeuib.ttf", 20)
SMALL = ImageFont.truetype(FONTS + "segoeui.ttf", 17)
BG, PANEL, TEXT, SUB = (34, 37, 44), (58, 64, 76), (240, 242, 246), (160, 168, 182)
GOLD, DARK = (255, 212, 42), (30, 26, 10)
FREE, FREE_TEXT = (88, 150, 96), (240, 255, 240)
W, PW, TOP = 980, 455, 104


def unlock(g):
    """What unlocks a glider, as the Locker's hover line says it."""
    need = g.get("need", 0)
    return {
        "default": "Free: everyone has it",
        "victory": "To unlock: win a Victory Royale",
        "obby": "To unlock: finish the obby",
        "kills": f"To unlock: get {need} eliminations",
        "wins": f"To unlock: win {need} Victory Royales",
        "range": f"To unlock: score {need} in one range round",
        "bullseyes": f"To unlock: hit {need} range bullseyes",
    }[g["unlock"]]


def header(img, g, source):
    """Name, the unlock badge, and a line about the glider, across the top TOP pixels."""
    d = ImageDraw.Draw(img)
    d.rectangle((0, 0, img.width, TOP - 4), fill=BG)
    d.text((24, 18), g["name"], font=TITLE, fill=TEXT)
    free = g["unlock"] == "default"
    text = unlock(g)
    tw = d.textlength(text, font=BADGE)
    x1 = img.width - 24
    x0 = x1 - tw - 32
    d.rounded_rectangle((x0, 22, x1, 64), radius=21, fill=FREE if free else GOLD)
    d.text(((x0 + x1) / 2, 43), text, font=BADGE, fill=FREE_TEXT if free else DARK, anchor="mm")
    deco = g.get("deco", "none")
    shape = g["shape"].capitalize() + ("" if deco == "none" else " with " + deco)
    if g["shape"] == "dominus":  # one for each Dominus hat in the Locker
        shape = f"Feathered wings, matches the Dominus {deco.capitalize()} hat"
    parts = [shape, source, "Mineblox Royale test9", g["id"]]
    while len(parts) > 2 and d.textlength("  |  ".join(parts), font=SMALL) > img.width - 48:
        parts.pop()  # (long lines lose the id, then the build)
    d.text((24, 72), "  |  ".join(parts), font=SMALL, fill=SUB)


def redo_headers(folder):
    for g in GLIDERS:
        for prefix, source in (("rendered_", "offline render"), ("in_game_", "in-game Locker")):
            path = os.path.join(folder, f"{prefix}{g['name']}.png")
            if not os.path.isfile(path):
                continue
            img = Image.open(path).convert("RGB")
            header(img, g, source)
            img.save(path)
            print("ok", os.path.basename(path))


def in_game(audit, folder):
    os.makedirs(folder, exist_ok=True)
    scale = 1
    done = os.path.join(audit, "done.txt")
    if os.path.isfile(done):
        m = re.search(r"guiScale=([\d.]+)", open(done).read())
        if m:
            scale = float(m.group(1))
    shots = {}
    for f in os.listdir(audit):
        m = re.match(r"\d+_glider_(.+)_(\d)(front|left|right|back)\.png$", f)
        if m:
            shots.setdefault(m.group(1), {})[m.group(3)] = os.path.join(audit, f)
    for g in GLIDERS:
        views = shots.get(g["id"], {})
        if len(views) < 4:
            print("missing shots for", g["id"], sorted(views))
            continue
        img = Image.new("RGB", (W, TOP + 2 * (PW + 66) - 16 + 40), BG)
        header(img, g, "in-game Locker")
        d = ImageDraw.Draw(img)
        for k, (view, label) in enumerate((("front", "Front"), ("back", "Back"), ("left", "Left side"), ("right", "Right side"))):
            x = 24 + (k % 2) * (PW + 22)
            y = TOP + (k // 2) * (PW + 66)
            d.rounded_rectangle((x, y, x + PW, y + PW + 50), radius=12, fill=PANEL)
            shot = Image.open(views[view]).convert("RGB")
            # A square around the Locker's preview of you (LockerScreen: the box from x = 370 to width - 20, y = 30 to
            # height - 20 in GUI units; the avatar is sized by the box's height). Sized from a 2560 x 1440 run: wide
            # enough for the kite's tail and tall enough for the balloons, and below the HUD text at the top.
            bx0, by0, bx1, by1 = 370 * scale, 30 * scale, shot.width - 20 * scale, shot.height - 20 * scale
            bh = by1 - by0
            half, cx, cy = 0.403 * bh, (bx0 + bx1) / 2, (by0 + by1) / 2 - 0.031 * bh
            crop = shot.crop((int(cx - half), int(cy - half), int(cx + half), int(cy + half)))
            crop = crop.resize((PW - 16, PW - 16), Image.LANCZOS)
            img.paste(crop, (x + 8, y + 8))
            d.text((x + PW // 2, y + PW + 28), label, font=LABEL, fill=TEXT, anchor="mm")
        d.text((24, img.height - 34), "Screenshots from the game's Locker (K), Gliders tab.", font=SMALL, fill=SUB)
        name = f"in_game_{g['name']}.png"
        img.save(os.path.join(folder, name))
        print("ok", name)


if __name__ == "__main__":
    if sys.argv[1] == "header":
        redo_headers(sys.argv[2])
    else:
        in_game(sys.argv[2], sys.argv[3])
