"""
Pickaxe skins (v2.0.0, creator: "Unlockable pickaxe skins in the Locker"): Blender models drawn in place of the
default pickaxe (items.py pickaxe) when a player picks one in the Locker (cosmetics sheet, slot pickaxe, shape = the
model's name after "pickaxe_"; the server puts it on the pickaxe item, MeshItemRenderer draws it).

Same space and size as the default pickaxe (item pixels, 1/16 block: the handle from the bottom left corner up to the
head at about (11.4, 11.4), lying in the z = 8 plane), so each is held exactly like it.

2.0.0-test3 (creator: "Change the diamond pick as that's already a Minecraft item. something from Fortnite maybe) Same
with Golden pick and add more skins too (Unlockable via conditions AND shop)"): the Golden Pick and the Diamond Pick are
now the Royal Cleaver and the Prism Breaker (same ids and unlocks), and eight more: Riff Ripper, Scoop Smasher, Pizza
Slicer and Boom Pick for gold, Storm Splitter, Bone Breaker, Bullseye Breaker and Flint Hunter unlocked by playing.
Fortnite-style (chunky, bevelled, one big blade), all our own.
"""
import math

from brlib import box, cyl, mat, prism, sphere, torus, tube

HX, HY, Z = 11.4, 11.4, 8
SQ = math.sqrt(0.5)


def at(u, v):
    """A point in the head's own frame, as item pixels (x, y): u across the head (+: the spike's way, up and left; -:
    the blade's, down and right), v along the handle (+: on past the head)."""
    return (HX - u * SQ + v * SQ, HY + u * SQ + v * SQ)


def head_prism(points, half, material, bevel=0.0, z=Z):
    """A flat piece of the head: an outline [(u, v), ...] in the head's frame, `half` thick each side of z."""
    return prism([at(u, v) for u, v in points], z - half, z + half, material, bevel=bevel, seg=2)


def head_tube(points, r, material, segs=12, z=Z):
    """A tube through [(u, v), ...] in the head's frame (z: its depth, or one per point)."""
    zs = z if isinstance(z, (list, tuple)) else [z] * len(points)
    return tube([(*at(u, v), zz) for (u, v), zz in zip(points, zs)], r, material, segs=segs)


def along(t, off=0.0):
    """A point t of the way up the handle (0: the pommel end, 1: the head), `off` across it (+: the spike's side)."""
    x = 2.2 + (HX - 2.2) * t
    return (x - off * SQ, x + off * SQ)


def _handle(stick, grip, bands=5, cap=None, step=0.75):
    tube([(2.2, 2.2, Z), (11.6, 11.6, Z)], 0.55, stick, segs=12)
    for i in range(bands):
        p = 2.9 + i * step
        tube([(p - 0.15, p - 0.15, Z), (p + 0.25, p + 0.25, Z)], 0.62, grip, segs=12)
    sphere(2.0, 2.0, Z, 0.75, 0.75, 0.75, cap or grip, segs=14, rings=8)


def _classic_head(head, edge, collar):
    """The default pickaxe's head: a long curved spike one way, a broad blade the other, a collar between."""
    cyl(HX, HY, Z, 0.95, 2.0, "z", collar, verts=16, bevel=0.15)
    tube([(HX, HY, Z), (HX - 2.6, HY + 2.2, Z), (HX - 5.0, HY + 3.3, Z), (HX - 6.9, HY + 3.5, Z)], [1.0, 0.85, 0.55, 0.1], head, segs=14)
    tube([(HX, HY, Z), (HX + 2.3, HY - 2.4, Z), (HX + 3.6, HY - 4.4, Z)], [1.0, 0.9, 0.72], head, segs=14)
    box(HX + 2.8, HY - 5.7, 7.2, HX + 4.6, HY - 4.1, 8.8, edge, bevel=0.35, seg=3).rot("z", -32, (HX + 3.7, HY - 4.9, Z))


