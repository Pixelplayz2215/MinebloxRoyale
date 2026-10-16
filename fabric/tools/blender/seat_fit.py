"""
Car seats (2.0.0-test4, creator: "When me or bots get inside some of the cars, we show on top rather than inside"):
where a rider sits in each kind of car, and how much smaller the sitting avatar is drawn to fit inside it
(CarEntity.SEATS, RobloxAvatarRenderer.SEAT_SCALE).

A Roblox avatar sitting is 1.54 blocks tall and 1.60 wide, taller than every car's inside (cars.py: a sedan's is 1.18,
a sports car's 0.92, an SUV's 1.50), and leaning it back doesn't fit it either. The windows are solid, so a rider is
never seen once inside: it's drawn smaller about its feet, just enough that no part of it pokes through the car's
shell. For each kind and row of seats: the biggest size that fits (in steps of 0.02), then the spot nearest the usual
one (side, along: + is the car's left and forwards) with the feet height in the middle of the heights that fit. Every
frame of the sit move is posed with the game's own pose maths (rig.py). Two riders side by side overlap in the middle,
inside the car where nobody sees.

python fabric/tools/blender/seat_fit.py        (a few minutes; prints each seat)
"""
import json
import math
import os

import numpy as np

import rig

HERE = os.path.dirname(os.path.abspath(__file__))
DESIGN = os.path.normpath(os.path.join(HERE, "..", "..", "..", "design"))
S = 0.36  # blocks per stud
HIP_Y = 0.72  # blocks from the entity's position (its feet) up to the root (hips), full size
M = 0.03  # inside the shell by this much

# cars.py KINDS: (cy, hx, hy, hz, cabin (y0, y1, z0, z1, zt0, zt1, hx0, hx1)); police and taxi are sedans
CARS = {
    "sedan": (0.64, 1.12, 0.30, 2.28, (0.90, 1.52, -1.25, 0.95, -0.95, 0.30, 1.00, 0.86)),
    "sports": (0.52, 1.16, 0.27, 2.30, (0.76, 1.17, -1.35, 0.75, -0.95, 0.00, 0.92, 0.74)),
    "pickup": (0.72, 1.15, 0.36, 2.35, (1.05, 1.80, -0.20, 1.20, -0.18, 0.78, 1.04, 0.96)),
    "suv": (0.74, 1.15, 0.36, 2.30, (1.07, 1.88, -2.05, 1.30, -1.95, 0.85, 1.06, 0.98)),
    "hatchback": (0.62, 1.08, 0.30, 1.95, (0.89, 1.53, -1.75, 0.95, -1.55, 0.35, 0.94, 0.82)),
}


def body_points(e):
    """Points over the whole posed avatar (studs, root space: y up, forward -z)."""
    js, bd = rig.joints(e), rig.bends(e)
    pts = []
    g = np.linspace(-1, 1, 9)
    torso = [(x, y, z) for x in g for y in g for z in (-0.5, 0.5)] + [(x, y, z) for x in (-1, 1) for y in g for z in np.linspace(-0.5, 0.5, 5)] \
        + [(x, y, z) for x in g for y in (-1, 1) for z in np.linspace(-0.5, 0.5, 5)]
    pts.append(rig._world(js["torso"], np.array(torso, dtype=float)))
    head = [(0.6 * math.cos(a), y, 0.6 * math.sin(a)) for a in np.linspace(0, 2 * math.pi, 24, endpoint=False) for y in (0.0, 0.6, 1.2)]
    pts.append(rig._world(js["head"], np.array(head)))
    for limb in rig.LIMBS:
        kind = "arm" if limb.endswith("arm") else "leg"
        pts.append(rig._world(js[limb], rig._bent(kind, bd[limb])))
    return np.vstack(pts)


def sitting():
    """Every few frames of the sit move, all together."""
    rows = [r for r in json.load(open(os.path.join(DESIGN, "move_keys.json"), encoding="utf-8"))["rows"] if r["move"] == "sit"]
    clouds = [body_points({k: v for k, v in r.items() if isinstance(v, (int, float))}) for r in rows[:: max(1, len(rows) // 6)]]
    return np.unique(np.round(np.vstack(clouds), 2), axis=0).astype(np.float32)


def worst(pts, car, s, side, along, feet):
    """How far the furthest point is outside the car's body or cabin (blocks; 0 inside), for each feet height."""
    cy, hx, hy, hz, (y0, y1, z0, z1, zt0, zt1, a, b) = car
    x = np.abs(side + pts[:, 0] * S * s)[None, :]
    y = feet[:, None] + (HIP_Y + pts[None, :, 1] * S) * s
    z = (along - pts[:, 2] * S * s)[None, :]
    mx = lambda parts: np.maximum.reduce(np.broadcast_arrays(*parts))
    body = mx([x - (hx - M), (cy - hy + M) - y, y - (cy + hy - M), np.abs(z) - (hz - M)])
    u = np.clip((y - y0) / (y1 - y0), 0, 1)
    zb, zf, w = z0 + (zt0 - z0) * u, z1 + (zt1 - z1) * u, a + (b - a) * u
    cab = mx([x - (w - M), (zb + M) - z, z - (zf - M), y0 - y, y - (y1 - M)])
    return np.maximum(np.minimum(body, cab), 0).max(axis=1)


def fit(pts, car, alongs, prefer):
    cy, hx, hy, hz, (y0, y1, *_) = car
    feet = np.arange(-0.4, 1.2, 0.01)
    tall = (pts[:, 1].max() - pts[:, 1].min()) * S
    top = min(1.0, (y1 - (cy - hy) - 2 * M) / tall)  # (no bigger than fits between the floor and the roof)
    for s in np.arange(math.floor(top * 50) / 50, 0.39, -0.02):
        found = []
        for side in (0.42, 0.38, 0.34, 0.30, 0.26, 0.22, 0.18, 0.14):
            for along in alongs:
                ok = np.nonzero(worst(pts, car, s, side, along, feet) <= 1e-6)[0]
                if len(ok):
                    found.append((abs(along - prefer), -side, float(along), float(feet[ok[len(ok) // 2]]), side))
        if found:
            _, _, along, f, side = min(found)
            return dict(scale=round(float(s), 2), side=side, along=round(along, 2), feet=round(f, 2))
    return None


def main():
    pts = sitting()
    for kind, car in CARS.items():
        front = fit(pts, car, np.arange(-0.8, 0.9, 0.05), prefer=0.15)
        print(kind, "front", front, flush=True)
        if kind != "pickup":  # (its back two ride in the open bed: CarEntity)
            back = fit(pts, car, np.arange(-1.9, front["along"] - 0.55, 0.05), prefer=front["along"] - 1.0)
            print(kind, "back ", back, flush=True)


if __name__ == "__main__":
    main()
