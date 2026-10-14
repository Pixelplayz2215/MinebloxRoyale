"""Checks the mod's resources without the game: every JSON parses; blockstates, item definitions and models point at
models and textures that exist (the mod's own or vanilla's, from the Minecraft jar); model elements are in range and
their faces' UVs and texture variables are valid; no two faces of a block model lie on the same plane over each other
(z-fighting); every registered block and item has a blockstate / item definition / translation; translation keys used
in the code exist; sounds point at real files; data overrides use feature types vanilla knows; mixin configs list
exactly the mixin classes there are.

  python check_resources.py <fabric dir> <minecraft client jar (~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar)>
"""
import io
import itertools
import json
import os
import re
import sys
import zipfile

from PIL import Image

FAB, JAR = sys.argv[1], sys.argv[2]
RES = os.path.join(FAB, "src", "main", "resources")
CRES = os.path.join(FAB, "src", "client", "resources")
NS = "minebloxroyale"
A = os.path.join(RES, "assets", NS)
jar = zipfile.ZipFile(JAR)
vanilla = set(jar.namelist())
problems, notes = [], []


def bad(msg):
    problems.append(msg)


def load(path):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except Exception as e:
        bad(f"JSON error in {os.path.relpath(path, FAB)}: {e}")
        return None


def split(ref, default_ns="minecraft"):
    ns, _, p = ref.partition(":") if ":" in ref else (default_ns, "", ref)
    return ns, p


def asset_exists(ref, kind, ext):
    ns, p = split(ref)
    if ns == NS:
        return os.path.isfile(os.path.join(A, kind, p + ext))
    return f"assets/{ns}/{kind}/{p}{ext}" in vanilla


# ---- every JSON parses ----
jsons = {}
for root in (RES, CRES):
    for d, _, fs in os.walk(root):
        for f in fs:
            if f.endswith((".json", ".mcmeta")):
                p = os.path.join(d, f)
                jsons[p] = load(p)
print("JSON files parsed:", len(jsons))

# ---- models ----
model_cache = {}


def model_json(ref):
    ns, p = split(ref)
    if ref in model_cache:
        return model_cache[ref]
    m = None
    if ns == NS:
        path = os.path.join(A, "models", p + ".json")
        m = jsons.get(path) if path in jsons else (load(path) if os.path.isfile(path) else None)
    elif f"assets/{ns}/models/{p}.json" in vanilla:
        m = json.loads(jar.read(f"assets/{ns}/models/{p}.json"))
    model_cache[ref] = m
    return m


FACE_NAMES = {"down", "up", "north", "south", "east", "west"}
used_models, used_textures = set(), set()


checked = set()


def check_model(ref, where):
    used_models.add(split(ref)[0] + ":" + split(ref)[1])
    if ref in checked:
        return
    checked.add(ref)
    m = model_json(ref)
    if m is None:
        bad(f"{where}: model {ref} doesn't exist")
        return
    # Resolve the parent chain: textures and elements.
    chain, cur, seen = [], m, set()
    while cur is not None:
        chain.append(cur)
        par = cur.get("parent")
        if not par or par.startswith("builtin/"):
            break
        if par in seen:
            bad(f"{ref}: parent loop")
            break
        seen.add(par)
        used_models.add(split(par)[0] + ":" + split(par)[1])
        pm = model_json(par)
        if pm is None:
            bad(f"{ref}: parent {par} doesn't exist")
            break
        cur = pm
    tex = {}
    for c in reversed(chain):
        tex.update(c.get("textures", {}))

    def resolve(v, depth=0):
        while isinstance(v, str) and v.startswith("#") and depth < 10:
            v = tex.get(v[1:])
            depth += 1
        return v
    if split(ref)[0] == NS:
        for k, v in m.get("textures", {}).items():
            r = resolve(v)
            if r is None:
                bad(f"{ref}: texture variable '{k}' -> {v} never resolves")
            elif not asset_exists(r, "textures", ".png"):
                bad(f"{ref}: texture {r} doesn't exist")
            else:
                used_textures.add(r if ":" in r else "minecraft:" + r)
    elements = next((c["elements"] for c in chain if "elements" in c), None)
    if split(ref)[0] != NS or not elements:
        return
    for i, e in enumerate(elements):
        f, t = e.get("from"), e.get("to")
        if not (isinstance(f, list) and isinstance(t, list) and len(f) == 3 and len(t) == 3):
            bad(f"{ref} element {i}: bad from/to")
            continue
        for a in range(3):
            if not (-16 <= f[a] <= 32 and -16 <= t[a] <= 32):
                bad(f"{ref} element {i}: outside -16..32 ({f} {t})")
            if f[a] > t[a]:
                bad(f"{ref} element {i}: from > to on axis {a}")
        r = e.get("rotation")
        if r:
            if r.get("axis") not in ("x", "y", "z") or not isinstance(r.get("angle"), (int, float)) or len(r.get("origin", [])) != 3:
                bad(f"{ref} element {i}: bad rotation {r}")
        for fn, face in e.get("faces", {}).items():
            if fn not in FACE_NAMES:
                bad(f"{ref} element {i}: unknown face {fn}")
            tx = face.get("texture")
            if not tx or resolve(tx) is None:
                bad(f"{ref} element {i} face {fn}: texture {tx} not defined")
            uv = face.get("uv")
            if uv is not None and (len(uv) != 4 or any(not (0 <= u <= 16) for u in uv)):
                bad(f"{ref} element {i} face {fn}: uv {uv} outside 0..16")
            if face.get("cullface") and face["cullface"] not in FACE_NAMES:
                bad(f"{ref} element {i} face {fn}: bad cullface")
    zfight(ref, elements, resolve)


