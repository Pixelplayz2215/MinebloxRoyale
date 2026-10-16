"""The launcher badges by players' names (2.0.0-test6.2, creator: "Icons next to players names based on the launcher
they're using ... If launched via Melty's prism then show Melty icon"): one small badge per launcher, drawn here (our own
designs in each launcher's colours with a simple symbol or its initials, not the launchers' logos), as a bitmap font so
the name tag can carry one as a character (assets/minebloxroyale/font/launchers.json: U+E000 on, in BADGES' order;
LauncherBadge.java has the same order).

  python fabric/tools/branding/make_launcher_badges.py [preview.png]
"""
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
ASSETS = os.path.join(ROOT, "fabric", "src", "client", "resources", "assets", "minebloxroyale")
S = 256  # (drawn this big, then shrunk)
CELL = 32  # (each badge in the font's picture)
F = lambda n: ImageFont.truetype("C:/Windows/Fonts/bahnschrift.ttf", n)
WHITE, INK = (255, 255, 255, 255), (24, 20, 32, 255)

# id, name shown in the preview, the badge's colours
BADGES = [
    ("melty", "Melty", (255, 122, 69), (232, 61, 132)),
    ("prism", "Prism Launcher", (38, 44, 72), (24, 28, 48)),
    ("multimc", "MultiMC", (66, 74, 88), (44, 50, 60)),
    ("modrinth", "Modrinth App", (27, 217, 106), (14, 160, 76)),
    ("lunar", "Lunar Client", (40, 70, 160), (18, 30, 84)),
    ("curseforge", "CurseForge", (241, 100, 54), (196, 64, 28)),
    ("atlauncher", "ATLauncher", (32, 168, 168), (18, 112, 120)),
    ("gdlauncher", "GDLauncher", (128, 76, 220), (82, 44, 160)),
    ("minecraft", "Minecraft Launcher", (92, 160, 64), (60, 112, 40)),
    ("dev", "Development (Dev)", (255, 200, 40), (214, 150, 20)),
]


def base(top, bottom, round_=True):
    """A badge: a disc (or rounded square) shaded from top to bottom, with a thin dark rim."""
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    grad = Image.new("RGBA", (S, S))
    gd = ImageDraw.Draw(grad)
    for y in range(S):
        f = y / (S - 1)
        gd.line([(0, y), (S, y)], fill=tuple(int(top[i] + (bottom[i] - top[i]) * f) for i in range(3)) + (255,))
    mask = Image.new("L", (S, S), 0)
    md = ImageDraw.Draw(mask)
    if round_:
        md.ellipse((8, 8, S - 8, S - 8), fill=255)
    else:
        md.rounded_rectangle((8, 8, S - 8, S - 8), radius=56, fill=255)
    rim = Image.new("L", (S, S), 0)
    rd = ImageDraw.Draw(rim)
    if round_:
        rd.ellipse((0, 0, S, S), fill=255)
    else:
        rd.rounded_rectangle((0, 0, S, S), radius=64, fill=255)
    im.paste((18, 14, 26, 255), (0, 0), rim)
    im.paste(grad, (0, 0), mask)
    return im, ImageDraw.Draw(im)


def letters(d, text, size, color=WHITE, dy=0):
    d.text((S / 2, S / 2 + dy), text, font=F(size), fill=color, anchor="mm", stroke_width=0)


def badge(bid, top, bottom):
    if bid == "melty":  # a warm drop, melting
        im, d = base(top, bottom)
        pts = []
        for i in range(64):
            a = math.pi * 2 * i / 64
            r = 62
            x, y = math.cos(a) * r, math.sin(a) * r
            if y < 0:  # (the top drawn up into a point)
                k = -y / r
                x *= 1 - k * k * 0.95
                y *= 1 + k * 0.9
            pts.append((S / 2 + x, S / 2 + 18 + y))
        d.polygon(pts, fill=WHITE)
        d.ellipse((S / 2 - 30, S / 2 + 6, S / 2 - 6, S / 2 + 30), fill=(255, 205, 180, 255))
    elif bid == "prism":  # a triangle with a fan of colours out of it
        im, d = base(top, bottom, round_=False)
        cols = [(255, 72, 72), (255, 160, 40), (255, 230, 60), (80, 210, 90), (60, 150, 255), (150, 90, 240)]
        for i, c in enumerate(cols):
            y0 = 112 + i * 12
            d.polygon([(150, 128), (226, y0 - 6), (226, y0 + 6)], fill=c + (255,))
        d.polygon([(64, 184), (128, 70), (192, 184)], fill=WHITE)
        d.polygon([(30, 132), (112, 128), (110, 136)], fill=(255, 255, 255, 220))
    elif bid == "multimc":
        im, d = base(top, bottom, round_=False)
        letters(d, "MMC", 82)
    elif bid == "modrinth":
        im, d = base(top, bottom)
        letters(d, "M", 150, INK, dy=-4)
    elif bid == "lunar":  # a crescent moon
        im, d = base(top, bottom)
        moon = Image.new("L", (S, S), 0)
        md = ImageDraw.Draw(moon)
        md.ellipse((54, 50, 202, 198), fill=255)
        md.ellipse((96, 30, 232, 166), fill=0)
        im.paste(WHITE, (0, 0), moon)
        for x, y, r in ((176, 176, 7), (196, 128, 5)):
            d.ellipse((x - r, y - r, x + r, y + r), fill=WHITE)
    elif bid == "curseforge":
        im, d = base(top, bottom, round_=False)
        letters(d, "CF", 118)
    elif bid == "atlauncher":
        im, d = base(top, bottom)
        letters(d, "AT", 112)
    elif bid == "gdlauncher":
        im, d = base(top, bottom)
        letters(d, "GD", 112)
    elif bid == "minecraft":  # a grass block (as the Mineblox Royale icon draws it)
        im, d = base((60, 66, 80), (36, 40, 52), round_=False)
        c, h = (S / 2, 64), 62
        top_ = [(c[0], c[1]), (c[0] + 74, c[1] + 40), (c[0], c[1] + 80), (c[0] - 74, c[1] + 40)]
        d.polygon(top_, fill=(104, 196, 64, 255), outline=INK)
        d.polygon([(c[0] - 74, c[1] + 40), (c[0], c[1] + 80), (c[0], c[1] + 80 + h + 30), (c[0] - 74, c[1] + 40 + h + 30)], fill=(150, 98, 52, 255), outline=INK)
        d.polygon([(c[0] + 74, c[1] + 40), (c[0], c[1] + 80), (c[0], c[1] + 80 + h + 30), (c[0] + 74, c[1] + 40 + h + 30)], fill=(112, 70, 36, 255), outline=INK)
        d.polygon([(c[0] - 74, c[1] + 40), (c[0], c[1] + 80), (c[0], c[1] + 104), (c[0] - 74, c[1] + 64)], fill=(74, 158, 46, 255))
        d.polygon([(c[0] + 74, c[1] + 40), (c[0], c[1] + 80), (c[0], c[1] + 104), (c[0] + 74, c[1] + 64)], fill=(62, 140, 40, 255))
    else:  # dev: a wrench
        im, d = base(top, bottom)
        w = Image.new("L", (S, S), 0)
        wd = ImageDraw.Draw(w)
        wd.line([(88, 168), (160, 96)], fill=255, width=34)
        wd.ellipse((140, 52, 212, 124), fill=255)
        wd.ellipse((164, 50, 200, 86), fill=0)
        wd.ellipse((64, 148, 108, 192), fill=255)
        im.paste(INK, (0, 0), w)
    return im


