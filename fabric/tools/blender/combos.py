"""
Every hat with every haircut, offline (2.0.0-test6, creator: "Offline render only, go through every single hair and hat
combo, screenshot all 4 angles and check for any visual glitches"): the stand-in avatar dressed as the game dresses it
(locker.dress: the haircut cut for that hat, the afro's own hats, hats that hide the hair), a close-up of the head from
the front, left, back and right. (Hat and hair surfaces fighting each other are checked by check_meshes.py; how it all
looks is checked by eye from these.)

blender --background --factory-startup --python combos.py -- <out dir> [hat ids...]
    <out dir>/<hat>/<hair>_<View>.png, and <out dir>/<hat>/models.json: the hat's and haircut's models for each.
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import brlib  # noqa: E402
import locker  # noqa: E402

args = sys.argv[sys.argv.index("--") + 1:]
OUT, ONLY = args[0], set(args[1:])
HATS = [r for r in locker.ROWS if r["slot"] == "hat" and (not ONLY or r["id"] in ONLY)]
HAIRS = [r for r in locker.ROWS if r["slot"] == "hair"]
VIEWS = {"Front": 180, "Left": 270, "Back": 0, "Right": 90}
SIZE = int(os.environ.get("BR_RES", "400"))

for hat in HATS:
    d = os.path.join(OUT, hat["id"])
    os.makedirs(d, exist_ok=True)
    models = {}
    for hair in HAIRS:
        brlib.reset()
        brlib._mats.clear()
        objs, js = locker.dress({"hat": hat, "hair": hair})
        models[hair["id"]] = {"hat": next((o.name for o in objs if o.name.startswith("hat_")), None),
                              "hair": next((o.name for o in objs if o.name.startswith("hair_")), None)}
        brlib.render_views(d, hair["id"], VIEWS, size=SIZE, focus=locker.frame(objs, js["head"], locker.HEAD, pad=0.12))
        print(f"COMBO {hat['id']} + {hair['id']}: {models[hair['id']]}", flush=True)
    json.dump(models, open(os.path.join(d, "models.json"), "w", encoding="utf-8"), indent=1)
    print("HAT", hat["id"], flush=True)
