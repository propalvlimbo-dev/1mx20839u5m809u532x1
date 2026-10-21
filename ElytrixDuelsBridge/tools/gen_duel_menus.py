# -*- coding: utf-8 -*-
"""Генератор меню дуэлей elytrix:duel*.

Стиль и кадр полностью повторяют дефолтное /menu (elytrix:menu):
  * title       — &#F8BEFB&l<раздел>
  * кнопка      — &7« &#F8BEFBНазвание &7»
  * lore        — &#F8BEFB&l┃ + «&#F8BEFB&l┃ &fПараметр: &#F8BEFBЗначение» + &7● &fНажмите…
  * кадр        — чёрные панели сверху, фиолетовые по низу и бокам
  * анимация    — панели по кадру, затем кнопки с мягким pling
"""
import json
import os

H = "&#F8BEFB"
B = H + "&l┃ &f"
B0 = H + "&l┃ "
DOT = "&7● &f"
T = lambda t: "&7« " + H + t + " &7»"

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, "..", "..", "BMenu", "menu", "elytrix", "duel"))

# --------------------------------------------------------------- сетка меню
BLACK = list(range(0, 11)) + [16, 17, 18, 26]
PURPLE = [27, 35, 36, 37, 43, 44] + list(range(45, 54))
ROW1 = [20, 21, 22, 23, 24]
ROW2 = [29, 30, 31, 32, 33]
PLACE = {
    1: [22],
    2: [21, 23],
    3: [21, 22, 23],
    4: [20, 21, 23, 24],
    5: ROW1,
    6: ROW1 + [31],
    7: ROW1 + [30, 32],
    8: ROW1 + [30, 31, 32],
    9: ROW1 + [29, 30, 31, 32],
    10: ROW1 + ROW2,
}


def item(key, material, name, lore, clicks=None, extra=None):
    lines = [f"  {key}:", f"    material: {material}",
             f"    name: {json.dumps(name, ensure_ascii=False)}"]
    if lore:
        lines.append("    lore:")
        for line in lore:
            lines.append("      - " + json.dumps(line, ensure_ascii=False))
    else:
        lines.append("    lore: []")
    if extra:
        lines.extend(extra)
    if clicks:
        lines.append("    on_click:")
        for click in clicks:
            if click.startswith("sound: "):
                lines.append("      - " + click)
            else:
                lines.append("      - " + json.dumps(click, ensure_ascii=False))
    return "\n".join(lines)


def key_of(block):
    return block.split(":", 1)[0].strip()


def build(menu_id, title, content, tail=None):
    """content — списки (item_block, slot). tail — закреплённые внизу кнопки."""
    tail = tail or []
    used = [slot for _block, slot in content] + [slot for _block, slot in tail]
    assert len(used) == len(set(used)), f"{menu_id}: дубли слотов {used}"

    head = [f"id: elytrix:{menu_id}", "provider: default", "type: chest", "size: 54",
            f"title: {json.dumps(H + '&l' + title, ensure_ascii=False)}", "items:"]
    head.extend(block for block, _slot in content)
    head.extend(block for block, _slot in tail)
    head.append("  design_purple_panel:\n    material: purple_stained_glass_pane\n    name: '&r'")
    head.append("  design_black_panel:\n    material: black_stained_glass_pane\n    name: '&r'")

    purple = [s for s in PURPLE if s not in used]
    black = [s for s in BLACK if s not in used]

    frames = []  # (opcode, sound)
    first = [(f"st: {key_of(tail[0][0])} {tail[0][1]}", "block.enchantment_table.use 0.3 1.5")]
    for block, slot in tail[1:]:
        first.append((f"st: {key_of(block)} {slot}", None))
    frames.append(first)

    # фиолетовый кадр — низ, бока, затем чёрный верх
    stages = [
        ("design_purple_panel", [s for s in purple if 45 <= s <= 53], 0.2, 0.85),
        ("design_purple_panel", [s for s in purple if 36 <= s <= 44], 0.22, 0.91),
        ("design_purple_panel", [s for s in purple if s in (27, 35)], 0.23, 0.98),
        ("design_black_panel", [s for s in black if s >= 18], 0.25, 1.05),
        ("design_black_panel", [s for s in black if 9 <= s <= 17], 0.27, 1.13),
        ("design_black_panel", [s for s in black if s <= 8], 0.28, 1.21),
    ]
    for panel, values, volume, pitch in stages:
        if values:
            frames.append([(f"st: {panel} {','.join(map(str, values))}",
                            f"block.note_block.chime {volume} {pitch}")])

    for index, (block, slot) in enumerate(content):
        frames.append([(f"st: {key_of(block)} {slot}",
                        f"block.note_block.pling {0.28 + min(index, 8) * 0.02:.2f} "
                        f"{1.0 + index * 0.07:.2f}")])

    lines = ["animation:"]
    for index, ops in enumerate(frames):
        if index == 0:
            lines.append("  - tick: 0")
        else:
            lines.append("  - opcodes:")
        if index == 0:
            lines.append("    opcodes:")
        for opcode, sound in ops:
            lines.append("      - " + opcode)
            if sound:
                lines.append(f"      - sound: '{sound}'")
    return "\n".join(head) + "\n\n" + "\n".join(lines) + "\n"


