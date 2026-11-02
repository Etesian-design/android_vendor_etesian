#!/usr/bin/env python3
"""Turns the rounded-rectangle <shape>s of the patched apps' text fields and buttons into the N9 shape: a nine-patch
(squircle9.py) in the shape's own colours, gradient and rim, with squircle-half ends. Selectors, insets and ripples
around the shapes stay. Run from the LineageOS top; edits the app trees, then regenerate their patches.

    squircle-shapes.py <app dir> <drawable.xml> [--field] [--color name=#AARRGGBB ...]

--field adds the text field's inset shade. Colours given as @color/x are read from the app's res/values*/colors.xml
(the day value); ?attr references need --color.
"""
import argparse, glob, os, re, subprocess, sys
HERE = os.path.dirname(os.path.abspath(__file__))

def colours(app, extra):
    c = {}
    for f in glob.glob(f'{app}/**/res/values/*.xml', recursive=True):
        for n, v in re.findall(r'<color name="([^"]+)">\s*([^<\s]+)\s*</color>', open(f, errors='ignore').read()): c.setdefault(n, v)
    c.update(extra)
    def res(v, depth=0):
        if v.startswith('#'): return v
        m = re.match(r'[@?](?:android:)?(?:attr/|color/)?(.+)', v)
        if v == '@android:color/white': return '#FFFFFFFF'
        if v in ('@android:color/transparent',): return '#00000000'
        if m and m.group(1) in c and depth < 5: return res(c[m.group(1)], depth + 1)
        sys.exit(f'cannot resolve colour {v}: pass --color {m.group(1) if m else v}=#AARRGGBB')
    return res

def main():
    p = argparse.ArgumentParser(); p.add_argument('app'); p.add_argument('drawable'); p.add_argument('--field', action='store_true')
    p.add_argument('--color', action='append', default=[]); p.add_argument('--height', default='40')
    a = p.parse_args(); res = colours(a.app, dict(x.split('=', 1) for x in a.color))
    src = open(a.drawable).read(); name = os.path.splitext(os.path.basename(a.drawable))[0]
    out_dir = os.path.join(os.path.dirname(os.path.dirname(a.drawable)), 'drawable-xxhdpi'); os.makedirs(out_dir, exist_ok=True)
    n = 0
    def swap(m):
        nonlocal n
        body = m.group(0)
        g = re.search(r'<gradient([^>]*)/>', body, re.S); s = re.search(r'<solid[^>]*android:color="([^"]+)"', body)
        st = re.search(r'<stroke([^>]*)/>', body, re.S)
        if g:
            at = dict(re.findall(r'android:(\w+)="([^"]+)"', g.group(1)))
            fills = [res(at[k]) for k in ('startColor', 'centerColor', 'endColor') if k in at]
            if at.get('angle', '270') == '90': fills.reverse()
        elif s: fills = [res(s.group(1))]
        else: fills = ['#00000000']
        args = ['python3', os.path.join(HERE, 'squircle9.py'), os.path.join(out_dir, f'{name}_sq{n}.9.png'), '--height', a.height]
        for f in fills: args += ['--fill', f]
        if st:
            at = dict(re.findall(r'android:(\w+)="([^"]+)"', st.group(1)))
            args += ['--stroke', res(at['color']), '--stroke-dp', at.get('width', '1dp').replace('dp', '')]
        if a.field: args.append('--shade')
        subprocess.run(args, check=True)
        r = f'<nine-patch xmlns:android="http://schemas.android.com/apk/res/android" android:src="@drawable/{name}_sq{n}" /><!-- N9: squircle-half ends (vendor/etesian/patches/tools/squircle-shapes.py) -->'
        n += 1; return r
    new = re.sub(r'<shape\b.*?</shape>', swap, src, flags=re.S)
    new = new.replace('xmlns:android="http://schemas.android.com/apk/res/android" android:src', 'android:src') if re.search(r'^<(?!nine-patch)[a-z-]+[^>]*xmlns:android', re.sub(r'<\?xml[^>]*>|<!--.*?-->', '', new, flags=re.S).lstrip()) else new
    open(a.drawable, 'w').write(new); print(f'{a.drawable}: {n} shape(s)')

if __name__ == '__main__': main()
