#!/usr/bin/env python3
"""Собирает jar без JDK. Ресурсы кладутся рекурсивно с сохранением путей."""
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
        for root, _, files in os.walk(classes):
            for name in sorted(files):
                path = os.path.join(root, name)
                arcname = os.path.relpath(path, classes).replace(os.sep, "/")
                if not name.endswith(".class"):
                    # ресурсы (plugin.yml, config.yml) лежат только в корне
                    if root != classes:
                        continue
                    if name.endswith(".txt") or name.endswith(".log"):
                        continue
                jar.write(path, arcname)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
