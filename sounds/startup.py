"""Etesian startup sound: water drops on the boot ripple, then a warm, wide chord. 4.2 s, 48 kHz stereo.

Made from scratch (additive synthesis), with the same shape as the N9 boot sound so it lines up with
the boot animation's ripple (it plays with the second part, once the phone has booted): a drop per
ring, the big one as the wordmark lights up, then the chord.

    startup.py out.wav
"""
import sys, wave
import numpy as np

SR = 48000
DUR = 4.2
t = np.arange(int(SR * DUR)) / SR
rng = np.random.default_rng(1)


def hz(midi):
    return 440.0 * 2 ** ((midi - 69) / 12)


def env(start, attack, decay, length=None):
    """Exponential-decay envelope with a smooth (raised-cosine) attack."""
    x = t - start
    e = np.where(x < 0, 0.0, np.exp(-np.maximum(x - attack, 0) / decay))
    rise = np.clip(x / attack, 0, 1)
    e *= 0.5 - 0.5 * np.cos(np.pi * rise)
    if length is not None:
        e *= np.clip((start + length - t) / 0.4, 0, 1)
    return e


def bell(f, start, amp, decay=0.9):
    """Glassy bell: a pure fundamental plus slightly inharmonic upper partials that die faster."""
    out = np.zeros_like(t)
    for ratio, a, d in ((1.0, 1.0, 1.0), (2.76, 0.32, 0.45), (5.40, 0.12, 0.25), (8.93, 0.05, 0.15)):
        out += a * np.sin(2 * np.pi * f * ratio * t + rng.uniform(0, 6.28)) * env(start, 0.004, decay * d)
    return amp * out


def pad(f, start, amp, attack=0.6, decay=1.6):
    """Warm pad voice: three slightly detuned sines with a soft second harmonic."""
    out = np.zeros_like(t)
    for det in (-0.12, 0.0, 0.11):
        ff = f * 2 ** (det / 12)
        out += np.sin(2 * np.pi * ff * t + rng.uniform(0, 6.28)) + 0.18 * np.sin(4 * np.pi * ff * t)
    return amp * out / 3 * env(start, attack, decay)


left = np.zeros_like(t)
right = np.zeros_like(t)


def put(sig, pan):
    """Equal-power pan, -1 left .. 1 right."""
    global left, right
    a = (pan + 1) * np.pi / 4
    left += sig * np.cos(a)
    right += sig * np.sin(a)


def drop(f, start, amp, ring=0.45, splash=0.0):
    """A water drop: a fast upward pitch flick (the bubble) into a ringing tone, plus an optional splash."""
    x = t - start
    on = x >= 0
    # Pitch rises ~30% over the first 12 ms, then drifts down a little as the ring dies.
    glide = np.where(x < 0.012, 0.77 + 0.23 * np.clip(x / 0.012, 0, 1), 1.0 - 0.015 * np.clip(x - 0.012, 0, 1))
    phase = 2 * np.pi * np.cumsum(f * glide * on) / SR
    tone = np.sin(phase) + 0.25 * np.sin(2.01 * phase) * np.exp(-np.maximum(x, 0) / (ring * 0.3))
    out = amp * tone * env(start, 0.003, ring)
    if splash:
        hiss = rng.normal(0, 1, len(t))
        hiss = hiss - np.convolve(hiss, np.ones(6) / 6, 'same')        # keep the bright part
        out += splash * hiss * env(start, 0.001, 0.035)
    return out


# 1. Drops (the boot ripple): four drops on the ring beats, the third the big one.
for s, m, a, p, ring, splash in ((0.39, 93, 0.30, -0.35, 0.22, 0.0), (0.64, 90, 0.30, 0.4, 0.22, 0.02),
                                  (0.90, 86, 0.48, 0.0, 0.32, 0.10), (1.42, 88, 0.42, -0.2, 0.30, 0.05)):
    put(drop(hz(m), s, a, ring, splash), p)
    # The drop's echo off the far side of the pool: later, quieter, opposite side.
    put(drop(hz(m), s + 0.11, a * 0.12, ring * 0.7), -p)

# A soft air swell under the drops (filtered noise), like wind over the water.
noise = rng.normal(0, 1, len(t))
k = np.exp(-np.arange(400) / 60.0); k /= k.sum()
air = np.convolve(noise, k, 'same') - np.convolve(noise, np.ones(4000) / 4000, 'same')
put(0.12 * air * env(0.2, 0.8, 0.5), -0.3)
put(0.12 * np.roll(air, 977) * env(0.2, 0.8, 0.5), 0.3)

# 2. Bloom (from 1.2 s, under the last drop): D, A, E, F#, A spread wide (Dadd9).
chord = [(50, 0.30, 0.0), (57, 0.22, -0.25), (64, 0.18, 0.3), (66, 0.14, -0.5), (69, 0.10, 0.55),
         (76, 0.06, -0.75)]
for m, a, p in chord:
    put(pad(hz(m), 1.35, a * 0.7, attack=0.6, decay=0.32), p)

# A little room: short multi-tap reverb per side.
for sig_name in ('left', 'right'):
    sig = globals()[sig_name]
    wet = np.zeros_like(sig)
    for d, g in ((0.031, 0.35), (0.047, 0.3), (0.071, 0.25), (0.113, 0.2), (0.167, 0.15)):
        n = int(d * SR) + (3 if sig_name == 'right' else 0)
        wet[n:] += g * sig[:-n]
    globals()[sig_name] = sig + 0.6 * wet

out = np.stack([left, right], 1)
out *= np.clip((DUR - t) / 0.5, 0, 1)[:, None]          # fade to silence at the end
# Same loudness as the N9 boot sound: its loudest 0.2 s is about -26 dBFS RMS.
win = SR // 5
peak = max(np.sqrt(np.mean(out[i:i + win] ** 2)) for i in range(0, len(out) - win, win // 2))
out *= 1700 / peak
with wave.open(sys.argv[1], 'wb') as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR)
    w.writeframes(out.astype(np.int16).tobytes())
