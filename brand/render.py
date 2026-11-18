"""Renders Etesian's icon (the wind, from github.com/Etesian-design/design brand/) for the launcher.

The two versions: deep (blue, for dark surfaces) and pearl (white, for light ones). Each is the whole squircle icon.
Rendered at 768 px so the biggest Breeze button (three app icons) stays sharp; also the launcher's own app icon.

    render.py            (needs cairosvg)
"""
import os
import cairosvg

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'launcher', 'app', 'src', 'main', 'res', 'drawable-nodpi')
os.makedirs(OUT, exist_ok=True)
for v in ('deep', 'pearl'):
    cairosvg.svg2png(url=os.path.join(HERE, f'etesian-{v}.svg'), write_to=os.path.join(OUT, f'etesian_icon_{v}.png'),
                     output_width=768, output_height=768)

# The launcher's own app icon, as an adaptive icon: the deep version, unchanged (tile, rim, wind, leaves), as the
# foreground, filling the 72 dp the system shows of the 108 dp layers (a 150-unit window). The background is only
# the tile's colours, for any corner a mask wider than the icon's own squircle might show.
RES = os.path.join(HERE, '..', 'launcher', 'app', 'src', 'main', 'res')
s = open(os.path.join(HERE, 'etesian-deep.svg')).read()
inner = s[s.index('>', s.index('<svg')) + 1:s.rindex('</svg>')]
head = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="-25 -25 150 150">'
bg = head + '''<defs>
<linearGradient id="b" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="0" y2="100">
<stop offset="0" stop-color="#2C6FD9"/><stop offset="1" stop-color="#0F3B8C"/></linearGradient>
<radialGradient id="h" gradientUnits="userSpaceOnUse" cx="50" cy="0" r="75">
<stop offset="0" stop-color="#fff" stop-opacity="0.28"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></radialGradient>
</defs><rect x="-25" y="-25" width="150" height="150" fill="url(#b)"/>
<rect x="-25" y="-25" width="150" height="150" fill="url(#h)"/></svg>'''
fg = head + inner + '</svg>'
for name, svg in (('ic_launcher_etesian_bg', bg), ('ic_launcher_etesian_fg', fg)):
    cairosvg.svg2png(bytestring=svg.encode(), write_to=os.path.join(OUT, name + '.png'), output_width=432,
                     output_height=432)
# Before Android 8 (no adaptive icons): the whole deep icon.
for d, px in (('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)):
    for n in ('ic_launcher', 'ic_launcher_round'):
        cairosvg.svg2png(url=os.path.join(HERE, 'etesian-deep.svg'),
                         write_to=os.path.join(RES, 'mipmap-' + d, n + '.png'), output_width=px, output_height=px)
