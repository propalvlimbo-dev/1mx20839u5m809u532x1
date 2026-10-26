#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Собирает шаблон арены EpicDuels из уже готового мира.

EpicDuels не умеет воевать в общем мире: арена `X` — это папка мира
`arena_template_X`, которую плагин копирует на каждый бой. Скрипт берёт
твой готовый мир (например `duel`), оставляет только чанки вокруг арены
и кладёт их в `arena_template_<имя>`. Ничего строить заново не нужно.

Запуск (сервер должен быть выключен):

    python3 tools/make_arena_template.py \
        --world "/путь/к/серверу/duel" --name arena1 \
        --center 120 64 -40 --radius 48

Параметры:
  --world   папка готового мира
  --name    имя арены (латиница, цифры, _ и -)
  --center  координаты центра арены в этом мире: X Z (Y не важна)
  --radius  сколько блоков вокруг центра оставить (по умолчанию 48)
  --out     куда положить шаблон (по умолчанию — рядом с миром-источником)
  --all     не обрезать: скопировать все чанки мира
"""
from __future__ import annotations

import argparse
import math
import os
import shutil
import struct
import sys
import time

SECTOR = 4096
HEADER = SECTOR * 2
FOLDERS = ("region", "entities", "poi")


def chunk_range(center_x: int, center_z: int, radius: int):
    """Диапазон номеров чанков, которые нужно оставить."""
    return (math.floor((center_x - radius) / 16.0),
            math.floor((center_x + radius) / 16.0),
            math.floor((center_z - radius) / 16.0),
            math.floor((center_z + radius) / 16.0))


def parse_region_name(name: str):
    # r.<x>.<z>.mca
    if not (name.startswith("r.") and name.endswith(".mca")):
        return None
    parts = name[2:-4].split(".")
    if len(parts) != 2:
        return None
    try:
        return int(parts[0]), int(parts[1])
    except ValueError:
        return None


def read_chunks(data: bytes):
    """Возвращает список (local_x, local_z, payload, timestamp)."""
    if len(data) < HEADER:
        return []
    out = []
    for index in range(1024):
        entry = data[index * 4:index * 4 + 4]
        offset = (entry[0] << 16) | (entry[1] << 8) | entry[2]
        sectors = entry[3]
        stamp = struct.unpack(">I", data[SECTOR + index * 4:SECTOR + index * 4 + 4])[0]
        if offset == 0 or sectors == 0:
            continue
        start = offset * SECTOR
        end = start + sectors * SECTOR
        if start + 5 > len(data):
            continue
        length = struct.unpack(">I", data[start:start + 4])[0]
        if length < 1:
            continue
        payload = data[start:start + 4 + length]
        if len(payload) < 5:
            payload = data[start:min(end, len(data))]
        out.append((index % 32, index // 32, payload, stamp))
    return out


def build_region(chunks) -> bytes:
    """Собирает region-файл из списка (local_x, local_z, payload, timestamp)."""
    header = bytearray(HEADER)
    body = bytearray()
    sector = 2
    for local_x, local_z, payload, stamp in chunks:
        padded = (len(payload) + SECTOR - 1) // SECTOR
        if sector + padded > 0xFFFFFF:
            break
        index = local_z * 32 + local_x
        header[index * 4] = (sector >> 16) & 0xFF
        header[index * 4 + 1] = (sector >> 8) & 0xFF
        header[index * 4 + 2] = sector & 0xFF
        header[index * 4 + 3] = padded & 0xFF
        struct.pack_into(">I", header, SECTOR + index * 4, stamp if stamp else int(time.time()))
        body += payload
        body += b"\x00" * (padded * SECTOR - len(payload))
        sector += padded
    return bytes(header + body)


def prune_file(src: str, dst: str, keep) -> tuple[int, int]:
    with open(src, "rb") as handle:
        data = handle.read()
    chunks = []
    for local_x, local_z, payload, stamp in read_chunks(data):
        if keep(local_x, local_z):
            chunks.append((local_x, local_z, payload, stamp))
    if not chunks:
        return 0, 0
    with open(dst, "wb") as handle:
        handle.write(build_region(chunks))
    return len(chunks), os.path.getsize(dst)


def main() -> int:
    parser = argparse.ArgumentParser(description="Шаблон арены EpicDuels из готового мира")
    parser.add_argument("--world", required=True, help="папка готового мира, например .../duel")
    parser.add_argument("--name", required=True, help="имя арены для EpicDuels")
    parser.add_argument("--center", nargs=2, type=int, metavar=("X", "Z"),
                        help="центр арены в блоках; без него нужен --all")
    parser.add_argument("--radius", type=int, default=48, help="радиус в блоках (по умолчанию 48)")
    parser.add_argument("--out", default=None, help="куда положить arena_template_<имя>")
    parser.add_argument("--all", action="store_true", help="копировать все чанки без обрезки")
    args = parser.parse_args()

    world = os.path.abspath(args.world)
    if not os.path.isdir(world):
        print(f"нет папки мира: {world}")
        return 1
    if not os.path.exists(os.path.join(world, "level.dat")):
        print(f"это не папка мира (нет level.dat): {world}")
        return 1
    if not args.all and not args.center:
        print("укажи --center X Z или --all")
        return 1
    if not all(c.isalnum() or c in "_-" for c in args.name):
        print("имя арены: только латиница, цифры, _ и -")
        return 1

    out_root = os.path.abspath(args.out) if args.out else os.path.dirname(world)
    target = os.path.join(out_root, "arena_template_" + args.name)
    if os.path.exists(target):
        print(f"уже существует: {target}\nудали её или возьми другое имя")
        return 1

    if args.all:
        shutil.copytree(world, target, ignore=shutil.ignore_patterns(
            "session.lock", "playerdata", "stats", "advancements", "DIM-1", "DIM1"))
        print(f"готово: {target} (полная копия)")
        return 0

    min_x, max_x, min_z, max_z = chunk_range(args.center[0], args.center[1], args.radius)
    keep = lambda cx, cz: min_x <= cx <= max_x and min_z <= cz <= max_z

    os.makedirs(target, exist_ok=True)
    shutil.copy2(os.path.join(world, "level.dat"), os.path.join(target, "level.dat"))
    icon = os.path.join(world, "icon.png")
    if os.path.exists(icon):
        shutil.copy2(icon, os.path.join(target, "icon.png"))

    total_chunks = 0
    total_size = 0
    for folder in FOLDERS:
        src_dir = os.path.join(world, folder)
        if not os.path.isdir(src_dir):
            continue
        dst_dir = os.path.join(target, folder)
        os.makedirs(dst_dir, exist_ok=True)
        for name in sorted(os.listdir(src_dir)):
            coords = parse_region_name(name)
            if coords is None:
                continue
            region_x, region_z = coords
            base_x, base_z = region_x * 32, region_z * 32
            if (max_x < base_x or min_x > base_x + 31 or max_z < base_z or min_z > base_z + 31):
                continue  # регион целиком вне арены
            chunks, size = prune_file(os.path.join(src_dir, name),
                                      os.path.join(dst_dir, name), keep)
            if chunks:
                total_chunks += chunks
                total_size += size

    print(f"чанки: {total_chunks}, размер шаблона: {total_size / 1024 / 1024:.1f} МБ")
    print(f"готово: {target}")
    print("Дальше на сервере: /duel arena tp " + args.name
          + " → /duel arena setspawn1 / setspawn2 → /duel arena save " + args.name)
    return 0


if __name__ == "__main__":
    sys.exit(main())
