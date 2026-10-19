"""The country flags after players' names (2.0.0, creator: "add a region flag on the username depending on local public IP
Address", then "add it to v2.0.0"; Mockups\\Name Badges\\Country flags v2.0.0). One picture per country, 4:3 with a thin
dark edge so white flags show on the name tag's box, all in one font picture (assets/minebloxroyale/textures/font/
flags.png, U+E000 on); font/flags_order.txt lists the countries in that order for CountryFlag.java.

Where the flags come from: a 4:3 picture per country in fabric/tools/branding/flags/<cc>.png (lower-case ISO code), and
for any of the few drawn here (BUILTIN) that has none there, the drawing. A country with neither shows its two letters.

  python fabric/tools/branding/make_flags.py [preview.png]
"""
import glob
import json
import os
import sys

from PIL import Image, ImageChops, ImageDraw

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
ASSETS = os.path.join(ROOT, "fabric", "src", "client", "resources", "assets", "minebloxroyale")
SOURCES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "flags")
CW, CH = 64, 48  # (each flag's cell in the font picture; the name tag shows it 8 units tall, like a letter)
PER_ROW = 16
FW, FH = 256, 192  # (the drawings below are made this big, then shrunk)


def stripes_h(cols):
    im = Image.new("RGB", (FW, FH))
    d = ImageDraw.Draw(im)
    for i, c in enumerate(cols):
        d.rectangle((0, round(FH * i / len(cols)), FW, round(FH * (i + 1) / len(cols))), fill=c)
    return im


def stripes_v(cols):
    im = Image.new("RGB", (FW, FH))
    d = ImageDraw.Draw(im)
    for i, c in enumerate(cols):
        d.rectangle((round(FW * i / len(cols)), 0, round(FW * (i + 1) / len(cols)), FH), fill=c)
    return im


def us():
    im = stripes_h([(178, 34, 52) if i % 2 == 0 else (255, 255, 255) for i in range(13)])
    d = ImageDraw.Draw(im)
    cw, ch = FW * 0.45, FH * 7 / 13
    d.rectangle((0, 0, cw, ch), fill=(60, 59, 110))
    for r in range(9):
        for k in range(6 if r % 2 == 0 else 5):
            x, y = cw * ((k * 2 + (1 if r % 2 == 0 else 2)) / 12), ch * (r + 1) / 10
            d.ellipse((x - 4.5, y - 4.5, x + 4.5, y + 4.5), fill=(255, 255, 255))
    return im


def gb():
    im = Image.new("RGB", (FW, FH), (1, 33, 105))
    d = ImageDraw.Draw(im)
    for w, c in ((38, (255, 255, 255)), (13, (200, 16, 46))):
        d.line((0, 0, FW, FH), fill=c, width=w)
        d.line((0, FH, FW, 0), fill=c, width=w)
    for w, c in ((32, (255, 255, 255)), (19, (200, 16, 46))):
        d.rectangle((FW / 2 - w, 0, FW / 2 + w, FH), fill=c)
        d.rectangle((0, FH / 2 - w, FW, FH / 2 + w), fill=c)
    return im


def ca():
    red = (213, 43, 30)
    im = Image.new("RGB", (FW, FH), (255, 255, 255))
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, FW * 0.25, FH), fill=red)
    d.rectangle((FW * 0.75, 0, FW, FH), fill=red)
    right = [(0.5, 0.06), (0.56, 0.2), (0.63, 0.16), (0.6, 0.36), (0.7, 0.26), (0.73, 0.33), (0.84, 0.3), (0.8, 0.43), (0.86, 0.46),
             (0.68, 0.6), (0.71, 0.68), (0.53, 0.65), (0.53, 0.86)]
    pts = right + [(1 - x, y) for x, y in reversed(right)]
    s, x0, y0 = FH * 0.84, FW / 2 - FH * 0.42, FH * 0.06
    d.polygon([(x0 + x * s, y0 + y * s) for x, y in pts], fill=red)
    return im


