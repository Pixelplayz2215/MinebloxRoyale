"""
The emotes and the avatar's moves animated in Blender (creator: "use blender to animate them and send render videos";
then "add knees and elbows ... new walking animation, running, aiming, reviving, rebooting, etc."). For each emote in
emote_anims.py, or move in moves.py: its key poses become Blender keyframes (smooth Bezier curves), every moment of it
gets the two fixes in rig.py (feet on, never through, the floor; hands kept out of the body), the R6 avatar is posed
from it frame by frame (avatar.joints, the game's own joint maths, elbows and knees bent as the game bends them) and
rendered, and the frames become a video with its name and a line under it (what unlocks an emote, "Free" when nothing
does; what a move is). The same poses, sampled finely, are the game's emote_keys / move_keys sheets, so the game plays
what the video shows.

blender --background --factory-startup --python emote_video.py -- [moves] <frames dir> [ids...]
    renders frames (and frames.json for make_videos); without ids it also writes the sheets: design/emote_keys.json
    and emotes.json, or (moves) design/move_keys.json and moves.json (BR_SHEETS_ONLY=1: only those)
python emote_video.py videos <frames dir> <out dir>       (no Blender: titles and MP4s with ffmpeg)
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "..", "..")
DESIGN = os.path.join(ROOT, "design")
FPS = 30
SAMPLES = 32  # sheet rows per loop
CHANNELS = {"rax": "rightArmX", "raz": "rightArmZ", "lax": "leftArmX", "laz": "leftArmZ", "rlx": "rightLegX", "llx": "leftLegX", "yaw": "bodyYaw",
            "bob": "bounce", "rlz": "rightLegZ", "llz": "leftLegZ", "hx": "headX", "hy": "headY", "hz": "headZ", "lean": "lean", "tilt": "tilt",
            "twist": "twist", "rel": "rightElbow", "lel": "leftElbow", "rkn": "rightKnee", "lkn": "leftKnee"}
UNLOCK = {"kills": "Get {n} eliminations", "wins": "Win {n} Victory Royales", "range": "Score {n} in one shooting range round",
          "bullseyes": "Hit {n} shooting range bullseyes", "obby": "Finish the obby in the lobby", "victory": "Win a Victory Royale"}
CRAWL_FLOOR = -0.3 / 0.36  # (knocked: the game lays the body flat 0.3 blocks in front of where it stood: RobloxAvatarRenderer.CRAWL)


def unlock_line(row):
    u = row.get("unlock", "default")
    return "Free" if u == "default" else "Unlock: " + UNLOCK.get(u, u).format(n=row.get("need", 0))


def smooth(vals, cyclic, r):
    """A per-moment correction made gentle: each side (up / down) widened by r samples, then blurred, so a fix eases
    in and out instead of popping."""
    n = len(vals)
    out = [0.0] * n
    for sign in (1, -1):
        part = [max(0.0, sign * v) for v in vals]
        idx = (lambda i: i % n) if cyclic else (lambda i: min(n - 1, max(0, i)))
        wide = [max(part[idx(i + k)] for k in range(-r, r + 1)) for i in range(n)]
        sig = max(1.0, r / 1.5)
        ker = [math.exp(-0.5 * (k / sig) ** 2) for k in range(-2 * r, 2 * r + 1)]
        tot = sum(ker)
        blur = [sum(wide[idx(i + k - 2 * r)] * ker[k] for k in range(len(ker))) / tot for i in range(n)]
        out = [o + sign * b for o, b in zip(out, blur)]
    return out


def at(poses, t):
    """The baked pose at t (0..1) between the samples."""
    n = len(poses) - 1
    x = min(max(t, 0.0), 1.0) * n
    i = min(n - 1, int(x))
    f = x - i
    a, b = poses[i], poses[i + 1]
    return {k: a.get(k, 0.0) + (b.get(k, 0.0) - a.get(k, 0.0)) * f for k in set(a) | set(b)}


# ---------------------------------------------------------------- Blender

def blender_main(args):
    import bpy
    from mathutils import Matrix, Vector

    sys.path.insert(0, HERE)
    import avatar
    import brlib
    import rig
    from emote_anims import EMOTES, REST
    from moves import MOVES

    kind = "moves" if args and args[0] == "moves" else "emotes"
    if kind == "moves":
        args = args[1:]
    out = os.path.abspath(args[0])
    only = set(args[1:])
    rows = {r["id"]: r for r in json.load(open(os.path.join(DESIGN, "emotes.json"), encoding="utf-8"))["rows"]}
    os.makedirs(out, exist_ok=True)
    index = []
    sheet_rows = []

    def curves(e):
        """The animation as Blender keyframes: an action on a controller, one F-curve per channel (looping, unless it
        runs along the look or once through)."""
        cyclic = getattr(e, "cyclic", True)
        ctl = bpy.data.objects.new("anim", None)
        bpy.context.scene.collection.objects.link(ctl)
        length = e.loop * FPS
        rest = REST if kind == "emotes" else {}
        used = sorted({k for _, ch in e.keys for k in ch} | set(rest))
        for name in used:
            pts = [(t, ch[name]) for t, ch in e.keys if name in ch]
            if name in rest and not pts:
                pts = [(0, rest[name])]
            if pts[0][0] > 0:
                pts.insert(0, (0.0, pts[-1][1] if cyclic else pts[0][1]))
            if pts[-1][0] < 1:
                pts.append((1.0, pts[0][1] if cyclic else pts[-1][1]))
            ctl[name] = float(pts[0][1])
            for t, v in pts:
                ctl[name] = float(v)
                ctl.keyframe_insert(data_path=f'["{name}"]', frame=1 + t * length)
        fcs = {}
        for fc in ctl.animation_data.action.fcurves if hasattr(ctl.animation_data.action, "fcurves") else _layered(ctl):
            name = fc.data_path[2:-2]
            for kp in fc.keyframe_points:
                kp.interpolation = "LINEAR" if name in e.linear else "BEZIER"
                kp.handle_left_type = kp.handle_right_type = "AUTO_CLAMPED"
            if cyclic:
                fc.modifiers.new("CYCLES")
            fc.update()
            fcs[name] = fc
        return fcs, length

    def _layered(ctl):  # (Blender 4.4+ layered actions keep F-curves in channel bags)
        act = ctl.animation_data.action
        for layer in act.layers:
            for strip in layer.strips:
                for bag in strip.channelbags:
                    yield from bag.fcurves

    def pose_at(fcs, frame):
        return {CHANNELS[n]: fc.evaluate(frame) for n, fc in fcs.items()}

    def bake(e, fcs, length):
        """The animation at evenly spaced moments (t = i / n), each with its feet and hands fixed (rig.py)."""
        cyclic = getattr(e, "cyclic", True)
        n = max(64, int(round(2 * length)))
        poses = [pose_at(fcs, 1 + i / n * length) for i in range(n + 1)]
        plant = getattr(e, "plant", "floor")
        poses = [rig.touch(p) if plant == "touch" else rig.floor(p) if plant == "floor" else p for p in poses]
        fixes = [rig.clear(p, getattr(e, "keep", ())) for p in poses]
        for col in sorted({c for d in fixes for c in d}):
            for p, v in zip(poses, smooth([d.get(col, 0.0) for d in fixes], cyclic, max(1, n // 40))):
                p[col] = p.get(col, 0.0) + v
        if fixes and any(fixes):
            print(f"  hands kept out of the body in {sum(1 for d in fixes if d)} of {len(fixes)} moments", flush=True)
        return poses

    def stage():
        brlib.reset()
        brlib._mats.clear()
        s = bpy.context.scene
        s.render.engine = "BLENDER_EEVEE"
        s.render.film_transparent = True  # (the videos put the dark panel behind; the world stays the soft light)
        s.render.resolution_x = s.render.resolution_y = int(os.environ.get("BR_RES", "720"))  # (BR_RES, BR_STEP: quick looks)
        brlib._lights()
        sun = bpy.data.objects.new("sun", bpy.data.lights.new("sun", "SUN"))
        sun.data.energy = 2.5
        sun.rotation_euler = (math.radians(35), math.radians(10), math.radians(30))
        s.collection.objects.link(sun)

    def ground(y):
        bpy.ops.mesh.primitive_circle_add(vertices=64, radius=4.5, fill_type="NGON", location=(0, 0, y))
        g = bpy.context.active_object
        m = bpy.data.materials.new("ground")
        m.use_nodes = True
        m.node_tree.nodes.get("Principled BSDF").inputs["Base Color"].default_value = (*brlib.to_linear((0.36, 0.39, 0.46)), 1)
        m.node_tree.nodes.get("Principled BSDF").inputs["Roughness"].default_value = 0.9
        g.data.materials.append(m)
        g["br_preview"] = 1

    def camera(boxes, yaw=150, elevation=12):
        """An orthographic camera, three-quarters from the front, fitting every frame's avatar (it doesn't move)."""
        lo = Vector((min(b[0][k] for b in boxes) for k in range(3)))
        hi = Vector((max(b[1][k] for b in boxes) for k in range(3)))
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
        cam.data.ortho_scale = max(w, h) * 1.12
        mx = (max(p.x for p in pts) + min(p.x for p in pts)) / 2
        my = (max(p.y for p in pts) + min(p.y for p in pts)) / 2 + max(w, h) * 0.04  # (room for the title at the top)
        cam.location = c + d * 30 + rot.to_matrix() @ Vector((mx, my, 0))
        cam.data.clip_end = 100

    def box_now():
        lo = Vector((1e9, 1e9, 1e9))
        hi = -lo
        for objs in avatar.BUILT.values():
            for o in objs:
                for v in o.bound_box:
                    w = o.matrix_world @ Vector(v)
                    lo = Vector(map(min, lo, w))
                    hi = Vector(map(max, hi, w))
        return lo, hi

    def render(name, frames, pose_fn, lying=None, ground_y=-2.0):
        stage()
        avatar.build()
        ground(ground_y)
        boxes = []
        for f in range(1, frames + 1, 3):
            p = pose_fn(f)
            avatar.repose(avatar.joints(p, lying=lying), avatar.bends(p))
            boxes.append(box_now())
        camera(boxes, yaw=150 if kind == "emotes" else 125)  # (moves a little more from the side: the legs show)
        d = os.path.join(out, name)
        os.makedirs(d, exist_ok=True)
        s = bpy.context.scene
        for f in range(1, frames + 1, int(os.environ.get("BR_STEP", "1"))):
            p = pose_fn(f)
            avatar.repose(avatar.joints(p, lying=lying), avatar.bends(p))
            s.render.filepath = os.path.join(d, f"{f:04d}.png")
            bpy.ops.render.render(write_still=True)
        return d

    anims = EMOTES if kind == "emotes" else MOVES
    for aid, e in anims.items():
        if only and aid not in only:
            continue
        stage()
        fcs, length = curves(e)
        poses = bake(e, fcs, length)
        # The game's sheet: SAMPLES rows along it (straight lines between them follow the curves).
        for i in range(SAMPLES + 1):
            p = at(poses, i / SAMPLES)
            sheet_rows.append({"id": f"{aid}{i}", ("emote" if kind == "emotes" else "move"): aid, "t": round(i / SAMPLES, 5),
                               **{col: round(p.get(col, 0.0), 2) for col in CHANNELS.values()}})
        if getattr(e, "cyclic", True):
            loops = max(2, math.ceil(3.2 / e.loop))
            frames = int(round(loops * length))
            tf = lambda f: ((f - 1) / length) % 1.0
        elif getattr(e, "axis", "") == "progress" and aid == "swing":  # (swung three times, a breath between)
            beat = length + 10
            frames = int(round(3 * beat))
            tf = lambda f: min(1.0, ((f - 1) % beat) / length)
        else:  # there and back (aiming: up to down and up again; jumping: up, falling, and back)
            frames = int(round(2 * length))
            tf = lambda f: 1 - abs(1 - 2 * ((f - 1) / frames))
        if os.environ.get("BR_SHEETS_ONLY"):  # (just the sheets: no pictures)
            continue
        lying = Matrix.Rotation(math.radians(-90), 4, "X") if getattr(e, "lying", False) else None
        d = render(aid, frames, lambda f: at(poses, tf(f)), lying=lying, ground_y=CRAWL_FLOOR if lying else -2.0)
        if kind == "emotes":
            row = rows[aid]
            index.append({"id": aid, "name": row["name"], "line": unlock_line(row), "kind": "emote", "dir": d, "frames": frames})
        else:
            index.append({"id": aid, "name": e.name, "line": e.line, "kind": "move", "dir": d, "frames": frames})
        print(f"{kind.upper()[:-1]} {aid}: {frames} frames", flush=True)

    with open(os.path.join(out, "frames.json"), "w", encoding="utf-8") as f:
        json.dump(index, f, indent=1)
    if not only:
        (write_sheets if kind == "emotes" else write_moves)(sheet_rows, anims)


ABOUT = ("Angles in degrees: armX/legX swing forward (+) / back (-), armZ/legZ out to the side (+); bodyYaw turns the whole body; above "
         "the hips the body twists (to its right), leans (forward) and tilts (to its right); the head nods (down), turns (to its right) "
         "and tilts (to its right); the elbows bend the forearm forward and the knees the shin back (0 = straight); bounce lifts the "
         "body (studs).")


def write_sheets(sheet_rows, emotes):
    p = os.path.join(DESIGN, "emote_keys.json")
    sheet = json.load(open(p, encoding="utf-8"))
    sheet["about"] = ("Keyframes: each emote's Blender animation (fabric/tools/blender/emote_anims.py, emote_video.py) sampled "
                      f"{SAMPLES} times a loop. t is 0..1 of the loop. " + ABOUT)
    sheet["columns"] = {"id": "string", "emote": "ref:emotes", "t": "float", **{c: "float" for c in CHANNELS.values()}}
    sheet["rows"] = sheet_rows
    json.dump(sheet, open(p, "w", encoding="utf-8", newline="\n"), indent=1, ensure_ascii=False)
    p = os.path.join(DESIGN, "emotes.json")
    sheet = json.load(open(p, encoding="utf-8"))
    for r in sheet["rows"]:
        if r["id"] in emotes:
            r["loopSeconds"] = emotes[r["id"]].loop
            r["note"] = emotes[r["id"]].note
    json.dump(sheet, open(p, "w", encoding="utf-8", newline="\n"), indent=1, ensure_ascii=False)


def write_moves(sheet_rows, moves):
    json.dump({"sheet": "moves", "class": "MoveDef",
               "about": "How the avatar moves when it isn't emoting (fabric/tools/blender/moves.py; played by RobloxAvatarRenderer). axis: "
                        "what t (0..1) runs along: phase (the walk cycle: right foot forward at 0, left at 0.5), time (a loop of "
                        "loopSeconds), pitch (where you look: 0 up, 0.5 ahead, 1 down), progress (once through: a pickaxe swing; "
                        "jumping, from going up to falling fast).",
               "columns": {"id": "string", "name": "string", "axis": "enum:phase|time|pitch|progress", "loopSeconds": "float", "note": "string"},
               "rows": [{"id": k, "name": m.name, "axis": m.axis, "loopSeconds": m.loop, "note": m.line} for k, m in moves.items()]},
              open(os.path.join(DESIGN, "moves.json"), "w", encoding="utf-8", newline="\n"), indent=1, ensure_ascii=False)
    json.dump({"sheet": "move_keys", "class": "MoveKeyDef",
               "about": f"Keyframes: each move's Blender animation (moves.py, emote_video.py) sampled {SAMPLES} times along it. " + ABOUT,
               "columns": {"id": "string", "move": "ref:moves", "t": "float", **{c: "float" for c in CHANNELS.values()}},
               "rows": sheet_rows},
              open(os.path.join(DESIGN, "move_keys.json"), "w", encoding="utf-8", newline="\n"), indent=1, ensure_ascii=False)


# ---------------------------------------------------------------- videos (no Blender)

def make_videos(frames_dir, out_dir):
    import shutil
    import subprocess
    import tempfile

    from PIL import Image, ImageDraw, ImageFont

    ffmpeg = os.path.join(ROOT, ".tools", "ffmpeg", "ffmpeg-n9.0-latest-win64-lgpl-shared-9.0", "bin", "ffmpeg.exe")
    font = lambda n: ImageFont.truetype("C:/Windows/Fonts/bahnschrift.ttf", n)
    os.makedirs(out_dir, exist_ok=True)

    def wrap(d, text, f, width):
        lines, cur = [], ""
        for word in text.split():
            nxt = (cur + " " + word).strip()
            if d.textlength(nxt, font=f) > width and cur:
                lines.append(cur)
                cur = word
            else:
                cur = nxt
        return lines + [cur]

    for e in json.load(open(os.path.join(frames_dir, "frames.json"), encoding="utf-8")):
        move = e.get("kind") == "move"
        color = (150, 200, 255) if move else (140, 220, 140) if e["line"] == "Free" else (255, 205, 90)
        with tempfile.TemporaryDirectory() as tmp:
            for f in range(1, e["frames"] + 1):
                img = Image.new("RGBA", (720, 720), (34, 37, 46, 255))
                img.alpha_composite(Image.open(os.path.join(e["dir"], f"{f:04d}.png")).convert("RGBA"))
                img = img.convert("RGB")
                d = ImageDraw.Draw(img)
                d.text((24, 18), e["name"], font=font(44), fill=(245, 246, 250))
                for i, line in enumerate(wrap(d, e["line"], font(26 if move else 28), 670)):
                    d.text((26, 72 + 32 * i), line, font=font(26 if move else 28), fill=color)
                img.save(os.path.join(tmp, f"{f:04d}.png"))
            name = "".join(c for c in e["name"] if c not in '<>:"/\\|?*')
            target = os.path.join(out_dir, name + ".mp4")
            subprocess.run([ffmpeg, "-y", "-loglevel", "error", "-framerate", str(FPS), "-i", os.path.join(tmp, "%04d.png"), "-c:v", "libopenh264",
                            "-b:v", "4M", "-pix_fmt", "yuv420p", "-movflags", "+faststart", target], check=True)
        print("video", target, flush=True)


if __name__ == "__main__":
    if "--" in sys.argv:
        blender_main(sys.argv[sys.argv.index("--") + 1:])
    elif len(sys.argv) > 1 and sys.argv[1] == "videos":
        make_videos(sys.argv[2], sys.argv[3])
