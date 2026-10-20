"""Превью /ah с земляной рамкой и земляной завесой.

python3 previews/render_ah_chain.py  ->  previews/preview_ah_chain.png, preview_ah_chain.gif
"""
import sys, os
sys.path.insert(0, '/tmp/pyyaml')
sys.path.insert(0, '/tmp/pil')
import yaml
from PIL import Image, ImageDraw, ImageFont

ROOT = '/home/user/1mx20839u5m809u532x1'
OUT = os.path.join(ROOT, 'previews')
os.makedirs(OUT, exist_ok=True)
F = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
FB = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'

COL = {
    'dirt': ((134, 96, 67), (121, 85, 58)),
    'iron_chain': ((125, 128, 134), (96, 99, 105)),
    'paper': ((232, 232, 226), (198, 198, 192)),
    'purple_stained_glass_pane': ((106, 47, 181), (88, 38, 150)),
    'golden_carrot': ((240, 170, 40), (208, 144, 30)),
    'red_dye': ((190, 40, 40), (155, 30, 30)),
    'black_dye': ((38, 38, 42), (26, 26, 30)),
    'hopper': ((110, 110, 118), (90, 90, 98)),
    'crafting_table': ((150, 100, 55), (120, 78, 42)),
    'purple_bundle': ((140, 70, 200), (112, 55, 165)),
    'music_disc_mellohi': ((230, 120, 180), (200, 95, 155)),
    'gray_dye': ((120, 120, 120), (98, 98, 98)),

}

def slots(s):
    out = []
    for p in str(s).replace(' ', '').split(','):
        if not p:
            continue
        if '-' in p:
            a, b = p.split('-')
            out += list(range(int(a), int(b) + 1))
        else:
            out.append(int(p))
    return out

menu = yaml.safe_load(open(os.path.join(ROOT, 'BMenu/menu/bauc/home.yml'), encoding='utf-8'))
items = menu['items']

def matrix(upto):
    m = [None] * 54
    for k, v in items.items():
        for sl in slots(v.get('slot', '')) if v.get('slot') else []:
            m[sl] = (v.get('material'), k)
    for sl in range(45):
        if m[sl] is None:
            m[sl] = ('__lot__', 'лот')
    for fr in menu['animation'][:upto]:
        for op in fr['opcodes']:
            c = op.get('cmd')
            if not c:
                continue
            rest = c[len('[layer] 3 '):]
            kind, arg = rest.split(' ', 1)
            if kind == 'set':
                it, sl = arg.split(' ', 1)
                mat = items[it].get('material')
                for s in slots(sl):
                    m[s] = (mat, it)
            else:
                for s in slots(arg):
                    m[s] = ('__lot__', 'лот')
    return m

def draw(m, title, note):
    cell, pad, top = 46, 12, 58
    img = Image.new('RGB', (9 * cell + pad * 2, 6 * cell + top + pad), (20, 17, 22))
    d = ImageDraw.Draw(img)
    d.text((pad, 12), title, font=ImageFont.truetype(FB, 17), fill=(248, 190, 251))
    d.text((pad, 34), note, font=ImageFont.truetype(F, 11), fill=(150, 143, 150))
    f = ImageFont.truetype(F, 11)
    for i in range(54):
        r, c = divmod(i, 9)
        x, y = pad + c * cell, top + r * cell
        mat, key = m[i]
        if mat is None:
            d.text((x + cell / 2, y + cell / 2), str(i), font=f, anchor='mm', fill=(70, 66, 72))
            continue
        if mat == '__lot__':
            bg, edge = (42, 48, 66), (62, 70, 92)
        else:
            bg, edge = COL.get(mat, ((70, 70, 70), (55, 55, 55)))
        d.rounded_rectangle([x + 2, y + 2, x + cell - 3, y + cell - 3], radius=5, fill=bg, outline=edge)
        if mat == 'dirt':
            for dy in range(6, cell - 8, 7):
                for dx in range(6, cell - 8, 7):
                    if (dx * 7 + dy * 13 + i) % 3:
                        d.point((x + dx, y + dy), fill=(110, 78, 52))
        elif mat == 'iron_chain':
            cx, cy = x + cell / 2, y + cell / 2 - 2
            for k in range(3):
                yy = cy - 10 + k * 10
                d.ellipse([cx - 7, yy - 5, cx + 7, yy + 5], outline=(214, 218, 226), width=2)
        elif mat == 'paper':
            d.rectangle([x + cell / 2 - 9, y + cell / 2 - 12, x + cell / 2 + 9, y + cell / 2 + 11], fill=(236, 236, 230))
            for k in range(3):
                d.line([(x + cell / 2 - 6, y + cell / 2 - 6 + k * 6), (x + cell / 2 + 6, y + cell / 2 - 6 + k * 6)], fill=(150, 150, 150))
        elif mat == 'oak_leaves':
            d.rectangle([x + 8, y + 8, x + cell - 9, y + cell - 12], fill=(64, 108, 46))
            for dy in range(10, cell - 13, 5):
                for dx in range(10, cell - 11, 5):
                    if (dx * 5 + dy * 7 + i) % 4 == 0:
                        d.point((x + dx, y + dy), fill=(92, 140, 66))
        elif mat == '__lot__':
            d.text((x + cell / 2, y + cell / 2), 'лот', font=f, anchor='mm', fill=(160, 168, 190))
        d.text((x + cell / 2, y + cell - 9), str(i), font=f, anchor='mm', fill=(238, 232, 228))
    return img

frames = []
for k in range(len(menu['animation'])):
    fr = menu['animation'][k]
    frames.append(draw(matrix(k + 1), 'Аукцион /ah — рамка из железных цепей',
                       f'кадр {k + 1}/{len(menu["animation"])}, tick {fr.get("tick")}'))
    frames[-1].save(f'/tmp/ahd_{k}.png')

frames[-1].save(os.path.join(OUT, 'preview_ah_chain.png'))
frames[0].save(os.path.join(OUT, 'preview_ah_chain.gif'), save_all=True,
               append_images=frames[1:], duration=[130] * (len(frames) - 1) + [1500], loop=0)
print('saved', len(frames), 'frames')
