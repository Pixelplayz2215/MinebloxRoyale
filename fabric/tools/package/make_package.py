"""Packages for Mineblox Royale (test builds in Test Builds\, releases in Releases\): one folder per launcher (creator: "make an import file for
different launchers"), each with the file that launcher imports and a HOW TO INSTALL for it, plus PICK YOUR LAUNCHER.txt:

  Minecraft Launcher (standalone mod)  mods + minebloxroyale folder, for Mojang's launcher (or any launcher by hand)
  CurseForge                           CurseForge profile .zip (My Modpacks > Import > Import Profile .zip)
  Prism Launcher                       portable Prism with the instance ready, and an instance .zip to import
  MultiMC                              the instance .zip to import
  Modrinth App, Lunar Client,          the .mrpack (Modrinth's format, which they all import); Fabric API and e4mc are
  ATLauncher, GDLauncher               downloaded from Modrinth on import (Lunar's import waited forever with nothing
                                       to download)

Every file is complete on its own. No accounts, worlds, settings or anything of the creator's go in."""
import glob, hashlib, json, os, shutil, sys, zipfile

ROOT = r"D:\ClaudeProjects\Minecraft x Roblox x Fortnite Mashup"
SP = os.path.dirname(os.path.abspath(__file__))
VER = [l.split("=", 1)[1].strip() for l in open(os.path.join(ROOT, "fabric", "gradle.properties")) if l.startswith("version=")][0]
NAME = f"MinebloxRoyale-{VER}"
OUT = os.path.join(ROOT, "Test Builds" if "-test" in VER else "Releases", VER)
HOME = os.environ["USERPROFILE"]
JAR = os.path.join(ROOT, "fabric", "build", "libs", f"minebloxroyale-{VER}.jar")
FAPI = glob.glob(os.path.join(HOME, ".gradle", "caches", "modules-2", "files-2.1", "net.fabricmc.fabric-api", "fabric-api", "0.161.0+26.3", "*", "fabric-api-0.161.0+26.3.jar"))[0]
E4MC = glob.glob(os.path.join(HOME, ".gradle", "caches", "**", "e4mc-AouleFRY.jar"), recursive=True)[0]
FF = os.path.join(ROOT, ".tools", "ffmpeg", "ffmpeg-n9.0-latest-win64-lgpl-shared-9.0")
PRISM = os.path.join(ROOT, ".tools", "prism")
# Prism's Windows build needs Microsoft's Visual C++ runtime, which not every PC has (Melty's check): its DLLs go
# beside prismlauncher.exe (Microsoft lets programs carry them).
VCRT = {n: os.path.join(os.environ["SystemRoot"], "System32", n)
        for n in ("vcruntime140.dll", "vcruntime140_1.dll", "msvcp140.dll", "msvcp140_1.dll", "msvcp140_2.dll")}
MODS ={f"minebloxroyale-{VER}.jar": JAR, "fabric-api-0.161.0+26.3.jar": FAPI, "e4mc-6.2.2.jar": E4MC}
FFMPEG = {"ffmpeg.exe": os.path.join(FF, "bin", "ffmpeg.exe"), "LICENSE.txt": os.path.join(FF, "LICENSE.txt")}
FFMPEG.update({os.path.basename(p): p for p in glob.glob(os.path.join(FF, "bin", "*.dll"))})
DOCS = {n: os.path.join(SP, "pkg", n) for n in ("HOW TO PLAY.txt", "THIRD-PARTY NOTICES.txt")}
# The modpack's picture (creator: the high-res Mineblox Royale icon, fabric/tools/branding/make_icon.py), inside every
# pack for the launchers that pick one up by themselves, and beside it for setting by hand.
ICON = os.path.join(ROOT, "Branding", "Mineblox Royale icon 1024.png")
for p in [JAR, FAPI, E4MC, PRISM, ICON, *VCRT.values(), *FFMPEG.values(), *DOCS.values()]:
    if not os.path.exists(p):
        sys.exit("missing: " + p)

INSTANCE_CFG = "[General]\nConfigVersion=1.2\nInstanceType=OneSix\nname=Mineblox Royale\niconKey=minebloxroyale\nOverrideMemory=true\nMinMemAlloc=1024\nMaxMemAlloc=4096\n"
MMC_PACK = json.dumps({"components": [
    {"important": True, "uid": "net.minecraft", "version": "26.3"},
    {"dependencyOnly": True, "uid": "net.fabricmc.intermediary", "version": "26.3"},
    {"uid": "net.fabricmc.fabric-loader", "version": "0.19.5"}], "formatVersion": 1}, indent=4)
