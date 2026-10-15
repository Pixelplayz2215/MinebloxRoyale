"""
Elbows and knees (creator: "add knees and elbows so the animations are more animated"): each arm and leg bends in its
middle, the same way here (renders, the emote and move sheets' maths: rig.py) as in the game (RobloxAvatarRenderer).

The bend turns the limb about an axis across its middle (its centre line keeps its length, so a bent leg is no longer
than a straight one): above a stretch round the middle nothing moves, below it the rest turns as one piece, and in
between each slice turns part of the way, so the outside of the bend is a smooth round corner and the inside is
squeezed. Elbows bend the forearm forward, knees bend the shin back. Everything is in the limb's joint space (Roblox
studs, y up, front -z; the joint at 0): the arms' middle is 0.5 below the shoulder joint, the legs' 1 below the hip
joint.

How long that stretch is depends on how far the limb bends (creator, 1.0.1-test4: "behind the knees the textures
overlap"): turning a slice squeezes the inside of the bend, and squeezed too hard the inside folds back over itself (two
layers of the same surface in one place, one seen from behind: it flickers). A stretch of length L turned by a (radians)
squeezes a surface d from the middle line to 1 - a d / L of its length, so the stretch is at least a D / SQUEEZE long, D
being how far anything worn sticks out on the inside of that joint: never squeezed past 1 - SQUEEZE, never folded. A
slight bend stays as short as before (2 * HALF); a deep one curves over more of the limb, reaching at most UP above the
middle (an arm's elbow is only 0.5 below its shoulder joint) and the rest below.
"""
import math

HALF = 0.25
SQUEEZE = 0.9
LIMB = {  # middle (joint-space y), which way the far end turns about +x (elbow: forward = towards -z)
    "arm": (-0.5, 1),
    "leg": (-1.0, -1),
}
INNER = {"arm": 0.5, "leg": 0.64}  # how far out on the inside of the bend anything sits (the body; boot rims behind the knee)
UP = {"arm": 0.45, "leg": 0.9}  # how far above the middle the bend may reach (an arm's stays below its shoulder joint)
STEP = 0.1  # meshes are cut across the limb this often where it can bend
REACH = {"arm": (-1.0, 0.45), "leg": (-1.0, 0.9)}  # (lowest, highest) cut, from the middle: the longest bend (STEP)


def kind(part):
    return "arm" if part.endswith("arm") else "leg" if part.endswith("leg") else None


def span(part_kind, deg):
    """The stretch that bends: (how far above the middle, how far below), studs."""
    a = math.radians(max(0.0, deg))
    length = max(2 * HALF, a * INNER[part_kind] / SQUEEZE)
    up = min(length / 2, UP[part_kind])
    return up, length - up


def turn(part_kind, deg, s):
    """How far (radians, about +x) the slice s studs above the middle turns."""
    up, down = span(part_kind, deg)
    if s >= up:
        return 0.0
    w = 1.0 if s <= -down else (up - s) / (up + down)
    return LIMB[part_kind][1] * math.radians(deg) * w


def bend_point(x, y, z, part_kind, deg):
    """A point of the limb (joint space) bent by deg degrees: (x, y, z, turn), turn being how far (radians, about +x)
    its surface turned, for normals."""
    if deg <= 0.01:
        return x, y, z, 0.0
    mid = LIMB[part_kind][0]
    s = y - mid
    a = turn(part_kind, deg, s)
    if a == 0.0:
        return x, y, z, 0.0
    c, sn = math.cos(a), math.sin(a)
    return x, mid + s * c - z * sn, s * sn + z * c, a


