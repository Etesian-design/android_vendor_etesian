"""Renders Etesian's icon (the wind, from github.com/Etesian-design/design brand/) for the launcher.

The two versions: deep (blue, for dark surfaces) and pearl (white, for light ones). Each is the whole squircle icon.
Rendered at 768 px so the biggest Breeze button (three app icons) stays sharp.

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
