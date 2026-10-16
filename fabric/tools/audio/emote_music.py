"""
Each emote's own music (2.0.0-test5, creator: "Add emote music!"): a seamless loop per emote, made from nothing but
maths like the rest of make_audio.py (no recording, nothing from any song or game: Fortnite's emote music is in its
encrypted paks, never opened). Each emote has a style that suits the dance (disco for Disco Point, a rock band for
Rock Out, a drum solo for Drum Solo, chiptune for the Obby Hop, a march for the Salute...), and its own key, chords
and tune (picked from its name, so every emote sounds different and the same every run). Its tempo fits the dance:
a whole number of beats to each loop of the moves (emote_anims.py), and both start together, so they keep in step.

Eight bars of 4/4 each (two runs round the chords: the tune, then its answer), joined up seamlessly; written mono, so
the game can play it from the player who's dancing, to sounds/emote/<id>.ogg as the event emote.<id>.

python fabric/tools/audio/make_audio.py emotes     (just these: the sounds sheet, emotes sheet and sounds.json too)
"""
import json
import os
import sys
import zlib

import numpy as np

import make_audio as ma
from make_audio import SR, env, filt, hat, kick, noise, note, place, saw, snare, soft, square, sweep, t_of, tom

ROOT = ma.ROOT
DESIGN = os.path.join(ROOT, "design")
sys.path.insert(0, os.path.join(ROOT, "fabric", "tools", "blender"))

# ------------------------------------------------------------------------------------------------- instruments


def partials(freq, sec, parts, attack=0.003):
    """A note made of sine partials [(multiple of freq, level, how fast it dies per second)]."""
    n = int(sec * SR)
    t = t_of(sec)
    out = np.zeros(n)
    for m, a, rate in parts:
        if freq * m < SR / 2.3:
            out += a * np.sin(2 * np.pi * freq * m * t) * np.exp(-t * rate)
    return out * np.clip(t / attack, 0, 1)


def pluck(freq, sec, bright=1.0, damp=3.0):
    """A plucked string (ukulele, banjo, koto, guitar): bright partials dying faster than the low ones, a pick's tick."""
    parts = [(k, (1.0 / k) ** (1.2 / bright) * abs(np.sin(np.pi * k * 0.23)) * 1.6, damp * (1 + 0.22 * (k - 1) ** 1.6 / bright)) for k in range(1, 15)]
    x = partials(freq, sec, parts, attack=0.002)
    tick = filt(noise(0.012), lo=1500, hi=8000) * env(int(0.012 * SR), 0.0005, 0.004)
    x[:len(tick)] += tick * 0.25 * bright
    return x


def epiano(freq, sec):
    """An electric piano: a soft sine with a bell-like tine on top that fades first."""
    return partials(freq, sec, [(1, 1.0, 1.6), (2, 0.22, 3.5), (3, 0.06, 6), (7.02, 0.10 * min(1.0, 400 / freq), 14)], attack=0.004)


def organ(freq, sec):
    """A drawbar organ: steady sines in octaves and fifths with a slow wobble, held, ending soft."""
    t = t_of(sec)
    x = sum(a * np.sin(2 * np.pi * freq * m * t) for m, a in ((0.5, 0.5), (1, 1.0), (2, 0.6), (3, 0.35), (4, 0.25), (8, 0.08)) if freq * m < SR / 2.3)
    x = x * (1 + 0.12 * np.sin(2 * np.pi * 6.2 * t))
    return x * np.clip(t / 0.012, 0, 1) * np.clip((sec - t) / 0.04, 0, 1)


def clav(freq, sec):
    """A clavinet-ish funk stab: a thin pulse, bright and quick."""
    x = square(freq, sec, 0.2) * 0.6 + saw(freq, sec) * 0.4
    return filt(x, lo=250, hi=3800) * env(int(sec * SR), 0.002, min(sec, 0.16), curve=3)


def supersaw(freq, sec, attack=0.005, bright=4200):
    """A big detuned saw stack (dance stabs and leads)."""
    x = sum(saw(freq, sec, d) for d in (-0.011, -0.006, -0.002, 0.002, 0.006, 0.011)) / 4.0
    return filt(x, hi=bright) * env(int(sec * SR), attack, sec * 0.9, curve=2.2)


def chip(freq, sec, duty=0.25):
    """A chiptune square, held then let go."""
    t = t_of(sec)
    return square(freq, sec, duty) * 0.5 * np.clip((sec - t) / 0.01, 0, 1) * np.exp(-t * 1.2)


def triangle(freq, sec):
    t = t_of(sec)
    ph = (freq * t) % 1.0
    return (4 * np.abs(ph - 0.5) - 1) * np.clip((sec - t) / 0.01, 0, 1)


