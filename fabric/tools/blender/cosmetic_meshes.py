"""
Every cosmetic model the game draws (build.py cosmetic_meshes): hats (and the ones made to sit over an afro), each haircut
for each kind of hat edge, face accessories, shoes and back bling. Models only; the labelled pictures of the Locker's
items are render_cosmetics.py's.
"""
import json
import os

import backbling
import clothes3d
import hair2
import hats2
import shoes2
import shapes
from cosmetics import CUTS, HEAD, OVER_HAIR, afro_headphones, face, over_afro
from shapes import S

ROWS = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "design", "cosmetics.json"), encoding="utf-8"))["rows"]


def shapes_of(slot):
    return sorted({r["shape"] for r in ROWS if r["slot"] == slot})


shapes.DETAIL = 1.4  # (creator: "smoothing": every curve in more pieces)
NO_RENDER = {"render": False, "scale": 1.0, "ao": 0.25, "lod": 0.35}  # (the lighter copy past 24 blocks)
ITEMS = {}
for sh in shapes_of("hat"):
    ITEMS["hat_" + sh] = (sh, lambda sh=sh: hats2.hat(sh, HEAD), NO_RENDER)
    if sh in OVER_HAIR:
        ITEMS["hat_" + sh + "_afro"] = (sh, (lambda: afro_headphones(S(), HEAD)) if sh == "headphones" else (lambda sh=sh: hats2.hat(sh, over_afro(sh, HEAD))), NO_RENDER)
for sh in shapes_of("hair"):
    for cut, hat_shape in CUTS.items():
        ITEMS[f"hair_{sh}_{cut}"] = (sh, lambda sh=sh, hs=hat_shape: hair2.hair(sh, HEAD, hs), NO_RENDER)
ITEMS["hair_curly_tiara"] = ("curly", lambda: hair2.hair("curly", HEAD, "tiara"), NO_RENDER)
for sh in shapes_of("face"):
    ITEMS["face_" + sh] = (sh, lambda sh=sh: face(S(), sh, HEAD), NO_RENDER)
for sh in shapes_of("shoes"):
    ITEMS["shoes_" + sh] = (sh, lambda sh=sh: shoes2.shoes(sh), NO_RENDER)
for r in ROWS:
    if r["slot"] == "back":
        ITEMS[r["id"]] = (r["name"], lambda r=r: backbling.draw(S(), r), NO_RENDER)
for sh in shapes_of("shirt"):  # shirts and pants: their 3D parts over the painted designs
    ITEMS[f"shirt_{sh}_torso"] = (sh, lambda sh=sh: clothes3d.shirt_torso(sh), NO_RENDER)
    for side, arm in ((1, "rarm"), (-1, "larm")):
        ITEMS[f"shirt_{sh}_{arm}"] = (sh, lambda sh=sh, side=side: clothes3d.shirt_arm(sh, side), NO_RENDER)
for sh in shapes_of("pants"):
    ITEMS[f"pants_{sh}_hips"] = (sh, lambda sh=sh: clothes3d.pants_hips(sh), NO_RENDER)
    for side, leg in ((1, "rleg"), (-1, "lleg")):
        ITEMS[f"pants_{sh}_{leg}"] = (sh, lambda sh=sh, side=side: clothes3d.pants_leg(sh, side), NO_RENDER)