# Each face as a rectangle in the model's space: axis, plane position, the 2D box it covers, the way it faces.
FACE_GEOM = {"down": (1, 0, -1), "up": (1, 1, 1), "north": (2, 0, -1), "south": (2, 1, 1), "west": (0, 0, -1), "east": (0, 1, 1)}


TEX_CACHE = {}


def colours(tref, uv):
    """The distinct colours a face shows: the pixels of its UV box in the texture."""
    if tref is None or not uv:
        return None
    ns, p = split(tref)
    path = os.path.join(A, "textures", p + ".png") if ns == NS else None
    if tref not in TEX_CACHE:
        TEX_CACHE[tref] = Image.open(path).convert("RGBA") if path and os.path.isfile(path) else None
    im = TEX_CACHE[tref]
    if im is None:
        return None
    w, h = im.size
    u0, v0, u1, v1 = min(uv[0], uv[2]), min(uv[1], uv[3]), max(uv[0], uv[2]), max(uv[1], uv[3])
    # The pixels whose centres the UV box covers (the game samples nearest-pixel); at least the one at its middle.
    xs = [x for x in range(w) if u0 * w / 16 <= x + 0.5 <= u1 * w / 16] or [min(w - 1, int((u0 + u1) / 2 * w / 16))]
    ys = [y for y in range(h) if v0 * h / 16 <= y + 0.5 <= v1 * h / 16] or [min(h - 1, int((v0 + v1) / 2 * h / 16))]
    return frozenset(im.getpixel((x, y)) for x in xs for y in ys)


def zfight(ref, elements, resolve):
    import numpy as np

    def rot(axis, ang):
        c, s = np.cos(np.radians(ang)), np.sin(np.radians(ang))
        return {"x": np.array([[1, 0, 0], [0, c, -s], [0, s, c]]), "y": np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]]),
                "z": np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])}[axis]
    faces = []
    for i, e in enumerate(elements):
        f, t = np.array(e["from"], float), np.array(e["to"], float)
        R, o = np.eye(3), np.zeros(3)
        if e.get("rotation"):
            R, o = rot(e["rotation"]["axis"], e["rotation"]["angle"]), np.array(e["rotation"]["origin"], float)
        for fn in e.get("faces", {}):
            ax, side, sign = FACE_GEOM[fn]
            others = [a for a in range(3) if a != ax]
            pos = (f if side == 0 else t)[ax]
            corners = []
            for u, v in itertools.product((0, 1), (0, 1)):
                p = np.zeros(3)
                p[ax] = pos
                p[others[0]] = (f, t)[u][others[0]]
                p[others[1]] = (f, t)[v][others[1]]
                corners.append((p - o) @ R.T + o)
            n = np.zeros(3)
            n[ax] = sign
            n = R @ n
            area = (t[others[0]] - f[others[0]]) * (t[others[1]] - f[others[1]])
            if area <= 0:
                continue
            fd = e["faces"][fn]
            faces.append((i, fn, np.array(corners), n, colours(resolve(fd.get("texture")), fd.get("uv"))))
    for (i, fa, ca, na, ta), (j, fb, cb, nb, tb) in itertools.combinations(faces, 2):
        if i == j or abs(na @ nb) < 0.9999:
            continue
        if na @ nb < 0:
            continue  # facing opposite ways: one of them is always culled
        d = abs((cb[0] - ca[0]) @ na)
        if d > 0.02:  # (in 1/16 units: more than 0.02 px apart is fine)
            continue
        # Overlap in the plane: project both onto two in-plane axes and compare boxes.
        u = ca[1] - ca[0] if np.linalg.norm(ca[1] - ca[0]) > 1e-9 else ca[2] - ca[0]
        u = u / np.linalg.norm(u)
        v = np.cross(na, u)
        pa, pb = ca @ np.array([u, v]).T, cb @ np.array([u, v]).T
        ox = min(pa[:, 0].max(), pb[:, 0].max()) - max(pa[:, 0].min(), pb[:, 0].min())
        oy = min(pa[:, 1].max(), pb[:, 1].max()) - max(pa[:, 1].min(), pb[:, 1].min())
        if ox > 0.01 and oy > 0.01:
            same = ta is not None and ta == tb and len(ta) == 1
            what = "both one identical colour, so the flicker can't be seen" if same else "different colours: visible flicker"
            (notes.append if same else bad)(f"{ref}: element {i} {fa} face and element {j} {fb} face on the same plane over each other, {ox:.2f} x {oy:.2f} px ({what})")