def power(freq, sec):
    """A distorted guitar power chord (root, fifth, octave), driven hard, a little palm-muted."""
    x = saw(freq, sec) + saw(freq * 1.4983, sec, 0.002) + 0.6 * saw(freq * 2, sec, -0.003)
    x = soft(filt(x, lo=90, hi=5000) * 2.5, 5.0)
    return filt(x, hi=3200) * env(int(sec * SR), 0.003, sec * 0.8, curve=1.8)


def lead_guitar(freq, sec):
    """A singing distorted lead with vibrato."""
    t = t_of(sec)
    vib = 1 + 0.006 * np.sin(2 * np.pi * 5.6 * t) * np.clip(t / 0.25, 0, 1)
    ph = np.cumsum(freq * vib) / SR
    x = 2 * (ph - np.floor(ph + 0.5)) + 0.4 * np.sin(2 * np.pi * 2 * ph)
    x = soft(filt(x, lo=150) * 2.0, 4.0)
    return filt(x, hi=3600) * env(len(t), 0.006, sec, curve=1.3)


def steel(freq, sec):
    """A steel pan: a bright ringing note with its octave and twelfth."""
    return partials(freq, sec, [(1, 1.0, 2.2), (2, 0.55, 3.5), (3, 0.22, 5.5), (4.02, 0.1, 8)], attack=0.0015)


def whistle(freq, sec, breath=0.15):
    """A whistle or flute: a pure tone with vibrato and a little breath."""
    t = t_of(sec)
    vib = 1 + 0.007 * np.sin(2 * np.pi * 5.2 * t) * np.clip(t / 0.2, 0, 1)
    x = np.sin(2 * np.pi * np.cumsum(freq * vib) / SR)
    x += filt(noise(sec), lo=freq * 0.8, hi=freq * 3) * breath
    return x * np.clip(t / 0.04, 0, 1) * np.clip((sec - t) / 0.05, 0, 1)


def theremin(freq, sec):
    """A wavering, spooky tone sliding in."""
    t = t_of(sec)
    f = freq * (1 + 0.02 * np.sin(2 * np.pi * 6.0 * t)) * (1 - 0.06 * np.exp(-t / 0.08))
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.clip(t / 0.06, 0, 1) * np.clip((sec - t) / 0.08, 0, 1)


def accordion(freq, sec):
    """A musette accordion: two reeds a hair apart, buzzy, with bellows pulse."""
    t = t_of(sec)
    x = (saw(freq, sec, -0.004) + saw(freq, sec, 0.004) + 0.5 * square(freq * 2, sec, 0.3)) / 2.5
    return filt(x, hi=2600) * (1 + 0.1 * np.sin(2 * np.pi * 5 * t)) * np.clip(t / 0.02, 0, 1) * np.clip((sec - t) / 0.03, 0, 1)


def brass_note(freq, sec):
    return ma.brass(freq, sec, attack=0.025)


def strings(freq, sec):
    """A soft string section pad, slow to swell."""
    return supersaw(freq, sec, attack=min(0.35, sec / 3), bright=2200) * 0.8


INSTR = {"pluck": lambda f, s: pluck(f, s), "uke": lambda f, s: pluck(f, s, 1.3, 4.5), "banjo": lambda f, s: pluck(f, s, 2.4, 7.0),
         "koto": lambda f, s: pluck(f, s, 1.6, 2.2), "guitar": lambda f, s: pluck(f, s, 1.0, 2.0), "epiano": epiano, "organ": organ,
         "clav": clav, "supersaw": supersaw, "chip": chip, "chip50": lambda f, s: chip(f, s, 0.5), "triangle": triangle, "power": power,
         "lead_guitar": lead_guitar, "steel": steel, "whistle": whistle, "flute": lambda f, s: whistle(f, s, 0.35), "theremin": theremin,
         "accordion": accordion, "brass": brass_note, "strings": strings,
         "saw": lambda f, s: filt(saw(f, s) + saw(f, s, 0.004), hi=2800) * env(int(s * SR), 0.004, s, curve=1.6) * 0.5,
         "square": lambda f, s: filt(square(f, s, 0.35), hi=3000) * env(int(s * SR), 0.004, s, curve=1.4) * 0.5,
         "bell": lambda f, s: ma.bell(f, s)}

# ------------------------------------------------------------------------------------------------- drums


def clap():
    out = np.zeros(int(0.25 * SR))
    for k, at in enumerate((0.0, 0.011, 0.022)):
        b = filt(noise(0.02 if k < 2 else 0.2), lo=900, hi=5500) * env(int((0.02 if k < 2 else 0.2) * SR), 0.0005, 0.01 if k < 2 else 0.09)
        i = int(at * SR)
        out[i:i + len(b)] += b
    return out * 0.8


def shaker():
    return filt(noise(0.07), lo=5000, hi=12000) * env(int(0.07 * SR), 0.012, 0.03) * 0.6


def cowbell():
    sec = 0.25
    x = square(540, sec, 0.5) + square(800, sec, 0.5)
    return filt(x, lo=500, hi=3000) * env(int(sec * SR), 0.001, 0.09) * 0.35


