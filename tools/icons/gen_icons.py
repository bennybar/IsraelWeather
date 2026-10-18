import sys, os
out = sys.argv[1]
CLOUD = "M30 74 H74 A14 14 0 0 0 76 46.2 A20 20 0 0 0 38.5 42 A16 16 0 0 0 30 74 Z"  # flat-bottomed cloud, 100x100 box

def svg(body, defs=""):
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" width="192" height="192"><defs>{defs}</defs>{body}</svg>'

def place(inner, x=0, y=0, s=1):
    return f'<g transform="translate({x} {y}) scale({s})">{inner}</g>'

# ---------- Glass ----------
G_DEFS = '''
<radialGradient id="sun" cx="40%" cy="35%" r="70%"><stop offset="0" stop-color="#FFF4B8"/><stop offset=".45" stop-color="#FFC93C"/><stop offset="1" stop-color="#FF8A1F"/></radialGradient>
<radialGradient id="glow" cx="50%" cy="50%" r="50%"><stop offset=".55" stop-color="#FFB84D" stop-opacity=".45"/><stop offset="1" stop-color="#FFB84D" stop-opacity="0"/></radialGradient>
<linearGradient id="glassc" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFFFFF" stop-opacity=".97"/><stop offset="1" stop-color="#B4C4E2" stop-opacity=".92"/></linearGradient>
<linearGradient id="darkc" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#B9C6DB"/><stop offset="1" stop-color="#7E8DA8"/></linearGradient>
<linearGradient id="rim" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#FFFFFF"/><stop offset="1" stop-color="#FFFFFF" stop-opacity=".2"/></linearGradient>
<linearGradient id="drop" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#9FD3FF"/><stop offset="1" stop-color="#2F74E8"/></linearGradient>
<linearGradient id="bolt" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFE97A"/><stop offset="1" stop-color="#FF9F1A"/></linearGradient>
<linearGradient id="moon" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#FFFBE6"/><stop offset="1" stop-color="#C9C3F2"/></linearGradient>
<linearGradient id="sand" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#E9C58F"/><stop offset="1" stop-color="#C08A4A"/></linearGradient>
<linearGradient id="mist" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#D7E0EE"/><stop offset="1" stop-color="#9AA9C2"/></linearGradient>
<filter id="soft" x="-30%" y="-30%" width="160%" height="160%"><feGaussianBlur stdDeviation="2.2"/></filter>
<mask id="cres"><rect width="100" height="100" fill="#fff"/><circle cx="66" cy="34" r="25" fill="#000"/></mask>
'''
def g_sun(x=0, y=0, s=1):
    return place('<circle cx="50" cy="50" r="31" fill="url(#glow)"/><circle cx="50" cy="50" r="24" fill="url(#sun)"/>'
                 '<ellipse cx="42" cy="40" rx="9" ry="5" fill="#fff" opacity=".55" transform="rotate(-30 42 40)"/>', x, y, s)
def g_moon(x=0, y=0, s=1):
    return place('<g mask="url(#cres)"><circle cx="48" cy="52" r="30" fill="url(#moon)"/></g>'
                 '<path d="M30 40 A22 22 0 0 0 34 66" stroke="#fff" stroke-width="2.5" fill="none" stroke-linecap="round" opacity=".7"/>', x, y, s)
def g_cloud(x=0, y=0, s=1, dark=False):
    fill = "url(#darkc)" if dark else "url(#glassc)"
    return place(f'<ellipse cx="53" cy="80" rx="26" ry="3.5" fill="#3B4A6B" opacity=".18" filter="url(#soft)"/>'
                 f'<path d="{CLOUD}" fill="{fill}"/>'
                 f'<path d="{CLOUD}" fill="none" stroke="#8FA3C6" stroke-width="1.2" stroke-opacity=".55"/>'
                 f'<path d="{CLOUD}" fill="none" stroke="url(#rim)" stroke-width="1.6" transform="translate(0.4 0.6)"/>'
                 '<path d="M30 52 A12 12 0 0 1 44 44" stroke="#fff" stroke-width="2.6" fill="none" stroke-linecap="round" opacity=".9"/>'
                 '<path d="M50 34 A16 16 0 0 1 64 33" stroke="#fff" stroke-width="2.2" fill="none" stroke-linecap="round" opacity=".75"/>', x, y, s)
def g_drops(y0=70, n=3, dx=12, x0=38):
    return "".join(f'<rect x="{x0+i*dx}" y="{y0+(i%2)*4}" width="5" height="14" rx="2.5" fill="url(#drop)" transform="rotate(15 {x0+i*dx+2.5} {y0+7})"/>'
                   f'<circle cx="{x0+i*dx+2}" cy="{y0+(i%2)*4+3}" r="1.1" fill="#fff" opacity=".8"/>' for i in range(n))
def g_bars(fill, y0=72):
    return (f'<rect x="22" y="{y0}" width="56" height="6" rx="3" fill="{fill}" opacity=".95"/>'
            f'<rect x="30" y="{y0+10}" width="46" height="6" rx="3" fill="{fill}" opacity=".85"/>'
            f'<rect x="22" y="{y0+20}" width="40" height="6" rx="3" fill="{fill}" opacity=".7"/>')
