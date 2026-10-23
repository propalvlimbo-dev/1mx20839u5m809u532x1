#!/usr/bin/env python3
"""Собирает jar без JDK: все .class из classes + все ресурсы рекурсивно из resources."""
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
    added: set[str] = set()
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as jar:
        for root, _, files in os.walk(classes):
            for name in sorted(files):
                if not name.endswith(".class"):
                    continue
                path = os.path.join(root, name)
                arcname = os.path.relpath(path, classes).replace(os.sep, "/")
                jar.write(path, arcname)
                added.add(arcname)
        for root, _, files in os.walk(resources):
            for name in sorted(files):
                path = os.path.join(root, name)
                arcname = os.path.relpath(path, resources).replace(os.sep, "/")
                if arcname not in added:
                    jar.write(path, arcname)
                    added.add(arcname)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
