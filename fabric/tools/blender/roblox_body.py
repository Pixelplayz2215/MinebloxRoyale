"""
The real Roblox R6 body for the renders (never exported, never copied anywhere): the same files the game reads from
the player's Roblox install (RobloxAssets, avatar parts sheet: avatar/meshes/*.mesh and the face picture), read like
RobloxMesh.java, each part placed and coloured as RobloxAvatarRenderer draws it, the face on the front of the head as
the game projects it. content(): the install's content folder (MINEBLOXROYALE_ROBLOX, or Roblox's own folder), or
None, and the renders keep the stand-in.
"""
import glob
import json
import os
import struct

import bpy
from mathutils import Matrix, Vector

import bend
from brlib import hexrgb, to_linear

HERE = os.path.dirname(os.path.abspath(__file__))
PARTS = json.load(open(os.path.join(HERE, "..", "..", "..", "design", "avatar_parts.json"), encoding="utf-8"))["rows"]
C = Matrix(((1, 0, 0, 0), (0, 0, -1, 0), (0, 1, 0, 0), (0, 0, 0, 1)))
REST = {}  # limb id -> (each mesh corner in joint space, its normal): what avatar.repose bends


def content():
    bases = []
    given = os.environ.get("MINEBLOXROYALE_ROBLOX", "").strip()
    if given:
        bases += [given, os.path.dirname(given)]
    local = os.environ.get("LOCALAPPDATA", "")
    for app in ("Roblox", "Bloxstrap", "Fishstrap"):
        bases += sorted(glob.glob(os.path.join(local, app, "Versions", "*")), key=os.path.getmtime, reverse=True)
    for b in bases:
        c = os.path.join(b, "content") if os.path.basename(b) != "content" else b
        if all(os.path.isfile(os.path.join(c, p["mesh"])) for p in PARTS):
            return c
    return None


def parse(data):
    """RobloxMesh.parse: (positions, normals, triangles) of the first level of detail."""
    nl = data.index(b"\n")
    version = data[:nl].decode("ascii").strip()
    if not version.startswith(("version 2.", "version 3.")):
        raise ValueError("unsupported mesh " + version)
    h = nl + 1
    header, vsize, fsize = struct.unpack_from("<HBB", data, h)
    lod_count = lod_size = 0
    if version.startswith("version 2."):
        nv, nf = struct.unpack_from("<ii", data, h + 4)
    else:
        lod_size, lod_count = struct.unpack_from("<HH", data, h + 4)
        nv, nf = struct.unpack_from("<ii", data, h + 8)
    p = h + header
    pos, nrm = [], []
    for i in range(nv):
        v = struct.unpack_from("<6f", data, p + i * vsize)
        pos.append(v[:3])
        nrm.append(v[3:6])
    faces_at = p + nv * vsize
    use = nf
    if lod_count >= 2:
        end = struct.unpack_from("<i", data, faces_at + nf * fsize + lod_size)[0]
        if 0 < end <= nf:
            use = end
    tris = [struct.unpack_from("<3i", data, faces_at + i * fsize) for i in range(use)]
    return pos, nrm, tris


def _mat(name, color, face=None):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    m.use_backface_culling = True
    nt = m.node_tree
    bsdf = nt.nodes.get("Principled BSDF")
    bsdf.inputs["Roughness"].default_value = 0.6
    lin = (*to_linear(hexrgb(color)), 1)
    if face is None:
        bsdf.inputs["Base Color"].default_value = lin
    else:  # the face picture over white, times the head colour (the game's one texture, tinted)
        tex = nt.nodes.new("ShaderNodeTexImage")
        tex.image = bpy.data.images.load(face, check_existing=True)
        tex.extension = "CLIP"
        white = nt.nodes.new("ShaderNodeMix")
        white.data_type = "RGBA"
        white.inputs["A"].default_value = (1, 1, 1, 1)
        nt.links.new(tex.outputs["Alpha"], white.inputs["Factor"])
        nt.links.new(tex.outputs["Color"], white.inputs["B"])
        tint = nt.nodes.new("ShaderNodeMix")
        tint.data_type = "RGBA"
        tint.blend_type = "MULTIPLY"
        tint.inputs["Factor"].default_value = 1.0
        tint.inputs["B"].default_value = lin
        nt.links.new(white.outputs["Result"], tint.inputs["A"])
        nt.links.new(tint.outputs["Result"], bsdf.inputs["Base Color"])
    m["br_col"], m["br_ch"], m["br_glow"] = color, 0, 0
    return m


def build(root, joints, colors):
    """The body parts, each made in its joint's space (offset from the joint as the sheet says) and put where its joint
    is (avatar.joints; moving the object moves the part: emote_video.py); colors: head, torso, arms, legs. Returns
    {part id: object}."""
    face_png = os.path.join(root, "textures", "face.png")
    objs = {}
    REST.clear()
    for part in PARTS:
        pos, nrm, tris = parse(open(os.path.join(root, part["mesh"]), "rb").read())
        off = (part["centerX"] - part["pivotX"], part["centerY"] - part["pivotY"], part["centerZ"] - part["pivotZ"])
        kd = bend.kind(part["id"])
        if kd:  # arms and legs: cut across the elbow / knee so they bend smoothly (bend.py, as the game cuts them)
            for cy in bend.cuts(kd) + [-0.45 if kd == "leg" else -0.5]:  # (and where the hip / shoulder ends: the clip check)
                pos, tris, (nrm,) = bend.split(pos, tris, cy - off[1], [nrm])
        m = C @ Matrix.Translation(off)
        nm = m.to_3x3().inverted_safe().transposed()
        slot = {"arms": "arms", "legs": "legs", "torso": "torso"}.get(part["colorSlot"], "head")
        verts, faces, normals, uvs, mats = [], [], [], [], []
        for t in tris:
            # Face decal per whole triangle: only when all three corners face forward (as the game decides it).
            front = part["faceDecal"] and all(nrm[i][2] < -0.3 for i in t)
            for i in t:
                verts.append(tuple(m @ Vector(pos[i])))
                normals.append(tuple((nm @ Vector(nrm[i])).normalized()))
                vx, vy = pos[i][0], pos[i][1]
                uvs.append((0.5 + vx / 1.2 * -1 * 0.92, 1 - (0.5 - vy / 1.2 * 0.92)) if front else (0.0, 0.0))
            faces.append(tuple(range(len(verts) - 3, len(verts))))
            mats.append(1 if front else 0)
        me = bpy.data.meshes.new(part["id"])
        me.from_pydata(verts, [], faces)
        layer = me.uv_layers.new(name="uv")
        layer.data.foreach_set("uv", [c for uv in uvs for c in uv])
        for p, k in zip(me.polygons, mats):
            p.use_smooth = True
            p.material_index = k
        me.normals_split_custom_set(normals)
        me.materials.append(_mat(part["id"], colors[slot]))
        if part["faceDecal"]:
            me.materials.append(_mat(part["id"] + "_face", colors[slot], face_png if os.path.isfile(face_png) else None))
        obj = bpy.data.objects.new(part["id"], me)
        if kd:  # (each corner in joint space, and its normal, to bend from: avatar.repose)
            REST[part["id"]] = ([tuple(Vector(pos[i]) + Vector(off)) for t in tris for i in t], [tuple(Vector(nrm[i]).normalized()) for t in tris for i in t])
        obj["br_preview"] = 1
        obj.matrix_world = C @ joints[part["id"]] @ C.inverted()
        bpy.context.scene.collection.objects.link(obj)
        objs[part["id"]] = obj
    return objs
