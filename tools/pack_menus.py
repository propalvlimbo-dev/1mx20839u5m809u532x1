"""Собрать отдельный архив только с меню BMenu для дуэлей."""
import os
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "downloads", "elytrix-duels-bmenu.zip")
MENUS = os.path.join(ROOT, "BMenu", "menu", "elytrix", "duel")
PREFIX = "elytrix-duels-bmenu/"

files = [(os.path.join("plugins", "BMenu", "menu", "elytrix", "duel", n), os.path.join(MENUS, n))
         for n in sorted(os.listdir(MENUS)) if n.endswith((".yml", ".yaml"))]
files.append((os.path.join("plugins", "BMenu", "config.yml"),
              os.path.join(ROOT, "BMenu", "config.yml")))
files.append(("README.md", os.path.join(ROOT, "EpicDuels", "BMenu-ДУЭЛИ.md")))

with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as z:
    for name, path in files:
        if os.path.exists(path):
            z.write(path, PREFIX + name)
print(f"готово: {OUT}")