# The bundled Fabric API and e4mc are byte for byte these Modrinth files (looked up by hash): the .mrpack lists them as
# downloads, with the local jars' hashes, so the import checks it got exactly the jars this build was tested with.
MODRINTH = {"fabric-api-0.161.0+26.3.jar": "https://cdn.modrinth.com/data/P7dR8mSH/versions/bNnaTiuM/fabric-api-0.161.0%2B26.3.jar",
            "e4mc-6.2.2.jar": "https://cdn.modrinth.com/data/qANg5Jrr/versions/AouleFRY/e4mc-fabric-6.2.2-modern.jar"}


def mrpack_file(name):
    data = open(MODS[name], "rb").read()
    return {"path": f"mods/{name}", "hashes": {"sha1": hashlib.sha1(data).hexdigest(), "sha512": hashlib.sha512(data).hexdigest()},
            "env": {"client": "required", "server": "required"}, "downloads": [MODRINTH[name]], "fileSize": len(data)}


# CurseForge's profile format: a manifest and everything as overrides (its import wants project / file ids for
# downloads, and none of these are needed from its site).
CF_MANIFEST = json.dumps({"minecraft": {"version": "26.3", "modLoaders": [{"id": "fabric-0.19.5", "primary": True}]},
                          "manifestType": "minecraftModpack", "manifestVersion": 1, "name": "Mineblox Royale", "version": VER,
                          "author": "Mineblox Royale creator", "files": [], "overrides": "overrides"}, indent=2)
MRPACK_INDEX = json.dumps({"formatVersion": 1, "game": "minecraft", "versionId": VER, "name": f"Mineblox Royale ({VER})",
                           "summary": "A Minecraft battle royale with a Roblox-style hub and Fortnite's rules." if "-test" not in VER else "Private test build - not published.", "files": [mrpack_file(n) for n in MODRINTH],
                           "dependencies": {"minecraft": "26.3", "fabric-loader": "0.19.5"}}, indent=2)
NEVER = {"accounts.json", "prismlauncher.cfg"}  # a login or this PC's launcher settings must never be shipped

