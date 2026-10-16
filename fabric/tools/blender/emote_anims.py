"""
The 40 emotes as key poses (creator: "cleaner and more expressive animations, use Blender to animate them"). Each is
a loop of key poses on the R6 avatar; emote_video.py makes them Blender keyframes (smooth Bezier curves between the
keys, cyclic), renders them and samples the curves into the emote_keys sheet, which the game plays.

Channels, in degrees (bob in studs), each 0 when not given (the elbows and knees: creator, "add knees and elbows so
the animations are more animated"):
  rax / lax  right / left arm forward (0 hanging, 90 pointing ahead, 180 straight up, below 0 back)
  raz / laz  right / left arm out to its own side (90 straight out; below 0 across the body)
  rlx / llx  right / left leg forward          rlz / llz  right / left leg out to its side
  yaw        the whole body turns               bob        the whole body lifts (studs)
  lean       above the hips: leans forward      tilt       ... leans to its right      twist  ... turns to its right
  hx         head nods down                     hy         head turns to its right     hz     head tilts to its right
  rel / lel  right / left elbow (0 straight; the forearm bends forward: with the upper arm out level and forward, up)
  rkn / lkn  right / left knee (0 straight; the shin bends back)
Every moment then gets rig.py's fixes: feet never through the floor (a dip bends the knees), hands kept out of the body.
Keys: K(t, ...) with t from 0 to 1 over the loop; a channel's value at t = 1 is its value at t = 0 unless given.
"""

REST = dict(raz=4, laz=4)


def K(t, **ch):
    return (t, ch)


class Emote:
    def __init__(self, loop, keys, linear=(), note="", free=(), plant="floor", lift=0.0, wrap=None):
        # free: limbs allowed to go into the body (only where the creator chose it: the Floss's arms, "Full reach");
        # plant: "floor" (feet kept out of the floor) or "touch" (the lowest point of the body on it: a headspin);
        # lift: the whole body raised that much more (studs: a headspin's head on top of its cardboard mat, 2.0.0-test6)
        # wrap: channels that go on round, by that many degrees each loop (an arm turning over and over: 2.0.0-test6.1,
        # the Swim); the game blends angles the short way, so the loop's end and its start are the same pose
        self.loop, self.keys, self.linear, self.note, self.free, self.plant, self.lift = loop, keys, set(linear), note, tuple(free), plant, lift
        self.wrap = dict(wrap or {})


def mirror(ch):
    """The same pose, mirrored left to right."""
    out = {}
    swap = {"rax": "lax", "raz": "laz", "lax": "rax", "laz": "raz", "rlx": "llx", "llx": "rlx", "rlz": "llz", "llz": "rlz", "rel": "lel", "lel": "rel",
            "rkn": "lkn", "lkn": "rkn", "ray": "lay", "lay": "ray"}
    for k, v in ch.items():
        if k in swap:
            out[swap[k]] = v
        elif k in ("yaw", "tilt", "twist", "hy", "hz"):
            out[k] = -v
        else:
            out[k] = v
    return out


def beat(t, ch):
    return (t, ch)


# -------------------------------------------------------------------------------------------------------------------

def _floss():
    # (creator, test4: "Too fast, and it doesn't reach all the way from one side to the other"; chose "Full reach")
    # Both arms swing to one side, then the other: one passes in front of the body, the other behind, and every second
    # swing they change over, so each arm crosses the body in front and then behind. The crossing arm reaches right
    # across (its elbow folding in: Y turns it so it can), the upper body tips with the arms and the hips sway the
    # other way on soft knees. An R6 arm can't cross without its upper part going into the body's edge: free.
    L = dict(tilt=-9, twist=-8, yaw=-4, hz=6, rlz=8, llz=-4, rkn=6, lkn=16)  # arms to its left, hips to its right
    R = mirror(L)
    across_front, across_back = dict(x=29, z=-38, y=90, e=15), dict(x=-29, z=-38, y=90, e=15)
    own_front, own_back = dict(x=30, z=17, y=90, e=8), dict(x=-30, z=17, y=90, e=8)

    def arms(right, left, body):
        return dict(rax=right["x"], raz=right["z"], ray=right["y"], rel=right["e"],
                    lax=left["x"], laz=left["z"], lay=left["y"], lel=left["e"], **body)
    return [K(0, **arms(across_front, own_back, L)), K(0.25, **arms(own_back, across_front, R)),
            K(0.5, **arms(across_back, own_front, L)), K(0.75, **arms(own_front, across_back, R)),
            K(1, **arms(across_front, own_back, L))]


def _robot():
    # stiff 90 degree elbows, snapping from pose to pose and holding
    a = dict(rax=8, raz=4, rel=88, lax=0, laz=4, lel=0, hy=0, twist=0)          # right forearm out in front
    b = dict(rax=0, raz=4, rel=0, lax=8, laz=4, lel=88, hy=-30, twist=-15)      # left forearm out, turned
    c = dict(rax=90, raz=88, rel=90, lax=90, laz=88, lel=90, hy=0, twist=0, bob=-0.06)  # both forearms up, elbows out
    d = dict(rax=8, raz=4, rel=88, lax=8, laz=4, lel=88, hy=30, twist=15)       # both forearms out, turned
    keys, t = [], 0.0
    for p in (a, b, c, d):  # snap (0.06) then hold
        keys += [K(t, **p), K(t + 0.19, **p)]
        t += 0.25
    return keys + [K(1, **a)]