def golden():
    """Royal Cleaver (was the Golden Pick): a Victory Royale battle axe, a flaring crescent of gold one way with a crown
    set in each face and a spike the other, a purple gem in the middle, a purple grip bound in gold."""
    gold = mat("pk_royal_gold", "#F2C230", rough=0.22, metal=1.0)
    bright = mat("pk_royal_edge", "#FFE58A", rough=0.12, metal=1.0)
    deep = mat("pk_royal_deep", "#B8861B", rough=0.3, metal=0.9)
    purple = mat("pk_royal_purple", "#5B2A9E", rough=0.45)
    gem = mat("pk_royal_gem", "#C77DFF", glow=True)
    _handle(purple, gold, bands=4, cap=gold, step=0.95)
    sphere(2.0, 2.0, Z, 0.42, 0.42, 0.42, gem, segs=12, rings=8).move(-0.45, -0.45, 0)
    # the blade flares from the collar out to a curved edge, its sides curving in (a crescent)
    top = [(-0.6 - 4.2 * t, 1.05 + 3.5 * t * t) for t in (i / 6 for i in range(7))]
    arc = [(-4.8 - 1.5 * math.cos(a), 4.55 * math.sin(a)) for a in (math.radians(d) for d in range(80, -81, -20))]
    blade = top + arc + [(u, -v) for u, v in reversed(top)]
    head_prism(blade, 0.42, gold, bevel=0.22)
    edge = [(-4.8 - 1.95 * math.cos(a), 5.0 * math.sin(a)) for a in (math.radians(d) for d in range(84, -85, -12))]
    edge += [(-4.8 - 1.2 * math.cos(a), 4.2 * math.sin(a)) for a in (math.radians(d) for d in range(-72, 73, 12))]
    head_prism(edge, 0.24, bright, bevel=0.12)
    # a crown on each face of the blade (the Victory Royale's), upright as you look at it
    cx, cy = at(-3.6, 0.0)
    crown = [(-1.15, -0.65), (1.15, -0.65), (1.3, 0.75), (0.62, 0.1), (0.0, 0.9), (-0.62, 0.1), (-1.3, 0.75)]
    for side in (1, -1):
        prism([(cx + a, cy + b) for a, b in crown], Z + side * 0.42, Z + side * 0.52, purple)
    # the spike the other way, the collar and the gem
    head_tube([(0.4, 0.0), (2.0, 0.25), (3.6, 0.5), (4.9, 0.6)], [0.95, 0.72, 0.42, 0.05], gold, segs=14)
    cyl(HX, HY, Z, 1.15, 2.2, "z", deep, verts=20, bevel=0.2)
    for side in (1, -1):
        sphere(HX, HY, Z + side * 1.08, 0.62, 0.62, 0.3, gem, segs=16, rings=8)


def candy():
    red, white = mat("pk_candy_red", "#E0263A", rough=0.3), mat("pk_candy_white", "#F7F4F0", rough=0.3)
    # a candy-cane handle: red and white turns all the way up
    for i in range(12):
        a, b = 2.2 + i * 0.78, 2.2 + (i + 1) * 0.78
        tube([(a, a, Z), (b, b, Z)], 0.58, red if i % 2 else white, segs=12)
    sphere(2.0, 2.0, Z, 0.8, 0.8, 0.8, red, segs=14, rings=8)
    # the head: a striped hook one way, a peppermint disc the other
    hook = [(HX, HY), (HX - 2.4, HY + 2.3), (HX - 4.7, HY + 3.1), (HX - 6.6, HY + 2.6)]
    for i in range(len(hook) - 1):
        (x0, y0), (x1, y1) = hook[i], hook[i + 1]
        tube([(x0, y0, Z), (x1, y1, Z)], [0.95 - 0.2 * i, 0.75 - 0.2 * i], red if i % 2 == 0 else white, segs=14)
    px, py = HX + 1.7, HY - 1.8  # (the peppermint sits right against the head's collar)
    cyl(px, py, Z, 1.8, 1.1, "z", white, verts=28, bevel=0.3)
    for k in range(6):  # the peppermint's red swirls
        box(px - 0.22, py, Z - 0.6, px + 0.22, py + 1.65, Z + 0.6, red, bevel=0.15).rot("z", k * 60 + 15, (px, py, Z))
    cyl(HX, HY, Z, 0.9, 1.6, "z", red, verts=16, bevel=0.2)


