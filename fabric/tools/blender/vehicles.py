"""
The Battle Bus and the reboot van, made in Blender (v1.0.1, creator: "Battle Bus needs more detail (also rendered with
Blender)"; the van shares the bus's style). Our own designs in a smooth toy-like style; no Fortnite asset is used.

Block units, front along +z. The bus: origin at the cabin floor (where its riders sit), its hot-air balloon high
above; the game draws it full bright, so its shading is the baked soft shadow. The van: origin on the ground in the
middle (it stands on the invisible blocks the server puts there); its screens and roof light are tint channel 1 and
glow: green when it's ready, red while it recharges.
"""
import math

from brlib import box, cyl, lathe, mat, mesh_from, sphere, surface, torus, tube

BLUE = lambda: mat("bus_blue", "#2F6BD8", rough=0.35, metal=0.1)
DARK_BLUE = lambda: mat("bus_dark_blue", "#1E4797", rough=0.4)
WHITE = lambda: mat("bus_white", "#F2F4F8", rough=0.35)
GLASS = lambda: mat("bus_glass", "#8FCBE8", rough=0.08, metal=0.2)
TIRE = lambda: mat("tire", "#232428", rough=0.92)
HUB = lambda: mat("hub", "#C9CDD4", rough=0.25, metal=0.9)
DARK = lambda: mat("dark", "#1A1D22", rough=0.7)
CHROME = lambda: mat("chrome", "#DDE3EA", rough=0.15, metal=1.0)
LIGHT = lambda: mat("light", "#FFE58A", glow=True)
RED = lambda: mat("tail", "#D8402F", rough=0.3)
ROPE = lambda: mat("rope", "#7A5E3C", rough=0.9)
WOOD = lambda: mat("wood", "#8A5A2B", rough=0.7)
YELLOW = lambda: mat("yellow", "#F5C629", rough=0.4)


def wheel(x, y, z, r, w, s):
    """A chunky wheel with its outer face at x + s * w: rounded tyre, a white-walled hub, five nuts."""
    lathe([(0.0, -w), (r * 0.6, -w), (r * 0.92, -w), (r, -w * 0.5), (r, w * 0.5), (r * 0.92, w), (r * 0.6, w), (0.0, w)], x, y, z, TIRE(), segs=24, axis="x")
    cyl(x + s * w, y, z, r * 0.58, 0.04, "x", WHITE(), verts=20)
    cyl(x + s * (w + 0.02), y, z, r * 0.4, 0.04, "x", HUB(), verts=18)
    for i in range(5):
        a = i * math.pi * 2 / 5
        cyl(x + s * (w + 0.045), y + math.cos(a) * r * 0.24, z + math.sin(a) * r * 0.24, r * 0.05, 0.03, "x", DARK(), verts=8)
    cyl(x + s * (w + 0.05), y, z, r * 0.1, 0.03, "x", DARK(), verts=10)


