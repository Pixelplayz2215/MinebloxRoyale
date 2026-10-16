"""
Emote props (2.0.0-test5, creator, of Drum Solo: "Just looks like moving Arms (Maybe add a Drum and Drumsticks to the
Emote)"): things an emote brings out while it plays, made like the cosmetics in studs, in the space they're drawn in.
The emotes sheet's props column says which and where (RobloxAvatarRenderer.submitProps, emote_video.py):

  @ground      the avatar's own space, its feet on y = -2, facing -z (it stays put while the body bounces)
  @body        the upper body's space (the torso's joint: the chest's front at z = -0.5), leaning and turning with it
  @head        the head's space (its top at y = 1.2)
  @right_hand  a hand's space below the elbow (joint space of the arm, the hand's end at y = -1.5), so it follows the
  @left_hand   forearm as it bends

blender --background --factory-startup --python build.py -- props <assets> <renders dir> [ids]  (BR_MODELS_ONLY=1)
"""
import math

from backbling import box
from shapes import S

SHELL, CHROME, HEAD, CYMBAL, STAND, WOOD, TIP = 0xC0282C, 0xC9CED6, 0xF2F0E8, 0xD9A93A, 0x2A2D33, 0xD8B98A, 0xF4EBDD


def drum(s, x, y, z, r, h, tilt=0.0, axis="y"):
    """A drum: a shell in its colour, white heads top and bottom, chrome hoops and a few lugs round it."""
    def place():
        s.at(x, y, z)
        if axis == "z":
            s.rotX(90)
        if tilt:
            s.rotX(tilt)
        return s
    place().color(SHELL).lathe([0, r * 0.97, r, r, r * 0.97, 0], [-h / 2, -h / 2, -h / 2 + 0.02, h / 2 - 0.02, h / 2, h / 2], 28)
    for end in (-1, 1):
        place().move(0, end * (h / 2 + 0.006), 0).color(HEAD).lathe([0, r * 0.95, r * 0.95, 0], [0, 0, 0.004, 0.004], 28)
        place().move(0, end * (h / 2 - 0.01), 0).color(CHROME).torus(r * 1.0, 0.03, 0, 360)
    n = 8 if r > 0.6 else 6
    for k in range(n):
        a = 360 * k / n
        place().rotY(a).move(r + 0.025, 0, 0).color(CHROME)
        box(s, 0.025, h * 0.28, 0.035, 0.01, 0.01)


def stand(s, x, y0, y1, z, legs=True):
    """A chrome pole on a tripod of dark legs, from the floor (y0) up to y1."""
    s.at(0, 0, 0).color(CHROME).tube([(x, y0 + 0.35, z), (x, y1, z)], [0.04, 0.035], caps=True, sides=10)
    if legs:
        for k in range(3):
            a = math.radians(120 * k + 30)
            s.at(0, 0, 0).color(STAND).tube([(x, y0 + 0.4, z), (x + math.cos(a) * 0.55, y0 + 0.02, z + math.sin(a) * 0.55)], [0.03, 0.025], caps=True, sides=8)


def cymbal(s, x, y, z, r, tilt=0.0):
    s.at(x, y, z).rotX(tilt).color(CYMBAL).lathe([0.04, r * 0.3, r, r, r * 0.3, 0.04], [0.05, 0.035, 0.0, -0.012, 0.022, 0.037], 32)
    s.at(x, y, z).rotX(tilt).color(CHROME).lathe([0, 0.05, 0.05, 0], [0.04, 0.04, 0.09, 0.09], 10)


