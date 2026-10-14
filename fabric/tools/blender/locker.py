"""
Pictures of the Locker (build.py locker <assets> <renders dir>; pictures only): every hat, haircut, face accessory,
back bling, pair of shoes, shirt and pants on the stand-in avatar, every emote posed on it and every preset outfit,
each from four sides, with what unlocks it. The cosmetics are the game's own model files (brm_import: what the game
draws, including which faces it culls) on the joints the game puts them on, in the item's colours; shirts and pants
are the game's own painted designs (paint_clothes.py) wrapped round the body as the game wraps them.
"""
import json
import os

import avatar
import brm_import
from gliders import unlock_text
from mathutils import Matrix, Vector

HERE = os.path.dirname(os.path.abspath(__file__))
DESIGN = os.path.join(HERE, "..", "..", "..", "design")
MESHES = os.environ.get("BR_MESHES", os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "minebloxroyale", "meshes"))
CLOTHES = os.environ.get("BR_CLOTHES", os.path.join(HERE, "clothes"))  # (paint_clothes.py's pictures)
ROWS = json.load(open(os.path.join(DESIGN, "cosmetics.json"), encoding="utf-8"))["rows"]
BY_ID = {r["id"]: r for r in ROWS}
VIEWS = {"Front": 180, "Left": 270, "Back": 0, "Right": 90}
# (creator: Locker Items v1.0.1 \ "New Hat", "New Pants", "New Back", "New Shoes", "New Shirt", "New Presets", "New Emotes"; hair beside them)
FOLDER = {"hat": "New Hat", "hair": "New Hair", "back": "New Back", "shoes": "New Shoes", "shirt": "New Shirt", "pants": "New Pants"}
OVER_HAIR = ("headphones", "halo", "bunny", "cat", "tiara")
CUT = {**{s: "b0" for s in ("cap", "backcap", "viking", "beanie", "army", "tricorne")}, **{s: "b2" for s in ("police", "fire_helmet")},
       **{s: "b4" for s in ("chef", "pith", "fedora", "cowboy")}, "witch": "b6"}
HEAD = (-0.6, 0.0, -0.6, 0.6, 1.2, 0.6)
TORSO, LEG = (-1, -1, -0.5, 1, 1, 0.5), (-0.5, -2, -0.5, 0.5, 0, 0.5)
C = avatar.C
CI = C.inverted()


def hexint(h):
    return int(h.lstrip("#"), 16)


def hair_mesh(hair, hat):
    """Cosmetics.hairMesh: the haircut's model for the hat worn with it."""
    if hair == "curly" and hat == "tiara":
        return "hair_curly_tiara"
    return f"hair_{hair}_{'full' if hat is None or hat in OVER_HAIR else CUT.get(hat, 'top')}"


def pieces(worn):
    """Cosmetics.submit's choice of models for an outfit {slot: row}: [(mesh id, body part, row)]."""
    hat, hair, face, back, shoes, shirt, pants = (worn.get(s) for s in ("hat", "hair", "face", "back", "shoes", "shirt", "pants"))
    if hat and hat["hideHair"] or face and face["hideHair"]:
        hair = None
    out = []
    if hair:
        out.append((hair_mesh(hair["shape"], hat["shape"] if hat else None), "head", hair))
    if hat:
        out.append(("hat_" + hat["shape"] + ("_afro" if hair and hair["shape"] == "afro" and hat["shape"] in OVER_HAIR else ""), "head", hat))
    if face:
        out.append(("face_" + face["shape"], "head", face))
    if back:
        out.append((back["id"], "torso", back))
    if shoes:
        out += [("shoes_" + shoes["shape"], "left_leg", shoes), ("shoes_" + shoes["shape"], "right_leg", shoes)]
    if shirt:  # 3D parts over the painted design
        out += [(f"shirt_{shirt['shape']}_torso", "torso", shirt), (f"shirt_{shirt['shape']}_rarm", "right_arm", shirt), (f"shirt_{shirt['shape']}_larm", "left_arm", shirt)]
    if pants:
        out += [(f"pants_{pants['shape']}_hips", "torso", pants), (f"pants_{pants['shape']}_rleg", "right_leg", pants), (f"pants_{pants['shape']}_lleg", "left_leg", pants)]
    return out