# ---- blockstates ----
blocks = re.findall(r'(?:chest|car)\("([a-z_]+)"', open(os.path.join(FAB, "src/main/java/gg/minebloxroyale/ModBlocks.java"), encoding="utf-8").read())
print("blocks registered:", blocks)
for b in blocks:
    p = os.path.join(A, "blockstates", b + ".json")
    if not os.path.isfile(p):
        bad(f"block {b}: no blockstate file")
for f in os.listdir(os.path.join(A, "blockstates")):
    bs = jsons[os.path.join(A, "blockstates", f)]
    if f[:-5] not in blocks:
        bad(f"blockstates/{f}: no block of that name")
    for key, v in (bs or {}).get("variants", {}).items():
        for vv in (v if isinstance(v, list) else [v]):
            check_model(vv["model"], f"blockstates/{f} [{key}]")
            for r in ("x", "y"):
                if r in vv and vv[r] % 90:
                    bad(f"blockstates/{f} [{key}]: {r} rotation {vv[r]} is not a multiple of 90")
props = {"loot_chest": {"facing": {"north", "south", "east", "west"}, "open": {"true", "false"}},
         "rare_chest": {"facing": {"north", "south", "east", "west"}, "open": {"true", "false"}},
         "car_part": {"low": {"true", "false"}}, "car_core": {"low": {"true", "false"}}}
for b, pv in props.items():
    bs = jsons.get(os.path.join(A, "blockstates", b + ".json")) or {}
    want = {",".join(f"{k}={x}" for k, x in zip(pv, combo)) for combo in itertools.product(*[sorted(s) for s in pv.values()])}
    have = set()
    for key in bs.get("variants", {}):
        have.add(",".join(sorted(key.split(","), key=lambda kv: list(pv).index(kv.split("=")[0]))))
    if want - have:
        bad(f"blockstates/{b}.json: missing states {sorted(want - have)}")
    if have - want:
        bad(f"blockstates/{b}.json: states the block doesn't have {sorted(have - want)}")

# ---- item definitions ----
ITEM_TYPES = {"minecraft:model", "minecraft:select", "minecraft:condition", "minecraft:range_dispatch", "minecraft:composite", "minecraft:special", "minecraft:empty",
              "minecraft:bundle/selected_item"}


def walk_item(node, where):
    if not isinstance(node, dict):
        return
    t = node.get("type")
    if t and t not in ITEM_TYPES:
        bad(f"{where}: unknown item model type {t}")
    if t == "minecraft:model":
        check_model(node["model"], where)
    if t == "minecraft:special":
        # A special renderer draws it (ours: a Blender mesh); the base model gives the hold in each hand.
        check_model(node["base"], where)
        spec = node.get("model", {})
        if spec.get("type") == f"{NS}:mesh" and not os.path.exists(os.path.join(A, "meshes", spec.get("mesh", "") + ".brm")):
            bad(f"{where}: no mesh meshes/{spec.get('mesh')}.brm")
        return
    for k in ("fallback", "on_true", "on_false", "model"):
        if isinstance(node.get(k), dict):
            walk_item(node[k], where)
    for c in node.get("cases", []) + node.get("entries", []) + node.get("models", []):
        walk_item(c.get("model", c) if isinstance(c, dict) else c, where)