ONLINE = "Be online while it imports: it downloads Fabric API and e4mc from Modrinth."
PICTURE = "For the profile's picture, choose \"Mineblox Royale icon.png\" (in this folder) in the profile's settings, if the launcher has one."
# Each launcher: its folder, the file it imports (a kind made below) with the name it gets, and how to install it.
LAUNCHERS = [
    ("Minecraft Launcher (standalone mod)", "mods", f"{NAME} (Minecraft Launcher).zip", True, [
        "Mojang's own Minecraft Launcher (also the way for any launcher not listed: put the mods in a Fabric 26.3 profile).",
        "1. Install Fabric: https://fabricmc.net/use/installer/ > Client > Minecraft 26.3, Loader 0.19.5 or newer > Install.",
        "2. In the Minecraft Launcher: Installations > the new \"fabric-loader-26.3\" > Edit > Game directory: pick an empty",
        "   folder (for example Documents\\Mineblox Royale), so these mods don't mix with your normal Minecraft. Save.",
        "3. Unzip the .zip in this folder and copy its \"mods\" and \"minebloxroyale\" folders into that game directory.",
        "4. Play that installation."]),
    ("CurseForge", "curseforge", f"{NAME} (CurseForge).zip", True, [
        "1. CurseForge App > Minecraft > My Modpacks > Import > \"Import Profile .zip\" > choose the .zip in this folder.",
        "2. Play.",
        PICTURE]),
    ("Prism Launcher", "prism", f"{NAME} (Prism Launcher, ready to play).zip", False, [
        "Don't have Prism yet? The \"ready to play\" .zip has Prism itself with the game set up:",
        "1. Unzip it somewhere you like (for example Documents\\Mineblox Royale) and run Prism Launcher\\prismlauncher.exe",
        "   (Windows may warn about an unknown app the first time: \"More info\" > \"Run anyway\").",
        "2. Keep Prism's defaults the first time; if it offers to download Java, say yes (Minecraft 26.3 needs Java 25).",
        "3. Add your account: Accounts > Manage Accounts > Add Microsoft, and sign in in the browser.",
        "4. Double-click \"Mineblox Royale\". The first start downloads Minecraft and Fabric (a few minutes).",
        "   After you sign in, that folder holds your login (Prism Launcher\\accounts.json): don't send it to anyone.",
        "",
        "Already have Prism? Import the \"import\" .zip instead: Add Instance > Import > choose it > OK (its icon comes too)."]),
    ("MultiMC", "instance", f"{NAME} (MultiMC import).zip", False, [
        "1. MultiMC > Add Instance > Import from zip > choose the .zip in this folder > OK.",
        "2. Add your Microsoft account if you haven't (Accounts), then double-click \"Mineblox Royale\".",
        "Not tried in MultiMC yet; if it can't find Minecraft 26.3, use Prism Launcher (MultiMC's newer cousin)."]),
    ("Modrinth App", "mrpack", f"{NAME} (Modrinth App).mrpack", True, [
        "1. Modrinth App > \"+\" (Create new instance) > \"From file\" > choose the .mrpack in this folder.",
        "2. Sign in with your Microsoft account and Play.",
        ONLINE]),
    ("Lunar Client", "mrpack", f"{NAME} (Lunar Client).mrpack", True, [
        "1. Lunar Client launcher: import the .mrpack in this folder as a modpack profile (it's a Fabric 26.3 pack).",
        "2. Launch the Mineblox Royale profile. The game should open straight into the Mineblox Royale world.",
        ONLINE,
        PICTURE]),
    ("ATLauncher", "mrpack", f"{NAME} (ATLauncher).mrpack", True, [
        "1. ATLauncher > Import (left) > choose the .mrpack in this folder > Install.",
        "2. Play the new Mineblox Royale instance.",
        ONLINE,
        "Not tried in ATLauncher yet."]),
    ("GDLauncher", "mrpack", f"{NAME} (GDLauncher).mrpack", True, [
        "1. GDLauncher > Add instance > Import > choose the .mrpack in this folder.",
        "2. Play the new Mineblox Royale instance.",
        ONLINE,
        "Not tried in GDLauncher yet."]),
]


def write_zip(path, entries):
    """entries: {archive path (forward slashes): source file path or bytes}."""
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for arc, src in entries.items():
            if os.path.basename(arc) in NEVER:
                sys.exit("refusing to package " + arc)
            if isinstance(src, (bytes, str)) and not (isinstance(src, str) and os.path.isfile(src)):
                z.writestr(arc, src)
            else:
                z.write(src, arc)


def install_text(folder, lines):
    return f"MINEBLOX ROYALE {VER} - {folder}\n" + "=" * 60 + "\n" + "\n".join(lines) + "\n\nThen see HOW TO PLAY.txt (inside the file) for controls, rooms and parties.\n"


# Each kind of file, made once in a work folder and copied into every launcher's folder that uses it.
WORK = os.path.join(OUT, ".work")
if os.path.isdir(OUT):
    shutil.rmtree(OUT)
os.makedirs(WORK)
docs = {n: p for n, p in DOCS.items()}

# Mods + the minebloxroyale folder, for a launcher's Fabric 26.3 profile by hand.
mo = {f"{NAME}/mods/{n}": p for n, p in MODS.items()}
mo.update({f"{NAME}/minebloxroyale/ffmpeg/{n}": p for n, p in FFMPEG.items()})
mo.update({f"{NAME}/{n}": p for n, p in docs.items()})
mo[f"{NAME}/Mineblox Royale icon.png"] = ICON
write_zip(os.path.join(WORK, "mods"), mo)

# CurseForge profile.
cf = {"manifest.json": CF_MANIFEST, "icon.png": ICON}
cf.update({f"overrides/mods/{n}": p for n, p in MODS.items()})
cf.update({f"overrides/minebloxroyale/ffmpeg/{n}": p for n, p in FFMPEG.items()})
cf.update({f"overrides/{n}": p for n, p in docs.items()})
write_zip(os.path.join(WORK, "curseforge"), cf)

