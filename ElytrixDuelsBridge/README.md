# ElytrixDuelsBridge

Мост между **EpicDuels** и **BMenu**: окна плагина подменяются нашими меню
BMenu — с анимацией сборки, звуками и русским текстом.

- Версия: 1.0.0
- Зависимости: `EpicDuels`, `BMenu` (BMenu сам требует `BLib` и `PlaceholderAPI`)
- Paper 1.21.1–1.21.11, Java 17+ (собрано под 17, сервер 21 подходит)
- Java-классы EpicDuels не меняются: мост работает через события и рефлексию

## Как работает

1. EpicDuels собирается открыть своё окно (`InventoryOpenEvent`).
2. Мост видит, что владелец инвентаря — `dev.epicduels.gui.MenuHolder`,
   и по типу меню (`MAIN`, `STATS`, `MATCHMAKING`, …) берёт id меню BMenu
   из своего `config.yml`.
3. Окно плагина отменяется, через тик выполняется
   `/bmenu open <меню> <игрок>` — открывается наше меню с анимацией.
4. Кнопки в меню вызывают команды. Где нужен поток плагина — `/duelx`:

```
/duelx rounds <1|3|5>            выбрать раунды  → плагин открывает выбор карты
/duelx kit <кит>                 кит из нашего меню → раунды или подтверждение
/duelx party <team|tournament>   режим группы   → выбор размера / кита
/duelx size <2|3|4>              размер команд  → выбор кита
/duelx open <menu>               открыть меню BMenu
```

`/duelx` вызывает публичные методы `GUIManager` (setChallengeRounds,
openArenaSelect, openPartyTeamSizeMenu, openPartyKitMenu, …) — то же, что
делает клик в окне плагина.

## Какие экраны заменяются

| Окно EpicDuels | Меню BMenu | Почему можно |
|---|---|---|
| `MAIN` (`/duel`) | `elytrix:duel` | статичное, 4 кнопки |
| `STATS` | `elytrix:duel_stats` | цифры берём из PlaceholderAPI |
| `MATCHMAKING` | `elytrix:duel_queue` |
| `KIT_SELECT` | `elytrix:duel_kit` |
| `PARTY_KIT` | `elytrix:duel_kit` | состав китов у нас фиксирован |
| `ROUNDS_SELECT` | `elytrix:duel_rounds` | 1 / 3 / 5 |
| `PARTY_MODE` | `elytrix:duel_party` | 2 кнопки |
| `PARTY_TEAM_SIZE` | `elytrix:duel_party_size` | 2v2 / 3v3 / 4v4 |

Остаются за плагином (содержимое зависит от сервера):

`DUELS` (список игроков), `ARENA_SELECT`, `SPECTATE`, `PARTY_CONFIRM`,
`KIT_LIST`, `KIT_EDITOR_LIST`, `ARENA_LIST`, `KIT_EDIT`, `KIT_PREVIEW`,
`KIT_CUSTOMIZE`.

Эти экраны перекрашены через `menus.yml` EpicDuels (названия, лор, слоты,
заголовки), но их рисует сам плагин: в статичном YAML нельзя вывести список
онлайн-игроков или арен.

## Установка

1. `ElytrixDuelsBridge-1.0.0.jar` → `plugins/`
2. Папка меню BMenu `elytrix/duel/*.yml` → `plugins/BMenu/menu/elytrix/duel/`
3. Запустить сервер (или `/reload confirm` — не рекомендуется) и проверить `/duel`.

Порядок загрузки задан в `plugin.yml`: `depend: [EpicDuels, BMenu]`,
так что Bukkit сам дождётся обоих.

## Настройка

`plugins/ElytrixDuelsBridge/config.yml`:

```yaml
menus:
  MAIN: "elytrix:duel"
  STATS: "elytrix:duel_stats"
  MATCHMAKING: "elytrix:duel_queue"
  ROUNDS_SELECT: "elytrix:duel_rounds"
  PARTY_MODE: "elytrix:duel_party"
  PARTY_TEAM_SIZE: "elytrix:duel_party_size"
```

Убрать строку или поставить `keep` — окно вернётся к EpicDuels.
`/duelx` не требует прав.

## Сборка

Нужен JRE/JDK 17+ и компилятор ECJ. Скрипт `build.sh`:

```
bash build.sh /путь/к/jdk /путь/к/ecj.jar
```

Если в JRE нет утилиты `jar` (бывает в jlink-сборках), скрипт сам упакует
через `tools/mkjar.py`. Готовый jar кладётся в эту папку, потом копируется
в `downloads/`.

`stubs/` — минимальные заглушки Bukkit API только для компиляции; в jar они
не попадают (в архиве ровно 5 файлов: `plugin.yml`, `config.yml`, 3 класса).

Меню `elytrix/duel/*.yml` генерируются скриптом
`tools/gen_duel_menus.py` — он же проверяет, что заняты все 54 слота и что
в анимации нет ссылок на несуществующие предметы.

## Ограничения и риски

- Мост не тестировался на живом сервере: проверяй на копии.
- Если BMenu не найдёт меню, окно просто не откроется — в консоли будет
  ошибка BMenu; `config.yml` моста правится без пересборки.
- Отмена окна не вызывает `InventoryCloseEvent` EpicDuels, поэтому данные
  вызова (игрок/кит) живут до следующего вызова или до выхода с сервера.
- Плагин не патчит EpicDuels: при обновлении EpicDuels мост продолжит
  работать, пока не изменятся имена классов/методов `GUIManager`.
