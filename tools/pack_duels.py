#!/usr/bin/env python3
"""Пересобирает downloads/ElytriX-duels-full.zip из файлов репозитория.

Архив обновляется поверх существующего: то, что не указано в REPLACE/ADD,
остаётся как было (в архиве лежит папка plugins/BMenu целиком и исходники
форка, их незачем каждый раз копировать заново).

Запуск:  python3 tools/pack_duels.py
"""
from __future__ import annotations

import os
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ZIP = os.path.join(ROOT, "downloads", "ElytriX-duels-full.zip")
PREFIX = "ElytriX-duels-full/"

# файл в репозитории -> путь внутри архива
REPLACE = {
    "EpicDuels/config.yml": "plugins/EpicDuels/config.yml",
    "EpicDuels/menus.yml": "plugins/EpicDuels/menus.yml",
    "EpicDuels/scoreboard.yml": "plugins/EpicDuels/scoreboard.yml",
    "EpicDuels/lang/messages_ru.yml": "plugins/EpicDuels/lang/messages_ru.yml",
    "EpicDuels/README.md": "README.md",
    "EpicDuels/УСТАНОВКА.md": "УСТАНОВКА.md",
    "EpicDuels/ГАЙД.md": "ГАЙД.md",
    "EpicDuels/КИТЫ.md": "КИТЫ.md",
    "EpicDuels/АРЕНЫ.md": "АРЕНЫ.md",
    "EpicDuels/СТАВКИ.md": "СТАВКИ.md",
    "EpicDuels/BMenu-ДУЭЛИ.md": "BMenu-ДУЭЛИ.md",
    "ElytrixDuelsBridge/README.md": "МОСТ-МЕНЮ.md",
}

# папки: все *.yml внутри кладутся с сохранением имени
REPLACE_DIRS = {
    "BMenu/menu/elytrix/duel": "plugins/BMenu/menu/elytrix/duel",
    "ElytrixDuelsBridge/resources": "plugins/ElytrixDuelsBridge",
}

ADD = {
    "downloads/EpicDuels-ElytriX-3.1.0.jar": "plugins/EpicDuels-ElytriX-3.1.0.jar",
    "downloads/ElytrixDuelsBridge-1.3.1.jar": "plugins/ElytrixDuelsBridge-1.3.1.jar",
}


def archive_name(info: zipfile.ZipInfo) -> str:
    if info.flag_bits & 0x800:
        return info.filename
    return info.filename.encode("cp437").decode("utf-8")


def main() -> int:
    if not os.path.exists(ZIP):
        print(f"нет архива {ZIP}")
        return 1

    with zipfile.ZipFile(ZIP) as old:
        entries = [(archive_name(i), i.is_dir(), old.read(i)) for i in old.infolist()
                   if not i.is_dir()]

    merged: dict[str, bytes] = {name: data for name, _, data in entries}
    # старые версии jar моста не тащим в архив
    for name in [n for n in merged if "/plugins/ElytrixDuelsBridge-" in n]:
        del merged[name]
    # удалённые из репозитория меню не должны оставаться в архиве:
    # сверяем папки REPLACE_DIRS с тем, что реально лежит на диске
    for src_dir, dst_dir in REPLACE_DIRS.items():
        keep = {PREFIX + dst_dir + "/" + n for n in os.listdir(os.path.join(ROOT, src_dir))
                if n.endswith((".yml", ".yaml"))}
        for name in [n for n in merged if n.startswith(PREFIX + dst_dir + "/")]:
            if name not in keep:
                del merged[name]
    before = set(merged)

    for src, dst in REPLACE.items():
        path = os.path.join(ROOT, src)
        if not os.path.exists(path):
            print(f"пропуск (нет файла): {src}")
            continue
        with open(path, "rb") as fh:
            merged[PREFIX + dst] = fh.read()
    for src_dir, dst_dir in REPLACE_DIRS.items():
        base = os.path.join(ROOT, src_dir)
        for name in sorted(os.listdir(base)):
            if not name.endswith((".yml", ".yaml")) or name == "plugin.yml":
                continue
            with open(os.path.join(base, name), "rb") as fh:
                merged[PREFIX + dst_dir + "/" + name] = fh.read()
    for src, dst in ADD.items():
        path = os.path.join(ROOT, src)
        if not os.path.exists(path):
            print(f"пропуск (нет файла): {src}")
            continue
        with open(path, "rb") as fh:
            merged[PREFIX + dst] = fh.read()

    added = sorted(set(merged) - before)
    tmp = ZIP + ".tmp"
    with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name in sorted(merged):
            z.writestr(name, merged[name])
    os.replace(tmp, ZIP)
    print(f"обновлено: {len(merged)} файлов, {os.path.getsize(ZIP)} байт")
    for name in added:
        print("  +", name)
    return 0


if __name__ == "__main__":
    sys.exit(main())
