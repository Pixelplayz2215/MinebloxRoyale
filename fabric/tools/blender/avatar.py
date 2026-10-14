"""
A stand-in classic Roblox (R6) avatar for the renders only (never exported): gliders are shown held over its head,
cosmetics worn on it, so their size shows. Roblox root space (studs, y up from the hips, facing -z), the body parts
where the avatar parts sheet puts them.
"""
import math

import brlib
from brlib import box, cyl, mat, sphere, torus
from mathutils import Matrix, Vector

HEAD_Y, HEAD_R, HEAD_H = 2.6, 0.62, 1.2
C = Matrix(((1, 0, 0, 0), (0, 0, -1, 0), (0, 1, 0, 0), (0, 0, 0, 1)))


def _mark(parts):
    for p in parts:
        p.obj["br_preview"] = 1


def _turn(parts, pivot, rx, rz):
    """Turns parts about the shoulder: rx degrees about x (up is negative for these arms), then rz about z."""
    pv = Vector(pivot)
    m = Matrix.Translation(pv) @ Matrix.Rotation(math.radians(rz), 4, "Z") @ Matrix.Rotation(math.radians(rx), 4, "X") @ Matrix.Translation(-pv)
    for p in parts:
        p.obj.matrix_world = C @ m @ C.inverted() @ p.obj.matrix_world


def build(pose="standing", face=True):
    skin = mat("av_skin", "#F5CD30", rough=0.6)
    shirt = mat("av_torso", "#0D69AC", rough=0.6)
    legs = mat("av_legs", "#A4BD47", rough=0.6)
    parts = [box(-1, 0, -0.5, 1, 2, 0.5, shirt, bevel=0.08)]
    head = cyl(0, HEAD_Y, 0, HEAD_R, HEAD_H, "y", skin, verts=28, bevel=0.22, seg=4)
    parts.append(head)
    if face:
        black = mat("av_face", "#1B1B1B", rough=0.5)
        for s in (-1, 1):
            parts.append(sphere(s * 0.2, HEAD_Y + 0.12, -HEAD_R + 0.02, 0.07, 0.12, 0.04, black, segs=12, rings=6))
        smile = torus(0, HEAD_Y - 0.02, -HEAD_R + 0.03, 0.26, 0.035, "z", black, segs=16, rsegs=5, arc=150)
        smile.rot("z", 195, (0, HEAD_Y - 0.02, -HEAD_R + 0.03))
        parts.append(smile)
    for s in (-1, 1):
        arm = [box(s * 1.5 - 0.5, 0, -0.5, s * 1.5 + 0.5, 2, 0.5, skin, bevel=0.08)]
        if pose == "gliding":  # both hands up on the glider's grips
            _turn(arm, (s * 1.5, 1.5, 0), 170, -s * 15 * -1)
        parts += arm
        parts.append(box(s * 0.5 - 0.5, -2, -0.5, s * 0.5 + 0.5, 0, 0.5, legs, bevel=0.08))
    _mark(parts)
    return parts
