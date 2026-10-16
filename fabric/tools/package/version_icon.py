"""The modpack's icon with the build's number on it (creator, 2026-10-16: "each test build and official release number
should show on the modpack icon starting with the new 2.0.0 test6.1 (should show 2-6.1 so I know the 2 is the version,
and 6.1 is the current test build)"): a gold tag across the bottom of the Mineblox Royale icon (Branding\\, make_icon.py).

  label("2.0.0-test6.1") -> "2-6.1" (the major version, then the test build); label("2.0.0") -> "2.0.0" (a release).
  python fabric/tools/package/version_icon.py <version> <out.png>      (a preview)
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
SRC = os.path.join(ROOT, "Branding", "Mineblox Royale icon 1024.png")
GOLD, INK, OUTLINE = (255, 200, 40), (34, 10, 66), (24, 16, 30)
FONT = "C:/Windows/Fonts/bahnschrift.ttf"


def label(version):
    if "-test" in version:
        base, test = version.split("-test", 1)
        return base.split(".")[0] + "-" + test
    return version


def font(px):
    f = ImageFont.truetype(FONT, px)
    try:
        f.set_variation_by_name("Bold")  # (Bahnschrift is one font of many weights)
    except Exception:  # noqa: BLE001
        pass
    return f


def make(version, out, src=SRC):
    """The icon with the version's tag, written to out (the same size as the icon)."""
    im = Image.open(src).convert("RGBA")
    n = im.width
    ss = 2  # (drawn twice as big, then shrunk: smooth edges)
    layer = Image.new("RGBA", (n * ss, n * ss), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    text = label(version)
    h = int(0.27 * n * ss)
    size = int(h * 0.78)
    f = font(size)
    while d.textlength(text, font=f) > 0.82 * n * ss and size > 10:
        size -= 4
        f = font(size)
    tw = d.textlength(text, font=f)
    w = int(max(tw + h * 0.7, h * 1.6))
    x0, y1 = (n * ss - w) // 2, int(0.975 * n * ss)
    y0 = y1 - h
    edge = int(0.028 * n * ss)
    d.rounded_rectangle((x0, y0, x0 + w, y1), radius=h // 2, fill=OUTLINE)
    d.rounded_rectangle((x0 + edge, y0 + edge, x0 + w - edge, y1 - edge), radius=h // 2 - edge, fill=GOLD)
    # a lighter band on the top half, like the crown's shine
    shine = Image.new("RGBA", layer.size, (0, 0, 0, 0))
    ImageDraw.Draw(shine).rounded_rectangle((x0 + edge * 2, y0 + edge * 2, x0 + w - edge * 2, y0 + h // 2), radius=(h // 2 - edge * 2) // 2,
                                           fill=(255, 236, 150, 90))
    layer.alpha_composite(shine)
    d = ImageDraw.Draw(layer)
    d.text(((x0 + x0 + w) / 2, (y0 + y1) / 2 + edge * 0.15), text, font=f, fill=INK, anchor="mm")
    im.alpha_composite(layer.resize((n, n), Image.LANCZOS))
    im.save(out)
    return out


if __name__ == "__main__":
    print(make(sys.argv[1], sys.argv[2]), label(sys.argv[1]))