def crash():
    return filt(noise(1.6), lo=3800, hi=13000) * env(int(1.6 * SR), 0.002, 0.8, curve=3) * 0.7


def ride():
    sec = 0.5
    return (filt(noise(sec), lo=6000) * 0.4 + np.sin(2 * np.pi * 3150 * t_of(sec)) * 0.15) * env(int(sec * SR), 0.001, 0.25)


def rim():
    sec = 0.05
    return (np.sin(2 * np.pi * 1750 * t_of(sec)) + filt(noise(sec), lo=2500) * 0.5) * env(int(sec * SR), 0.0005, 0.015) * 0.7


def taiko():
    sec = 0.9
    x = sweep(115, 58, sec, shape=5) * env(int(sec * SR), 0.002, 0.45)
    hit = filt(noise(0.05), lo=80, hi=900) * env(int(0.05 * SR), 0.001, 0.02)
    x[:len(hit)] += hit * 0.8
    return x


def gong():
    sec = 3.0
    return partials(98, sec, [(1, 1.0, 0.9), (1.48, 0.6, 1.2), (2.12, 0.45, 1.6), (2.77, 0.3, 2.0), (3.41, 0.2, 2.6)], attack=0.03) * 0.6


def woodblock():
    sec = 0.08
    return (np.sin(2 * np.pi * 880 * t_of(sec)) + 0.3 * np.sin(2 * np.pi * 2210 * t_of(sec))) * env(int(sec * SR), 0.0005, 0.025) * 0.6


def brush():
    return filt(noise(0.18), lo=1500, hi=8000) * env(int(0.18 * SR), 0.01, 0.07) * 0.5


def k808():
    sec = 0.9
    return sweep(115, 44, sec, shape=4) * env(int(sec * SR), 0.001, 0.5, curve=3)


def snap():
    return (filt(noise(0.06), lo=2200, hi=9000) * env(int(0.06 * SR), 0.0005, 0.02) + np.sin(2 * np.pi * 1300 * t_of(0.06)) * env(int(0.06 * SR), 0.0003, 0.008) * 0.4) * 0.8


def chip_kick():
    sec = 0.12
    return np.sign(sweep(220, 45, sec, shape=6)) * env(int(sec * SR), 0.0005, 0.06) * 0.6


def chip_snare():
    sec = 0.14
    return np.sign(noise(sec)) * env(int(sec * SR), 0.0005, 0.05) * 0.35


def chip_hat():
    sec = 0.04
    return filt(np.sign(noise(sec)), lo=6000) * env(int(sec * SR), 0.0003, 0.015) * 0.35


def drumset(style):
    """The hits a style's patterns name, each made once."""
    return {"kick": kick(), "snare": snare(), "hat": hat(), "ohat": hat(open_=True), "clap": clap(), "shaker": shaker(), "cow": cowbell(),
            "crash": crash(), "ride": ride(), "rim": rim(), "taiko": taiko(), "gong": gong(), "wood": woodblock(), "brush": brush(),
            "808": k808(), "snap": snap(), "ckick": chip_kick(), "csnare": chip_snare(), "chat": chip_hat(),
            "tom_hi": tom(196), "tom_mid": tom(147), "tom_lo": tom(104)}


# ------------------------------------------------------------------------------------------------- styles

SCALES = {"major": [0, 2, 4, 5, 7, 9, 11], "minor": [0, 2, 3, 5, 7, 8, 10], "dorian": [0, 2, 3, 5, 7, 9, 10], "mixo": [0, 2, 4, 5, 7, 9, 10],
          "pmaj": [0, 2, 4, 7, 9], "pmin": [0, 3, 5, 7, 10], "blues": [0, 3, 5, 6, 7, 10], "harm": [0, 2, 3, 5, 7, 8, 11], "in": [0, 1, 5, 7, 8]}
CHORD = {"maj": [0, 4, 7], "min": [0, 3, 7], "dom7": [0, 4, 7, 10], "min7": [0, 3, 7, 10], "maj7": [0, 4, 7, 11], "pow": [0, 7, 12],
         "sus": [0, 5, 7], "dim": [0, 3, 6]}
