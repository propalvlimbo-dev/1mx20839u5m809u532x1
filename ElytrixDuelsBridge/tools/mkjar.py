#!/usr/bin/env python3
"""Собирает jar без JDK (когда в JRE нет утилиты jar).

Запуск: python3 tools/mkjar.py build/out ElytrixDuelsBridge-1.7.4.jar resources
"""
from __future__ import annotations

import os
import sys
import zipfile


def main() -> int:
    if len(sys.argv) < 4:
        print(__doc__)
        return 2
    classes, target, resources = sys.argv[1], sys.argv[2], sys.argv[3]
    if os.path.exists(target):
        os.remove(target)
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as jar:
        for name in sorted(os.listdir(resources)):
            path = os.path.join(resources, name)
            if os.path.isfile(path):
                jar.write(path, name)
        for root, _, files in os.walk(classes):
            for name in sorted(files):
                if not name.endswith(".class"):
                    continue
                path = os.path.join(root, name)
                jar.write(path, os.path.relpath(path, classes).replace(os.sep, "/"))
    with zipfile.ZipFile(target) as jar:
        bad = jar.testzip()
        names = jar.namelist()
    print(f"готово: {target} ({os.path.getsize(target)} байт, {len(names)} файлов)")
    for name in names:
        print("  ", name)
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
