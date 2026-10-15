"""
The avatar's pose maths without Blender (numpy): where each body part is for a pose (as avatar.joints and the game's
RobloxAvatarRenderer.jointMatrix), elbows and knees included (bend.py), and the two fixes every emote and move frame
gets before it is baked (emote_video.py):
  floor   feet never go through the floor: a dip bends the knees instead (the hip forward a, the knee 2a)
  touch   the body's height set so the lowest foot rests on the floor (walking, running, kneeling)
  clear   nothing goes into anything: an arm or leg that would (anywhere over its surface, bend included) instead
          bends its elbow or swings a little further out of the way, just enough
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


# ------------------------------------------------------------------------------------------------- no clipping

# The body as solids (the real Roblox R6 parts: boxes with barely-rounded edges, the head a rounded cylinder), each in
# its joint's space. An arm or leg is checked over its whole surface, bend included, against every other part, with no
# tolerance: nothing may go into anything (creator: "make sure everything is frame and pixel perfect"). Only where the
# parts are joined do they meet, as in Roblox itself: an upper arm may sit a little into the torso at its shoulder (an
# R6 arm turns about its own middle, so turning it in at all does that), a thigh at its hip; from the elbow / knee down
# nothing may touch.
SHOULDER_CAP = -0.5  # (joint-space y: an arm above its elbow's middle is its upper arm, by the shoulder)
HIP_CAP = -0.45
JOINT_ALLOW = 0.2  # (how far an upper arm / thigh may sit into the torso where it is joined on: the joint itself)
EPS = 0.01  # (studs: 0.0036 blocks, well under a pixel at any distance)
LIMBS = ("right_arm", "left_arm", "right_leg", "left_leg")
EXTENT = {"arm": (-1.5, 0.5), "leg": (-2.0, 0.0)}


def _surface(kind, step=0.1):
    """Points over a limb's box surface (joint space, unbent)."""
    y0, y1 = EXTENT[kind]
    xs = np.linspace(-0.5, 0.5, int(round(1 / step)) + 1)
    ys = np.linspace(y0, y1, int(round((y1 - y0) / step)) + 1)
    pts = []
    for x in xs:
        for y in ys:
            pts += [(x, y, -0.5), (x, y, 0.5)]
    for z in xs:
        for y in ys:
            pts += [(-0.5, y, z), (0.5, y, z)]
    for x in xs:
        for z in xs:
            pts += [(x, y0, z), (x, y1, z)]
    return np.unique(np.round(np.array(pts), 6), axis=0)


SURF = {k: _surface(k) for k in ("arm", "leg")}


def _bent(kind, deg):
    """The limb's surface points bent (joint space)."""
    pts = SURF[kind]
    if deg <= 0.01:
        return pts
    return np.array([bend.bend_point(x, y, z, kind, deg)[:3] for x, y, z in pts])


def _to_local(m, world):
    inv = np.linalg.inv(m)
    return (inv[:3, :3] @ world.T).T + inv[:3, 3]


def _box_inside(q, lo, hi):
    """How deep each point is inside a box (> 0 inside)."""
    return np.minimum(q - lo, hi - q).min(axis=1)


def _head_inside(q):
    """How deep each point is inside the head (a cylinder up the y axis, 0.6 across, 0 to 1.2 tall)."""
    r = 0.6 - np.hypot(q[:, 0], q[:, 2])
    h = np.minimum(q[:, 1], 1.2 - q[:, 1])
    return np.minimum(r, h)


def _limb_inside(kind, deg, joint, world):
    """How deep each world point is inside a (bent) limb: its upper half and its turned lower half as two boxes, each
    reaching to the middle of the bend (a little more than the round elbow itself: on the safe side)."""
    y0, y1 = EXTENT[kind]
    mid = bend.LIMB[kind][0]
    q = _to_local(joint, world)
    upper = _box_inside(q, np.array((-0.5, mid, -0.5)), np.array((0.5, y1, 0.5)))
    ql = _to_local(joint @ np.array(bend.lower_matrix(kind, deg)), world)
    lower = _box_inside(ql, np.array((-0.5, y0, -0.5)), np.array((0.5, mid, 0.5)))
    return np.maximum(upper, lower)


TORSO_SURF = None
HEAD_SURF = None


def _torso_surface(step=0.1):
    xs, ys, zs = np.linspace(-1, 1, 21), np.linspace(-1, 1, 21), np.linspace(-0.5, 0.5, 11)
    pts = [(x, y, z) for x in xs for y in ys for z in (-0.5, 0.5)] + [(x, y, z) for x in (-1, 1) for y in ys for z in zs] + \
          [(x, y, z) for x in xs for y in (-1, 1) for z in zs]
    return np.unique(np.round(np.array(pts), 6), axis=0)


