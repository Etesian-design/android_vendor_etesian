"""Etesian boot animation: the N9 frames with the NOKIA wordmark swapped for ETESIAN.

Per frame: rebuild the background under the wordmark by blending the rows just above and below,
fit the old wordmark as  a*letters + b*glow(letters), and draw ETESIAN with the same a and b.
"""
import os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

SRC, OUT, FONT = sys.argv[1], sys.argv[2], sys.argv[3]
# Optional: variation axes as 'wght,wdth' (or 'none' for a static font), and only some frames.
AXES = os.environ.get('AXES', '800,125')
ONLY = os.environ.get('ONLY', '').split(',') if os.environ.get('ONLY') else None
W, H = 1080, 2160            # the N9 frames, where everything is measured and fitted
# The size drawn (OUT_SIZE=WxH, default the N9's): the same picture scaled to the width, the background
# continued above and below for a taller screen (1440x3120 on the OnePlus 7 Pro).
OW, OH = (int(v) for v in os.environ.get('OUT_SIZE', f'{W}x{H}').split('x'))
S = OW / W
OFFY = (OH / S - H) / 2      # extra N9 rows above (and below) the N9 frame
Y0, Y1 = 1010, 1175          # rows replaced (wordmark + glow)
X0, X1 = 200, 880            # columns replaced
CAP_TOP, CAP_BOT = 1051, 1105  # NOKIA cap height on the N9 frames
GLOW = 14


def axes(f):
    if AXES != 'none':
        f.set_variation_by_axes([float(v) for v in AXES.split(',')])


def wordmark(text, scale=1.0, cy=(CAP_TOP + CAP_BOT) / 2, wdth=125, wght=800, track=0.03, out=False):
    """The wordmark, on the N9 frame (or, [out], on the drawn size: same place, scaled)."""
    cw, ch, k = (OW, OH, S) if out else (W, H, 1.0)
    if out:
        cy = (cy + OFFY) * S
    scale *= k
    f = ImageFont.truetype(FONT, 200)
    axes(f)
    # Scale so the caps are exactly as tall as NOKIA's.
    l, t, r, b = f.getbbox('E')
    cap = (CAP_BOT - CAP_TOP + 1) * scale
    size = round(200 * cap / (b - t))
    f = ImageFont.truetype(FONT, size)
    axes(f)
    l, t, r, b = f.getbbox('E')
    adv = [f.getlength(c) for c in text]
    gap = track * size
    total = sum(adv) + gap * (len(text) - 1)
    img = Image.new('L', (cw, ch), 0)
    d = ImageDraw.Draw(img)
    x = (cw - total) / 2
    for c, a in zip(text, adv):
        d.text((x, cy - (b - t) / 2 - t), c, font=f, fill=255)
        x += a + gap
    return np.asarray(img).astype(float) / 255


def background(a):
    """Blend each column from the rows above Y0 to the rows below Y1."""
    bg = a.copy()
    top = a[Y0 - 4:Y0].mean(0)
    bot = a[Y1:Y1 + 4].mean(0)
    t = ((np.arange(Y0, Y1) - Y0 + 0.5) / (Y1 - Y0))[:, None, None]
    bg[Y0:Y1] = top[None] * (1 - t) + bot[None] * t
    return bg


CX, CY, ASPECT = 540, 1078, 1.3
yy, xx = np.mgrid[0:H, 0:W]
RHO = np.hypot(xx - CX, (yy - CY) / ASPECT)
RBIN = np.minimum(RHO.astype(int), 1300)
OUTSIDE = np.ones((H, W), bool)
OUTSIDE[Y0:Y1, X0:X1] = False
rng = np.random.default_rng(7)