item_defs = sorted(f[:-5] for f in os.listdir(os.path.join(A, "items")))
for f in item_defs:
    d = jsons[os.path.join(A, "items", f + ".json")]
    walk_item((d or {}).get("model"), f"items/{f}.json")
# Minecraft's own items the mod draws its way (the harvesting pickaxe is an iron pickaxe).
mc_items = os.path.join(FAB, "src/main/resources/assets/minecraft/items")
for f in sorted(os.listdir(mc_items)) if os.path.isdir(mc_items) else []:
    walk_item(load(os.path.join(mc_items, f)).get("model"), f"assets/minecraft/items/{f}")

# Items the code registers: the ids in the design sheets the registration reads, plus the reboot card and the
# creative tab's icon.
design = os.path.join(FAB, "..", "design")
reg_items = {"reboot_card", "icon"}
src_items = open(os.path.join(FAB, "src/main/java/gg/minebloxroyale/ModItems.java"), encoding="utf-8").read()
for sheet in os.listdir(design):
    if not sheet.endswith(".json"):
        continue
    try:
        rows = json.load(open(os.path.join(design, sheet), encoding="utf-8")).get("rows", [])
    except Exception as e:
        bad(f"design/{sheet}: {e}")
        continue
    stem = sheet[:-5]
    prefix = {"weapons": "gun_", "consumables": "", "ammo": "ammo_", "materials": "mat_"}.get(stem)
    if prefix is not None:
        for r in rows:
            reg_items.add(prefix + r["id"])
print("items the sheets register:", len(reg_items))
for it in sorted(reg_items):
    if it not in item_defs:
        bad(f"item {it}: no items/{it}.json (would show as the purple-black missing model)")
for it in item_defs:
    if it not in reg_items:
        notes.append(f"items/{it}.json: no item registered by that id (unused file)")

# ---- models and textures nobody uses ----
for kind in ("block", "item"):
    for f in os.listdir(os.path.join(A, "models", kind)):
        ref = f"{NS}:{kind}/{f[:-5]}"
        if ref not in used_models:
            notes.append(f"models/{kind}/{f}: not used by any blockstate or item")
for kind in ("block", "item"):
    for f in os.listdir(os.path.join(A, "textures", kind)):
        if f.endswith(".png"):
            ref = f"{NS}:{kind}/{f[:-4]}"
            path = os.path.join(A, "textures", kind, f)
            try:
                im = Image.open(path)
                im.verify()
                im = Image.open(path)
                w, h = im.size
                if os.path.isfile(path + ".mcmeta"):
                    pass
                elif w != h and kind == "block":
                    bad(f"textures/{kind}/{f}: {w}x{h} is not square and has no .mcmeta")
                if w & (w - 1) or h & (h - 1):
                    notes.append(f"textures/{kind}/{f}: {w}x{h} isn't a power of two (fine, but mipmaps get worse)")
            except Exception as e:
                bad(f"textures/{kind}/{f}: not a readable PNG ({e})")
            if ref not in used_textures:
                notes.append(f"textures/{kind}/{f}: not used by any model (may be used from code)")

# ---- translations ----
lang = jsons[os.path.join(A, "lang", "en_us.json")] or {}
for b in blocks:
    if f"block.{NS}.{b}" not in lang:
        bad(f"block {b}: no name in en_us.json")
for it in reg_items:
    if f"item.{NS}.{it}" not in lang:
        bad(f"item {it}: no name in en_us.json")
srcs = {}
for root in ("src/main/java", "src/client/java"):
    for d, _, fs in os.walk(os.path.join(FAB, root)):
        for f in fs:
            if f.endswith(".java"):
                srcs[os.path.join(d, f)] = open(os.path.join(d, f), encoding="utf-8").read()
alltext = "\n".join(srcs.values())
for key in sorted(set(re.findall(r'"((?:key|item|block|entity|gui|text|message|category|key\.category)\.minebloxroyale\.[a-z0-9_.]+)"', alltext))):
    if key not in lang:
        bad(f"translation key {key} is used in the code but missing from en_us.json")
for m in re.finditer(r'Component\.translatable\("([^"]+)"', alltext):
    k = m.group(1)
    if NS in k and k not in lang:
        bad(f"translatable {k} missing from en_us.json")
for k in lang:
    if k.startswith(("key.", "item.", "block.", "entity.")) and k.split(".")[-1] not in alltext and k not in alltext:
        if not (k.startswith("item.") and k.split(".")[-1] in reg_items) and not (k.startswith("block.") and k.split(".")[-1] in blocks):
            notes.append(f"en_us.json: {k} is never used")