def unbend(y, z, part_kind, deg):
    """Where a bent point (joint-space y, z) was before the bend: (y, z). Each slice turns about the same axis, so the
    point lies on the slice whose turn, undone, brings it back onto that slice's own line; lines far outside the limb
    can cross it too, so of those the one nearest the middle line (the limb is never folded: only one is inside it)."""
    if deg <= 0.01:
        return y, z
    mid = LIMB[part_kind][0]
    ry, rz = y - mid, z

    def back(s):  # the point turned back by slice s's turn: (its distance along the limb, less s; across it)
        a = turn(part_kind, deg, s)
        c, sn = math.cos(a), math.sin(a)
        return ry * c + rz * sn - s, -ry * sn + rz * c

    best = None
    grid = [-3.0 + i * 0.01 for i in range(601)]
    prev = back(grid[0])[0]
    for s0, s1 in zip(grid, grid[1:]):
        cur = back(s1)[0]
        if (prev > 0) != (cur > 0) or cur == 0:
            lo, hi = s0, s1
            for _ in range(50):
                m = (lo + hi) / 2
                if (back(m)[0] > 0) == (back(lo)[0] > 0):
                    lo = m
                else:
                    hi = m
            s = (lo + hi) / 2
            across = back(s)[1]
            if best is None or abs(across) < abs(best[1]):
                best = (s, across)
        prev = cur
    if best is None:  # (beyond the grid: the rigid part above or below)
        s = 3.5 if ry > 0 else -3.5
        a = turn(part_kind, deg, s)
        c, sn = math.cos(a), math.sin(a)
        return mid + ry * c + rz * sn, -ry * sn + rz * c
    return mid + best[0], best[1]


def lower_matrix(part_kind, deg):
    """The turn of the limb's far end (below the bend: the hand, the foot) as a 4x4 row-major nested list, joint space
    -> joint space. (What a held item follows.)"""
    mid, sign = LIMB[part_kind]
    a = sign * math.radians(max(0.0, deg))
    c, s = math.cos(a), math.sin(a)
    # T(0, mid, 0) . Rx(a) . T(0, -mid, 0)
    return [[1, 0, 0, 0], [0, c, -s, mid - c * mid], [0, s, c, -s * mid], [0, 0, 0, 1]]


def cuts(part_kind, n=None):
    """Where to cut a limb's mesh (joint-space y) so its bend shows smoothly: every STEP across where it can bend."""
    mid = LIMB[part_kind][0]
    lo, hi = REACH[part_kind]
    k = int(round((hi - lo) / STEP))
    return [mid + hi - STEP * i for i in range(k + 1)]


def squeeze(part_kind, deg, z):
    """How much a surface z from the middle line keeps of its length round the bend (1 = none squeezed; <= 0 folded)."""
    up, down = span(part_kind, deg)
    rate = math.radians(max(0.0, deg)) / (up + down)
    inner = -z if part_kind == "arm" else z  # (the inside of an elbow is the front, of a knee the back)
    return 1 - rate * max(0.0, inner)


def split(verts, tris, plane_y, attrs=()):
    """Cuts triangles crossing y = plane_y in two (verts: list of [x, y, z]; attrs: parallel per-vertex lists that are
    interpolated, e.g. normals, uvs). Returns new (verts, tris, attrs)."""
    verts = [list(v) for v in verts]
    attrs = [[list(a) for a in at] for at in attrs]
    out = []
    eps = 1e-6

    def cut(i, j):
        a, b = verts[i], verts[j]
        t = (plane_y - a[1]) / (b[1] - a[1])
        verts.append([a[k] + (b[k] - a[k]) * t for k in range(3)])
        for at in attrs:
            at.append([at[i][k] + (at[j][k] - at[i][k]) * t for k in range(len(at[i]))])
        return len(verts) - 1

    for t in tris:
        side = [1 if verts[i][1] > plane_y + eps else -1 if verts[i][1] < plane_y - eps else 0 for i in t]
        if not (1 in side and -1 in side):
            out.append(tuple(t))
            continue
        for r in range(3):  # the corner alone on its side of the plane first
            a, b, c = t[r], t[(r + 1) % 3], t[(r + 2) % 3]
            sa, sb, sc = side[r], side[(r + 1) % 3], side[(r + 2) % 3]
            if sa != 0 and sb != sa and sc != sa:
                break
        if sb == 0 or sc == 0:  # one corner on the plane: two triangles
            other = c if sb == 0 else b
            m = cut(a, other)
            out += [(a, b, m), (b, c, m)] if sb == 0 else [(a, m, c), (m, b, c)]
            continue
        m1, m2 = cut(a, b), cut(a, c)
        out += [(a, m1, m2), (m1, b, c), (m1, c, m2)]
    return verts, out, attrs


