"""
Facial expressions (2.1.0, creator: "Facial expressions that work with every face accessory, on a page of their own in
the Locker; some free, some unlocked by a condition, some for gold"): each one a face decal of our own, drawn here in
the classic flat style (black eyes and mouth; the odd touch of color), 128 x 128 like Roblox's own face.png and with
the eyes where it has them (x 0.38-0.63, y 0.21-0.34), so glasses, masks and the rest of the face accessories sit on
them as they do on the default face. Written to assets/minebloxroyale/textures/expression/<shape>.png; the cosmetics
sheet's "expression" rows name them (shape). The game lays them side by side with the player's own Roblox face
(the default) in one texture and tints them with the head color, as it does that face.

python fabric/tools/locker/make_expressions.py
"""
import math
import os

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "minebloxroyale", "textures", "expression"))
S = 128
K = 4  # drawn 4 times bigger, then shrunk: smooth edges
INK = (22, 22, 24, 255)
RED = (214, 38, 58, 255)
GOLD = (245, 196, 40, 255)
# the default face's eyes: centres and size (fractions of the decal)
LX, RX, EY, EW, EH = 0.41, 0.5975, 0.2775, 0.056, 0.13


def px(v):
    return v * S * K


def box(cx, cy, w, h):
    return [px(cx - w / 2), px(cy - h / 2), px(cx + w / 2), px(cy + h / 2)]


def line(d, pts, w=0.04, fill=INK):
    d.line([(px(x), px(y)) for x, y in pts], fill=fill, width=int(px(w)), joint="curve")
    for x, y in (pts[0], pts[-1]):  # round ends
        d.ellipse(box(x, y, w, w), fill=fill)


def curve(d, f, x0, x1, w=0.04, fill=INK, n=40):
    line(d, [(x0 + (x1 - x0) * i / n, f(x0 + (x1 - x0) * i / n)) for i in range(n + 1)], w, fill)


def eyes(d, w=EW, h=EH, dy=0.0):
    for x in (LX, RX):
        d.ellipse(box(x, EY + dy, w, h), fill=INK)


def happy_eyes(d, up=True):
    """Closed, smiling eyes: ^ ^ (up) or u u."""
    for x in (LX, RX):
        if up:
            curve(d, lambda t, x=x: EY + 0.02 + 0.09 * ((t - x) / 0.055) ** 2 - 0.045, x - 0.055, x + 0.055, 0.042)
        else:
            curve(d, lambda t, x=x: EY - 0.02 - 0.09 * ((t - x) / 0.055) ** 2 + 0.045, x - 0.055, x + 0.055, 0.042)


def smile(d, w=0.17, depth=0.09, y=0.63, thick=0.045):
    curve(d, lambda t: y + depth * (1 - ((t - 0.5) / w) ** 2), 0.5 - w, 0.5 + w, thick)


def open_mouth(d, w=0.15, depth=0.17, y=0.6, tongue=True):
    """A wide open D-shaped mouth, flat on top, with a red tongue at the bottom."""
    pts = [(0.5 - w, y)] + [(0.5 - w * math.cos(math.pi * i / 30), y + depth * math.sin(math.pi * i / 30)) for i in range(31)]
    d.polygon([(px(x), px(yy)) for x, yy in pts], fill=INK)
    if tongue:
        d.chord(box(0.5, y + depth * 0.86, w * 1.05, depth * 0.62), 180, 360, fill=RED)


def heart(d, cx, cy, s, fill=RED):
    pts = []
    for i in range(80):
        t = 2 * math.pi * i / 80
        x = 16 * math.sin(t) ** 3
        y = -(13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t))
        pts.append((px(cx + x / 34 * s), px(cy + y / 34 * s)))
    d.polygon(pts, fill=fill)


def star(d, cx, cy, r, fill=GOLD):
    pts = []
    for i in range(10):
        a = -math.pi / 2 + math.pi * i / 5
        rr = r if i % 2 == 0 else r * 0.45
        pts.append((px(cx + rr * math.cos(a)), px(cy + rr * math.sin(a))))
    d.polygon(pts, fill=fill)


def brows(d, slope, y=0.17, w=0.036):
    """Eyebrows: slope > 0 lower towards the middle (cross), < 0 higher towards the middle (worried)."""
    line(d, [(LX - 0.06, y - slope), (LX + 0.05, y + slope)], w)
    line(d, [(RX - 0.05, y + slope), (RX + 0.06, y - slope)], w)


# ------------------------------------------------------------------------------------------------- the expressions

def grin(d):
    eyes(d)
    open_mouth(d, 0.15, 0.15, 0.6)


def wink(d):
    d.ellipse(box(LX, EY, EW, EH), fill=INK)
    curve(d, lambda t: EY + 0.01 - 0.05 * (1 - ((t - RX) / 0.055) ** 2), RX - 0.055, RX + 0.055, 0.042)
    smile(d, 0.16, 0.08, 0.62)


