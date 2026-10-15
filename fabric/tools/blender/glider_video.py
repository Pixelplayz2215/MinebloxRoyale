"""
Gliders opening, gliding and being put away, and contrails (v2.0.0, creator: "lets add glider animations. Deploy
annimation, actual gliding (with a contrail (also unlockable and purchaseBle contrails) as well as putting gliders
away ... All animations and renders should be done in blender").

The glider's own motion is keyframed here (GLIDER_ANIMS) as Blender F-curves (smooth Bezier, like the emotes) and
sampled into design/glider_keys.json, which the game plays (client/avatar/Gliders.java): deploy (pulled out of the
back, swung up over the head and popping open with a little overshoot), glide (a gentle sway, looping) and stow (folded
and pulled back into the back on landing). Each is a transform about the glider's crossbar in Roblox root space (studs,
y up from the hips, facing -z): scale sx sy sz, moved y (up) and z (back), turned yaw, pitch (+: swung back) and roll.

Contrails (cosmetics sheet, slot contrail; shape = the look, CONTRAILS): light trails streaming from the hands while
skydiving (the body falls upright here, so trails from the feet would rise through it) and from the glider's wingtips while gliding, drawn by the game the same way (client/avatar/Contrails.java).

blender --background --factory-startup --python glider_video.py -- sheet
blender --background --factory-startup --python glider_video.py -- gliders <frames dir> [glider ids]
blender --background --factory-startup --python glider_video.py -- contrails <frames dir> [contrail ids]
blender --background --factory-startup --python glider_video.py -- boosttrails <frames dir> [boost trail ids]
    (2.0.0-test3: each car boost trail on the sports car, seen from behind and the side as the Locker shows it)
python emote_video.py videos <frames dir> <out dir>      (the titled MP4s, and a titled still of each)
"""
import colorsys
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
DESIGN = os.path.join(HERE, "..", "..", "..", "design")
FPS = 30
SAMPLES = 32  # sheet rows per animation


class K:
    def __init__(self, t, **ch):
        self.t, self.ch = t, ch


class Anim:
    def __init__(self, seconds, keys, cyclic=False):
        self.seconds, self.keys, self.cyclic = seconds, keys, cyclic


REST = {"sx": 1.0, "sy": 1.0, "sz": 1.0, "y": 0.0, "z": 0.0, "pitch": 0.0, "roll": 0.0, "yaw": 0.0}
GLIDER_ANIMS = {
    # out of the back (tiny, swung back behind the shoulders), shooting up over the head and popping open past full
    # size, settling
    "deploy": Anim(0.6, [
        K(0, sx=0.04, sy=0.04, sz=0.04, y=-2.6, z=0.9, pitch=75, roll=0, yaw=0),
        K(0.22, sx=0.28, sy=0.8, sz=0.4, y=-1.1, z=0.55, pitch=40),
        K(0.48, sx=1.12, sy=1.05, sz=1.1, y=0.15, z=0.0, pitch=-6),
        K(0.72, sx=0.96, sy=0.98, sz=0.97, y=-0.05, z=0.0, pitch=2),
        K(1, sx=1, sy=1, sz=1, y=0, z=0, pitch=0, roll=0, yaw=0),
    ]),
    # riding the air: a slow sway side to side, the nose lifting and dipping a little, bobbing
    "glide": Anim(2.4, [
        K(0, roll=-3.0, pitch=1.2, y=0.05, yaw=-1.5),
        K(0.25, roll=0.0, pitch=-0.8, y=-0.04, yaw=0.0),
        K(0.5, roll=3.0, pitch=1.2, y=0.05, yaw=1.5),
        K(0.75, roll=0.0, pitch=-0.8, y=-0.04, yaw=0.0),
    ], cyclic=True),
    # landed: a little squash, then folded back and pulled into the back
    "stow": Anim(0.4, [
        K(0, sx=1, sy=1, sz=1, y=0, z=0, pitch=0, roll=0, yaw=0),
        K(0.28, sx=1.06, sy=0.84, sz=1.04, y=-0.12, z=0.05, pitch=10),
        K(1, sx=0.03, sy=0.03, sz=0.03, y=-2.6, z=0.9, pitch=80),
    ]),
}
PIVOT = (0.0, 3.7, -0.22)  # the crossbar (gliders.py BAR_Y, BAR_Z)

