# -*- coding: utf-8 -*-
import json, os

H = "&#F8BEFB"
B = H + "&l┃ &f"
B0 = H + "&l┃ "
DOT = "&7● &f"
T = lambda t: "&7« " + H + t + " &7»"

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, "..", "..", "BMenu", "menu", "elytrix", "duel"))


def item(key, slot, material, name, lore, clicks=None):
    lines = [f"  {key}:", f"    slot: {slot}", f"    material: {material}",
             f"    name: {json.dumps(name, ensure_ascii=False)}"]
    if lore:
        lines.append("    lore:")
        for l in lore:
            lines.append("      - " + json.dumps(l, ensure_ascii=False))
    else:
        lines.append("    lore: []")
    if clicks:
        lines.append("    on_click:")
        for c in clicks:
            if c.startswith("sound: "):
                lines.append("      - " + c)
            else:
                lines.append("      - " + json.dumps(c, ensure_ascii=False))
    return "\n".join(lines)


def build(menu_id, title, items, reveal):
    head = [f"id: elytrix:{menu_id}", "provider: default", "type: chest",
            "size: 54", f"title: {json.dumps(H + '&l' + title, ensure_ascii=False)}", "items:"]
    for it in items:
        head.append(it)
    # панели
    head.append("  design_purple_panel:\n    material: purple_stained_glass_pane\n    name: '&r'")
    head.append("  design_black_panel:\n    material: black_stained_glass_pane\n    name: '&r'")

    used = set()
    for it in items:
        for line in it.splitlines():
            if line.strip().startswith("slot:"):
                used.add(int(line.split(":", 1)[1].strip()))
    for _name, slot in reveal:
        used.add(slot)

    border = [0,1,2,3,4,5,6,7,8, 9,17, 18,26, 27,35, 36,44, 45,46,47,48,49,50,51,52,53]
    purple = [s for s in border if s not in used]
    inner = [s for s in range(54) if s not in border and s not in used]

    def chunks(values, sizes):
        out, pos = [], 0
        for size in sizes:
            part = values[pos:pos + size]
            pos += size
            if part:
                out.append(",".join(str(v) for v in part))
        if pos < len(values):
            out.append(",".join(str(v) for v in values[pos:]))
        return out

    frames = []
    frames.append([(f"st: {reveal[0][0]} {reveal[0][1]}", "block.enchantment_table.use 0.3 1.5")])
    # рамка по спирали: низ-углы, бока, верх
    stages = [
        ([s for s in purple if s >= 36], 0.2, 0.85),
        ([s for s in purple if 18 <= s < 36], 0.23, 0.98),
        ([s for s in purple if 9 <= s < 18], 0.27, 1.13),
        ([s for s in purple if s < 9], 0.28, 1.21),
    ]
    for values, volume, pitch in stages:
        if values:
            frames.append([(f"st: design_purple_panel {','.join(str(v) for v in values)}",
                            f"block.note_block.chime {volume} {pitch}")])
    # внутреннее поле — двумя кадрами, чтобы не мигало всё сразу
    inner_parts = chunks(inner, [max(1, len(inner) // 2)])
    for idx, part in enumerate(inner_parts):
        frames.append([(f"st: design_black_panel {part}",
                        f"block.note_block.chime {0.26 + idx * 0.02:.2f} {1.3 + idx * 0.05:.2f}")])
    for i, (name, slot) in enumerate(reveal):
        pitch = 1.0 + i * 0.07
        frames.append([(f"st: {name} {slot}", f"block.note_block.pling 0.3 {pitch:.2f}")])

    lines = ["animation:"]
    first = True
    for ops in frames:
        if first:
            lines.append("  - tick: 0")
            lines.append("    opcodes:")
            first = False
        else:
            lines.append("  - opcodes:")
        for op, snd in ops:
            lines.append("      - " + op)
            if snd:
                lines.append(f"      - sound: '{snd}'")
    return "\n".join(head) + "\n\n" + "\n".join(lines) + "\n"


# ---------------------------------------------------------------- статистика
stats_items = [
    item("back", 45, "black_dye", T("Назад"), [B0, B + "Вернуться в меню дуэлей", B0, DOT + "Нажмите для возврата"],
         ["sound: 'ui.button.click 0.4 1.0'", "[open] elytrix:duel"]),
    item("close", 49, "red_dye", "&7« &cЗакрыть меню &7»", [B0, B + "Выход в игровой мир.", B0, DOT + "Нажмите для закрытия"],
         ["sound: 'ui.button.click 0.4 1.0'", "[close]"]),
    item("wins", 11, "emerald", T("Победы"),
         [B0, B + "Всего: " + H + "%epicduels_wins%", B0, DOT + "Растёт за каждый выигранный бой"]),
    item("losses", 13, "redstone", T("Поражения"),
         [B0, B + "Всего: " + H + "%epicduels_losses%", B0, DOT + "Не влияет на очередь"]),
    item("winrate", 15, "gold_ingot", T("Процент побед"),
         [B0, B + "Победы: " + H + "%epicduels_winrate%%", B0, DOT + "Считается по всем дуэлям"]),
    item("score", 29, "nether_star", T("Очки"),
         [B0, B + "Рейтинг: " + H + "%epicduels_score%", B0, DOT + "Растёт с каждой победой"]),
    item("top", 31, "book", T("Топ игроков"),
         [B0, B + "Список: " + H + "топ-10 по победам", B0, DOT + "Нажмите, чтобы посмотреть"],
         ["sound: 'block.note_block.pling 0.4 1.2'", "[player] duel leaderboard wins"]),
    item("play", 33, "compass", T("Сыграть"),
         [B0, B + "Режим: " + H + "вызов игрока", B0, DOT + "Нажмите, чтобы выбрать игрока"],
         ["sound: 'block.note_block.pling 0.4 1.3'", "[player] duel duels"]),
]
open(os.path.join(OUT, "stats.yml"), "w", encoding="utf-8").write(
    build("duel_stats", "Статистика", stats_items,
          [("back", 45), ("close", 49), ("wins", 11), ("losses", 13), ("winrate", 15),
           ("score", 29), ("top", 31), ("play", 33)]))

# ------------------------------------------------------------------- раунды
rounds_items = [
    item("back", 45, "black_dye", T("Назад"), [B0, B + "Вернуться к выбору кита", B0, DOT + "Нажмите для возврата"],
         ["sound: 'ui.button.click 0.4 1.0'", "[player] duel duels"]),
    item("close", 49, "red_dye", "&7« &cЗакрыть меню &7»", [B0, B + "Выход в игровой мир.", B0, DOT + "Нажмите для закрытия"],
         ["sound: 'ui.button.click 0.4 1.0'", "[close]"]),
    item("bo1", 11, "paper", T("1 раунд"),
         [B0, B + "Победа: " + H + "один бой", B0, DOT + "Нажмите, чтобы выбрать"],
         ["sound: 'block.note_block.pling 0.4 1.1'", "[player] duelx rounds 1"]),
    item("bo3", 13, "book", T("3 раунда"),
         [B0, B + "Победа: " + H + "2 боя", B0, DOT + "Нажмите, чтобы выбрать"],
         ["sound: 'block.note_block.pling 0.4 1.2'", "[player] duelx rounds 3"]),
    item("bo5", 15, "enchanted_book", T("5 раундов"),
         [B0, B + "Победа: " + H + "3 боя", B0, DOT + "Нажмите, чтобы выбрать"],
         ["sound: 'block.note_block.pling 0.4 1.3'", "[player] duelx rounds 5"]),
]
open(os.path.join(OUT, "rounds.yml"), "w", encoding="utf-8").write(
    build("duel_rounds", "Раунды", rounds_items,
          [("back", 45), ("close", 49), ("bo1", 11), ("bo3", 13), ("bo5", 15)]))

# -------------------------------------------------------------------- группа
party_items = [
    item("back", 45, "black_dye", T("Назад"), [B0, B + "Вернуться в меню дуэлей", B0, DOT + "Нажмите для возврата"],
         ["sound: 'ui.button.click 0.4 1.0'", "[open] elytrix:duel"]),
    item("close", 49, "red_dye", "&7« &cЗакрыть меню &7»", [B0, B + "Выход в игровой мир.", B0, DOT + "Нажмите для закрытия"],
         ["sound: 'ui.button.click 0.4 1.0'", "[close]"]),
    item("team", 11, "diamond_sword", T("Командная дуэль"),
         [B0, B + "Формат: " + H + "2 на 2 — 4 на 4", B0, DOT + "Нажмите, чтобы выбрать размер"],
         ["sound: 'block.note_block.pling 0.4 1.15'", "[player] duelx party team"]),
    item("tournament", 15, "golden_horse_armor", T("Турнир"),
         [B0, B + "Формат: " + H + "сетка на вылет", B0, DOT + "Нажмите, чтобы выбрать кит"],
         ["sound: 'block.note_block.pling 0.4 1.3'", "[player] duelx party tournament"]),
]
open(os.path.join(OUT, "party.yml"), "w", encoding="utf-8").write(
    build("duel_party", "Группа", party_items,
          [("back", 45), ("close", 49), ("team", 11), ("tournament", 15)]))

# ------------------------------------------------------------- размер команд
size_items = [
    item("back", 45, "black_dye", T("Назад"), [B0, B + "Вернуться к режимам", B0, DOT + "Нажмите для возврата"],
         ["sound: 'ui.button.click 0.4 1.0'", "[open] elytrix:duel_party"]),
    item("close", 49, "red_dye", "&7« &cЗакрыть меню &7»", [B0, B + "Выход в игровой мир.", B0, DOT + "Нажмите для закрытия"],
         ["sound: 'ui.button.click 0.4 1.0'", "[close]"]),
    item("2v2", 11, "iron_sword", T("2 на 2"),
         [B0, B + "Игроков: " + H + "4", B0, DOT + "Нажмите, чтобы выбрать"],
         ["sound: 'block.note_block.pling 0.4 1.1'", "[player] duelx size 2"]),
    item("3v3", 13, "golden_sword", T("3 на 3"),
         [B0, B + "Игроков: " + H + "6", B0, DOT + "Нажмите, чтобы выбрать"],
         ["sound: 'block.note_block.pling 0.4 1.2'", "[player] duelx size 3"]),
    item("4v4", 15, "diamond_sword", T("4 на 4"),
         [B0, B + "Игроков: " + H + "8", B0, DOT + "Нажмите, чтобы выбрать"],
         ["sound: 'block.note_block.pling 0.4 1.3'", "[player] duelx size 4"]),
]
open(os.path.join(OUT, "party_size.yml"), "w", encoding="utf-8").write(
    build("duel_party_size", "Размер команд", size_items,
          [("back", 45), ("close", 49), ("2v2", 11), ("3v3", 13), ("4v4", 15)]))

print("готово:", sorted(os.listdir(OUT)))
