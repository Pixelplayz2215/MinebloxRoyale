"""
The avatar's pose maths without Blender (numpy): where each body part is for a pose (as avatar.joints and the game's
RobloxAvatarRenderer.jointMatrix), elbows and knees included (bend.py), and the two fixes every emote and move frame
gets before it is baked (emote_video.py):
  floor   feet never go through the floor: a dip bends the knees instead (the hip forward a, the knee 2a)
  touch   the body's height set so the lowest foot rests on the floor (walking, running, kneeling)
  clear   a hand going into the chest, the hips or the head instead bends its elbow (or swings a little further
          out of the way) just enough to stay in front of it
Poses are dicts with the sheets' columns (rightArmX ... twist, rightElbow ... leftKnee), missing ones 0.
"""
import math

import numpy as np

import bend

PIVOT = {"torso": (0, 1, 0), "head": (0, 2, 0), "left_arm": (-1.5, 1.5, 0), "right_arm": (1.5, 1.5, 0), "left_leg": (-0.5, 0, 0),
         "right_leg": (0.5, 0, 0)}
BEND_KEYS = {"right_arm": "rightElbow", "left_arm": "leftElbow", "right_leg": "rightKnee", "left_leg": "leftKnee"}
FLOOR = -2.0  # (root space: the hips at 0, the feet 2 studs down when standing)


