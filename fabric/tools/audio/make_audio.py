"""
The mod's own sounds (v2.0.0, creator: "battle music when in game", "New Storm Warning Sound. New gun sounds when
shooting as well (should be different based on the gun. Different raritys do not have different sounds)"), made here
from nothing but maths (numpy): no recording, nothing taken from another game (Fortnite's audio is in its encrypted
paks, never opened). Written as Ogg Vorbis into assets/minebloxroyale/sounds (the ffmpeg in .tools), listed in the
assets' sounds.json:

  music/battle_1, music/battle_2   two seamless battle loops (A minor, 140 bpm; the second harder, for the endgame)
  gun/<gun>_1..3                   each gun's shot, three takes so repeats don't sound copied (the rocket launcher too)
  boom/explosion_1..3              a rocket or grenade going off; boom/grenade_bounce, a grenade knocking on the ground
  storm/warning                    the storm starting to close in
  car/engine, car/skid             a car's engine and its tyres squealing round a turn: seamless loops the game pitches
                                   and fades with how fast the car goes and turns (2.0.0-test3, creator: "Cars should have
                                   a driving animation (also sounds)")
  car/boost, car/door_open, car/door_close
                                   a boost kicking in, a car door opening and shutting

python fabric/tools/audio/make_audio.py [cars]      (cars: just the car sounds)
"""
import json
import os
import subprocess
import sys
import tempfile
import wave
import zlib

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", "..", ".."))
ASSETS = os.path.join(ROOT, "fabric", "src", "main", "resources", "assets", "minebloxroyale")
FFMPEG = os.path.join(ROOT, ".tools", "ffmpeg", "ffmpeg-n9.0-latest-win64-lgpl-shared-9.0", "bin", "ffmpeg.exe")
SR = 44100
RNG = np.random.default_rng(2026)


# ------------------------------------------------------------------------------------------------- building blocks

def t_of(sec):
    return np.arange(int(sec * SR)) / SR


def filt(x, lo=None, hi=None, order=2.0):
    """Zero-phase filtering in the frequency domain: a smooth high-pass at lo and low-pass at hi (Hz)."""
    n = len(x)
    if n == 0:
        return x
    m = 1 << int(np.ceil(np.log2(n * 2)))
    X = np.fft.rfft(x, m)
    f = np.fft.rfftfreq(m, 1 / SR)
    g = np.ones_like(f)
    if lo:
        g *= 1 / np.sqrt(1 + (lo / np.maximum(f, 1e-3)) ** (2 * order))
    if hi:
        g *= 1 / np.sqrt(1 + (f / hi) ** (2 * order))
    return np.fft.irfft(X * g, m)[:n]


def env(n, attack, decay, curve=4.0):
    """Attack (s) then an exponential-ish decay over decay (s)."""
    t = np.arange(n) / SR
    a = np.clip(t / max(attack, 1e-4), 0, 1)
    d = np.exp(-curve * np.maximum(0, t - attack) / max(decay, 1e-4))
    return a * d


def noise(sec):
    return RNG.standard_normal(int(sec * SR))


def sweep(f0, f1, sec, shape=6.0):
    """A sine falling (or rising) from f0 to f1 Hz."""
    t = t_of(sec)
    f = f1 + (f0 - f1) * np.exp(-shape * t / sec)
    return np.sin(2 * np.pi * np.cumsum(f) / SR)


def saw(freq, sec, detune=0.0):
    t = t_of(sec)
    ph = (freq * (1 + detune)) * t
    return 2 * (ph - np.floor(ph + 0.5))


def square(freq, sec, duty=0.5):
    t = t_of(sec)
    ph = (freq * t) % 1.0
    return np.where(ph < duty, 1.0, -1.0)


def echo(x, delay, feedback, times=4):
    out = x.copy()
    d = int(delay * SR)
    g = feedback
    for k in range(1, times + 1):
        if d * k >= len(out):
            break
        out[d * k:] += x[:len(out) - d * k] * g
        g *= feedback
    return out


def room(x, size=0.6, wet=0.3):
    """A small, cheap reverb: a few spread echoes of a darkened copy."""
    dark = filt(x, hi=3500)
    out = x.copy()
    for k, (dl, g) in enumerate(((0.031, 0.5), (0.047, 0.42), (0.067, 0.36), (0.089, 0.3), (0.113, 0.24), (0.149, 0.18))):
        d = int(dl * size / 0.6 * SR)
        out[d:] += dark[:len(out) - d] * g * wet
    return out


