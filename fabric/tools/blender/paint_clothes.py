"""
The shirt and pants pictures exactly as the game paints them (client/avatar/ClothingTextures.java), for the Locker
renders: the game's painting code is copied without its Minecraft parts, given a stand-in for the cosmetics sheet's
rows, compiled and run with the project's JDK. Writes shirt_<id>.png (288 x 96: front | +X side | back | -X side) and
pants_<id>_px.png / pants_<id>_mx.png (192 x 96, one per leg: the leg whose outer side is +X / -X).

python paint_clothes.py <out dir>
"""
import glob
import json
import os
import re
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "..", "..")
SRC = os.path.join(ROOT, "fabric", "src", "client", "java", "gg", "minebloxroyale", "client", "avatar", "ClothingTextures.java")

MAIN = '''
	record CosmeticDef(String id, String shape, int color, int color2) {
	}

	public static void main(String[] args) throws Exception {
		java.io.File out = new java.io.File(args[0]);
		for (String line : java.nio.file.Files.readAllLines(java.nio.file.Path.of(args[1]))) {
			String[] f = line.split("\\t");
			CosmeticDef c = new CosmeticDef(f[1], f[2], Integer.parseInt(f[3], 16), Integer.parseInt(f[4], 16));
			if (f[0].equals("shirt")) javax.imageio.ImageIO.write(paintShirt(c), "png", new java.io.File(out, "shirt_" + c.id() + ".png"));
			else {
				javax.imageio.ImageIO.write(paintPants(c, true), "png", new java.io.File(out, "pants_" + c.id() + "_px.png"));
				javax.imageio.ImageIO.write(paintPants(c, false), "png", new java.io.File(out, "pants_" + c.id() + "_mx.png"));
			}
		}
	}
}
'''


def java_source():
    s = open(SRC, encoding="utf-8").read()
    s = re.sub(r"^package .*?;\n", "", s, flags=re.M)
    s = re.sub(r"^import (com\.mojang|gg\.minebloxroyale|net\.minecraft)\..*?;\n", "", s, flags=re.M)
    # Without the game: no texture uploads (shirt(), pants(), upload() and their cache).
    s = re.sub(r"\n\tprivate static final Map<String, Identifier> CACHE.*?\n", "\n", s)
    for name in ("shirt", "pants", "upload"):
        s = re.sub(r"\n(\t/\*\*[^\n]*\*/\n)?\tstatic Identifier " + name + r"\(.*?\n\t}\n" if name != "upload" else r"\n\tprivate static Identifier upload\(.*?\n\t}\n", "\n", s, flags=re.S)
    s = s.replace("final class ClothingTextures", "public final class ClothingTextures")
    return s.rstrip()[:-1] + MAIN  # (the class's closing brace: main goes in)


def main():
    out = os.path.abspath(sys.argv[1])
    os.makedirs(out, exist_ok=True)
    rows = json.load(open(os.path.join(ROOT, "design", "cosmetics.json"), encoding="utf-8"))["rows"]
    jdk = sorted(glob.glob(os.path.join(ROOT, ".tools", "jdk", "jdk-25*")))[-1]
    with tempfile.TemporaryDirectory() as tmp:
        with open(os.path.join(tmp, "ClothingTextures.java"), "w", encoding="utf-8") as f:
            f.write(java_source())
        with open(os.path.join(tmp, "rows.tsv"), "w", encoding="utf-8") as f:
            for r in rows:
                if r["slot"] in ("shirt", "pants"):
                    f.write("\t".join((r["slot"], r["id"], r["shape"], r["color"].lstrip("#"), r["color2"].lstrip("#"))) + "\n")
        subprocess.run([os.path.join(jdk, "bin", "java"), "-Djava.awt.headless=true", os.path.join(tmp, "ClothingTextures.java"), out, os.path.join(tmp, "rows.tsv")],
                       check=True, cwd=tmp)
    print("painted", len(os.listdir(out)), "pictures in", out)


if __name__ == "__main__":
    main()