# Prism / MultiMC instance to import: the instance's files at the top, its icon named after its iconKey (imported with
# it).
inst = {"instance.cfg": INSTANCE_CFG, "mmc-pack.json": MMC_PACK, "minebloxroyale.png": ICON, "icon.png": ICON}
inst.update({f"minecraft/mods/{n}": p for n, p in MODS.items()})
inst.update({f"minecraft/minebloxroyale/ffmpeg/{n}": p for n, p in FFMPEG.items()})
inst.update({f"minecraft/{n}": p for n, p in docs.items()})
write_zip(os.path.join(WORK, "instance"), inst)

# Portable Prism Launcher with the instance ready.
prism = {}
for d, _, fs in os.walk(PRISM):
    for f in fs:
        rel = os.path.relpath(os.path.join(d, f), PRISM).replace("\\", "/")
        if rel.split("/")[0] in ("instances", "logs", "accounts.json", "prismlauncher.cfg", "cache", "meta", "libraries", "assets"):
            continue
        prism[f"{NAME}/Prism Launcher/{rel}"] = os.path.join(d, f)
prism.update({f"{NAME}/Prism Launcher/{n}": p for n, p in VCRT.items()})
prism[f"{NAME}/Prism Launcher/icons/minebloxroyale.png"] = ICON  # the instance's icon (iconKey)
pi = f"{NAME}/Prism Launcher/instances/Mineblox Royale"
prism[f"{pi}/instance.cfg"] = INSTANCE_CFG
prism[f"{pi}/mmc-pack.json"] = MMC_PACK
prism.update({f"{pi}/minecraft/mods/{n}": p for n, p in MODS.items()})
prism.update({f"{pi}/minecraft/minebloxroyale/ffmpeg/{n}": p for n, p in FFMPEG.items()})
prism.update({f"{NAME}/{n}": p for n, p in docs.items()})
write_zip(os.path.join(WORK, "prism"), prism)

# Modrinth's .mrpack: Fabric API and e4mc downloaded from Modrinth, the rest as overrides.
mr = {"modrinth.index.json": MRPACK_INDEX, "icon.png": ICON}
mr.update({f"overrides/mods/{n}": p for n, p in MODS.items() if n not in MODRINTH})
mr.update({f"overrides/minebloxroyale/ffmpeg/{n}": p for n, p in FFMPEG.items()})
mr.update({f"overrides/{n}": p for n, p in docs.items()})
write_zip(os.path.join(WORK, "mrpack"), mr)

pick = [f"MINEBLOX ROYALE {VER} - pick your launcher", "=" * 60,
        "Open the folder for the launcher you use; it has the file to import and HOW TO INSTALL.txt.", ""]
for folder, kind, filename, picture, lines in LAUNCHERS:
    d = os.path.join(OUT, folder)
    os.makedirs(d)
    shutil.copyfile(os.path.join(WORK, kind), os.path.join(d, filename))
    if kind == "prism":  # Prism also gets the instance .zip, for people who have Prism already
        shutil.copyfile(os.path.join(WORK, "instance"), os.path.join(d, f"{NAME} (Prism Launcher import).zip"))
    if picture:
        shutil.copyfile(ICON, os.path.join(d, "Mineblox Royale icon.png"))
    open(os.path.join(d, "HOW TO INSTALL.txt"), "w", encoding="utf-8", newline="\r\n").write(install_text(folder, lines))
    pick.append(f"  {folder:<36} {filename}")
pick += ["", "Another launcher? Use \"Minecraft Launcher (standalone mod)\": put its mods and minebloxroyale folders in a",
         "Fabric 26.3 profile. Everyone who plays together must use the same version."]
open(os.path.join(OUT, "PICK YOUR LAUNCHER.txt"), "w", encoding="utf-8", newline="\r\n").write("\n".join(pick) + "\n")
shutil.rmtree(WORK)

for d, _, fs in sorted(os.walk(OUT)):
    for f in sorted(fs):
        p = os.path.join(d, f)
        if not f.endswith((".zip", ".mrpack")):
            continue
        with zipfile.ZipFile(p) as z:
            names = z.namelist()
            bad = [n for n in names if os.path.basename(n) in NEVER or "\\" in n]
        sha = hashlib.sha256(open(p, "rb").read()).hexdigest()[:16]
        print(f"{os.path.relpath(p, OUT)}: {os.path.getsize(p) / 1e6:.1f} MB, {len(names)} files, problems: {bad or 'none'}, sha256 {sha}")