def norm(x, peak=0.89):
    m = np.max(np.abs(x))
    return x * (peak / m) if m > 0 else x


def soft(x, drive=1.4):
    return np.tanh(x * drive) / np.tanh(drive)


def write_ogg(path, data, stereo=False, quality=5):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    data = np.clip(data, -1, 1)
    pcm = (data * 32767).astype("<i2")
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "x.wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(2 if stereo else 1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        # (bit-exact: the same sound makes the same file, so re-running only changes what changed)
        subprocess.run([FFMPEG, "-v", "error", "-y", "-i", wav, "-c:a", "libvorbis", "-q:a", str(quality), "-fflags", "+bitexact", "-flags:a", "+bitexact",
                        path], check=True)


# ------------------------------------------------------------------------------------------------- guns

GUNS = {  # body band (Hz), body length (s), thump (from, to Hz, length s), tail (s), crack brightness, loudness
    "pistol": dict(band=(700, 4200), body=0.10, thump=(170, 60, 0.09), tail=0.45, crack=5200, gain=0.85),
    "smg": dict(band=(1100, 5600), body=0.055, thump=(210, 80, 0.05), tail=0.28, crack=7000, gain=0.7),
    "assault_rifle": dict(band=(500, 3600), body=0.12, thump=(140, 48, 0.12), tail=0.6, crack=4600, gain=0.95),
    "pump_shotgun": dict(band=(180, 1900), body=0.26, thump=(95, 34, 0.24), tail=1.0, crack=3000, gain=1.0),
    "sniper": dict(band=(400, 5200), body=0.16, thump=(120, 38, 0.2), tail=1.6, crack=8000, gain=1.0),
}


def gun(name, take):
    g = GUNS[name]
    rng_pitch = 1 + (take - 2) * 0.035
    total = g["body"] + g["tail"] + 0.2
    n = int(total * SR)
    out = np.zeros(n)
    # the crack: a very short, very bright burst
    c = filt(noise(0.012), lo=g["crack"] * 0.45, hi=g["crack"] * 2.2) * env(int(0.012 * SR), 0.0004, 0.006)
    out[:len(c)] += c * 1.4
    # the body: the bang itself, a burst of filtered noise
    lo, hi = g["band"]
    b = filt(noise(g["body"] * 2.2), lo=lo * rng_pitch, hi=hi * rng_pitch) * env(int(g["body"] * 2.2 * SR), 0.0015, g["body"])
    out[:len(b)] += b * 1.0
    # the thump: a falling low tone you feel
    f0, f1, ln = g["thump"]
    th = sweep(f0 * rng_pitch, f1, ln * 2.5) * env(int(ln * 2.5 * SR), 0.002, ln)
    out[:len(th)] += th * 0.9
    # the tail: the shot ringing off the surroundings, darker and darker
    tl = filt(noise(g["tail"]), lo=lo * 0.5, hi=hi * 0.35) * env(int(g["tail"] * SR), 0.01, g["tail"] * 0.55, curve=5)
    s = int(0.02 * SR)
    out[s:s + len(tl)] += tl * 0.35
    if name == "sniper":  # a far echo off a hill
        out = echo(out, 0.21, 0.32, 3)
    if name == "pump_shotgun":  # the pump racked after the shot: two clicks
        for at, f in ((0.48, 2400), (0.62, 1700)):
            k = filt(noise(0.03), lo=f * 0.6, hi=f * 2) * env(int(0.03 * SR), 0.0005, 0.012)
            i = int(at * SR)
            if i + len(k) < n:
                out[i:i + len(k)] += k * 0.45
    out = room(out, 0.5, 0.25)
    return norm(soft(out, 1.6), 0.95 * g["gain"])