def drum_kit():
    """A small kit in front of the drummer: the bass drum on the floor with a tom on it, the snare on its stand, a floor
    tom to the right, the hi-hat to the left and a crash cymbal up on the right."""
    s = S()
    drum(s, 0.0, -1.18, -2.35, 0.82, 0.62, axis="z")  # the bass drum, its head facing the drummer
    s.at(0.0, -1.18, -2.04).rotX(90).color(SHELL).torus(0.45, 0.02, 0, 360)  # (a ring on its front head)
    for side in (-1, 1):  # its spurs
        s.at(0, 0, 0).color(CHROME).tube([(side * 0.6, -1.5, -2.3), (side * 0.85, -1.98, -2.1)], [0.025, 0.02], sides=6)
    drum(s, 0.32, 0.42, -2.0, 0.4, 0.34, tilt=-18)  # a tom on the bass drum, tipped towards the drummer
    s.at(0, 0, 0).color(CHROME).tube([(0.2, -0.38, -2.25), (0.3, 0.2, -2.05)], [0.035, 0.03], sides=8)
    stand(s, -0.35, -2.0, -0.08, -1.25)
    drum(s, -0.35, 0.06, -1.25, 0.5, 0.26)  # the snare
    drum(s, 1.0, -0.62, -1.15, 0.55, 0.72)  # the floor tom
    for k in range(3):
        a = math.radians(120 * k + 90)
        s.at(0, 0, 0).color(CHROME).tube([(1.0 + math.cos(a) * 0.5, -0.8, -1.15 + math.sin(a) * 0.5), (1.0 + math.cos(a) * 0.62, -1.99, -1.15 + math.sin(a) * 0.62)],
                                          [0.025, 0.02], sides=6)
    stand(s, -1.2, -2.0, 0.62, -1.05)
    cymbal(s, -1.2, 0.5, -1.05, 0.5)  # the hi-hat, its two cymbals
    cymbal(s, -1.2, 0.58, -1.05, 0.5)
    stand(s, 1.25, -2.0, 1.3, -1.8)
    cymbal(s, 1.25, 1.32, -1.8, 0.66, tilt=16)  # the crash


def drumstick(s=None):
    """A drumstick held in the hand, carrying on from the forearm a little forward of it, its tip a rounded bead."""
    s = s or S()
    a, b = (0.0, -1.18, 0.12), (0.0, -1.18 - 1.25 * 0.94, 0.12 - 1.25 * 0.34)
    s.at(0, 0, 0).color(WOOD).tube([a, b], [0.055, 0.035], caps=True, sides=10)
    s.at(*b).color(TIP).ellipsoid(0.05, 0.065, 0.05)


# ---- the accessories the creator agreed to for more emotes (2.0.0-test5: "the emotes you suggested for having
# accessories, just do it") ----

RED, GOLD, BLACK, WHITE, SILVER, CARDBOARD = 0xC8232C, 0xE2B13C, 0x1E1F24, 0xF2F2EE, 0xC9CED6, 0xB98A55


def guitar():
    """Rock Out (@body, the upper body's space: the chest's front at z = -0.5): an electric guitar slung on a strap,
    its body low on the right of the belly (where the strumming hand reaches: an arm is 1.5 studs from its shoulder),
    the neck rising to the left where the left hand holds it."""
    s = S()

    def at(lx, ly, lz=0.0):  # (the guitar's own frame: the body's middle, the neck along -x, tipped up 25 degrees)
        return s.at(0.8, -0.35, -0.72).rotZ(-25).move(lx, ly, lz)
    body = ([0, 0.45, 0.47, 0.47, 0.45, 0], [-0.08, -0.08, -0.06, 0.06, 0.08, 0.08])
    at(0.12, -0.02).rotX(90).color(RED).lathe(*body, 28)  # the lower bout
    at(-0.42, 0.04).rotX(90).color(RED).lathe([0, 0.34, 0.36, 0.36, 0.34, 0], body[1], 24)  # the upper bout
    at(-0.62, 0.28).rotX(90).color(RED).lathe([0, 0.12, 0.13, 0.13, 0.12, 0], body[1], 14)  # the top horn
    at(-0.05, -0.12, -0.086).rotX(90).color(WHITE).lathe([0, 0.3, 0.3, 0], [0, 0, 0.006, 0.006], 24)  # the pickguard
    for x in (-0.32, -0.08, 0.16):  # pickups
        at(x, 0.0, -0.1).color(BLACK)
        box(s, 0.06, 0.16, 0.012, 0.01, 0.01)
    at(0.33, 0.0, -0.1).color(SILVER)  # the bridge
    box(s, 0.05, 0.17, 0.015, 0.01, 0.01)
    for k in range(3):  # knobs
        at(0.26 + 0.1 * k, -0.28 - 0.05 * k, -0.11).color(BLACK).lathe([0, 0.035, 0.035, 0], [0, 0, 0.03, 0.03], 10)
    # the neck with its frets, the headstock with its tuners
    at(-1.3, 0.0, -0.02).color(0x7A4A28)
    box(s, 0.86, 0.07, 0.045, 0.01, 0.015)
    for k in range(10):
        at(-0.55 - 0.15 * k, 0.0, -0.068).color(SILVER)
        box(s, 0.006, 0.07, 0.004, 0.002, 0.002)
    at(-2.32, 0.03, -0.02).color(RED)
    box(s, 0.2, 0.1, 0.035, 0.02, 0.02)
    for k in range(3):
        for side in (-1, 1):
            at(-2.2 - 0.1 * k, side * 0.12, -0.02).rotX(90).color(SILVER).lathe([0, 0.025, 0.025, 0], [-0.05, -0.05, 0.05, 0.05], 8)
    for k in range(6):  # the strings, bridge to headstock
        y = -0.05 + 0.02 * k
        at(0, 0, 0).color(0xE8E8E8).tube([(0.33, y, -0.1), (-2.15, y * 0.8, -0.07)], [0.005, 0.005], caps=False, sides=4)
    # the strap: from the bottom of the body up across the chest, over the left shoulder and down the back
    s.at(0, 0, 0).color(BLACK).tube([(1.2, -0.55, -0.72), (1.02, -0.2, -0.58), (0.4, 0.55, -0.56), (-0.55, 1.03, -0.3), (-0.62, 1.03, 0.3),
                                     (-0.2, 0.2, 0.56), (0.5, -0.3, 0.56), (0.95, -0.55, 0.3)], [0.05] * 8, caps=False, sides=6)


