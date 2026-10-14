"""One picture per Locker item: its Front, Left, Right and Back views side by side, with the item's name on it, from
the game's Locker audit screenshots (LockerAudit: minebloxroyale/audit_locker.txt in the dev game folder).

  python locker_pics.py <audit dir> <Model Renders\\Locker Screenshots dir> [subfolder prefix] [tabs, e.g. preset,shirt,pants,shoes] [update]

Pictures go to "<NN> <Tab>\\<prefix> <Tab>" (for example "06 Shirts\\New Shirts") when a prefix is given, or straight
into "<NN> <Tab>" otherwise, named "001 Shirts - Red Tee.png" by the item's place in the Locker. Only the folders being
written are cleared first ("update": nothing is cleared, only the items in the audit are redone); everything else in
Model Renders\\Locker Screenshots stays as it is.
"""
import collections
import json
import os
import re
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
src, out = sys.argv[1], sys.argv[2]
prefix = sys.argv[3] if len(sys.argv) > 3 else ""
only = sys.argv[4].split(",") if len(sys.argv) > 4 and sys.argv[4] else None
update = len(sys.argv) > 5 and sys.argv[5] == "update"
TABS = [("preset", "Presets"), ("hat", "Hats"), ("hair", "Hair"), ("face", "Face"), ("back", "Back"), ("shirt", "Shirts"),
        ("pants", "Pants"), ("shoes", "Shoes"), ("particle", "Particles"), ("glider", "Gliders")]
VIEWS = {"front": "Front", "left": "Left", "right": "Right", "back": "Back"}

names, place = {}, collections.defaultdict(list)
for sheet in ("cosmetics", "presets", "gliders"):
    for r in json.load(open(os.path.join(ROOT, "design", sheet + ".json"), encoding="utf-8"))["rows"]:
        names[r["id"]] = r["name"]
        place[r.get("slot", sheet[:-1] if sheet != "presets" else "preset")].append(r["id"])  # Locker order per tab

# The GUI scale and window size the screenshots were taken at (LockerAudit writes done.txt at the end).
scale, width, height = 3.0, 2560, 1440
done = os.path.join(src, "done.txt")
if os.path.isfile(done):
    t = open(done).read()
    scale = float(re.search(r"guiScale=([\d.]+)", t).group(1))
    width = int(re.search(r"width=(\d+)", t).group(1))
    height = int(re.search(r"height=(\d+)", t).group(1))

shots = collections.defaultdict(collections.OrderedDict)
order = collections.defaultdict(list)
for f in sorted(os.listdir(src)):
    m = re.match(r"(\d+)_([a-z]+)_(.+)_(\d)(front|left|right|back)\.png$", f)
    if not m:
        continue
    tab, item, view = m.group(2), m.group(3), m.group(5)
    if item not in shots[tab]:
        order[tab].append(item)
    shots[tab].setdefault(item, {})[view] = f  # (particles: the last of its shots wins - it has had time to fill up)


def crop_box(tab):
    """The part of the screenshot around the Locker's preview of you (LockerScreen: the box from x = 370 to width - 20,
    y = 30 to height - 20 in GUI units; the avatar is sized by the box's height)."""
    gw, gh = width / scale, height / scale
    cx, cy, bh = (370 + gw - 20) / 2, (30 + gh - 20) / 2, gh - 50
    # Back bling is wider (wings) and taller (balloons) than an outfit: a bigger square, a little higher.
    hw, hh = (0.426, 0.527) if tab == "glider" else (0.43, 0.43) if tab == "back" else (0.318, 0.395)
    dy = -5 if tab == "glider" else -21.7 if tab == "back" else 1.7
    return tuple(int(v * scale) for v in (cx - hw * bh, cy + dy - hh * bh, cx + hw * bh, cy + dy + hh * bh))


font = ImageFont.truetype("C:/Windows/Fonts/arialbd.ttf", 64)
small = ImageFont.truetype("C:/Windows/Fonts/arialbd.ttf", 40)
W, TOP, LAB = 820, 110, 60
made = 0
for ti, (tab, tab_name) in enumerate(TABS):
    if tab not in shots or only and tab not in only:
        continue
    folder = os.path.join(out, f"{ti + 1:02d} {tab_name}")
    if prefix:
        folder = os.path.join(folder, f"{prefix} {tab_name}")
    os.makedirs(folder, exist_ok=True)
    if not update:
        for f in os.listdir(folder):
            if f.lower().endswith(".png"):
                os.remove(os.path.join(folder, f))
    box = crop_box(tab)
    H = round(W * (box[3] - box[1]) / (box[2] - box[0]))
    for n, item in enumerate(order[tab]):
        views = [v for v in VIEWS if v in shots[tab][item]]
        sheet = Image.new("RGB", (W * len(views), TOP + LAB + H), (24, 26, 34))
        d = ImageDraw.Draw(sheet)
        name = names.get(item, item)
        title, size = f"{tab_name}: {name}", 64
        while size > 30 and d.textlength(title, font=ImageFont.truetype("C:/Windows/Fonts/arialbd.ttf", size)) > sheet.width - 40:
            size -= 2  # (one-view pictures are narrow: long names shrink to fit)
        d.text((sheet.width // 2, TOP // 2), title, font=ImageFont.truetype("C:/Windows/Fonts/arialbd.ttf", size), fill=(255, 212, 42), anchor="mm")
        for j, v in enumerate(views):
            im = Image.open(os.path.join(src, shots[tab][item][v])).convert("RGB").crop(box).resize((W, H), Image.LANCZOS)
            sheet.paste(im, (j * W, TOP + LAB))
            d.text((j * W + W // 2, TOP + LAB // 2), VIEWS[v], font=small, fill=(220, 226, 236), anchor="mm")
        safe = re.sub(r'[\\/:*?"<>|]', "", name)
        num = place[tab].index(item) + 1 if item in place[tab] else n + 1
        sheet.save(os.path.join(folder, f"{num:03d} {tab_name} - {safe}.png"))
        made += 1
    print(tab_name, len(order[tab]), "pictures in", folder)
print(made, "pictures")