# Four chords, a bar each (root above the key, quality); a style picks one of its own by the emote's name.
PROGS = {"pop": [(0, "maj"), (7, "maj"), (9, "min"), (5, "maj")], "pop2": [(0, "maj"), (9, "min"), (5, "maj"), (7, "maj")],
         "epic": [(0, "min"), (8, "maj"), (3, "maj"), (10, "maj")], "funk": [(0, "min7"), (5, "dom7"), (0, "min7"), (5, "dom7")],
         "disco": [(0, "min7"), (5, "min7"), (10, "dom7"), (3, "maj7")], "blues": [(0, "dom7"), (5, "dom7"), (0, "dom7"), (7, "dom7")],
         "jazz": [(2, "min7"), (7, "dom7"), (0, "maj7"), (9, "dom7")], "rock": [(0, "pow"), (10, "pow"), (5, "pow"), (0, "pow")],
         "rock2": [(0, "pow"), (8, "pow"), (10, "pow"), (0, "pow")], "island": [(0, "maj"), (5, "maj"), (7, "maj"), (5, "maj")],
         "oompah": [(0, "maj"), (7, "dom7"), (7, "dom7"), (0, "maj")], "march": [(0, "maj"), (5, "maj"), (7, "maj"), (0, "maj")],
         "spooky": [(0, "min"), (5, "min"), (0, "min"), (7, "maj")], "eastern": [(0, "sus"), (8, "maj"), (5, "sus"), (7, "sus")],
         "edm": [(9, "min"), (5, "maj"), (0, "maj"), (7, "maj")], "edm2": [(0, "min"), (8, "maj"), (3, "maj"), (10, "maj")],
         "hiphop": [(0, "min7"), (8, "maj7"), (5, "min7"), (7, "min7")], "hiphop2": [(0, "min7"), (5, "min7"), (0, "min7"), (10, "dom7")],
         "smooth": [(0, "maj7"), (4, "min7"), (9, "min7"), (5, "maj7")], "chip": [(0, "maj"), (9, "min"), (5, "maj"), (7, "maj")],
         "western": [(0, "maj"), (5, "maj"), (0, "maj"), (7, "dom7")], "heroic": [(0, "min"), (8, "maj"), (10, "maj"), (0, "min")]}

