# EpicDuels 3.1 — установка под ElytriX

Плагин: **EpicDuels 3.1.0-elytrix** (форк, `EpicDuels-Fork/`), Paper
1.21.1–1.21.11, Java 21. Клиенты 1.21.4 заходят через ViaBackwards, плагин
от этого не зависит.

В форке меню, скорборд и все сообщения уже внутри jar — отдельно заливать
конфиги не нужно (папка ниже нужна только для правок после установки).
Исходники: `EpicDuels-Fork/`, архив `downloads/EpicDuels-Fork-src.zip`.

## Что в папке

| Файл | Куда на сервере |
|---|---|
| `config.yml` | `plugins/EpicDuels/config.yml` |
| `menus.yml` | `plugins/EpicDuels/menus.yml` |
| `scoreboard.yml` | `plugins/EpicDuels/scoreboard.yml` |
| `lang/messages_ru.yml` | `plugins/EpicDuels/lang/messages_ru.yml` |
| `КИТЫ.md` | инструкция: 10 китов |
| `АРЕНЫ.md` | инструкция: перенос арен из мира `duel` |
| `downloads/EpicDuels-ElytriX-3.1.0.jar` | `plugins/EpicDuels-ElytriX-3.1.0.jar` |

## Установка

1. Залить jar в `plugins/`, запустить сервер один раз — создадутся папки.
2. Остановить сервер.
3. Заменить `config.yml`, `menus.yml`, `scoreboard.yml` и положить
   `lang/messages_ru.yml`.
4. Запустить сервер.
5. В игре, на спавне: `/duel setlobby` — иначе после дуэли игроков
   будет телепортировать в `0 100 0` мира `spawn`.
6. Создать арены — см. `АРЕНЫ.md`.
7. Создать киты — см. `КИТЫ.md`.

## Что настроено не по умолчанию

Это важно: у плагина «лобби» = **любой** мир, кроме арен. На выживании
значения по умолчанию ломают игру, поэтому в конфиге:

| Параметр | Значение | Почему |
|---|---|---|
| `lobby.handle-join` | `false` | иначе при входе чистится инвентарь и игрока кидает в лобби |
| `lobby.disable-pvp` | `false` | иначе PvP выключается на всём сервере |
| `protections.*` | всё `false` | иначе нельзя ломать, ставить, подбирать и есть в обычных мирах |
| `language` | `ru` | русские сообщения |
| `duel.countdown-seconds` | `3` | быстрый старт |
| `duel.broadcast-results` | `true` | результат дуэли видят все |
| `features.own-inventory-duels` | `false` | игроки играют только готовыми китами |
| `features.kit-editor` | `false` | обычные игроки не собирают киты |
| `duel.time-limit-seconds` | `300` | 5 минут на бой |

Награды выключены:

```
result-commands:
  winner: []
  loser: []
```

За победу не выдаётся ничего — дуэли только для статистики и рейтинга.
Если позже решишь платить, вписать команды сюда (`points give {player} N`),
`/duel reload`.

Ставок в EpicDuels нет: в плагине нет ни ставок, ни событий/API, через
которые их можно было бы считать. Подробно — `СТАВКИ.md`.

## Оформление

`menus.yml`, `scoreboard.yml` и `lang/messages_ru.yml` переписаны под стиль
ElytriX:

- префикс `☁ ᴇʟʏᴛʀɪx »`, акцент `#F8BEFB`, разделители `gray` / `dark_gray`;
- лор: `┃` — данные, `●` — действие;
- успех `green`, ошибка `red`;
- заголовки меню `#F8BEFB`, жирные;
- навигация: назад/далее — `black_dye`, закрыть — `red_dye`, стрелок нет;
- сообщения без длинных фраз, без скобок и рамок.

Пункты меню, которые генерирует сам плагин (числа в статистике, размер
группы), остаются от плагина — их нельзя заменить шаблоном.

## Права

| Право | Кому |
|---|---|
| `epicduels.duel` | всем |
| `epicduels.stats` | всем |
| `epicduels.party` | всем |
| `epicduels.spectate` | всем |
| `epicduels.admin` | только админам |

По умолчанию права уже стоят правильно, в LuckPerms ничего выдавать не нужно.

## Команды

```
/duel                  главное меню
/duel challenge <ник>  вызвать игрока
/duel queue <кит>      встать в очередь
/duel spectate         смотреть живые дуэли
/duel stats [ник]      статистика
/duel kits             своя раскладка китов
/duel forfeit          сдаться
/duel rematch          реванш
/duel leaderboard wins|score
/duel arena ...        арены (админ)
/duel kit ...          киты (админ)
/duel setlobby         точка лобби (админ)
/duel reload           перезагрузить конфиг (админ)
/party ...             группа, 2v2–4v4 и турниры
```

Псевдонимы: `/d`, `/p`.

## PlaceholderAPI

```
%epicduels_wins%       побед
%epicduels_losses%     поражений
%epicduels_winrate%    процент побед
%epicduels_score%      очки (wins² / (wins+losses))
%epicduels_in_duel%    true, если игрок в бою
```

В TAB можно вывести, например, победы в сайдбаре:

```
Дуэли: %epicduels_wins% побед | %epicduels_winrate%%
```

## Интеграция с остальным сервером

- **BMenu:** меню дуэлей в стиле ElytriX — `BMenu/menu/elytrix/duel/`,
  открывается командой `/duels`. См. `BMenu-ДУЭЛИ.md`.
- **Голограммы:** `/duel leaderboard sethologram wins` — обычная голограмма
  плагина; или вывести топ в DH через PAPI.
- **Кейсы:** если включишь награды, ключ можно выдать из `result-commands`
  консольной командой BCases.

## Если что-то не работает

| Симптом | Причина |
|---|---|
| игрока кидает в `0 100 0` | не выполнен `/duel setlobby` |
| после входа пустой инвентарь | `lobby.handle-join: true` |
| не работает PvP в мире | `lobby.disable-pvp: true` |
| нельзя ломать блоки | включена одна из `protections.*` |
| «No arenas available» | нет ни одной арены со статусом ready |
| бой не начинается | у арены не заданы обе точки спавна |

## Обновление плагина

Новые версии: https://modrinth.com/plugin/epicduels или
https://github.com/nuroso000/EpicDuels/releases
Конфиги совместимы, но перед заменой jar лучше сохранить `config.yml`.
