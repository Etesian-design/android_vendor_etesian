"""Etesian's own interface sounds and tones, for builds without the user's N9 files.

Made from scratch (additive synthesis, like startup.py): soft, glassy, water-drop sounds that belong
with the startup sound, never copies of the N9's. The interface sounds take the N9 files' names, so
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


# Interface sounds, under the N9 files' names.
def ui():
    out = {}
    t = t_of(0.05); out["static_press"] = finish(click(t, 0, tone=2400), 0.5)
    t = t_of(0.05); out["static_release"] = finish(click(t, 0, tone=1700, decay=0.005), 0.35)
    t = t_of(0.04); out["vkb_popup_press"] = finish(click(t, 0, tone=3200, decay=0.004), 0.4)
    t = t_of(0.35); out["popup"] = finish(drop(t, 0, 79, length=0.12) + 0.5 * bell(t, 0.05, 86, 0.12), 0.55)
    # Closing: the drop falls instead of rising, one or a ripple of three.
    t = t_of(0.3); out["close"] = finish(drop(t, 0, 88, length=0.12, up=False), 0.5)
    t = t_of(0.45); out["close_multiple"] = finish(sum(drop(t, i * 0.07, 88 - 3 * i, 1 - 0.2 * i, 0.1, up=False)
                                                       for i in range(3)), 0.5)
    t = t_of(0.6); out["accessory_connected"] = finish(bell(t, 0, 76, 0.25) + bell(t, 0.11, 83, 0.35), 0.6)
    t = t_of(0.8); out["battery_low"] = finish(bell(t, 0, 81, 0.25) + bell(t, 0.16, 74, 0.45), 0.6)
    t = t_of(0.2); out["volume_max"] = finish(bell(t, 0, 88, 0.06, bright=0.1), 0.5)
    t = t_of(0.2); out["volume_min"] = finish(bell(t, 0, 76, 0.06, bright=0.1), 0.5)
    t = t_of(0.2); out["profile_ringer_level"] = finish(bell(t, 0, 81, 0.07, bright=0.1), 0.5)
    t = t_of(0.15); out["camera_af"] = finish(bell(t, 0, 93, 0.02, bright=0) + bell(t, 0.06, 93, 0.02, bright=0), 0.4)
    rng = np.random.default_rng(7)
    t = t_of(0.18); n = rng.standard_normal(len(t))
    out["camera_shutter"] = finish(n * (env(t, 0, 0.001, 0.012) + 0.7 * env(t, 0.07, 0.001, 0.02))
                                   + click(t, 0, 0.6, 1800, 0.01) + click(t, 0.07, 0.5, 1400, 0.015), 0.7)
    t = t_of(0.4); out["camera_video_record_start"] = finish(bell(t, 0, 79, 0.1) + bell(t, 0.1, 86, 0.18), 0.55)
    t = t_of(0.4); out["camera_video_record_stop"] = finish(bell(t, 0, 86, 0.1) + bell(t, 0.1, 79, 0.18), 0.55)
    return out


# Tones: a ringtone, three notifications and an alarm. Pentatonic (D major), so they never clash.
def tones():
    out = {}
    # Ringtone: a rising bell figure over soft fifths, 2 bars, played four times (with the drops of startup.py).
    bar = [(0, 74), (0.25, 78), (0.5, 81), (0.75, 86), (1.0, 83), (1.375, 81), (1.75, 78)]
    t = t_of(16)
    s = np.zeros_like(t)
    for r in range(4):
        b0 = r * 4
        for dt, m in bar:
            s += bell(t, b0 + dt, m, 0.9, 0.8)
        for dt, m in bar:
            s += bell(t, b0 + 2 + dt, m + (2 if dt >= 1.0 and r % 2 else 0), 0.9, 0.7)
        for k in (0, 2):
            s += 0.35 * bell(t, b0 + k, 50, 2.5, bright=0.05) + 0.25 * bell(t, b0 + k, 57, 2.5, bright=0.05)
        s += 0.4 * drop(t, b0 + 3.6, 91, length=0.08)
    out["Etesian"] = finish(s, 0.85, 0.5)
    t = t_of(1.4)
    out["Etesian_message"] = finish(drop(t, 0, 86, 0.6) + bell(t, 0.09, 81, 0.5) + bell(t, 0.22, 86, 0.6), 0.8, 0.2)
    t = t_of(1.6)
    out["Etesian_mail"] = finish(bell(t, 0, 78, 0.4) + bell(t, 0.14, 81, 0.4) + bell(t, 0.28, 85, 0.7), 0.8, 0.2)
    t = t_of(1.8)
    out["Etesian_calendar"] = finish(bell(t, 0, 81, 0.7) + bell(t, 0.3, 81, 0.7, 0.7) + bell(t, 0.6, 74, 0.9), 0.8, 0.2)
    # Alarm: gentle chimes that rise and get fuller over 24 s (it loops).
    t = t_of(24)
    s = np.zeros_like(t)
    seq = [74, 78, 81, 86, 81, 78]
    for i in range(48):
        g = 0.35 + 0.65 * min(1, i / 32)
        s += g * bell(t, i * 0.5, seq[i % 6] + (12 if i >= 24 and i % 3 == 0 else 0), 0.8, 0.9)
        if i % 6 == 0:
            s += g * 0.4 * bell(t, i * 0.5, 62, 2.5, bright=0.05)
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
