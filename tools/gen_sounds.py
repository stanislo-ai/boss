#!/usr/bin/env python3
"""
Synthesises every ScooterMC sound effect and writes the resource pack's sounds.json.

Minecraft cannot stream a looping sound at a continuously varying pitch, so the motor and the
tyre roll are generated as *exactly periodic* loops that the plugin re-triggers each cycle with
the pitch and gain computed from the current wheel speed.  "Exactly periodic" is the important
part: every partial - noise beds included - is synthesised on a frequency grid of 1/duration Hz,
so the last sample runs into the first with no click, and consecutive plays butt together
seamlessly.

Everything is mono; Minecraft only pans and attenuates mono samples, stereo ones play flat.

Usage:  python3 tools/gen_sounds.py
"""

import json
import math
import os

import numpy as np
import soundfile as sf

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
NS = "scootermc"
ASSETS = os.path.join(ROOT, "resourcepack", "assets", NS)
SOUND_DIR = os.path.join(ASSETS, "sounds", "scooter")

SR = 44100

# Loop lengths, also declared to the plugin so the retrigger period matches exactly.
MOTOR_LOOP = 0.50
ROLL_LOOP = 0.50


# ============================================================================================
# synthesis toolkit
# ============================================================================================

def n_samples(dur):
    return int(round(SR * dur))


def t_axis(dur):
    return np.arange(n_samples(dur)) / SR


def snap(freq, dur):
    """Nearest frequency that completes a whole number of cycles in `dur` - keeps loops seamless."""
    return max(1.0, round(freq * dur)) / dur


def tone(dur, freq, amp=1.0, phase=0.0, loop=False):
    f = snap(freq, dur) if loop else freq
    return amp * np.sin(2 * np.pi * f * t_axis(dur) + phase)


def saw_stack(dur, freq, n_harm, amp=1.0, rolloff=1.25, odd_boost=1.0, loop=False, seed=0):
    """A motor's electrical whine: a harmonic stack rather than a mathematically perfect saw."""
    rng = np.random.default_rng(seed)
    out = np.zeros(n_samples(dur))
    for k in range(1, n_harm + 1):
        a = (1.0 / k ** rolloff) * (odd_boost if k % 2 else 1.0)
        out += tone(dur, freq * k, a, rng.random() * 2 * np.pi, loop)
    return amp * out / max(1e-9, np.abs(out).max())


def periodic_noise(dur, shape_fn, seed=0):
    """
    Noise built in the frequency domain, so it is periodic over `dur` by construction.

    `shape_fn(freqs)` returns the magnitude envelope.
    """
    n = n_samples(dur)
    rng = np.random.default_rng(seed)
    freqs = np.fft.rfftfreq(n, 1.0 / SR)
    mag = shape_fn(freqs)
    mag[0] = 0.0
    phase = rng.random(len(freqs)) * 2 * np.pi
    sig = np.fft.irfft(mag * np.exp(1j * phase), n)
    return sig / max(1e-9, np.abs(sig).max())


def band(lo, hi, slope=2.0):
    def shape(f):
        m = np.zeros_like(f)
        inside = (f > 0)
        with np.errstate(divide="ignore", invalid="ignore"):
            hp = 1.0 / (1.0 + (lo / np.maximum(f, 1e-9)) ** slope)
            lp = 1.0 / (1.0 + (np.maximum(f, 1e-9) / hi) ** slope)
        m[inside] = (hp * lp)[inside]
        return m
    return shape


def noise(dur, lo, hi, seed=0, slope=2.0):
    return periodic_noise(dur, band(lo, hi, slope), seed)