def diamond():
    """Prism Breaker (was the Diamond Pick): a faceted crystal axe, its blade cut like a gem round a darker heart and
    crystals growing from it, a glowing core in a gunmetal collar, a gunmetal haft with glowing bands."""
    metal = mat("pk_prism_metal", "#2A2E38", rough=0.35, metal=0.8)
    crystal = mat("pk_prism_crystal", "#4FE3E0", rough=0.08, metal=0.2)
    heart = mat("pk_prism_heart", "#1E9C9A", rough=0.15)
    glow = mat("pk_prism_glow", "#8FFFF8", glow=True)
    _handle(metal, glow, bands=3, cap=metal, step=1.2)
    # the blade: flat facets, no rounding, a darker heart inside and a thin bright rim
    blade = [(-0.8, 1.1), (-2.4, 2.7), (-4.3, 3.9), (-5.7, 2.1), (-6.1, 0.0), (-5.7, -2.1), (-4.3, -3.9), (-2.4, -2.7), (-0.8, -1.1)]
    head_prism(blade, 0.5, crystal)
    head_prism([(-1.4, 0.8), (-2.8, 1.9), (-4.2, 2.6), (-5.0, 0.0), (-4.2, -2.6), (-2.8, -1.9), (-1.4, -0.8)], 0.56, heart)
    # crystals growing from the blade's back and a crystal spike the other way
    for (u, v, ln, r, turn) in ((-3.0, 2.8, 2.0, 0.42, 30), (-4.2, 3.4, 1.5, 0.32, 55), (-3.4, -3.0, 1.7, 0.36, -40)):
        x, y = at(u, v)
        cyl(x, y, Z, r, ln, "y", crystal, verts=6, r2=0.03).rot("z", -45 + turn, (x, y, Z)).move(0, 0, 0)
    sx, sy = at(2.6, 0.2)
    cyl(sx, sy, Z, 0.85, 4.6, "y", crystal, verts=6, r2=0.04).rot("z", 45, (sx, sy, Z))
    box(HX - 1.1, HY - 1.1, Z - 1.05, HX + 1.1, HY + 1.1, Z + 1.05, metal, bevel=0.35, seg=3).rot("z", 45, (HX, HY, Z))
    for side in (1, -1):
        sphere(HX, HY, Z + side * 1.02, 0.55, 0.55, 0.22, glow, segs=6, rings=4)
def ice():
    ice_m = mat("pk_ice", "#BFEAFF", rough=0.05, metal=0.1)
    deep = mat("pk_ice_deep", "#5FB8F0", rough=0.1)
    _handle(mat("pk_frost", "#E8F6FF", rough=0.4), deep, cap=ice_m)
    cyl(HX, HY, Z, 1.0, 2.0, "z", deep, verts=8, bevel=0.1)
    # crystal shards for the spike and the blade
    for (dx, dy, ln, r, rot) in ((-1.6, 1.6, 5.8, 1.0, 135), (1.6, -1.8, 4.6, 1.15, -45), (-0.6, 2.2, 3.0, 0.6, 105), (2.2, -0.4, 2.6, 0.55, -15)):
        c = cyl(HX + dx, HY + dy, Z, r, ln, "y", ice_m if r > 0.8 else deep, verts=6, r2=0.05)
        c.rot("z", rot - 90, (HX + dx, HY + dy, Z))


def lava():
    rock = mat("pk_rock", "#4A3A34", rough=0.9)
    glow = mat("pk_lava", "#FF7A1A", glow=True)
    _handle(mat("pk_char", "#141012", rough=0.7), mat("pk_ember", "#E8461A", rough=0.5), cap=rock)
    _classic_head(rock, rock, rock)
    # glowing cracks along the spike and the blade
    # (on the faces of the spike and the blade: their half-thickness there, and a touch more)
    for side in (1, -1):
        tube([(HX - 1.0, HY + 1.0, Z + side * 1.0), (HX - 3.3, HY + 2.6, Z + side * 0.8), (HX - 5.4, HY + 3.2, Z + side * 0.42)], 0.18, glow, segs=6)
        tube([(HX + 0.8, HY - 0.8, Z + side * 1.02), (HX + 2.2, HY - 2.6, Z + side * 0.94), (HX + 3.3, HY - 4.0, Z + side * 0.78)], 0.18, glow, segs=6)
    sphere(HX, HY, Z, 0.55, 0.55, 1.15, glow, segs=12, rings=6)


def lollipop():
    stick = mat("pk_lolly_stick", "#F4F1EA", rough=0.5)
    _handle(stick, mat("pk_lolly_grip", "#FF8FC8", rough=0.5), bands=3)
    cols = [mat(f"pk_lolly{i}", c, rough=0.15) for i, c in enumerate(("#FF4FA3", "#FFD23F", "#4FD1FF", "#9B5DE5"))]
    # a big swirl on the head (rings of colour), a little blade the other way
    cyl(HX - 2.2, HY + 2.2, Z, 3.0, 1.2, "z", cols[0], verts=32, bevel=0.35)
    for i in range(4):
        torus(HX - 2.2, HY + 2.2, Z + 0.62, 0.6 + i * 0.62, 0.17, "z", cols[(i + 1) % 4], segs=28, rsegs=6)
        torus(HX - 2.2, HY + 2.2, Z - 0.62, 0.6 + i * 0.62, 0.17, "z", cols[(i + 1) % 4], segs=28, rsegs=6)
    tube([(HX, HY, Z), (HX + 2.0, HY - 2.1, Z), (HX + 3.0, HY - 3.6, Z)], [0.8, 0.7, 0.5], cols[1], segs=12)