def happy(d):
    happy_eyes(d)
    smile(d, 0.17, 0.1, 0.6)


def surprised(d):
    eyes(d, 0.075, 0.15)
    brows(d, -0.01, 0.13, 0.042)
    d.ellipse(box(0.5, 0.67, 0.09, 0.12), fill=INK)


def smirk(d):
    eyes(d)
    curve(d, lambda t: 0.66 - 0.06 * max(0, (t - 0.47) / 0.17) ** 1.6, 0.36, 0.64, 0.042)


def angry(d):
    eyes(d, EW, 0.1, 0.015)
    brows(d, 0.035, 0.18, 0.04)
    curve(d, lambda t: 0.7 - 0.06 * (1 - ((t - 0.5) / 0.13) ** 2), 0.37, 0.63, 0.044)


def determined(d):
    eyes(d, EW, 0.11, 0.01)
    brows(d, 0.02, 0.17, 0.034)
    line(d, [(0.4, 0.68), (0.6, 0.665)], 0.044)


def cool(d):
    # half-closed eyes: the lid a flat line across the top
    for x in (LX, RX):
        d.chord(box(x, EY + 0.005, EW * 1.4, EH * 1.1), 0, 180, fill=INK)
        line(d, [(x - 0.055, EY + 0.005), (x + 0.055, EY + 0.005)], 0.034)
    curve(d, lambda t: 0.655 - 0.05 * max(0, (t - 0.45) / 0.18) ** 1.4 + 0.01 * (1 - ((t - 0.5) / 0.15) ** 2), 0.37, 0.64, 0.042)


def tongue(d):
    eyes(d)
    smile(d, 0.16, 0.07, 0.6)
    d.chord(box(0.53, 0.66, 0.1, 0.16), 0, 180, fill=RED)
    line(d, [(0.53, 0.665), (0.53, 0.715)], 0.012, (150, 20, 35, 255))


def laughing(d):
    # squeezed-shut eyes > <
    line(d, [(LX - 0.04, EY - 0.04), (LX + 0.035, EY), (LX - 0.04, EY + 0.04)], 0.042)
    line(d, [(RX + 0.04, EY - 0.04), (RX - 0.035, EY), (RX + 0.04, EY + 0.04)], 0.042)
    open_mouth(d, 0.17, 0.2, 0.57)


def worried(d):
    eyes(d, EW, 0.12)
    brows(d, -0.03, 0.16, 0.044)
    curve(d, lambda t: 0.68 + 0.018 * math.sin((t - 0.38) / 0.24 * 3 * math.pi), 0.38, 0.62, 0.04)


def victory(d):
    happy_eyes(d)
    open_mouth(d, 0.17, 0.19, 0.57)
    for x in (0.31, 0.69):  # rosy cheeks
        d.ellipse(box(x, 0.5, 0.08, 0.045), fill=(232, 120, 130, 255))


def heart_eyes(d):
    for x in (LX, RX):
        heart(d, x, EY + 0.005, 0.15)
    open_mouth(d, 0.14, 0.15, 0.6)


def starstruck(d):
    for x in (LX, RX):
        star(d, x, EY + 0.005, 0.09)
    open_mouth(d, 0.15, 0.17, 0.59)


def sleepy(d):
    for x in (LX, RX):
        curve(d, lambda t, x=x: EY + 0.02 + 0.035 * (1 - ((t - x) / 0.06) ** 2), x - 0.06, x + 0.06, 0.036)
        for k in (-0.035, 0.0, 0.035):  # lashes
            line(d, [(x + k, EY + 0.06), (x + k * 1.5, EY + 0.095)], 0.02)
    d.ellipse(box(0.5, 0.68, 0.06, 0.07), fill=INK)
    for zx, zy, s in ((0.72, 0.2, 0.06), (0.8, 0.12, 0.045)):  # zz
        line(d, [(zx - s / 2, zy - s / 2), (zx + s / 2, zy - s / 2), (zx - s / 2, zy + s / 2), (zx + s / 2, zy + s / 2)], 0.022)


EXPRESSIONS = {
    "grin": grin, "wink": wink, "happy": happy, "surprised": surprised, "smirk": smirk, "angry": angry, "determined": determined,
    "cool": cool, "tongue": tongue, "laughing": laughing, "worried": worried, "victory": victory, "heart_eyes": heart_eyes,
    "starstruck": starstruck, "sleepy": sleepy,
}


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, draw in EXPRESSIONS.items():
        big = Image.new("RGBA", (S * K, S * K), (0, 0, 0, 0))
        draw(ImageDraw.Draw(big))
        big.resize((S, S), Image.LANCZOS).save(os.path.join(OUT, name + ".png"), optimize=True)
    print(len(EXPRESSIONS), "expressions ->", OUT)


if __name__ == "__main__":
    main()