if __name__ == "__main__":
    for kd in ("arm", "leg"):
        mid, sign = LIMB[kd]
        for deg in (10, 45, 90, 110, 135, 160):
            up, down = span(kd, deg)
            for z in (-0.5, 0.0, 0.5):
                for y in (mid + up, mid - down):  # continuous at both ends of the bend
                    a, b = bend_point(0, y + 1e-7, z, kd, deg), bend_point(0, y - 1e-7, z, kd, deg)
                    assert all(abs(a[k] - b[k]) < 1e-5 for k in range(3)), (kd, deg, z, a, b)
                p = bend_point(0.2, mid - down - 0.05, z, kd, deg)
                m = lower_matrix(kd, deg)
                q = [sum(m[r][c] * v for c, v in enumerate((0.2, mid - down - 0.05, z, 1))) for r in range(3)]
                assert all(abs(p[k] - q[k]) < 1e-6 for k in range(3)), (kd, deg, z, p, q)
            end = bend_point(0, mid - 1.0, 0, kd, deg)  # the far end turns the right way and keeps its distance
            assert (end[2] < 0) == (kd == "arm"), (kd, deg, end)
            assert abs(math.hypot(end[1] - mid, end[2]) - 1.0) < 1e-9
            # never folded: anything worn on the inside keeps at least 1 - SQUEEZE of its length, and the bend never
            # reaches up to the shoulder joint
            assert squeeze(kd, deg, INNER[kd] * (-1 if kd == "arm" else 1)) >= 1 - SQUEEZE - 1e-9, (kd, deg)
            assert kd != "arm" or mid + up <= -0.05 + 1e-9
            # numerically too: neighbouring points on the inside surface stay in order along it
            zi = INNER[kd] * (-1 if kd == "arm" else 1)
            ys = [mid + up + 0.05 - i * 0.002 for i in range(int((up + down + 0.1) / 0.002))]
            pts = [bend_point(0, y, zi, kd, deg) for y in ys]
            for i in range(1, len(pts) - 1):
                d1 = (pts[i][1] - pts[i - 1][1], pts[i][2] - pts[i - 1][2])
                d2 = (pts[i + 1][1] - pts[i][1], pts[i + 1][2] - pts[i][2])
                assert d1[0] * d2[0] + d1[1] * d2[1] > 0, (kd, deg, ys[i])
            # unbend finds a point of the limb that bends back to the same place (deep bends press the far end into the
            # near one: a point there is in both)
            for y in (mid + 0.3, mid, mid - 0.3, mid - 0.8):
                for z in (-0.5, 0.0, 0.5):
                    _, by, bz, _ = bend_point(0, y, z, kd, deg)
                    uy, uz = unbend(by, bz, kd, deg)
                    _, ry, rz, _ = bend_point(0, uy, uz, kd, deg)
                    assert abs(ry - by) < 1e-6 and abs(rz - bz) < 1e-6 and abs(uz) <= 0.5 + 1e-6, (kd, deg, y, z, uy, uz)
        # the cut splits a triangle into pieces that cover it
        v, t, (n,) = split([[0, 0, 0], [1, -1, 0], [0, -1, 0]], [(0, 1, 2)], -0.5, [[[0, 0, 1]] * 3])
        assert len(t) == 3 and len(v) == 5
    print("bend ok:", {k: [tuple(round(x, 2) for x in span(k, d)) for d in (30, 60, 90, 110, 128)] for k in LIMB})