def main():
    pics = [badge(b[0], b[2], b[3]) for b in BADGES]
    atlas = Image.new("RGBA", (CELL * len(pics), CELL), (0, 0, 0, 0))
    for i, p in enumerate(pics):
        atlas.paste(p.resize((CELL, CELL), Image.LANCZOS), (i * CELL, 0))
    os.makedirs(os.path.join(ASSETS, "textures", "font"), exist_ok=True)
    atlas.save(os.path.join(ASSETS, "textures", "font", "launchers.png"))
    os.makedirs(os.path.join(ASSETS, "font"), exist_ok=True)
    chars = "".join(chr(0xE000 + i) for i in range(len(pics)))
    import json
    json.dump({"providers": [{"type": "bitmap", "file": "minebloxroyale:font/launchers.png", "ascent": 8, "height": 9, "chars": [chars]}]},
              open(os.path.join(ASSETS, "font", "launchers.json"), "w", encoding="utf-8"), indent=1)
    if len(sys.argv) > 1:
        preview(pics, sys.argv[1])
    print(len(pics), "badges")


def preview(pics, out):
    """Each badge big with its launcher's name, and over a player's head in name tags (as the game draws them)."""
    W = 1500
    im = Image.new("RGB", (W, 820), (24, 27, 34))
    d = ImageDraw.Draw(im)
    d.text((30, 24), "Launcher badges by players' names", font=F(42), fill=(255, 200, 61))
    d.text((30, 76), "Our own designs in each launcher's colours (not their logos). Shown in everyone's name tag.", font=F(20), fill=(154, 163, 178))
    for i, (p, b) in enumerate(zip(pics, BADGES)):
        x, y = 30 + (i % 5) * 292, 124 + (i // 5) * 230
        d.rounded_rectangle((x, y, x + 272, y + 210), radius=14, fill=(44, 49, 61))
        im.paste(p.resize((120, 120), Image.LANCZOS), (x + 76, y + 16), p.resize((120, 120), Image.LANCZOS))
        d.text((x + 136, y + 160), b[1], font=F(22), fill=(240, 243, 248), anchor="mm")
        small = p.resize((18, 18), Image.LANCZOS)
        im.paste(small, (x + 112, y + 180), small)
        d.text((x + 134, y + 189), "(in game)", font=F(14), fill=(154, 163, 178), anchor="lm")
    # name tags over a scene
    y0 = 600
    sky = Image.new("RGB", (W - 60, 190), (120, 166, 220))
    ImageDraw.Draw(sky).rectangle((0, 120, W - 60, 190), fill=(96, 150, 70))
    im.paste(sky, (30, y0))
    tags = [("MinebloxDev", 9), ("creaper89O1", 0), ("Builderman", 3), ("NoobSlayer", 4), ("PrismPlayer", 1)]
    for k, (name, bi) in enumerate(tags):
        cx = 130 + k * 290
        fnt = F(30)
        tw = d.textlength(name, font=fnt)
        bw = 36
        total = bw + 10 + tw
        x = cx - total / 2 + 60
        tag = Image.new("RGBA", (int(total + 24), 48), (0, 0, 0, 0))
        ImageDraw.Draw(tag).rectangle((0, 0, int(total + 24), 48), fill=(0, 0, 0, 100))
        im.paste(tag, (int(x - 12), y0 + 28), tag)
        b = pics[bi].resize((bw, bw), Image.LANCZOS)
        im.paste(b, (int(x), y0 + 34), b)
        d.text((x + bw + 10, y0 + 52), name, font=fnt, fill=(255, 255, 255), anchor="lm")
        # a little avatar under the tag
        d.rectangle((cx + 40, y0 + 92, cx + 80, y0 + 132), fill=(245, 205, 48))
        d.rectangle((cx + 30, y0 + 132, cx + 90, y0 + 190), fill=(13, 105, 172))
    im.save(out)


if __name__ == "__main__":
    main()