def neon():
    dark = mat("pk_neon_body", "#1C1F2A", rough=0.35, metal=0.6)
    lit = mat("pk_neon_glow", "#3FF2FF", glow=True)
    _handle(dark, lit, bands=4)
    _classic_head(dark, lit, dark)
    tube([(HX - 0.6, HY + 1.4, Z + 0.7), (HX - 2.9, HY + 2.9, Z + 0.55), (HX - 5.2, HY + 3.7, Z + 0.3)], 0.13, lit, segs=6)
    tube([(HX - 0.6, HY + 1.4, Z - 0.7), (HX - 2.9, HY + 2.9, Z - 0.55), (HX - 5.2, HY + 3.7, Z - 0.3)], 0.13, lit, segs=6)


def riff():
    """Riff Ripper: an electric guitar swung as an axe ("axe" is a guitarist's word for a guitar): the neck is the handle,
    frets and all, the headstock with its tuning pegs at the bottom, the body (red, white scratchplate, two pickups, the
    bridge, a knob) for a head."""
    red = mat("pk_riff_red", "#D7263D", rough=0.25, metal=0.1)
    white = mat("pk_riff_white", "#F4F1EA", rough=0.4)
    wood = mat("pk_riff_neck", "#8A5A2B", rough=0.6)
    board = mat("pk_riff_board", "#3B2416", rough=0.7)
    steel = mat("pk_riff_steel", "#D8DCE4", rough=0.2, metal=1.0)
    black = mat("pk_riff_black", "#16161A", rough=0.5)
    # the neck (the handle) runs on into the body, its fretboard and frets on top
    box(0, -0.48, Z - 0.38, 14.4, 0.48, Z + 0.38, wood, bevel=0.15).rot("z", 45, (0, 0, Z)).move(2.0, 2.0, 0)
    box(0, -0.42, Z + 0.3, 10.8, 0.42, Z + 0.5, board, bevel=0.05).rot("z", 45, (0, 0, Z)).move(2.6, 2.6, 0)
    for i in range(9):
        x, y = along(0.16 + i * 0.092)
        box(-0.05, -0.44, Z + 0.46, 0.05, 0.44, Z + 0.56, steel).rot("z", 45, (0, 0, Z)).move(x, y, 0)
    # the headstock at the bottom, three pegs a side
    hs = [(0.6, 0.5), (0.95, -0.55), (-1.4, -2.2), (-2.0, -2.5), (-2.3, -1.8), (-0.6, 0.6)]
    prism([(2.2 + a, 2.2 + b) for a, b in hs], Z - 0.3, Z + 0.3, black, bevel=0.12)
    for k in range(3):
        x, y = 2.2 - 0.55 - k * 0.55, 2.2 - 0.55 - k * 0.55
        for side in (1, -1):
            cyl(x + side * 0.55 * SQ, y - side * 0.55 * SQ, Z + 0.45, 0.16, 0.6, "z", steel, verts=8)
    # the body: two horns reaching back along the neck, a waist, the round lower bout
    body = [(-0.55, 0.3), (-0.95, 0.1), (-2.1, -1.5), (-2.7, 0.9), (-2.4, 2.1), (-3.1, 3.5), (-2.5, 4.9), (-0.8, 5.5), (0.8, 5.5), (2.4, 4.9),
            (3.0, 3.5), (2.3, 2.1), (2.6, 1.1), (2.3, -1.0), (0.95, 0.0), (0.55, 0.3)]
    head_prism(body, 0.62, red, bevel=0.35)
    head_prism([(-0.8, 0.6), (-2.1, 1.0), (-2.3, 2.8), (-1.6, 4.4), (0.6, 4.6), (2.0, 3.6), (1.6, 2.2), (0.8, 0.6)], 0.05, white, z=Z + 0.66)
    for v in (1.6, 3.0):
        head_prism([(-1.5, v - 0.3), (1.5, v - 0.3), (1.5, v + 0.3), (-1.5, v + 0.3)], 0.07, black, bevel=0.03, z=Z + 0.74)
    head_prism([(-1.4, 3.9), (1.4, 3.9), (1.4, 4.35), (-1.4, 4.35)], 0.08, steel, bevel=0.03, z=Z + 0.72)
    for (u, v) in ((-2.0, 4.1), (-1.6, 3.4)):
        kx, ky = at(u, v)
        cyl(kx, ky, Z + 0.78, 0.3, 0.3, "z", black, verts=12, bevel=0.06)


