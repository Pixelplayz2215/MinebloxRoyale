"""
The game's own model files in Blender, for renders that show exactly what the game draws: the .brm's triangles, normals
and colours (with their baked shadows), the tint channels coloured as the game colours them (the colour times the
tint), glowing corners lit. Faces seen from behind are left out: that changes nothing for a sound model (each side of
a sheet has its own copy) and shows any part made inside out.
"""
import bpy
from mathutils import Matrix, Vector

import brm
from brlib import to_linear

C = Matrix(((1, 0, 0, 0), (0, 0, -1, 0), (0, 1, 0, 0), (0, 0, 0, 1)))


def _material():
    m = bpy.data.materials.get("brm_game")
    if m:
        return m
    m = bpy.data.materials.new("brm_game")
    m.use_nodes = True
    m.use_backface_culling = True
    nt = m.node_tree
    bsdf = nt.nodes.get("Principled BSDF")
    bsdf.inputs["Roughness"].default_value = 0.55
    col = nt.nodes.new("ShaderNodeAttribute")
    col.attribute_name = "col"
    glow = nt.nodes.new("ShaderNodeAttribute")
    glow.attribute_name = "glow"
    strength = nt.nodes.new("ShaderNodeMath")
    strength.operation = "MULTIPLY"
    strength.inputs[1].default_value = 2.0
    nt.links.new(col.outputs["Color"], bsdf.inputs["Base Color"])
    nt.links.new(col.outputs["Color"], bsdf.inputs["Emission Color"])
    nt.links.new(glow.outputs["Fac"], strength.inputs[0])
    nt.links.new(strength.outputs["Value"], bsdf.inputs["Emission Strength"])
    m["br_col"], m["br_ch"], m["br_glow"] = "#FFFFFF", 0, 0
    return m


def _mul(c, tint):
    return tuple(a * ((tint >> s) & 255) // 255 for a, s in zip(c, (16, 8, 0)))


def load(path, tint_a=0xFFFFFF, tint_b=0xFFFFFF, joint=None, name="brm"):
    """Adds the model, its corners moved by joint (a Minecraft-space matrix) if given. Returns the object."""
    corners = brm.read(path)
    j = joint or Matrix.Identity(4)
    m = C @ j
    nm = m.to_3x3().inverted_safe().transposed()
    verts = [tuple(m @ Vector(c[:3])) for c in corners]
    faces = [(i, i + 1, i + 2) for i in range(0, len(corners), 3)]
    me = bpy.data.meshes.new(name)
    me.from_pydata(verts, [], faces)
    me.update()
    cols, glows, normals = [], [], []
    for c in corners:
        rgb, ch = c[6:9], c[9] & 3
        if ch == 1:
            rgb = _mul(rgb, tint_a)
        elif ch == 2:
            rgb = _mul(rgb, tint_b)
        cols += [*to_linear(tuple(v / 255 for v in rgb)), 1.0]
        glows.append(1.0 if c[9] & 4 else 0.0)
        normals.append(tuple((nm @ Vector((c[3] / 127, c[4] / 127, c[5] / 127))).normalized()))
    # (from_pydata keeps the corners in order: loop i is corner i)
    me.color_attributes.new("col", "FLOAT_COLOR", "CORNER").data.foreach_set("color", cols)
    me.attributes.new("glow", "FLOAT", "CORNER").data.foreach_set("value", glows)
    for p in me.polygons:
        p.use_smooth = True
    me.normals_split_custom_set(normals)
    me.materials.append(_material())
    obj = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(obj)
    return obj
