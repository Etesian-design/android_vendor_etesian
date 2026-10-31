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
W, H = 1080, 2160
Y0, Y1 = 1010, 1175          # rows replaced (wordmark + glow)
X0, X1 = 200, 880            # columns replaced
CAP_TOP, CAP_BOT = 1051, 1105  # NOKIA cap height on the N9 frames
GLOW = 14


def axes(f):
    if AXES != 'none':
        f.set_variation_by_axes([float(v) for v in AXES.split(',')])


def wordmark(text, scale=1.0, cy=(CAP_TOP + CAP_BOT) / 2, wdth=125, wght=800, track=0.03):
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
    img = Image.new('L', (W, H), 0)
    d = ImageDraw.Draw(img)
    x = (W - total) / 2
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
QA = np.hstack([hat(_e, NE), hat(_c, NC), hat(_c, NC) * _up[:, None]])
_E, _C, _UP = basis(xx, yy)
QTQ = QA.T @ QA + 1e-3 * np.eye(QA.shape[1])  # tiny ridge: empty bins stay 0


def synth(a):
    """A new background drawn only from the fitted glow, ring and highlight curves."""
    g = np.asarray(Image.fromarray(a.mean(2).astype(np.uint8)).resize((W // Q, H // Q), Image.BOX)).astype(float)
    coef = np.linalg.solve(QTQ, QA.T @ g[QOUT])
    ce, cc, cu = coef[:NE].copy(), coef[NE:NE + NC].copy(), coef[NE + NC:].copy()
    # Radii hidden behind the wordmark have no samples: hold the curves flat there.
    ce[:9] = ce[9]; cc[:45] = cc[45]; cu[:45] = cu[45] * np.linspace(0, 1, 45)
    f = lambda v, c: np.interp(v, np.arange(len(c)), c)
    img = f(_E, ce) + f(_C, cc) + f(_C, cu) * _UP + rng.uniform(-0.5, 0.5, (H, W))
    return np.repeat(img[:, :, None], 3, 2)


def glow(m, r=GLOW, dy=0):
    g = Image.fromarray((m * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(r))
    return np.roll(np.asarray(g).astype(float) / 255, dy, 0)


def terms(m):
    # letters, soft glow around them, and the dark drop shadow under them
    return [m, glow(m), glow(m, 8, 14)]


REF_W = None
cache = {}
for part in sorted(os.listdir(SRC)):
    p = os.path.join(SRC, part)
    if not os.path.isdir(p):
        continue
    os.makedirs(os.path.join(OUT, part), exist_ok=True)
    for name in sorted(os.listdir(p)):
        if not name.endswith('.jpg') or (ONLY and f'{part}/{name}' not in ONLY):
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
        key = (scale, cy)
        if key not in cache:
            cache[key] = terms(wordmark('ETESIAN', scale, cy))
        new_t = cache[key]
        A = np.stack([t[Y0:Y1, X0:X1].ravel() for t in terms(old_m)], 1)
        k, *_ = np.linalg.lstsq(A, r[Y0:Y1, X0:X1].ravel(), rcond=None)
        out = synth(a) + sum(c * t for c, t in zip(k, new_t))[:, :, None]
        Image.fromarray(np.clip(out, 0, 255).astype(np.uint8)).save(
            os.path.join(OUT, part, name), quality=92)
        print(part, name, 'scale', scale, 'k', np.round(k, 1))