def rocket_launch(take):
    """The rocket launcher: a hollow thump out of the tube, then the motor's rising hiss as it flies off."""
    k = 1 + (take - 2) * 0.04
    sec = 1.3
    n = int(sec * SR)
    out = np.zeros(n)
    th = sweep(150 * k, 50, 0.4) * env(int(0.4 * SR), 0.002, 0.18)
    out[:len(th)] += th * 0.9
    pop = filt(noise(0.08), lo=300, hi=2500) * env(int(0.08 * SR), 0.001, 0.04)
    out[:len(pop)] += pop * 0.8
    hiss_len = sec - 0.05
    m = int(hiss_len * SR)
    hiss = noise(hiss_len)
    parts = []
    blocks = 12
    for b in range(blocks):  # (the motor's whoosh brightening, then going away)
        a_, b_ = b * m // blocks, (b + 1) * m // blocks
        parts.append(filt(hiss[a_:b_], lo=500, hi=1800 + 2600 * (b / blocks)))
    hiss = np.concatenate(parts) * env(m, 0.08, hiss_len * 0.7, curve=2.5)
    i = int(0.05 * SR)
    out[i:i + m] += hiss * 0.5
    return norm(soft(room(out, 0.6, 0.3), 1.4), 0.95)


def explosion(take):
    """An explosion: a deep boom, a crack on top, rumble and debris pattering down after."""
    k = 1 + (take - 2) * 0.05
    sec = 2.4
    n = int(sec * SR)
    out = np.zeros(n)
    boom = sweep(90 * k, 28, 1.0, shape=4) * env(int(1.0 * SR), 0.004, 0.55)
    out[:len(boom)] += boom * 1.0
    crack = filt(noise(0.05), lo=900, hi=7000) * env(int(0.05 * SR), 0.0005, 0.02)
    out[:len(crack)] += crack * 0.8
    body = filt(noise(0.6), lo=60, hi=1400) * env(int(0.6 * SR), 0.003, 0.35)
    out[:len(body)] += body * 0.9
    rumble = filt(noise(sec), lo=30, hi=260) * env(n, 0.05, sec * 0.6, curve=3)
    out += rumble * 0.6
    for _ in range(int(14 * k)):  # debris
        at = int((0.25 + RNG.random() * 1.4) * SR)
        f = 800 + RNG.random() * 2500
        d = filt(noise(0.03), lo=f * 0.6, hi=f * 1.6) * env(int(0.03 * SR), 0.0005, 0.01)
        if at + len(d) < n:
            out[at:at + len(d)] += d * (0.12 + RNG.random() * 0.12)
    return norm(soft(room(out, 1.0, 0.35), 1.8), 0.97)


def grenade_bounce():
    """A grenade knocking on the ground: a dull metal clink."""
    sec = 0.25
    n = int(sec * SR)
    t = t_of(sec)
    out = np.zeros(n)
    for f, a in ((1650.0, 0.5), (2450.0, 0.3), (3900.0, 0.18)):
        out += a * np.sin(2 * np.pi * f * t) * env(n, 0.0005, 0.06)
    knock = filt(noise(0.03), lo=200, hi=1500) * env(int(0.03 * SR), 0.0005, 0.01)
    out[:len(knock)] += knock * 0.6
    return norm(out, 0.8)


# ------------------------------------------------------------------------------------------------- storm

def storm_warning():
    sec = 3.2
    n = int(sec * SR)
    t = t_of(sec)
    # a swell of wind, its filter opening as it rises
    w = noise(sec)
    parts = []
    blocks = 16
    for k in range(blocks):
        a, b = k * n // blocks, (k + 1) * n // blocks
        parts.append(filt(w[a:b], lo=120, hi=300 + 3200 * (k / blocks) ** 1.5))
    wind = np.concatenate(parts) * env(n, 1.6, 1.2, curve=3)
    # an uneasy pair of tones a semitone apart, shimmering
    tone = np.zeros(n)
    for f, a in ((440.0, 0.5), (466.16, 0.45), (220.0, 0.35), (880.0, 0.15)):
        tone += a * np.sin(2 * np.pi * f * t + 0.6 * np.sin(2 * np.pi * 5.5 * t))
    tone *= (0.65 + 0.35 * np.sin(2 * np.pi * 7 * t)) * env(n, 0.5, 1.8, curve=2.5)
    # three falling chimes on top
    chimes = np.zeros(n)
    for k, f in enumerate((1318.5, 1174.7, 987.8)):
        i = int((0.25 + 0.42 * k) * SR)
        c = np.sin(2 * np.pi * f * t[:n - i]) * env(n - i, 0.002, 0.7)
        chimes[i:] += c * 0.35
    out = wind * 0.55 + tone * 0.45 + chimes
    out = room(out, 1.0, 0.45)
    fade = np.ones(n)
    fade[-int(0.3 * SR):] = np.linspace(1, 0, int(0.3 * SR))
    return norm(out * fade, 0.85)