# Contrails: how each look is drawn (the game draws the same: Contrails.java). width (studs, at the head of the trail),
# life (seconds a point lasts), and the style.
CONTRAILS = {
    "streak": dict(width=0.34, life=0.6),  # the colour fading into the second colour, tapering
    "rainbow": dict(width=0.38, life=0.7),  # every colour along it, moving
    "bands": dict(width=0.36, life=0.6),  # stripes of the two colours
    "neon": dict(width=0.16, life=0.55),  # a thin white-hot core in a wide glow
    "sparkle": dict(width=0.24, life=0.75),  # a faint streak with bright sparks along it
    "flame": dict(width=0.42, life=0.45),  # flickering, hot colour to the second
    "wave": dict(width=0.4, life=0.8),  # a wide pale ribbon weaving side to side
    "dots": dict(width=0.34, life=0.7),  # just bubbles
    # 2.0.0-test3 (creator: "more contrails")
    "zigzag": dict(width=0.2, life=0.5),  # a forked bolt, flickering into a new shape twelve times a second
    "ribbon": dict(width=0.4, life=0.75),  # a ribbon twisting over and over, one colour a side
    "pixel": dict(width=0.3, life=0.8),  # square blocks in the two colours by turns
    "hearts": dict(width=0.32, life=0.8),  # hearts, beating
}
HEART = 14  # (slices a heart is drawn with)


def tri(x):
    return 2 * abs(2 * (x - math.floor(x + 0.5))) - 1


def zig(u, time):
    """A bolt's sideways offset at u along it (about -1..1, none at the hand): the game's Contrails.zig."""
    flick = math.floor(time * 12)
    return (0.7 * tri(u * 3.2 + flick * 0.618) + 0.3 * tri(u * 7.7 + flick * 0.371)) * min(1.0, u * 4)


def contrail_rows():
    return [r for r in json.load(open(os.path.join(DESIGN, "cosmetics.json"), encoding="utf-8"))["rows"] if r["slot"] == "contrail"]


def hexrgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) / 255 for i in (0, 2, 4))


# Stripes along a trail ("bands") and how many go by a second; points a trail is drawn with: the game's Contrails.
STRIPES, STRIPE_SPEED, POINTS = 9, 3, 64


def resample(pts, m):
    """The game's Contrails.resample (2.0.0-test2): m points evenly along a trail (head first), on a Catmull-Rom curve
    through its points, so its look sits on a fine, steady grid."""
    n = len(pts)
    out = []
    for j in range(m):
        x = j / (m - 1) * (n - 1)
        k = min(int(x), n - 2)
        f = x - k
        a, b, c, d = pts[max(0, k - 1)], pts[k], pts[k + 1], pts[min(n - 1, k + 2)]
        out.append(0.5 * (2 * b + (c - a) * f + (2 * a - 5 * b + 4 * c - d) * f * f + (3 * b - a - 3 * c + d) * f * f * f))
    return out


def look_at(style, c1, c2, u, time):
    """A contrail's colour and opacity, and its width factor, at u along it (0 = at the hand, 1 = the end), at time
    (seconds): the game's Contrails.look, line for line."""
    fade = (1 - u) ** 1.4
    w = 1 - u * 0.85
    if style == "rainbow":
        rgb = colorsys.hsv_to_rgb((u * 1.2 - time * 0.6) % 1.0, 0.75, 1.0)
    elif style == "bands":
        rgb = c1 if math.floor(u * STRIPES - time * STRIPE_SPEED) % 2 == 0 else c2  # (ribbons() cuts at the edges)
    elif style == "flame":
        flick = 0.75 + 0.25 * math.sin(u * 37 + time * 23) * math.sin(u * 13 - time * 17)
        w *= flick
        rgb = tuple(a + (b - a) * min(1, u * 1.6) for a, b in zip(c1, c2))
    elif style == "wave":
        rgb = tuple(a + (b - a) * u for a, b in zip(c1, c2))
        fade *= 0.75
    elif style == "sparkle":
        rgb = tuple(a + (b - a) * u for a, b in zip(c1, c2))
        fade *= 0.45
    elif style == "ribbon":
        rgb = c1  # (ribbons() colours each face)
        fade *= 0.9
    else:
        rgb = tuple(a + (b - a) * u for a, b in zip(c1, c2))
    return rgb, fade, w


# ---------------------------------------------------------------- the sheet (Blender: the F-curves)

