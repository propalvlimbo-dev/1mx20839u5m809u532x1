#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
DecentHolograms/*.yml  ->  dHolograms/holograms.yml  (новый вид).

Что делает:
  * берёт текстовые строки, координаты и иконки из старых голограмм;
  * переводит цвета & и &#RRGGBB в MiniMessage;
  * заголовок каждой голограммы — градиент + <bold>;
  * первую пустую строку после заголовка заменяет на анимированный разделитель (WAVE);
  * иконки (#ICON:) — отдельные ITEM-строки с вращением и покачиванием.

Запуск:
    python3 tools/make_dholograms.py DecentHolograms/holograms dHolograms/holograms.yml
"""
import os
import re
import sys

import yaml

LEGACY = {
    '0': 'black', '1': 'dark_blue', '2': 'dark_green', '3': 'dark_aqua',
    '4': 'dark_red', '5': 'dark_purple', '6': 'gold', '7': 'gray',
    '8': 'dark_gray', '9': 'blue', 'a': 'green', 'b': 'aqua',
    'c': 'red', 'd': 'light_purple', 'e': 'yellow', 'f': 'white',
    'k': 'obfuscated', 'l': 'bold', 'm': 'strikethrough', 'n': 'underlined',
    'o': 'italic', 'r': 'reset',
}

HEX_AMP = re.compile(r'&#([0-9a-fA-F]{6})')
AMP = re.compile(r'&([0-9a-fk-orA-FK-OR])')
ICON_RE = re.compile(r'^#(ICON|HEAD|SMALLHEAD)\s*[:\s]\s*(.+?)\s*$', re.IGNORECASE)

C1, C2 = '#F8BEFB', '#89A0FF'
DIVIDER = '▬▬▬▬▬▬▬▬▬▬▬▬▬'


def to_mini(text: str) -> str:
    """&7 / &l / &#F8BEFB  ->  <gray> / <bold> / <#F8BEFB>"""
    text = HEX_AMP.sub(lambda m: '<#%s>' % m.group(1), text)
    text = AMP.sub(lambda m: '<%s>' % LEGACY[m.group(1).lower()], text)
    return text


def strip_codes(text: str) -> str:
    """Убирает все & и &#RRGGBB коды — остаётся чистый текст."""
    text = HEX_AMP.sub('', text)
    text = AMP.sub('', text)
    return text.strip()


def gradient(text: str, start: str, end: str) -> str:
    return '<gradient:%s:%s><bold>%s</bold></gradient>' % (start, end, text)


def parse_location(raw: str):
    parts = str(raw).replace(',', '.').split(':')
    return {'world': parts[0], 'x': float(parts[1]),
            'y': float(parts[2]), 'z': float(parts[3])}


def build(name, data):
    loc = parse_location(data.get('location', ''))
    pages = data.get('pages') or [{}]
    src = pages[0].get('lines') or []

    raw_texts, icon = [], None
    for line in src:
        content = '' if line is None else str(line.get('content', ''))
        m = ICON_RE.match(content)
        if m:
            icon = m.group(2).upper().replace('MINECRAFT:', '')
            continue
        if re.match(r'^#(ENTITY|EMPTY)\b', content, re.IGNORECASE):
            continue
        raw_texts.append(content)

    out_lines = []
    divider_done = False
    for i, raw in enumerate(raw_texts):
        plain = strip_codes(raw)

        if i == 0:
            out_lines.append({'type': 'TEXT',
                              'content': gradient(plain, C1, C2),
                              'height-offset': 0.0})
            continue

        if not plain and not divider_done:
            divider_done = True
            out_lines.append({
                'type': 'TEXT',
                'content': '<#3A3F52>' + DIVIDER + '</#3A3F52>',
                'height-offset': 0.0,
                'anim-type': 'WAVE',
                'anim-color1': 0xF8BEFB,
                'anim-color2': 0x89A0FF,
                'anim-speed': 3,
            })
            continue

        # подзаголовок: строка только с &l и без прочих цветов
        if raw.startswith('&f&l') and len(plain) <= 24 and '&# ' not in raw:
            out_lines.append({'type': 'TEXT',
                              'content': gradient(plain, C1, '#C7D8FF'),
                              'height-offset': 0.0})
            continue

        out_lines.append({'type': 'TEXT',
                          'content': to_mini(raw) if raw.strip() else ' ',
                          'height-offset': 0.0})

    if icon:
        out_lines.append({'type': 'ITEM', 'content': icon,
                          'height-offset': 0.0,
                          'rotation-speed': 1.5,
                          'bob-amplitude': 0.12})

    display = ('billboard=CENTER'
               '|alignment=CENTER'
               '|scaleX=1.0|scaleY=1.0|scaleZ=1.0'
               '|backgroundColor=0'
               '|shadow=true'
               '|range=48.0'
               '|updateRange=-1.0'
               '|lineHeight=0.3'
               '|lineWidth=400'
               '|facing=0.0'
               '|downOrigin=false'
               '|unifiedBackground=true')

    return {
        'world': loc['world'],
        'x': loc['x'],
        'y': loc['y'],
        'z': loc['z'],
        'refresh-ticks': 20,
        'display-settings': display,
        'pages': [out_lines],
    }


def main():
    src = sys.argv[1] if len(sys.argv) > 1 else 'DecentHolograms/holograms'
    out = sys.argv[2] if len(sys.argv) > 2 else 'dHolograms/holograms.yml'

    result = {'holograms': {}}
    for f in sorted(os.listdir(src)):
        if not f.endswith('.yml'):
            continue
        name = f[:-4]
        data = yaml.safe_load(open(os.path.join(src, f), encoding='utf-8'))
        result['holograms'][name] = build(name, data)
        print('%-8s -> %d строк' % (name, len(result['holograms'][name]['pages'][0])))

    os.makedirs(os.path.dirname(out) or '.', exist_ok=True)
    with open(out, 'w', encoding='utf-8', newline='') as fh:
        yaml.dump(result, fh, allow_unicode=True, sort_keys=False,
                  default_flow_style=False, width=1000)
    print('\nзаписано:', out)


if __name__ == '__main__':
    main()
