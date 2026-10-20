#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Большой «квадрат» фона у голограмм DecentHolograms 2.10+ — без блочной панели.

Клиент рисует фон text_display ПОСТРОЧНО (плашка = ширина строки).
Поэтому все строки добиваются пробелами до одной ширины — плашки складываются
в один ровный прямоугольник. Пробелы добавляются с двух сторон, текст остаётся
по центру.

Запуск:
    python3 tools/make_boxes.py DecentHolograms/displays DecentHolograms/displays-box
"""
import os
import re
import sys

import yaml

W = {' ': 4, '!': 2, '"': 5, '#': 6, '$': 6, '%': 6, '&': 6, "'": 2,
     '(': 5, ')': 5, '*': 5, '+': 6, ',': 2, '-': 6, '.': 2, '/': 6,
     ':': 2, ';': 2, '<': 5, '=': 6, '>': 5, '?': 6, '@': 7,
     'I': 4, '[': 4, ']': 4, 't': 4, 'i': 2, 'l': 3, 'f': 5, 'k': 5,
     '⭐': 14, '⛈': 13, '☁': 10, '●': 7, '▪': 6, '▬': 10,
     'ᴇ': 6, 'ᴛ': 6, 'ʟ': 6, 'ᴅ': 6, 'ʀ': 6, 'ᴀ': 6, 'ɢ': 6, 'ᴏ': 6,
     'ɴ': 6, 'ᴍ': 6, 'ɪ': 4, 'ᴘ': 6, 'ѕ': 6, 'ᴄ': 6, 'ᴜ': 6, 'ʜ': 6,
     'ᴡ': 7, 'ꜰ': 6, 'ᴠ': 6, 'ᴋ': 6, 'ᴢ': 6, 'ᴊ': 6, 'ʏ': 6}

CODE = re.compile(r'&#[0-9a-fA-F]{6}|&[0-9a-fk-orA-FK-OR]|<[^>]{1,24}>')
SPACE = 4
# цвет плашки: тёмно-синий почти чёрный, alpha 150 (~59%)
BG = (8, 8, 12, 150)


def width_px(text):
    total = 0
    for ch in CODE.sub('', text):
        total += W.get(ch, 6)
    return total


def pad(line, target):
    need = max(0, target - width_px(line))
    left = (need // 2) // SPACE
    right = (need - left * SPACE) // SPACE
    return ' ' * left + line + ' ' * right


def q(text):
    return "'" + text.replace("'", "''") + "'"


def convert(src, dst, out_dir):
    name = os.path.splitext(os.path.basename(src))[0]
    text = open(src, encoding='utf-8').read()
    data = yaml.safe_load(text)
    if data.get('type') != 'TEXT':
        return None

    lines = data['pages'][0]['lines']
    target = max(width_px(l) for l in lines)
    padded = [pad(l, target) for l in lines]

    # собираем файл руками, чтобы сохранить комментарии и порядок
    out, skip_lines = [], False
    for raw in text.splitlines():
        if raw.strip() == 'pages:':
            break
        if raw.startswith('      red:'):
            out.append('      red: %d' % BG[0]); continue
        if raw.startswith('      green:'):
            out.append('      green: %d' % BG[1]); continue
        if raw.startswith('      blue:'):
            out.append('      blue: %d' % BG[2]); continue
        if raw.startswith('      alpha:'):
            out.append('      alpha: %d' % BG[3]); continue
        if 'Фон' in raw or 'плашк' in raw.lower():
            continue
        out.append(raw)

    out.insert(2, '# Вариант с большим фоном-квадратом (строки добиты пробелами).')
    out.append('pages:')
    out.append('  - lines:')
    for line in padded:
        out.append('      - ' + q(line))

    path = os.path.join(out_dir, name + '.yml')
    open(path, 'w', encoding='utf-8', newline='').write('\n'.join(out) + '\n')
    return name, target


def main():
    src = sys.argv[1] if len(sys.argv) > 1 else 'DecentHolograms/displays'
    dst = sys.argv[2] if len(sys.argv) > 2 else 'DecentHolograms/displays-box'
    os.makedirs(dst, exist_ok=True)
    for f in sorted(os.listdir(src)):
        if not f.endswith('.yml') or f.endswith('_icon.yml') or f.endswith('_panel.yml'):
            continue
        res = convert(os.path.join(src, f), dst, dst)
        if res:
            print('%-8s ширина %d px = %.1f блока' % (res[0], res[1], res[1] / 40.0))


if __name__ == '__main__':
    main()