def back(slot, menu="elytrix:duel", label="Назад", hint="Вернуться в меню дуэлей"):
    return (item("back", "black_dye", T(label), [B0, B + hint, B0, DOT + "Нажмите для возврата"],
                 ["sound: 'ui.button.click 0.4 1.0'", f"[open] {menu}"]), slot)


def close(slot=49):
    return (item("close", "red_dye", "&7« &cЗакрыть меню &7»",
                 [B0, B + "Выход в игровой мир.", B0, DOT + "Нажмите для закрытия"],
                 ["sound: 'ui.button.click 0.4 1.0'", "[close]"]), slot)


def write(name, text):
    with open(os.path.join(OUT, name), "w", encoding="utf-8") as handle:
        handle.write(text)


KITS = [
    ("sword", "netherite_sword", "Классика", "меч, железная броня, гаплы"),
    ("uhc", "golden_apple", "УХК", "меч, лук, гаплы"),
    ("gapple", "enchanted_golden_apple", "Гаппли", "алмазка и яблоки"),
    ("sumo", "fishing_rod", "Сумо", "только кулаки"),
    ("archer", "bow", "Лучник", "лук и стрелы"),
    ("mace", "mace", "Булава", "булава и вихри"),
    ("axe", "netherite_axe", "Топор и щит", "топор, щит, броня"),
    ("trident", "trident", "Тризуб", "тризуб и броня"),
    ("crossbow", "crossbow", "Арбалет", "арбалет и стрелы"),
    ("netherite", "netherite_chestplate", "Незерит", "незерит и меч"),
]

# ------------------------------------------------------------------ главное
main = [
    (item("duel", "netherite_sword", T("Дуэль 1 на 1"),
          [B0, B + "Формат: " + H + "1 на 1", B + "Кит: " + H + "выбираешь сам",
           B + "Раунды: " + H + "выбираешь сам", B0, DOT + "Нажмите, чтобы выбрать игрока"],
          ["sound: 'block.note_block.pling 0.4 1.1'", "[player] duel duels"]), 20),
    (item("team", "iron_sword", T("Бои 2 на 2"),
          [B0, B + "Формат: " + H + "2 на 2", B + "Тиммейт: " + H + "выбираешь сам",
           B + "Очередь: " + H + "встаёте парой", B0, DOT + "Нажмите, чтобы выбрать тиммейта"],
          ["sound: 'block.note_block.pling 0.4 1.18'", "[player] duelx team"]), 21),
    (item("queue", "compass", T("Подбор соперника"),
          [B0, B + "Режим: " + H + "очередь", B + "Кит: " + H + "выбираешь сам",
           B + "Соперник: " + H + "подбирается сам", B0, DOT + "Нажмите, чтобы выбрать кит"],
          ["sound: 'block.note_block.pling 0.4 1.26'", "[open] elytrix:duel_queue"]), 22),
    (item("stats", "book", T("Статистика"),
          [B0, B + "Победы: " + H + "{wins}", B + "Поражения: " + H + "{losses}",
           B + "Процент побед: " + H + "{winrate}%", B + "Очки: " + H + "{score}",
           B0, DOT + "Нажмите для подробностей"],
          ["sound: 'block.note_block.pling 0.4 1.34'", "[open] elytrix:duel_stats"]), 23),
    (item("top", "nether_star", T("Рейтинг"),
          [B0, B + "Список: " + H + "топ-10 по победам", B + "Обновление: " + H + "каждый бой",
           B0, DOT + "Нажмите, чтобы посмотреть"],
          ["sound: 'block.note_block.pling 0.4 1.42'", "[player] duel leaderboard wins"]), 24),
    (item("help", "knowledge_book", T("Как играть"),
          [B0, B + "Гайд: " + H + "4 шага до боя", B + "Команды: " + H + "внутри",
           B0, DOT + "Нажмите, чтобы открыть"],
          ["sound: 'block.note_block.pling 0.4 1.5'", "[open] elytrix:duel_help"]), 31),
]
main_tail = [back(45, menu="elytrix:menu", label="Главное меню",
                  hint="Вернуться в общее меню сервера"), close(49)]