def clothes(path, joint, e, widths, name):
    """Cosmetics.wrap: the picture round a body part's four sides, just outside it (both sides shown)."""
    import bpy
    d = 0.012
    x0, y0, z0, x1, y1, z1 = e[0] - d, e[1], e[2] - d, e[3] + d, e[4], e[5] + d
    faces = [((x1, z0), (x0, z0)), ((x1, z1), (x1, z0)), ((x0, z1), (x1, z1)), ((x0, z0), (x0, z1))]
    total, u = sum(widths), 0
    verts, polys, uvs = [], [], []
    m = C @ joint
    for f in range(4):
        ua, ub = u / total, (u + widths[f]) / total
        u += widths[f]
        (lx, lz), (rx, rz) = faces[f]
        for x, y, z, uu, vv in ((rx, y1, rz, ua, 0), (rx, y0, rz, ua, 1), (lx, y0, lz, ub, 1), (lx, y1, lz, ub, 0)):
            verts.append(tuple(m @ Vector((x, y, z))))
            uvs.append((uu, 1 - vv))
        polys.append(tuple(range(len(verts) - 4, len(verts))))
    me = bpy.data.meshes.new(name)
    me.from_pydata(verts, [], polys)
    layer = me.uv_layers.new(name="uv")
    for i, uv in enumerate(uvs):
        layer.data[i].uv = uv
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    mat.use_backface_culling = False
    try:
        mat.surface_render_method = "DITHERED"
    except AttributeError:
        mat.blend_method = "HASHED"
    nt = mat.node_tree
    tex = nt.nodes.new("ShaderNodeTexImage")
    tex.image = bpy.data.images.load(path, check_existing=True)
    bsdf = nt.nodes.get("Principled BSDF")
    bsdf.inputs["Roughness"].default_value = 0.6
    nt.links.new(tex.outputs["Color"], bsdf.inputs["Base Color"])
    nt.links.new(tex.outputs["Alpha"], bsdf.inputs["Alpha"])
    mat["br_col"], mat["br_ch"], mat["br_glow"] = "#FFFFFF", 0, 0
    me.materials.append(mat)
    obj = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(obj)
    return obj


def dress(worn, emote=None):
    """The avatar wearing an outfit {slot: row}; returns the objects of what it wears and its joints."""
    shirt, pants = worn.get("shirt"), worn.get("pants")
    colors = {}
    if shirt:
        colors.update(torso=shirt["color"], arms=shirt["color"])
    if pants:
        colors["legs"] = pants["color"]
    js = avatar.build(emote=emote, colors=colors)
    objs = []
    for mid, part, row in pieces(worn):
        objs.append(brm_import.load(os.path.join(MESHES, mid + ".brm"), hexint(row["color"]), hexint(row["color2"]), js[part], mid))
    if shirt:
        objs.append(clothes(os.path.join(CLOTHES, f"shirt_{shirt['id']}.png"), js["torso"], TORSO, (96, 48, 96, 48), "shirt"))
    if pants:
        for leg in ("left_leg", "right_leg"):
            side = "_px" if leg == "right_leg" else "_mx"
            objs.append(clothes(os.path.join(CLOTHES, f"pants_{pants['id']}{side}.png"), js[leg], LEG, (48, 48, 48, 48), "pants"))
    return objs, js


def frame(objs, joint, e, pad=0.15):
    """A Minecraft box round the objects and a body part (its extents e in its joint's space), for a close-up."""
    pts = [CI @ (o.matrix_world @ Vector(c)) for o in objs for c in o.bound_box]
    pts += [joint @ Vector((x, y, z)) for x in (e[0], e[3]) for y in (e[1], e[4]) for z in (e[2], e[5])]
    lo = [min(p[k] for p in pts) - pad for k in range(3)]
    hi = [max(p[k] for p in pts) + pad for k in range(3)]
    return tuple(lo), tuple(hi)


def cosmetic(row):
    def fn():
        objs, js = dress({row["slot"]: row})
        if row["slot"] in ("hat", "hair", "face"):
            return {"frame": frame(objs, js["head"], HEAD)}
        if row["slot"] == "shoes":
            return {"frame": frame(objs, Matrix.Translation((0, 0, 0)), (-1.2, -2.0, -0.5, 1.2, -0.3, 0.5))}
        return {}
    views = {"Back": 0} if row["slot"] == "back" else VIEWS  # (creator: back bling only needs the back)
    return (row["name"], fn, {"export": False, "folder": FOLDER[row["slot"]], "unlock": unlock_text(row), "views": views})


def pose_of(emote):
    """The emote's most telling moment: its keyframe furthest from standing still (turned to face the front)."""
    keys = [k for k in json.load(open(os.path.join(DESIGN, "emote_keys.json"), encoding="utf-8"))["rows"] if k["emote"] == emote]
    angles = ("rightArmX", "rightArmZ", "leftArmX", "leftArmZ", "rightLegX", "leftLegX")
    best = max(keys, key=lambda k: sum(abs(k[a]) for a in angles) + 200 * abs(k["bounce"]))
    return {**best, "bodyYaw": 0}


def emote(row):
    return (row["name"], lambda: avatar.build(emote=pose_of(row["id"])) and None,
            {"export": False, "folder": "New Emotes", "unlock": unlock_text(row), "views": VIEWS})


SLOTS = ("hat", "hair", "face", "back", "shirt", "pants", "shoes")


def preset(row):
    worn = {s: BY_ID[row[s]] for s in SLOTS if row.get(s) and row[s] != "-" and row[s] in BY_ID}
    # A preset with locked pieces is locked until they are (the Locker shows the first one's condition).
    lock = next((unlock_text(worn[s]) for s in SLOTS if s in worn and unlock_text(worn[s])), None)
    return (row["name"], lambda: dress(worn) and None, {"export": False, "folder": "New Presets", "unlock": lock, "views": VIEWS})


ITEMS = {}
for r in ROWS:
    if r["slot"] in FOLDER:
        ITEMS[r["id"]] = cosmetic(r)
for r in json.load(open(os.path.join(DESIGN, "emotes.json"), encoding="utf-8"))["rows"]:
    ITEMS["emote_" + r["id"]] = emote(r)
for r in json.load(open(os.path.join(DESIGN, "presets.json"), encoding="utf-8"))["rows"]:
    ITEMS[r["id"]] = preset(r)
