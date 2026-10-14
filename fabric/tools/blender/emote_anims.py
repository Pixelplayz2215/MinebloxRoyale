"""
The 40 emotes as key poses (creator: "cleaner and more expressive animations, use Blender to animate them"). Each is
a loop of key poses on the R6 avatar; emote_video.py makes them Blender keyframes (smooth Bezier curves between the
keys, cyclic), renders them and samples the curves into the emote_keys sheet, which the game plays.

Channels, in degrees (bob in studs), each 0 when not given:
  rax / lax  right / left arm forward (0 hanging, 90 pointing ahead, 180 straight up, below 0 back)
  raz / laz  right / left arm out to its own side (90 straight out; below 0 across the body)
  rlx / llx  right / left leg forward          rlz / llz  right / left leg out to its side
  yaw        the whole body turns               bob        the whole body lifts (studs)
  lean       above the hips: leans forward      tilt       ... leans to its right      twist  ... turns to its right
  hx         head nods down                     hy         head turns to its right     hz     head tilts to its right
Keys: K(t, ...) with t from 0 to 1 over the loop; a channel's value at t = 1 is its value at t = 0 unless given.
"""

REST = dict(raz=4, laz=4)


def K(t, **ch):
    return (t, ch)


class Emote:
    def __init__(self, loop, keys, linear=(), note=""):
        self.loop, self.keys, self.linear, self.note = loop, keys, set(linear), note


def mirror(ch):
    """The same pose, mirrored left to right."""
    out = {}
    swap = {"rax": "lax", "raz": "laz", "lax": "rax", "laz": "raz", "rlx": "llx", "llx": "rlx", "rlz": "llz", "llz": "rlz"}
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
    L = dict(tilt=7, twist=-10, rlz=4, llz=-2, hz=-6)  # arms to its left, hips to its right
    R = mirror(L)
    return [K(0, rax=22, raz=-38, lax=-22, laz=38, **L), K(0.25, rax=-22, raz=38, lax=22, laz=-38, **R),
            K(0.5, rax=-22, raz=-38, lax=22, laz=38, **L), K(0.75, rax=22, raz=38, lax=-22, laz=-38, **R), K(1, rax=22, raz=-38, lax=-22, laz=38, **L)]


def _robot():
    a = dict(rax=90, raz=0, lax=0, laz=4, hy=0, twist=0)
    b = dict(rax=0, raz=4, lax=90, laz=0, hy=-30, twist=-15)
    c = dict(rax=90, raz=-30, lax=90, laz=-30, hy=0, twist=0, bob=-0.06)
    d = dict(rax=0, raz=90, lax=0, laz=90, hy=30, twist=15)
    keys, t = [], 0.0
    for p in (a, b, c, d):  # snap (0.06) then hold
        keys += [K(t, **p), K(t + 0.19, **p)]
        t += 0.25
    return keys + [K(1, **a)]