def bake_sheet():
    import bpy
    rows = []
    for aid, a in GLIDER_ANIMS.items():
        ctl = bpy.data.objects.new("glider_" + aid, None)
        bpy.context.scene.collection.objects.link(ctl)
        length = a.seconds * FPS
        for name, rest in REST.items():
            pts = [(k.t, k.ch[name]) for k in a.keys if name in k.ch] or [(0.0, rest)]
            if pts[0][0] > 0:
                pts.insert(0, (0.0, pts[-1][1] if a.cyclic else pts[0][1]))
            if pts[-1][0] < 1:
                pts.append((1.0, pts[0][1] if a.cyclic else pts[-1][1]))
            for t, v in pts:
                ctl[name] = float(v)
                ctl.keyframe_insert(data_path=f'["{name}"]', frame=1 + t * length)
        act = ctl.animation_data.action
        fcs = {}
        for fc in (act.fcurves if hasattr(act, "fcurves") else [f for l in act.layers for s in l.strips for b in s.channelbags for f in b.fcurves]):
            for kp in fc.keyframe_points:
                kp.interpolation = "BEZIER"
                kp.handle_left_type = kp.handle_right_type = "AUTO_CLAMPED"
            if a.cyclic:
                fc.modifiers.new("CYCLES")
            fc.update()
            fcs[fc.data_path[2:-2]] = fc
        for i in range(SAMPLES + 1):
            t = i / SAMPLES
            rows.append({"id": f"{aid}{i}", "anim": aid, "seconds": a.seconds, "t": round(t, 5),
                         **{n: round(fcs[n].evaluate(1 + t * length), 4) for n in REST}})
    json.dump({"sheet": "glider_keys", "class": "GliderKeyDef",
               "about": "How a glider opens, rides and is put away (fabric/tools/blender/glider_video.py: Blender keyframes sampled "
                        f"{SAMPLES} times along each; played by client/avatar/Gliders.java). anim: deploy (seconds long, once), glide "
                        "(a loop), stow (once). Each row is a transform about the glider's crossbar in Roblox root space (studs, y up, "
                        "facing -z): scale sx sy sz, moved y (up) and z (back), turned yaw, pitch (+ swings it back) and roll (degrees).",
               "columns": {"id": "string", "anim": "enum:deploy|glide|stow", "seconds": "float", "t": "float",
                           **{n: "float" for n in REST}},
               "rows": rows},
              open(os.path.join(DESIGN, "glider_keys.json"), "w", encoding="utf-8", newline="\n"), indent=1, ensure_ascii=False)
    print("glider_keys:", len(rows), "rows", flush=True)


def sample(rows, anim, t):
    """A glider animation's transform at t (0..1) from the baked rows (straight between them, as the game does)."""
    rs = [r for r in rows if r["anim"] == anim]
    x = min(max(t, 0.0), 1.0) * (len(rs) - 1)
    i = min(len(rs) - 2, int(x))
    f = x - i
    return {n: rs[i][n] + (rs[i + 1][n] - rs[i][n]) * f for n in REST}


# ---------------------------------------------------------------- videos (Blender)

