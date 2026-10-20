#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
DecentHolograms: классические голограммы (armor stand) -> новый тип Displays (2.10+).

Читает holograms/*.yml и пишет displays/*.yml:
  * текстовая часть -> один TEXT-дисплей (все текстовые строки сразу);
  * строка #ICON: / #HEAD: / #SMALLHEAD: -> отдельный ITEM-дисплей <имя>_icon.yml.

Координаты, display-range и update-interval сохраняются; вертикальное смещение
считается по старым height, чтобы блок текста остался на том же месте
(у text_display текст растёт вверх от точки привязки).

Запуск:
    python3 tools/dh2displays.py DecentHolograms/holograms DecentHolograms/displays
"""
import os
import re
import sys

try:
    import yaml
except ImportError:
    sys.exit('Нужен PyYAML:  pip install pyyaml')

ICON_RE = re.compile(r'^#(ICON|HEAD|SMALLHEAD)\s*[:\s]\s*(.+?)\s*$', re.IGNORECASE)
SKIP_RE = re.compile(r'^#(ENTITY|EMPTY)\b', re.IGNORECASE)
# шаг строк у text_display: 9 пикселей шрифта = 0.225 блока
LINE_H = 0.225
# голограммы, которые не должны поворачиваться за игроком
STATIC = {'addvk', 'helps'}

HEADER = '''# DecentHolograms 2.10+ — голограмма нового типа (display entity)
# Переделано из классической голограммы: {source}
{note}{extra}type: {dtype}
location:
  world: {world}
  x: {x}
  y: {y}
  z: {z}
  yaw: 0.0
  pitch: 0.0
settings:
  enabled: {enabled}
  display-range: {range}
  update-interval: {interval}
attributes:
'''

TEXT_ATTRS = '''  billboard:
    value-type: billboard_constraints
    value: {billboard}
  background-color:
    value-type: rgba
    value:
      red: 8
      green: 8
      blue: 12
      alpha: 0
  text-shadow:
    value-type: boolean
    value: true
  see-through:
    value-type: boolean
    value: false
  alignment:
    value-type: text_alignment
    value: LEFT
  line-width:
    value-type: integer
    value: 1000
  text-opacity:
    value-type: integer
    value: 255
  scale:
    value-type: vector3f
    value:
      x: 1.0
      y: 1.0
      z: 1.0
  translation:
    value-type: vector3f
    value:
      x: 0.0
      y: {ty}
      z: 0.0
'''

ITEM_ATTRS = '''  billboard:
    value-type: billboard_constraints
    value: CENTER
  display-type:
    value-type: item_display_type
    value: GUI
  scale:
    value-type: vector3f
    value:
      x: 1.0
      y: 1.0
      z: 1.0
  translation:
    value-type: vector3f
    value:
      x: 0.0
      y: {ty}
      z: 0.0
'''

PAGES = 'pages:\n  - lines:\n'


def q(value):
    return "'" + str(value).replace("'", "''") + "'"


def parse_location(raw):
    parts = str(raw).replace(',', '.').split(':')
    if len(parts) < 4:
        raise ValueError('не разобрал location: %r' % raw)
    return {
        'world': parts[0],
        'x': float(parts[1]),
        'y': float(parts[2]),
        'z': float(parts[3]),
    }


def to_display_name(filename):
    return os.path.splitext(os.path.basename(filename))[0]


def convert(path, out_dir, stats):
    data = yaml.safe_load(open(path, encoding='utf-8'))
    name = to_display_name(path)
    loc = parse_location(data.get('location', ''))
    enabled = 'true' if data.get('enabled', True) else 'false'
    rng = data.get('display-range', 48)
    interval = data.get('update-interval', 20)

    pages = data.get('pages') or []
    lines = []
    if pages:
        lines = pages[0].get('lines') or []
    else:
        lines = data.get('lines') or []

    texts, items = [], []
    offset = 0.0
    for line in lines:
        content = '' if line is None else str(line.get('content', ''))
        height = float(line.get('height', 0.3))
        m = ICON_RE.match(content)
        if m:
            items.append({'kind': m.group(1).upper(), 'value': m.group(2),
                          'y': offset, 'height': height})
        elif SKIP_RE.match(content):
            stats['skipped'].append((name, content))
        else:
            texts.append({'content': content, 'y': offset})
        offset += height

    if not texts and not items:
        stats['empty'].append(name)
        return

    written = []
    if texts:
        # у старой голограммы первая строка стояла в точке location, строки шли
        # вниз; text_display ведёт себя так же, поэтому смещение не нужно
        text_bottom = 0.0  # верхняя строка = якорь
        fname = os.path.join(out_dir, name + '.yml')
        note = ('# Текст 1:1, иконка — отдельный файл %s_icon.yml.\n' % name) if items else \
               '# Текст 1:1 из старой голограммы.\n'
        body = HEADER.format(source=name + '.yml', note=note,
                             extra='', dtype='TEXT', world=q(loc['world']),
                             x='%.3f' % loc['x'], y='%.3f' % loc['y'], z='%.3f' % loc['z'],
                             enabled=enabled, range=rng, interval=interval)
        billboard = 'FIXED' if name in STATIC else 'VERTICAL'
        body += (TEXT_ATTRS.replace('{ty}', '%.2f' % text_bottom)
                            .replace('{billboard}', billboard))
        body += PAGES
        for t in texts:
            body += '      - ' + q(t['content']) + '\n'
        open(fname, 'w', encoding='utf-8', newline='').write(body)
        written.append(os.path.basename(fname))
        stats['text'].append((name, len(texts), text_bottom))

    icon_y = -len(texts) * LINE_H - 0.45

    for item in items:
        icon_name = name + '_icon'
        fname = os.path.join(out_dir, icon_name + '.yml')
        material = item['value'].upper().replace('MINECRAFT:', '')
        note = '# Иконка (строка %s) из старой голограммы, текст — в %s.yml.\n' % (
            item['value'], name)
        body = HEADER.format(source=name + '.yml', note=note,
                             extra="item: %s\n" % q(material), dtype='ITEM',
                             world=q(loc['world']), x='%.3f' % loc['x'],
                             y='%.3f' % loc['y'], z='%.3f' % loc['z'],
                             enabled=enabled, range=rng, interval=interval)
        body += ITEM_ATTRS.replace('{ty}', '%.2f' % icon_y)
        open(fname, 'w', encoding='utf-8', newline='').write(body)
        written.append(os.path.basename(fname))
        stats['items'].append((icon_name, material, icon_y))

    print('%-10s -> %s' % (name, ', '.join(written)))


def main():
    src = sys.argv[1] if len(sys.argv) > 1 else 'holograms'
    dst = sys.argv[2] if len(sys.argv) > 2 else 'displays'
    os.makedirs(dst, exist_ok=True)
    stats = {'skipped': [], 'empty': [], 'text': [], 'items': []}

    files = sorted(f for f in os.listdir(src) if f.endswith('.yml'))
    if not files:
        print('В %s нет .yml' % src)
        return

    for f in files:
        try:
            convert(os.path.join(src, f), dst, stats)
        except Exception as exc:  # noqa: BLE001
            print('!! %s: %s' % (f, exc))

    print('\nТекстовых дисплеев: %d, предметных: %d' % (len(stats['text']), len(stats['items'])))
    for name, content in stats['skipped']:
        print('   пропущено (%s): %s' % (name, content))
    for name in stats['empty']:
        print('   пусто, не создано: %s' % name)


if __name__ == '__main__':
    main()
