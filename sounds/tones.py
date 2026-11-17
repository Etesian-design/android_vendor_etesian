"""Etesian's own interface sounds and tones, for builds without the user's N9 files.

Made from scratch: wind and breath (filtered noise) and blown tones, as the name Etesian, never copies of
the N9's. The interface sounds take the N9 files' names, so
whatever plays an N9 sound plays Etesian's in its place.

    tones.py <out dir>     writes <out>/ui/*.wav (48 kHz mono) and <out>/tones/*.wav (to encode to Ogg)
"""
import os
import sys
import wave

import numpy as np

SR = 48000


def hz(midi):
    return 440.0 * 2 ** ((midi - 69) / 12)


def t_of(dur):
    return np.arange(int(SR * dur)) / SR


def env(t, start, attack, decay):
    x = t - start
    e = np.where(x < 0, 0.0, np.exp(-np.maximum(x - attack, 0) / decay))
    return e * (0.5 - 0.5 * np.cos(np.pi * np.clip(x / attack, 0, 1)))


def bell(t, start, midi, decay=0.6, gain=1.0, bright=0.35):
    """A soft glass bell: a sine with two inharmonic partials that die away faster."""
    f = hz(midi)
    x = t - start
    s = np.sin(2 * np.pi * f * x) * env(t, start, 0.004, decay)
    s += bright * np.sin(2 * np.pi * f * 2.76 * x) * env(t, start, 0.002, decay * 0.35)
    s += bright * 0.5 * np.sin(2 * np.pi * f * 5.4 * x) * env(t, start, 0.001, decay * 0.15)
    return gain * s


def drop(t, start, midi, gain=1.0, length=0.09, up=True):
    """A water drop: a short sine gliding up a fifth, as a drop's pitch rises when it lands (down: going away)."""
    x = np.clip(t - start, 0, None)
    glide = 0.5 * np.clip(x / length, 0, 1) ** 0.6
    f = hz(midi) * (1 + glide if up else 1 - glide / 1.5)
    ph = 2 * np.pi * np.cumsum(f) / SR
    return gain * np.sin(ph) * env(t, start, 0.002, length * 0.6)


def click(t, start, gain=1.0, tone=2600, decay=0.006):
    """A soft tap: a damped sine burst with a breath of noise."""
    x = t - start
    rng = np.random.default_rng(int(tone))
    s = np.sin(2 * np.pi * tone * x) + 0.25 * rng.standard_normal(len(t))
    return gain * s * env(t, start, 0.0006, decay)


def wind(t, start, dur, f0, f1, width=0.35, gain=1.0, seed=1, attack=None):
    """A breath of wind: noise through a soft band that glides from f0 to f1 Hz (a gust, a whoosh).

    Filtered block by block in the frequency domain (overlapping Hann windows), so the band can move."""
    n = len(t)
    rng = np.random.default_rng(seed)
    noise = rng.standard_normal(n)
    out = np.zeros(n)
    blk, hop = 1024, 256
    win = np.hanning(blk)
    freqs = np.fft.rfftfreq(blk, 1 / SR)
    for a in range(0, n, hop):
        seg = noise[a:a + blk]
        if len(seg) < blk:
            seg = np.pad(seg, (0, blk - len(seg)))
        x = np.clip((a / SR - start) / dur, 0, 1)
        fc = f0 * (f1 / f0) ** x          # glides evenly in pitch
        band = np.exp(-0.5 * (np.log2(np.maximum(freqs, 1) / fc) / width) ** 2)
        y = np.fft.irfft(np.fft.rfft(seg * win) * band, blk) * win
        m = min(blk, n - a)
        out[a:a + m] += y[:m]
    out /= np.max(np.abs(out)) or 1
    a = attack if attack is not None else dur * 0.35
    x = t - start
    shape = np.where((x < 0) | (x > dur), 0.0,
                     np.sin(np.pi / 2 * np.clip(x / a, 0, 1)) ** 2 * np.clip((dur - x) / max(dur - a, 1e-3), 0, 1) ** 1.5)
    return gain * out * shape


