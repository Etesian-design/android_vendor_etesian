#!/usr/bin/env python3
"""Etesian's N9 text field and button shape as an Android nine-patch (the N9 drew these from nine-patches too).

The ends are a squircle as tall as the shape (n = 2.8, the icons' curve), cut in half and pulled apart, joined by
straight edges (blanco meegotouch-textedit-background and meegotouch-button-background). The stretch areas are the
single middle column and row, so a wider view lengthens the straight edges and a taller one keeps those corners.

    squircle9.py out.9.png --fill '#FFFFFF' [--fill '#ECECEE' ...] [--stroke '#B5B4B8'] [--height 40] [--shade]

--fill colours run top to bottom (one is solid). --shade adds the text field's faint inset shade along the top.
Rendered at xxhdpi (3 px per dp), 4x supersampled.
"""
import argparse
from PIL import Image, ImageDraw

N, D, SS = 2.8, 3, 4


def rgba(s):
    s = s.lstrip('#')
    if len(s) == 6: s = 'FF' + s
    a, r, g, b = (int(s[i:i + 2], 16) for i in (0, 2, 4, 6))
    return (r, g, b, a)


def mix(c1, c2, t): return tuple(round(a + (b - a) * t) for a, b in zip(c1, c2))


def ramp(cols, t):
    if len(cols) == 1: return cols[0]
    seg = min(int(t * (len(cols) - 1)), len(cols) - 2)
    return mix(cols[seg], cols[seg + 1], t * (len(cols) - 1) - seg)


def outline(w, h, inset):
    """The shape's points (supersampled px): a quarter of the n = 2.8 squircle at each corner, radius h/2."""
    s = h / 2 - inset
    import math
    pts = []
    for cx, cy, a0 in ((w - h / 2, h / 2, -90), (w - h / 2, h / 2, 0), (h / 2, h / 2, 90), (h / 2, h / 2, 180)):
        for i in range(65):
            a = math.radians(a0 + i * 90 / 64); c, sn = math.cos(a), math.sin(a)
            pts.append((cx + s * math.copysign(abs(c) ** (2 / N), c), cy + s * math.copysign(abs(sn) ** (2 / N), sn)))
    return pts


def render(fills, stroke, height_dp, shade, stroke_dp=1.0):
    h = round(height_dp * D); w = h + 2              # corners h/2 each, two stretchable middle px
    H, W, sw = h * SS, w * SS, stroke_dp * D * SS
    fill = Image.new('RGBA', (W, H))
    for y in range(H):
        c = ramp(fills, y / (H - 1))
        if shade and y < 4 * D * SS: c = mix(c, (0, 0, 0, c[3]), 0.08 * (1 - y / (4 * D * SS)))
        ImageDraw.Draw(fill).line([(0, y), (W, y)], fill=c)
    out = Image.new('RGBA', (W, H))
    if stroke:
        edge = Image.new('L', (W, H)); ImageDraw.Draw(edge).polygon(outline(W, H, 0), fill=255)
        out.paste(Image.new('RGBA', (W, H), stroke), (0, 0), edge)
    inner = Image.new('L', (W, H)); ImageDraw.Draw(inner).polygon(outline(W, H, sw if stroke else 0), fill=255)
    out.paste(fill, (0, 0), inner)
    out = out.resize((w, h), Image.LANCZOS)
    # nine-patch border: stretch = middle column/row (top, left); content = all of it (bottom, right), so it adds no
    # padding and lays out like the <shape> it replaces
    np_ = Image.new('RGBA', (w + 2, h + 2)); np_.paste(out, (1, 1))
    px, black = np_.load(), (0, 0, 0, 255)
    px[1 + w // 2 - 1, 0] = px[1 + w // 2, 0] = black
    px[0, 1 + h // 2] = black
    for x in range(1, 1 + w): px[x, h + 1] = black
    for y in range(1, 1 + h): px[w + 1, y] = black
    return np_


if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('out'); p.add_argument('--fill', action='append', required=True); p.add_argument('--stroke')
    p.add_argument('--stroke-dp', type=float, default=1.0); p.add_argument('--height', type=float, default=40); p.add_argument('--shade', action='store_true')
    a = p.parse_args()
    render([rgba(c) for c in a.fill], rgba(a.stroke) if a.stroke else None, a.height, a.shade, a.stroke_dp).save(a.out)