glass = {
 "sun": g_sun(),
 "moon": g_moon(),
 "partly": g_sun(-8, -10, .8) + g_cloud(6, 8, 1),
 "partly_night": g_moon(-8, -10, .78) + g_cloud(6, 8, 1),
 "cloud": g_cloud(0, 4),
 "partly_rain": g_sun(-8, -14, .75) + g_cloud(6, -2, 1) + g_drops(72, x0=42),
 "rain": g_cloud(0, -6, 1, dark=True) + g_drops(70),
 "storm": g_cloud(0, -8, 1, dark=True) + '<path d="M54 62 L42 80 H52 L46 96 L64 74 H54 L60 62 Z" fill="url(#bolt)" stroke="#fff" stroke-width="1" stroke-opacity=".6"/>',
 "snow": g_cloud(0, -6) + "".join(f'<g transform="translate({x} {y})" stroke="#7FB8FF" stroke-width="2.4" stroke-linecap="round"><path d="M0 -5 V5 M-4.3 -2.5 L4.3 2.5 M-4.3 2.5 L4.3 -2.5"/></g>' for x, y in [(36, 82), (52, 88), (68, 82)]),
 "fog": g_cloud(0, -14) + g_bars("url(#mist)", 66),
 "dust": g_sun(-10, -16, .7).replace('url(#sun)', 'url(#sand)') + g_bars("url(#sand)", 58),
 "wind": '<path d="M16 40 H58 A10 10 0 1 0 48 30" stroke="url(#mist)" stroke-width="7" fill="none" stroke-linecap="round"/>'
         '<path d="M16 56 H74 A11 11 0 1 1 63 67" stroke="url(#mist)" stroke-width="7" fill="none" stroke-linecap="round"/>'
         '<path d="M16 40 H58 A10 10 0 1 0 48 30" stroke="#fff" stroke-width="1.6" fill="none" stroke-linecap="round" opacity=".8" transform="translate(0 -1.8)"/>'
         '<path d="M24 72 H48" stroke="url(#mist)" stroke-width="7" stroke-linecap="round"/>',
}
# ---------- One UI–style: flat, chunky, two-tone ----------
O_DEFS = '<mask id="ocres"><rect width="100" height="100" fill="#fff"/><circle cx="64" cy="36" r="24" fill="#000"/></mask>'
def o_sun(x=0, y=0, s=1):
    return place('<circle cx="50" cy="50" r="27" fill="#FFB300"/><circle cx="50" cy="50" r="20" fill="#FFCB2E"/>', x, y, s)
def o_moon(x=0, y=0, s=1):
    return place('<g mask="url(#ocres)"><circle cx="48" cy="52" r="28" fill="#FFC531"/></g>', x, y, s)
def o_cloud(x=0, y=0, s=1, dark=False):
    top, under = ("#9FAEC5", "#7F90AB") if dark else ("#E8EEF6", "#B5C2D5")
    return place(f'<path d="{CLOUD}" fill="{under}"/><path d="{CLOUD}" fill="{top}" transform="translate(0 -3.5)"/>', x, y, s)
def o_drops(y0=72, x0=38, n=3, dx=12, c="#3D8BFF"):
    return "".join(f'<path d="M{x0+i*dx} {y0} l-4 12" stroke="{c}" stroke-width="5.5" stroke-linecap="round"/>' for i in range(n))
def o_bars(c, y0=70):
    return (f'<path d="M22 {y0} H78 M30 {y0+11} H74 M22 {y0+22} H62" stroke="{c}" stroke-width="6.5" stroke-linecap="round"/>')
oneui = {
 "sun": o_sun(),
 "moon": o_moon(),
 "partly": o_sun(-8, -10, .8) + o_cloud(6, 8, 1),
 "partly_night": o_moon(-8, -10, .78) + o_cloud(6, 8, 1),
 "cloud": o_cloud(0, 4),
 "partly_rain": o_sun(-8, -14, .75) + o_cloud(6, -2, 1) + o_drops(78, x0=42),
 "rain": o_cloud(0, -6, 1, dark=True) + o_drops(76),
 "storm": o_cloud(0, -8, 1, dark=True) + '<path d="M54 64 L43 81 H52 L47 95 L63 75 H54 L59 64 Z" fill="#FFC107"/>',
 "snow": o_cloud(0, -6) + "".join(f'<circle cx="{x}" cy="{y}" r="4.2" fill="#8CC4FF"/>' for x, y in [(37, 82), (52, 88), (67, 82)]),
 "fog": o_cloud(0, -14) + o_bars("#A9B5C8", 66),
 "dust": o_sun(-10, -16, .7).replace("#FFB300", "#D79A55").replace("#FFCB2E", "#E8B878") + o_bars("#D2A160", 58),
 "wind": '<path d="M16 40 H58 A10 10 0 1 0 48 30 M16 56 H74 A11 11 0 1 1 63 67 M24 72 H48" stroke="#8FB3D9" stroke-width="7" fill="none" stroke-linecap="round"/>',
}
for name, (icons, defs) in {"glass": (glass, G_DEFS), "oneui": (oneui, O_DEFS)}.items():
    os.makedirs(f"{out}/{name}", exist_ok=True)
    for k, body in icons.items():
        open(f"{out}/{name}/{k}.svg", "w").write(svg(body, defs))
