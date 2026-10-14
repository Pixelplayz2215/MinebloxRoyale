"""
Builds the Blender models: blender --background --factory-startup --python build.py -- <set> <assets dir> <renders dir> [ids...]
For each model: the game's mesh file (assets/minebloxroyale/meshes/<id>.brm), its four side views (and, for items, the
inventory picture and its rarity mask) as plain renders in the renders dir, and manifest.json listing them for
compose.py, which labels them.
"""
import importlib
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import brlib  # noqa: E402

args = sys.argv[sys.argv.index("--") + 1:]
SET, ASSETS, RAW = args[0], args[1], args[2]
ONLY = set(args[3:])
RENDER = not os.environ.get("BR_MODELS_ONLY")  # (BR_MODELS_ONLY=1: the game's models again, no pictures)
module = importlib.import_module(SET)
os.makedirs(RAW, exist_ok=True)
manifest = []
VIEWS = {"Front": 90, "Right": 0, "Back": 270, "Left": 180}  # guns: the front is the muzzle end (+x); its own right side faces +z
for mid, spec in module.ITEMS.items():
    if ONLY and mid not in ONLY:
        continue
    name, fn, opts = spec[0], spec[1], spec[2] if len(spec) > 2 else {}
    brlib.reset()
    brlib._mats.clear()
    made = fn() or {}  # (a model may say what to frame in its pictures: {"frame": Minecraft box})
    scale = opts.get("scale", 1 / 16)
    origin = opts.get("origin", (0, 0, 0))
    n, lo, hi, fixes = 0, None, None, {}
    if opts.get("export", True):  # (export False: pictures only, of models the game already has)
        n, lo, hi = brlib.export(os.path.join(ASSETS, "meshes", mid + ".brm"), scale=scale, origin=origin, ao_distance=opts.get("ao", 1.5))
        fixes = dict(brlib.STATS)
    if opts.get("avatar"):  # a stand-in avatar in the renders, for size (not exported)
        import avatar
        avatar.build(opts["avatar"])
    entry = {"id": mid, "name": name, "tris": n, "bounds": [lo, hi], "variants": [], "folder": opts.get("folder", ""),
             "variant_first": opts.get("variant_first", False), "unlock": opts.get("unlock")}
    for vname, tints in (opts.get("variants", {"": {}}) if RENDER and opts.get("render", True) else {}).items():
        brlib.tint_previews(tints)
        stem = mid + ("_" + vname if vname else "")
        entry["variants"].append({"variant": vname, "views": brlib.render_views(RAW, stem, opts.get("views", VIEWS), focus=made.get("frame"))})
        if "focus" in opts:  # a close-up of part of it (the bus under its balloon)
            label, box = opts["focus"]
            entry["variants"].append({"variant": label, "views": brlib.render_views(RAW, stem + "_focus", opts.get("views", VIEWS), focus=box)})
    if "icon" in opts and RENDER:
        yaw, pitch = opts["icon"]
        brlib.tint_previews({1: "#E1E1E6"})
        entry["icon"] = os.path.join(RAW, mid + "_icon.png")
        brlib.render_icon(entry["icon"], yaw, pitch)
        if opts.get("tinted"):
            entry["icon_mask"] = os.path.join(RAW, mid + "_iconmask.png")
            brlib.render_icon(entry["icon_mask"], yaw, pitch, mask=True)
    if "lod" in opts:  # a lighter copy for far away (the renderer picks it by distance)
        brlib.decimated(opts["lod"])
        entry["far_tris"] = brlib.export(os.path.join(ASSETS, "meshes", mid + "_far.brm"), scale=scale, origin=origin, ao_distance=opts.get("ao", 1.5))[0]
    print(f"BUILT {mid}: " + (f"{n} triangles" if n else "pictures") + (f" ({entry['far_tris']} far)" if "far_tris" in entry else "")
          + (f"; {fixes['turned']} parts turned right way out" if fixes.get("turned") else "")
          + (f"; {len(fixes['open'])} open sheets given a back" if fixes.get("open") else ""), flush=True)
    manifest.append(entry)
brlib.write_manifest(os.path.join(RAW, f"manifest_{SET}.json"), manifest)
