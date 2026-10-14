"""
Every cosmetic model the game draws (build.py cosmetic_meshes): hats (and the ones made to sit over an afro), each haircut
for each kind of hat edge, face accessories, shoes and back bling. Models only; the labelled pictures of the Locker's
items are render_cosmetics.py's.
"""
import json
import os

import backbling
from cosmetics import CUTS, HEAD, OVER_HAIR, afro_headphones, face, hair, hat, over_afro, shoes
from shapes import S

ROWS = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "design", "cosmetics.json"), encoding="utf-8"))["rows"]


def shapes_of(slot):
    return sorted({r["shape"] for r in ROWS if r["slot"] == slot})


NO_RENDER = {"render": False, "scale": 1.0, "ao": 0.25, "lod": 0.35}  # (the lighter copy past 24 blocks)
ITEMS = {}
for sh in shapes_of("hat"):
    ITEMS["hat_" + sh] = (sh, lambda sh=sh: hat(S(), sh, HEAD), NO_RENDER)
    if sh in OVER_HAIR:
        ITEMS["hat_" + sh + "_afro"] = (sh, (lambda: afro_headphones(S(), HEAD)) if sh == "headphones" else (lambda sh=sh: hat(S(), sh, over_afro(sh, HEAD))), NO_RENDER)
for sh in shapes_of("hair"):
    for cut, hat_shape in CUTS.items():
        ITEMS[f"hair_{sh}_{cut}"] = (sh, lambda sh=sh, hs=hat_shape: hair(S(), sh, HEAD, hs), NO_RENDER)
ITEMS["hair_curly_tiara"] = ("curly", lambda: hair(S(), "curly", HEAD, "tiara"), NO_RENDER)
for sh in shapes_of("face"):
    ITEMS["face_" + sh] = (sh, lambda sh=sh: face(S(), sh, HEAD), NO_RENDER)
for sh in shapes_of("shoes"):
    ITEMS["shoes_" + sh] = (sh, lambda sh=sh: shoes(S(), sh), NO_RENDER)
for r in ROWS:
    if r["slot"] == "back":
        ITEMS[r["id"]] = (r["name"], lambda r=r: backbling.draw(S(), r), NO_RENDER)