EMOTES = {
    "wave": Emote(1.0, [
        # (2.0.0-test6, creator: "Arms should bend side to side, not rotate front and back": only the elbow moves)
        K(0, hz=6, hy=-6, tilt=-4, bob=0, rkn=4, lkn=4, rax=-7.1, raz=130.2, ray=-94.2, rel=66.0, lax=0, laz=4, lay=0, lel=6),
        K(0.25, hz=2, bob=0.02, rax=-7.1, raz=130.2, ray=-94.2, rel=8.0),
        K(0.5, hz=6, bob=0, rax=-7.1, raz=130.2, ray=-94.2, rel=66.0),
        K(0.75, hz=2, bob=0.02, rax=-7.1, raz=130.2, ray=-94.2, rel=8.0),
    ], note="right arm up and out, the forearm swinging side to side from the elbow, in line and then upright beside the head"),
    "swing": Emote(0.8, [
        K(0, rax=25, raz=70, rel=20, lax=72, laz=-6, lel=48, twist=22, tilt=6, hy=10, bob=0, rkn=6, lkn=18),
        K(0.25, rax=20, raz=10, rel=30, lax=20, laz=10, lel=30, twist=0, tilt=0, hy=0, bob=-0.1),
        K(0.5, rax=72, raz=-6, rel=48, lax=25, laz=70, lel=20, twist=-22, tilt=-6, hy=-10, bob=0, rkn=18, lkn=6),
        K(0.75, rax=20, raz=10, rel=30, lax=20, laz=10, lel=30, twist=0, tilt=0, hy=0, bob=-0.1),
    ], note="both arms swing side to side, elbows easing, dipping in the middle"),
    "robot": Emote(1.6, _robot(), note="stiff, snapping 90 degree elbows with holds"),
    "floss": Emote(1.4, _floss(), note="arms swing right across the body, front and back in turn, hips the other way",
                   free=("right_arm", "left_arm")),
    "dab": Emote(1.6, [
        # (creator, test4: "doesn't look like a real dab") a wind-up, then the dab: the right arm straight out and up to
        # the right, the left arm folded across in front of the face, its forearm pointing the same way, the face bowed
        # into the crook of the left elbow (it presses in there: free)
        K(0, raz=4, laz=4, rel=8, lel=8, lean=0, hx=0, hy=0, hz=0, tilt=0, twist=0, bob=0, ray=0, lay=0),
        K(0.15, rax=-25, lax=-25, raz=10, laz=10, rel=40, lel=40, lean=-6, bob=-0.12),
        K(0.24, rax=-6, raz=142, ray=0, rel=0, lax=136, laz=-30, lay=25, lel=32, lean=12, twist=12, tilt=6, hx=30, hy=-32, hz=10,
          bob=0.04, rkn=10, lkn=4),
        K(0.7, rax=-5, raz=144, ray=0, rel=0, lax=137, laz=-31, lay=25, lel=34, lean=13, twist=13, tilt=7, hx=32, hy=-33, hz=10, bob=0),
        K(0.85, raz=4, laz=4, rax=0, lax=0, rel=8, lel=8, ray=0, lay=0, lean=0, twist=0, tilt=0, hx=0, hy=0, hz=0, bob=0, rkn=0, lkn=0),
    ], note="a wind-up, then the dab snapped and held: one arm out and up, the face in the other's elbow", free=("left_arm",)),
    "jumping_jacks": Emote(0.8, [
        K(0, raz=6, laz=6, rel=6, lel=6, rlz=0, llz=0, bob=0, hx=0, rkn=0, lkn=0),
        K(0.12, raz=8, laz=8, bob=-0.12),
        K(0.25, raz=95, laz=95, rel=20, lel=20, rlz=10, llz=10, bob=0.35, hx=-6, rkn=24, lkn=24),
        K(0.42, raz=172, laz=172, rel=12, lel=12, rlz=16, llz=16, bob=0, rkn=4, lkn=4),
        K(0.5, raz=176, laz=176, rlz=16, llz=16, bob=-0.1),
        K(0.75, raz=95, laz=95, rel=20, lel=20, rlz=8, llz=8, bob=0.35, hx=-6, rkn=24, lkn=24),
        K(0.92, raz=6, laz=6, rel=6, lel=6, rlz=0, llz=0, bob=0, rkn=0, lkn=0),
    ], note="arms up and legs out, then back, hopping with bent knees"),
    "twist": Emote(0.6, [
        # elbows bent, forearms out in front, knees low and twisting
        K(0, rax=40, raz=38, rel=72, lax=40, laz=38, lel=72, twist=35, yaw=-8, hy=-20, rlz=6, llz=-4, rkn=30, lkn=40, bob=-0.05),
        K(0.25, twist=0, yaw=0, hy=0, rkn=44, lkn=44, bob=-0.14),
        K(0.5, rax=40, raz=38, rel=72, lax=40, laz=38, lel=72, twist=-35, yaw=8, hy=20, rlz=-4, llz=6, rkn=40, lkn=30, bob=-0.05),
        K(0.75, twist=0, yaw=0, hy=0, rkn=44, lkn=44, bob=-0.14),
    ], note="the body twists left and right, bent arms, knees low and bouncing"),
    "obby_hop": Emote(1.0, [
        K(0, rax=0, lax=0, raz=6, laz=6, rel=8, lel=8, lean=0, bob=0, rlx=0, llx=0, rkn=0, lkn=0, hx=0),
        K(0.18, rax=-45, lax=-45, rel=20, lel=20, lean=18, bob=-0.3, hx=10),
        # the hop: knees tucked high, arms thrown up
        K(0.38, rax=150, lax=150, raz=15, laz=15, rel=20, lel=20, lean=-5, bob=0.75, rlx=60, llx=40, rkn=95, lkn=80, hx=-12),
        K(0.55, rax=110, lax=110, rel=30, lel=30, lean=0, bob=0.6, rlx=25, llx=45, rkn=40, lkn=70),
        K(0.72, rax=60, lax=60, raz=30, laz=30, rel=40, lel=40, lean=14, bob=-0.22, rlx=0, llx=0, rkn=0, lkn=0, hx=8),
        K(0.88, rax=10, lax=10, raz=10, laz=10, rel=12, lel=12, lean=2, bob=0, hx=0),
    ], note="crouch, a big parkour hop with knees tucked and arms swinging, a soft landing"),
    "victory_dance": Emote(0.6, [
        K(0, rax=15, raz=150, rel=40, lax=-15, laz=150, lel=40, bob=0, hx=-10, twist=10, rkn=16, lkn=4),
        K(0.25, rax=0, raz=166, rel=4, lax=0, laz=166, lel=4, bob=0.18, hx=-18, twist=0, rkn=8, lkn=8),
        K(0.5, rax=-15, raz=150, rel=40, lax=15, laz=150, lel=40, bob=0, hx=-10, twist=-10, rkn=4, lkn=16),
        K(0.75, rax=0, raz=166, rel=4, lax=0, laz=166, lel=4, bob=0.18, hx=-18, twist=0, rkn=8, lkn=8),
    ], note="arms up in a V, pumping from the elbows, bouncing, looking up"),
    "clap": Emote(0.5, [
        # (2.0.0-test6, creator: "Should not snap back, should be a smooth clap back and front")
        K(0, hx=4, bob=0, rkn=4, lkn=4, rax=45.0, raz=29.0, ray=50.5, rel=92.1, lax=45.0, laz=29.0, lay=50.5, lel=92.1),
        K(0.5, hx=10, bob=-0.05, rkn=10, lkn=10, rax=70.4, raz=-32.7, ray=50.1, rel=20.1, lax=70.4, laz=-32.7, lay=50.1, lel=20.1),
    ], note="hands clapping together in front of the chest and apart again, smoothly, with a little nod and bounce", free=("right_arm", "left_arm")),
    "salute": Emote(1.8, [
        # (2.0.0-test5, redone from the creator's notes on test4: TODO.md, Emotes to redo)
        K(0, lean=0, tilt=0, hx=0, hz=0, bob=0, rax=0, raz=4, ray=0, rel=6, lax=0, laz=4, lay=0, lel=6),
        K(0.12, lean=0, tilt=0, hx=0, hz=0, rax=0, raz=4, ray=0, rel=6),
        K(0.22, bob=0.04, lean=-5, tilt=5, hx=-4, hz=4, rax=140.6, raz=-28.7, ray=87.6, rel=14.0),
        K(0.26, bob=0.02, lean=-5, tilt=5, hx=-4, hz=4, rax=140.6, raz=-28.7, ray=87.6, rel=14.0),
        K(0.72, bob=0.02, lean=-5, tilt=5, hx=-4, hz=4, rax=140.6, raz=-28.7, ray=87.6, rel=14.0),
        K(0.8, lean=-3, tilt=2, hx=-2, hz=2, rax=45.0, raz=64.8, ray=-6.6, rel=67.0),
        K(0.92, lean=0, tilt=0, hx=0, hz=0, bob=0, rax=0, raz=4, ray=0, rel=6, lax=0, laz=4, lay=0, lel=6),
    ], note="the fingertips snapped up to the right brow, elbow out, chest up, held, then away", free=("right_arm",)),
    "shrug": Emote(1.4, [
        K(0, rax=0, raz=4, rel=6, lax=0, laz=4, lel=6, bob=0, hz=0, lean=0, hy=0),
        # elbows at the sides, forearms out, palms up
        K(0.18, rax=20, raz=32, rel=70, lax=20, laz=32, lel=70, bob=0.07, hz=16, lean=-6, hy=-8),
        K(0.55, rax=22, raz=36, rel=74, lax=22, laz=36, lel=74, bob=0.07, hz=18, lean=-7, hy=-8),
        K(0.72, rax=0, raz=4, rel=6, lax=0, laz=4, lel=6, bob=0, hz=0, lean=0, hy=0),
    ], note="elbows in, forearms out, palms up, shoulders up, head tilted"),
    "chicken": Emote(0.6, [
        # hands tucked up in front of the shoulders, elbows out and flapping; a knee comes up in turn
        K(0, rax=40, raz=90, rel=120, lax=40, laz=90, lel=120, hx=-12, lean=4, rlx=0, llx=0, rkn=4, lkn=4, bob=0),
        K(0.25, rax=40, raz=58, rel=120, lax=40, laz=58, lel=120, hx=22, lean=14, rlx=40, rkn=70, bob=0.06),
        K(0.5, rax=40, raz=90, rel=120, lax=40, laz=90, lel=120, hx=-12, lean=4, rlx=0, llx=0, rkn=4, lkn=4, bob=0),
        K(0.75, rax=40, raz=58, rel=120, lax=40, laz=58, lel=120, hx=22, lean=14, llx=40, lkn=70, bob=0.06),
    ], note="hands tucked up in front of the shoulders, elbows flapping, pecking head, knees up in turns"),
    "disco": Emote(1.0, [
        # (2.0.0-test5, redone from the creator's notes on test4: TODO.md, Emotes to redo)
        K(0, tilt=-8, twist=8, yaw=10, hx=-22, hy=12, rlz=8, rkn=4, lkn=16, bob=0, rax=173.1, raz=33.3, ray=0.0, rel=0.0, lax=-13.4, laz=25.3, lay=65.5, lel=70.5),
        K(0.2, bob=-0.06, rax=173.1, raz=33.3, ray=0.0, rel=0.0, lax=-13.4, laz=25.3, lay=65.5, lel=70.5),
        K(0.5, tilt=8, twist=-22, yaw=-8, hx=24, hy=-16, rlz=0, llz=6, rkn=16, lkn=4, bob=-0.04, rax=42.1, raz=-33.4, ray=0.0, rel=0.0, lax=-13.4, laz=25.3, lay=65.5, lel=70.5),
        K(0.7, bob=-0.1, rax=42.1, raz=-33.4, ray=0.0, rel=0.0, lax=-13.4, laz=25.3, lay=65.5, lel=70.5),
    ], note="point up high, then right down across to the other hip and back up, the other hand on the hip, hips swinging", free=("right_arm", "left_arm")),
    "running_man": Emote(0.7, [
        K(0, rlx=50, rkn=90, llx=-25, lkn=20, rax=-40, rel=85, lax=50, lel=90, raz=8, laz=8, lean=8, bob=0.12, hx=4),
        K(0.25, rlx=0, rkn=10, llx=0, lkn=10, rax=0, rel=80, lax=0, lel=80, bob=-0.05),
        K(0.5, rlx=-25, rkn=20, llx=50, lkn=90, rax=50, rel=90, lax=-40, lel=85, lean=8, bob=0.12),
        K(0.75, rlx=0, rkn=10, llx=0, lkn=10, rax=0, rel=80, lax=0, lel=80, bob=-0.05),
    ], note="running on the spot, knees high, arms pumping with bent elbows"),
    "karate": Emote(2.4, [
        # (2.0.0-test5, redone from the creator's notes on test4: TODO.md, Emotes to redo)
        K(0, bob=0, rkn=4, lkn=4, rlz=0, llz=0, lean=0, hx=0, rax=0, raz=4, ray=0, rel=6, lax=0, laz=4, lay=0, lel=6),
        K(0.08, bob=-0.3, rkn=46, lkn=46, rlx=22, llx=22, lean=12, hx=6, rax=-170.4, raz=25.0, ray=23.5, rel=39.0, lax=-94.0, laz=16.7, lay=-2.8, lel=104.1),
        K(0.16, bob=0.8, rkn=60, lkn=60, rlx=30, llx=30, lean=-4, hx=-10, rax=-170.4, raz=25.0, ray=23.5, rel=39.0, lax=-94.0, laz=16.7, lay=-2.8, lel=104.1),
        K(0.24, bob=-0.25, rkn=34, lkn=34, rlz=20, llz=20, rlx=0, llx=0, lean=14, hx=10, twist=-10, rax=-78.4, raz=175.4, ray=-0.8, rel=41.7, lax=-94.0, laz=16.7, lay=-2.8, lel=104.1),
        K(0.4, bob=-0.25, rkn=34, lkn=34, rlz=20, llz=20, lean=14, hx=10, twist=-10, rax=-78.4, raz=175.4, ray=-0.8, rel=41.7, lax=-94.0, laz=16.7, lay=-2.8, lel=104.1),
        K(0.5, bob=0, rkn=4, lkn=4, rlz=0, llz=0, lean=0, hx=0, twist=0, rax=0, raz=4, ray=0, rel=6, lax=0, laz=4, lay=0, lel=6),
        K(0.58, bob=-0.3, rkn=46, lkn=46, rlx=22, llx=22, lean=12, hx=6, rax=-94.0, raz=16.7, ray=-2.8, rel=104.1, lax=-170.4, laz=25.0, lay=23.5, lel=39.0),
        K(0.66, bob=0.8, rkn=60, lkn=60, rlx=30, llx=30, lean=-4, hx=-10, rax=-94.0, raz=16.7, ray=-2.8, rel=104.1, lax=-170.4, laz=25.0, lay=23.5, lel=39.0),
        K(0.74, bob=-0.25, rkn=34, lkn=34, rlz=20, llz=20, rlx=0, llx=0, lean=14, hx=10, twist=10, rax=-94.0, raz=16.7, ray=-2.8, rel=104.1, lax=-78.4, laz=175.4, lay=-0.8, lel=41.7),
        K(0.9, bob=-0.25, rkn=34, lkn=34, rlz=20, llz=20, lean=14, hx=10, twist=10, rax=-94.0, raz=16.7, ray=-2.8, rel=104.1, lax=-78.4, laz=175.4, lay=-0.8, lel=41.7),
    ], note="a crouch, a jumping chop: the hand high, landing in a wide stance with the chop down in front, the other fist at the hip"),
    "air_guitar": Emote(0.5, [
        # (2.0.0-test6: the strums on the beat, for its music: emote_music.py)
        K(0, hx=26, lean=-6, rlz=10, llz=10, rkn=24, lkn=24, bob=0, rax=39.9, raz=-46.3, ray=61.5, rel=14.0, lax=-5.4, laz=21.1, lay=-39.4, lel=100.1),
        K(0.25, hx=2, bob=-0.06, rax=28.9, raz=-33.4, ray=67.8, rel=14.0, lax=-5.4, laz=21.1, lay=-39.4, lel=100.1),
        K(0.5, hx=26, bob=0, rax=39.9, raz=-46.3, ray=61.5, rel=14.0, lax=-5.4, laz=21.1, lay=-39.4, lel=100.1),
        K(0.75, hx=2, bob=-0.06, rax=28.9, raz=-33.4, ray=67.8, rel=14.0, lax=-5.4, laz=21.1, lay=-39.4, lel=100.1),
    ], note="the left hand out on the neck, the right forearm across the body strumming, head banging, rocking on bent knees", free=("right_arm",)),
    "drum": Emote(1.6, [
        # (2.0.0-test5, redone from the creator's notes on test4: TODO.md, Emotes to redo)
        K(0, hx=14, lean=10, bob=-0.06, rkn=14, lkn=14, rax=43.9, raz=-46.9, ray=56.4, rel=14.0, lax=48.6, laz=-1.8, lay=63.5, lel=83.0),
        K(0.0625, hx=10, rax=71.2, raz=-54.2, ray=44.0, rel=14.5, lax=29.2, laz=-5.5, lay=65.8, lel=64.5),
        K(0.125, hx=14, rax=43.9, raz=-46.9, ray=56.4, rel=14.0, lax=48.6, laz=-1.8, lay=63.5, lel=83.0),
        K(0.1875, hx=10, rax=71.2, raz=-54.2, ray=44.0, rel=14.5, lax=29.2, laz=-5.5, lay=65.8, lel=64.5),
        K(0.25, hx=14, twist=-6, rax=43.9, raz=-46.9, ray=56.4, rel=14.0, lax=47.3, laz=54.9, lay=47.0, lel=130.2),
        K(0.3125, hx=10, rax=64.9, raz=-53.5, ray=46.3, rel=14.0, lax=16.2, laz=51.5, lay=44.1, lel=129.3),
        K(0.375, hx=14, rax=43.9, raz=-46.9, ray=56.4, rel=14.0, lax=47.3, laz=54.9, lay=47.0, lel=130.2),
        K(0.4375, hx=10, twist=-6, rax=64.9, raz=-53.5, ray=46.3, rel=14.0, lax=16.2, laz=51.5, lay=44.1, lel=129.3),
        K(0.5, twist=14, hy=10, hx=16, rax=17.8, raz=11.4, ray=66.2, rel=57.4, lax=42.5, laz=-1.7, lay=63.9, lel=81.8),
        K(0.5625, twist=10, rax=16.1, raz=30.4, ray=60.7, rel=96.3, lax=29.2, laz=-5.5, lay=65.8, lel=64.5),
        K(0.625, twist=14, rax=17.8, raz=11.4, ray=66.2, rel=57.4, lax=42.5, laz=-1.7, lay=63.9, lel=81.8),
        K(0.6875, twist=18, hy=14, rax=16.1, raz=30.4, ray=60.7, rel=96.3, lax=26.8, laz=-45.4, lay=62.5, lel=14.0),
        K(0.75, twist=0, hy=0, hx=6, bob=0.02, rax=78.1, raz=-54.4, ray=42.2, rel=15.0, lax=56.1, laz=-1.8, lay=63.4, lel=83.2),
        K(0.82, twist=12, hy=12, hx=-10, bob=-0.08, rax=108.6, raz=27.0, ray=75.3, rel=68.8, lax=29.2, laz=-5.5, lay=65.8, lel=64.5),
        K(0.9, twist=8, hx=-6, bob=-0.04, rax=127.8, raz=7.1, ray=82.9, rel=35.7, lax=48.6, laz=-1.8, lay=63.5, lel=83.0),
    ], note="at a drum kit with sticks: beats on the snare and hi-hat, a roll round the toms, a crash", free=("right_arm", "left_arm")),
    "boxing": Emote(0.7, [
        # guard up (elbows bent, fists in front of the chest), a jab and a cross snapping straight
        K(0, rax=60, raz=-14, rel=100, lax=66, laz=-12, lel=104, lean=8, bob=0, twist=0, rlx=-10, llx=12, rkn=22, lkn=16),
        K(0.12, lax=88, laz=-2, lel=6, twist=-14, bob=0.04),
        K(0.2, lax=66, laz=-12, lel=104, twist=0),
        K(0.45, rax=90, raz=0, rel=6, twist=22, lean=14, bob=-0.05, rkn=34),
        K(0.58, rax=60, raz=-14, rel=100, twist=0, lean=8, bob=0.04, rkn=22),
        K(0.7, lax=88, laz=-2, lel=6, twist=-14),
        K(0.8, lax=66, laz=-12, lel=104, twist=0, bob=0),
    ], note="guard up, jab, cross with the hips behind it, jab, light on bent knees"),
    "spin": Emote(1.2, [
        K(0, yaw=0, raz=88, laz=88, rel=10, lel=10, hz=0, bob=0, rkn=6, lkn=6),
        K(0.5, hz=10, bob=0.12, rel=24, lel=24),
        K(1, yaw=360, raz=88, laz=88, rel=10, lel=10, hz=0, bob=0),
    ], linear=("yaw",), note="a full turn with arms out"),
    "helicopter": Emote(0.6, [
        K(0, yaw=0, raz=92, laz=92, rax=8, lax=-8, rel=4, lel=4, bob=0.1, rlz=8, llz=8, rkn=20, lkn=20),
        K(0.5, rax=-8, lax=8, bob=0.16, rkn=34, lkn=34),
        K(1, yaw=360, rax=8, lax=-8, bob=0.1, rkn=20, lkn=20),
    ], linear=("yaw",), note="arms out, spinning fast, lifting off, knees tucking"),
    "hip_shake": Emote(0.5, [
        # hands on the hips, the hips going side to side over bending knees
        K(0, rax=-10, raz=30, rel=96, lax=-10, laz=30, lel=96, tilt=-11, yaw=8, rlz=8, llz=-4, hz=7, rkn=6, lkn=26, bob=0),
        K(0.25, tilt=0, yaw=0, rlz=2, llz=2, hz=0, rkn=20, lkn=20, bob=-0.06),
        K(0.5, tilt=11, yaw=-8, rlz=-4, llz=8, hz=-7, rkn=26, lkn=6, bob=0),
        K(0.75, tilt=0, yaw=0, rlz=2, llz=2, hz=0, rkn=20, lkn=20, bob=-0.06),
    ], note="hands on the hips, hips side to side"),
    "swim": Emote(1.4, [
        # (2.0.0-test5, redone from the creator's notes on test4: TODO.md, Emotes to redo.) 2.0.0-test6.1 (creator: "the
        # swim emote has some glitches with the arms"): the same poses, but each arm's X carries on round (a full turn
        # back a loop: wrap) and the arm over the top is written x + 180, 180 - z, y + 180 (the same arm): it was written
        # over the side, so the blends between keys passed the arm straight out sideways, where its angles swing wildly
        # for a small move, and the forearm whipped round. The reaching arm turned a little squarer to the body (Y 175,
        # was 163: its corner by the shoulder brushed the torso between two of the game's rows).
        K(0, lean=22, hy=0, rlx=12, llx=-12, rkn=10, lkn=24, rax=141.3, raz=17.3, ray=175.0, rel=9.4, lax=-33.7, laz=23.9, lay=169.1, lel=23.5),
        K(0.125, hy=-16, rax=106.4, raz=26.9, ray=178.9, rel=26.3, lax=-60.9, laz=58.8, lay=173.3, lel=17.1),
        K(0.25, hy=-30, rlx=-12, llx=12, rkn=24, lkn=10, rax=76.0, raz=33.7, ray=181.4, rel=37.8, lax=-163.3, laz=58.4, lay=215.7, lel=96.9),
        K(0.375, hy=-16, rax=-10.3, raz=55.1, ray=158.8, rel=21.8, lax=-198.4, laz=37.2, lay=172.0, lel=38.2),
        K(0.5, hy=0, rlx=12, llx=-12, rkn=10, lkn=24, rax=-33.7, raz=23.9, ray=169.1, rel=23.5, lax=-218.7, laz=17.3, lay=175.0, lel=9.4),
        K(0.625, hy=16, rax=-60.9, raz=58.8, ray=173.3, rel=17.1, lax=-253.6, laz=26.9, lay=178.9, lel=26.3),
        K(0.75, hy=30, rlx=-12, llx=12, rkn=24, lkn=10, rax=-163.3, raz=58.4, ray=215.7, rel=96.9, lax=-284.0, laz=33.7, lay=181.4, lel=37.8),
        K(0.875, hy=16, rax=-198.4, raz=37.2, ray=172.0, rel=38.2, lax=-370.3, laz=55.1, lay=158.8, lel=21.8),
    ], note="front crawl in the air: reach, pull, push past the hip, the elbow high coming over, turning to breathe", wrap={"rax": -360, "lax": -360}),
    "zombie": Emote(1.4, [
        # stiff arms out in front, wrists limp (a little elbow), knees bent and dragging
        K(0, rax=92, raz=6, rel=14, lax=86, laz=6, lel=8, hz=22, hx=10, lean=10, yaw=-5, rlx=14, llx=-6, rkn=10, lkn=28, bob=0),
        K(0.25, rax=86, lax=92, rel=8, lel=14, hz=14, rkn=20, lkn=20, bob=-0.06),
        K(0.5, rax=92, lax=86, rel=14, lel=8, hz=26, yaw=5, rlx=-6, llx=14, rkn=28, lkn=10, bob=0),
        K(0.75, rax=86, lax=92, rel=8, lel=14, hz=18, rkn=20, lkn=20, bob=-0.06),
    ], note="arms out, head lolling, shuffling on dragging knees"),
    "flex": Emote(1.6, [
        # (2.0.0-test5, redone from the creator's notes on test4: TODO.md, Emotes to redo)
        K(0, lean=0, bob=0, hx=0, twist=0, hy=0, rax=0, raz=4, ray=0, rel=6, lax=0, laz=4, lay=0, lel=6),
        K(0.12, lean=-6, bob=0.05, hx=-8, rax=116.6, raz=83.6, ray=37.0, rel=77.8, lax=116.6, laz=83.6, lay=37.0, lel=77.8),
        K(0.24, lean=-7, bob=0.07, hx=-10, rax=135.0, raz=80.4, ray=58.9, rel=84.5, lax=135.0, laz=80.4, lay=58.9, lel=84.5),
        K(0.36, lean=-6, bob=0.05, hx=-8, rax=116.6, raz=83.6, ray=37.0, rel=77.8, lax=116.6, laz=83.6, lay=37.0, lel=77.8),
        K(0.52, twist=-28, hy=24, lean=-3, bob=0.03, hx=-4, rkn=14, lkn=4, rax=93.4, raz=30.4, ray=15.3, rel=75.7, lax=44.5, laz=-4.5, lay=70.6, lel=61.0),
        K(0.64, twist=-30, hy=26, lean=-4, bob=0.05, hx=-6, rkn=14, lkn=4, rax=93.4, raz=30.4, ray=15.3, rel=75.7, lax=44.5, laz=-4.5, lay=70.6, lel=61.0),
        K(0.78, twist=0, hy=0, lean=-7, bob=0.07, hx=-10, rkn=0, lkn=0, rax=135.0, raz=80.4, ray=58.9, rel=84.5, lax=135.0, laz=80.4, lay=58.9, lel=84.5),
        K(0.92, lean=0, bob=0, hx=0, twist=0, hy=0, rax=0, raz=4, ray=0, rel=6, lax=0, laz=4, lay=0, lel=6),
    ], note="double biceps, a pulse, a side flex, double biceps: just flexing", free=("left_arm",)),
    "bow": Emote(2.0, [
        K(0, rax=0, raz=4, rel=6, lax=0, laz=4, lel=6, lean=0, hx=0),
        # the right forearm across the front of the waist, the left arm a little back, then a bow
        K(0.15, rax=35, raz=0, rel=110, lax=-15, laz=5, lel=10),
        K(0.38, lean=26, hx=24, bob=-0.05, rkn=6, lkn=6),
        K(0.62, lean=28, hx=26, bob=-0.05),
        K(0.82, rax=35, raz=0, rel=110, lean=0, hx=0, bob=0, rkn=0, lkn=0),
        K(0.95, rax=0, raz=4, rel=6, lax=0, laz=4, lel=6),
    ], note="a hand across the waist, the other back, a polite bow"),
    "cheer": Emote(0.5, [
        K(0, rax=10, raz=168, rel=8, lax=30, laz=20, lel=70, bob=0.12, hx=-14, tilt=-5, rkn=20, lkn=4),
        K(0.25, bob=0, rel=40, lel=40),
        K(0.5, lax=10, laz=168, lel=8, rax=30, raz=20, rel=70, bob=0.12, hx=-14, tilt=5, rkn=4, lkn=20),
        K(0.75, bob=0, rel=40, lel=40),
    ], note="fists up in turns, bouncing on bent knees"),
    "raise_roof": Emote(0.8, [
        # (2.0.0-test5, redone from the creator's notes on test4: TODO.md, Emotes to redo)
        K(0, bob=-0.12, hx=4, rkn=26, lkn=26, rax=90.0, raz=87.1, ray=2.6, rel=85.3, lax=90.0, laz=87.1, lay=2.6, lel=85.3),
        K(0.25, bob=0.35, hx=-14, rkn=6, lkn=6, rax=177.0, raz=12.8, ray=89.3, rel=2.4, lax=177.0, laz=12.8, lay=89.3, lel=2.4),
        K(0.5, bob=-0.12, hx=4, rkn=26, lkn=26, rax=90.0, raz=87.1, ray=2.6, rel=85.3, lax=90.0, laz=87.1, lay=2.6, lel=85.3),
        K(0.75, bob=0.35, hx=-14, rkn=6, lkn=6, rax=177.0, raz=12.8, ray=89.3, rel=2.4, lax=177.0, laz=12.8, lay=89.3, lel=2.4),
    ], note="palms up overhead, pumping the arms straight up and down, jumping on each push"),
    "penguin": Emote(0.5, [
        # stiff straight arms and legs, little flaps and a waddle
        K(0, raz=30, laz=8, rel=0, lel=0, tilt=-10, yaw=10, rlx=8, llx=-4, hz=8, bob=0.04),
        K(0.25, raz=10, laz=10, tilt=0, yaw=0, rlx=0, llx=0, hz=0, bob=0),
        K(0.5, raz=8, laz=30, tilt=10, yaw=-10, rlx=-4, llx=8, hz=-8, bob=0.04),
        K(0.75, raz=10, laz=10, tilt=0, yaw=0, rlx=0, llx=0, hz=0, bob=0),
    ], note="little flaps and a waddle, stiff as a penguin"),
    "slide": Emote(3.2, [
        # (2.0.0-test5, redone from the creator's notes on test4: TODO.md, Emotes to redo)
        K(0.0, sx=-0.9, rlz=2.9, llz=2.9, rkn=10, lkn=10, tilt=-6, hy=-14, lean=2, bob=0, rax=12.5, raz=20.8, ray=6.4, rel=18.6, lax=12.5, laz=20.8, lay=6.4, lel=18.6),
        K(0.125, sx=-0.9, rlz=26.6, llz=2.9, rkn=10, lkn=10, tilt=-6, hy=-14, lean=2, bob=0, rax=12.5, raz=20.8, ray=6.4, rel=18.6, lax=12.5, laz=20.8, lay=6.4, lel=18.6),
        K(0.25, sx=0.0, rlz=2.9, llz=2.9, rkn=10, lkn=10, tilt=-6, hy=-14, lean=2, bob=0, rax=12.5, raz=20.8, ray=6.4, rel=18.6, lax=12.5, laz=20.8, lay=6.4, lel=18.6),
        K(0.375, sx=0.0, rlz=26.6, llz=2.9, rkn=10, lkn=10, tilt=-6, hy=-14, lean=2, bob=0, rax=12.5, raz=20.8, ray=6.4, rel=18.6, lax=12.5, laz=20.8, lay=6.4, lel=18.6),
        K(0.5, sx=0.9, rlz=2.9, llz=2.9, rkn=10, lkn=10, tilt=6, hy=14, lean=2, bob=0, rax=12.5, raz=20.8, ray=6.4, rel=18.6, lax=12.5, laz=20.8, lay=6.4, lel=18.6),
        K(0.625, sx=0.9, rlz=2.9, llz=26.6, rkn=10, lkn=10, tilt=6, hy=14, lean=2, bob=0, rax=12.5, raz=20.8, ray=6.4, rel=18.6, lax=12.5, laz=20.8, lay=6.4, lel=18.6),
        K(0.75, sx=0.0, rlz=2.9, llz=2.9, rkn=10, lkn=10, tilt=6, hy=14, lean=2, bob=0, rax=12.5, raz=20.8, ray=6.4, rel=18.6, lax=12.5, laz=20.8, lay=6.4, lel=18.6),
        K(0.875, sx=0.0, rlz=2.9, llz=26.6, rkn=10, lkn=10, tilt=6, hy=14, lean=2, bob=0, rax=12.5, raz=20.8, ray=6.4, rel=18.6, lax=12.5, laz=20.8, lay=6.4, lel=18.6),
    ], note="a foot steps out to the side, the other drags along the floor to it, twice, then back the other way"),
    "fly_pose": Emote(1.5, [
        # one fist up, the other at the hip, knees bent back, hovering
        K(0, rax=180, raz=-6, rel=0, lax=-18, laz=30, lel=96, rlx=-8, llx=-18, rkn=30, lkn=60, lean=8, hx=-10, bob=0.35),
        K(0.5, rax=176, lax=-24, rlx=-14, llx=-10, rkn=40, lkn=50, lean=10, hx=-14, bob=0.5),
    ], note="one fist up, the other at the hip, hovering"),
    "star_jump": Emote(1.0, [
        K(0, raz=6, laz=6, rel=6, lel=6, rlz=0, llz=0, lean=0, bob=0, hx=0),
        K(0.2, rax=-30, lax=-30, raz=10, laz=10, rel=30, lel=30, lean=16, bob=-0.3, hx=8),
        K(0.42, rax=0, lax=0, raz=145, laz=145, rel=4, lel=4, rlz=28, llz=28, lean=-4, bob=0.95, hx=-14),
        K(0.58, raz=150, laz=150, rlz=30, llz=30, bob=0.9),
        K(0.78, raz=40, laz=40, rel=30, lel=30, rlz=4, llz=4, lean=12, bob=-0.2, hx=6),
        K(0.92, raz=6, laz=6, rel=6, lel=6, rlz=0, llz=0, lean=0, bob=0, hx=0),
    ], note="crouch, a big jumping star, land on bent knees"),
    "hula": Emote(1.2, [
        # (2.0.0-test5, redone from the creator's notes on test4: TODO.md, Emotes to redo)
        K(0.0, sx=0.0, rlz=-0.0, llz=0.0, rlx=-6.3, llx=-6.3, tilt=-0.0, lean=-5.0, rkn=8, lkn=8, hz=0.0, rax=-13.4, raz=25.3, ray=65.5, rel=70.5, lax=-13.4, laz=25.3, lay=65.5, lel=70.5),
        K(0.125, sx=0.2, rlz=-5.7, llz=5.7, rlx=-4.4, llx=-4.4, tilt=-4.9, lean=-3.5, rkn=8, lkn=8, hz=2.8, rax=-13.4, raz=25.3, ray=65.5, rel=70.5, lax=-13.4, laz=25.3, lay=65.5, lel=70.5),
        K(0.25, sx=0.28, rlz=-8.0, llz=8.0, rlx=-0.0, llx=-0.0, tilt=-7.0, lean=-0.0, rkn=8, lkn=8, hz=4.0, rax=-13.4, raz=25.3, ray=65.5, rel=70.5, lax=-13.4, laz=25.3, lay=65.5, lel=70.5),
        K(0.375, sx=0.2, rlz=-5.7, llz=5.7, rlx=4.4, llx=4.4, tilt=-4.9, lean=3.5, rkn=8, lkn=8, hz=2.8, rax=-13.4, raz=25.3, ray=65.5, rel=70.5, lax=-13.4, laz=25.3, lay=65.5, lel=70.5),
        K(0.5, sx=0.0, rlz=-0.0, llz=0.0, rlx=6.3, llx=6.3, tilt=-0.0, lean=5.0, rkn=8, lkn=8, hz=0.0, rax=-13.4, raz=25.3, ray=65.5, rel=70.5, lax=-13.4, laz=25.3, lay=65.5, lel=70.5),
        K(0.625, sx=-0.2, rlz=5.7, llz=-5.7, rlx=4.4, llx=4.4, tilt=4.9, lean=3.5, rkn=8, lkn=8, hz=-2.8, rax=-13.4, raz=25.3, ray=65.5, rel=70.5, lax=-13.4, laz=25.3, lay=65.5, lel=70.5),
        K(0.75, sx=-0.28, rlz=8.0, llz=-8.0, rlx=0.0, llx=0.0, tilt=7.0, lean=0.0, rkn=8, lkn=8, hz=-4.0, rax=-13.4, raz=25.3, ray=65.5, rel=70.5, lax=-13.4, laz=25.3, lay=65.5, lel=70.5),
        K(0.875, sx=-0.2, rlz=5.7, llz=-5.7, rlx=-4.4, llx=-4.4, tilt=4.9, lean=-3.5, rkn=8, lkn=8, hz=-2.8, rax=-13.4, raz=25.3, ray=65.5, rel=70.5, lax=-13.4, laz=25.3, lay=65.5, lel=70.5),
    ], note="hands on the hips, the hips circling round and round, shoulders level", free=("right_arm", "left_arm")),
    "wave_both": Emote(0.8, [
        # (2.0.0-test6, creator: "Arms should bend side to side, not rotate front and back": only the elbows move)
        K(0, hz=-6, bob=0, rkn=4, lkn=4, rax=-7.1, raz=130.2, ray=-94.2, rel=66.0, lax=-7.1, laz=130.2, lay=-94.2, lel=66.0),
        K(0.25, hz=0, bob=0.06, rax=-7.1, raz=130.2, ray=-94.2, rel=8.0, lax=-7.1, laz=130.2, lay=-94.2, lel=8.0),
        K(0.5, hz=6, bob=0, rax=-7.1, raz=130.2, ray=-94.2, rel=66.0, lax=-7.1, laz=130.2, lay=-94.2, lel=66.0),
        K(0.75, hz=0, bob=0.06, rax=-7.1, raz=130.2, ray=-94.2, rel=8.0, lax=-7.1, laz=130.2, lay=-94.2, lel=8.0),
    ], note="both arms up and out, the forearms swinging side to side from the elbows, swaying"),
    "rock_out": Emote(1.8, [
        # (2.0.0-test5: playing a real guitar (props.py guitar); 2.0.0-test6: the strums on the beat, for its music)
        K(0, rlz=10, llz=10, hx=26, lean=8, bob=-0.1, rkn=26, lkn=26, rax=36.2, raz=-12.4, ray=57.2, rel=14.0, lax=26.6, laz=-6.2, lay=37.7, lel=79.5),
        K(0.0625, hx=2, lean=4, bob=0, rkn=12, lkn=12, rax=36.7, raz=14.1, ray=59.5, rel=64.6, lax=26.6, laz=-6.2, lay=37.7, lel=79.5),
        K(0.125, hx=26, lean=8, bob=-0.1, rkn=26, lkn=26, rax=36.2, raz=-12.4, ray=57.2, rel=14.0, lax=26.6, laz=-6.2, lay=37.7, lel=79.5),
        K(0.1875, hx=2, lean=4, bob=0, rkn=12, lkn=12, rax=36.7, raz=14.1, ray=59.5, rel=64.6, lax=26.6, laz=-6.2, lay=37.7, lel=79.5),
        K(0.25, hx=26, lean=8, bob=-0.1, rkn=26, lkn=26, rax=36.2, raz=-12.4, ray=57.2, rel=14.0, lax=26.6, laz=-6.2, lay=37.7, lel=79.5),
        K(0.3125, hx=2, lean=4, bob=0, rkn=12, lkn=12, rax=36.7, raz=14.1, ray=59.5, rel=64.6, lax=26.6, laz=-6.2, lay=37.7, lel=79.5),
        K(0.375, hx=26, lean=8, bob=-0.1, rkn=26, lkn=26, rax=36.2, raz=-12.4, ray=57.2, rel=14.0, lax=26.6, laz=-6.2, lay=37.7, lel=79.5),
        K(0.5, hx=-20, lean=-8, bob=0.04, rkn=6, lkn=6, rax=172.8, raz=17.4, ray=47.9, rel=6.2, lax=21.6, laz=5.8, lay=29.4, lel=95.8),
        K(0.625, hx=-24, lean=-10, bob=0.06, rkn=4, lkn=4, rax=172.8, raz=17.4, ray=47.9, rel=6.2, lax=21.6, laz=5.8, lay=29.4, lel=95.8),
        K(0.75, hx=32, lean=14, bob=-0.18, rkn=36, lkn=36, rax=36.2, raz=-12.4, ray=57.2, rel=14.0, lax=26.6, laz=-6.2, lay=37.7, lel=79.5),
        K(0.8125, hx=4, lean=6, bob=-0.04, rkn=16, lkn=16, rax=36.7, raz=14.1, ray=59.5, rel=64.6, lax=26.6, laz=-6.2, lay=37.7, lel=79.5),
        K(0.875, hx=26, lean=8, bob=-0.1, rkn=26, lkn=26, rax=36.2, raz=-12.4, ray=57.2, rel=14.0, lax=26.6, laz=-6.2, lay=37.7, lel=79.5),
        K(0.9375, hx=2, lean=4, bob=0, rkn=12, lkn=12, rax=36.7, raz=14.1, ray=59.5, rel=64.6, lax=26.6, laz=-6.2, lay=37.7, lel=79.5),
    ], note="strumming hard on the guitar, the left hand on its neck, head banging; then the arm thrown up high and brought down in a big sweep onto the strings", free=("right_arm", "left_arm")),
    "tap_dance": Emote(0.5, [
        # quick feet: a foot kicks forward and taps, the knee bending as it comes back
        K(0, rlx=28, rkn=6, llx=0, lkn=14, raz=28, laz=22, rel=20, lel=20, twist=6, bob=0.05, hx=-4),
        K(0.12, rlx=10, rkn=50),
        K(0.25, rlx=0, rkn=14, llx=0, lkn=14, bob=0),
        K(0.5, rlx=0, rkn=14, llx=28, lkn=6, raz=22, laz=28, twist=-6, bob=0.05),
        K(0.62, llx=10, lkn=50),
        K(0.75, rlx=0, rkn=14, llx=0, lkn=14, bob=0),
    ], note="quick feet with bending knees, arms out for balance"),
    "lasso": Emote(0.6, [
        # the rope hand circles overhead; the other hand on the hip
        # (2.0.0-test5, with the rope (props.py lasso): the arm higher and straighter, so the loop spins above the head's
        # right instead of through the head)
        K(0, rax=22, raz=158, rel=12, lax=-25, laz=38, lel=100, hx=-22, bob=0, hy=0, rkn=10, lkn=10),
        K(0.25, rax=0, raz=166, rel=18, hy=8, bob=0.03),
        K(0.5, rax=-22, raz=158, rel=12, hy=0, bob=0),
        K(0.75, rax=0, raz=150, rel=6, hy=-8, bob=0.03),
    ], note="a rope twirled overhead, the other hand on the hip"),
    # ---- v2.0.0 (creator: "a Griddy emote based on the viral Griddy dance"; "some OG fortnite emotes (boneless,
    # orange justice, defult dance, etc.) ... in the locker shop"): bought with gold (emotes sheet unlock shop) ----
    "griddy": Emote(1.6, [
        # (2.0.0-test5, redone from the creator's notes on test4: TODO.md, Emotes to redo)
        K(0, rlx=34, rkn=0, llx=-6, lkn=24, lean=-5, bob=-0.05, hz=4, hx=-4, rax=-30.5, raz=5.8, ray=-178.5, rel=28.9, lax=53.1, laz=5.7, lay=-3.0, lel=54.1),
        K(0.125, rlx=4, llx=4, rkn=16, lkn=16, bob=0.04, rax=-30.5, raz=5.8, ray=-178.5, rel=28.9, lax=53.1, laz=5.7, lay=-3.0, lel=54.1),
        K(0.25, llx=34, lkn=0, rlx=-6, rkn=24, lean=-5, bob=-0.05, hz=-4, hx=-4, rax=53.1, raz=5.7, ray=-3.0, rel=54.1, lax=-30.5, laz=5.8, lay=-178.5, lel=28.9),
        K(0.375, rlx=4, llx=4, rkn=16, lkn=16, bob=0.04, rax=53.1, raz=5.7, ray=-3.0, rel=54.1, lax=-30.5, laz=5.8, lay=-178.5, lel=28.9),
        K(0.5, rlx=34, rkn=0, llx=-6, lkn=24, bob=-0.05, hz=0, lean=8, hx=8, rax=154.1, raz=-31.9, ray=83.0, rel=14.0, lax=154.1, laz=-31.9, lay=83.0, lel=14.0),
        K(0.625, rlx=4, llx=4, rkn=16, lkn=16, bob=0.04, lean=8, hx=8, rax=154.1, raz=-31.9, ray=83.0, rel=14.0, lax=154.1, laz=-31.9, lay=83.0, lel=14.0),
        K(0.75, llx=34, lkn=0, rlx=-6, rkn=24, bob=-0.05, lean=8, hx=8, rax=154.1, raz=-31.9, ray=83.0, rel=14.0, lax=154.1, laz=-31.9, lay=83.0, lel=14.0),
        K(0.875, rlx=4, llx=4, rkn=16, lkn=16, bob=0.04, lean=-5, hx=-4, rax=-30.5, raz=5.8, ray=-178.5, rel=28.9, lax=53.1, laz=5.7, lay=-3.0, lel=54.1),
    ], note="heels tapped out in front in turn, arms swinging, then both hands up round the eyes like goggles", free=("right_arm", "left_arm")),
    "default_dance": Emote(2.0, [
        # arms swung to one side and the other with a bounce, then pumped up and down in front, then a kick back
        K(0, rax=40, raz=-6, rel=60, lax=10, laz=40, lel=40, tilt=-6, twist=-10, rlz=6, llz=-2, rkn=10, lkn=16, bob=-0.06),
        K(0.125, rax=10, raz=40, rel=40, lax=40, laz=-6, lel=60, tilt=6, twist=10, rlz=-2, llz=6, rkn=16, lkn=10, bob=-0.06),
        K(0.25, rax=40, raz=-6, rel=60, lax=10, laz=40, lel=40, tilt=-6, twist=-10, rlz=6, llz=-2, rkn=10, lkn=16, bob=-0.06),
        K(0.375, rax=10, raz=40, rel=40, lax=40, laz=-6, lel=60, tilt=6, twist=10, rlz=-2, llz=6, rkn=16, lkn=10, bob=-0.06),
        K(0.5, rax=95, raz=20, rel=100, lax=20, laz=20, lel=40, tilt=0, twist=0, rlz=0, llz=0, rkn=20, lkn=4, bob=-0.08, hx=6),
        K(0.5625, rax=20, raz=20, rel=40, lax=95, laz=20, lel=100, rkn=4, lkn=20, bob=0.02, hx=-4),
        K(0.625, rax=95, raz=20, rel=100, lax=20, laz=20, lel=40, rkn=20, lkn=4, bob=-0.08, hx=6),
        K(0.6875, rax=20, raz=20, rel=40, lax=95, laz=20, lel=100, rkn=4, lkn=20, bob=0.02, hx=-4),
        K(0.8, rax=60, raz=10, rel=20, lax=60, laz=10, lel=20, rlx=-40, rkn=70, llx=6, lkn=14, lean=12, bob=-0.04, hx=0),
        K(0.9, rax=20, raz=12, rel=30, lax=20, laz=12, lel=30, rlx=0, rkn=8, llx=0, lkn=8, lean=2, bob=0),
    ], note="arms swung side to side on a bounce, pumped up and down, then a kick back"),
    "orange_justice": Emote(1.0, [
        # (2.0.0-test5, redone from the creator's notes on test4: TODO.md, Emotes to redo)
        K(0, llz=28, lkn=6, rlz=0, rkn=26, tilt=8, twist=10, lean=12, bob=-0.06, hx=6, rax=29.1, raz=58.8, ray=0.0, rel=0.0, lax=36.3, laz=-20.6, lay=74.7, lel=6.4),
        K(0.25, llz=0, rlz=0, rkn=30, lkn=30, tilt=0, twist=0, lean=16, bob=-0.16, hx=10, rax=30.5, raz=5.8, ray=24.4, rel=7.0, lax=30.5, laz=5.8, lay=24.4, lel=7.0),
        K(0.5, rlz=28, rkn=6, llz=0, lkn=26, tilt=-8, twist=-10, lean=12, bob=-0.06, hx=6, rax=36.3, raz=-20.6, ray=74.7, rel=6.4, lax=29.1, laz=58.8, lay=0.0, lel=0.0),
        K(0.75, llz=0, rlz=0, rkn=30, lkn=30, tilt=0, twist=0, lean=16, bob=-0.16, hx=10, rax=30.5, raz=5.8, ray=24.4, rel=7.0, lax=30.5, laz=5.8, lay=24.4, lel=7.0),
    ], note="knees bent and bouncing, both arms swinging together side to side across the front, a foot kicking out sideways in turn", free=("right_arm", "left_arm")),
    "boneless": Emote(2.4, [
        # (2.0.0-test5, redone from the creator's notes on test4: TODO.md, Emotes to redo)
        K(0, flip=0, lean=0, bob=0, hx=0, rkn=4, lkn=4, rlx=0, llx=0, tilt=0, twist=0, rax=0, raz=4, ray=0, rel=6, lax=0, laz=4, lay=0, lel=6),
        K(0.1, lean=12, tilt=10, bob=-0.6, hx=30, hz=14, rkn=70, lkn=62, rlx=22, llx=18, rax=6.3, raz=23.8, ray=165.3, rel=18.5, lax=6.3, laz=23.8, lay=165.3, lel=18.5),
        K(0.2, flip=78, lean=0, tilt=0, bob=-1.4, hx=-20, rkn=10, lkn=30, rlx=-10, llx=10, rax=76.0, raz=65.4, ray=37.8, rel=27.1, lax=-59.0, laz=126.1, lay=51.2, lel=32.4),
        K(0.3, flip=86, twist=16, tilt=8, hz=10, rkn=40, lkn=6, rlx=10, llx=-14, rax=76.0, raz=65.4, ray=37.8, rel=27.1, lax=-59.0, laz=126.1, lay=51.2, lel=32.4),
        K(0.4, flip=86, twist=-16, tilt=-8, hz=-10, rkn=6, lkn=40, rlx=-14, llx=10, rax=-59.0, raz=126.1, ray=51.2, rel=32.4, lax=76.0, laz=65.4, lay=37.8, lel=27.1),
        K(0.5, flip=86, twist=16, tilt=8, hz=10, rkn=40, lkn=6, rlx=10, llx=-14, rax=76.0, raz=65.4, ray=37.8, rel=27.1, lax=-59.0, laz=126.1, lay=51.2, lel=32.4),
        K(0.6, flip=86, twist=-16, tilt=-8, hz=-10, rkn=6, lkn=40, rlx=-14, llx=10, rax=-59.0, raz=126.1, ray=51.2, rel=32.4, lax=76.0, laz=65.4, lay=37.8, lel=27.1),
        K(0.7, flip=86, twist=10, tilt=4, hz=6, rkn=30, lkn=20, rlx=4, llx=-4, rax=76.0, raz=65.4, ray=37.8, rel=27.1, lax=-59.0, laz=126.1, lay=51.2, lel=32.4),
        K(0.82, flip=20, twist=0, tilt=0, hz=0, lean=14, bob=-0.6, hx=24, rkn=76, lkn=76, rlx=30, llx=30, rax=6.3, raz=23.8, ray=165.3, rel=18.5, lax=6.3, laz=23.8, lay=165.3, lel=18.5),
        K(0.94, flip=0, lean=0, bob=0, hx=0, rkn=4, lkn=4, rlx=0, llx=0, rax=0, raz=4, ray=0, rel=6, lax=0, laz=4, lay=0, lel=6),
    ], note="going limp, dropping to the floor and flopping about on it like there are no bones, then back up", plant="touch", free=("right_arm", "left_arm")),
    "take_the_l": Emote(1.0, [
        # an L on the forehead, the legs kicked out to the sides in turn on little hops
        K(0, rax=130.8, raz=35.7, ray=82.3, rel=65.6, lax=-20, laz=20, lel=60, rlz=34, rkn=4, llz=-2, lkn=18, yaw=20, bob=0.02, hx=-6, hy=-10),
        K(0.25, rlz=4, llz=4, rkn=14, lkn=14, bob=-0.05),
        K(0.5, llz=34, lkn=4, rlz=-2, rkn=18, bob=0.02),
        K(0.75, rlz=4, llz=4, rkn=14, lkn=14, bob=-0.05),
    ], note="an L on the forehead, legs kicked out to the sides in turn"),
    "electro_shuffle": Emote(1.0, [
        # feet shuffling out to the sides, forearms pumping up and down in front in turn, shoulders bouncing
        K(0, rax=27.9, raz=14.6, ray=-8.5, rel=109.7, lax=21.3, laz=14.5, lay=-3.2, lel=79.3, rlz=20, rkn=8, llz=0, lkn=22, tilt=4, bob=-0.05),
        K(0.25, rax=21.3, raz=14.5, ray=-3.2, rel=79.3, lax=27.9, laz=14.6, lay=-8.5, lel=109.7, rlz=0, rkn=22, llz=20, lkn=8, tilt=-4, bob=0.0),
        K(0.5, rax=27.9, raz=14.6, ray=-8.5, rel=109.7, lax=21.3, laz=14.5, lay=-3.2, lel=79.3, rlz=20, rkn=8, llz=0, lkn=22, tilt=4, bob=-0.05),
        K(0.75, rax=21.3, raz=14.5, ray=-3.2, rel=79.3, lax=27.9, laz=14.6, lay=-8.5, lel=109.7, rlz=0, rkn=22, llz=20, lkn=8, tilt=-4, bob=0.0),
    ], note="feet shuffling out to the sides, forearms pumping in front in turn"),
    "fresh": Emote(1.6, [
        # (2.0.0-test5, redone from the creator's notes on test4: TODO.md, Emotes to redo)
        K(0, sx=0.12, tilt=6, twist=-10, rlz=4, llz=-4, rkn=8, lkn=20, bob=-0.04, hy=-10, rax=36.7, raz=-42.6, ray=59.7, rel=14.0, lax=-16.8, laz=10.8, lay=-20.4, lel=85.6),
        K(0.15, sx=-0.12, tilt=-6, twist=10, rlz=-4, llz=4, rkn=20, lkn=8, bob=-0.04, hy=10, rax=-16.8, raz=10.8, ray=-20.4, rel=85.6, lax=36.7, laz=-42.6, lay=59.7, lel=14.0),
        K(0.3, sx=0.12, tilt=6, twist=-10, rlz=4, llz=-4, rkn=8, lkn=20, bob=-0.04, hy=-10, rax=36.7, raz=-42.6, ray=59.7, rel=14.0, lax=-16.8, laz=10.8, lay=-20.4, lel=85.6),
        K(0.45, sx=-0.12, tilt=-6, twist=10, rlz=-4, llz=4, rkn=20, lkn=8, bob=-0.04, hy=10, rax=-16.8, raz=10.8, ray=-20.4, rel=85.6, lax=36.7, laz=-42.6, lay=59.7, lel=14.0),
        K(0.62, sx=0.0, tilt=0, twist=-14, rlz=0, llz=0, rlx=18, rkn=30, lkn=6, bob=0.02, hy=-34, hz=6, rax=51.4, raz=40.9, ray=46.6, rel=102.4, lax=33.5, laz=22.0, lay=61.4, lel=74.8),
        K(0.72, twist=-16, rlx=0, rkn=6, bob=-0.02, hy=-36, rax=51.4, raz=40.9, ray=46.6, rel=102.4, lax=33.5, laz=22.0, lay=61.4, lel=74.8),
        K(0.84, twist=14, llx=18, lkn=30, rkn=6, bob=0.02, hy=34, hz=-6, rax=51.4, raz=40.9, ray=46.6, rel=102.4, lax=33.5, laz=22.0, lay=61.4, lel=74.8),
        K(0.94, sx=0.12, tilt=6, twist=-10, rlz=4, llz=-4, llx=0, rkn=8, lkn=20, bob=-0.04, hy=-10, hz=0, rax=36.7, raz=-42.6, ray=59.7, rel=14.0, lax=-16.8, laz=10.8, lay=-20.4, lel=85.6),
    ], note="the Carlton: arms swinging together side to side at the waist, the hips the other way, then a step and a snap with a head turn", free=("right_arm", "left_arm")),
    "hype": Emote(0.8, [
        # fists thrust up overhead in turn, knees pumping, bouncing
        K(0, rax=172, raz=10, rel=8, lax=20, laz=20, lel=90, rlx=0, rkn=10, llx=40, lkn=60, bob=0.04, hx=-10),
        K(0.25, rax=30, raz=20, rel=80, lax=30, laz=20, lel=80, rlx=0, llx=0, rkn=24, lkn=24, bob=-0.08, hx=4),
        K(0.5, lax=172, laz=10, lel=8, rax=20, raz=20, rel=90, llx=0, lkn=10, rlx=40, rkn=60, bob=0.04, hx=-10),
        K(0.75, rax=30, raz=20, rel=80, lax=30, laz=20, lel=80, rlx=0, llx=0, rkn=24, lkn=24, bob=-0.08, hx=4),
    ], note="fists thrust up overhead in turn, knees pumping"),
    "breakdance": Emote(1.2, [
        # (creator, test4: "Should spin on its head upside-down") a headspin: the whole body over (flip), the top of the
        # head on the floor (plant: touch), spinning twice a loop, legs up in a V scissoring a little, arms out low
        K(0, flip=180, yaw=0, rax=0, raz=104, rel=15, lax=0, laz=104, lel=15, rlz=30, llz=30, rlx=6, llx=-6, rkn=15, lkn=15),
        K(0.5, yaw=360, rlx=-6, llx=6, rlz=34, llz=26),
        K(1, flip=180, yaw=720, rax=0, raz=104, rel=15, lax=0, laz=104, lel=15, rlz=30, llz=30, rlx=6, llx=-6, rkn=15, lkn=15),
    ], linear=("yaw",), note="a headspin: upside down on its head, legs in a V", plant="touch", lift=0.05),
    # (2.0.0-test6, creator: "Breakdance emote: head should not go through the mat": the head on top of the cardboard,
    # 0.05 up, over its tape; in the game a hat or hair on the head lifts it further, RobloxAvatarRenderer.restOnHat)
    "ripple": Emote(1.4, [
        # (2.0.0-test5, redone from the creator's notes on test4: TODO.md, Emotes to redo)
        K(0, tilt=0, hz=0, bob=0, rkn=8, lkn=8, rax=90.0, raz=87.1, ray=0.0, rel=0.0, lax=25.6, laz=83.4, lay=-63.5, lel=39.9),
        K(0.1, tilt=0, hz=0, rax=90.0, raz=87.1, ray=0.0, rel=0.0, lax=-7.6, laz=112.2, lay=82.1, lel=34.0),
        K(0.2, tilt=-8, hz=-10, bob=0.05, rax=90.0, raz=87.1, ray=0.0, rel=0.0, lax=-19.8, laz=98.5, lay=70.3, lel=6.0),
        K(0.3, tilt=8, hz=10, bob=0.05, rax=-19.8, raz=98.5, ray=70.3, rel=6.0, lax=90.0, laz=87.1, lay=0.0, lel=0.0),
        K(0.4, tilt=0, hz=0, bob=0, rax=-7.6, raz=112.2, ray=82.1, rel=34.0, lax=90.0, laz=87.1, lay=0.0, lel=0.0),
        K(0.5, tilt=0, hz=0, rax=25.6, raz=83.4, ray=-63.5, rel=39.9, lax=90.0, laz=87.1, lay=0.0, lel=0.0),
        K(0.6, tilt=0, hz=0, rax=-7.6, raz=112.2, ray=82.1, rel=34.0, lax=90.0, laz=87.1, lay=0.0, lel=0.0),
        K(0.7, tilt=8, hz=10, bob=0.05, rax=-19.8, raz=98.5, ray=70.3, rel=6.0, lax=90.0, laz=87.1, lay=0.0, lel=0.0),
        K(0.8, tilt=-8, hz=-10, bob=0.05, rax=90.0, raz=87.1, ray=0.0, rel=0.0, lax=-19.8, laz=98.5, lay=70.3, lel=6.0),
        K(0.9, tilt=0, hz=0, bob=0, rax=90.0, raz=87.1, ray=0.0, rel=0.0, lax=-7.6, laz=112.2, lay=82.1, lel=34.0),
    ], note="the arm wave: a ripple running from one hand up the arm, across the shoulders and out to the other hand, and back"),
    "legendary": Emote(2.0, [
        K(0, yaw=0, raz=6, laz=6, rel=6, lel=6, bob=0, lean=0, hx=0, rlz=0, llz=0, rkn=0, lkn=0),
        K(0.15, rax=-30, lax=-30, raz=12, laz=12, rel=30, lel=30, bob=-0.35, lean=18, hx=10),
        K(0.3, rax=0, lax=0, raz=160, laz=160, rel=6, lel=6, bob=1.1, lean=-6, hx=-20, rlz=14, llz=14, rkn=60, lkn=60),
        K(0.45, yaw=360, raz=168, laz=168, bob=1.0, rkn=30, lkn=30),
        K(0.58, raz=120, laz=120, rel=40, lel=40, bob=-0.25, lean=12, rlz=8, llz=8, rkn=0, lkn=0, hx=6),
        K(0.7, raz=172, laz=172, rax=10, lax=10, rel=4, lel=4, bob=0.12, lean=-8, hx=-25, rlz=10, llz=10),
        K(0.86, raz=168, laz=168, bob=0.05, hx=-22),
        K(1, yaw=360, raz=6, laz=6, rax=0, lax=0, rel=6, lel=6, bob=0, lean=0, hx=0, rlz=0, llz=0, rkn=0, lkn=0),
    ], note="the 1000-kill emote: crouch, a spinning jump with knees tucked, land, victory pose"),
}
