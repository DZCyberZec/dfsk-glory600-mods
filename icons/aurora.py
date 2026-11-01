# Set definitivo "Aurora": bw_app_icon_<nome>_n (normale) e _p (premuta) 80x80 per drawable-hdpi della Home BW.
import sys, subprocess, os, base64
P = sys.argv[1]
src = open(f'{P}/genera_icone.py').read()
G = eval(src[src.index('G = {')+4: src.index('\n}\n')+2])
LIME, TEAL, WHITE = '#c5f56c', '#2fd6a6', '#f1f4ee'
def icon(k, a, b, glow):
    g = G[k].replace('@C', a)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80" viewBox="0 0 80 80"><defs>'
            f'<linearGradient id="a" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{a}"/><stop offset="1" stop-color="{b}"/></linearGradient>'
            f'<radialGradient id="r" cx=".5" cy=".5" r=".5"><stop offset="0" stop-color="{TEAL}" stop-opacity="{glow}"/><stop offset="1" stop-color="{TEAL}" stop-opacity="0"/></radialGradient>'
            f'<radialGradient id="q" cx=".5" cy=".5" r=".5"><stop offset="0" stop-color="{LIME}" stop-opacity="{glow*0.85:.2f}"/><stop offset="1" stop-color="{LIME}" stop-opacity="0"/></radialGradient></defs>'
            '<circle cx="30" cy="48" r="30" fill="url(#r)"/><circle cx="52" cy="30" r="28" fill="url(#q)"/>'
            f'<g transform="translate(9 9) scale(0.78)"><g fill="none" stroke="url(#a)" stroke-width="4.6" stroke-linecap="round" stroke-linejoin="round">{g}</g></g></svg>')
os.makedirs(f'{P}/aurora/svg', exist_ok=True); os.makedirs(f'{P}/aurora/png', exist_ok=True); os.makedirs(f'{P}/aurora/png320', exist_ok=True)
for k in G:
    for suf, a, b, glow in (('n', LIME, TEAL, .35), ('p', WHITE, LIME, .55)):
        f = f'{P}/aurora/svg/bw_app_icon_{k}_{suf}.svg'; open(f, 'w').write(icon(k, a, b, glow))
        subprocess.run(['rsvg-convert', '-w', '80', '-h', '80', f, '-o', f'{P}/aurora/png/bw_app_icon_{k}_{suf}.png'], check=True)
        subprocess.run(['rsvg-convert', '-w', '320', '-h', '320', f, '-o', f'{P}/aurora/png320/bw_app_icon_{k}_{suf}.png'], check=True)
# anteprima: tutte le icone nella tessera del menu
L=[('avm','Vista 360°'),('dvr','Dashcam'),('erya','CarAuto'),('car_setting','Veicolo'),('setting','Impostazioni'),('ac','Climatizzatore'),('phone','Telefono'),('media','Media'),('gallery','Foto'),('video','Video')]
W,tw,th,gx,gy=1920,580,130,24,30; H=110+4*(th+gy)
o=[f'<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="{W}" height="{H}"><defs><linearGradient id="t" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#2a3034"/><stop offset="1" stop-color="#1a1f22"/></linearGradient></defs><rect width="{W}" height="{H}" fill="#0c1012"/>',
   f'<text x="60" y="65" fill="{LIME}" font-family="Helvetica" font-size="32" font-weight="bold">Set icone CyberZec · Aurora</text>']
for i,(k,t) in enumerate(L):
    x=60+(i%3)*(tw+gx); y=100+(i//3)*(th+gy)
    b=base64.b64encode(open(f'{P}/aurora/png320/bw_app_icon_{k}_n.png','rb').read()).decode()
    o.append(f'<rect x="{x}" y="{y}" width="{tw}" height="{th}" rx="8" fill="url(#t)" stroke="#3a4146" stroke-width="2"/><image x="{x+18}" y="{y+15}" width="100" height="100" xlink:href="data:image/png;base64,{b}"/><text x="{x+140}" y="{y+th/2+12}" fill="{WHITE}" font-family="Helvetica" font-size="34">{t}</text>')
o.append('</svg>'); open(f'{P}/aurora/anteprima.svg','w').write(''.join(o))
subprocess.run(['rsvg-convert', f'{P}/aurora/anteprima.svg', '-o', f'{P}/anteprima_aurora.png'], check=True)
print('ok', len(G))