# Drum patterns: 16 steps a bar ("x" a hit, "o" a soft one), the fill in the last half of bar 8.
X = "x---x---x---x---"
STYLES = {
    "funk": dict(bpm=(104, 124), scale="dorian", progs=("disco", "funk"), keys=(52, 57, 50, 55), swing=0.0,
                 drums={"kick": X, "clap": "----x-------x---", "hat": "x-x-x-x-x-x-x-x-", "ohat": "--x---x---x---x-", "shaker": "oooooooooooooooo"},
                 bass="octave", bass_instr="saw", comp="offbeat", comp_instr=("clav", "epiano"), lead_instr=("brass", "saw"), density=0.55, fill="snare"),
    "electro": dict(bpm=(118, 130), scale="minor", progs=("edm", "edm2"), keys=(57, 53, 55, 50), swing=0.0,
                    drums={"kick": X, "clap": "----x-------x---", "hat": "--x---x---x---x-", "chat": "x-x-x-x-x-x-x-x-"},
                    bass="offbeat", bass_instr="saw", comp="stabs", comp_instr=("supersaw",), lead_instr=("square", "supersaw", "chip50"), density=0.6,
                    fill="snare_roll"),
    "hiphop": dict(bpm=(84, 100), scale="minor", progs=("hiphop", "hiphop2"), keys=(50, 53, 55, 48), swing=0.18,
                   drums={"kick": "x-----x---x-----", "snare": "----x-------x---", "hat": "x-x-x-x-x-x-x-x-", "snap": "------------x---"},
                   bass="boom", bass_instr="808", comp="pad", comp_instr=("epiano",), lead_instr=("bell", "pluck", "flute"), density=0.4, fill="hats"),
    "trap": dict(bpm=(70, 80), scale="minor", progs=("hiphop2", "epic"), keys=(50, 52, 49), swing=0.0,
                 drums={"kick": "x------x--x-----", "clap": "--------x-------", "hat": "x-x-x-x-xxx-x-xx"},
                 bass="boom", bass_instr="808", comp="pad", comp_instr=("strings",), lead_instr=("bell", "pluck"), density=0.35, fill="hats"),
    "rock": dict(bpm=(120, 160), scale="pmin", progs=("rock", "rock2"), keys=(52, 57, 50), swing=0.0,
                 drums={"kick": "x-----x-x-------", "snare": "----x-------x---", "hat": "x-x-x-x-x-x-x-x-"},
                 bass="eighths", bass_instr="saw", comp="eighths", comp_instr=("power",), lead_instr=("lead_guitar",), density=0.6, fill="toms"),
    "montage": dict(bpm=(110, 130), scale="minor", progs=("epic", "rock2"), keys=(52, 55, 50), swing=0.0,
                    drums={"kick": "x-----x-x-------", "snare": "----x-------x---", "hat": "x-x-x-x-x-x-x-x-"},
                    bass="eighths", bass_instr="saw", comp="eighths", comp_instr=("power",), lead_instr=("brass",), density=0.45, fill="toms"),
    "drums": dict(bpm=(100, 130), scale="pmin", progs=("rock",), keys=(45,), swing=0.0, drums={}, bass=None, bass_instr="saw",
                  comp=None, comp_instr=(), lead_instr=(), density=0.0, fill="solo"),
    "chip": dict(bpm=(130, 160), scale="major", progs=("chip", "pop2"), keys=(60, 62, 65, 57), swing=0.0,
                 drums={"ckick": "x-------x-x-----", "csnare": "----x-------x---", "chat": "x-x-x-x-x-x-x-x-"},
                 bass="chip", bass_instr="triangle", comp="arp", comp_instr=("chip",), lead_instr=("chip50", "chip"), density=0.65, fill="chip"),
    "island": dict(bpm=(92, 116), scale="major", progs=("island", "pop"), keys=(60, 65, 62, 67), swing=0.12,
                   drums={"kick": "x-------x-------", "rim": "----x--x----x---", "shaker": "xoxoxoxoxoxoxoxo"},
                   bass="island", bass_instr="saw", comp="strum", comp_instr=("uke",), lead_instr=("steel", "whistle"), density=0.5, fill="rim"),
    "march": dict(bpm=(100, 122), scale="major", progs=("march", "oompah"), keys=(58, 60, 55), swing=0.0,
                  drums={"kick": "x-------x-------", "snare": "x--xx-x-x--xx-xx", "crash": ""},
                  bass="oompah", bass_instr="brass", comp="oom", comp_instr=("brass",), lead_instr=("brass", "flute"), density=0.55, fill="snare_roll"),
    "heroic": dict(bpm=(80, 100), scale="minor", progs=("heroic", "epic"), keys=(50, 52, 48), swing=0.0,
                   drums={"taiko": "x-----x-x-------", "tom_lo": "--------x-----x-", "snare": "----x-------x---"},
                   bass="held", bass_instr="saw", comp="pad", comp_instr=("strings",), lead_instr=("brass",), density=0.4, fill="toms"),
    "swing": dict(bpm=(120, 180), scale="major", progs=("jazz",), keys=(53, 58, 51), swing=0.33,
                  drums={"ride": "x---x-xx--x-x-xx", "brush": "----x-------x---", "kick": "x---------------"},
                  bass="walk", bass_instr="pluck", comp="charleston", comp_instr=("epiano",), lead_instr=("flute", "brass"), density=0.5, fill="brush"),
    "polka": dict(bpm=(110, 130), scale="major", progs=("oompah",), keys=(55, 60, 62), swing=0.0,
                  drums={"kick": "x-------x-------", "snare": "----x-------x---"},
                  bass="oompah", bass_instr="brass", comp="oom", comp_instr=("accordion",), lead_instr=("accordion", "chip50"), density=0.6, fill="snare"),
    "eastern": dict(bpm=(90, 120), scale="in", progs=("eastern",), keys=(57, 52), swing=0.0,
                    drums={"taiko": "x-------x--x----", "wood": "--x---x---x---x-", "gong": ""},
                    bass="held", bass_instr="saw", comp="arp", comp_instr=("koto",), lead_instr=("flute",), density=0.4, fill="taiko"),
    "spooky": dict(bpm=(80, 100), scale="harm", progs=("spooky",), keys=(50, 52), swing=0.2,
                   drums={"kick": "x-------x-------", "snare": "--------x-------", "wood": "----x-------x--x"},
                   bass="walk", bass_instr="pluck", comp="pad", comp_instr=("organ",), lead_instr=("theremin",), density=0.4, fill="toms"),
    "western": dict(bpm=(100, 130), scale="major", progs=("western",), keys=(55, 57, 52), swing=0.1,
                    drums={"kick": "x-------x-------", "snare": "----x-------x---", "wood": "x-x-x-x-x-x-x-x-"},
                    bass="oompah", bass_instr="pluck", comp="arp", comp_instr=("banjo",), lead_instr=("whistle",), density=0.5, fill="snare"),
    "smooth": dict(bpm=(72, 90), scale="major", progs=("smooth",), keys=(53, 51, 56), swing=0.15,
                   drums={"kick": "x------x--x-----", "rim": "----x-------x---", "hat": "x-x-x-x-x-x-x-x-"},
                   bass="boom", bass_instr="saw", comp="pad", comp_instr=("epiano",), lead_instr=("saw", "flute"), density=0.4, fill="hats"),
}

# Which style each emote dances to.
EMOTE_STYLE = {
    "wave": "island", "swing": "hiphop", "robot": "electro", "floss": "electro", "dab": "electro", "jumping_jacks": "chip", "twist": "funk",
    "obby_hop": "chip", "victory_dance": "funk", "clap": "swing", "salute": "march", "shrug": "hiphop", "chicken": "polka", "disco": "funk",
    "running_man": "hiphop", "karate": "eastern", "air_guitar": "rock", "drum": "drums", "boxing": "montage", "spin": "electro",
    "helicopter": "electro", "hip_shake": "funk", "swim": "island", "zombie": "spooky", "flex": "trap", "bow": "heroic", "cheer": "march",
    "raise_roof": "hiphop", "penguin": "chip", "slide": "smooth", "fly_pose": "heroic", "star_jump": "chip", "hula": "island",
    "wave_both": "island", "rock_out": "rock", "tap_dance": "swing", "lasso": "western", "breakdance": "hiphop", "ripple": "electro",
    "legendary": "heroic", "griddy": "trap", "default_dance": "funk", "orange_justice": "electro", "boneless": "hiphop", "take_the_l": "electro",
    "electro_shuffle": "electro", "fresh": "funk", "hype": "electro"}