# профиль — шапка меню, цифры обновляются на месте
main.append((item("profile", "player_head", T("Твой профиль"),
                  [B0, B + "Победы: " + H + "{wins}", B + "Поражения: " + H + "{losses}",
                   B + "Процент побед: " + H + "{winrate}%", B + "Очки: " + H + "{score}",
                   B0, DOT + "Данные обновляются в бою"],
                  None, extra=["    tick_speed: 40", "    on_tick:", "      - '[update]'"]), 4))
write("duel.yml", build("duel", "Дуэли", main, main_tail))

# -------------------------------------------------------------- статистика
stats_content = [
    (item("wins", "emerald", T("Победы"),
          [B0, B + "Всего: " + H + "{wins}", B0, DOT + "Растёт за каждый выигранный бой"]), 20),
    (item("losses", "redstone", T("Поражения"),
          [B0, B + "Всего: " + H + "{losses}", B0, DOT + "В очереди не учитывается"]), 22),
    (item("winrate", "gold_ingot", T("Процент побед"),
          [B0, B + "Победы: " + H + "{winrate}%", B0, DOT + "Считается по всем дуэлям"]), 24),
    (item("score", "nether_star", T("Очки"),
          [B0, B + "Рейтинг: " + H + "{score}", B0, DOT + "Растёт с каждой победой"]), 30),
    (item("top", "book", T("Топ игроков"),
          [B0, B + "Список: " + H + "топ-10 по победам", B0, DOT + "Нажмите, чтобы посмотреть"],
          ["sound: 'block.note_block.pling 0.4 1.2'", "[player] duel leaderboard wins"]), 32),
    (item("play", "compass", T("Сыграть"),
          [B0, B + "Режим: " + H + "вызов игрока", B0, DOT + "Нажмите, чтобы выбрать игрока"],
          ["sound: 'block.note_block.pling 0.4 1.3'", "[player] duel duels"]), 31),
]
stats_tail = [back(45), close(49)]
write("stats.yml", build("duel_stats", "Статистика", stats_content, stats_tail))

# ------------------------------------------------------------------ раунды
rounds_content = [
    (item("bo1", "paper", T("1 раунд"),
          [B0, B + "Победа: " + H + "один бой", B0, DOT + "Нажмите, чтобы выбрать"],
          ["sound: 'block.note_block.pling 0.4 1.1'", "[player] duelx rounds 1"]), 21),
    (item("bo3", "book", T("3 раунда"),
          [B0, B + "Победа: " + H + "2 боя", B0, DOT + "Нажмите, чтобы выбрать"],
          ["sound: 'block.note_block.pling 0.4 1.2'", "[player] duelx rounds 3"]), 22),
    (item("bo5", "enchanted_book", T("5 раундов"),
          [B0, B + "Победа: " + H + "3 боя", B0, DOT + "Нажмите, чтобы выбрать"],
          ["sound: 'block.note_block.pling 0.4 1.3'", "[player] duelx rounds 5"]), 23),
]
rounds_tail = [back(45, hint="Вернуться к выбору кита"), close(49)]
write("rounds.yml", build("duel_rounds", "Раунды", rounds_content, rounds_tail))

# ---------------------------------------------------------------- подтверждение
confirm_content = [
    (item("confirm", "lime_dye", "&7« &aНачать бой &7»",
          [B0, B + "Режим: " + H + "{mode}", B + "Размер: " + H + "{size}",
           B + "Кит: " + H + "{kit}", B0, DOT + "{action}"],
          ["sound: 'block.note_block.pling 0.4 1.4'", "[player] duelx confirm"]), 21),
    (item("cancel", "red_dye", "&7« &cОтмена &7»",
          [B0, B + "Вернуться к выбору кита", B0, DOT + "Нажмите, чтобы отменить"],
          ["sound: 'ui.button.click 0.4 1.0'", "[player] duelx cancel"]), 23),
]
confirm_tail = [back(45, menu="elytrix:duel_kit", hint="Вернуться к выбору кита"), close(49)]
write("confirm.yml", build("duel_confirm", "Подтверждение", confirm_content, confirm_tail))

