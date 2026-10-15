"""
The HUD's own art, made in Blender (v2.0.0, creator: "Cleaner UI (taken from Fortnite and re-rendered smoother)"; "All
animations and renders should be done in blender"). Fortnite's HUD lives in its encrypted paks (never opened), so these
are our own, in its spirit: smooth shaded icons (shield, health, the storm, players left, eliminations, the team, wood,
brick and metal, the five ammo types, gold) and the HUD's lettering, a bold condensed face (Inter: the open-licence font
Blender ships, OFL) rendered into glyph atlases. client/Smooth.java draws both with smooth filtering, at any size.

blender --background --factory-startup --python hud_art.py -- <scratch dir>
    -> fabric/src/client/resources/assets/minebloxroyale/textures/gui/hud/<icon>.png, hud_font_<em>.png and
       hud_font.json (each texture with a .mcmeta: smooth filtering), and the icons, named, into Model Renders
       (Locker Items v2.0.0/HUD Icons)
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", "..", ".."))
OUT = os.path.join(ROOT, "fabric", "src", "client", "resources", "assets", "minebloxroyale", "textures", "gui", "hud")
RENDERS = os.path.join(ROOT, "Model Renders", "Locker Screenshots", "Locker Items v2.0.0", "HUD Icons")
FONT = "C:/Program Files/Blender Foundation/Blender 5.2/5.2/datafiles/fonts/Inter.woff2"
ICON_PX = 128
FONT_EMS = (32, 64)  # two atlases: small text and big numbers, each drawn near its own size so it stays crisp
FONT_RENDER_EM = 128  # (rendered at this, made bolder, then scaled down to each)
CHARS = "".join(chr(c) for c in range(32, 127))
COLS = 16
CONDENSE, BOLD = 0.86, 0.022  # (narrower, and heavier by this much of an em all round, like a HUD face)


def main(scratch):
    import bmesh
    import bpy
    from mathutils import Vector

    sys.path.insert(0, HERE)
    import brlib
    from brlib import box, cyl, mat, sphere, torus, tube

    os.makedirs(OUT, exist_ok=True)
    os.makedirs(RENDERS, exist_ok=True)
    os.makedirs(scratch, exist_ok=True)

    def slab(points, z0, z1, material, bevel=0.06):
        """A flat shape (outline points x, y in Minecraft space) from z0 to z1, its edges rounded."""
        me = bpy.data.meshes.new("slab")
        bm = bmesh.new()
        lo = [bm.verts.new(brlib.P(x, y, z0)) for x, y in points]
        hi = [bm.verts.new(brlib.P(x, y, z1)) for x, y in points]
        bm.faces.new(lo[::-1])
        bm.faces.new(hi)
        n = len(points)
        for i in range(n):
            bm.faces.new((lo[i], lo[(i + 1) % n], hi[(i + 1) % n], hi[i]))
        bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
        bm.to_mesh(me)
        bm.free()
        o = bpy.data.objects.new("slab", me)
        bpy.context.scene.collection.objects.link(o)
        o.data.materials.append(material)
        if bevel:
            m = o.modifiers.new("bevel", "BEVEL")
            m.width = bevel
            m.segments = 4
            m.limit_method = "ANGLE"
        for p in o.data.polygons:
            p.use_smooth = True
        return o

    def scaled(points, k, dx=0.0, dy=0.0):
        return [(x * k + dx, y * k + dy) for x, y in points]

    SHIELD = [(-1, 1.08), (-0.5, 1.18), (0, 1.24), (0.5, 1.18), (1, 1.08), (1, 0.25), (0.82, -0.42), (0.45, -0.95), (0, -1.25),
              (-0.45, -0.95), (-0.82, -0.42), (-1, 0.25)]

    def shield():
        slab(SHIELD, -0.2, 0.2, mat("hud_shield", "#2E86FF", rough=0.25, metal=0.2), bevel=0.1)
        slab(scaled(SHIELD, 0.66, 0, 0.02), 0.2, 0.34, mat("hud_shield_in", "#8FD0FF", rough=0.2, glow=False), bevel=0.06)

    def health():
        g = mat("hud_health", "#38D74A", rough=0.3)
        box(-0.36, -1.05, -0.25, 0.36, 1.05, 0.25, g, bevel=0.16, seg=4)
        box(-1.05, -0.36, -0.25, 1.05, 0.36, 0.25, g, bevel=0.16, seg=4)
        w = mat("hud_health_in", "#C8FFC8", rough=0.3)
        box(-0.14, -0.7, 0.22, 0.14, 0.7, 0.34, w, bevel=0.06, seg=3)
        box(-0.7, -0.14, 0.22, 0.7, 0.14, 0.34, w, bevel=0.06, seg=3)

    def storm():
        torus(0, 0, 0, 1.0, 0.2, "z", mat("hud_storm", "#B45CFF", rough=0.3), segs=48, rsegs=12)
        cyl(0, 0, -0.1, 0.78, 0.2, "z", mat("hud_storm_dark", "#3A1270", rough=0.5), verts=48, bevel=0.05)
        cyl(0, 0, 0.0, 0.42, 0.22, "z", mat("hud_storm_eye", "#E6A8FF", rough=0.2, glow=True), verts=40, bevel=0.06)
        sphere(0, 0, 0.12, 0.16, 0.16, 0.12, mat("hud_storm_pupil", "#1A0533", rough=0.4), segs=20, rings=10)

    def person(dx=0.0, color="#F2F4F8", name="hud_person"):
        m = mat(name, color, rough=0.35)
        sphere(dx, 0.62, 0, 0.36, 0.36, 0.3, m, segs=28, rings=16)
        box(dx - 0.62, -1.0, -0.26, dx + 0.62, 0.18, 0.26, m, bevel=0.3, seg=6)

    def players():
        person()

    def team():
        person(0.5, "#5FA8E0", "hud_team_back")
        for o in bpy.context.scene.objects:
            if o.type == "MESH":
                o.location.y += 0.4  # (Blender +y: further from the camera)
        person(-0.45, "#8FD3FF", "hud_team")

    def kills():
        r = mat("hud_kill", "#FF4A4A", rough=0.3)
        torus(0, 0, 0, 0.82, 0.11, "z", r, segs=48, rsegs=10)
        for a in range(4):
            ang = math.radians(a * 90)
            cx, cy = math.cos(ang), math.sin(ang)
            tube([(cx * 0.45, cy * 0.45, 0), (cx * 1.15, cy * 1.15, 0)], 0.1, r, segs=10)
        sphere(0, 0, 0, 0.13, 0.13, 0.13, mat("hud_kill_dot", "#FFFFFF", rough=0.3), segs=16, rings=8)

    def wood():
        b = mat("hud_wood", "#A8703A", rough=0.7)
        d = mat("hud_wood_dark", "#7A4D24", rough=0.8)
        for i, y in enumerate((-0.55, 0.0, 0.55)):
            box(-1.1, y - 0.24, -0.3 + i * 0.06, 1.1, y + 0.24, 0.3, b, bevel=0.06, seg=2)
            for k in (-0.5, 0.25, 0.7):
                box(-1.0 + i * 0.1, y + k * 0.2 - 0.02, 0.29, 0.6 + i * 0.12, y + k * 0.2 + 0.02, 0.32, d)

    def brick():
        r = mat("hud_brick", "#C8553A", rough=0.75)
        for row, y in enumerate((-0.55, 0.0, 0.55)):
            # (every other row laid half a brick along: half bricks at its ends)
            spans = [(-1.23, -0.43), (-0.41, 0.39), (0.41, 1.21)] if row % 2 == 0 else [(-1.23, -0.84), (-0.82, -0.02), (0.0, 0.8), (0.82, 1.21)]
            for x0, x1 in spans:
                box(x0, y - 0.23, -0.3, x1, y + 0.23, 0.3, r, bevel=0.07, seg=2)
        box(-1.25, -0.82, -0.25, 1.25, 0.82, 0.2, mat("hud_mortar", "#E8DCC8", rough=0.9))

    def metal():
        m = mat("hud_metal", "#A9B3BF", rough=0.3, metal=0.9)
        box(-1.05, -0.8, -0.18, 1.05, 0.8, 0.18, m, bevel=0.14, seg=4)
        box(-0.85, -0.6, 0.12, 0.85, 0.6, 0.24, mat("hud_metal_in", "#C9D1DA", rough=0.25, metal=0.9), bevel=0.06, seg=3)
        for x in (-0.82, 0.82):
            for y in (-0.58, 0.58):
                sphere(x, y, 0.22, 0.1, 0.1, 0.08, mat("hud_rivet", "#6E7884", rough=0.3, metal=0.9), segs=14, rings=8)

    def bullet(x, y, length, r, casing, tip):
        cyl(x, y, 0, r, length, "y", mat("hud_case_" + casing, casing, rough=0.25, metal=0.8), verts=24, bevel=0.03)
        cyl(x, y + length / 2 + length * 0.18, 0, r * 0.98, length * 0.36, "y", mat("hud_tip_" + tip, tip, rough=0.3, metal=0.7), verts=24,
            r2=r * 0.25)

    def ammo_light():
        for x in (-0.55, 0.0, 0.55):
            bullet(x, -0.1, 1.1, 0.2, "#D9DDE3", "#8FB8D8")

    def ammo_medium():
        for x in (-0.38, 0.38):
            bullet(x, -0.1, 1.4, 0.26, "#E3B04B", "#9CC86A")

    def ammo_heavy():
        bullet(0, -0.15, 1.8, 0.36, "#E3B04B", "#E05A4A")

    def ammo_shells():
        for x in (-0.4, 0.4):
            cyl(x, 0.15, 0, 0.32, 1.3, "y", mat("hud_shell", "#D63A2E", rough=0.4), verts=28, bevel=0.04)
            cyl(x, -0.62, 0, 0.34, 0.3, "y", mat("hud_shell_base", "#E3B04B", rough=0.25, metal=0.85), verts=28, bevel=0.04)

    def ammo_rockets():
        body = mat("hud_rocket", "#7D8A6A", rough=0.4)
        cyl(0, -0.1, 0, 0.3, 1.6, "y", body, verts=28, bevel=0.03)
        cyl(0, 0.95, 0, 0.3, 0.55, "y", mat("hud_rocket_tip", "#D84A3A", rough=0.35), verts=28, r2=0.02)
        for a in range(4):
            ang = a * 90 + 45
            box(-0.04, -1.0, 0, 0.04, -0.45, 0.55, body, bevel=0.02).rot("y", ang, (0, 0, 0))

    def gold():
        g = mat("hud_gold", "#F2C230", rough=0.2, metal=1.0)
        cyl(0, 0, 0, 1.0, 0.3, "z", g, verts=56, bevel=0.1)
        cyl(0, 0, 0.12, 0.74, 0.12, "z", mat("hud_gold_in", "#FFDF73", rough=0.2, metal=1.0), verts=56, bevel=0.04)
        star = [(math.cos(math.radians(90 + i * 36)) * (0.5 if i % 2 == 0 else 0.21), math.sin(math.radians(90 + i * 36)) * (0.5 if i % 2 == 0 else 0.21))
                for i in range(10)]
        slab(star, 0.16, 0.26, mat("hud_gold_star", "#B8861B", rough=0.3, metal=1.0), bevel=0.02)

    def boss():
        """A boss on the map: a skull in a golden crown, on a dark red badge."""
        cyl(0, -0.1, -0.35, 1.18, 0.25, "z", mat("hud_boss_badge", "#6E1420", rough=0.4), verts=48, bevel=0.08)
        torus(0, -0.1, -0.2, 1.18, 0.09, "z", mat("hud_boss_rim", "#F2C230", rough=0.25, metal=0.9), segs=48, rsegs=8)
        bone = mat("hud_bone", "#F4F1E8", rough=0.45)
        sphere(0, 0.0, 0, 0.62, 0.58, 0.45, bone, segs=32, rings=16)
        box(-0.36, -0.68, -0.25, 0.36, -0.28, 0.3, bone, bevel=0.12, seg=3)
        dark = mat("hud_socket", "#1A0B0E", rough=0.6)
        for sx in (-0.24, 0.24):
            sphere(sx, -0.02, 0.36, 0.16, 0.18, 0.1, dark, segs=20, rings=10)
        cyl(0, -0.3, 0.38, 0.07, 0.06, "z", dark, verts=12, r2=0.0)
        for tx in (-0.2, -0.07, 0.07, 0.2):
            box(tx - 0.04, -0.66, 0.28, tx + 0.04, -0.5, 0.33, dark)
        gold = mat("hud_crown", "#F2C230", rough=0.2, metal=1.0)
        box(-0.5, 0.42, -0.2, 0.5, 0.62, 0.2, gold, bevel=0.05, seg=2)
        for k, tx in enumerate((-0.42, 0.0, 0.42)):
            h = 0.42 if k == 1 else 0.32
            cyl(tx, 0.62 + h / 2, 0, 0.12, h, "y", gold, verts=4, r2=0.01)
            sphere(tx, 0.62 + h + 0.05, 0, 0.07, 0.07, 0.07, mat("hud_crown_gem", "#E8263A", rough=0.15), segs=12, rings=8)

    ICONS = {
        "shield": ("Shield", shield), "health": ("Health", health), "storm": ("Storm", storm), "players": ("Players Left", players),
        "kills": ("Eliminations", kills), "team": ("Team Eliminations", team), "wood": ("Wood", wood), "brick": ("Brick", brick),
        "metal": ("Metal", metal), "ammo_light": ("Light Ammo", ammo_light), "ammo_medium": ("Medium Ammo", ammo_medium),
        "ammo_heavy": ("Heavy Ammo", ammo_heavy), "ammo_shells": ("Shells", ammo_shells), "ammo_rockets": ("Rockets", ammo_rockets),
        "gold": ("Gold", gold), "boss": ("Boss", boss),
    }
    only = set(sys.argv[sys.argv.index("--") + 2:]) if "--" in sys.argv else set()
    made = []
    for key, (label, fn) in ICONS.items():
        if only and key not in only:
            continue
        brlib.reset()
        brlib._mats.clear()
        s = bpy.context.scene
        s.render.film_transparent = True
        fn()
        path = os.path.join(OUT, key + ".png")
        brlib.render_icon(path, yaw=-18, pitch=14, size=ICON_PX)
        mcmeta(path)
        big = os.path.join(scratch, key + "_big.png")
        brlib.render_icon(big, yaw=-18, pitch=14, size=256)
        made.append((key, label, big))
        print("ICON", key, flush=True)
    if not only or "font" in only:
        font_atlases(bpy, Vector)
    with open(os.path.join(scratch, "icons.json"), "w", encoding="utf-8") as f:
        json.dump(made, f)


def mcmeta(png):
    """Smooth filtering for a texture (no blocky pixels when it's drawn bigger or smaller)."""
    with open(png + ".mcmeta", "w", encoding="utf-8", newline="\n") as f:
        json.dump({"texture": {"blur": True, "clamp": True}}, f)


def font_atlases(bpy, Vector):
    """Every printable ASCII letter of the HUD face in a grid (COLS across), white on clear: rendered large, made bolder
    (each letter grown by BOLD of an em all round: thickening the outline itself tangles letters with holes), then scaled
    down to each em size; and its metrics: how far each letter moves the pen on (ems), the cell size and baseline."""
    import numpy as np

    import brlib
    brlib.reset()
    s = bpy.context.scene
    s.render.engine = "BLENDER_EEVEE"
    s.render.film_transparent = True
    s.view_settings.view_transform = "Standard"
    font = bpy.data.fonts.load(FONT)
    white = bpy.data.materials.new("glyph")
    white.use_nodes = True
    nt = white.node_tree
    for n in list(nt.nodes):
        nt.nodes.remove(n)
    em_node = nt.nodes.new("ShaderNodeEmission")
    em_node.inputs["Color"].default_value = (1, 1, 1, 1)
    em_node.inputs["Strength"].default_value = 1.0
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    nt.links.new(em_node.outputs["Emission"], out.inputs["Surface"])

    def text(body):
        cu = bpy.data.curves.new("t", "FONT")
        cu.body = body
        cu.font = font
        cu.size = 1.0
        o = bpy.data.objects.new("t", cu)
        o.scale.x = CONDENSE
        s.collection.objects.link(o)
        o.data.materials.append(white)
        return o

    def width(body):
        o = text(body)
        bpy.context.view_layer.update()
        w = o.dimensions.x
        bpy.data.objects.remove(o)
        return w

    adv = {}
    for c in CHARS:
        adv[c] = (width("x x") - width("xx")) if c == " " else (width(c + c) - width(c))
        adv[c] += 2 * BOLD if c != " " else 0  # (a bolder letter takes that much more room)
    # the grid: cells 1.25 em wide, 1.5 em tall, the baseline 0.42 em up from a cell's bottom, 0.14 em in from its left
    cw, ch, base, pad = 1.25, 1.5, 0.42, 0.14
    rows = (len(CHARS) + COLS - 1) // COLS
    for i, c in enumerate(CHARS):
        if c == " ":
            continue
        o = text(c)
        col, row = i % COLS, i // COLS
        o.location = (col * cw + pad + BOLD, (rows - 1 - row) * ch + base, 0)
    W, H = COLS * cw, rows * ch
    cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam"))
    cam.data.type = "ORTHO"
    cam.data.ortho_scale = W
    cam.location = (W / 2, H / 2, 10)
    s.collection.objects.link(cam)
    s.camera = cam
    big = FONT_RENDER_EM
    s.render.resolution_x, s.render.resolution_y = round(W * big), round(H * big)
    raw = os.path.join(OUT, "_hud_font_raw.png")
    s.render.filepath = raw
    bpy.ops.render.render(write_still=True)
    img = bpy.data.images.load(raw)
    w, h = img.size
    a = np.array(img.pixels[:], dtype=np.float32).reshape(h, w, 4)[:, :, 3]
    bpy.data.images.remove(img)
    os.remove(raw)
    # bolder: each pixel the most of its neighbours within r (a round brush), keeping the soft edge
    r = BOLD * big
    ri = int(math.ceil(r))
    grown = a.copy()
    for dy in range(-ri, ri + 1):
        for dx in range(-ri, ri + 1):
            d = math.hypot(dx, dy)
            if d > r + 0.5:
                continue
            k = min(1.0, r + 0.5 - d)  # (the brush's rim counts partly: smooth)
            grown = np.maximum(grown, np.roll(np.roll(a, dy, 0), dx, 1) * k)
    for em in FONT_EMS:
        f = big // em
        small = grown.reshape(h // f, f, w // f, f).mean(axis=(1, 3))
        px = np.ones((h // f, w // f, 4), dtype=np.float32)
        px[:, :, 3] = small
        im = bpy.data.images.new(f"font{em}", w // f, h // f, alpha=True)
        im.pixels = px.ravel()
        im.filepath_raw = os.path.join(OUT, f"hud_font_{em}.png")
        im.file_format = "PNG"
        im.save()
        bpy.data.images.remove(im)
        mcmeta(os.path.join(OUT, f"hud_font_{em}.png"))
    with open(os.path.join(OUT, "..", "..", "..", "hud_font.json"), "w", encoding="utf-8", newline=chr(10)) as f:
        json.dump({"about": "The HUD face's metrics (fabric/tools/blender/hud_art.py): chars in grid order, cols across; cell size, "
                            "baseline (from a cell's bottom) and pad (from its left to the pen) in ems; each char's advance in ems; "
                            "atlases at these ems (pixels per em): textures/gui/hud/hud_font_<em>.png",
                   "chars": CHARS, "cols": COLS, "cell": [cw, ch], "baseline": base, "pad": pad, "ems": list(FONT_EMS),
                   "advance": [round(adv[c], 4) for c in CHARS]}, f, indent=1)
    print("FONT", len(CHARS), "glyphs", flush=True)


if __name__ == "__main__":
    main(sys.argv[sys.argv.index("--") + 1] if "--" in sys.argv else os.path.join(HERE, "_hud_scratch"))