STYLE_NAMES = {"funk": "disco funk", "electro": "electro dance", "hiphop": "a hip-hop beat", "trap": "a slow trap beat", "rock": "a rock band",
               "montage": "a training-montage rock anthem", "drums": "a drum solo", "chip": "chiptune", "island": "island ukulele and steel pans",
               "march": "a brass march", "heroic": "heroic brass and drums", "swing": "swing jazz", "polka": "an oompah polka",
               "eastern": "koto and taiko", "spooky": "a spooky organ shuffle", "western": "a cowboy banjo tune", "smooth": "smooth R&B"}

# ------------------------------------------------------------------------------------------------- the engine


def tempo(loop_sec, lo, hi):
    """A tempo in the style's range with a whole number of beats to each loop of the dance (even counts preferred)."""
    best = None
    for beats in (1, 2, 3, 4, 6, 8, 12, 16):
        bpm = 60 * beats / loop_sec
        cost = (0 if lo <= bpm <= hi else 10 * min(abs(bpm - lo), abs(bpm - hi))) + 0.05 * abs(bpm - (lo + hi) / 2) + (0 if beats % 2 == 0 or beats == 1 else 2)
        if best is None or cost < best[0]:
            best = (cost, bpm)
    return best[1]


def scale_notes(key, scale, lo, hi):
    out = []
    for octave in range(-2, 5):
        for s in SCALES[scale]:
            m = key + 12 * octave + s
            if lo <= m <= hi:
                out.append(m)
    return sorted(out)


