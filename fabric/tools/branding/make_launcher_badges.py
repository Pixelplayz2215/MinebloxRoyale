"""The launcher badges by players' names (2.0.0-test6.2, creator: "Icons next to players names based on the launcher
they're using ... If launched via Melty's prism then show Melty icon"): one small badge per launcher, drawn here (our own
designs in each launcher's colours, each a symbol for its name, none of them the launchers' logos: 2.0.0-test6.2, creator:
"create your own icon for each where players can easily notice what launcher it is without being confused OR infringing
copywrite"), as a bitmap font so
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
    ("melty", "Melty", (40, 156, 255), (1, 120, 232)),  # (Melty's own blue, 2.0.0-test6.2)
    ("prism", "Prism Launcher", (44, 48, 60), (30, 32, 42)),
    ("multimc", "MultiMC", (44, 46, 52), (28, 30, 34)),
    ("modrinth", "Modrinth App", (30, 36, 34), (16, 20, 18)),
    ("lunar", "Lunar Client", (34, 36, 46), (16, 17, 24)),
    ("curseforge", "CurseForge", (34, 32, 32), (18, 16, 16)),
    ("atlauncher", "ATLauncher", (48, 50, 54), (30, 32, 34)),
    ("gdlauncher", "GDLauncher", (58, 118, 196), (36, 86, 160)),
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
    if bid == "melty":  # Melty's style (creator: "make it similar to melty icon"): its blue, a rounded square, a chunky soft M
        im, d = base(top, bottom, round_=False)
        from PIL import ImageFilter
        m = Image.new("L", (S, S), 0)
        md = ImageDraw.Draw(m)
        x0, x1, y0, y1 = 64, 192, 66, 190  # (the M's box)
        md.polygon([(x0, y1), (x0, y0), (x0 + 34, y0), (128, y0 + 62), (x1 - 34, y0), (x1, y0), (x1, y1), (x1 - 36, y1), (x1 - 36, y0 + 64),
                    (128, y0 + 112), (x0 + 36, y0 + 64), (x0 + 36, y1)], fill=255)
        # (rounded off like Melty's letters: blurred and cut again, so its corners go soft)
        m = m.filter(ImageFilter.GaussianBlur(9)).point(lambda v: 255 if v > 118 else 0).filter(ImageFilter.GaussianBlur(1.2))
        im.paste(WHITE, (0, 0), m)
    elif bid == "prism":  # Prism's style: a hexagon of rainbow segments round a white cube
        im, d = base(top, bottom, round_=False)
        c, R = (S / 2, S / 2), 96
        hexp = [(c[0] + R * math.cos(math.radians(90 + 60 * k)), c[1] - R * math.sin(math.radians(90 + 60 * k))) for k in range(6)]
        cols = [(236, 92, 120), (250, 140, 80), (246, 210, 90), (110, 196, 150), (80, 140, 220), (150, 96, 200)]
        for k in range(6):  # (each side's triangle, then split in two shades)
            a, b = hexp[k], hexp[(k + 1) % 6]
            d.polygon([c, a, b], fill=cols[k] + (255,))
        r = 40  # (the white cube in the middle, three faces)
        cub = [(c[0] + r * math.cos(math.radians(90 + 60 * k)), c[1] - r * math.sin(math.radians(90 + 60 * k))) for k in range(6)]
        d.polygon([c, cub[0], cub[1], cub[2]], fill=(250, 250, 252, 255))
        d.polygon([c, cub[2], cub[3], cub[4]], fill=(214, 216, 222, 255))
        d.polygon([c, cub[4], cub[5], cub[0]], fill=(236, 238, 242, 255))
        d.polygon(hexp, outline=INK, width=6)
    elif bid == "multimc":  # MultiMC's style: a grass and dirt block with a gold infinity sign
        im, d = base(top, bottom, round_=False)
        x0, y0, x1, y1 = 44, 44, 212, 212
        d.rectangle((x0, y0, x1, y1), fill=(134, 92, 56, 255), outline=INK, width=6)
        d.rectangle((x0 + 3, y0 + 3, x1 - 3, y0 + 50), fill=(96, 168, 62, 255))
        for k in range(7):  # (grass hanging over the dirt)
            gx = x0 + 6 + k * 24
            d.rectangle((gx, y0 + 50, gx + 12, y0 + 50 + (14 if k % 2 else 26)), fill=(96, 168, 62, 255))
        for px, py in ((72, 160), (170, 176), (120, 196), (184, 128)):
            d.rectangle((px, py, px + 12, py + 12), fill=(104, 70, 40, 255))
        inf = Image.new("L", (S, S), 0)
        idr = ImageDraw.Draw(inf)
        lem = []  # (a figure-eight: the lemniscate)
        for k in range(121):
            t = 2 * math.pi * k / 120
            q = 1 + math.sin(t) ** 2
            lem.append((128 + 74 * math.cos(t) / q, 138 + 74 * math.sin(t) * math.cos(t) / q))
        idr.line(lem, fill=255, width=17, joint="curve")
        im.paste((255, 210, 70, 255), (0, 0), inf)
    elif bid == "modrinth":  # Modrinth's style: green labyrinth rings on dark
        im, d = base(top, bottom)
        c = S / 2
        G = (40, 220, 110, 255)
        for r, gap in ((90, 300), (62, 120), (34, 210)):
            d.arc((c - r, c - r, c + r, c + r), start=gap + 24, end=gap + 360 - 24, fill=G, width=15)
        d.line([(c + 34, c), (c + 62, c)], fill=G, width=15)
        d.line([(c, c - 62), (c, c - 90)], fill=G, width=15)
        d.line([(c - 62, c + 8), (c - 90, c + 8)], fill=G, width=15)
        d.ellipse((c - 13, c - 13, c + 13, c + 13), fill=G)
    elif bid == "lunar":  # Lunar's style: a crescent moon inside a house-shaped outline, on dark
        im, d = base(top, bottom, round_=False)
        house = [(128, 40), (210, 98), (210, 200), (46, 200), (46, 98)]
        d.line(house + [house[0]], fill=WHITE, width=12, joint="curve")
        moon = Image.new("L", (S, S), 0)
        md = ImageDraw.Draw(moon)
        md.ellipse((78, 88, 172, 182), fill=255)
        md.ellipse((106, 72, 192, 158), fill=0)
        im.paste(WHITE, (0, 0), moon)
        for x, y, r in ((168, 172, 5), (186, 132, 4), (150, 112, 3)):
            d.ellipse((x - r, y - r, x + r, y + r), fill=WHITE)
        d.line([(30, 214), (226, 214)], fill=WHITE, width=10)
    elif bid == "curseforge":  # CurseForge's style: a white anvil on dark (with an orange spark)
        im, d = base(top, bottom, round_=False)
        anvil = [(46, 92), (210, 92), (210, 112), (176, 126), (156, 132), (150, 160), (174, 178), (180, 198), (80, 198), (86, 178),
                 (110, 160), (104, 132), (72, 120), (46, 104)]
        d.polygon(anvil, fill=WHITE)
        d.polygon([(46, 92), (24, 84), (46, 106)], fill=WHITE)  # (the horn)
        for x0, y0, x1, y1 in ((196, 70, 214, 48), (180, 64, 184, 40), (212, 82, 236, 70)):
            d.line([(x0, y0), (x1, y1)], fill=(241, 100, 54, 255), width=8)
    elif bid == "atlauncher":  # ATLauncher's style: a dark cube with a green and white cube inside
        im, d = base(top, bottom, round_=False)
        c, R = (S / 2, S / 2 + 4), 100
        h = [(c[0] + R * math.cos(math.radians(90 + 60 * k)), c[1] - R * math.sin(math.radians(90 + 60 * k))) for k in range(6)]
        d.polygon([c, h[0], h[1], h[2]], fill=(70, 72, 76, 255))
        d.polygon([c, h[2], h[3], h[4]], fill=(44, 46, 50, 255))
        d.polygon([c, h[4], h[5], h[0]], fill=(58, 60, 64, 255))
        r = 58
        i = [(c[0] + r * math.cos(math.radians(90 + 60 * k)), c[1] + 10 - r * math.sin(math.radians(90 + 60 * k))) for k in range(6)]
        ci = (c[0], c[1] + 10)
        d.polygon([ci, i[0], i[1], i[2]], fill=(250, 250, 250, 255))
        d.polygon([ci, i[2], i[3], i[4]], fill=(130, 194, 64, 255))
        d.polygon([ci, i[4], i[5], i[0]], fill=(236, 240, 236, 255))
    elif bid == "gdlauncher":  # GDLauncher's style: a blue rounded hexagon with a white monkey face
        im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        d = ImageDraw.Draw(im)
        c, R = (S / 2, S / 2), 122
        hx = [(c[0] + R * math.cos(math.radians(90 + 60 * k)), c[1] - R * math.sin(math.radians(90 + 60 * k))) for k in range(6)]
        d.polygon(hx, fill=(18, 14, 26, 255))
        hx2 = [(c[0] + (R - 10) * math.cos(math.radians(90 + 60 * k)), c[1] - (R - 10) * math.sin(math.radians(90 + 60 * k))) for k in range(6)]
        d.polygon(hx2, fill=top + (255,))
        d.polygon([(c[0], c[1]), hx2[2], hx2[3], hx2[4]], fill=bottom + (255,))
        d.ellipse((58, 92, 102, 136), fill=WHITE)  # (ears)
        d.ellipse((154, 92, 198, 136), fill=WHITE)
        d.ellipse((80, 70, 176, 170), fill=WHITE)  # (head)
        d.ellipse((92, 126, 164, 186), fill=WHITE)  # (muzzle)
        d.ellipse((98, 100, 122, 124), fill=bottom + (255,))  # (eyes)
        d.ellipse((134, 100, 158, 124), fill=bottom + (255,))
        d.ellipse((114, 146, 124, 154), fill=bottom + (255,))  # (nose)
        d.ellipse((132, 146, 142, 154), fill=bottom + (255,))
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
    d.text((30, 76), "Our own icons in each launcher's style: its colours and main shape, drawn simply (not copied). Shown in everyone's name tag.", font=F(20), fill=(154, 163, 178))
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
