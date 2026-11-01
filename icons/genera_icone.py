# Genera il set di icone cyberzec per la Home BW (80x80 hdpi, normale _n e premuta _p).
import sys, os, subprocess
OUT = sys.argv[1]
LIME, WHITE = '#c5f56c', '#f1f4ee'
S = 'fill="none" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"'
G = {
 'avm': '<circle cx="40" cy="40" r="27" stroke-dasharray="10 7"/><rect x="31" y="24" width="18" height="32" rx="6"/><path d="M33 33h14M33 47h14"/>',
 'dvr': '<rect x="12" y="25" width="38" height="30" rx="6"/><path d="M50 35l16-8v26l-16-8"/><circle cx="22" cy="34" r="2.5" fill="@C"/>',
 'erya': '<rect x="13" y="16" width="20" height="38" rx="4"/><path d="M20 49h6"/><path d="M42 30a10 10 0 0 1 0 20M48 24a18 18 0 0 1 0 32"/><path d="M56 40h12"/>',
 'car_setting': '<path d="M14 50v-8l6-14a6 6 0 0 1 6-4h28a6 6 0 0 1 6 4l6 14v8z"/><path d="M14 50v6M66 50v6"/><circle cx="24" cy="44" r="3"/><circle cx="56" cy="44" r="3"/><path d="M22 36h36"/>',
 'setting': '<path d="M58.2 34.4 L64.7 36.1 L64.7 43.9 L58.2 45.6 L56.8 48.9 L60.2 54.7 L54.7 60.2 L48.9 56.8 L45.6 58.2 L43.9 64.7 L36.1 64.7 L34.4 58.2 L31.1 56.8 L25.3 60.2 L19.8 54.7 L23.2 48.9 L21.8 45.6 L15.3 43.9 L15.3 36.1 L21.8 34.4 L23.2 31.1 L19.8 25.3 L25.3 19.8 L31.1 23.2 L34.4 21.8 L36.1 15.3 L43.9 15.3 L45.6 21.8 L48.9 23.2 L54.7 19.8 L60.2 25.3 L56.8 31.1 Z"/><circle cx="40" cy="40" r="7"/>',
 'ac': '<path d="M40 14v52M17.5 27l45 26M17.5 53l45-26"/><path d="M34 18l6 6 6-6M34 62l6-6 6 6"/><circle cx="40" cy="40" r="5"/>',
 'phone': '<path d="M24 14h8l5 13-6 4a32 32 0 0 0 18 18l4-6 13 5v8a6 6 0 0 1-6 6A48 48 0 0 1 18 20a6 6 0 0 1 6-6z"/>',
 'media': '<path d="M32 54V22l28-6v32"/><circle cx="25" cy="54" r="7"/><circle cx="53" cy="48" r="7"/><path d="M32 30l28-6"/>',
 'gallery': '<rect x="12" y="16" width="56" height="48" rx="7"/><path d="M12 54l16-16 12 12 8-8 20 20"/><circle cx="52" cy="30" r="5"/>',
 'video': '<rect x="12" y="18" width="56" height="44" rx="8"/><path d="M34 30v20l16-10z" fill="@C"/>',
}
def svg(glyph, color, glow):
    g = glyph.replace('@C', color)
    filt = ('<filter id="g" x="-30%" y="-30%" width="160%" height="160%"><feGaussianBlur stdDeviation="2.2" result="b"/>'
            '<feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter>') if glow else ''
    node = f'<circle cx="71" cy="9" r="3" fill="{LIME}"/>'  # nodo "tech" in alto a destra
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80" viewBox="0 0 80 80"><defs>{filt}</defs>'
            f'<g transform="translate(6 7) scale(0.85)"><g {S} stroke="{color}" filter="{"url(#g)" if glow else ""}">{g}</g></g>{node}</svg>')
os.makedirs(f'{OUT}/svg', exist_ok=True); os.makedirs(f'{OUT}/png', exist_ok=True)
for name, glyph in G.items():
    for suf, col, glow in (('n', LIME, True), ('p', WHITE, True)):
        f = f'{OUT}/svg/bw_app_icon_{name}_{suf}.svg'
        open(f, 'w').write(svg(glyph, col, glow))
        for px, d in ((80, 'png'), (320, 'png320')):
            os.makedirs(f'{OUT}/{d}', exist_ok=True)
            subprocess.run(['rsvg-convert', '-w', str(px), '-h', str(px), f, '-o', f'{OUT}/{d}/bw_app_icon_{name}_{suf}.png'], check=True)
print(len(G), 'icone')