def scoop():
    """Scoop Smasher: a waffle cone for a handle, a scoop of mint on top and a scoop of strawberry and a cherry for the
    head, a rolled wafer the other way, drips down the cone."""
    cone = mat("pk_scoop_cone", "#D9A35B", rough=0.7)
    waffle = mat("pk_scoop_waffle", "#A8743A", rough=0.8)
    pink = mat("pk_scoop_pink", "#FF8FB8", rough=0.55)
    mint = mat("pk_scoop_mint", "#9DF2C8", rough=0.55)
    cherry = mat("pk_scoop_cherry", "#E01E37", rough=0.2)
    wafer = mat("pk_scoop_wafer", "#E8C58C", rough=0.6)
    choc = mat("pk_scoop_choc", "#5A3420", rough=0.5)
    # the cone, wide at the head end, its waffle ridges standing out
    tube([(*along(0.0), Z), (*along(1.0), Z)], [0.3, 1.0], cone, segs=18)
    for i in range(8):
        t = 0.1 + i * 0.11
        x, y = along(t)
        torus(x, y, Z, 0.3 + 0.7 * t + 0.02, 0.08, "y", waffle, segs=18, rsegs=5).rot("z", 45, (x, y, Z))
    # a scoop of mint sitting in the cone's mouth, strawberry beside it (the blade's way), the cherry on the end
    for (u, v, r, m) in ((-0.4, 1.0, 1.55, mint), (-2.9, 0.9, 1.45, pink)):
        x, y = at(u, v)
        sphere(x, y, Z, r, r, r * 0.95, m, segs=22, rings=12)
    x, y = at(-4.75, 1.1)
    sphere(x, y, Z, 0.6, 0.6, 0.6, cherry, segs=14, rings=8)
    head_tube([(-4.9, 1.5), (-5.2, 2.2), (-5.0, 2.8)], 0.08, mat("pk_scoop_stem", "#3E7B27", rough=0.6), segs=6)
    # drips down the cone and the strawberry
    for (u, v, z, m) in ((0.55, -0.4, 0.75, mint), (-0.9, -0.6, -0.7, mint), (-2.6, -0.3, 0.8, pink), (-3.4, -0.2, -0.8, pink)):
        x, y = at(u, v)
        sphere(x, y, Z + z, 0.3, 0.55, 0.28, m, segs=10, rings=6).rot("z", -45, (x, y, Z + z))
    # the rolled wafer the other way, a chocolate spiral round it
    head_tube([(0.9, 1.2), (3.6, 1.9)], 0.32, wafer, segs=12)
    for k in range(4):
        u0 = 1.2 + k * 0.6
        head_tube([(u0, 1.28 + k * 0.15), (u0 + 0.3, 1.36 + k * 0.15)], 0.34, choc, segs=12)


def pizza():
    """Pizza Slicer: a pizza cutter's wheel for the blade, a slice of pepperoni pizza for the spike, a red grip."""
    red = mat("pk_pizza_grip", "#C8202F", rough=0.45)
    black = mat("pk_pizza_black", "#1A1A1F", rough=0.5)
    steel = mat("pk_pizza_steel", "#D5DAE2", rough=0.15, metal=1.0)
    crust = mat("pk_pizza_crust", "#D9963F", rough=0.7)
    cheese = mat("pk_pizza_cheese", "#FFD447", rough=0.5)
    pep = mat("pk_pizza_pep", "#B3261E", rough=0.4)
    _handle(red, black, bands=5, cap=black)
    # the wheel, its hub and the fork that holds it
    x, y = at(-3.4, 0.0)
    cyl(x, y, Z, 3.0, 0.28, "z", steel, verts=40, bevel=0.1)
    cyl(x, y, Z, 0.75, 0.9, "z", black, verts=16, bevel=0.15)
    for side in (1, -1):
        head_prism([(-0.6, -0.7), (-3.4, -0.45), (-3.4, 0.45), (-0.6, 0.7)], 0.12, steel, bevel=0.05, z=Z + side * 0.42)
    # the slice: crust along the back, cheese on top, three pepperoni
    head_prism([(0.5, -1.2), (0.5, 1.2), (4.9, 0.0)], 0.42, cheese, bevel=0.15)
    head_tube([(0.55, -1.35), (0.65, 0.0), (0.55, 1.35)], 0.42, crust, segs=12)
    for (u, v) in ((1.7, -0.45), (2.2, 0.5), (3.4, 0.0)):
        for side in (1, -1):
            px, py = at(u, v)
            cyl(px, py, Z + side * 0.44, 0.38, 0.12, "z", pep, verts=14, bevel=0.03)
    cyl(HX, HY, Z, 0.9, 1.4, "z", black, verts=16, bevel=0.2)


