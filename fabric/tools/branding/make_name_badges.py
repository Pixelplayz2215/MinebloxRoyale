"""The badges over some players' name tags (2.0.0, creator: "Give creaper89O1 a Beta Tester badge in the nametag / Give
Pixel2215YT a Owner Badge in the nametag", then picked option C of Mockups\\Name Badges\\Options v2.0.0: "Badge on it's own
line over the name tag. The nametag doesnt change"). A gold OWNER badge with a crown and a purple BETA TESTER badge with a
flask, our own drawings. Each is one character of the font minebloxroyale:name_badges (U+E000 on, in BADGES' order;
NameBadge.java has the same order), drawn 4 times bigger and shrunk, so its edges stay smooth.

  python fabric/tools/branding/make_name_badges.py [preview.png]
"""
import json
import os
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
ASSETS = os.path.join(ROOT, "fabric", "src", "client", "resources", "assets", "minebloxroyale")
CELL = 48  # (each badge's height in its font picture; the name tag shows it 9 units tall, like the launcher badges)
S = CELL * 4  # (drawn this big, then shrunk)
INK_RIM = (24, 16, 30, 255)


def bold(n):
    f = ImageFont.truetype("C:/Windows/Fonts/bahnschrift.ttf", n)
    f.set_variation_by_name("Bold")
    return f


def gradient(w, h, top, bottom):
    g = Image.new("RGBA", (w, h))
    d = ImageDraw.Draw(g)
    for y in range(h):
        f = y / max(1, h - 1)
        d.line([(0, y), (w, y)], fill=tuple(int(top[i] + (bottom[i] - top[i]) * f) for i in range(3)) + (255,))
    return g


def crown(s, col):
    im = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    P = lambda pts: [(x * s, y * s) for x, y in pts]
    d.polygon(P([(0.06, 0.78), (0.06, 0.3), (0.3, 0.52), (0.5, 0.16), (0.7, 0.52), (0.94, 0.3), (0.94, 0.78)]), fill=col)
    d.rounded_rectangle((0.06 * s, 0.8 * s, 0.94 * s, 0.94 * s), radius=0.04 * s, fill=col)
    for x, y in ((0.06, 0.27), (0.5, 0.13), (0.94, 0.27)):
        r = 0.075 * s
        d.ellipse((x * s - r, y * s - r, x * s + r, y * s + r), fill=col)
    return im


def flask(s, col, liquid=(140, 255, 190, 255)):
    im = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    P = lambda pts: [(x * s, y * s) for x, y in pts]
    d.polygon(P([(0.38, 0.1), (0.62, 0.1), (0.62, 0.4), (0.9, 0.86), (0.84, 0.94), (0.16, 0.94), (0.1, 0.86), (0.38, 0.4)]), fill=col)
    d.rounded_rectangle((0.3 * s, 0.04 * s, 0.7 * s, 0.14 * s), radius=0.04 * s, fill=col)
    d.polygon(P([(0.27, 0.62), (0.73, 0.62), (0.84, 0.82), (0.79, 0.87), (0.21, 0.87), (0.16, 0.82)]), fill=liquid)
    for x, y, r in ((0.44, 0.52, 0.045), (0.55, 0.42, 0.03)):
        d.ellipse(((x - r) * s, (y - r) * s, (x + r) * s, (y + r) * s), fill=liquid)
    return im


# id, the word, its icon, the badge's colours top and bottom, the ink for the word and icon
BADGES = [
    ("owner", "OWNER", crown, (255, 214, 92), (226, 150, 22), (54, 32, 0, 255)),
    ("beta_tester", "BETA TESTER", flask, (170, 112, 255), (112, 52, 214), (255, 255, 255, 255)),
]


def badge(word, icon, top, bottom, ink, h=S):
    """A rounded badge: a thin dark rim, shaded top to bottom, a soft shine on its top half, the icon and the word."""
    f = bold(int(h * 0.56))
    tw = int(f.getlength(word))
    pad, isz, gap = int(h * 0.3), int(h * 0.62), int(h * 0.14)
    w = pad + isz + gap + tw + pad
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    rim = Image.new("L", (w, h), 0)
    ImageDraw.Draw(rim).rounded_rectangle((0, 0, w - 1, h - 1), radius=h // 2, fill=255)
    im.paste(INK_RIM, (0, 0), rim)
    e = max(2, h // 24)
    m = Image.new("L", (w, h), 0)
    ImageDraw.Draw(m).rounded_rectangle((e, e, w - 1 - e, h - 1 - e), radius=h // 2 - e, fill=255)
    im.paste(gradient(w, h, top, bottom), (0, 0), m)
    hm = Image.new("L", (w, h), 0)
    ImageDraw.Draw(hm).rounded_rectangle((2 * e, 2 * e, w - 1 - 2 * e, h // 2), radius=h // 3, fill=56)
    shine = Image.new("RGBA", (w, h), (255, 255, 255, 0))
    shine.putalpha(ImageChops.darker(hm, m))
    im.alpha_composite(shine)
    im.alpha_composite(icon(isz, ink), (pad, (h - isz) // 2))
    ImageDraw.Draw(im).text((pad + isz + gap, h / 2 + h / 72), word, font=f, fill=ink, anchor="lm")
    return im


def main():
    providers, pics = [], []
    for i, (bid, word, icon, top, bottom, ink) in enumerate(BADGES):
        big = badge(word, icon, top, bottom, ink)
        pic = big.resize((round(big.width * CELL / S), CELL), Image.LANCZOS)
        pic.save(os.path.join(ASSETS, "textures", "font", "name_badge_%s.png" % bid))
        pics.append(pic)
        # (one character per picture, since the badges are different widths; 9 tall from 1 above the text's top)
        providers.append({"type": "bitmap", "file": "minebloxroyale:font/name_badge_%s.png" % bid, "ascent": 8, "height": 9, "chars": [chr(0xE000 + i)]})
    with open(os.path.join(ASSETS, "font", "name_badges.json"), "w", encoding="utf-8", newline="\n") as fh:
        json.dump({"providers": providers}, fh, indent=1)
        fh.write("\n")
    if len(sys.argv) > 1:
        prev = Image.new("RGBA", (sum(p.width for p in pics) + 20 * (len(pics) + 1), CELL + 40), (40, 44, 56, 255))
        x = 20
        for p in pics:
            prev.alpha_composite(p, (x, 20))
            x += p.width + 20
        prev.convert("RGB").save(sys.argv[1])
    print("name badges:", ", ".join("%s %dx%d" % (b[0], p.width, p.height) for b, p in zip(BADGES, pics)))


if __name__ == "__main__":
    main()
