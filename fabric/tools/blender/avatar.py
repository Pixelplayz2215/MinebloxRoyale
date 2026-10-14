"""
The classic Roblox (R6) avatar for the renders only (never exported): gliders are shown held over its head, cosmetics
worn on it, emotes posed on it, so their size shows. The real body from the Roblox install when there is one
(roblox_body), else a stand-in made of boxes. Roblox root space (studs, y up from the hips, facing -z), each body part
where the avatar parts sheet puts it, turned about its joint as the game turns it (RobloxAvatarRenderer.jointMatrix).
"""
import math

import roblox_body
from brlib import box, cyl, mat, sphere, torus
from mathutils import Matrix

ROBLOX = roblox_body.content()

HEAD_R, HEAD_H = 0.6, 1.2  # (the game's head: 1.2 across, 1.2 tall)
C = Matrix(((1, 0, 0, 0), (0, 0, -1, 0), (0, 1, 0, 0), (0, 0, 0, 1)))
PIVOT = {"torso": (0, 1, 0), "head": (0, 2, 0), "left_arm": (-1.5, 1.5, 0), "right_arm": (1.5, 1.5, 0), "left_leg": (-0.5, 0, 0), "right_leg": (0.5, 0, 0)}
CLASSIC = {"head": "#F5CD30", "torso": "#0D69AC", "arms": "#F5CD30", "legs": "#A4BD47"}


def _rot(axis, deg):
    return Matrix.Rotation(math.radians(deg), 4, axis)


def joints(emote=None, gliding=False):
    """Root space -> each part's joint (Minecraft-style space), as RobloxAvatarRenderer.jointMatrix: emote = a pose
    {rightArmX, rightArmZ, leftArmX, leftArmZ, rightLegX, leftLegX, bodyYaw, bounce} (the emote_keys sheet's columns)."""
    e = emote or {}
    out = {}
    for part, pv in PIVOT.items():
        m = Matrix.Translation((0, e.get("bounce", 0), 0)) @ _rot("Y", e.get("bodyYaw", 0)) @ Matrix.Translation(pv)
        right = pv[0] > 0
        if part.endswith("arm"):
            if emote:
                x, z = (e["rightArmX"], e["rightArmZ"]) if right else (e["leftArmX"], e["leftArmZ"])
            elif gliding:  # both hands up on the glider's grips
                x, z = 170, -15
            else:
                x, z = 0, 0
            m = m @ _rot("X", x) @ _rot("Z", z if right else -z)
        elif part.endswith("leg") and emote:
            m = m @ _rot("X", e["rightLegX"] if right else e["leftLegX"])
        out[part] = m
    return out


def place(parts, joint):
    """Puts parts made in a joint's space (Minecraft-style coordinates) where the joint is."""
    for p in parts:
        p.obj.matrix_world = C @ joint @ C.inverted() @ p.obj.matrix_world


def build(pose="standing", face=True, emote=None, colors=None):
    """The avatar; returns its joints. pose: standing or gliding. colors: head, torso, arms, legs ('#rrggbb')."""
    col = {**CLASSIC, **(colors or {})}
    js = joints(emote, pose == "gliding")
    if ROBLOX:
        roblox_body.build(ROBLOX, js, col)
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
    return js