def boom():
    """Boom Pick: a firework rocket for a head, fins and all, with a lit fuse the other way and a hazard-striped
    handle."""
    yellow = mat("pk_boom_yellow", "#FFD23A", rough=0.4)
    black = mat("pk_boom_black", "#1A1A1F", rough=0.5)
    red = mat("pk_boom_red", "#E0312B", rough=0.35)
    white = mat("pk_boom_white", "#F4F4F0", rough=0.4)
    blue = mat("pk_boom_blue", "#2E6BE6", rough=0.4)
    fuse = mat("pk_boom_fuse", "#7A5A3A", rough=0.8)
    spark = mat("pk_boom_spark", "#FFB23A", glow=True)
    # hazard stripes up the handle
    for i in range(12):
        a, b = along(i / 12.0), along((i + 1) / 12.0)
        tube([(*a, Z), (*b, Z)], 0.56, yellow if i % 2 else black, segs=12)
    sphere(2.0, 2.0, Z, 0.78, 0.78, 0.78, black, segs=14, rings=8)
    # the rocket across the head: body, white band, nose cone, three fins
    head_tube([(0.9, 0.0), (-4.2, 0.0)], 1.05, red, segs=20)
    head_tube([(-1.6, 0.0), (-2.2, 0.0)], 1.08, white, segs=20)
    x, y = at(-4.2, 0.0)
    nx, ny = at(-6.4, 0.0)
    tube([(x, y, Z), (nx, ny, Z)], [1.05, 0.05], white, segs=20)
    for k in range(3):
        a = math.radians(90 + k * 120)
        dv, dz = math.cos(a), math.sin(a)
        pts = [(0.9, dv * 0.9), (-0.6, dv * 0.9), (0.9, dv * 2.2), (1.4, dv * 2.3)]
        head_prism(pts, 0.1, blue, bevel=0.04, z=Z + dz * 0.9)
    # the fuse the other way, sparking
    head_tube([(1.0, 0.0), (2.0, 0.4), (2.8, -0.2), (3.6, 0.3)], 0.14, fuse, segs=8)
    x, y = at(3.75, 0.35)
    sphere(x, y, Z, 0.42, 0.42, 0.42, spark, segs=8, rings=6)
    cyl(HX, HY, Z, 1.15, 2.4, "z", black, verts=16, bevel=0.2)


def storm():
    """Storm Splitter: a forked lightning bolt for a blade, crackling purple in dark iron, the storm's colours."""
    iron = mat("pk_storm_iron", "#2B2638", rough=0.4, metal=0.8)
    bolt = mat("pk_storm_bolt", "#C9A2FF", glow=True)
    purple = mat("pk_storm_purple", "#6A2FD8", rough=0.35)
    _handle(iron, purple, bands=4, cap=iron)
    for t in (0.3, 0.55, 0.8):
        x, y = along(t)
        torus(x, y, Z, 0.6, 0.08, "y", bolt, segs=16, rsegs=5).rot("z", 45, (x, y, Z))
    # the iron back of the blade, then the bolt: a zigzag out to a fork
    head_prism([(-0.8, 1.2), (-2.4, 1.9), (-3.4, 1.0), (-3.4, -1.0), (-2.4, -1.9), (-0.8, -1.2)], 0.5, iron, bevel=0.2)
    zig = [(-2.6, 1.6), (-4.0, 2.9), (-4.6, 1.4), (-5.9, 2.6), (-6.4, 3.8), (-6.9, 1.6), (-5.6, 0.2), (-6.9, -1.4), (-6.3, -3.6),
           (-5.6, -2.0), (-4.5, -2.9), (-4.1, -1.1), (-2.6, -1.6)]
    head_prism(zig, 0.32, bolt)
    head_prism(zig, 0.18, purple, z=Z)
    # a short jagged spike the other way
    head_prism([(0.6, -0.8), (2.5, -0.5), (2.1, 0.0), (4.6, 0.2), (2.3, 0.7), (2.6, 1.1), (0.6, 0.8)], 0.38, iron, bevel=0.12)
    cyl(HX, HY, Z, 1.1, 2.2, "z", iron, verts=8, bevel=0.15)
    for side in (1, -1):
        sphere(HX, HY, Z + side * 1.05, 0.5, 0.5, 0.25, bolt, segs=12, rings=6)