def air(t, start, midi, dur, gain=1.0, breath=0.5, seed=3, vibrato=0.004):
    """A tone blown across a bottle: a soft sine with a slow sway and a band of breath around its pitch."""
    f = hz(midi)
    x = np.clip(t - start, 0, None)
    ph = 2 * np.pi * np.cumsum(f * (1 + vibrato * np.sin(2 * np.pi * 4.5 * x))) / SR
    a = min(0.08, dur * 0.3)
    shape = np.where((t < start) | (x > dur), 0.0,
                     np.sin(np.pi / 2 * np.clip(x / a, 0, 1)) ** 2 * np.exp(-np.maximum(x - a, 0) / (dur * 0.45)))
    tone = (np.sin(ph) + 0.12 * np.sin(2 * ph)) * shape
    return gain * (tone + breath * 0.6 * wind(t, start, dur, f * 0.98, f * 1.02, 0.12, seed=seed, attack=a))


def finish(s, peak=0.7, fade=0.01):
    s = s / (np.max(np.abs(s)) or 1) * peak
    n = int(SR * fade)
    if n and len(s) > n:
        s[-n:] *= np.linspace(1, 0, n)
    return s


def write(path, s):
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((np.clip(s, -1, 1) * 32767).astype("<i2").tobytes())


# Interface sounds, under the N9 files' names. Wind and breath, as the name Etesian (the summer winds of the
# Aegean): gusts that rise when something opens, fall away when it closes, and tones blown rather than struck.
def ui():
    out = {}
    out["silence"] = np.zeros(int(SR * 0.02))  # Android's tap click: the N9 has no sound on a plain tap
    t = t_of(0.09); out["static_press"] = finish(wind(t, 0, 0.08, 1500, 2200, 0.4, attack=0.012), 0.45)
    t = t_of(0.07); out["static_release"] = finish(wind(t, 0, 0.06, 2200, 1500, 0.4, seed=2, attack=0.01), 0.3)
    t = t_of(0.05); out["vkb_popup_press"] = finish(wind(t, 0, 0.045, 3200, 3600, 0.3, seed=4, attack=0.006), 0.35)
    # Long press, edit mode: a gust that rises, with a faint blown note at its top.
    t = t_of(0.35)
    out["popup"] = finish(wind(t, 0, 0.26, 900, 2800, 0.45, seed=5, attack=0.12) + 0.35 * air(t, 0.12, 86, 0.2, breath=0.3), 0.55)
    # Closing: the gust falls away; a ripple of three for closing all.
    t = t_of(0.32); out["close"] = finish(wind(t, 0, 0.3, 3000, 700, 0.4, seed=6, attack=0.05), 0.5)
    t = t_of(0.5); out["close_multiple"] = finish(sum((1 - 0.2 * i) * wind(t, i * 0.08, 0.3, 3200 - 300 * i, 650, 0.4,
                                                                             seed=7 + i, attack=0.05) for i in range(3)), 0.5)
    t = t_of(0.7); out["accessory_connected"] = finish(air(t, 0, 76, 0.3) + air(t, 0.12, 83, 0.45, seed=4)
                                                       + 0.3 * wind(t, 0, 0.4, 800, 2400, seed=9), 0.6)
    t = t_of(0.9); out["battery_low"] = finish(air(t, 0, 81, 0.35) + air(t, 0.18, 74, 0.6, seed=4), 0.6)
    t = t_of(0.22); out["volume_max"] = finish(air(t, 0, 88, 0.18, breath=0.3), 0.5)
    t = t_of(0.22); out["volume_min"] = finish(air(t, 0, 76, 0.18, breath=0.3), 0.5)
    t = t_of(0.22); out["profile_ringer_level"] = finish(air(t, 0, 81, 0.18, breath=0.3), 0.5)
    t = t_of(0.16); out["camera_af"] = finish(wind(t, 0, 0.04, 4000, 4400, 0.25, seed=10, attack=0.005)
                                              + wind(t, 0.07, 0.04, 4400, 4800, 0.25, seed=11, attack=0.005), 0.4)
    rng = np.random.default_rng(7)
    t = t_of(0.18); n = rng.standard_normal(len(t))
    out["camera_shutter"] = finish(n * (env(t, 0, 0.001, 0.012) + 0.7 * env(t, 0.07, 0.001, 0.02))
                                   + click(t, 0, 0.6, 1800, 0.01) + click(t, 0.07, 0.5, 1400, 0.015), 0.7)
    t = t_of(0.45); out["camera_video_record_start"] = finish(air(t, 0, 79, 0.15) + air(t, 0.1, 86, 0.3, seed=4), 0.55)
    t = t_of(0.45); out["camera_video_record_stop"] = finish(air(t, 0, 86, 0.15) + air(t, 0.1, 79, 0.3, seed=4), 0.55)
    return out