def blender_main(args):
    import bpy
    from mathutils import Matrix, Vector

    sys.path.insert(0, HERE)
    import avatar
    import bend
    import brlib
    import gliders

    mode = args[0]
    if mode == "sheet":
        bake_sheet()
        return
    out = os.path.abspath(args[1])
    only = set(args[2:])
    os.makedirs(out, exist_ok=True)
    keys = json.load(open(os.path.join(DESIGN, "glider_keys.json"), encoding="utf-8"))["rows"]
    moves = {}
    for r in json.load(open(os.path.join(DESIGN, "move_keys.json"), encoding="utf-8"))["rows"]:
        moves.setdefault(r["move"], []).append(r)
    loops = {r["id"]: r["loopSeconds"] for r in json.load(open(os.path.join(DESIGN, "moves.json"), encoding="utf-8"))["rows"]}
    C, CI = avatar.C, avatar.C.inverted()

    def move(mid, t):
        rs = sorted(moves[mid], key=lambda r: r["t"])
        x = min(max(t, 0.0), 1.0) * (len(rs) - 1)
        i = min(len(rs) - 2, int(x))
        f = x - i
        return {k: rs[i][k] + (rs[i + 1][k] - rs[i][k]) * f for k in rs[i] if isinstance(rs[i][k], (int, float)) and k != "t"}

    def loop(mid, sec):
        return move(mid, (sec / loops[mid]) % 1.0)

    def blend(a, b, k):
        k = k * k * (3 - 2 * k)
        return {n: a.get(n, 0.0) + (b.get(n, 0.0) - a.get(n, 0.0)) * k for n in set(a) | set(b)}

    trail_mat = {}

    def stage():
        brlib.reset()
        brlib._mats.clear()
        trail_mat.clear()  # (the scene's materials went with it)
        s = bpy.context.scene
        s.render.engine = "BLENDER_EEVEE"
        s.render.film_transparent = True
        s.render.resolution_x = s.render.resolution_y = int(os.environ.get("BR_RES", "720"))
        brlib._lights()
        sun = bpy.data.objects.new("sun", bpy.data.lights.new("sun", "SUN"))
        sun.data.energy = 2.5
        sun.rotation_euler = (math.radians(35), math.radians(10), math.radians(30))
        s.collection.objects.link(sun)

    def camera(lo, hi, yaw=150, elevation=12):
        c = (lo + hi) / 2
        cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam"))
        cam.data.type = "ORTHO"
        bpy.context.scene.collection.objects.link(cam)
        bpy.context.scene.camera = cam
        a, e = math.radians(yaw), math.radians(elevation)
        d = Vector((math.sin(a) * math.cos(e), -math.cos(a) * math.cos(e), math.sin(e)))
        rot = (-d).to_track_quat("-Z", "Y")
        cam.rotation_euler = rot.to_euler()
        inv = rot.to_matrix().inverted()
        corners = [Vector((x, y, z)) for x in (lo.x, hi.x) for y in (lo.y, hi.y) for z in (lo.z, hi.z)]
        pts = [inv @ (p - c) for p in corners]
        w = max(p.x for p in pts) - min(p.x for p in pts)
        h = max(p.y for p in pts) - min(p.y for p in pts)
        cam.data.ortho_scale = max(w, h) * 1.1
        mx = (max(p.x for p in pts) + min(p.x for p in pts)) / 2
        my = (max(p.y for p in pts) + min(p.y for p in pts)) / 2 + max(w, h) * 0.05  # (room for the title)
        cam.location = c + d * 40 + rot.to_matrix() @ Vector((mx, my, 0))
        cam.data.clip_end = 200
        return cam

    def bounds(objs):
        lo, hi = Vector((1e9, 1e9, 1e9)), Vector((-1e9, -1e9, -1e9))
        for o in objs:
            for v in o.bound_box:
                w = o.matrix_world @ Vector(v)
                lo, hi = Vector(map(min, lo, w)), Vector(map(max, hi, w))
        return lo, hi

    def tips(p):
        """Root space: the hands' ends this pose (each arm's end 1.5 below its shoulder, past the elbow's bend), as the
        game finds them."""
        js, bd = avatar.joints(p), avatar.bends(p)
        out = []
        for part, kind, down in (("right_arm", "arm", -1.5), ("left_arm", "arm", -1.5)):
            m = js[part] @ Matrix(bend.lower_matrix(kind, bd.get(part, 0.0)))
            out.append(m @ Vector((0, down, 0)))
        return out

    def ribbon_material():
        if "m" in trail_mat:
            return trail_mat["m"]
        m = bpy.data.materials.new("contrail")
        m.use_nodes = True
        nt = m.node_tree
        for n in list(nt.nodes):
            nt.nodes.remove(n)
        attr = nt.nodes.new("ShaderNodeAttribute")
        attr.attribute_name = "Col"
        em = nt.nodes.new("ShaderNodeEmission")
        em.inputs["Strength"].default_value = 2.2
        tr = nt.nodes.new("ShaderNodeBsdfTransparent")
        mix = nt.nodes.new("ShaderNodeMixShader")
        outn = nt.nodes.new("ShaderNodeOutputMaterial")
        nt.links.new(attr.outputs["Color"], em.inputs["Color"])
        nt.links.new(attr.outputs["Alpha"], mix.inputs["Fac"])
        nt.links.new(tr.outputs["BSDF"], mix.inputs[1])
        nt.links.new(em.outputs["Emission"], mix.inputs[2])
        nt.links.new(mix.outputs["Shader"], outn.inputs["Surface"])
        if hasattr(m, "surface_render_method"):
            m.surface_render_method = "BLENDED"
        else:
            m.blend_method = "BLEND"
        m.use_backface_culling = False
        trail_mat["m"] = m
        return m

    def ribbons(trails, style, c1, c2, time, base_width, view):
        """The contrails this frame as one mesh: each trail (points in root space, head first) a flat band facing the
        camera (view: the direction it looks along, root space), its colour, opacity and width from look_at; sparks and
        bubbles as little squares; bolts, twisting ribbons, pixels and hearts as the game's Contrails draws them."""
        verts, faces, cols = [], [], []

        def quad(a, b, c, d, ca, cb):
            i = len(verts)
            verts.extend([a, b, c, d])
            cols.extend([ca, ca, cb, cb])
            faces.append((i, i + 1, i + 2, i + 3))

        def tri(a, b, c, col):
            i = len(verts)
            verts.extend([a, b, c])
            cols.extend([col, col, col])
            faces.append((i, i + 1, i + 2))

        def sides(pts):
            out = []
            for k in range(len(pts)):
                t = pts[min(len(pts) - 1, k + 1)] - pts[max(0, k - 1)]
                sd = t.cross(view)
                out.append(sd.normalized() if sd.length > 1e-9 else Vector((1, 0, 0)))
            return out

        for pts in trails:
            if len(pts) < 2:
                continue
            pts = resample(pts, POINTS)
            n = len(pts)
            us = [k / (n - 1) for k in range(n)]
            looks = [look_at(style, c1, c2, u, time) for u in us]
            if style == "wave":  # (weaving side to side as it goes)
                side = Vector((1, 0, 0))
                pts = [p + side * 0.35 * u * math.sin(u * 9 - time * 6) for p, u in zip(pts, us)]
            if style == "zigzag":  # (a bolt, flickering)
                pts = [p + sd * 0.42 * zig(u, time) for p, sd, u in zip(pts, sides(pts), us)]
            if style == "ribbon":  # twisting: the width turns through the trail's plane, a colour a face
                sd = sides(pts)
                hs, cs = [], []
                for (rgb, a, w), u in zip(looks, us):
                    twist = math.cos(u * 10 - time * 5)
                    hs.append(base_width * w / 2 * twist)
                    f = 0.5 + 0.5 * max(-1.0, min(1.0, twist * 3))
                    cs.append(tuple(y + (x - y) * f for x, y in zip(c1, c2)) + (a,))
                for k in range(n - 1):
                    quad(pts[k] - sd[k] * hs[k], pts[k] + sd[k] * hs[k], pts[k + 1] + sd[k + 1] * hs[k + 1], pts[k + 1] - sd[k + 1] * hs[k + 1], cs[k], cs[k + 1])
                continue
            if style in ("pixel", "hearts"):  # shapes along it, each the right way up as it streams back
                sd = sides(pts)
                hearts = style == "hearts"
                for k in range(0, n, 4 if hearts else 3):
                    rgb, a, w = looks[k]
                    up = pts[max(0, k - 1)] - pts[min(n - 1, k + 1)]
                    up = up - view * up.dot(view)
                    up = up.normalized() if up.length > 1e-9 else sd[k].cross(view).normalized()
                    right = up.cross(view).normalized()
                    p = pts[k]
                    if not hearts:
                        r = base_width * 0.5 * (1 - us[k] * 0.5)
                        col = (c1 if (k // 3) % 2 == 0 else c2) + (min(1.0, a * 1.3),)
                        quad(p - right * r - up * r, p + right * r - up * r, p + right * r + up * r, p - right * r + up * r, col, col)
                        continue
                    r = base_width * 0.85 * (0.85 + 0.15 * math.sin(time * 9 + k)) * (1 - us[k] * 0.5) / 17
                    col = rgb + (min(1.0, a * 1.3),)
                    centre, prev = p - up * 2 * r, None
                    for i in range(HEART + 1):
                        t = 2 * math.pi * i / HEART
                        x = 16 * math.sin(t) ** 3
                        y = 13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t)
                        at = p + right * x * r + up * y * r
                        if prev is not None:
                            tri(centre, prev, at, col)
                        prev = at
                continue
            if style != "dots":
                for layer in ((1.0, 1.0), (2.6, 0.35)) if style in ("neon", "zigzag") else ((1.0, 1.0),):
                    wk, ak = layer
                    for k in range(n - 1):
                        p0, p1 = pts[k], pts[k + 1]
                        t = (p1 - p0)
                        if t.length < 1e-6:
                            continue
                        s0 = t.cross(view).normalized()
                        (r0, a0, w0), (r1, a1, w1) = looks[k], looks[k + 1]
                        h0, h1 = base_width * w0 * wk / 2, base_width * w1 * wk / 2
                        if style == "bands":
                            # cut where a stripe ends between the two points (the game's Contrails.band): each piece one
                            # colour, so the edges are sharp and slide as the stripes move
                            st0, st1 = us[k] * STRIPES - time * STRIPE_SPEED, us[k + 1] * STRIPES - time * STRIPE_SPEED
                            stripe, fr = math.floor(st0), 0.0
                            while fr < 1:
                                to = min(1.0, (stripe + 1 - st0) / (st1 - st0)) if st1 > st0 else 1.0
                                rgb = c1 if stripe % 2 == 0 else c2
                                pa, pb = p0.lerp(p1, fr), p0.lerp(p1, to)
                                ha, hb = h0 + (h1 - h0) * fr, h0 + (h1 - h0) * to
                                quad(pa - s0 * ha, pa + s0 * ha, pb + s0 * hb, pb - s0 * hb, rgb + (a0 + (a1 - a0) * fr,), rgb + (a0 + (a1 - a0) * to,))
                                fr, stripe = to, stripe + 1
                            continue
                        core = style == "neon" and wk == 1.0
                        if style == "zigzag":  # (a core in the first colour, a glow of the second)
                            ca = (c1 if wk == 1.0 else c2) + (a0 * ak,)
                            cb = (c1 if wk == 1.0 else c2) + (a1 * ak,)
                        else:
                            ca = ((1, 1, 1) if core else r0) + (a0 * ak,)
                            cb = ((1, 1, 1) if core else r1) + (a1 * ak,)
                        quad(p0 - s0 * h0, p0 + s0 * h0, p1 + s0 * h1, p1 - s0 * h1, ca, cb)
            if style in ("sparkle", "dots"):
                up = view.cross(Vector((1, 0, 0))).normalized()
                right = up.cross(view).normalized()
                every = 3 if style == "sparkle" else 4
                for k in range(0, n, every):
                    rgb, a, w = looks[k]
                    if style == "sparkle":
                        tw = 0.6 + 0.4 * math.sin(k * 2.3 + time * 14)
                        r = base_width * 0.55 * tw * (1 - us[k] * 0.6)
                        col = tuple(min(1, x * 0.5 + 0.6) for x in rgb) + (min(1, a / 0.45),)
                    else:
                        r = base_width * 0.55 * (0.7 + 0.3 * math.sin(k * 1.7))
                        col = rgb + (min(1.0, a * 1.2),)
                    p = pts[k]
                    quad(p - right * r, p - up * r, p + right * r, p + up * r, col, col)
        me = bpy.data.meshes.new("trails")
        me.from_pydata([tuple(C @ v.to_4d())[:3] for v in verts], [], faces)
        me.update()
        attr = me.color_attributes.new("Col", "FLOAT_COLOR", "POINT")
        for i, c in enumerate(cols):
            attr.data[i].color = (*brlib.to_linear(c[:3]), max(0.0, min(1.0, c[3])))
        o = bpy.data.objects.new("trails", me)
        o.data.materials.append(ribbon_material())
        bpy.context.scene.collection.objects.link(o)
        return o

    def view_dir(cam):
        """The camera's view direction in root space (Blender -> Minecraft-style)."""
        d = cam.rotation_euler.to_matrix() @ Vector((0, 0, -1))  # (matrix_world isn't updated until the scene is)
        return (CI.to_3x3() @ d).normalized()

    index = []
    if mode == "gliders":
        rows = json.load(open(os.path.join(DESIGN, "gliders.json"), encoding="utf-8"))["rows"]
        # one of each shape (the first in the sheet), unless asked for particular ones
        pick, seen = [], set()
        for r in rows:
            if (only and r["id"] in only) or (not only and r["shape"] not in seen):
                pick.append(r)
                seen.add(r["shape"])
        streak = next((r for r in contrail_rows() if r["unlock"] == "default"), None)
        for row in pick:
            stage()
            gliders.build(row["shape"], row.get("deco", "none"))
            brlib.tint_previews({1: row["color"], 2: row["color2"]})
            gobjs = [o for o in bpy.context.scene.objects if o.type == "MESH"]
            rest = {o.name: o.matrix_world.copy() for o in gobjs}
            # the timeline (seconds): skydiving, the glider opening, gliding, landing and putting it away, standing
            SKY, DEP, GLIDE, STOW, STAND = 0.8, GLIDER_ANIMS["deploy"].seconds, GLIDER_ANIMS["glide"].seconds, GLIDER_ANIMS["stow"].seconds, 0.6
            total = SKY + DEP + GLIDE + STOW + STAND
            frames = int(round(total * FPS))
            air = move("skydive", 0.0)  # (2.0.0-test3: the new skydiving animation, as in the game)

            def avatar_pose(sec):
                if sec < SKY:
                    return loop("skydive", sec)
                if sec < SKY + DEP + GLIDE:
                    return blend(loop("skydive", sec), loop("glide", sec - SKY), min(1, (sec - SKY) / 0.22))
                return blend(loop("glide", sec - SKY), loop("idle", sec), min(1, (sec - SKY - DEP - GLIDE) / 0.22))

            def glider_at(sec):
                if sec < SKY:
                    return None
                if sec < SKY + DEP:
                    return sample(keys, "deploy", (sec - SKY) / DEP)
                if sec < SKY + DEP + GLIDE:
                    g = sample(keys, "glide", ((sec - SKY - DEP) / GLIDE) % 1.0)
                    k = min(1, (sec - SKY - DEP) / 0.5)  # (the sway eased in after the pop)
                    return {n: REST[n] + (g[n] - REST[n]) * k for n in REST}
                if sec < SKY + DEP + GLIDE + STOW:
                    return sample(keys, "stow", (sec - SKY - DEP - GLIDE) / STOW)
                return None

            def place_glider(g):
                for o in gobjs:
                    if g is None or g["sx"] < 0.02:
                        o.hide_render = True
                        continue
                    o.hide_render = False
                    px, py, pz = PIVOT
                    a = (Matrix.Translation((px, py + g["y"], pz + g["z"])) @ Matrix.Rotation(math.radians(g["yaw"]), 4, "Y")
                         @ Matrix.Rotation(math.radians(g["pitch"]), 4, "X") @ Matrix.Rotation(math.radians(g["roll"]), 4, "Z")
                         @ Matrix.Diagonal((g["sx"], g["sy"], g["sz"], 1)) @ Matrix.Translation((-px, -py, -pz)))
                    o.matrix_world = C @ a @ CI @ rest[o.name]

            avatar.build(emote=loop("glide", 0))
            body = [o for objs in avatar.BUILT.values() for o in objs]
            lo, hi = bounds(body + gobjs)
            lo.y -= 3.6  # (room behind for the gliding trails: Blender -y is behind the avatar)
            cam = camera(lo, hi, yaw=140, elevation=14)
            view = view_dir(cam)
            # wingtips: the glider's furthest points out to each side (root space)
            gpts = [CI @ (o.matrix_world @ Vector(v)) for o in gobjs for v in o.bound_box]
            span = max(abs(p.x) for p in gpts)
            wy = max(p.y for p in gpts if abs(p.x) > span * 0.8)
            wingtips = [Vector((span * 0.96, wy - 0.2, 0.2)), Vector((-span * 0.96, wy - 0.2, 0.2))]
            history = []  # [(sec, [tip points]), ...]
            d = os.path.join(out, row["id"])
            os.makedirs(d, exist_ok=True)
            s = bpy.context.scene
            trail_obj = None
            for f in range(1, frames + 1):
                sec = (f - 1) / FPS
                p = avatar_pose(sec)
                avatar.repose(avatar.joints(p), avatar.bends(p))
                g = glider_at(sec)
                place_glider(g)
                if trail_obj is not None:
                    bpy.data.objects.remove(trail_obj, do_unlink=True)
                    trail_obj = None
                gliding = SKY + 0.25 < sec < SKY + DEP + GLIDE
                if streak and (sec < SKY or gliding):
                    pts = tips(p) if sec < SKY else wingtips
                    history.append((sec, pts, sec < SKY))
                    look = CONTRAILS.get(streak["shape"], CONTRAILS["streak"])
                    life = look["life"]
                    live = [h for h in history if sec - h[0] <= life and h[2] == (sec < SKY)]
                    trails = []
                    for i in range(len(live[-1][1])):
                        trail = []
                        for (hs, hp, sky) in reversed(live):
                            age = sec - hs
                            # (the world going by: falling, the trail rises; gliding, it streams back and a little up)
                            off = Vector((0, 7.0 * age, 0)) if sky else Vector((0, 1.6 * age, 6.0 * age))
                            trail.append(hp[i] + off)
                        trails.append(trail)
                    trail_obj = ribbons(trails, streak["shape"], hexrgb(streak["color"]), hexrgb(streak["color2"]), sec, look["width"], view)
                s.render.filepath = os.path.join(d, f"{f:04d}.png")
                bpy.ops.render.render(write_still=True)
            line = gliders.unlock_text(row)
            index.append({"id": row["id"], "name": row["name"], "line": line or "Free", "kind": "glider", "dir": d,
                          "frames": frames, "still": int((SKY + DEP + GLIDE * 0.4) * FPS)})
            print(f"GLIDER {row['id']}: {frames} frames", flush=True)
    elif mode == "contrails":
        from emote_video import unlock_line
        for row in contrail_rows():
            if only and row["id"] not in only:
                continue
            stage()
            look = CONTRAILS.get(row["shape"], CONTRAILS["streak"])
            frames = int(round(2.4 * FPS))
            avatar.build(emote=move("skydive", 0.0))
            body = [o for objs in avatar.BUILT.values() for o in objs]
            lo, hi = bounds(body)
            hi.z += 4.5  # (room above for the trails rising)
            cam = camera(lo, hi, yaw=150, elevation=8)
            view = view_dir(cam)
            history = []
            d = os.path.join(out, row["id"])
            os.makedirs(d, exist_ok=True)
            s = bpy.context.scene
            trail_obj = None
            c1, c2 = hexrgb(row["color"]), hexrgb(row["color2"])
            for f in range(1, frames + 1):
                sec = (f - 1) / FPS
                # skydiving (2.0.0-test3: the new animation, as in the game), drifting a little side to side so the trails curve
                p = dict(loop("skydive", sec))
                p["bodyYaw"] = 8 * math.sin(sec * 2.2)
                p["tilt"] = 6 * math.sin(sec * 1.7 + 1)
                avatar.repose(avatar.joints(p), avatar.bends(p))
                if trail_obj is not None:
                    bpy.data.objects.remove(trail_obj, do_unlink=True)
                history.append((sec, tips(p)))
                live = [h for h in history if sec - h[0] <= look["life"]]
                trails = [[hp[i] + Vector((0, 7.0 * (sec - hs), 0)) for hs, hp in reversed(live)] for i in range(2)]
                trail_obj = ribbons(trails, row["shape"], c1, c2, sec, look["width"], view)
                s.render.filepath = os.path.join(d, f"{f:04d}.png")
                bpy.ops.render.render(write_still=True)
            index.append({"id": row["id"], "name": row["name"], "line": unlock_line(row), "kind": "contrail", "dir": d, "frames": frames,
                          "still": frames - 6})
            print(f"CONTRAIL {row['id']}: {frames} frames", flush=True)
    elif mode == "boosttrails":
        # (2.0.0-test3) a driven car in its pieces, as the game draws it (cars.py, car_rigs.json; CarEntityRenderer), in
        # studs like the avatar
        import brm_import
        from emote_video import unlock_line
        assets = os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "minebloxroyale")
        rigs = json.load(open(os.path.join(assets, "car_rigs.json"), encoding="utf-8"))
        k = 1 / 0.36  # (a block in studs: the game's RobloxAvatarRenderer.S, 0.36 blocks a stud)

        def to_blender(m):
            """A Minecraft-space matrix (blocks) as a Blender one in studs."""
            return C @ Matrix.Diagonal((k, k, k, 1)) @ m @ C.inverted()

        def piece(mid, paint, trim):
            return brm_import.load(os.path.join(assets, "meshes", mid + ".brm"), paint, trim, name=mid)

        def car(kind, paint, trim):
            """The shell, the wheels and the front doors (shut, hidden); returns them to move."""
            rig = rigs[kind]
            parts = {"shell": piece(f"car_{kind}_1_shell", paint, trim)}
            for i in range(4):
                parts[f"wheel{i}"] = piece(f"car_wheel_{kind}" + ("" if i % 2 == 0 else "_r"), paint, trim)
            parts["door"] = piece(f"car_door_{kind}", paint, trim)
            parts["doorway"] = piece(f"car_doorway_{kind}", paint, trim)
            return rig, parts

        def pose_car(rig, parts, spin, steer=0.0, lean=0.0, door=0.0, place=Matrix.Identity(4)):
            body = place @ Matrix.Rotation(math.radians(lean), 4, "Z") @ Matrix.Identity(4)
            parts["shell"].matrix_world = to_blender(body)
            for i in range(4):
                front, left = i < 2, i % 2 == 0
                m = place @ Matrix.Translation(((1 if left else -1) * rig["wheelX"], rig["wheelR"], rig["frontZ"] if front else rig["backZ"]))
                if front:
                    m = m @ Matrix.Rotation(math.radians(steer), 4, "Y")
                parts[f"wheel{i}"].matrix_world = to_blender(m @ Matrix.Rotation(spin, 4, "X"))
            hinge = body @ Matrix.Translation((rig["hingeX"], 0, rig["hingeZ"])) @ Matrix.Rotation(math.radians(-65 * door), 4, "Y")
            parts["door"].matrix_world = to_blender(hinge)
            parts["doorway"].matrix_world = to_blender(body)
            for name in ("door", "doorway"):
                parts[name].hide_render = door <= 0.01

        rows = [r for r in json.load(open(os.path.join(DESIGN, "cosmetics.json"), encoding="utf-8"))["rows"] if r["slot"] == "boosttrail"]
        for row in rows:
            if only and row["id"] not in only:
                continue
            stage()
            look = CONTRAILS.get(row["shape"], CONTRAILS["streak"])
            rig, parts = car("sports", 0xD8352C, 0xF2F4F8)
            pose_car(rig, parts, 0)
            lo, hi = bounds(list(parts.values()))
            tail = C @ Vector((0, 1.0 * k, -(rig["length"] / 2 + 4.0) * k))
            lo, hi = Vector(map(min, lo, tail)), Vector(map(max, hi, tail))
            cam = camera(lo, hi, yaw=135, elevation=20)
            view = view_dir(cam)
            frames = int(round(2.4 * FPS))
            d = os.path.join(out, row["id"])
            os.makedirs(d, exist_ok=True)
            sc = bpy.context.scene
            trail_obj = None
            c1, c2 = hexrgb(row["color"]), hexrgb(row["color2"])
            width = look["width"] * 2.6  # (the game: a boost trail is CAR_WIDTH times a contrail's)
            for f in range(1, frames + 1):
                sec = (f - 1) / FPS
                pose_car(rig, parts, sec * 17 / rig["wheelR"])  # (boosting: about 17 blocks a second)
                if trail_obj is not None:
                    bpy.data.objects.remove(trail_obj, do_unlink=True)
                trails = []
                for e in rig["exhausts"]:
                    pts = []
                    for j in range(POINTS):
                        u = j / (POINTS - 1)
                        pts.append(Vector((e[0] + 0.05 * math.sin(sec * 3 - u * 6), e[1] + 0.08 * u, e[2] - 4.2 * u)) * k)
                    trails.append(pts)
                trail_obj = ribbons(trails, row["shape"], c1, c2, sec, width, view)
                sc.render.filepath = os.path.join(d, f"{f:04d}.png")
                bpy.ops.render.render(write_still=True)
            index.append({"id": row["id"], "name": row["name"], "line": unlock_line(row), "kind": "boosttrail", "dir": d, "frames": frames,
                          "still": frames - 6})
            print(f"BOOSTTRAIL {row['id']}: {frames} frames", flush=True)
    with open(os.path.join(out, "frames.json"), "w", encoding="utf-8") as f:
        json.dump(index, f, indent=1)


if __name__ == "__main__":
    if "--" in sys.argv:
        blender_main(sys.argv[sys.argv.index("--") + 1:])
