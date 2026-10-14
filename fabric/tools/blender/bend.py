"""
Elbows and knees (creator: "add knees and elbows so the animations are more animated"): each arm and leg bends in its
middle, the same way here (renders, the emote and move sheets' maths: rig.py) as in the game (RobloxAvatarRenderer).

The bend turns the limb about an axis across its middle (its centre line keeps its length, so a bent leg is no longer
than a straight one): above a short stretch round the middle (2 * HALF studs) nothing moves, below it the rest turns
as one piece, and in between each slice turns part of the way, so the outside of the bend is a smooth round corner and
the inside tucks into the limb. Elbows bend the forearm forward, knees bend the shin back. Everything is in the
limb's joint space (Roblox studs, y up, front -z; the joint at 0): the arms' middle is 0.5 below the shoulder joint,
the legs' 1 below the hip joint.
"""
import math

HALF = 0.25
LIMB = {  # middle (joint-space y), which way the far end turns about +x (elbow: forward = towards -z)
    "arm": (-0.5, 1),
    "leg": (-1.0, -1),
}


def kind(part):
    return "arm" if part.endswith("arm") else "leg" if part.endswith("leg") else None


def bend_point(x, y, z, part_kind, deg):
    """A point of the limb (joint space) bent by deg degrees: (x, y, z, turn), turn being how far (radians, about +x)
    its surface turned, for normals."""
    if deg <= 0.01:
        return x, y, z, 0.0
    mid, sign = LIMB[part_kind]
    s = y - mid
    if s >= HALF:
        return x, y, z, 0.0
    w = 1.0 if s <= -HALF else (HALF - s) / (2 * HALF)
    a = sign * math.radians(deg) * w
    c, sn = math.cos(a), math.sin(a)
    return x, mid + s * c - z * sn, s * sn + z * c, a


def lower_matrix(part_kind, deg):
    """The turn of the limb's far end (below the bend: the hand, the foot) as a 4x4 row-major nested list, joint space
    -> joint space. (What a held item follows.)"""
    mid, sign = LIMB[part_kind]
    a = sign * math.radians(max(0.0, deg))
    c, s = math.cos(a), math.sin(a)
    # T(0, mid, 0) . Rx(a) . T(0, -mid, 0)
    return [[1, 0, 0, 0], [0, c, -s, mid - c * mid], [0, s, c, -s * mid], [0, 0, 0, 1]]


def cuts(part_kind, n=6):
    """Where to cut a limb's mesh (joint-space y) so its bend shows smoothly: across the bend."""
    mid = LIMB[part_kind][0]
    return [mid + HALF - 2 * HALF * i / n for i in range(n + 1)]


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
        for deg in (10, 45, 90, 135, 160):
            for z in (-0.5, 0.0, 0.5):
                for y in (mid + HALF, mid - HALF):  # continuous at both ends of the bend
                    a, b = bend_point(0, y + 1e-7, z, kd, deg), bend_point(0, y - 1e-7, z, kd, deg)
                    assert all(abs(a[k] - b[k]) < 1e-5 for k in range(3)), (kd, deg, z, a, b)
                p = bend_point(0.2, mid - 0.7, z, kd, deg)
                m = lower_matrix(kd, deg)
                q = [sum(m[r][c] * v for c, v in enumerate((0.2, mid - 0.7, z, 1))) for r in range(3)]
                assert all(abs(p[k] - q[k]) < 1e-6 for k in range(3)), (kd, deg, z, p, q)
            end = bend_point(0, mid - 1.0, 0, kd, deg)  # the far end turns the right way and keeps its distance
            assert (end[2] < 0) == (kd == "arm"), (kd, deg, end)
            assert abs(math.hypot(end[1] - mid, end[2]) - 1.0) < 1e-9
        # the cut splits a triangle into pieces that cover it
        v, t, (n,) = split([[0, 0, 0], [1, -1, 0], [0, -1, 0]], [(0, 1, 2)], -0.5, [[[0, 0, 1]] * 3])
        assert len(t) == 3 and len(v) == 5
    print("bend ok")