def song(eid, style_id, loop_sec):
    st = STYLES[style_id]
    rng = np.random.default_rng(zlib.crc32(("emote:" + eid).encode()))
    pick = lambda xs: xs[int(rng.integers(len(xs)))] if xs else None
    bpm = tempo(loop_sec, *st["bpm"])
    beat = 60.0 / bpm
    step = beat / 4
    bars = 8
    n = int(round(bars * 16 * step * SR))
    buf = np.zeros(n)
    swing = st["swing"]

    def at(bar, s):  # (a 16th step in a bar, the off-beat ones pushed late by the swing)
        s_ = s + (swing * 2 if (s % 4 == 2 and swing >= 0.25) else swing if s % 2 == 1 and swing < 0.25 else 0)
        return int(round((bar * 16 + s_) * step * SR))

    key = pick(st["keys"])
    prog = PROGS[pick(st["progs"])]
    chord = lambda bar: (key + prog[bar % 4][0], CHORD[prog[bar % 4][1]])
    kit = drumset(style_id)
    levels = {"kick": 0.9, "snare": 0.55, "hat": 0.16, "ohat": 0.1, "clap": 0.5, "shaker": 0.12, "cow": 0.3, "crash": 0.3, "ride": 0.22, "rim": 0.35,
              "taiko": 0.85, "gong": 0.4, "wood": 0.3, "brush": 0.4, "808": 0.9, "snap": 0.45, "ckick": 0.7, "csnare": 0.5, "chat": 0.3,
              "tom_hi": 0.5, "tom_mid": 0.5, "tom_lo": 0.55}
    # drums
    for bar in range(bars):
        fill = bar == bars - 1 or (bar == 3 and st["fill"] == "solo")
        for name, pat in st["drums"].items():
            for s in range(16):
                if fill and s >= 8 and name not in ("kick", "ckick", "taiko"):
                    continue
                c = pat[s] if s < len(pat) else "-"
                if c in "xo":
                    place(buf, kit[name], at(bar, s), levels[name] * (0.55 if c == "o" else 1.0))
        if fill:
            f = st["fill"]
            if f in ("snare", "snare_roll"):
                for s in range(8, 16, 1 if f == "snare_roll" else 2):
                    place(buf, kit["snare"], at(bar, s), levels["snare"] * (0.4 + 0.6 * (s - 8) / 8))
            elif f == "toms":
                for k, s in enumerate(range(8, 16)):
                    place(buf, kit[("tom_hi", "tom_mid", "tom_lo")[min(2, k // 3)]], at(bar, s), 0.5)
            elif f == "hats":
                for s in range(8, 16):
                    place(buf, kit["hat"], at(bar, s), 0.18)
                    place(buf, kit["hat"], at(bar, s) + int(step * SR / 2), 0.12)
            elif f == "rim":
                for s in (8, 10, 11, 12, 14, 15):
                    place(buf, kit["rim"], at(bar, s), 0.35)
            elif f == "brush":
                for s in (8, 10, 12, 13, 14):
                    place(buf, kit["brush"], at(bar, s), 0.45)
            elif f == "chip":
                for s in range(8, 16):
                    place(buf, kit["csnare"], at(bar, s), 0.3 + 0.04 * s)
            elif f == "taiko":
                for s in (8, 10, 12, 13, 14, 15):
                    place(buf, kit["taiko"], at(bar, s), 0.7)
    if st["fill"] == "solo":  # Drum Solo: the kit played all over, a roll round the toms each half, crashes
        for bar in range(bars):
            for s in range(16):
                a = at(bar, s)
                if bar % 4 == 3 and s >= 4:
                    place(buf, kit[("tom_hi", "tom_hi", "tom_mid", "tom_mid", "tom_lo", "tom_lo")[min(5, (s - 4) // 2)]], a, 0.55)
                    continue
                if s in (0, 7, 10) or (bar % 2 and s == 14):
                    place(buf, kit["kick"], a, 0.95)
                if s in (4, 12) or (bar % 2 == 0 and s == 15):
                    place(buf, kit["snare"], a, 0.6 if s != 15 else 0.35)
                if s % 2 == 0:
                    place(buf, kit["hat"], a, 0.2 if s % 4 else 0.26)
                if bar % 2 == 1 and s in (6, 9):
                    place(buf, kit["tom_hi"], a, 0.4)
            if bar % 4 == 0:
                place(buf, kit["crash"], at(bar, 0), 0.4)
    # bass
    bi = INSTR.get(st["bass_instr"]) or (lambda f, s: k808() * 0)
    for bar in range(bars if st["bass"] else 0):  # (the drum solo: just the drums)
        root, tones = chord(bar)
        r = root - 24 if root - 24 >= 33 else root - 12
        nxt = chord(bar + 1)[0] - 24
        while nxt - r > 7:
            nxt -= 12
        while nxt - r < -7:
            nxt += 12
        pattern = {
            "octave": [(s, 2, 12 if s % 4 == 2 else 0) for s in range(0, 16, 2)],
            "offbeat": [(s, 2, 0) for s in (2, 6, 10, 14)],
            "funk": [(0, 3, 0), (3, 1, 12), (6, 2, 0), (8, 1, 7), (10, 2, 10), (12, 1, 12), (14, 2, 0)],
            "eighths": [(s, 2, 0) for s in range(0, 16, 2)],
            "boom": [(0, 6, 0), (10, 2, 0), (12, 4, 7)],
            "walk": [(0, 4, 0), (4, 4, tones[1]), (8, 4, 7), (12, 4, (nxt - r) - 1 if nxt >= r else (nxt - r) + 1)],  # (a step into the next chord)
            "oompah": [(0, 3, 0), (8, 3, 7)],
            "island": [(0, 3, 0), (3, 1, 0), (6, 2, 7), (8, 3, 0), (11, 1, 0), (14, 2, 7)],
            "chip": [(s, 2, (0, 7, 12, 7)[(s // 2) % 4]) for s in range(0, 16, 2)],
            "held": [(0, 16, 0)],
        }[st["bass"]]
        for s, ln, iv in pattern:
            sec = ln * step * 0.92
            if st["bass_instr"] == "808":
                b = sweep(note(r + iv) * 1.5, note(r + iv), sec, shape=30) * env(int(sec * SR), 0.003, sec, curve=1.5)
            elif st["bass_instr"] == "brass":
                b = filt(ma.brass(note(r + iv), sec, attack=0.01), hi=900)
            else:
                b = filt(bi(note(r + iv), sec), hi=700) * env(int(sec * SR), 0.004, sec, curve=1.8)
            place(buf, b, at(bar, s), 0.4 if st["bass_instr"] != "808" else 0.55)
    # chords
    ci = INSTR.get(pick(st["comp_instr"]) or "", None)
    if st["comp"] and ci:
        for bar in range(bars):
            root, tones = chord(bar)
            voice = [root + t for t in tones]
            while voice[0] < 52:
                voice = [v + 12 for v in voice]
            hits = {"offbeat": [(s, 1) for s in (2, 6, 10, 14)], "stabs": [(0, 2), (3, 1), (6, 2), (10, 2), (12, 1)],
                    "pad": [(0, 16)], "eighths": [(s, 2) for s in range(0, 16, 2)], "strum": [(0, 2), (3, 1), (6, 2), (8, 2), (11, 1), (14, 2)],
                    "oom": [(4, 2), (12, 2)], "charleston": [(0, 3), (6, 4), (11, 3)], "arp": None}[st["comp"]]
            if hits is None:  # an arpeggio up and down the chord in eighths
                seq = voice + [voice[0] + 12] + voice[1:][::-1]
                for k, s in enumerate(range(0, 16, 2)):
                    place(buf, ci(note(seq[k % len(seq)]), 2 * step * 1.4), at(bar, s), 0.16)
                continue
            for s, ln in hits:
                sec = ln * step * (0.9 if st["comp"] != "pad" else 1.0)
                if st["comp"] == "eighths":  # power chords: one driven note on the root does it
                    place(buf, ci(note(root - 12 if root >= 52 else root), sec), at(bar, s), 0.22)
                    continue
                for k, v in enumerate(voice):
                    spread = int((0.012 * k if st["comp"] == "strum" else 0) * SR)
                    place(buf, ci(note(v), sec), at(bar, s) + spread, (0.09 if st["comp"] == "pad" else 0.11))
    # the tune: a two-bar phrase and its answer, then both again with the answer ending home on the key
    li = INSTR.get(pick(st["lead_instr"]) or "", None)
    if li and st["density"] > 0:
        pool = scale_notes(key, st["scale"], key + 7, key + 24)

        def phrase(start_bar, end_home):
            out = []
            s = 0
            prev = pool[len(pool) // 2]
            while s < 32:
                strong = s % 8 == 0
                if rng.random() < (st["density"] + (0.3 if strong else 0)):
                    bar = start_bar + s // 16
                    root, tones = chord(bar)
                    if strong:  # (on the beat: a note of the chord, near the last)
                        cands = [m for m in pool if (m - root) % 12 in tones] or pool
                    else:
                        i = pool.index(min(pool, key=lambda m: abs(m - prev)))
                        cands = pool[max(0, i - 2):i + 3]
                    m = min(cands, key=lambda c: abs(c - prev) + rng.random() * 4)
                    ln = int(rng.choice([2, 2, 4, 3, 6] if st["density"] < 0.5 else [1, 2, 2, 3, 4]))
                    out.append((s, ln, m))
                    prev = m
                    s += ln
                else:
                    s += 2
            if end_home:
                home = min((m for m in pool if (m - key) % 12 == 0), key=lambda m: abs(m - prev))
                out = [o for o in out if o[0] < 24] + [(24, 8, home)]
            return out

        a, b = phrase(0, False), phrase(2, False)
        b_home = phrase(6, True)
        for start, ph in ((0, a), (2, b), (4, a), (6, b_home)):
            for s, ln, m in ph:
                sec = ln * step * 0.95
                place(buf, li(note(m), sec), at(start + s // 16, s % 16), 0.17)
    out = ma.room(buf, 0.5, 0.18)
    out = soft(out * (0.8 / max(1e-9, np.max(np.abs(out)))), 1.3)
    return out * (0.85 / np.max(np.abs(out))), bpm


def loops():
    """Each emote's loop length (seconds), from its animation (emote_anims.py)."""
    import emote_anims
    return {eid: e.loop for eid, e in emote_anims.EMOTES.items()}


def write_all(snd, reg):
    """Every emote's music, its sounds.json event, a sounds sheet row each and the emotes sheet's music column."""
    lengths = loops()
    sp = os.path.join(DESIGN, "sounds.json")
    sounds = json.load(open(sp, encoding="utf-8"))
    sounds["rows"] = [r for r in sounds["rows"] if not r["id"].startswith("emote_")]
    ep = os.path.join(DESIGN, "emotes.json")
    emotes = json.load(open(ep, encoding="utf-8"))
    for row in emotes["rows"]:
        eid = row["id"]
        style = EMOTE_STYLE[eid]
        ma.seeded(f"emote/{eid}")
        data, bpm = song(eid, style, lengths[eid])
        ma.write_ogg(os.path.join(snd, "emote", f"{eid}.ogg"), data, quality=2)
        reg[f"emote.{eid}"] = {"sounds": [{"name": f"minebloxroyale:emote/{eid}", "stream": True, "attenuation_distance": 24}]}
        sounds["rows"].append({"id": f"emote_{eid}", "game": "mod", "file": f"emote.{eid}", "volume": 0.6, "pitch": 1.0,
                               "use": f"{row['name']}'s music while it's danced: {STYLE_NAMES[style]}, {bpm:.0f} bpm (2.0.0-test5)"})
        row["music"] = f"emote_{eid}"
        print("emote", eid, style, f"{bpm:.0f} bpm")
    emotes["columns"]["music"] = "string"
    if "music:" not in emotes["about"]:
        emotes["about"] += (" music: the sounds-sheet row of the emote's own music (2.0.0-test5: fabric/tools/audio/emote_music.py), looping while"
                            " it's danced: the dancer hears it and their own game music goes quiet till they stop; everyone near hears it"
                            " coming from them, over their own music.")
    json.dump(sounds, open(sp, "w", encoding="utf-8", newline="\n"), indent=1, ensure_ascii=False)
    json.dump(emotes, open(ep, "w", encoding="utf-8", newline="\n"), indent=1, ensure_ascii=False)
