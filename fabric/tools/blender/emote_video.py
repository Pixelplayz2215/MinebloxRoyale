"""
The emotes animated in Blender (creator: "use blender to animate them and send render videos"). For each emote in
emote_anims.py: its key poses become Blender keyframes (smooth Bezier curves, looping), the R6 avatar is posed from
them frame by frame (avatar.joints, the game's own joint maths) and rendered, and the frames become a video with the
emote's name and what unlocks it ("Free" when nothing does). The same curves, sampled finely, are the game's
emote_keys sheet, so the game plays what the video shows.

blender --background --factory-startup --python emote_video.py -- <frames dir> [ids...]
    renders frames (and frames.json for make_videos) and writes design/emote_keys.json and emotes.json
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
            "twist": "twist"}
UNLOCK = {"kills": "Get {n} eliminations", "wins": "Win {n} Victory Royales", "range": "Score {n} in one shooting range round",
          "bullseyes": "Hit {n} shooting range bullseyes", "obby": "Finish the obby in the lobby", "victory": "Win a Victory Royale"}


def unlock_line(row):
    u = row.get("unlock", "default")
    return "Free" if u == "default" else "Unlock: " + UNLOCK.get(u, u).format(n=row.get("need", 0))


# ---------------------------------------------------------------- Blender

def blender_main(args):
    import bpy
    from mathutils import Matrix, Vector

    sys.path.insert(0, HERE)
    import avatar
    import brlib
    from emote_anims import CRAWL_CYCLE, EMOTES, REST

    out = os.path.abspath(args[0])
    only = set(args[1:])
    rows = {r["id"]: r for r in json.load(open(os.path.join(DESIGN, "emotes.json"), encoding="utf-8"))["rows"]}
    os.makedirs(out, exist_ok=True)
    index = []
    sheet_rows = []

    def curves(e):
        """The emote as Blender keyframes: an action on a controller, one F-curve per channel, looping."""
        ctl = bpy.data.objects.new("emote", None)
        bpy.context.scene.collection.objects.link(ctl)
        length = e.loop * FPS
        used = sorted({k for _, ch in e.keys for k in ch} | set(REST))
        for name in used:
            pts = [(t, ch[name]) for t, ch in e.keys if name in ch]
            if name in REST and not pts:
                pts = [(0, REST[name])]
            if pts[0][0] > 0:
                pts.insert(0, (0.0, pts[-1][1]))
            if pts[-1][0] < 1:
                pts.append((1.0, pts[0][1]))
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

    def stage():
        brlib.reset()
        brlib._mats.clear()
        s = bpy.context.scene
        s.render.engine = "BLENDER_EEVEE"
        s.render.film_transparent = True  # (the videos put the dark panel behind; the world stays the soft light)
        s.render.resolution_x = s.render.resolution_y = 720
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
            avatar.repose(avatar.joints(pose_fn(f), lying=lying))
            boxes.append(box_now())
        camera(boxes)
        d = os.path.join(out, name)
        os.makedirs(d, exist_ok=True)
        s = bpy.context.scene
        for f in range(1, frames + 1):
            avatar.repose(avatar.joints(pose_fn(f), lying=lying))
            s.render.filepath = os.path.join(d, f"{f:04d}.png")
            bpy.ops.render.render(write_still=True)
        return d

    for eid, e in EMOTES.items():
        if only and eid not in only:
            continue
        stage()
        fcs, length = curves(e)
        # The game's sheet: the curves sampled SAMPLES times a loop (straight lines between them follow the curves).
        for i in range(SAMPLES + 1):
            p = pose_at(fcs, 1 + i / SAMPLES * length)
            sheet_rows.append({"id": f"{eid}{i}", "emote": eid, "t": round(i / SAMPLES, 5),
                               **{col: round(p.get(col, 0.0), 2) for col in CHANNELS.values()}})
        loops = max(2, math.ceil(3.2 / e.loop))
        frames = int(round(loops * length))
        poses = {f: pose_at(fcs, f) for f in range(1, frames + 1)}
        row = rows[eid]
        d = render(eid, frames, lambda f: poses[f])
        index.append({"id": eid, "name": row["name"], "line": unlock_line(row), "dir": d, "frames": frames})
        print(f"EMOTE {eid}: {frames} frames", flush=True)

    if not only or "crawl" in only:
        # Knocked down: RobloxAvatarRenderer.crawlPose while crawling along (the body laid flat, face down).
        def crawl(f):
            ph = (f - 1) / (CRAWL_CYCLE * FPS) * math.pi * 2
            s_, c_ = math.sin(ph), math.cos(ph)
            breathe = math.sin((f - 1) / FPS * 20 * 0.08) * 3
            return {"rightArmX": 150 + 38 * s_ + breathe, "rightArmZ": 18 - 8 * c_, "leftArmX": 150 - 38 * s_ + breathe, "leftArmZ": 18 + 8 * c_,
                    "rightLegX": -8 - 8 * s_, "leftLegX": -8 + 8 * s_, "bodyYaw": 7 * s_, "headX": -55}
        lying = Matrix.Rotation(math.radians(-90), 4, "X")
        frames = int(round(2 * CRAWL_CYCLE * FPS))
        d = render("crawl", frames, crawl, lying=lying, ground_y=-0.8)
        index.append({"id": "crawl", "name": "Knocked Crawl", "line": "Knocked down in Duos, Trios and Squads", "dir": d, "frames": frames})

    with open(os.path.join(out, "frames.json"), "w", encoding="utf-8") as f:
        json.dump(index, f, indent=1)
    if not only:
        write_sheets(sheet_rows, EMOTES)


def write_sheets(sheet_rows, emotes):
    p = os.path.join(DESIGN, "emote_keys.json")
    sheet = json.load(open(p, encoding="utf-8"))
    sheet["about"] = ("Keyframes: each emote's Blender animation (fabric/tools/blender/emote_anims.py, emote_video.py) sampled "
                      f"{SAMPLES} times a loop. t is 0..1 of the loop. Angles in degrees: armX/legX swing forward (+) / back (-), armZ/legZ "
                      "out to the side (+); bodyYaw turns the whole body; above the hips the body twists (to its right), leans (forward) "
                      "and tilts (to its right); the head nods (down), turns (to its right) and tilts (to its right); bounce lifts the body (studs).")
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


# ---------------------------------------------------------------- videos (no Blender)

def make_videos(frames_dir, out_dir):
    import shutil
    import subprocess
    import tempfile

    from PIL import Image, ImageDraw, ImageFont

    ffmpeg = os.path.join(ROOT, ".tools", "ffmpeg", "ffmpeg-n9.0-latest-win64-lgpl-shared-9.0", "bin", "ffmpeg.exe")
    font = lambda n: ImageFont.truetype("C:/Windows/Fonts/bahnschrift.ttf", n)
    os.makedirs(out_dir, exist_ok=True)
    for e in json.load(open(os.path.join(frames_dir, "frames.json"), encoding="utf-8")):
        with tempfile.TemporaryDirectory() as tmp:
            for f in range(1, e["frames"] + 1):
                img = Image.new("RGBA", (720, 720), (34, 37, 46, 255))
                img.alpha_composite(Image.open(os.path.join(e["dir"], f"{f:04d}.png")).convert("RGBA"))
                img = img.convert("RGB")
                d = ImageDraw.Draw(img)
                d.text((24, 18), e["name"], font=font(44), fill=(245, 246, 250))
                d.text((26, 72), e["line"], font=font(28), fill=(255, 205, 90) if e["line"] != "Free" else (140, 220, 140))
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
