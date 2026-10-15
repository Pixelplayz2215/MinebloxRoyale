"""
The classic Roblox (R6) avatar for the renders only (never exported): gliders are shown held over its head, cosmetics
worn on it, emotes posed on it, so their size shows. The real body from the Roblox install when there is one
(roblox_body), else a stand-in made of boxes. Roblox root space (studs, y up from the hips, facing -z), each body part
where the avatar parts sheet puts it, turned about its joint as the game turns it (RobloxAvatarRenderer.jointMatrix).
"""
import math

import bend
import roblox_body
from brlib import box, cyl, mat, sphere, torus
from mathutils import Matrix, Vector

ROBLOX = roblox_body.content()

HEAD_R, HEAD_H = 0.6, 1.2  # (the game's head: 1.2 across, 1.2 tall)
C = Matrix(((1, 0, 0, 0), (0, 0, -1, 0), (0, 1, 0, 0), (0, 0, 0, 1)))
BEND_KEYS = {"right_arm": "rightElbow", "left_arm": "leftElbow", "right_leg": "rightKnee", "left_leg": "leftKnee"}
PIVOT = {"torso": (0, 1, 0), "head": (0, 2, 0), "left_arm": (-1.5, 1.5, 0), "right_arm": (1.5, 1.5, 0), "left_leg": (-0.5, 0, 0), "right_leg": (0.5, 0, 0)}
CLASSIC = {"head": "#F5CD30", "torso": "#0D69AC", "arms": "#F5CD30", "legs": "#A4BD47"}


def _rot(axis, deg):
    return Matrix.Rotation(math.radians(deg), 4, axis)


def joints(emote=None, gliding=False, lying=None):
    """Root space -> each part's joint (Minecraft-style space), exactly as RobloxAvatarRenderer.jointMatrix: emote = a
    pose with the emote_keys sheet's columns (rightArmX ... twist; missing ones 0). lying: a matrix put before it all
    (the knocked crawl lays the body flat)."""
    e = emote or {}
    g = lambda k: e.get(k, 0.0)
    out = {}
    for part, pv in PIVOT.items():
        m = (lying or Matrix.Identity(4)) @ Matrix.Translation((0, g("bounce"), 0)) @ _rot("Y", g("bodyYaw")) @ _rot("X", -g("flip"))
        right = pv[0] > 0
        if not part.endswith("leg"):  # everything above the hips twists, leans and tilts together
            m = m @ _rot("Y", -g("twist")) @ _rot("X", -g("lean")) @ _rot("Z", -g("tilt"))
        m = m @ Matrix.Translation(pv)
        if part.endswith("arm"):
            y = 0
            if emote:
                x, z, y = (g("rightArmX"), g("rightArmZ"), g("rightArmY")) if right else (g("leftArmX"), g("leftArmZ"), g("leftArmY"))
            elif gliding:  # both hands up on the glider's grips
                x, z = 170, -15
            else:
                x, z = 0, 0
            # (an arm's Y turns it about its own length, so the elbow can fold any way)
            m = m @ _rot("X", x) @ _rot("Z", z if right else -z) @ _rot("Y", y if right else -y)
        elif part.endswith("leg"):
            x, z = (g("rightLegX"), g("rightLegZ")) if right else (g("leftLegX"), g("leftLegZ"))
            m = m @ _rot("X", x) @ _rot("Z", z if right else -z)
        elif part == "head":
            m = m @ _rot("X", -g("headX")) @ _rot("Y", -g("headY")) @ _rot("Z", -g("headZ"))
        out[part] = m
    return out


def bends(emote=None):
    """How far each elbow and knee is bent (degrees) in a pose with the sheets' columns (rightElbow ... leftKnee)."""
    e = emote or {}
    return {p: max(0.0, float(e.get(k, 0.0))) for p, k in BEND_KEYS.items()}