def top_hat():
    """Tap Dance (@head, the head's space: its top at y = 1.2): a black top hat with a red band, a little on the tilt."""
    s = S()
    s.at(0.0, 1.08, 0.0).rotZ(-6).color(BLACK).lathe([0, 0.72, 0.74, 0.74, 0.46, 0.47, 0.46, 0], [0, 0, 0.01, 0.05, 0.05, 0.6, 0.98, 0.98], 32)
    s.at(0.0, 1.08, 0.0).rotZ(-6).move(0, 0.06, 0).color(RED).lathe([0.48, 0.48], [0.0, 0.16], 32)


def cane():
    """Tap Dance (@right_hand): a black cane with a silver knob in the hand and a white tip, down to the floor."""
    s = S()
    s.at(0, 0, 0).color(BLACK).tube([(0, -1.2, 0.0), (0, -3.35, -0.05)], [0.05, 0.045], caps=False, sides=10)
    s.at(0, -1.13, 0.0).color(SILVER).ellipsoid(0.11, 0.09, 0.11)
    s.at(0, -3.38, -0.05).color(WHITE).lathe([0, 0.06, 0.055, 0], [0, 0, -0.12, -0.12], 10)


def lasso():
    """Lasso Twirl (@right_hand): a rope from the hand out to a wide loop spinning past it (the arm circles overhead)."""
    s = S()
    s.at(0, -2.55, 0).color(0xC9A46A).torus(0.85, 0.04, 0, 360)
    s.at(0, 0, 0).color(0xC9A46A).tube([(0, -1.45, 0), (0, -1.9, 0.0), (0, -2.45, 0.5), (0, -2.55, 0.85)], [0.04] * 4, caps=False, sides=6)
    s.at(0, -2.55, 0.85).color(0xA8844A).ellipsoid(0.07, 0.06, 0.09)  # (the honda knot)


def rounded_box(cx, cy, cz, a, b, c, side=0.4, ends=0.6):
    """A rounded box (a superellipsoid) for S.surface: half sizes a, b, c round (cx, cy, cz); side and ends (under 1)
    square off its sides and its top and bottom."""
    sgn = lambda x: (x > 0) - (x < 0)

    def f(u, v):
        th, ph = 2 * math.pi * u, math.pi * (v - 0.5)
        k = abs(math.cos(ph)) ** ends
        return (cx + a * k * sgn(math.cos(th)) * abs(math.cos(th)) ** side, cy + b * sgn(math.sin(ph)) * abs(math.sin(ph)) ** ends,
                cz + c * k * sgn(math.sin(th)) * abs(math.sin(th)) ** side)
    return f


