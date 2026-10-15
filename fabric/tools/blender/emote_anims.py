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
    def __init__(self, loop, keys, linear=(), note="", free=(), plant="floor"):
        # free: limbs allowed to go into the body (only where the creator chose it: the Floss's arms, "Full reach");
        # plant: "floor" (feet kept out of the floor) or "touch" (the lowest point of the body on it: a headspin)
        self.loop, self.keys, self.linear, self.note, self.free, self.plant = loop, keys, set(linear), note, tuple(free), plant


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
        # the upper arm out to the side, the forearm up, waving from the elbow
        K(0, rax=90, raz=96, rel=74, laz=6, lel=10, hz=8, hy=-6, tilt=-4, bob=0, rkn=4, lkn=4),
        K(0.25, raz=102, rel=112, hz=4, bob=0.02),
        K(0.5, raz=96, rel=74, hz=8, bob=0),
        K(0.75, raz=102, rel=112, hz=4, bob=0.02),
    ], note="right arm up, waving from the elbow, head tilted with a smile"),
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
    "clap": Emote(0.45, [
        # elbows in, forearms up and in front: the hands meet at chest height
        K(0, rax=40, raz=30, rel=62, lax=40, laz=30, lel=62, hx=4, bob=0, rkn=4, lkn=4),
        K(0.42, rax=44, raz=-12, rel=70, lax=44, laz=-12, lel=70, hx=10, bob=-0.05),
        K(0.5, rax=44, raz=-14, rel=70, lax=44, laz=-14, lel=70, hx=10, bob=-0.05),
        K(0.62, rax=42, raz=14, rel=66, lax=42, laz=14, lel=66, hx=6, bob=0),
    ], note="hands meet in front with a little nod"),
    "salute": Emote(1.8, [
        K(0, rax=0, raz=4, rel=6, laz=4, lel=6, lean=0, hx=0, bob=0),
        K(0.12, rax=0, raz=4, rel=6, lean=0, hx=0),
        # the arm snapped up, the hand by the brow (as close as an R6 arm gets: rig.py); standing tall
        K(0.22, rax=128, raz=-15, rel=6, lel=0, laz=2, lean=-6, hx=-6, bob=0.04),
        K(0.26, rax=126, raz=-15, rel=4, lean=-5, hx=-5, bob=0.02),
        K(0.72, rax=127, raz=-15, rel=5, lean=-5, hx=-5),
        K(0.8, rax=110, raz=10, rel=40, lean=-3, hx=-3),
        K(0.92, rax=0, raz=4, rel=6, laz=4, lel=6, lean=0, hx=0, bob=0),
    ], note="snaps the right hand up to the brow, chest out, holds, then away"),
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
    "disco": Emote(0.9, [
        # pointing up and down across with a straight arm; the other hand on the hip
        K(0, rax=35, raz=140, rel=0, lax=-25, laz=40, lel=100, tilt=-9, yaw=12, hx=-22, hy=12, rlz=8, rkn=4, lkn=18, bob=0),
        K(0.25, bob=-0.1),
        K(0.5, rax=45, raz=-28, rel=0, lax=-25, laz=40, lel=100, tilt=9, yaw=-12, hx=22, hy=-12, rlz=0, llz=4, rkn=18, lkn=4, bob=0),
        K(0.75, bob=-0.1),
    ], note="point up, point down across, hand on the hip, hips swinging, eyes on the finger"),
    "running_man": Emote(0.7, [
        K(0, rlx=50, rkn=90, llx=-25, lkn=20, rax=-40, rel=85, lax=50, lel=90, raz=8, laz=8, lean=8, bob=0.12, hx=4),
        K(0.25, rlx=0, rkn=10, llx=0, lkn=10, rax=0, rel=80, lax=0, lel=80, bob=-0.05),
        K(0.5, rlx=-25, rkn=20, llx=50, lkn=90, rax=50, rel=90, lax=-40, lel=85, lean=8, bob=0.12),
        K(0.75, rlx=0, rkn=10, llx=0, lkn=10, rax=0, rel=80, lax=0, lel=80, bob=-0.05),
    ], note="running on the spot, knees high, arms pumping with bent elbows"),
    "karate": Emote(1.2, [
        # horse stance (knees bent, legs out), guard up, the hand winds up high and chops down
        K(0, rax=40, raz=-14, rel=70, lax=40, laz=-14, lel=70, rlz=16, llz=16, rkn=30, lkn=30, bob=-0.12, twist=0, hy=0),
        K(0.18, rax=150, raz=-20, rel=90, twist=-12, hy=-10, lean=-4),
        K(0.26, rax=72, raz=-12, rel=4, twist=18, hy=8, lean=10, bob=-0.18),
        K(0.45, rax=40, raz=-14, rel=70, lax=40, laz=-14, lel=70, twist=0, hy=0, lean=0, bob=-0.12),
        K(0.68, lax=150, laz=-20, lel=90, twist=12, hy=10, lean=-4),
        K(0.76, lax=72, laz=-12, lel=4, twist=-18, hy=-8, lean=10, bob=-0.18),
        K(0.95, rax=40, raz=-14, rel=70, lax=40, laz=-14, lel=70, twist=0, hy=0, lean=0, bob=-0.12),
    ], note="horse stance, wind up high, chop, then the other hand"),
    "air_guitar": Emote(0.5, [
        # the left hand out along the neck, the right strumming across the hips from the elbow; knees bent, rocking
        K(0, lax=72, laz=34, lel=10, rax=20, raz=-30, rel=70, hx=28, lean=-6, rlz=10, llz=10, rkn=24, lkn=24, bob=0),
        K(0.2, rax=34, raz=-26, rel=40, hx=0, bob=-0.06),
        K(0.5, rax=20, raz=-30, rel=70, hx=28, bob=0),
        K(0.7, rax=34, raz=-26, rel=40, hx=0, bob=-0.06),
    ], note="one hand on the neck, strumming, head banging, leaning back on bent knees"),
    "drum": Emote(0.4, [
        # elbows bent, the sticks beating down from the elbows in turn
        K(0, rax=30, raz=12, rel=40, lax=22, laz=12, lel=90, hx=12, bob=-0.04, twist=-5, rkn=12, lkn=12),
        K(0.5, rax=22, raz=12, rel=90, lax=30, laz=12, lel=40, hx=4, bob=0, twist=5),
    ], note="drumming fast, sticks in turn from the elbows, head bobbing"),
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
    "swim": Emote(1.0, [
        # windmill front crawl: the elbow bends high as each arm comes over
        K(0, rax=0, lax=180, rel=10, lel=10, lean=22, hy=0, rlx=15, llx=-15, rkn=10, lkn=30, raz=10, laz=10),
        K(0.25, hy=35, rlx=-15, llx=15, rkn=30, lkn=10, rel=60, lel=20),
        K(0.5, hy=0, rlx=15, llx=-15, rkn=10, lkn=30, rel=10, lel=10),
        K(0.75, hy=-35, rlx=-15, llx=15, rkn=30, lkn=10, rel=20, lel=60),
        K(1, rax=360, lax=540, rel=10, lel=10, hy=0, rlx=15, llx=-15, rkn=10, lkn=30),
    ], linear=("rax", "lax"), note="front crawl in the air: windmill arms, turning to breathe, legs fluttering"),
    "zombie": Emote(1.4, [
        # stiff arms out in front, wrists limp (a little elbow), knees bent and dragging
        K(0, rax=92, raz=6, rel=14, lax=86, laz=6, lel=8, hz=22, hx=10, lean=10, yaw=-5, rlx=14, llx=-6, rkn=10, lkn=28, bob=0),
        K(0.25, rax=86, lax=92, rel=8, lel=14, hz=14, rkn=20, lkn=20, bob=-0.06),
        K(0.5, rax=92, lax=86, rel=14, lel=8, hz=26, yaw=5, rlx=-6, llx=14, rkn=28, lkn=10, bob=0),
        K(0.75, rax=86, lax=92, rel=8, lel=14, hz=18, rkn=20, lkn=20, bob=-0.06),
    ], note="arms out, head lolling, shuffling on dragging knees"),
    "flex": Emote(1.2, [
        K(0, raz=4, laz=4, rax=0, lax=0, rel=6, lel=6, lean=0, bob=0, hx=0),
        # double biceps: upper arms out level, forearms up, fists by the ears
        K(0.14, rax=90, raz=96, rel=110, lax=90, laz=96, lel=110, lean=-6, bob=0.05, hx=-8),
        K(0.42, rax=90, raz=100, rel=122, lax=90, laz=100, lel=122, lean=-7, bob=0.05, hx=-8),
        # the most-muscular crunch: fists together low in front, leaning in on bent knees
        K(0.55, rax=40, raz=0, rel=60, lax=40, laz=0, lel=60, lean=16, bob=-0.14, hx=10, rkn=10, lkn=10),
        K(0.82, rax=41, raz=0, rel=64, lax=41, laz=0, lel=64, lean=17, bob=-0.14, hx=10),
        K(0.94, raz=4, laz=4, rax=0, lax=0, rel=6, lel=6, lean=0, bob=0, hx=0, rkn=0, lkn=0),
    ], note="double biceps, then the most-muscular crunch"),
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
    "raise_roof": Emote(0.9, [
        # (creator, test4: "Looks like it's flexing muscles") palms up, pushing the roof: from hands up in front of the
        # shoulders, elbows out and a little forward (a flex has them level out to the sides), the arms push up into a
        # V overhead, twice a loop, bouncing on the beat (both ends and the way between clear of the body)
        K(0, rax=69.5, raz=58.6, ray=-1.7, rel=84.8, lax=69.5, laz=58.6, lay=-1.7, lel=84.8, bob=-0.1, hx=6, lean=4, rkn=14, lkn=14),
        K(0.25, rax=-6.1, raz=165, ray=-1.6, rel=2.8, lax=-6.1, laz=165, lay=-1.6, lel=2.8, bob=0.03, hx=-14, lean=-4, rkn=2, lkn=2),
        K(0.5, rax=69.5, raz=58.6, ray=-1.7, rel=84.8, lax=69.5, laz=58.6, lay=-1.7, lel=84.8, bob=-0.1, hx=6, lean=4, rkn=14, lkn=14),
        K(0.75, rax=-6.1, raz=165, ray=-1.6, rel=2.8, lax=-6.1, laz=165, lay=-1.6, lel=2.8, bob=0.03, hx=-14, lean=-4, rkn=2, lkn=2),
    ], note="palms up, pushing the roof up overhead, bouncing"),
    "penguin": Emote(0.5, [
        # stiff straight arms and legs, little flaps and a waddle
        K(0, raz=30, laz=8, rel=0, lel=0, tilt=-10, yaw=10, rlx=8, llx=-4, hz=8, bob=0.04),
        K(0.25, raz=10, laz=10, tilt=0, yaw=0, rlx=0, llx=0, hz=0, bob=0),
        K(0.5, raz=8, laz=30, tilt=10, yaw=-10, rlx=-4, llx=8, hz=-8, bob=0.04),
        K(0.75, raz=10, laz=10, tilt=0, yaw=0, rlx=0, llx=0, hz=0, bob=0),
    ], note="little flaps and a waddle, stiff as a penguin"),
    "slide": Emote(0.9, [
        # one foot slides out as that knee straightens, the other knee bends; arms ease with soft elbows
        K(0, rlz=26, llz=0, rkn=0, lkn=36, rax=-15, lax=25, raz=20, laz=10, rel=20, lel=40, tilt=-8, yaw=12, hy=-10, bob=-0.08),
        K(0.25, rlz=4, llz=4, rkn=16, lkn=16, rax=0, lax=0, raz=10, laz=10, rel=24, lel=24, tilt=0, yaw=0, hy=0, bob=0),
        K(0.5, rlz=0, llz=26, rkn=36, lkn=0, rax=25, lax=-15, raz=10, laz=20, rel=40, lel=20, tilt=8, yaw=-12, hy=10, bob=-0.08),
        K(0.75, rlz=4, llz=4, rkn=16, lkn=16, rax=0, lax=0, raz=10, laz=10, rel=24, lel=24, tilt=0, yaw=0, hy=0, bob=0),
    ], note="smooth sliding feet, knees taking turns, arms easing back and forth"),
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
    "hula": Emote(0.8, [
        K(0, rax=20, raz=95, rel=30, lax=40, laz=-15, lel=60, tilt=-12, rlz=10, llz=-4, hz=8, rkn=4, lkn=24, bob=0),
        K(0.25, rax=30, raz=60, rel=40, lax=30, laz=60, lel=40, tilt=0, rlz=3, llz=3, hz=0, rkn=16, lkn=16, bob=-0.05),
        K(0.5, rax=40, raz=-15, rel=60, lax=20, laz=95, lel=30, tilt=12, rlz=-4, llz=10, hz=-8, rkn=24, lkn=4, bob=0),
        K(0.75, rax=30, raz=60, rel=40, lax=30, laz=60, lel=40, tilt=0, rlz=3, llz=3, hz=0, rkn=16, lkn=16, bob=-0.05),
    ], note="arms flowing side to side from the elbows, hips swaying on soft knees"),
    "wave_both": Emote(0.8, [
        # both upper arms out to the sides, both forearms up, waving from the elbows
        K(0, rax=90, raz=100, rel=60, lax=90, laz=100, lel=110, hz=-8, bob=0, rkn=4, lkn=4),
        K(0.25, bob=0.06),
        K(0.5, rel=110, lel=60, hz=8, bob=0),
        K(0.75, bob=0.06),
    ], note="waving with both hands from the elbows, swaying"),
    "rock_out": Emote(0.45, [
        # the fist pumps from the elbow, head banging, knees bending
        K(0, rax=150, raz=-10, rel=10, lax=10, laz=25, lel=30, hx=-12, lean=-6, bob=0.04, rkn=8, lkn=8),
        K(0.45, rax=120, raz=-5, rel=70, hx=30, lean=10, bob=-0.1, rkn=26, lkn=26),
        K(0.55, rax=120, raz=-5, rel=70, hx=30, lean=10, bob=-0.1),
    ], note="fist pumping, head banging"),
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
        # the rope hand circles overhead from the elbow; the other hand on the hip
        K(0, rax=25, raz=158, rel=30, lax=-25, laz=38, lel=100, hx=-22, bob=0, hy=0, rkn=10, lkn=10),
        K(0.25, rax=0, raz=172, rel=50, hy=8, bob=0.03),
        K(0.5, rax=-25, raz=158, rel=30, hy=0, bob=0),
        K(0.75, rax=0, raz=144, rel=10, hy=-8, bob=0.03),
    ], note="a rope twirled overhead, the other hand on the hip"),
    "breakdance": Emote(1.2, [
        # (creator, test4: "Should spin on its head upside-down") a headspin: the whole body over (flip), the top of the
        # head on the floor (plant: touch), spinning twice a loop, legs up in a V scissoring a little, arms out low
        K(0, flip=180, yaw=0, rax=0, raz=104, rel=15, lax=0, laz=104, lel=15, rlz=30, llz=30, rlx=6, llx=-6, rkn=15, lkn=15),
        K(0.5, yaw=360, rlx=-6, llx=6, rlz=34, llz=26),
        K(1, flip=180, yaw=720, rax=0, raz=104, rel=15, lax=0, laz=104, lel=15, rlz=30, llz=30, rlx=6, llx=-6, rkn=15, lkn=15),
    ], linear=("yaw",), note="a headspin: upside down on its head, legs in a V", plant="touch"),
    "ripple": Emote(0.9, [
        # a wave rolls from one hand through the elbows and shoulders to the other
        K(0, laz=115, raz=10, lel=10, rel=60, tilt=-10, hz=10, rax=10, lax=10, bob=0),
        K(0.25, laz=60, raz=60, lel=60, rel=10, tilt=0, hz=0, bob=0.05),
        K(0.5, laz=10, raz=115, lel=60, rel=10, tilt=10, hz=-10, bob=0),
        K(0.75, laz=60, raz=60, lel=10, rel=60, tilt=0, hz=0, bob=0.05),
    ], note="a wave rolling through both arms and the body"),
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