RING_CY, RING_ASPECT, Q = 1072, 1.02, 4
qy, qx = np.mgrid[0:H // Q, 0:W // Q] * Q + Q / 2
QOUT = np.ones(qy.shape, bool)
QOUT[Y0 // Q:Y1 // Q + 1, X0 // Q:X1 // Q + 1] = False


# The water drops: two that land either side of the wordmark, their rings spreading in and crossing before the
# centred ripple (N9 part1). Rings round both centres share one curve (the drops are mirror images), with a lit edge.
DROP_DX, DROP_CY = (float(v) for v in os.environ.get('DROP', '520,1080').split(','))
ND = 220


def drops(x, y):
    dl = np.hypot(x - CX + DROP_DX, y - DROP_CY); dr = np.hypot(x - CX - DROP_DX, y - DROP_CY)
    return dl / 4, dr / 4, -(y - DROP_CY) / np.maximum(dl, 1), -(y - DROP_CY) / np.maximum(dr, 1)


def basis(x, y):
    """Oval glow (16px bins), round rings (4px bins) and their top-lit edge (same bins x cos)."""
    e = np.hypot(x - CX, (y - CY) / ASPECT) / 16
    rr = np.hypot(x - CX, (y - RING_CY) / RING_ASPECT)
    up = -(y - RING_CY) / np.maximum(rr, 1)
    c = rr / 4
    return e, c, up


def hat(v, n):
    """Linear interpolation weights of v onto bins 0..n-1 (sparse as dense columns)."""
    v = np.clip(v, 0, n - 1.001)
    i = v.astype(int); f = v - i
    m = np.zeros((v.size, n))
    m[np.arange(v.size), i] = 1 - f
    m[np.arange(v.size), i + 1] = f
    return m


NE, NC = 90, 330
_e, _c, _up = [t[QOUT] for t in basis(qx, qy)]
_dl, _dr, _ul, _ur = [t[QOUT] for t in drops(qx, qy)]
_hl, _hr = hat(_dl, ND), hat(_dr, ND)
QA = np.hstack([hat(_e, NE), hat(_c, NC), hat(_c, NC) * _up[:, None]])
# The drops are fitted to what the glow and centred rings leave over.
QD = np.hstack([_hl + _hr, _hl * _ul[:, None] + _hr * _ur[:, None]])
QDD = QD.T @ QD + 1e-1 * np.eye(QD.shape[1])
# Evaluated on the drawn size, each pixel mapped back into N9 coordinates.
_oy, _ox = np.mgrid[0:OH, 0:OW].astype(float)
_E, _C, _UP = basis((_ox + 0.5) / S - 0.5, (_oy + 0.5) / S - 0.5 - OFFY)
_DL, _DR, _UL, _URr = drops((_ox + 0.5) / S - 0.5, (_oy + 0.5) / S - 0.5 - OFFY)
# Radii past the N9 frame's corners had no samples: hold the curves at their last fitted value.
E_MAX, C_MAX, D_MAX = int(_e.max()), int(_c.max()), int(min(_dl.max(), _dr.max()))
QTQ = QA.T @ QA + 1e-3 * np.eye(QA.shape[1])  # tiny ridge: empty bins stay 0


def smooth(x, sigma, axis, wrap):
    """Gaussian smoothing along [axis] (wrapping round for a looping part)."""
    if sigma <= 0:
        return x
    r = int(3 * sigma)
    k = np.exp(-0.5 * (np.arange(-r, r + 1) / sigma) ** 2); k /= k.sum()
    pad = [(0, 0)] * x.ndim; pad[axis] = (r, r)
    xp = np.pad(x, pad, mode='wrap' if wrap else 'edge')
    return np.apply_along_axis(lambda v: np.convolve(v, k, mode='valid'), axis, xp)


def ss(e0, e1, v):
    t = np.clip((v - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def fit(a):
    """This frame's glow, ring and highlight curves, fitted to its background."""
    g = np.asarray(Image.fromarray(a.mean(2).astype(np.uint8)).resize((W // Q, H // Q), Image.BOX)).astype(float)
    coef = np.linalg.solve(QTQ, QA.T @ g[QOUT])
    dco = np.linalg.solve(QDD, QD.T @ (g[QOUT] - QA @ coef))
    ce, cc, cu = coef[:NE].copy(), coef[NE:NE + NC].copy(), coef[NE + NC:].copy()
    # Radii hidden behind the wordmark have no samples: hold the curves flat there.
    ce[:9] = ce[9]; cc[:45] = cc[45]; cu[:45] = cu[45] * np.linspace(0, 1, 45)
    ce[E_MAX:] = ce[E_MAX - 1]; cc[C_MAX:] = cc[C_MAX - 1]; cu[C_MAX:] = cu[C_MAX - 1]
    cd, cdu = dco[:ND].copy(), dco[ND:].copy()
    # Ripples only: no slow level of their own (that is the glow's), fading out where they were last seen.
    taper = ss(D_MAX, D_MAX - 25, np.arange(ND)) * ss(0, 6, np.arange(ND))
    cd = (cd - smooth(cd, 8, 0, False)) * taper; cdu = (cdu - smooth(cdu, 8, 0, False)) * taper
    return np.concatenate([ce, cc, cu, cd, cdu])


# The glow fades out softly towards the screen's edges, most at the top (the status bar and camera are there).
_vy = (_oy + 0.5) / OH
_vx = (_ox + 0.5) / OW
VIGNETTE = (ss(0.0, float(os.environ.get('FADE_TOP', 0.24)), _vy) *
            ss(1.0, 1.0 - float(os.environ.get('FADE_BOTTOM', 0.16)), _vy) *
            (0.75 + 0.25 * ss(0.0, 0.18, _vx) * ss(1.0, 0.82, _vx)))


def synth(coef):
    """A new background drawn only from the fitted glow, ring and highlight curves."""
    ce, cc, cu = coef[:NE], coef[NE:NE + NC], coef[NE + NC:NE + 2 * NC]
    o = NE + 2 * NC
    cd, cdu = coef[o:o + ND], coef[o + ND:]
    f = lambda v, c: np.interp(v, np.arange(len(c)), c)
    img = (f(_E, ce) + f(_C, cc) + f(_C, cu) * _UP
           + f(_DL, cd) + f(_DR, cd) + f(_DL, cdu) * _UL + f(_DR, cdu) * _URr) * VIGNETTE
    # Triangular dither of one level: smooth dark gradients without banding.
    img += rng.uniform(-0.5, 0.5, (OH, OW)) + rng.uniform(-0.5, 0.5, (OH, OW))
    return np.repeat(img[:, :, None], 3, 2)


def glow(m, r=GLOW, dy=0):
    g = Image.fromarray((m * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(r))
    return np.roll(np.asarray(g).astype(float) / 255, int(round(dy)), 0)


def terms(m, k=1.0):
    # letters, soft glow around them, and the dark drop shadow under them (at [k] times the N9's size)
    return [m, glow(m, GLOW * k), glow(m, 8 * k, 14 * k)]


REF_W = None
cache = {}
# Over time: the curves are fitted per frame, and their small frame-to-frame wobble showed as noise while the
# pulse faded. Across radius: the fitted rings carried a little noise of their own.
# The wordmark's shadow, measured on the N9 frames: about 26 px down, soft, the background under it at ~25%.
SHADOW, SHADOW_BLUR, SHADOW_DY = 0.75, 14, 26
T_SIGMA = float(os.environ.get('T_SIGMA', 1.6))
R_SIGMA = float(os.environ.get('R_SIGMA', 1.5))
for part in sorted(os.listdir(SRC)):
    p = os.path.join(SRC, part)
    if not os.path.isdir(p):
        continue
    os.makedirs(os.path.join(OUT, part), exist_ok=True)
    frames = []
    for name in sorted(os.listdir(p)):
        if not name.endswith('.jpg'):
            continue
        a = np.asarray(Image.open(os.path.join(p, name)).convert('RGB')).astype(float)
        bg = background(a)
        r = (a - bg).mean(2)
        # This frame's NOKIA letters: the bright part of what differs from the background.
        win = r[Y0:Y1, X0:X1]
        old_m = np.zeros((H, W))
        old_m[Y0:Y1, X0:X1] = win > 0.5 * max(win.max(), 1)
        ys, xs = np.nonzero(old_m)
        w = xs.max() - xs.min()
        if REF_W is None:
            REF_W = w
        scale = round(w / REF_W / 0.005) * 0.005
        cy = round((ys.min() + ys.max()) / 2)
        A = np.stack([t[Y0:Y1, X0:X1].ravel() for t in terms(old_m)], 1)
        k, *_ = np.linalg.lstsq(A, r[Y0:Y1, X0:X1].ravel(), rcond=None)
        frames.append((name, fit(a), (scale, cy), k))
    # part0 loops: its smoothing wraps round from the last frame to the first.
    loop = part == 'part0'
    raw = np.stack([f[1] for f in frames])
    # The glow is slow: smoothed over time. Rings travel a few pixels a frame: only lightly, or they would smear.
    curves = smooth(raw, 0.7, 0, loop)
    curves[:, :NE] = smooth(raw[:, :NE], T_SIGMA, 0, loop)
    o = NE + 2 * NC
    for lo, hi in ((0, NE), (NE, NE + NC), (NE + NC, o), (o, o + ND), (o + ND, o + 2 * ND)):
        curves[:, lo:hi] = smooth(curves[:, lo:hi], R_SIGMA, 1, False)
    for (name, _, key, k), coef in zip(frames, curves):
        if ONLY and f'{part}/{name}' not in ONLY:
            continue
        if key not in cache:
            cache[key] = terms(wordmark('ETESIAN', key[0], key[1], out=True), S)
        new_t = cache[key]
        if ('shadow',) + key not in cache:
            sh = glow(wordmark('ETESIAN', key[0], key[1], out=True), SHADOW_BLUR * S, SHADOW_DY * S)
            cache[('shadow',) + key] = sh / max(sh.max(), 1e-6)
        # The N9's drop shadow: the background darkened under and below the letters.
        bg = synth(coef) * (1 - SHADOW * cache[('shadow',) + key])[:, :, None]
        # Letters and their glow (the fitted shadow term is replaced by the one above).
        out = bg + sum(c * t for c, t in zip(k[:2], new_t[:2]))[:, :, None]
        Image.fromarray(np.clip(np.round(out), 0, 255).astype(np.uint8)).save(
            os.path.join(OUT, part, name), quality=97, subsampling=0)
        print(part, name, 'scale', key[0], 'k', np.round(k, 1))
