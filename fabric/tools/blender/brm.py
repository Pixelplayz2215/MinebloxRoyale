"""
The game's mesh file (assets/minebloxroyale/meshes/<id>.brm, read by client/mesh/Mesh.java), without Blender.

BRM2 (gzip, little-endian): "BRM2", int vertex count, int corner count, float min x, y, z, max x, y, z. Then the
vertices field by field, each value stored as the change from the vertex before (wrapping round): x, y, z (u16 across
the bounds), then normal x, y, z (i8), r, g, b and flags (u8; flags: bits 0-1 tint channel, bit 2 glow). Then the
corners, three per triangle, u16 (int past 65535 vertices): 0 = the next vertex not used yet, n = the vertex n before
that one. Corners with the same data are stored once and the vertices come in the order they're first used, so the
numbers are small and repeat, and gzip packs them to about a tenth of BRM1 (every corner in full: "BRM1", int count,
then float x, y, z, i8 normal, u8 r, g, b, u8 flags per corner), which the game still reads.

python brm.py <meshes dir>: rewrites every BRM1 file there as BRM2.
"""
import gzip
import os
import struct
import sys

FIELDS = "HHHBBBBBBB"


def write(path, corners):
    """corners: [(x, y, z, nx, ny, nz, r, g, b, flags)] (normal as -127..127), three per triangle."""
    lo = [min(c[k] for c in corners) for k in range(3)] if corners else [0.0] * 3
    hi = [max(c[k] for c in corners) for k in range(3)] if corners else [0.0] * 3
    span = [max(hi[k] - lo[k], 1e-9) for k in range(3)]
    verts, order, codes = {}, [], []
    for c in corners:
        key = tuple(round((c[k] - lo[k]) / span[k] * 65535) for k in range(3)) + tuple(c[3:])
        i = verts.get(key)
        if i is None:
            i = verts[key] = len(order)
            order.append(key)
            codes.append(0)
        else:
            codes.append(len(order) - i)
    buf = bytearray(b"BRM2" + struct.pack("<ii6f", len(order), len(corners), *lo, *hi))
    for f, kind in enumerate(FIELDS):
        size = 65536 if kind == "H" else 256
        prev, vals = 0, []
        for v in order:
            vals.append((v[f] - prev) % size)
            prev = v[f]
        buf += struct.pack(f"<{len(vals)}{kind}", *vals)
    buf += struct.pack(f"<{len(codes)}{'H' if len(order) <= 65535 else 'i'}", *codes)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with gzip.open(path, "wb", compresslevel=9) as f:
        f.write(bytes(buf))


def read(path):
    """The corners of a BRM1 or BRM2 file, as write() takes them."""
    with gzip.open(path, "rb") as f:
        data = f.read()
    if data[:4] == b"BRM1":
        n = struct.unpack_from("<i", data, 4)[0]
        return [struct.unpack_from("<fffbbbBBBB", data, 8 + 19 * i) for i in range(n)]
    nv, nc = struct.unpack_from("<ii", data, 4)
    lo, hi = struct.unpack_from("<3f", data, 12), struct.unpack_from("<3f", data, 24)
    at = 36
    cols = []
    for kind in FIELDS:
        size = 65536 if kind == "H" else 256
        raw = struct.unpack_from(f"<{nv}{kind}", data, at)
        at += nv * struct.calcsize(kind)
        prev, col = 0, []
        for d in raw:
            prev = (prev + d) % size
            col.append(prev)
        cols.append(col)
    vs = []
    for i in range(nv):
        pos = tuple(lo[k] + cols[k][i] / 65535 * (hi[k] - lo[k]) for k in range(3))
        nrm = tuple(v - 256 if v > 127 else v for v in (cols[3][i], cols[4][i], cols[5][i]))
        vs.append(pos + nrm + tuple(cols[k][i] for k in range(6, 10)))
    codes = struct.unpack_from(f"<{nc}{'H' if nv <= 65535 else 'i'}", data, at)
    out, seen = [], 0
    for c in codes:
        if c == 0:
            out.append(vs[seen])
            seen += 1
        else:
            out.append(vs[seen - c])
    return out


if __name__ == "__main__":
    before = after = 0
    for name in sorted(os.listdir(sys.argv[1])):
        p = os.path.join(sys.argv[1], name)
        if not name.endswith(".brm"):
            continue
        with gzip.open(p, "rb") as f:
            old = f.read(4) == b"BRM1"
        if old:
            size = os.path.getsize(p)
            write(p, read(p))
            before += size
            after += os.path.getsize(p)
    print(f"{before / 1e6:.1f} MB -> {after / 1e6:.1f} MB")