def env(dur, attack, decay, sustain=0.0, release=None, curve=2.5):
    """Simple AD/ADSR envelope; `release` defaults to whatever time is left."""
    n = n_samples(dur)
    a = max(1, int(attack * SR))
    d = max(1, int(decay * SR))
    e = np.zeros(n)
    a = min(a, n)
    e[:a] = np.linspace(0.0, 1.0, a) ** 0.6
    rest = n - a
    if rest <= 0:
        return e
    if sustain <= 0.0:
        k = np.linspace(0.0, 1.0, rest)
        e[a:] = np.exp(-curve * 3.0 * k * (dur / max(decay, 1e-3)) / 8.0)
    else:
        d = min(d, rest)
        e[a:a + d] = np.linspace(1.0, sustain, d)
        r = rest - d
        if r > 0:
            rl = release if release is not None else r / SR
            k = np.linspace(0.0, 1.0, r)
            e[a + d:] = sustain * np.exp(-curve * k * (r / SR) / max(rl, 1e-3))
    return e


def sweep(dur, f0, f1, amp=1.0, shape="lin"):
    t = t_axis(dur)
    if shape == "exp":
        f = f0 * (f1 / f0) ** (t / dur)
        phase = 2 * np.pi * f0 * dur / math.log(f1 / f0) * ((f1 / f0) ** (t / dur) - 1.0)
    else:
        f = f0 + (f1 - f0) * t / dur
        phase = 2 * np.pi * (f0 * t + 0.5 * (f1 - f0) * t * t / dur)
    return amp * np.sin(phase)


def partials(dur, base, ratios, amps, decays, jitter=0.0, seed=0):
    """Inharmonic partials - bells, metallic clatter, spring twang."""
    rng = np.random.default_rng(seed)
    t = t_axis(dur)
    out = np.zeros_like(t)
    for r, a, d in zip(ratios, amps, decays):
        f = base * r * (1.0 + rng.normal(0, jitter))
        out += a * np.sin(2 * np.pi * f * t + rng.random() * 6.283) * np.exp(-t / d)
    return out


def click(dur, amp=1.0, bright=6000.0, decay=0.004, seed=0):
    return amp * noise(dur, 200, bright, seed) * np.exp(-t_axis(dur) / decay)


def beep(dur, freq, amp=1.0, square=0.35):
    """A piezo-ish beep: sine plus a touch of odd harmonics, with click-free edges."""
    t = t_axis(dur)
    s = np.sin(2 * np.pi * freq * t)
    s += square * np.sin(2 * np.pi * 3 * freq * t) / 3.0
    s += square * 0.5 * np.sin(2 * np.pi * 5 * freq * t) / 5.0
    ramp = max(1, int(0.004 * SR))
    e = np.ones_like(t)
    e[:ramp] = np.linspace(0, 1, ramp)
    e[-ramp:] = np.linspace(1, 0, ramp)
    return amp * s * e


def place(total, pieces):
    """Lay clips onto a silent buffer at given offsets, in seconds."""
    out = np.zeros(n_samples(total))
    for offset, clip in pieces:
        i = int(offset * SR)
        j = min(len(out), i + len(clip))
        if i < len(out):
            out[i:j] += clip[:j - i]
    return out


def normalise(x, peak=0.89):
    m = float(np.abs(x).max())
    return x if m < 1e-9 else x * (peak / m)


def soft_clip(x, drive=1.0):
    return np.tanh(x * drive) / math.tanh(max(drive, 1e-6))