def glove(side):
    """Boxing (@right_hand / @left_hand): a big red boxing glove holding the whole hand (2.0.0-test6, creator: "Gloves
    should not clip through the hands"): a Roblox hand is the square end of the arm, a stud across with its corners
    0.71 from the middle, which poked out of test5's round glove. Now a rounded boxy mitt wider than the hand on every
    side, corners and all (0.6 out along the diagonal), from the wrist to well past the fingertips, the thumb along the
    front, and a white padded cuff round the forearm outside its corners (checked: no corner of the hand or the forearm
    under the cuff's top reaches either surface). 2.0.0-test6.1 (creator: "Boxing Gloves should rotate so the thumb
    holes are on the inside rather than at the top"): the thumb on the inner side, towards the other hand (side: +1
    the right hand's glove, -1 the left's; the guard doesn't turn the arms about their length, so the inside is the
    arm's own side towards the body), not on its front, which the raised forearm turns to the top."""
    s = S()
    s.at(0, 0, 0).color(RED).surface(rounded_box(0, -1.55, -0.03, 0.72, 0.56, 0.73, 0.4, 0.6), 36, 22, one_sided=True)
    s.at(0, 0, 0).color(RED).surface(rounded_box(-side * 0.73, -1.38, -0.03, 0.17, 0.32, 0.24, 0.7, 0.7), 16, 12, one_sided=True)  # the thumb
    s.at(0, 0, 0).color(WHITE).surface(rounded_box(0, -1.06, 0, 0.64, 0.17, 0.64, 0.35, 0.3), 36, 10, one_sided=True)  # the cuff


def pompom():
    """Cheer (@right_hand / @left_hand): a big pom-pom of gold and white strands bursting out of the fist."""
    import random
    s = S()
    rnd = random.Random("pompom")
    s.at(0, -1.75, 0).color(GOLD).ellipsoid(0.42, 0.42, 0.42)
    for k in range(240):  # (2.0.0-test5: thick with fine strands, so it's fluffy, not spiky)
        u, v = rnd.uniform(-1, 1), rnd.uniform(0, 2 * math.pi)
        dx, dy, dz = math.sqrt(1 - u * u) * math.cos(v), u, math.sqrt(1 - u * u) * math.sin(v)
        r = rnd.uniform(0.5, 0.74)
        bend = rnd.uniform(-0.08, 0.08)
        s.at(0, 0, 0).color(GOLD if k % 3 else WHITE).tube([(dx * 0.3, -1.75 + dy * 0.3, dz * 0.3), (dx * (r - 0.12) + bend, -1.75 + dy * (r - 0.12), dz * (r - 0.12) - bend),
                                                          (dx * r, -1.75 + dy * r, dz * r)], [0.03, 0.022, 0.012], caps=False, sides=4)


def cardboard():
    """Breakdance Spin (@ground): a flattened cardboard box on the floor to spin on, its folds and a strip of tape."""
    s = S()
    s.at(0, -1.99, 0).color(CARDBOARD)
    box(s, 2.2, 0.02, 2.0, 0.01, 0.01)
    for x in (-0.75, 0.75):
        s.at(x, -1.965, 0).color(0x8E6538)
        box(s, 0.02, 0.006, 2.0, 0.003, 0.003)
    s.at(0.0, -1.962, -1.2).color(0xD9C9A3)
    box(s, 1.6, 0.006, 0.12, 0.003, 0.003)


