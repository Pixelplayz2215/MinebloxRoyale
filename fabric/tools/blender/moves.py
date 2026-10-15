"""
How the avatar moves when it isn't emoting (creator: "add knees and elbows so the animations are more animated. New
walking animation, running, aiming, reviving, rebooting, etc."): key poses like the emotes' (emote_anims.py: the same
channels, plus the elbows and knees), made smooth curves in Blender by emote_video.py, sampled into the move_keys
sheet, which the game plays (RobloxAvatarRenderer), and rendered as videos for the creator.

Each move's t (0..1) is along one of:
  phase     the walk cycle: right foot forward at 0, left foot forward at 0.5 (the game: its walk animation's position)
  time      a loop of loop seconds (breathing, reviving, rebooting...)
  pitch     where you look: 0 straight up, 0.5 ahead, 1 straight down (aiming)
  progress  once through, 0 to 1 (a pickaxe swing; jumping, from going up to falling fast)
plant: touch  the lowest foot rests on the floor (the body's height comes from the legs; bob adds a hop on top)
       floor  feet never below the floor (bent knees take a dip)
       none   as given (in the air; lying down)
Channels (degrees; bob in studs): as emote_anims.py, plus
  rel / lel  right / left elbow (0 straight; the forearm bends forward)
  rkn / lkn  right / left knee (0 straight; the shin bends back)
keep: arms the hand fix leaves alone (aiming: the gun arm stays on the look)
free: limbs allowed to touch the body (sitting: an R6 leg can't come up level without its top meeting the torso, as in
      Roblox's own sit; in a car the doors hide it)
"""
from emote_anims import K, mirror


class Move:
    def __init__(self, name, line, axis, loop, keys, plant="touch", linear=(), lying=False, keep=(), free=()):
        self.name, self.line, self.axis, self.loop, self.keys = name, line, axis, loop, keys
        self.plant, self.linear, self.lying, self.keep, self.free = plant, set(linear), lying, tuple(keep), tuple(free)
        self.note = line

    @property
    def cyclic(self):
        return self.axis in ("phase", "time")


def half_cycle(keys):
    """A walk-style cycle from its first half (t 0..0.5): the second half is the first mirrored left to right."""
    out = list(keys)
    for t, ch in keys:
        out.append((t + 0.5, mirror(ch)))
    out.append((1.0, dict(keys[0][1])))
    return out


# ------------------------------------------------------------------------------------------------- moving about

def _walk():
    # 0: right heel down ahead, left toe pushing off behind; the arms swing the other way, elbows soft
    return half_cycle([
        K(0, rlx=24, rkn=4, llx=-20, lkn=14, rax=-18, rel=14, lax=20, lel=26, twist=4, hx=2, raz=5, laz=5),
        K(0.12, rlx=16, rkn=16, llx=-14, lkn=40, rax=-8, rel=16, lax=10, lel=22, twist=2),
        K(0.25, rlx=0, rkn=8, llx=14, lkn=58, rax=4, rel=18, lax=-4, lel=16, twist=0),
        K(0.38, rlx=-12, rkn=6, llx=26, lkn=22, rax=14, rel=24, lax=-14, lel=14, twist=-3),
    ])


def _run():
    # sprinting: leaning in, knees driving high, heels kicking up, arms pumping with the elbows bent
    return half_cycle([
        K(0, rlx=34, rkn=18, llx=-34, lkn=36, rax=-42, rel=78, lax=48, lel=96, twist=8, lean=12, hx=-8, raz=8, laz=8, bob=0.0),
        K(0.1, rlx=16, rkn=34, llx=-26, lkn=84, rax=-24, rel=82, lax=30, lel=96, twist=4, bob=-0.02),
        K(0.22, rlx=-14, rkn=18, llx=26, lkn=108, rax=12, rel=88, lax=-10, lel=86, twist=-2, bob=0.12),
        K(0.36, rlx=-30, rkn=44, llx=50, lkn=56, rax=36, rel=94, lax=-34, lel=80, twist=-6, bob=0.1),
    ])


def _crouch_walk():
    base = dict(lean=20, hx=-14, rel=40, lel=40, rax=16, lax=16, raz=10, laz=10)
    return half_cycle([
        K(0, rlx=48, rkn=62, llx=14, lkn=78, **base),
        K(0.25, rlx=30, rkn=78, llx=40, lkn=100, rax=22, lax=10),
        K(0.38, rlx=20, rkn=70, llx=52, lkn=78),
    ])


