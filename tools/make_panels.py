#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Панели-«квадраты» под голограммы для DecentHolograms 2.10+ (тип BLOCK).

У text_display фон рисуется ПОСТРОЧНО (каждая строка — своя плашка), поэтому
«один большой квадрат» из текста не сделать. Решение — отдельный BLOCK-дисплей
(бетон) позади текста, размером в квадрат.

Запуск:
    python3 tools/make_panels.py DecentHolograms/displays DecentHolograms/displays
"""
import math
import os
import re
import sys

import yaml

# ширины символов шрифта Minecraft по умолчанию (в пикселях)
W = {' ': 4, '!': 2, '"': 5, '#': 6, '$': 6, '%': 6, '&': 6, "'": 2,
     '(': 5, ')': 5, '*': 5, '+': 6, ',': 2, '-': 6, '.': 2, '/': 6,
     ':': 2, ';': 2, '<': 5, '=': 6, '>': 5, '?': 6, '@': 7,
     'I': 4, '[': 4, ']': 4, 't': 4, 'i': 2, 'l': 3, 'f': 5, 'k': 5,
     '⭐': 14, '⛈': 13, '☁': 10, '●': 7, '▪': 6, '▬': 10, '★': 9,
     'ᴇ': 6, 'ᴛ': 6, 'ʟ': 6, 'ᴅ': 6, 'ʀ': 6, 'ᴀ': 6, 'ɢ': 6, 'ᴏ': 6,
     'ɴ': 6, 'ᴍ': 6, 'ɪ': 4, 'ᴘ': 6, 'ѕ': 6, 'ᴄ': 6, 'ᴜ': 6, 'ʜ': 6,
     'ᴡ': 7, 'ꜰ': 6, 'ᴠ': 6, 'ᴋ': 6, 'ᴢ': 6, 'ᴊ': 6, 'ʏ': 6, 'ᴠ': 6}

# 1 пиксель шрифта = 1/40 блока
PX = 1.0 / 40.0
LINE_H = 9 * PX        # высота строки шрифта: 9 px = 0.225 блока
PAD = 0.35             # отступ вокруг текста
ICON_RE = re.compile(r'^#(ICON|HEAD|SMALLHEAD)', re.IGNORECASE)
CODEX = re.compile(r'&#[0-9a-fA-F]{6}|&[0-9a-fk-orA-FK-OR]')


def width_px(text):
    text = CODEX.sub('', text)
    total = 0
    for ch in text:
        total += W.get(ch, 6)
    return total


def convert(src, out):
    data = yaml.safe_load(open(src, encoding='utf-8'))
    name = os.path.splitext(os.path.basename(src))[0]
    loc = data['location']
    world, x, y, z = loc['world'], loc['x'], loc['y'], loc['z']

    lines = (data.get('pages') or [{}])[0].get('lines') or []
    texts = [str(l) for l in lines]
    if not texts:
        return None

    # точка привязки текста уже смещена в самом дисплее
    text_ty = data['attributes']['translation']['value']['y']

    n = len(texts)
    h_text = n * LINE_H
    w_text = max(width_px(t) for t in texts) * PX
    side = max(w_text, h_text) + PAD * 2
    ty = text_ty - PAD - (side - h_text) / 2

    body = '''# DecentHolograms 2.10+ — панель (BLOCK) под голограммой {name}
# Большой квадрат за текстом. Удалить файл — панели не будет.
type: BLOCK
block: 'minecraft:black_concrete'
location:
  world: {world}
  x: {x}
  y: {y}
  z: {z}
  yaw: 0.0
  pitch: 0.0
settings:
  enabled: true
  display-range: {rng}
  update-interval: 20
attributes:
  billboard:
    value-type: billboard_constraints
    value: CENTER
  scale:
    value-type: vector3f
    value:
      x: {sx}
      y: {sy}
      z: 0.05
  translation:
    value-type: vector3f
    value:
      x: {tx}
      y: {ty}
      z: -0.06
'''.format(name=name, world=world, x='%.3f' % x, y='%.3f' % y, z='%.3f' % z,
           rng=data.get('settings', {}).get('display-range', 48), sx='%.2f' % side, sy='%.2f' % side,
           tx='%.2f' % (-side / 2), ty='%.2f' % ty)

    path = os.path.join(out, name + '_panel.yml')
    open(path, 'w', encoding='utf-8', newline='').write(body)
    return name, side, ty


def main():
    src = sys.argv[1] if len(sys.argv) > 1 else 'DecentHolograms/displays'
    out = sys.argv[2] if len(sys.argv) > 2 else 'DecentHolograms/displays'
    for f in sorted(os.listdir(src)):
        if not f.endswith('.yml') or f.endswith('_panel.yml'):
            continue
        res = convert(os.path.join(src, f), out)
        if res:
            print('%-8s панель %.1f x %.1f  (y=%.2f)' % (res[0], res[1], res[1], res[2]))


if __name__ == '__main__':
    main()