def battle_bus():
    # The body: a long rounded box, a lower bonnet in front with a sloped windscreen above it.
    box(-1.6, 0.45, -4.2, 1.6, 2.45, 3.0, BLUE(), bevel=0.35, seg=4)
    box(-1.5, 0.45, 2.6, 1.5, 1.55, 4.25, BLUE(), bevel=0.3, seg=4)
    mesh_from([(-1.45, 1.5, 3.05), (1.45, 1.5, 3.05), (1.38, 2.35, 2.6), (-1.38, 2.35, 2.6)], [(0, 1, 2, 3)], GLASS(), smooth=False).move(0, 0, 0.02)
    # Roof: a white cap with a luggage rack and two vents.
    box(-1.5, 2.4, -4.05, 1.5, 2.66, 2.85, WHITE(), bevel=0.14, seg=3)
    for x in (-1.25, 1.25):
        tube([(x, 2.66, -3.6), (x, 2.82, -3.45), (x, 2.82, 2.2), (x, 2.66, 2.35)], 0.05, CHROME(), segs=8)
    for z in (-2.6, -1.2, 0.2, 1.6):
        tube([(-1.25, 2.82, z), (1.25, 2.82, z)], 0.035, CHROME(), segs=6)
    for z in (-3.3, 1.0):
        box(-0.4, 2.66, z - 0.3, 0.4, 2.8, z + 0.3, WHITE(), bevel=0.06)
        for k in range(4):
            box(-0.32, 2.8, z - 0.24 + k * 0.15, 0.32, 2.82, z - 0.18 + k * 0.15, DARK())
    # The stripes: white under the windows, a dark blue skirt.
    box(-1.62, 1.2, -4.22, 1.62, 1.36, 3.02, WHITE(), bevel=0.06)
    box(-1.63, 0.42, -4.23, 1.63, 0.62, 3.03, DARK_BLUE(), bevel=0.08)
    box(-1.52, 0.42, 2.6, 1.52, 0.62, 4.27, DARK_BLUE(), bevel=0.08)
    # Side windows in white frames; the door (right side, near the front) with its steps.
    for s in (-1, 1):
        for i in range(6):
            z = -3.6 + i * 1.02
            if s < 0 and i == 4:  # (the door's place: behind the front wheel, clear of its arch)
                continue
            box(s * 1.6 - 0.04, 1.48, z, s * 1.6 + 0.04, 2.3, z + 0.86, WHITE(), bevel=0.05)
            box(s * 1.6 - 0.05, 1.56, z + 0.08, s * 1.6 + 0.05, 2.22, z + 0.78, GLASS(), bevel=0.03)
    box(-1.65, 0.62, 0.47, -1.55, 2.32, 1.45, WHITE(), bevel=0.05)
    for k, (y0, y1) in enumerate(((0.7, 1.45), (1.55, 2.25))):
        box(-1.66, y0, 0.54, -1.58, y1, 0.94, GLASS(), bevel=0.03)
        box(-1.66, y0, 0.98, -1.58, y1, 1.38, GLASS(), bevel=0.03)
    box(-1.75, 0.2, 0.52, -1.5, 0.3, 1.4, DARK(), bevel=0.04)
    # The nose: grille, round headlights, a big bumper, a yellow plate.
    box(-0.9, 0.7, 4.22, 0.9, 1.35, 4.32, DARK(), bevel=0.06)
    for k in range(5):
        box(-0.84, 0.76 + k * 0.12, 4.3, 0.84, 0.8 + k * 0.12, 4.34, CHROME())
    for s in (-1, 1):
        cyl(s * 1.15, 1.05, 4.25, 0.24, 0.14, "z", CHROME(), verts=20)
        cyl(s * 1.15, 1.05, 4.32, 0.19, 0.04, "z", LIGHT(), verts=20)
    box(-1.62, 0.32, 4.2, 1.62, 0.6, 4.5, CHROME(), bevel=0.1, seg=3)
    box(-0.35, 0.36, 4.48, 0.35, 0.56, 4.52, YELLOW(), bevel=0.02)
    # Side mirrors on arms by the windscreen.
    for s in (-1, 1):
        tube([(s * 1.45, 1.9, 2.9), (s * 1.85, 2.0, 3.05), (s * 1.9, 1.9, 3.1)], 0.03, DARK(), segs=6)
        box(s * 1.82, 1.6, 3.0, s * 2.0, 1.95, 3.2, DARK(), bevel=0.05)
    # The back: tail lights, an emergency door, a ladder to the roof, the exhaust.
    for s in (-1, 1):
        box(s * 1.15 - 0.2, 0.9, -4.27, s * 1.15 + 0.2, 1.2, -4.18, RED(), bevel=0.05)
    box(-0.6, 0.7, -4.25, 0.6, 2.25, -4.18, WHITE(), bevel=0.05)
    box(-0.48, 1.45, -4.28, 0.48, 2.12, -4.22, GLASS(), bevel=0.03)
    for s in (-1, 1):
        tube([(s * 0.95, 0.6, -4.3), (s * 0.95, 2.6, -4.3)], 0.035, CHROME(), segs=6)
    for k in range(7):
        tube([(-0.95, 0.75 + k * 0.28, -4.3), (0.95, 0.75 + k * 0.28, -4.3)], 0.025, CHROME(), segs=6)
    tube([(1.2, 0.35, -3.9), (1.2, 0.35, -4.4)], 0.07, CHROME(), segs=10)
    # Wheels under round arches.
    for z in (-2.7, 2.6):
        for s in (-1, 1):
            torus(s * 1.6, 0.42, z, 0.62, 0.07, "x", DARK_BLUE(), segs=20, rsegs=6, arc=180).rot("x", 90, (s * 1.6, 0.42, z))  # (the upper half)
            wheel(s * 1.5, 0.42, z, 0.5, 0.18, s)
    # Rigging: four struts up from the roof corners to a wooden rack, ropes on to the balloon.
    for x in (-1.3, 1.3):
        for z in (-3.6, 3.6 if True else 0):
            zz = min(z, 2.4)
            tube([(x, 2.7, zz), (x * 0.95, 6.15, z * 0.95)], 0.06, ROPE(), segs=6)
    box(-1.5, 6.12, -3.8, 1.5, 6.48, 3.8, WOOD(), bevel=0.12, seg=3)
    for z in (-3.0, -1.5, 0.0, 1.5, 3.0):
        box(-1.55, 6.1, z - 0.08, 1.55, 6.5, z + 0.08, mat("wood_dark", "#5E3A1E", rough=0.8))
    for i in range(8):
        a = i * math.pi * 2 / 8
        tube([(1.4 * math.cos(a), 6.48, 3.6 * math.sin(a)), (3.2 * math.cos(a), 7.4, 3.2 * math.sin(a))], 0.04, ROPE(), segs=5)
    balloon(0, 10.8, 0, 4.3)