def bone():
    """Bone Breaker: a bone for a handle, a grinning skull in the middle of the head, a curved bone blade one way and a
    fang the other."""
    bone_m = mat("pk_bone", "#EDE6D6", rough=0.6)
    shade = mat("pk_bone_shade", "#B9AE96", rough=0.7)
    dark = mat("pk_bone_dark", "#1E1A18", rough=0.6)
    # the handle: a long bone with knobbly ends
    tube([(*along(0.04), Z), (*along(0.9), Z)], [0.5, 0.42, 0.42, 0.5], bone_m, segs=14)
    for off in (0.45, -0.45):
        x, y = along(0.02, off)
        sphere(x, y, Z, 0.55, 0.55, 0.55, bone_m, segs=14, rings=8)
    # the blade: two curved bones fused, a ridge along it
    head_tube([(-0.6, 0.6), (-2.6, 1.9), (-4.6, 2.3), (-6.0, 1.2), (-6.3, -0.4)], [0.75, 0.85, 0.8, 0.6, 0.3], bone_m, segs=14)
    head_prism([(-0.6, 0.4), (-2.6, 1.6), (-4.5, 1.9), (-5.7, 1.0), (-5.9, -0.6), (-4.6, -1.8), (-2.6, -1.4), (-0.6, -0.4)], 0.32, bone_m, bevel=0.15)
    head_tube([(-1.0, -0.2), (-3.0, 0.2), (-4.6, 0.3)], 0.12, shade, segs=6, z=Z + 0.33)
    # the fang the other way
    head_tube([(0.8, 0.0), (2.5, 0.4), (4.3, 1.1)], [0.75, 0.5, 0.05], bone_m, segs=12)
    # the skull: a round cranium, a jaw, dark eyes and nose
    sphere(HX, HY, Z, 1.35, 1.25, 1.2, bone_m, segs=20, rings=12)
    jx, jy = at(0.0, -0.95)
    box(jx - 0.7, jy - 0.5, Z - 0.75, jx + 0.7, jy + 0.35, Z + 0.75, bone_m, bevel=0.25).rot("z", 45, (jx, jy, Z))
    for side in (1, -1):
        for du in (0.48, -0.48):
            ex, ey = at(du, 0.15)
            sphere(ex, ey, Z + side * 1.05, 0.32, 0.32, 0.2, dark, segs=12, rings=6)
        nx, ny = at(0.0, -0.4)
        sphere(nx, ny, Z + side * 1.12, 0.14, 0.18, 0.1, dark, segs=8, rings=4)


def bullseye():
    """Bullseye Breaker: a target for a hammer face, red and white rings, and a steel arrowhead for the blade, the
    shooting range's own colours."""
    red = mat("pk_eye_red", "#E0262F", rough=0.4)
    white = mat("pk_eye_white", "#F6F4EE", rough=0.4)
    steel = mat("pk_eye_steel", "#C6CCD6", rough=0.18, metal=1.0)
    wood = mat("pk_eye_wood", "#8A5A2B", rough=0.6)
    _handle(white, red, bands=6, cap=red, step=0.7)
    # the target: rings stepping out, on a stem from the head (the spike's side)
    head_tube([(0.5, 0.0), (2.0, 0.0)], 0.5, wood, segs=12)
    for k, (r, m) in enumerate(((2.25, red), (1.75, white), (1.25, red), (0.75, white), (0.3, red))):
        cx, cy = at(2.6 + k * 0.08, 0.0)
        cyl(cx, cy, Z, r, 0.8 - k * 0.0, "y", m, verts=36, bevel=0.06).rot("z", 45, (cx, cy, Z))
    # the arrowhead blade, its bevelled edges brighter
    head_prism([(-0.7, 0.9), (-3.0, 2.6), (-6.4, 0.0), (-3.0, -2.6), (-0.7, -0.9)], 0.4, steel, bevel=0.25)
    head_prism([(-1.0, 0.0), (-3.0, 1.4), (-5.2, 0.0), (-3.0, -1.4)], 0.46, mat("pk_eye_dark", "#7F8794", rough=0.3, metal=1.0))
    cyl(HX, HY, Z, 1.0, 1.9, "z", red, verts=16, bevel=0.2)