# ------------------------------------------------------------------------------------------------- cars

def car_engine():
    """An engine running, 2 s that loop without a seam: every part repeats a whole number of times in the 2 s (the noise is
    filtered round the loop, so it wraps too). The game raises its pitch with the car's speed."""
    sec = 2.0
    n = int(sec * SR)
    t = t_of(sec)
    f0 = 40.0  # (the firing: 80 times in the loop)
    out = np.zeros(n)
    for k, a in ((1, 1.0), (2, 0.62), (3, 0.42), (4, 0.3), (5, 0.18), (6, 0.12), (8, 0.07)):
        out += a * np.sin(2 * np.pi * f0 * k * t + k * 0.7)
    out *= 0.78 + 0.22 * np.sin(2 * np.pi * 12.5 * t)  # (each cylinder's pulse)
    out += filt(noise(sec), lo=60, hi=420) * 0.55  # (the rumble)
    out += filt(noise(sec), lo=900, hi=2600) * 0.06 * (0.6 + 0.4 * np.sin(2 * np.pi * 25 * t))  # (a little valve chatter)
    return norm(soft(out, 1.5), 0.8)


def car_skid():
    """Tyres squealing: a screech over hiss, 1 s that loops (a whole number of cycles of everything)."""
    sec = 1.0
    n = int(sec * SR)
    t = t_of(sec)
    squeal = np.sin(2 * np.pi * 1250 * t + 9 * np.sin(2 * np.pi * 6 * t)) * 0.5
    squeal += np.sin(2 * np.pi * 2500 * t + 18 * np.sin(2 * np.pi * 6 * t)) * 0.18
    hiss = filt(noise(sec), lo=1400, hi=4200) * 0.6
    out = (squeal + hiss) * (0.85 + 0.15 * np.sin(2 * np.pi * 9 * t))
    return norm(out, 0.7)


def car_boost():
    """A boost: a thump, a turbo whistle rising and a rush of air as it kicks in, a crackle as it settles."""
    sec = 1.4
    n = int(sec * SR)
    out = np.zeros(n)
    th = sweep(120, 45, 0.35) * env(int(0.35 * SR), 0.002, 0.16)
    out[:len(th)] += th * 0.9
    whistle = sweep(900, 2600, 0.9, shape=3) * env(int(0.9 * SR), 0.05, 0.5)
    i = int(0.05 * SR)
    out[i:i + len(whistle)] += whistle * 0.25
    rush = noise(sec)
    parts = []
    blocks = 14
    for b in range(blocks):  # (the rush of air opening up, then easing)
        a_, b_ = b * n // blocks, (b + 1) * n // blocks
        parts.append(filt(rush[a_:b_], lo=300, hi=1200 + 4200 * min(1.0, b / 5)))
    out += np.concatenate(parts) * env(n, 0.12, 0.8, curve=2.5) * 0.55
    for _ in range(10):  # crackle
        at = int((0.7 + RNG.random() * 0.6) * SR)
        c = filt(noise(0.02), lo=1500, hi=6000) * env(int(0.02 * SR), 0.0005, 0.006)
        if at + len(c) < n:
            out[at:at + len(c)] += c * (0.25 + RNG.random() * 0.25)
    return norm(soft(room(out, 0.4, 0.2), 1.3), 0.9)


def car_door(close):
    """A car door: opening, the latch clicks and the hinge creaks; shutting, a solid thump and the latch."""
    sec = 0.45 if not close else 0.4
    n = int(sec * SR)
    out = np.zeros(n)
    click = filt(noise(0.015), lo=2500, hi=8000) * env(int(0.015 * SR), 0.0003, 0.004)
    if close:
        thump = sweep(140, 55, 0.25, shape=4) * env(int(0.25 * SR), 0.002, 0.08)
        out[:len(thump)] += thump * 1.0
        body = filt(noise(0.12), lo=80, hi=900) * env(int(0.12 * SR), 0.001, 0.04)
        out[:len(body)] += body * 0.7
        i = int(0.02 * SR)
        out[i:i + len(click)] += click * 0.5
    else:
        out[:len(click)] += click * 0.7
        i = int(0.06 * SR)
        m = int(0.32 * SR)
        creak = sweep(300, 430, 0.32, shape=1.5) * (0.6 + 0.4 * np.sign(np.sin(2 * np.pi * 38 * t_of(0.32))))
        creak = filt(creak, lo=200, hi=2500) * env(m, 0.04, 0.14)
        out[i:i + m] += creak * 0.35
    return norm(room(out, 0.3, 0.15), 0.8)