def fade_edges(x, ms=6.0):
    k = max(1, int(ms * SR / 1000.0))
    k = min(k, len(x) // 2)
    x = x.copy()
    x[:k] *= np.linspace(0, 1, k)
    x[-k:] *= np.linspace(1, 0, k)
    return x


# ============================================================================================
# the sounds
# ============================================================================================

def s_motor_loop():
    """
    600 W brushless hub motor.

    A BLDC's dominant tone is the electrical fundamental (pole pairs x mechanical rpm), with the
    PWM switching whine roughly two octaves above it and sidebands either side.  On top of that
    sit bearing rumble and the cogging modulation you hear at low speed.
    """
    d = MOTOR_LOOP
    f0 = 112.0
    sig = saw_stack(d, f0, 14, amp=0.62, rolloff=1.22, odd_boost=1.35, loop=True, seed=3)
    # PWM carrier plus its first sidebands
    carrier = snap(4480.0, d)
    sig += tone(d, carrier, 0.10, loop=True)
    sig += tone(d, carrier - f0 * 6, 0.045, loop=True)
    sig += tone(d, carrier + f0 * 6, 0.045, loop=True)
    # magnetic hum an octave down gives the motor some weight
    sig += tone(d, f0 / 2.0, 0.16, loop=True)
    # bearings and airflow
    sig += 0.10 * noise(d, 260, 5200, seed=11)
    sig += 0.05 * noise(d, 40, 320, seed=12, slope=3.0)
    # cogging: gentle AM at six times the electrical fundamental
    cog = 1.0 + 0.11 * np.sin(2 * np.pi * snap(f0 * 6, d) * t_axis(d))
    sig *= cog
    return normalise(soft_clip(sig, 1.5), 0.72)


def s_tyre_roll():
    """Rolling noise: broadband road hiss with the tread's 16-lug pattern beating through it."""
    d = ROLL_LOOP
    sig = 0.9 * noise(d, 180, 4200, seed=21)
    sig += 0.45 * noise(d, 60, 700, seed=22, slope=3.0)
    lug = 1.0 + 0.28 * np.sin(2 * np.pi * snap(16 * 4.0, d) * t_axis(d))
    return normalise(sig * lug, 0.55)


def s_throttle():
    """The surge as the controller ramps current in."""
    d = 0.55
    sig = sweep(d, 90, 260, 0.55, "exp") * env(d, 0.03, 0.30)
    sig += sweep(d, 3200, 5400, 0.12, "exp") * env(d, 0.05, 0.35)
    sig += 0.10 * noise(d, 300, 3000, seed=31) * env(d, 0.01, 0.20)
    return normalise(fade_edges(sig), 0.65)


def s_brake():
    """
    120 mm disc.  Pad rub is band-limited noise; the squeal is a high formant that climbs a
    little as the rotor slows, then dies with it.
    """
    d = 0.85
    t = t_axis(d)
    rub = noise(d, 700, 6500, seed=41) * (0.45 * np.exp(-t / 0.42))
    squeal = sweep(d, 2750, 3350, 0.30) * (np.exp(-t / 0.5) * (1 - np.exp(-t / 0.06)))
    squeal += sweep(d, 5500, 6700, 0.10) * (np.exp(-t / 0.4) * (1 - np.exp(-t / 0.08)))
    grind = noise(d, 120, 900, seed=42, slope=3.0) * 0.18 * np.exp(-t / 0.3)
    return normalise(fade_edges(rub + squeal + grind), 0.7)


def s_horn():
    """Electric scooter horn: a loud, slightly warbling two-tone buzzer."""
    d = 0.48
    t = t_axis(d)
    warble = 1.0 + 0.012 * np.sin(2 * np.pi * 34 * t)
    sig = np.sin(2 * np.pi * 2050 * t * warble) * 0.6
    sig += np.sin(2 * np.pi * 2560 * t * warble) * 0.45
    sig += np.sin(2 * np.pi * 4100 * t) * 0.14
    sig += np.sin(2 * np.pi * 1025 * t) * 0.18
    e = np.ones_like(t)
    a = int(0.008 * SR)
    e[:a] = np.linspace(0, 1, a)
    e[-int(0.03 * SR):] = np.linspace(1, 0, int(0.03 * SR))
    return normalise(soft_clip(sig * e, 2.2), 0.70)


def s_bell():
    """Mechanical ding for the polite version of the horn."""
    d = 1.30
    sig = partials(d, 2180,
                   ratios=[1.0, 2.01, 3.04, 4.21, 5.47, 6.83],
                   amps=[1.0, 0.62, 0.40, 0.26, 0.16, 0.10],
                   decays=[0.55, 0.42, 0.30, 0.22, 0.16, 0.12], jitter=0.004, seed=51)
    sig += click(d, 0.35, 9000, 0.003, seed=52)
    return normalise(fade_edges(sig), 0.8)


def s_kick():
    """Kick-off: sole scuffing tarmac plus the deck taking the load."""
    d = 0.42
    t = t_axis(d)
    scuff = noise(d, 420, 5200, seed=61) * (np.exp(-t / 0.10) * (1 - np.exp(-t / 0.012)))
    thud = np.sin(2 * np.pi * 92 * t) * 0.5 * np.exp(-t / 0.07)
    rattle = noise(d, 900, 2600, seed=62) * 0.15 * np.exp(-t / 0.05)
    return normalise(fade_edges(scuff * 0.7 + thud + rattle), 0.7)


def s_crash():
    """Hitting something hard: aluminium clatter, a tyre slap and a bit of scrape."""
    d = 1.15
    hits = []
    for i, (off, base, amp) in enumerate([(0.0, 320, 1.0), (0.055, 210, 0.6),
                                          (0.14, 480, 0.45), (0.26, 260, 0.3),
                                          (0.42, 620, 0.2)]):
        clip = partials(0.7, base,
                        ratios=[1.0, 1.73, 2.41, 3.19, 4.62, 6.1],
                        amps=[1.0, 0.7, 0.5, 0.35, 0.22, 0.14],
                        decays=[0.16, 0.12, 0.09, 0.07, 0.05, 0.04],
                        jitter=0.02, seed=70 + i) * amp
        clip += click(0.7, 0.5 * amp, 8000, 0.005, seed=80 + i)
        hits.append((off, clip))
    sig = place(d, hits)
    t = t_axis(d)
    sig += noise(d, 150, 3500, seed=91) * 0.25 * np.exp(-t / 0.18)
    return normalise(fade_edges(soft_clip(sig, 1.3)), 0.80)


def s_power_on():
    """Controller boot: relay click then a rising confirmation triad."""
    d = 0.75
    return normalise(place(d, [
        (0.00, click(0.08, 0.5, 5000, 0.006, seed=101)),
        (0.07, beep(0.09, 880, 0.55)),
        (0.19, beep(0.09, 1174, 0.6)),
        (0.31, beep(0.16, 1568, 0.65)),
    ]), 0.7)


def s_power_off():
    d = 0.55
    return normalise(place(d, [
        (0.00, beep(0.10, 1174, 0.6)),
        (0.13, beep(0.18, 784, 0.55)),
        (0.34, click(0.08, 0.35, 4000, 0.006, seed=111)),
    ]), 0.65)


def s_beep():
    return normalise(beep(0.075, 1568, 0.7), 0.6)


def s_low_battery():
    d = 0.95
    return normalise(place(d, [
        (0.00, beep(0.11, 1000, 0.6)),
        (0.18, beep(0.11, 1000, 0.6)),
        (0.36, beep(0.24, 760, 0.6)),
    ]), 0.65)


def s_mode():
    d = 0.30
    return normalise(place(d, [
        (0.00, beep(0.055, 1318, 0.55)),
        (0.08, beep(0.09, 1760, 0.6)),
    ]), 0.6)


def s_deploy():
    """Setting the scooter down: kickstand clack, a tyre bump and the springs settling."""
    d = 0.75
    t = t_axis(d)
    sig = place(d, [
        (0.00, click(0.10, 0.55, 7000, 0.005, seed=121)),
        (0.03, partials(0.5, 430, [1, 2.2, 3.4, 4.7], [1, .5, .3, .2],
                        [.10, .08, .06, .04], 0.01, 122) * 0.5),
        (0.16, np.sin(2 * np.pi * 78 * t_axis(0.3)) * 0.45 * np.exp(-t_axis(0.3) / 0.06)),
        (0.22, partials(0.45, 260, [1, 1.9, 3.1], [1, .5, .3], [.13, .09, .06], 0.02, 123) * 0.3),
    ])
    sig += noise(d, 400, 3000, seed=124) * 0.10 * np.exp(-t / 0.05)
    return normalise(fade_edges(sig), 0.72)


def s_pickup():
    """One-step fold and lift."""
    d = 0.70
    return normalise(fade_edges(place(d, [
        (0.00, noise(0.22, 300, 2600, seed=131) * 0.35 * np.exp(-t_axis(0.22) / 0.06)),
        (0.10, partials(0.4, 520, [1, 2.1, 3.3, 4.9], [1, .55, .32, .2],
                        [.09, .07, .05, .04], 0.015, 132) * 0.55),
        (0.26, partials(0.4, 340, [1, 1.8, 2.9], [1, .5, .3], [.11, .08, .05], 0.02, 133) * 0.45),
    ])), 0.72)


def s_suspension():
    """Landing: the four-arm coil-overs compressing and rebounding."""
    d = 0.55
    t = t_axis(d)
    thud = np.sin(2 * np.pi * 62 * t) * 0.6 * np.exp(-t / 0.055)
    thud += np.sin(2 * np.pi * 124 * t) * 0.25 * np.exp(-t / 0.04)
    boing = np.sin(2 * np.pi * (760 - 380 * np.clip(t / 0.25, 0, 1)) * t) * 0.18 * np.exp(-t / 0.12)
    rattle = noise(d, 500, 4200, seed=141) * 0.13 * np.exp(-t / 0.05)
    return normalise(fade_edges(thud + boing + rattle), 0.72)


def s_regen():
    """Regenerative braking: the motor whine descending with a bit of electrical fizz."""
    d = 0.70
    t = t_axis(d)
    sig = sweep(d, 3200, 900, 0.30, "exp") * np.exp(-t / 0.45)
    sig += sweep(d, 220, 90, 0.28, "exp") * np.exp(-t / 0.4)
    sig += noise(d, 1500, 7000, seed=151) * 0.09 * np.exp(-t / 0.25)
    return normalise(fade_edges(sig), 0.6)


def s_lock():
    d = 0.40
    return normalise(fade_edges(place(d, [
        (0.00, click(0.06, 0.5, 6000, 0.004, seed=161)),
        (0.04, partials(0.3, 900, [1, 2.3, 3.7], [1, .5, .3], [.05, .04, .03], 0.01, 162) * 0.5),
        (0.13, beep(0.07, 1046, 0.45)),
    ])), 0.6)


# name -> (generator, subtitle, attenuation distance)
SOUNDS = {
    "motor_loop": (s_motor_loop, "Scooter motor", 24),
    "tyre_roll": (s_tyre_roll, "Tyres rolling", 16),
    "throttle": (s_throttle, "Throttle", 20),
    "brake": (s_brake, "Brakes squeal", 20),
    "regen": (s_regen, "Regenerative braking", 18),
    "horn": (s_horn, "Scooter horn", 40),
    "bell": (s_bell, "Scooter bell", 32),
    "kick": (s_kick, "Kick off", 16),
    "crash": (s_crash, "Scooter crashes", 24),
    "power_on": (s_power_on, "Scooter powers up", 16),
    "power_off": (s_power_off, "Scooter shuts down", 16),
    "beep": (s_beep, "Scooter beeps", 16),
    "mode": (s_mode, "Drive mode changed", 16),
    "low_battery": (s_low_battery, "Battery low", 16),
    "deploy": (s_deploy, "Scooter set down", 16),
    "pickup": (s_pickup, "Scooter folded", 16),
    "suspension": (s_suspension, "Suspension compresses", 16),
    "lock": (s_lock, "Scooter locked", 16),
}


def write_ogg(path, sig, ceiling=0.92):
    """
    Encode, decode, and pull the gain back until the *decoded* peak fits under the ceiling.

    Vorbis is lossy, so a signal encoded at 0.7 can come back at 1.14 - bright, near-square
    material overshoots badly.  Minecraft converts to 16-bit on the way to OpenAL, where that
    overshoot is audible clipping, so the level that matters is the one after decoding.
    """
    gain = 1.0
    for _ in range(8):
        sf.write(path, (sig * gain).astype(np.float32), SR, format="OGG", subtype="VORBIS")
        peak = float(np.abs(sf.read(path)[0]).max())
        if peak <= ceiling or peak < 1e-6:
            return peak, gain
        gain *= (ceiling / peak) * 0.985
    return peak, gain


def check_loop(name, sig, tolerance=0.06):
    """A loop is only seamless if the wrap-around step is no worse than a normal sample step."""
    step = abs(float(sig[0] - sig[-1]))
    typical = float(np.abs(np.diff(sig)).mean())
    if step > max(tolerance, typical * 25):
        raise SystemExit(f"FAIL: {name} does not loop cleanly (wrap step {step:.4f}, "
                         f"mean step {typical:.4f})")
    return step, typical


def main():
    os.makedirs(SOUND_DIR, exist_ok=True)
    entries = {}
    subtitles = {}
    total = 0
    for name, (fn, subtitle, dist) in SOUNDS.items():
        sig = fn().astype(np.float32)
        if name in ("motor_loop", "tyre_roll"):
            step, typical = check_loop(name, sig)
            note = f"  loop seam {step:.5f} (mean step {typical:.5f})"
        else:
            note = ""
        path = os.path.join(SOUND_DIR, f"{name}.ogg")
        peak, gain = write_ogg(path, sig)
        if peak > 0.95:
            raise SystemExit(f"FAIL: {name} still decodes at {peak:.3f}")
        size = os.path.getsize(path)
        total += size
        trim = "" if gain > 0.999 else f"  (gain {gain:.2f})"
        print(f"  scooter.{name:<13} {len(sig) / SR:5.2f}s  {size / 1024:6.1f} KiB  "
              f"peak {peak:.2f}{trim}{note}")

        key = f"subtitles.{NS}.scooter.{name}"
        entries[f"scooter.{name}"] = {
            "category": "neutral",
            "subtitle": key,
            "sounds": [{"name": f"{NS}:scooter/{name}", "stream": False,
                        "attenuation_distance": dist}],
        }
        subtitles[key] = subtitle

    with open(os.path.join(ASSETS, "sounds.json"), "w", encoding="utf-8") as fh:
        json.dump(entries, fh, indent=2)
        fh.write("\n")

    lang_dir = os.path.join(ASSETS, "lang")
    os.makedirs(lang_dir, exist_ok=True)
    with open(os.path.join(lang_dir, "en_us.json"), "w", encoding="utf-8") as fh:
        json.dump(subtitles, fh, indent=2, ensure_ascii=False)
        fh.write("\n")
    pl = {
        "motor_loop": "Silnik hulajnogi", "tyre_roll": "Toczenie opon", "throttle": "Manetka",
        "brake": "Pisk hamulców", "regen": "Hamowanie odzyskowe", "horn": "Klakson hulajnogi",
        "bell": "Dzwonek hulajnogi", "kick": "Odbicie nogą", "crash": "Hulajnoga uderza",
        "power_on": "Hulajnoga włącza się", "power_off": "Hulajnoga wyłącza się",
        "beep": "Sygnał hulajnogi", "mode": "Zmiana trybu jazdy", "low_battery": "Niski poziom baterii",
        "deploy": "Hulajnoga postawiona", "pickup": "Hulajnoga złożona",
        "suspension": "Amortyzatory uginają się", "lock": "Hulajnoga zablokowana",
    }
    with open(os.path.join(lang_dir, "pl_pl.json"), "w", encoding="utf-8") as fh:
        json.dump({f"subtitles.{NS}.scooter.{k}": v for k, v in pl.items()},
                  fh, indent=2, ensure_ascii=False)
        fh.write("\n")

    # the plugin needs the loop lengths to schedule retriggering
    meta_path = os.path.join(ROOT, "src", "main", "resources", "sounds.json")
    with open(meta_path, "w", encoding="utf-8") as fh:
        json.dump({
            "_comment": "Generated by tools/gen_sounds.py - do not edit by hand.",
            "namespace": NS,
            "loops": {"scooter.motor_loop": MOTOR_LOOP, "scooter.tyre_roll": ROLL_LOOP},
            "keys": sorted(f"{NS}:scooter.{n}" for n in SOUNDS),
        }, fh, indent=2)
        fh.write("\n")

    print(f"\n  {len(SOUNDS)} sounds, {total / 1024:.0f} KiB total")


if __name__ == "__main__":
    main()