def rot(axis, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    m = np.eye(4)
    if axis == "X":
        m[1:3, 1:3] = [[c, -s], [s, c]]
    elif axis == "Y":
        m[0, 0], m[0, 2], m[2, 0], m[2, 2] = c, s, -s, c
    else:
        m[0:2, 0:2] = [[c, -s], [s, c]]
    return m


def tr(x, y, z):
    m = np.eye(4)
    m[:3, 3] = (x, y, z)
    return m


def joints(e, lying=None):
    g = lambda k: float(e.get(k, 0.0))
    out = {}
    for part, pv in PIVOT.items():
        m = (lying if lying is not None else np.eye(4)) @ tr(0, g("bounce"), 0) @ rot("Y", g("bodyYaw"))
        right = pv[0] > 0
        if not part.endswith("leg"):
            m = m @ rot("Y", -g("twist")) @ rot("X", -g("lean")) @ rot("Z", -g("tilt"))
        m = m @ tr(*pv)
        if part.endswith("arm"):
            x, z = (g("rightArmX"), g("rightArmZ")) if right else (g("leftArmX"), g("leftArmZ"))
            m = m @ rot("X", x) @ rot("Z", z if right else -z)
        elif part.endswith("leg"):
            x, z = (g("rightLegX"), g("rightLegZ")) if right else (g("leftLegX"), g("leftLegZ"))
            m = m @ rot("X", x) @ rot("Z", z if right else -z)
        elif part == "head":
            m = m @ rot("X", -g("headX")) @ rot("Y", -g("headY")) @ rot("Z", -g("headZ"))
        out[part] = m
    return out


def bends(e):
    return {p: max(0.0, float(e.get(k, 0.0))) for p, k in BEND_KEYS.items()}


# Where a leg meets the floor: across the middle of its sole (a tilted shin's toe or heel may dip a little: there's
# no ankle), down its front (the outside of the knee, kneeling) and its back (lying or sitting).
LEG_SAMPLES = ([(x, -2.0, z) for x in (-0.5, 0.5) for z in (-0.25, 0.0, 0.25)]
               + [(x, y, z) for x in (-0.5, 0.5) for y in (-0.55, -0.8, -1.0, -1.2, -1.45, -1.75) for z in (-0.5, 0.5)])


SOLE = [(x, -2.0, 0.0) for x in (-0.5, 0.5)]  # (just the middle of the sole: an emote's dip)


def leg_low(js, bd, leg, samples=LEG_SAMPLES):
    lo = 1e9
    for x, y, z in samples:
        x2, y2, z2, _ = bend.bend_point(x, y, z, "leg", bd[leg])
        lo = min(lo, (js[leg] @ np.array((x2, y2, z2, 1.0)))[1])
    return lo


def lowest(e):
    js, bd = joints(e), bends(e)
    return min(leg_low(js, bd, "left_leg"), leg_low(js, bd, "right_leg"))


# ------------------------------------------------------------------------------------------------- feet

LEG_COLS = {"right_leg": ("rightLegX", "rightKnee"), "left_leg": ("leftLegX", "leftKnee")}


def floor(e):
    """Feet never below the floor: each leg that would go through it bends at the knee instead (hip forward a, knee
    2a, the smallest a that keeps it on the floor)."""
    e = dict(e)
    for leg, (hx, kn) in LEG_COLS.items():
        js, bd = joints(e), bends(e)
        if leg_low(js, bd, leg, SOLE) >= FLOOR - 1e-3:
            continue
        lo, hi = 0.0, 85.0
        for _ in range(22):
            mid = (lo + hi) / 2
            f = dict(e)
            f[hx] = e.get(hx, 0.0) + mid
            f[kn] = e.get(kn, 0.0) + 2 * mid
            if leg_low(joints(f), bends(f), leg, SOLE) >= FLOOR:
                hi = mid
            else:
                lo = mid
        e[hx] = e.get(hx, 0.0) + hi
        e[kn] = e.get(kn, 0.0) + 2 * hi
    return e


def touch(e):
    """The lowest foot on the floor (the legs say how high the body is), plus any hop the pose's bounce asks for."""
    e = dict(e)
    hop = max(0.0, float(e.get("bounce", 0.0)))
    e["bounce"] = 0.0
    e["bounce"] = FLOOR - lowest(e) + hop
    return e


# ------------------------------------------------------------------------------------------------- hands

BOX = {"torso": ((-1, -1, -0.5), (1, 1, 0.5)), "head": ((-0.6, 0.02, -0.6), (0.6, 1.2, 0.6)), "thigh": ((-0.5, -0.8, -0.5), (0.5, 0, 0.5))}
FORE = ((-0.5, -1.5, -0.5), (0.5, bend.LIMB["arm"][0] - bend.HALF, 0.5))  # the forearm, below the elbow


def obb(m, box):
    lo, hi = np.array(box[0], float), np.array(box[1], float)
    return (m @ np.append((lo + hi) / 2, 1))[:3], m[:3, :3], (hi - lo) / 2


def depth(a, b):
    """How far two boxes go into each other (0 = apart), by separating axes."""
    ca, ra, ea = a
    cb, rb, eb = b
    axes = [ra[:, i] for i in range(3)] + [rb[:, i] for i in range(3)]
    for i in range(3):
        for j in range(3):
            v = np.cross(ra[:, i], rb[:, j])
            n = np.linalg.norm(v)
            if n > 1e-6:
                axes.append(v / n)
    d, best = cb - ca, 1e9
    for n in axes:
        o = sum(abs(ra[:, i] @ n) * ea[i] for i in range(3)) + sum(abs(rb[:, i] @ n) * eb[i] for i in range(3)) - abs(d @ n)
        if o <= 0:
            return 0.0
        best = min(best, o)
    return best


ARM_COLS = {"right_arm": ("rightArmX", "rightArmZ", "rightElbow"), "left_arm": ("leftArmX", "leftArmZ", "leftElbow")}


def hand_into(e, arm):
    """How deep this arm's forearm goes into the torso, the head or a thigh."""
    js, bd = joints(e), bends(e)
    fore = obb(js[arm] @ np.array(bend.lower_matrix("arm", bd[arm])), FORE)
    worst = depth(fore, obb(js["torso"], BOX["torso"]))
    worst = max(worst, depth(fore, obb(js["head"], BOX["head"])))
    for leg in ("left_leg", "right_leg"):
        worst = max(worst, depth(fore, obb(js[leg], BOX["thigh"])))
    return worst


TOL = 0.12  # (a hand resting on the body may touch it: under a pixel in the game)


def clear(e, keep=()):
    """Each arm whose hand goes into the body (but those in keep): the smallest change that keeps it out, as {column:
    change}: more bend at the elbow, or the arm a little further forward / back (whichever is less)."""
    out = {}
    for arm, (xc, zc, ec) in ARM_COLS.items():
        if arm in keep or hand_into(e, arm) <= TOL:
            continue
        best = None
        for col, sign in ((ec, 1), (xc, 1 if e.get(xc, 0.0) >= 0 else -1)):
            for d in range(3, 121, 3):
                f = dict(e)
                f[col] = e.get(col, 0.0) + sign * d
                if hand_into(f, arm) <= TOL:
                    if best is None or d < best[2]:
                        best = (col, sign, d)
                    break
        if best:
            out[best[0]] = best[1] * best[2]
    return out