def hand(js, bd, right=True):
    """Root space -> the hand end of an arm (its joint, then the forearm's turn at the elbow): what a held item follows."""
    part = "right_arm" if right else "left_arm"
    return js[part] @ Matrix(bend.lower_matrix("arm", bd.get(part, 0.0)))


def place(parts, joint):
    """Puts parts made in a joint's space (Minecraft-style coordinates) where the joint is."""
    for p in parts:
        p.obj.matrix_world = C @ joint @ C.inverted() @ p.obj.matrix_world


def build(pose="standing", face=True, emote=None, colors=None):
    """The avatar; returns its joints. pose: standing or gliding. colors: head, torso, arms, legs ('#rrggbb')."""
    col = {**CLASSIC, **(colors or {})}
    js = joints(emote, pose == "gliding")
    global BUILT
    _BENT.clear()
    if ROBLOX:
        BUILT = {k: [v] for k, v in roblox_body.build(ROBLOX, js, col).items()}
        _bend(bends(emote))
        return js
    skin = mat("av_head", col["head"], rough=0.6)
    arms = mat("av_arms", col["arms"], rough=0.6)
    made = {"torso": [box(-1, -1, -0.5, 1, 1, 0.5, mat("av_torso", col["torso"], rough=0.6), bevel=0.08)],
            "head": [cyl(0, HEAD_H / 2, 0, HEAD_R, HEAD_H, "y", skin, verts=28, bevel=0.22, seg=4)]}
    if face:
        black = mat("av_face", "#1B1B1B", rough=0.5)
        for s in (-1, 1):
            made["head"].append(sphere(s * 0.2, 0.72, -HEAD_R + 0.02, 0.07, 0.12, 0.04, black, segs=12, rings=6))
        smile = torus(0, 0.58, -HEAD_R + 0.03, 0.26, 0.035, "z", black, segs=16, rsegs=5, arc=150)
        smile.rot("z", 195, (0, 0.58, -HEAD_R + 0.03))
        made["head"].append(smile)
    for side in ("left", "right"):
        made[side + "_arm"] = [box(-0.5, -1.5, -0.5, 0.5, 0.5, 0.5, arms, bevel=0.08)]
        made[side + "_leg"] = [box(-0.5, -2, -0.5, 0.5, 0, 0.5, mat("av_legs", col["legs"], rough=0.6), bevel=0.08)]
    for part, ps in made.items():
        place(ps, js[part])
        for p in ps:
            p.obj["br_preview"] = 1
    BUILT = {k: [p.obj for p in v] for k, v in made.items()}
    return js


BUILT = {}  # the last avatar built: {part: [objects]}, each made in its joint's space


def repose(js, bd=None):
    """Moves the last avatar built to new joints (an emote's next frame), its elbows and knees bent by bd ({limb:
    degrees}, avatar.bends)."""
    for part, objs in BUILT.items():
        for o in objs:
            o.matrix_world = C @ js[part] @ C.inverted()
    _bend(bd or {})


_BENT = {}  # each limb's bend as last drawn


def _bend(bd):
    """Bends the real body's arms and legs (cut across the elbow and knee when built: roblox_body.REST) as the game
    does (bend.py); the stand-in's boxes stay straight."""
    for part, (pts, nrms) in roblox_body.REST.items():
        deg = bd.get(part, 0.0)
        if part in _BENT and abs(_BENT[part] - deg) < 1e-4 or part not in BUILT:
            continue
        _BENT[part] = deg
        me = BUILT[part][0].data
        co, ns = [], []
        for (x, y, z), n in zip(pts, nrms):
            x2, y2, z2, a = bend.bend_point(x, y, z, "arm" if part.endswith("arm") else "leg", deg)
            co += [x2, -z2, y2]  # (C: Roblox joint space -> Blender)
            c, s_ = math.cos(a), math.sin(a)
            ny, nz = n[1] * c - n[2] * s_, n[1] * s_ + n[2] * c
            ns.append((n[0], -nz, ny))
        me.vertices.foreach_set("co", co)
        me.update()
        me.normals_split_custom_set(ns)