# Tones: a ringtone, three notifications and an alarm, blown over the wind. Pentatonic (D major), so they never clash.
def tones():
    out = {}
    # Ringtone: a rising blown figure over a swelling wind, 2 bars, four times.
    bar = [(0, 74, 0.3), (0.25, 78, 0.3), (0.5, 81, 0.3), (0.75, 86, 0.5), (1.25, 83, 0.3), (1.5, 81, 0.6)]
    t = t_of(16)
    s = np.zeros_like(t)
    for r in range(4):
        b0 = r * 4
        for half in (0, 2):
            for dt, m, d in bar:
                s += 0.8 * air(t, b0 + half + dt, m + (2 if half and dt >= 1.0 and r % 2 else 0), d, seed=int(dt * 8) + 1)
        s += 0.35 * wind(t, b0, 3.8, 350, 1400, 0.5, seed=20 + r, attack=1.6)
    out["Etesian"] = finish(s, 0.85, 0.5)
    t = t_of(1.3)
    out["Etesian_message"] = finish(0.5 * wind(t, 0, 0.35, 900, 2600, seed=31, attack=0.15) + air(t, 0.12, 81, 0.35)
                                    + air(t, 0.3, 86, 0.7, seed=4), 0.8, 0.2)
    t = t_of(1.5)
    out["Etesian_mail"] = finish(air(t, 0, 78, 0.35) + air(t, 0.16, 81, 0.35, seed=4) + air(t, 0.32, 85, 0.8, seed=5)
                                 + 0.3 * wind(t, 0.2, 0.9, 2400, 700, seed=32), 0.8, 0.2)
    t = t_of(1.8)
    out["Etesian_calendar"] = finish(air(t, 0, 81, 0.5) + 0.7 * air(t, 0.32, 81, 0.5, seed=4) + air(t, 0.64, 74, 1.0, seed=5),
                                     0.8, 0.2)
    # Alarm: a morning wind that rises and fills, blown notes getting stronger over 24 s (it loops).
    t = t_of(24)
    s = np.zeros_like(t)
    seq = [74, 78, 81, 86, 81, 78]
    for i in range(48):
        g = 0.35 + 0.65 * min(1, i / 32)
        s += g * air(t, i * 0.5, seq[i % 6] + (12 if i >= 24 and i % 3 == 0 else 0), 0.45, seed=i % 5 + 1)
    for k in range(6):
        s += (0.2 + 0.08 * k) * wind(t, k * 4, 4, 300, 1600, 0.5, seed=40 + k, attack=1.8)
    out["Etesian_dawn"] = finish(s, 0.85, 0.3)
    return out


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.abspath(__file__))
    for sub, sounds in (("ui", ui()), ("tones", tones())):
        os.makedirs(os.path.join(root, sub), exist_ok=True)
        for name, s in sounds.items():
            write(os.path.join(root, sub, name + ".wav"), s)


if __name__ == "__main__":
    main()