EMOTES = {
    "wave": Emote(1.0, [
        K(0, rax=12, raz=132, laz=6, hz=8, hy=-6, tilt=-4, bob=0),
        K(0.25, raz=168, hz=4, bob=0.03),
        K(0.5, raz=132, hz=8, bob=0),
        K(0.75, raz=168, hz=4, bob=0.03),
    ], note="right arm up, waving, head tilted with a smile"),
    "swing": Emote(0.8, [
        K(0, rax=25, raz=70, lax=55, laz=-35, twist=22, tilt=6, hy=10, bob=0),
        K(0.25, rax=20, raz=10, lax=20, laz=10, twist=0, tilt=0, hy=0, bob=-0.1),
        K(0.5, rax=55, raz=-35, lax=25, laz=70, twist=-22, tilt=-6, hy=-10, bob=0),
        K(0.75, rax=20, raz=10, lax=20, laz=10, twist=0, tilt=0, hy=0, bob=-0.1),
    ], note="both arms swing side to side, dipping in the middle"),
    "robot": Emote(1.6, _robot(), note="stiff, snapping 90 degree moves with holds"),
    "floss": Emote(0.7, _floss(), note="straight arms swing across the body, front and back in turn, hips the other way"),
    "dab": Emote(1.6, [
        K(0, raz=4, laz=4, lean=0, hx=0, hy=0, hz=0, tilt=0, bob=0),
        K(0.15, rax=-25, lax=-25, raz=10, laz=10, lean=-6, bob=-0.08),
        K(0.24, rax=20, raz=138, lax=118, laz=-58, lean=12, tilt=10, hx=32, hy=-28, hz=12, bob=0.04),
        K(0.7, rax=22, raz=142, lax=120, laz=-56, lean=13, tilt=11, hx=34, hy=-28, hz=12, bob=0),
        K(0.85, raz=4, laz=4, rax=0, lax=0, lean=0, tilt=0, hx=0, hy=0, hz=0, bob=0),
    ], note="a wind-up, then the dab snapped and held"),
    "jumping_jacks": Emote(0.8, [
        K(0, raz=6, laz=6, rlz=0, llz=0, bob=0, hx=0),
        K(0.12, raz=8, laz=8, bob=-0.1),
        K(0.25, raz=95, laz=95, rlz=10, llz=10, bob=0.35, hx=-6),
        K(0.42, raz=172, laz=172, rlz=16, llz=16, bob=0),
        K(0.5, raz=176, laz=176, rlz=16, llz=16, bob=-0.08),
        K(0.75, raz=95, laz=95, rlz=8, llz=8, bob=0.35, hx=-6),
        K(0.92, raz=6, laz=6, rlz=0, llz=0, bob=0),
    ], note="arms up and legs out, then back, hopping"),
    "twist": Emote(0.6, [
        K(0, rax=65, raz=35, lax=65, laz=35, twist=35, yaw=-8, hy=-20, rlz=6, llz=-4, bob=-0.05),
        K(0.25, twist=0, yaw=0, hy=0, bob=-0.12),
        K(0.5, rax=65, raz=35, lax=65, laz=35, twist=-35, yaw=8, hy=20, rlz=-4, llz=6, bob=-0.05),
        K(0.75, twist=0, yaw=0, hy=0, bob=-0.12),
    ], note="the body twists left and right, bent arms, low bounce"),
    "obby_hop": Emote(1.0, [
        K(0, rax=0, lax=0, raz=6, laz=6, lean=0, bob=0, rlx=0, llx=0, hx=0),
        K(0.18, rax=-45, lax=-45, lean=18, bob=-0.25, hx=10),
        K(0.38, rax=150, lax=150, raz=15, laz=15, lean=-5, bob=0.75, rlx=35, llx=15, hx=-12),
        K(0.55, rax=110, lax=110, lean=0, bob=0.6, rlx=10, llx=30),
        K(0.72, rax=60, lax=60, raz=30, laz=30, lean=14, bob=-0.18, rlx=0, llx=0, hx=8),
        K(0.88, rax=10, lax=10, raz=10, laz=10, lean=2, bob=0, hx=0),
    ], note="crouch, a big parkour hop with arms swinging, a soft landing"),
    "victory_dance": Emote(0.6, [
        K(0, rax=15, raz=150, lax=-15, laz=150, bob=0, hx=-10, twist=10),
        K(0.25, rax=0, raz=162, lax=0, laz=162, bob=0.18, hx=-18, twist=0),
        K(0.5, rax=-15, raz=150, lax=15, laz=150, bob=0, hx=-10, twist=-10),
        K(0.75, rax=0, raz=162, lax=0, laz=162, bob=0.18, hx=-18, twist=0),
    ], note="arms up in a V, pumping, bouncing, looking up"),
    "clap": Emote(0.45, [
        K(0, rax=72, raz=28, lax=72, laz=28, hx=4, bob=0),
        K(0.42, rax=78, raz=-10, lax=78, laz=-10, hx=10, bob=-0.05),
        K(0.5, rax=78, raz=-12, lax=78, laz=-12, hx=10, bob=-0.05),
        K(0.62, rax=74, raz=12, lax=74, laz=12, hx=6, bob=0),
    ], note="hands meet in front with a little nod"),
    "salute": Emote(1.8, [
        K(0, rax=0, raz=4, laz=4, lean=0, hx=0, bob=0),
        K(0.12, rax=0, raz=4, lean=0, hx=0),
        K(0.22, rax=128, raz=-52, lean=-6, hx=-6, bob=0.04),
        K(0.26, rax=124, raz=-48, lean=-5, hx=-5, bob=0.02),
        K(0.72, rax=126, raz=-50, lean=-5, hx=-5),
        K(0.8, rax=150, raz=-30, lean=-3, hx=-3),
        K(0.92, rax=0, raz=4, lean=0, hx=0, bob=0),
    ], note="snaps the right hand to the forehead, chest out, holds, then away"),
    "shrug": Emote(1.4, [
        K(0, rax=0, raz=4, lax=0, laz=4, bob=0, hz=0, lean=0, hy=0),
        K(0.18, rax=35, raz=48, lax=35, laz=48, bob=0.07, hz=16, lean=-6, hy=-8),
        K(0.55, rax=38, raz=52, lax=38, laz=52, bob=0.07, hz=18, lean=-7, hy=-8),
        K(0.72, rax=0, raz=4, lax=0, laz=4, bob=0, hz=0, lean=0, hy=0),
    ], note="arms out, palms up, shoulders up, head tilted"),
    "chicken": Emote(0.6, [
        K(0, rax=-15, raz=55, lax=-15, laz=55, hx=-12, lean=4, rlx=0, llx=0, bob=0),
        K(0.25, rax=-10, raz=18, lax=-10, laz=18, hx=22, lean=14, rlx=25, bob=0.06),
        K(0.5, rax=-15, raz=55, lax=-15, laz=55, hx=-12, lean=4, rlx=0, llx=0, bob=0),
        K(0.75, rax=-10, raz=18, lax=-10, laz=18, hx=22, lean=14, llx=25, bob=0.06),
    ], note="elbows flapping, pecking head, knees up in turns"),
    "disco": Emote(0.9, [
        K(0, rax=35, raz=140, lax=-25, laz=40, tilt=-9, yaw=12, hx=-22, hy=12, rlz=8, bob=0),
        K(0.25, bob=-0.1),
        K(0.5, rax=45, raz=-28, lax=-25, laz=40, tilt=9, yaw=-12, hx=22, hy=-12, rlz=0, llz=4, bob=0),
        K(0.75, bob=-0.1),
    ], note="point up, point down across, hips swinging, eyes on the finger"),
    "running_man": Emote(0.7, [
        K(0, rlx=45, llx=-25, rax=-40, lax=50, raz=8, laz=8, lean=8, bob=0.12, hx=4),
        K(0.25, rlx=0, llx=0, rax=0, lax=0, bob=-0.05),
        K(0.5, rlx=-25, llx=45, rax=50, lax=-40, lean=8, bob=0.12),
        K(0.75, rlx=0, llx=0, rax=0, lax=0, bob=-0.05),
    ], note="running on the spot, knees high, arms pumping"),
    "karate": Emote(1.2, [
        K(0, rax=60, raz=-20, lax=60, laz=-20, rlz=14, llz=14, bob=-0.12, twist=0, hy=0),
        K(0.18, rax=165, raz=-25, twist=-12, hy=-10, lean=-4),
        K(0.26, rax=72, raz=-18, twist=18, hy=8, lean=10, bob=-0.18),
        K(0.45, rax=60, raz=-20, lax=60, laz=-20, twist=0, hy=0, lean=0, bob=-0.12),
        K(0.68, lax=165, laz=-25, twist=12, hy=10, lean=-4),
        K(0.76, lax=72, laz=-18, twist=-18, hy=-8, lean=10, bob=-0.18),
        K(0.95, rax=60, raz=-20, lax=60, laz=-20, twist=0, hy=0, lean=0, bob=-0.12),
    ], note="horse stance, wind up high, chop, then the other hand"),
    "air_guitar": Emote(0.5, [
        K(0, lax=75, laz=28, rax=30, raz=-35, hx=28, lean=-6, rlz=10, llz=10, bob=0),
        K(0.2, rax=58, raz=-30, hx=0, bob=-0.06),
        K(0.5, rax=30, raz=-35, hx=28, bob=0),
        K(0.7, rax=58, raz=-30, hx=0, bob=-0.06),
    ], note="one hand on the neck, strumming, head banging, leaning back"),
    "drum": Emote(0.4, [
        K(0, rax=78, raz=12, lax=42, laz=12, hx=12, bob=-0.04, twist=-5),
        K(0.5, rax=42, raz=12, lax=78, laz=12, hx=4, bob=0, twist=5),
    ], note="drumming fast, sticks in turn, head bobbing"),
    "boxing": Emote(0.7, [
        K(0, rax=85, raz=-18, lax=95, laz=-15, lean=8, bob=0, twist=0, rlx=-10, llx=12),
        K(0.12, lax=92, laz=-2, twist=-14, bob=0.04),
        K(0.2, lax=95, laz=-15, twist=0),
        K(0.45, rax=92, raz=0, twist=22, lean=14, bob=-0.05),
        K(0.58, rax=85, raz=-18, twist=0, lean=8, bob=0.04),
        K(0.7, lax=92, laz=-2, twist=-14),
        K(0.8, lax=95, laz=-15, twist=0, bob=0),
    ], note="guard up, jab, cross with the hips behind it, jab, light on the feet"),
    "spin": Emote(1.2, [
        K(0, yaw=0, raz=88, laz=88, hz=0, bob=0),
        K(0.5, hz=10, bob=0.12),
        K(1, yaw=360, raz=88, laz=88, hz=0, bob=0),
    ], linear=("yaw",), note="a full turn with arms out"),
    "helicopter": Emote(0.6, [
        K(0, yaw=0, raz=92, laz=92, rax=8, lax=-8, bob=0.1, rlz=8, llz=8),
        K(0.5, rax=-8, lax=8, bob=0.16),
        K(1, yaw=360, rax=8, lax=-8, bob=0.1),
    ], linear=("yaw",), note="arms out, spinning fast, lifting off"),
    "hip_shake": Emote(0.5, [
        K(0, rax=70, raz=8, lax=70, laz=8, tilt=-11, yaw=8, rlz=8, llz=-4, hz=7, bob=0),
        K(0.25, tilt=0, yaw=0, rlz=2, llz=2, hz=0, bob=-0.06),
        K(0.5, tilt=11, yaw=-8, rlz=-4, llz=8, hz=-7, bob=0),
        K(0.75, tilt=0, yaw=0, rlz=2, llz=2, hz=0, bob=-0.06),
    ], note="arms forward, hips side to side"),
    "swim": Emote(1.0, [
        K(0, rax=0, lax=180, lean=22, hy=0, rlx=15, llx=-15, raz=10, laz=10),
        K(0.25, hy=35, rlx=-15, llx=15),
        K(0.5, hy=0, rlx=15, llx=-15),
        K(0.75, hy=-35, rlx=-15, llx=15),
        K(1, rax=360, lax=540, hy=0, rlx=15, llx=-15),
    ], linear=("rax", "lax"), note="front crawl in the air: windmill arms, turning to breathe, legs fluttering"),
    "zombie": Emote(1.4, [
        K(0, rax=92, raz=6, lax=86, laz=6, hz=22, hx=10, lean=10, yaw=-5, rlx=14, llx=-6, bob=0),
        K(0.25, rax=86, lax=92, hz=14, bob=-0.06),
        K(0.5, rax=92, lax=86, hz=26, yaw=5, rlx=-6, llx=14, bob=0),
        K(0.75, rax=86, lax=92, hz=18, bob=-0.06),
    ], note="arms out, head lolling, shuffling"),
    "flex": Emote(1.2, [
        K(0, raz=4, laz=4, rax=0, lax=0, lean=0, bob=0, hx=0),
        K(0.14, raz=110, laz=110, rax=25, lax=25, lean=-6, bob=0.05, hx=-8),
        K(0.42, raz=114, laz=114, rax=28, lax=28, lean=-7, bob=0.05, hx=-8),
        K(0.55, rax=35, raz=-40, lax=35, laz=-40, lean=14, bob=-0.1, hx=10),
        K(0.82, rax=37, raz=-42, lax=37, laz=-42, lean=15, bob=-0.1, hx=10),
        K(0.94, raz=4, laz=4, rax=0, lax=0, lean=0, bob=0, hx=0),
    ], note="double biceps, then the most-muscular crunch"),
    "bow": Emote(2.0, [
        K(0, rax=0, raz=4, lax=0, laz=4, lean=0, hx=0),
        K(0.15, rax=52, raz=-48, lax=-30, laz=-15),
        K(0.38, lean=48, hx=18, bob=-0.05),
        K(0.62, lean=50, hx=20, bob=-0.05),
        K(0.82, rax=52, raz=-48, lean=0, hx=0, bob=0),
        K(0.95, rax=0, raz=4, lax=0, laz=4),
    ], note="hand on the heart, a deep polite bow"),
    "cheer": Emote(0.5, [
        K(0, rax=10, raz=168, lax=40, laz=20, bob=0.12, hx=-14, tilt=-5),
        K(0.25, bob=0),
        K(0.5, lax=10, laz=168, rax=40, raz=20, bob=0.12, hx=-14, tilt=5),
        K(0.75, bob=0),
    ], note="arms up in turns, bouncing"),
    "raise_roof": Emote(0.5, [
        K(0, rax=150, raz=22, lax=150, laz=22, bob=-0.1, hx=-6, lean=4),
        K(0.5, rax=172, raz=18, lax=172, laz=18, bob=0.04, hx=-16, lean=-3),
    ], note="pushing the roof up, bouncing"),
    "penguin": Emote(0.5, [
        K(0, raz=30, laz=8, tilt=-10, yaw=10, rlx=8, llx=-4, hz=8, bob=0.04),
        K(0.25, raz=10, laz=10, tilt=0, yaw=0, rlx=0, llx=0, hz=0, bob=0),
        K(0.5, raz=8, laz=30, tilt=10, yaw=-10, rlx=-4, llx=8, hz=-8, bob=0.04),
        K(0.75, raz=10, laz=10, tilt=0, yaw=0, rlx=0, llx=0, hz=0, bob=0),
    ], note="little flaps and a waddle"),
    "slide": Emote(0.9, [
        K(0, rlz=26, llz=0, rax=-15, lax=25, raz=20, laz=10, tilt=-8, yaw=12, hy=-10, bob=-0.08),
        K(0.25, rlz=4, llz=4, rax=0, lax=0, raz=10, laz=10, tilt=0, yaw=0, hy=0, bob=0),
        K(0.5, rlz=0, llz=26, rax=25, lax=-15, raz=10, laz=20, tilt=8, yaw=-12, hy=10, bob=-0.08),
        K(0.75, rlz=4, llz=4, rax=0, lax=0, raz=10, laz=10, tilt=0, yaw=0, hy=0, bob=0),
    ], note="smooth sliding feet, arms easing back and forth"),
    "fly_pose": Emote(1.5, [
        K(0, rax=180, raz=-6, lax=-18, laz=8, rlx=-8, llx=-18, lean=8, hx=-10, bob=0.35),
        K(0.5, rax=176, lax=-24, rlx=-14, llx=-10, lean=10, hx=-14, bob=0.5),
    ], note="one fist up, hovering"),
    "star_jump": Emote(1.0, [
        K(0, raz=6, laz=6, rlz=0, llz=0, lean=0, bob=0, hx=0),
        K(0.2, rax=-30, lax=-30, raz=10, laz=10, lean=16, bob=-0.25, hx=8),
        K(0.42, rax=0, lax=0, raz=145, laz=145, rlz=28, llz=28, lean=-4, bob=0.95, hx=-14),
        K(0.58, raz=150, laz=150, rlz=30, llz=30, bob=0.9),
        K(0.78, raz=40, laz=40, rlz=4, llz=4, lean=12, bob=-0.15, hx=6),
        K(0.92, raz=6, laz=6, rlz=0, llz=0, lean=0, bob=0, hx=0),
    ], note="crouch, a big jumping star, land"),
    "hula": Emote(0.8, [
        K(0, rax=20, raz=95, lax=40, laz=-15, tilt=-12, rlz=10, llz=-4, hz=8, bob=0),
        K(0.25, rax=30, raz=60, lax=30, laz=60, tilt=0, rlz=3, llz=3, hz=0, bob=-0.05),
        K(0.5, rax=40, raz=-15, lax=20, laz=95, tilt=12, rlz=-4, llz=10, hz=-8, bob=0),
        K(0.75, rax=30, raz=60, lax=30, laz=60, tilt=0, rlz=3, llz=3, hz=0, bob=-0.05),
    ], note="arms flowing side to side, hips swaying"),
    "wave_both": Emote(0.8, [
        K(0, rax=10, raz=140, lax=10, laz=170, hz=-8, bob=0),
        K(0.25, bob=0.06),
        K(0.5, raz=170, laz=140, hz=8, bob=0),
        K(0.75, bob=0.06),
    ], note="waving with both hands, swaying"),
    "rock_out": Emote(0.45, [
        K(0, rax=165, raz=-10, lax=10, laz=25, hx=-12, lean=-6, bob=0.04),
        K(0.45, rax=118, raz=-5, hx=30, lean=10, bob=-0.08),
        K(0.55, rax=118, raz=-5, hx=30, lean=10, bob=-0.08),
    ], note="fist pumping, head banging"),
    "tap_dance": Emote(0.5, [
        K(0, rlx=28, llx=0, raz=28, laz=22, twist=6, bob=0.05, hx=-4),
        K(0.25, rlx=0, llx=0, bob=0),
        K(0.5, rlx=0, llx=28, raz=22, laz=28, twist=-6, bob=0.05),
        K(0.75, rlx=0, llx=0, bob=0),
    ], note="quick feet, arms out for balance"),
    "lasso": Emote(0.6, [
        K(0, rax=25, raz=158, lax=-25, laz=38, hx=-22, bob=0, hy=0),
        K(0.25, rax=0, raz=172, hy=8, bob=0.03),
        K(0.5, rax=-25, raz=158, hy=0, bob=0),
        K(0.75, rax=0, raz=144, hy=-8, bob=0.03),
    ], note="a rope twirled overhead, the other hand on the hip"),
    "breakdance": Emote(0.9, [
        K(0, yaw=0, lean=30, raz=80, laz=80, rax=40, lax=40, rlz=40, llz=6, bob=-0.35),
        K(0.5, rlz=10, llz=38, bob=-0.42),
        K(1, yaw=360, rlz=40, llz=6, bob=-0.35),
    ], linear=("yaw",), note="low spin, arms out, legs kicking out in turn"),
    "ripple": Emote(0.9, [
        K(0, laz=115, raz=10, tilt=-10, hz=10, rax=10, lax=10, bob=0),
        K(0.25, laz=60, raz=60, tilt=0, hz=0, bob=0.05),
        K(0.5, laz=10, raz=115, tilt=10, hz=-10, bob=0),
        K(0.75, laz=60, raz=60, tilt=0, hz=0, bob=0.05),
    ], note="a wave rolling through both arms and the body"),
    "legendary": Emote(2.0, [
        K(0, yaw=0, raz=6, laz=6, bob=0, lean=0, hx=0, rlz=0, llz=0),
        K(0.15, rax=-30, lax=-30, raz=12, laz=12, bob=-0.3, lean=18, hx=10),
        K(0.3, rax=0, lax=0, raz=160, laz=160, bob=1.1, lean=-6, hx=-20, rlz=14, llz=14),
        K(0.45, yaw=360, raz=168, laz=168, bob=1.0),
        K(0.58, raz=120, laz=120, bob=-0.2, lean=12, rlz=8, llz=8, hx=6),
        K(0.7, raz=172, laz=172, rax=10, lax=10, bob=0.12, lean=-8, hx=-25, rlz=10, llz=10),
        K(0.86, raz=168, laz=168, bob=0.05, hx=-22),
        K(1, yaw=360, raz=6, laz=6, rax=0, lax=0, bob=0, lean=0, hx=0, rlz=0, llz=0),
    ], note="the 1000-kill emote: crouch, a spinning jump with arms up, land, victory pose"),
}

# Knocked down (not an emote): crawling, as RobloxAvatarRenderer.crawlPose draws it, for the videos.
CRAWL_CYCLE = 2.0