MOVES = {
    "idle": Move("Standing", "Breathing: elbows and knees relaxed", "time", 3.2, [
        K(0, rel=6, lel=6, raz=4, laz=4, rkn=3, lkn=3, hx=0),
        K(0.5, rel=11, lel=11, raz=5.5, laz=5.5, rkn=6, lkn=6, hx=-1.5, rax=2, lax=2),
    ]),
    "walk": Move("Walking", "Heel to toe with soft knees, arms swinging the other way", "phase", 1.1, _walk()),
    "run": Move("Running", "Sprinting: knees up, heels kick back, arms pumping", "phase", 0.62, _run()),
    "crouch": Move("Crouching", "Sneaking: low on bent knees, leaning in", "time", 2.4, [
        K(0, rlx=46, llx=40, rkn=82, lkn=74, lean=20, hx=-14, rax=16, lax=16, rel=40, lel=40, raz=10, laz=10),
        K(0.5, rlx=48, llx=42, rkn=86, lkn=78, lean=21, hx=-15, rax=18, lax=18, rel=44, lel=44, raz=11, laz=11),
    ]),
    "crouch_walk": Move("Sneaking", "Creeping along, low and leaning in", "phase", 1.4, _crouch_walk()),
    "air": Move("Jumping and falling", "Knees tucked going up, legs reaching for the ground coming down, spread out falling fast",
                "progress", 1.6, [
        K(0, rlx=38, rkn=70, llx=12, lkn=40, rax=30, raz=24, rel=40, lax=42, laz=24, lel=46, hx=-6, lean=4),
        K(0.55, rlx=14, rkn=20, llx=-6, lkn=30, rax=14, raz=48, rel=30, lax=12, laz=48, lel=32, hx=4, lean=0),
        K(1, rlx=-10, rkn=60, llx=-14, lkn=70, rlz=14, llz=14, rax=40, raz=108, rel=36, lax=40, laz=108, lel=36, hx=-12, lean=-4),
    ], plant="none"),
    "swim": Move("Swimming", "Front crawl: one arm reaching, one pulling, legs kicking", "time", 1.3, [
        K(0, rax=176, raz=8, rel=8, lax=0, laz=12, lel=12, rlx=10, rkn=12, llx=-10, lkn=26, hy=-14),
        K(0.17, rlx=-10, rkn=26, llx=10, lkn=12),
        K(0.25, rax=96, raz=6, rel=38, lax=96, laz=82, lel=78, hy=0),
        K(0.33, rlx=10, rkn=12, llx=-10, lkn=26),
        K(0.5, rax=0, raz=12, rel=12, lax=176, laz=8, lel=8, rlx=-10, rkn=26, llx=10, lkn=12, hy=14),
        K(0.67, rlx=10, rkn=12, llx=-10, lkn=26),
        K(0.75, rax=96, raz=82, rel=78, lax=96, laz=6, lel=38, hy=0),
        K(0.83, rlx=-10, rkn=26, llx=10, lkn=12),
    ], plant="none"),
    "tread": Move("Treading water", "Head up, hands sculling in front, legs cycling", "time", 1.2, [
        K(0, rax=46, raz=40, rel=58, lax=46, laz=18, lel=66, rlx=26, rkn=50, llx=6, lkn=20, bob=0.0),
        K(0.5, rax=46, raz=18, rel=66, lax=46, laz=40, lel=58, rlx=6, rkn=20, llx=26, lkn=50, bob=0.04),
    ], plant="none"),
    "climb": Move("Climbing", "Up a ladder: hand over hand, knee over knee", "phase", 1.0, half_cycle([
        K(0, rax=158, raz=8, rel=30, lax=112, laz=8, lel=72, rlx=52, rkn=84, llx=8, lkn=14, hx=-10),
        K(0.25, rax=138, rel=50, lax=132, lel=50, rlx=30, rkn=50, llx=30, lkn=50),
    ]), plant="none"),
    "glide": Move("Gliding", "Both hands up on the glider's grips, legs dangling", "time", 2.2, [
        K(0, rax=170, raz=-15, lax=170, laz=-15, rel=4, lel=4, rlx=10, rkn=26, llx=-2, lkn=38, hx=-4),
        K(0.5, rlx=-2, rkn=38, llx=10, lkn=26, hx=-6),
    ], plant="none"),
    # v2.0.0-test3 (creator: "a new skydiving animation"): falling from the bus before the glider opens, Fortnite-style.
    # Arm angles from where each part should point (the arm solver): the upper arms out to the sides, a little towards
    # the head and the sky, the forearms towards the head; the arms take turns to reach a little further.
    "skydive": Move("Skydiving", "Belly down in the air: arms out wide with the elbows bent, knees bent, a little flutter", "time", 1.4, [
        K(0, flip=70, hx=-48, rax=45.0, raz=113.0, ray=-48.7, rel=52.7, lax=45.0, laz=113.0, lay=-48.7, lel=52.7,
          rlx=-8, rlz=14, rkn=70, llx=-8, llz=14, lkn=70, tilt=0, yaw=0),
        K(0.25, rax=26.1, raz=116.6, ray=-77.2, rel=53.0, lax=58.6, laz=112.9, lay=-29.3, lel=52.8, rkn=78, lkn=62, tilt=3, yaw=2),
        K(0.5, rax=45.0, raz=113.0, ray=-48.7, rel=52.7, lax=45.0, laz=113.0, lay=-48.7, lel=52.7, rkn=70, lkn=70, tilt=0, yaw=0),
        K(0.75, rax=58.6, raz=112.9, ray=-29.3, rel=52.8, lax=26.1, laz=116.6, lay=-77.2, lel=53.0, rkn=62, lkn=78, tilt=-3, yaw=-2),
    ], plant="none"),
    # (looking down while skydiving: the game blends from the skydive into this as you look further down)
    "dive": Move("Diving", "Head first towards the ground, arms swept back along the body, legs together", "time", 1.0, [
        # (the arms straight and not turned along their length: a turned arm's square corners catch the body when it's
        # close in; straight and square, it clears at any spread, also blending to the skydive and the glider)
        K(0, flip=145, hx=-32, rax=-19.3, raz=18, ray=0, rel=3, lax=-19.3, laz=18, lay=0, lel=3,
          rlx=-4, rlz=4, rkn=14, llx=-4, llz=4, lkn=10, tilt=0),
        K(0.5, rax=-16.7, raz=21, rel=3.5, lax=-16.7, laz=21, lel=3.5, rkn=10, lkn=16, tilt=1.5),
    ], plant="none"),

    # ------------------------------------------------------------------------------------------- doing things
    "aim": Move("Aiming", "Holding a gun where you look (here: up, ahead, then down)", "pitch", 3.0, [
        # The right forearm carries the gun exactly along the look (solved for each pitch with rig.py: the shoulder
        # forward 45 - pitch / 2 and 4 degrees out, so the upper arm stays out of the chest, the elbow bent to match; 84
        # degrees up or down at most); the left arm reaches in along the gun (an R6 arm can't get across to its front).
        K(0, rax=100.0, raz=4, rel=74.1, lax=166, laz=-24, lel=20),
        K(0.1667, rax=75.0, raz=4, rel=75.0, lax=139, laz=-24, lel=20),
        K(0.3333, rax=60.0, raz=4, rel=60.0, lax=112, laz=-24, lel=20),
        K(0.5, rax=45.0, raz=4, rel=44.9, lax=85, laz=-24, lel=20),
        K(0.6667, rax=30.0, raz=4, rel=29.9, lax=58, laz=-24, lel=20),
        K(0.8333, rax=15.0, raz=4, rel=14.7, lax=31, laz=-24, lel=20),
        K(1, rax=0.0, raz=4, rel=4.5, lax=4, laz=-24, lel=20),
    ], plant="none", linear=("rax", "rel", "lax"), keep=("right_arm",)),
    # v2.0.0 (creator: "make shooting guns a bit better ... a proper aim animation, recoil to each gun"): a gun is
    # carried at the hip and raised into the aim (above) when aiming down sights; every shot kicks (recoil, by the gun).
    "hip": Move("Hip", "Holding a gun at the hip, pointed where you look (here: up, ahead, then down); raised into the aim when aiming",
                "pitch", 3.0, [
        # The right forearm carries the gun along the look, low: the shoulder only 15 - pitch / 3 forward and 10 out,
        # the elbow bent the rest of the way; the left hand under the front of the gun.
        K(0, rax=43.0, raz=10, rel=131.0, lax=90.4, laz=-22, lel=50),
        K(0.1667, rax=33.7, raz=10, rel=112.3, lax=73.6, laz=-22, lel=50),
        K(0.3333, rax=24.3, raz=10, rel=93.7, lax=56.8, laz=-22, lel=50),
        K(0.5, rax=15.0, raz=10, rel=75.0, lax=40.0, laz=-22, lel=50),
        K(0.6667, rax=5.7, raz=10, rel=56.3, lax=23.2, laz=-22, lel=50),
        K(0.8333, rax=-3.7, raz=10, rel=37.7, lax=6.4, laz=-22, lel=50),
        K(1, rax=-13.0, raz=10, rel=19.0, lax=-10.4, laz=-22, lel=50),
    ], plant="none", linear=("rax", "rel", "lax"), keep=("right_arm",)),
    "recoil": Move("Recoil", "A shot's kick: the gun and arms jump up and back, the head and body with them, and settle (each gun kicks its own amount)",
                   "progress", 0.25, [
        # From the aim straight ahead (aim at 0.5); the game adds how far this moves from its start to whatever hold
        # the gun is in, times the gun's kick (weapons sheet).
        K(0, rax=45, raz=4, rel=44.9, lax=85, laz=-24, lel=20, lean=0, hx=0, twist=0),
        K(0.18, rax=53, raz=5, rel=55, lax=91, laz=-24, lel=27, lean=-3, hx=-4, twist=-3),
        K(0.5, rax=47.5, raz=4.3, rel=48, lax=87, laz=-24, lel=22, lean=-1, hx=-1.5, twist=-1),
        K(1, rax=45, raz=4, rel=44.9, lax=85, laz=-24, lel=20, lean=0, hx=0, twist=0),
    ], plant="none", keep=("right_arm",)),
    # v2.0.0 (creator: "Drivable cars"): riding in a car, the driver and passengers alike.
    "sit": Move("Sitting in a car", "Sitting back in a car, hands on the wheel", "time", 2.4, [
        K(0, rlx=88, rkn=86, llx=88, lkn=86, rlz=4, llz=4, rax=58, raz=8, rel=34, lax=58, laz=8, lel=34, lean=-4, hx=0),
        K(0.5, rax=55, rel=37, lax=61, lel=31, hx=2),
    ], plant="none", free=("right_leg", "left_leg")),
    "swing": Move("Pickaxe swing", "Wound up over the shoulder, a big chop, follow through", "progress", 0.7, [
        K(0, rax=30, raz=6, rel=40, twist=0, lean=0, lax=12, lel=24),
        K(0.3, rax=158, raz=14, rel=72, twist=-16, lean=-6, lax=34, lel=50, hx=-4),
        K(0.55, rax=48, raz=2, rel=8, twist=12, lean=10, lax=-6, lel=20, hx=6),
        K(0.8, rax=12, raz=4, rel=26, twist=6, lean=6, lax=4, lel=22, hx=3),
        K(1, rax=30, raz=6, rel=40, twist=0, lean=0, lax=12, lel=24, hx=0),
    ], plant="none"),
    "heal": Move("Healing", "Bandages and med kits: hands together in front, wrapping", "time", 0.9, [
        K(0, rax=42, raz=-22, rel=74, lax=34, laz=-22, lel=86, hx=22, lean=6, rkn=6, lkn=6),
        K(0.5, rax=34, raz=-22, rel=86, lax=42, laz=-22, lel=74, hx=24),
    ], plant="none"),
    "drink": Move("Drinking", "Shield potions, Slurp Juice and the Chug Jug: the drink to the mouth, head back", "time", 1.2, [
        K(0, rax=70, raz=-30, rel=128, lax=10, laz=8, lel=30, hx=-14, lean=-3, rkn=4, lkn=4),
        K(0.5, rax=76, raz=-28, rel=124, hx=-20, lean=-5),
    ], plant="none"),
    "revive": Move("Reviving", "Down on one knee by a knocked teammate, both hands working on them", "time", 1.5, [
        # (right knee and shin down, left foot flat: both on the floor, rig.touch; hands pressing down in front)
        K(0, rlx=-6, rkn=104, llx=64, lkn=64, rlz=4, lean=28, hx=18, rax=70, raz=-4, rel=12, lax=70, laz=-4, lel=12),
        K(0.5, rax=58, rel=26, lax=58, lel=26, lean=32, hx=20),
    ]),
    "reboot": Move("Rebooting", "At the Reboot Van's screen, typing", "time", 1.1, [
        K(0, lean=8, hx=16, rax=56, raz=-8, rel=58, lax=50, laz=-8, lel=66, rkn=8, lkn=8),
        K(0.25, rax=50, rel=68),
        K(0.5, rax=56, rel=58, lax=56, lel=58),
        K(0.75, lax=50, lel=68),
    ]),
    "search": Move("Searching a chest", "Bent over it, rummaging with both hands", "time", 1.0, [
        K(0, rlx=26, rkn=46, llx=22, lkn=40, lean=24, hx=22, rax=66, rel=22, raz=-4, lax=54, lel=34, laz=-4),
        K(0.5, rax=54, rel=34, lax=66, lel=22),
    ]),
    "crawl": Move("Knocked down", "Crawling on the belly: one arm reaches ahead along the ground while the other pulls back, legs push",
                  "phase", 2.0, half_cycle([
        # (arms along the ground: shoulder angles where the hand just rests on it, rig.py)
        K(0, rax=166, raz=15, rel=0, lax=160, laz=82, lel=14, rlx=-4, rkn=10, llx=-16, lkn=58, yaw=-5, hx=-50),
        K(0.25, rax=162, raz=48, rel=8, lax=192, laz=48, lel=40, rlx=-10, rkn=30, llx=-10, lkn=30, yaw=0),
    ]), plant="none", lying=True),
}