def br():
    im = Image.new("RGB", (FW, FH), (0, 156, 59))
    d = ImageDraw.Draw(im)
    d.polygon([(FW * 0.08, FH / 2), (FW / 2, FH * 0.1), (FW * 0.92, FH / 2), (FW / 2, FH * 0.9)], fill=(255, 223, 0))
    r = FH * 0.25
    d.ellipse((FW / 2 - r, FH / 2 - r, FW / 2 + r, FH / 2 + r), fill=(0, 39, 118))
    band = Image.new("L", (FW, FH), 0)
    R, cx, cy = r * 2.2, FW / 2 - r * 0.35, FH / 2 + r * 2.15
    ImageDraw.Draw(band).ellipse((cx - R, cy - R, cx + R, cy + R), outline=255, width=8)
    globe = Image.new("L", (FW, FH), 0)
    ImageDraw.Draw(globe).ellipse((FW / 2 - r, FH / 2 - r, FW / 2 + r, FH / 2 + r), fill=255)
    im.paste((255, 255, 255), (0, 0), ImageChops.darker(band, globe))
    return im


def jp():
    im = Image.new("RGB", (FW, FH), (255, 255, 255))
    r = FH * 0.3
    ImageDraw.Draw(im).ellipse((FW / 2 - r, FH / 2 - r, FW / 2 + r, FH / 2 + r), fill=(188, 0, 45))
    return im


BUILTIN = {
    "us": us, "gb": gb, "ca": ca, "br": br, "jp": jp,
    "de": lambda: stripes_h([(0, 0, 0), (221, 0, 0), (255, 206, 0)]),
    "fr": lambda: stripes_v([(0, 35, 149), (255, 255, 255), (237, 41, 57)]),
}


def cell(pic):
    """A flag in its cell: shrunk to fit inside a 2-pixel dark edge."""
    out = Image.new("RGBA", (CW, CH), (20, 20, 26, 255))
    out.alpha_composite(pic.convert("RGBA").resize((CW - 4, CH - 4), Image.LANCZOS), (2, 2))
    return out


def main():
    pics = {}
    for p in sorted(glob.glob(os.path.join(SOURCES, "*.png"))):
        cc = os.path.splitext(os.path.basename(p))[0].lower()
        if len(cc) == 2 and cc.isalpha():
            pics[cc] = Image.open(p)
    for cc, draw in BUILTIN.items():
        pics.setdefault(cc, draw())
    codes = sorted(pics)
    rows = (len(codes) + PER_ROW - 1) // PER_ROW
    atlas = Image.new("RGBA", (CW * PER_ROW, CH * rows), (0, 0, 0, 0))
    for i, cc in enumerate(codes):
        atlas.alpha_composite(cell(pics[cc]), ((i % PER_ROW) * CW, (i // PER_ROW) * CH))
    atlas.save(os.path.join(ASSETS, "textures", "font", "flags.png"))
    chars = "".join(chr(0xE000 + i) for i in range(len(codes))) + "\0" * (rows * PER_ROW - len(codes))
    with open(os.path.join(ASSETS, "font", "flags.json"), "w", encoding="utf-8", newline="\n") as fh:
        json.dump({"providers": [{"type": "bitmap", "file": "minebloxroyale:font/flags.png", "ascent": 7, "height": 8,
                                  "chars": [chars[r * PER_ROW:(r + 1) * PER_ROW] for r in range(rows)]}]}, fh, indent=1)
        fh.write("\n")
    with open(os.path.join(ASSETS, "font", "flags_order.txt"), "w", encoding="utf-8", newline="\n") as fh:
        fh.write(" ".join(cc.upper() for cc in codes) + "\n")
    if len(sys.argv) > 1:
        atlas.save(sys.argv[1])
    print("flags:", len(codes), "(%d from %s, the rest drawn here)" % (len(glob.glob(os.path.join(SOURCES, "*.png"))), SOURCES))


if __name__ == "__main__":
    main()