def boombox():
    """Breakdance Spin (@ground): a boombox on the floor beside the mat, two speakers, a tape deck and a handle."""
    s = S()
    cx, cy, cz = 3.2, -1.48, -0.6
    s.at(cx, cy, cz).rotY(-30).color(0x3A3E46)
    box(s, 0.85, 0.5, 0.26, 0.06, 0.06)
    for side in (-1, 1):
        s.at(cx, cy, cz).rotY(-30).move(side * 0.5, -0.02, -0.27).rotX(90).color(BLACK).lathe([0, 0.3, 0.33, 0.33, 0], [0.02, 0.02, 0.0, -0.03, -0.03], 24)
        # (the silver cap 0.02 proud of the cone's front: flush, the two faces fought, 2.0.0-test6)
        s.at(cx, cy, cz).rotY(-30).move(side * 0.5, -0.02, -0.29).rotX(90).color(SILVER).lathe([0, 0.08, 0.08, 0], [0.02, 0.02, -0.03, -0.03], 14)
    s.at(cx, cy, cz).rotY(-30).move(0, 0.12, -0.27).color(SILVER)
    box(s, 0.16, 0.12, 0.02, 0.01, 0.01)
    s.at(cx, cy, cz).rotY(-30).color(SILVER).tube([(-0.6, 0.5, 0), (-0.55, 0.72, 0), (0.55, 0.72, 0), (0.6, 0.5, 0)], [0.04] * 4, caps=True, sides=8)


def cape():
    """Hero Fly (@body, the upper body's space: shoulders at y = 1, the back at z = 0.5): a red cape with a gold edge
    from a clasp at the collar, streaming out behind and down."""
    s = S()

    def sheet(u, v, lift=0.0):  # u across (0 left .. 1 right), v down the cape (0 at the collar)
        x = (u - 0.5) * (1.9 + 0.9 * v)
        y = 0.95 - 3.2 * v + 0.15 * math.sin(3 * math.pi * u + 2 * v) * v
        z = 0.56 + 1.9 * v * v + 0.25 * math.sin(math.pi * u) * v + lift
        return (x, y, z)
    s.at(0, 0, 0).color(RED).surface(lambda u, v: sheet(u, v), 16, 18)
    s.at(0, 0, 0).color(GOLD).tube([sheet(u / 16, 1.0, 0.01) for u in range(17)], [0.035] * 17, caps=True, sides=6)
    s.at(0, 0.98, -0.52).rotX(90).color(GOLD).lathe([0, 0.13, 0.13, 0], [0, 0, 0.04, 0.04], 14)  # (the clasp at the collar)
    s.at(0, 0, 0).color(GOLD).tube([(-0.95, 0.98, 0.56), (-0.5, 1.02, -0.45), (0, 0.98, -0.52), (0.5, 1.02, -0.45), (0.95, 0.98, 0.56)], [0.03] * 5,
                                   caps=True, sides=6)


def glowstick(color):
    """Electro Shuffle (@right_hand / @left_hand): a glow stick held up out of the fist, glowing."""
    def make():
        s = S()
        s.at(0, 0, 0).color(color, glow=True).tube([(0, -1.25, 0.1), (0, -2.55, -0.2)], [0.07, 0.07], caps=True, sides=12)
        # (the cap starting a little up the stick: its end and the stick's end were one face, fighting, 2.0.0-test6)
        s.at(0, 0, 0).color(0xE8E8E8).tube([(0, -2.53, -0.195), (0, -2.65, -0.22)], [0.082, 0.066], caps=True, sides=12)
    return make


NO_RENDER = {"render": False, "scale": 1.0, "ao": 0.25}
ITEMS = {
    "prop_drum_kit": ("Drum kit", drum_kit, NO_RENDER),
    "prop_drumstick": ("Drumstick", drumstick, NO_RENDER),
    "prop_guitar": ("Electric guitar", guitar, NO_RENDER),
    "prop_top_hat": ("Top hat", top_hat, NO_RENDER),
    "prop_cane": ("Cane", cane, NO_RENDER),
    "prop_lasso": ("Lasso", lasso, NO_RENDER),
    "prop_glove_r": ("Boxing glove (right)", lambda: glove(1), NO_RENDER),
    "prop_glove_l": ("Boxing glove (left)", lambda: glove(-1), NO_RENDER),
    "prop_pompom": ("Pom-pom", pompom, NO_RENDER),
    "prop_cardboard": ("Cardboard mat", cardboard, NO_RENDER),
    "prop_boombox": ("Boombox", boombox, NO_RENDER),
    "prop_cape": ("Hero cape", cape, NO_RENDER),
    "prop_glowstick_blue": ("Glow stick (blue)", glowstick(0x3AA8FF), NO_RENDER),
    "prop_glowstick_pink": ("Glow stick (pink)", glowstick(0xFF4FD8), NO_RENDER),
}