# ------------------------------------------------------------------ выбор кита
kit_content = []
for index, (kit_id, material, title, hint) in enumerate(KITS):
    kit_content.append((
        item("kit_" + kit_id, material, T(title),
             [B0, B + "Кит: " + H + kit_id, B + "Состав: " + H + hint,
              B0, DOT + "Нажмите, чтобы выбрать"],
             [f"sound: 'block.note_block.pling 0.4 {1.05 + index * 0.05:.2f}'",
              f"[player] duelx kit {kit_id}"]),
        PLACE[10][index]))
kit_tail = [back(45), close(49)]
write("kit.yml", build("duel_kit", "Выбор кита", kit_content, kit_tail))

# --------------------------------------------------------------- поиск соперника
queue_content = []
for index, (kit_id, material, title, hint) in enumerate(KITS):
    queue_content.append((
        item("kit_" + kit_id, material, T(title),
             [B0, B + "Кит: " + H + hint, B + "Поиск: " + H + "начнётся сразу",
              B0, DOT + "Нажмите, чтобы искать бой"],
             [f"sound: 'block.note_block.pling 0.4 {1.05 + index * 0.05:.2f}'",
              f"[player] duel queue {kit_id}", "[close]"]),
        PLACE[10][index]))
queue_content.append((
    item("leave", "barrier", "&7« &cВыйти из очереди &7»",
         [B0, B + "Очередь: " + H + "будет сброшена", B0, DOT + "Нажмите, чтобы выйти"],
         ["sound: 'ui.button.click 0.4 1.0'", "[player] duel queue leave", "[close]"]),
    40))
queue_tail = [back(45), close(49)]
write("queue.yml", build("duel_queue", "Поиск соперника", queue_content, queue_tail))

# --------------------------------------------------------------------- помощь
help_content = [
    (item("step1", "paper", T("Шаг 1 — выбери режим"),
          [B0, B + "Дуэль: " + H + "вызываешь игрока сам",
           B + "Очередь: " + H + "соперника ищем за тебя",
           B0, DOT + "Открой меню дуэлей: /duels"],
          ["sound: 'ui.button.click 0.4 1.0'", "[open] elytrix:duel"]), 20),
    (item("step2", "paper", T("Шаг 2 — выбери кит"),
          [B0, B + "Киты: " + H + "10 готовых наборов",
           B + "Предметы: " + H + "выдаются автоматически",
           B + "Свой кит собрать: " + H + "нельзя",
           B0, DOT + "Нажми на кит, чтобы продолжить"]), 21),
    (item("step3", "paper", T("Шаг 3 — карта и раунды"),
          [B0, B + "Карта: " + H + "любая или случайная",
           B + "Раунды: " + H + "1, 3 или 5",
           B + "Соперник: " + H + "получит твой вызов",
           B0, DOT + "Прими вызов, когда он придёт"]), 22),
    (item("step4", "paper", T("Шаг 4 — бой"),
          [B0, B + "Старт: " + H + "отсчёт 3 секунды",
           B + "Раунд: " + H + "выходит победитель боя",
           B + "Сдаться: " + H + "/duel forfeit",
           B0, DOT + "Реванш предлагается после боя"]), 23),
    (item("commands", "book", T("Команды"),
          [B0, B + "Меню дуэлей: " + H + "/duels",
           B + "Вызов: " + H + "/duel ник",
           B + "Очередь: " + H + "/duel queue кит",
           B + "Статистика: " + H + "/duel stats",
           B0, DOT + "Все команды: /duel help"]), 24),
    (item("important", "knowledge_book", T("Важно"),
          [B0, B + "Награды: &cнет", B + "Считается: " + H + "только статистика",
           B + "Инвентарь: " + H + "выдаётся на бой",
           B + "Твой инвентарь: " + H + "не трогается",
           B0, DOT + "Выход из боя — поражение"]), 31),
]
help_tail = [back(45), close(49)]
write("help.yml", build("duel_help", "Как играть", help_content, help_tail))

# групповых окон и наблюдения больше нет
for stale in ("party.yml", "party_size.yml"):
    path = os.path.join(OUT, stale)
    if os.path.exists(path):
        os.remove(path)

print("готово:", ", ".join(sorted(os.listdir(OUT))))