def _head_surface():
    pts = []
    for a in np.linspace(0, 2 * np.pi, 36, endpoint=False):
        for y in np.linspace(0, 1.2, 13):
            pts.append((0.6 * np.cos(a), y, 0.6 * np.sin(a)))
        for r in (0.15, 0.3, 0.45):
            pts += [(r * np.cos(a), 0.0, r * np.sin(a)), (r * np.cos(a), 1.2, r * np.sin(a))]
    return np.array(pts)


def _world(m, local):
    return (m[:3, :3] @ local.T).T + m[:3, 3]


def _parts(e, limb):
    """How far one arm or leg goes into each other part (studs, > 0 = into it), both ways round, leaving out where it
    is joined on: {part: depth}."""
    global TORSO_SURF, HEAD_SURF
    if TORSO_SURF is None:
        TORSO_SURF, HEAD_SURF = _torso_surface(), _head_surface()
    js, bd = joints(e), bends(e)
    kind = "arm" if limb.endswith("arm") else "leg"
    world = _world(js[limb], _bent(kind, bd[limb]))
    ys = SURF[kind][:, 1]
    cap = SHOULDER_CAP if kind == "arm" else HIP_CAP
    out = {}
    # its surface in the torso (the upper arm / thigh may sit in a little where it is joined on)
    torso = _box_inside(_to_local(js["torso"], world), np.array((-1, -1, -0.5)), np.array((1, 1, 0.5)))
    d = max(float(torso[ys < cap].max(initial=-1)), float(torso[ys >= cap].max(initial=-1)) - JOINT_ALLOW)
    # and the torso's surface in it: none in its lower part, a little in its upper part
    tw = _world(js["torso"], TORSO_SURF)
    y0, y1 = EXTENT[kind]
    mid = bend.LIMB[kind][0]
    up = _box_inside(_to_local(js[limb], tw), np.array((-0.5, mid, -0.5)), np.array((0.5, y1, 0.5)))
    low = _box_inside(_to_local(js[limb] @ np.array(bend.lower_matrix(kind, bd[limb])), tw), np.array((-0.5, y0, -0.5)), np.array((0.5, mid, 0.5)))
    d = max(d, float(low.max()), float(up.max()) - JOINT_ALLOW)
    out["torso"] = d
    if kind == "arm":
        out["head"] = max(float(_head_inside(_to_local(js["head"], world)).max()),
                          float(_limb_inside("arm", bd[limb], js[limb], _world(js["head"], HEAD_SURF)).max()))
    for other in LIMBS:
        if other == limb:
            continue
        ok = "arm" if other.endswith("arm") else "leg"
        if kind == "leg" and ok == "arm":  # (an arm answers for touching a leg)
            continue
        inside = _limb_inside(ok, bd[other], js[other], world)
        if kind == "leg" and ok == "leg":  # (the two hips sit side by side)
            inside = inside[ys < cap]
        d = float(inside.max(initial=-1))
        if kind == "arm":  # (and the other's surface in this arm)
            ow = _world(js[other], _bent(ok, bd[other]))
            d = max(d, float(_limb_inside(kind, bd[limb], js[limb], ow).max()))
        out[other] = d
    return out


def penetration(e, limb):
    """How far one arm or leg goes into anything else (studs; 0 = clear), both ways round, leaving out where it is
    joined on."""
    return max(0.0, max(_parts(e, limb).values()))


def hits(e, limb):
    """What one arm or leg goes into, and how far: {part: studs} (for finding out why)."""
    return {k: round(v, 3) for k, v in _parts(e, limb).items() if v > EPS}


def hand_into(e, arm):
    return penetration(e, arm)


ARM_COLS = {"right_arm": ("rightArmX", "rightArmZ", "rightElbow"), "left_arm": ("leftArmX", "leftArmZ", "leftElbow")}
LEG_FIX_COLS = {"right_leg": ("rightLegX", "rightLegZ", "rightKnee"), "left_leg": ("leftLegX", "leftLegZ", "leftKnee")}


def _smallest(e, limb, moves, limit=150.0):
    """The smallest amount d (degrees) of a move (columns and their signs) that clears a limb, or None."""
    bendcol = (ARM_COLS.get(limb) or LEG_FIX_COLS.get(limb))[2]

    def clear_at(d):
        f = dict(e)
        for col, sign in moves:
            f[col] = e.get(col, 0.0) + sign * d
        if not 0 <= f.get(bendcol, 0.0) <= (160 if limb.endswith("arm") else 165):  # (no bending past what a joint can)
            return False
        return penetration(f, limb) <= EPS

    prev = 0.0
    for d in list(np.arange(1, 10, 1.0)) + list(np.arange(10, limit + 0.1, 2.5)):
        if clear_at(d):
            lo, hi = prev, d
            for _ in range(12):
                m = (lo + hi) / 2
                if clear_at(m):
                    hi = m
                else:
                    lo = m
            return hi
        prev = d
    return None