def balloon(cx, cy, cz, r, gores=16):
    """A hot-air balloon: gores of blue and white round a teardrop envelope, a yellow band round its middle, a crown
    ring on top and a dark skirt where it narrows."""
    blue = mat("balloon_blue", "#3B86F0", rough=0.5)
    white = mat("balloon_white", "#F5F7FA", rough=0.5)

    def env(u, v):
        t = math.pi * v  # 0 at the top, pi at the bottom
        a = 2 * math.pi * u
        st, ct = math.sin(t), math.cos(t)
        squeeze = 1 + ct * 0.38 if ct < 0 else 1.0
        bulge = 1 + 0.03 * math.cos(gores * a)  # each gore bulges a little between its seams
        return (cx + st * math.cos(a) * r * squeeze * bulge, cy + ct * r * 1.15, cz + st * math.sin(a) * r * squeeze * bulge)

    for g in range(gores):
        surface(lambda u, v, g=g: env((g + u) / gores, 0.03 + v * 0.86), 4, 18, blue if g % 2 == 0 else white, thickness=0.05)
    band = env(0, 0.5)[0] - cx
    torus(cx, cy, cz, band + 0.02, 0.12, "y", YELLOW(), segs=48, rsegs=6)
    top = env(0, 0.03)
    torus(cx, top[1], cz, top[0] - cx, 0.1, "y", YELLOW(), segs=24, rsegs=6)
    bottom = env(0, 0.89)
    lathe([(bottom[0] - cx, 0), (bottom[0] - cx - 0.05, -0.55), (1.6, -1.2), (1.55, -1.25), (0.0, -1.25)], cx, bottom[1], cz, mat("skirt", "#1E4797", rough=0.6), segs=24)