def flint():
    """Flint Hunter: a knapped flint blade lashed to a wooden haft with leather, a feather tied on: a hunter's axe."""
    wood = mat("pk_flint_wood", "#7A5230", rough=0.75)
    leather = mat("pk_flint_leather", "#4A2F1E", rough=0.8)
    flint_m = mat("pk_flint", "#5D6B78", rough=0.35, metal=0.1)
    flint_hi = mat("pk_flint_hi", "#8C9AA8", rough=0.3)
    feather = mat("pk_flint_feather", "#E8E0D0", rough=0.6)
    feather_tip = mat("pk_flint_feather_tip", "#C0392B", rough=0.6)
    tube([(*along(0.0), Z), (*along(1.08), Z)], [0.5, 0.55, 0.62], wood, segs=12)
    for t in (0.08, 0.16, 0.24, 0.32):
        x, y = along(t)
        torus(x, y, Z, 0.58, 0.12, "y", leather, segs=14, rsegs=5).rot("z", 45, (x, y, Z))
    sphere(2.1, 2.1, Z, 0.62, 0.62, 0.62, wood, segs=12, rings=8)
    # the blade: chipped facets (flat, no rounding), a lighter knapped edge
    blade = [(-0.4, 1.6), (-2.1, 2.5), (-3.6, 3.3), (-5.2, 2.6), (-5.6, 1.3), (-6.3, 0.2), (-5.7, -1.0), (-5.6, -2.4), (-4.0, -3.2),
             (-2.3, -2.4), (-0.4, -1.6)]
    head_prism(blade, 0.42, flint_m)
    head_prism([(-3.6, 3.0), (-5.0, 2.4), (-5.3, 1.2), (-5.9, 0.2), (-5.4, -0.9), (-5.3, -2.2), (-3.9, -2.9), (-4.5, 0.0)], 0.5, flint_hi)
    # the lashing round the head, criss-crossed, and a feather hanging from it down the haft
    for k in range(4):
        head_tube([(-0.3, -1.4 + k * 0.9), (0.6, -1.0 + k * 0.9)], 0.16, leather, segs=6, z=[Z + 0.55, Z - 0.55])
        head_tube([(-0.3, -1.0 + k * 0.9), (0.6, -1.4 + k * 0.9)], 0.16, leather, segs=6, z=[Z - 0.55, Z + 0.55])
    head_tube([(0.7, -0.9), (1.05, -1.8), (1.15, -2.6)], 0.1, leather, segs=6, z=Z + 0.45)
    fx, fy = at(1.2, -3.55)
    sphere(fx, fy, Z + 0.45, 0.34, 1.05, 0.1, feather, segs=12, rings=8).rot("z", -45, (fx, fy, Z))
    tx, ty = at(1.25, -4.45)
    sphere(tx, ty, Z + 0.45, 0.26, 0.42, 0.11, feather_tip, segs=10, rings=6).rot("z", -45, (tx, ty, Z))


VIEWS = {"Front": 0, "Right": 270, "Back": 180, "Left": 90}


def _rows():
    import json, os
    here = os.path.dirname(os.path.abspath(__file__))
    rows = json.load(open(os.path.join(here, "..", "..", "..", "design", "cosmetics.json"), encoding="utf-8"))["rows"]
    return {r["id"]: r for r in rows if r["slot"] == "pickaxe"}


def _entry(rid, fn):
    """The Locker pictures' way (creator: every render named, with what unlocks it or its price): its name and unlock
    from the cosmetics sheet, into Locker Items v2.0.0/New Pickaxes (compose.py into Locker Screenshots)."""
    from gliders import unlock_text
    row = _rows()[rid]
    return (row["name"], fn, {"icon": (0, 0), "ao": 1.5, "folder": "New Pickaxes", "views": VIEWS, "unlock": unlock_text(row)})


ITEMS = {
    "pickaxe_golden": _entry("pickaxe_golden", golden),
    "pickaxe_candy": _entry("pickaxe_candy", candy),
    "pickaxe_diamond": _entry("pickaxe_diamond", diamond),
    "pickaxe_ice": _entry("pickaxe_ice", ice),
    "pickaxe_lava": _entry("pickaxe_lava", lava),
    "pickaxe_lollipop": _entry("pickaxe_lollipop", lollipop),
    "pickaxe_neon": _entry("pickaxe_neon", neon),
    "pickaxe_riff": _entry("pickaxe_riff", riff),
    "pickaxe_scoop": _entry("pickaxe_scoop", scoop),
    "pickaxe_pizza": _entry("pickaxe_pizza", pizza),
    "pickaxe_boom": _entry("pickaxe_boom", boom),
    "pickaxe_storm": _entry("pickaxe_storm", storm),
    "pickaxe_bone": _entry("pickaxe_bone", bone),
    "pickaxe_bullseye": _entry("pickaxe_bullseye", bullseye),
    "pickaxe_flint": _entry("pickaxe_flint", flint),
}