def clear(e, keep=()):
    """Each limb that goes into the body: the smallest change that takes it out, as {column: change}. An arm bends its
    elbow, swings further forward / back or out to its side, or (an arm holding a gun, in keep) swings at the shoulder
    while its elbow turns back the same amount, so the forearm keeps pointing the same way. A leg swings out."""
    out = {}
    f = dict(e)
    for limb in LIMBS:
        if penetration(f, limb) <= EPS:
            continue
        if limb in ARM_COLS:
            xc, zc, ec = ARM_COLS[limb]
            fwd = 1 if f.get(xc, 0.0) >= 0 else -1
            if limb in keep:
                moves = [((xc, 1), (ec, -1)), ((xc, -1), (ec, 1))]
            else:
                moves = [((ec, 1),), ((xc, fwd),), ((zc, 1),), ((xc, fwd), (ec, 1))]
        else:
            xc, zc, kc = LEG_FIX_COLS[limb]
            moves = [((zc, 1),), ((zc, -1),), ((xc, 1),), ((xc, -1),)]
        best = None
        for mv in moves:
            d = _smallest(f, limb, mv)
            if d is not None and (best is None or d < best[0]):
                best = (d, mv)
        if best:
            d = best[0] + 1.5  # (a little more: clear between the samples too)
            for col, sign in best[1]:
                out[col] = out.get(col, 0.0) + sign * d
                f[col] = f.get(col, 0.0) + sign * d
    return out


# What a fix costs per degree (swinging out sideways changes the move most: a crossing arm should rather pass in front).
WEIGHT = {"elbow": 1.0, "swing": 1.0, "swing+elbow": 1.1, "out": 1.6, "in": 1.6, "kept": 1.0}


def _moves(limb, sample, keep):
    if limb in ARM_COLS:
        xc, zc, ec = ARM_COLS[limb]
        fwd = 1 if sample.get(xc, 0.0) >= 0 else -1
        if limb in keep:
            return {"kept": ((xc, 1), (ec, -1)), "kept-back": ((xc, -1), (ec, 1))}
        return {"elbow": ((ec, 1),), "swing": ((xc, fwd),), "out": ((zc, 1),), "swing+elbow": ((xc, fwd), (ec, 1))}
    xc, zc, kc = LEG_FIX_COLS[limb]
    return {"out": ((zc, 1),), "in": ((zc, -1),), "swing": ((xc, 1),), "swing-back": ((xc, -1),)}


def _runs(bad, n, cyclic):
    """Moments that clip, grouped into runs of moments next to each other (round the loop's end when it loops)."""
    runs, cur = [], []
    for i in bad:
        if cur and i != cur[-1] + 1:
            runs.append(cur)
            cur = []
        cur.append(i)
    if cur:
        runs.append(cur)
    if cyclic and len(runs) > 1 and runs[0][0] == 0 and runs[-1][-1] == n - 1:
        runs[0] = runs.pop() + runs[0]
    return runs


def fix_sequence(poses, cyclic, keep=(), smooth=None, log=print):
    """Clears a whole animation (poses at evenly spaced moments). For each limb, each run of moments where it would go
    into something gets one kind of fix (so it doesn't switch between them and jitter), each moment the smallest amount
    that clears it, eased in and out; then any moment still not clear gets its own fix. Changes poses in place; returns
    how many moments needed fixing."""
    fixed = 0
    n = len(poses)
    for limb in LIMBS:
        bad = [i for i, p in enumerate(poses) if penetration(p, limb) > EPS]
        if not bad:
            continue
        fixed = max(fixed, len(bad))
        for run in _runs(bad, n, cyclic):
            best = None
            for name, mv in _moves(limb, poses[run[len(run) // 2]], keep).items():
                ds = []
                for i in run:
                    d = _smallest(poses[i], limb, mv)
                    if d is None:
                        break
                    ds.append(d)
                else:
                    cost = max(ds) * WEIGHT.get(name.split("-")[0], 1.0)
                    if best is None or cost < best[0]:
                        best = (cost, name, mv, ds)
            if best is None:
                log(f"  {limb}: no one fix clears moments {run[0]}-{run[-1]}; fixing them one by one")
                continue
            _, name, mv, ds = best
            amount = [0.0] * n
            for i, d in zip(run, ds):
                amount[i] = d + 1.5
            if smooth:
                amount = [max(a, b) for a, b in zip(amount, smooth(amount, cyclic))]
            for p, a in zip(poses, amount):
                if a:
                    for col, sign in mv:
                        p[col] = p.get(col, 0.0) + sign * a
            log(f"  {limb}: moments {run[0]}-{run[-1]} of {n} would clip: {name} by up to {max(ds) + 1.5:.1f} degrees")
    for _ in range(4):  # (anything left, moment by moment)
        left = [i for i, p in enumerate(poses) if any(penetration(p, l) > EPS for l in LIMBS)]
        if not left:
            break
        for i in left:
            for col, v in clear(poses[i], keep).items():
                poses[i][col] = poses[i].get(col, 0.0) + v
    left = [i for i, p in enumerate(poses) if any(penetration(p, l) > EPS for l in LIMBS)]
    if left:
        log(f"  STILL CLIPPING at {len(left)} moments")
    return fixed