def reboot_van():
    state = lambda: mat("state_glow", "#FFFFFF", ch=1, glow=True)
    white = mat("van_white", "#E9EDF2", rough=0.35)
    # Body: tall and rounded, the bus's blue along the bottom, a dark sill and a blue stripe round the middle.
    box(-1.25, 0.6, -2.35, 1.25, 2.67, 2.35, white, bevel=0.35, seg=4)
    box(-1.28, 0.56, -2.38, 1.28, 1.08, 2.38, BLUE(), bevel=0.18, seg=3)
    box(-1.2, 0.48, -2.3, 1.2, 0.64, 2.3, DARK_BLUE(), bevel=0.06)
    box(-1.27, 1.43, -2.37, 1.27, 1.57, 2.37, BLUE(), bevel=0.05)
    # Windscreen, the cab's side windows.
    box(-1.0, 1.7, 2.3, 1.0, 2.42, 2.38, GLASS(), bevel=0.04)
    for s in (-1, 1):
        box(s * 1.26 - 0.04, 1.73, 1.05, s * 1.26 + 0.04, 2.37, 2.05, GLASS(), bevel=0.03)
    # Headlights, grille, bumper.
    for s in (-1, 1):
        box(s * 0.8 - 0.22, 0.96, 2.33, s * 0.8 + 0.22, 1.24, 2.43, LIGHT(), bevel=0.05)
    box(-0.45, 0.8, 2.35, 0.45, 1.25, 2.4, DARK(), bevel=0.04)
    box(-1.15, 0.58, 2.38, 1.15, 0.82, 2.58, mat("bumper", "#9AA3AE", rough=0.4, metal=0.4), bevel=0.08)
    # The reboot terminal on both sides: a dark panel, a glowing screen and a card slot.
    for s in (-1, 1):
        box(s * 1.27 - 0.04, 1.25, -1.45, s * 1.27 + 0.04, 2.25, 0.05, mat("panel", "#1B2230", rough=0.6), bevel=0.04)
        box(s * 1.3 - 0.03, 1.5, -1.25, s * 1.3 + 0.03, 2.14, -0.15, state(), bevel=0.03)
        box(s * 1.32 - 0.03, 1.35, -1.0, s * 1.32 + 0.03, 1.45, -0.4, DARK(), bevel=0.02)
    # Wheels.
    for z in (-1.5, 1.5):
        for s in (-1, 1):
            wheel(s * 1.2, 0.42, z, 0.42, 0.12, s)
    # Roof: a light bar over the cab and a satellite dish on a mast.
    box(-0.75, 2.66, 1.17, 0.75, 2.82, 1.53, mat("roof_grey", "#9AA3AE", rough=0.4), bevel=0.06)
    box(-0.65, 2.8, 1.22, 0.65, 2.94, 1.48, state(), bevel=0.05)
    cyl(0, 3.02, -1.0, 0.07, 0.7, "y", mat("roof_grey", "#9AA3AE"), verts=10)
    lathe([(0.0, 0.0), (0.3, 0.02), (0.55, 0.1), (0.66, 0.18), (0.62, 0.2), (0.5, 0.13), (0.28, 0.06), (0.0, 0.04)], 0.05, 3.5, -1.0, white, segs=24, axis="x")
    tube([(0.1, 3.5, -1.0), (0.55, 3.5, -1.0)], 0.03, mat("roof_grey", "#9AA3AE"), segs=6)
    sphere(0.57, 3.5, -1.0, 0.06, 0.06, 0.06, DARK(), segs=10, rings=6)


ITEMS = {
    "battle_bus": ("Battle Bus", battle_bus, {"scale": 1.0, "ao": 1.2, "folder": "Battle Bus", "views": {"Front": 0, "Left": 90, "Back": 180, "Right": 270},
                                              "focus": ("close-up", ((-2.1, -0.2, -4.6), (2.1, 3.0, 4.6)))}),
    "reboot_van": ("Reboot Van", reboot_van, {"scale": 1.0, "ao": 0.8, "folder": "Reboot Van",
                                               "variants": {"Ready": {1: "#4CD964"}, "Recharging": {1: "#E03A3A"}},
                                               "views": {"Front": 0, "Left": 90, "Back": 180, "Right": 270}}),
}