# Keybind names: KeyMapping("key.minebloxroyale.x", ...) and the category.
for k in set(re.findall(r'"(key\.minebloxroyale\.[a-z_]+)"', alltext)):
    if k not in lang:
        bad(f"keybind {k}: no name in en_us.json")
ents = set(re.findall(r'id\("([a-z_]+)"\)', alltext))

# ---- sounds ----
sounds = jsons[os.path.join(A, "sounds.json")] or {}
for ev, d in sounds.items():
    for s in d.get("sounds", []):
        name = s["name"] if isinstance(s, dict) else s
        ns, p = split(name)
        if ns == NS and not os.path.isfile(os.path.join(A, "sounds", p + ".ogg")):
            bad(f"sounds.json {ev}: {name}.ogg doesn't exist")
for ev in set(re.findall(r'id\("((?:music|sfx|sound)\.[a-z_.]+)"\)', alltext)):
    if ev not in sounds:
        bad(f"sound event {ev} used in the code but not in sounds.json")

# ---- data overrides: same file exists in vanilla, same type, types vanilla knows ----
D = os.path.join(RES, "data")
vanilla_types = set()
for n in vanilla:
    if n.startswith("data/minecraft/worldgen/") and n.endswith(".json"):
        for t in re.findall(rb'"type"\s*:\s*"([a-z_:/]+)"', jar.read(n)):
            vanilla_types.add(t.decode())
for d, _, fs in os.walk(D):
    for f in fs:
        p = os.path.join(d, f)
        rel = os.path.relpath(p, RES).replace(os.sep, "/")
        mine = jsons.get(p)
        if rel not in vanilla:
            bad(f"{rel}: vanilla has no such file, so it overrides nothing")
            continue
        theirs = json.loads(jar.read(rel))
        if isinstance(mine, dict) and isinstance(theirs, dict):
            if mine.get("type") != theirs.get("type"):
                bad(f"{rel}: type {mine.get('type')} differs from vanilla's {theirs.get('type')}")
            if "feature" in mine and mine["feature"] != theirs.get("feature"):
                bad(f"{rel}: feature {mine['feature']} differs from vanilla's {theirs.get('feature')}")
            missing = [k for k in theirs if k not in mine and k not in ("placement",)]
            if missing and "tags" not in rel:
                bad(f"{rel}: missing fields vanilla has: {missing}")
        for t in re.findall(r'"type"\s*:\s*"([a-z_:/]+)"', json.dumps(mine)):
            if t not in vanilla_types:
                bad(f"{rel}: type {t} isn't used anywhere in vanilla worldgen (typo?)")
        if "tags" in rel and isinstance(mine, dict):
            for v in mine.get("values", []):
                v = v if isinstance(v, str) else v.get("id")
                if not v.startswith("#") and f"data/minecraft/worldgen/biome/{split(v)[1]}.json" not in vanilla:
                    bad(f"{rel}: biome {v} doesn't exist")

# ---- mixin configs list exactly the mixin classes ----
for cfg, base in ((os.path.join(RES, f"{NS}.mixins.json"), "src/main/java/gg/minebloxroyale/mixin"),
                  (os.path.join(CRES, f"{NS}.client.mixins.json"), "src/client/java/gg/minebloxroyale/client/mixin")):
    c = jsons[cfg] or {}
    listed = set(c.get("mixins", []) + c.get("client", []) + c.get("server", []))
    have = {f[:-5] for f in os.listdir(os.path.join(FAB, base)) if f.endswith(".java")}
    for m in listed - have:
        bad(f"{os.path.basename(cfg)}: lists {m}, which doesn't exist (crash at start)")
    for m in have - listed:
        bad(f"{os.path.basename(cfg)}: {m} exists but isn't listed (never applied)")

# ---- fabric.mod.json entrypoints ----
fmj = jsons[os.path.join(RES, "fabric.mod.json")] or {}
for side, eps in fmj.get("entrypoints", {}).items():
    for ep in eps:
        path = ep.replace(".", "/") + ".java"
        if not any(k.replace(os.sep, "/").endswith(path) for k in srcs):
            bad(f"fabric.mod.json: entrypoint {ep} not found")

print()
print(f"{len(problems)} problems")
for p in problems:
    print("  PROBLEM", p)
print(f"{len(notes)} notes")
for n in notes:
    print("  note", n)