def cars(snd, reg):
    for name, make in (("engine", car_engine), ("skid", car_skid), ("boost", car_boost), ("door_open", lambda: car_door(False)),
                       ("door_close", lambda: car_door(True))):
        seeded(f"car/{name}")
        write_ogg(os.path.join(snd, "car", f"{name}.ogg"), make())
        reg[f"car.{name}"] = {"sounds": [f"minebloxroyale:car/{name}"]}
    print("car sounds")


# ------------------------------------------------------------------------------------------------- music

def note(midi):
    return 440.0 * 2 ** ((midi - 69) / 12)


def kick(sec=0.4):
    n = int(sec * SR)
    k = sweep(160, 44, sec, shape=14) * env(n, 0.001, 0.22)
    click = filt(noise(0.006), lo=2000) * env(int(0.006 * SR), 0.0002, 0.003)
    k[:len(click)] += click * 0.4
    return k


def snare(sec=0.3):
    n = int(sec * SR)
    body = np.sin(2 * np.pi * 195 * t_of(sec)) * env(n, 0.001, 0.08)
    sn = filt(noise(sec), lo=1200, hi=9000) * env(n, 0.001, 0.16)
    return body * 0.5 + sn * 0.8


def hat(sec=0.06, open_=False):
    sec = 0.25 if open_ else sec
    n = int(sec * SR)
    return filt(noise(sec), lo=7000) * env(n, 0.0005, sec * (0.5 if open_ else 0.35))


def place(buf, sound, at, gain=1.0):
    """Adds sound into a looping buffer at sample at, wrapping round the end (so the loop joins up seamlessly)."""
    n = len(buf)
    i = at % n
    m = len(sound)
    first = min(m, n - i)
    buf[i:i + first] += sound[:first] * gain
    if m > first:
        rest = sound[first:]
        k = 0
        while len(rest) > 0:
            take = min(len(rest), n)
            buf[:take] += rest[:take] * gain
            rest = rest[take:]
            k += 1


def tom(f, sec=0.35):
    n = int(sec * SR)
    return sweep(f * 1.6, f, sec, shape=8) * env(n, 0.001, 0.18) + filt(noise(sec), lo=300, hi=2500) * env(n, 0.001, 0.04) * 0.3


def track(intense):
    """32 bars in four sections of eight, so it doesn't feel like a short loop: the beat and bass, then the arpeggio,
    a breakdown (half-time drums, the pad and arpeggio), then everything with the melody; a drum fill into each section.
    The calmer one is A minor at 140 bpm; the endgame one is harder and quicker (150 bpm), its melody throughout."""
    bpm = 150 if intense else 140
    beat = 60 / bpm
    bars = 32
    n = int(round(bars * 4 * beat * SR))
    L, R = np.zeros(n), np.zeros(n)
    step = beat / 4  # sixteenths
    at_ = lambda bar, st: int(round((bar * 16 + st) * step * SR))
    # A minor: Am - F - C - G (each two bars); the harder one Am - F - G - E
    prog = [(57, "m"), (53, "M"), (48, "M"), (55, "M")] if not intense else [(57, "m"), (53, "M"), (55, "M"), (52, "M")]
    chord_of = lambda bar: prog[(bar // 2) % 4]
    tones = lambda root, q: [root, root + (3 if q == "m" else 4), root + 7]
    section = lambda bar: bar // 8  # 0 beat + bass, 1 + arpeggio, 2 breakdown, 3 everything + melody
    # drums
    k_, s_, h_, ho_ = kick(), snare(), hat(), hat(open_=True)
    toms = [tom(f) for f in (196, 147, 110)]
    for bar in range(bars):
        sec = section(bar)
        fill = bar % 8 == 7
        for st in range(16):
            at = at_(bar, st)
            if fill and st >= 8:  # the last half bar of a section: a fill down the toms into the next
                if st % 2 == 0 or intense:
                    place(L, toms[min(2, (st - 8) // 3)], at, 0.5 if st % 2 == 0 else 0.3)
                    place(R, toms[min(2, (st - 8) // 3)], at, 0.42 if st % 2 == 0 else 0.25)
                if st == 15:
                    place(L, s_, at, 0.5)
                    place(R, s_, at, 0.5)
                continue
            if sec == 2:  # the breakdown: half time
                if st == 0 or (st == 10 and bar % 2):
                    place(L, k_, at, 0.85)
                    place(R, k_, at, 0.85)
                if st == 8:
                    place(L, s_, at, 0.5)
                    place(R, s_, at, 0.55)
                if st % 4 == 0:
                    place(L, h_, at, 0.1)
                    place(R, h_, at, 0.14)
                continue
            if st in (0, 8) or (intense and st in (6, 14)) or (not intense and st == 10 and bar % 2 == 1):
                place(L, k_, at, 0.95)
                place(R, k_, at, 0.95)
            if st in (4, 12):
                place(L, s_, at, 0.55)
                place(R, s_, at, 0.6)
            if st % 2 == 0:
                place(L, h_, at, 0.18 if st % 4 else 0.12)
                place(R, h_, at, 0.12 if st % 4 else 0.18)
            if (intense or sec == 3) and st % 4 == 2:
                place(R, ho_, at, 0.08)
                place(L, ho_, at, 0.04)
    # bass: eighths on the root, an octave jump at the end of each bar (held notes in the breakdown)
    for bar in range(bars):
        root, q = chord_of(bar)
        if section(bar) == 2:
            ln = 4 * beat * 0.97
            b = saw(note(root - 24), ln) * 0.6 + square(note(root - 36), ln) * 0.4
            b = filt(b, hi=380) * env(len(b), 0.02, ln * 0.9, curve=1.5)
            place(L, b, at_(bar, 0), 0.38)
            place(R, b, at_(bar, 0), 0.38)
            continue
        for e in range(8):
            f = note(root - 24 + (12 if e == 7 else 0))
            ln = step * 2 * 0.92
            b = saw(f, ln) * 0.6 + square(f / 2, ln) * 0.4
            b = filt(b, hi=520 if not intense else 760) * env(len(b), 0.004, ln * 0.8, curve=2.5)
            place(L, b, at_(bar, e * 2), 0.42)
            place(R, b, at_(bar, e * 2), 0.42)
    # pad: the chord, soft and wide (louder in the breakdown)
    for bar in range(0, bars, 2):
        root, q = chord_of(bar)
        ln = 8 * beat
        pad = np.zeros(int(ln * SR))
        for m in tones(root, q):
            for dt in (-0.004, 0.004):
                pad += saw(note(m), ln, dt) * 0.12
        pad = filt(pad, hi=1600) * env(len(pad), 0.6, ln, curve=1.2)
        g = 0.75 if section(bar) == 2 else 0.5
        place(L, pad, at_(bar, 0), g)
        place(R, filt(pad, hi=1400), at_(bar, 0), g)
    # arpeggio: sixteenths up and down the chord, an echo across the stereo (from the second section on)
    arp_l, arp_r = np.zeros(n), np.zeros(n)
    for bar in range(bars):
        if section(bar) == 0 and not intense:
            continue
        root, q = chord_of(bar)
        seq = tones(root + 12, q) + [root + 24]
        order = [0, 1, 2, 3, 2, 1, 2, 3] if section(bar) != 2 else [0, 2, 1, 3, 0, 2, 1, 3]
        for st in range(16):
            m = seq[order[st % 8]]
            ln = step * 0.9
            a = square(note(m), ln, 0.3) * 0.5 + saw(note(m), ln) * 0.5
            a = filt(a, hi=2600 if intense else 2000) * env(len(a), 0.002, ln * 0.6)
            at = at_(bar, st)
            place(arp_l, a, at, 0.16)
            place(arp_r, a, at + int(0.75 * beat * SR), 0.11)
    L += arp_l
    R += arp_r
    # the melody: a two-bar phrase over each chord, answered (the harder one has it in every section but the first)
    calls = {"m": [(0, 4, 0), (4, 2, 3), (6, 2, 7), (8, 6, 5), (14, 2, 3), (16, 4, 0), (20, 4, -2), (24, 8, 0)],
             "M": [(0, 4, 4), (4, 2, 7), (6, 2, 9), (8, 6, 7), (14, 2, 4), (16, 4, 2), (20, 4, 0), (24, 8, 4)]}
    for bar in range(0, bars, 2):
        sec = section(bar)
        if not (sec == 3 or (intense and sec in (1, 3))):
            continue
        root, q = chord_of(bar)
        for st, ln_st, iv in calls[q]:
            m = root + 12 + iv
            ln = ln_st * step * 0.95
            ld = saw(note(m), ln, 0.003) + saw(note(m), ln, -0.003) + 0.5 * square(note(m + 12), ln, 0.25)
            vib = 1 + 0.004 * np.sin(2 * np.pi * 5.5 * t_of(ln))
            ld = ld * vib
            ld = filt(ld, hi=3000 if intense else 2400) * env(len(ld), 0.012, ln * 1.1, curve=1.4)
            place(L, ld, at_(bar, st), 0.085)
            place(R, ld, at_(bar, st) + int(0.012 * SR), 0.085)
    out = np.stack([soft(L, 1.2), soft(R, 1.2)], axis=1)
    return out * (0.9 / np.max(np.abs(out)))


# ------------------------------------------------------------------------------------------------- write

def seeded(name):
    """Each sound its own random numbers (from its name): the same every run, whatever else is made before it."""
    global RNG
    RNG = np.random.default_rng(zlib.crc32(name.encode()))


def main():
    snd = os.path.join(ASSETS, "sounds")
    if len(sys.argv) > 1 and sys.argv[1] == "cars":  # (just the cars' sounds)
        p = os.path.join(ASSETS, "sounds.json")
        reg = json.load(open(p, encoding="utf-8"))
        cars(snd, reg)
        json.dump(reg, open(p, "w", encoding="utf-8", newline="\n"), indent=2)
        return
    for name in GUNS:
        for take in (1, 2, 3):
            seeded(f"gun/{name}_{take}")
            write_ogg(os.path.join(snd, "gun", f"{name}_{take}.ogg"), gun(name, take))
        print("gun", name)
    for take in (1, 2, 3):
        seeded(f"gun/rocket_launcher_{take}")
        write_ogg(os.path.join(snd, "gun", f"rocket_launcher_{take}.ogg"), rocket_launch(take))
        seeded(f"boom/explosion_{take}")
        write_ogg(os.path.join(snd, "boom", f"explosion_{take}.ogg"), explosion(take))
    seeded("boom/grenade_bounce")
    write_ogg(os.path.join(snd, "boom", "grenade_bounce.ogg"), grenade_bounce())
    print("rocket, explosions, grenade")
    seeded("storm/warning")
    write_ogg(os.path.join(snd, "storm", "warning.ogg"), storm_warning())
    print("storm warning")
    for i, intense in ((1, False), (2, True)):
        seeded(f"music/battle_{i}")
        write_ogg(os.path.join(snd, "music", f"battle_{i}.ogg"), track(intense), stereo=True, quality=4)
        print("battle", i)
    # the assets' sounds.json: these alongside what's there
    p = os.path.join(ASSETS, "sounds.json")
    reg = json.load(open(p, encoding="utf-8"))
    for name in GUNS:
        reg[f"gun.{name}"] = {"sounds": [f"minebloxroyale:gun/{name}_{k}" for k in (1, 2, 3)]}
    reg["storm.warning"] = {"sounds": ["minebloxroyale:storm/warning"]}
    reg["gun.rocket_launcher"] = {"sounds": [f"minebloxroyale:gun/rocket_launcher_{k}" for k in (1, 2, 3)]}
    reg["boom.explosion"] = {"sounds": [f"minebloxroyale:boom/explosion_{k}" for k in (1, 2, 3)]}
    reg["boom.grenade_bounce"] = {"sounds": ["minebloxroyale:boom/grenade_bounce"]}
    for i in (1, 2):
        reg[f"music.battle_{i}"] = {"sounds": [{"name": f"minebloxroyale:music/battle_{i}", "stream": True}]}
    cars(snd, reg)
    json.dump(reg, open(p, "w", encoding="utf-8", newline="\n"), indent=2)
    print("sounds.json:", len(reg), "events")


if __name__ == "__main__":
    main()
