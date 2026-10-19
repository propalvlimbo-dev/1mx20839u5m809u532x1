# Перенос меню DeluxeMenus → BMenu

Эта папка содержит BMenu-версии всех меню из `DeluxeMenus/gui_menus/`. Исходные файлы DeluxeMenus не изменялись и остаются в репозитории для сверки и отката.

## Что перенесено

- Перенесённых меню: **31** (все YAML из `gui_menus/`, включая файлы вне `DeluxeMenus/config.yml`) плюс **1** демонстрационный кейс с анимацией.
- В перенесённых меню: **283** уникальных предмета, **1071** размещение по слотам (мультислот-панели считаются по каждому слоту). В демо-кейсе ещё 13 предметов.
- Команды открытия, права, алиасы, приоритеты `oneOf`, `view_requirement`, `click_requirement` с `deny_commands`, `update`, флаги, зачарования, цвет зелий/стрел и головы `basehead-` сохранены.

## Таблица меню

| BMenu id | Файл | Исходник DeluxeMenus | Размер | Предметов |
|---|---|---|---|---|
| `elytrix:cases_wheel` | `cases/wheel.yml` | — (новый демо-кейс, не из DeluxeMenus) | 54 | 13 |
| `elytrix:customizemenu` | `customize/customizemenu.yml` | `DeluxeMenus/gui_menus/customize/customizemenu.yml` | 54 | 6 |
| `elytrix:titles` | `customize/titles.yml` | `DeluxeMenus/gui_menus/customize/titles.yml` | 54 | 15 |
| `elytrix:airdrop_event` | `events/airdrop_event.yml` | `DeluxeMenus/gui_menus/events/airdrop_event.yml` | 54 | 8 |
| `elytrix:events` | `events/events.yml` | `DeluxeMenus/gui_menus/events/events.yml` | 54 | 9 |
| `elytrix:merchant_event` | `events/merchant_event.yml` | `DeluxeMenus/gui_menus/events/merchant_event.yml` | 54 | 7 |
| `elytrix:paradise_event` | `events/paradise_event.yml` | `DeluxeMenus/gui_menus/events/paradise_event.yml` | 54 | 8 |
| `elytrix:shulkerpvp_event` | `events/shulkerpvp_event.yml` | `DeluxeMenus/gui_menus/events/shulkerpvp_event.yml` | 54 | 6 |
| `elytrix:talisman_event` | `events/talisman_event.yml` | `DeluxeMenus/gui_menus/events/talisman_event.yml` | 54 | 7 |
| `elytrix:help` | `help/help.yml` | `DeluxeMenus/gui_menus/help/help.yml` | 54 | 8 |
| `elytrix:help_basics` | `help/help_basics.yml` | `DeluxeMenus/gui_menus/help/help_basics.yml` | 54 | 10 |
| `elytrix:help_events` | `help/help_events.yml` | `DeluxeMenus/gui_menus/help/help_events.yml` | 9 | 9 |
| `elytrix:help_freedonate` | `help/help_freedonate.yml` | `DeluxeMenus/gui_menus/help/help_freedonate.yml` | 54 | 5 |
| `elytrix:lobby` | `lobby/lobby.yml` | `DeluxeMenus/gui_menus/lobby/lobby.yml` | 54 | 5 |
| `elytrix:don` | `main/don.yml` | `DeluxeMenus/gui_menus/main/don.yml` | 54 | 12 |
| `elytrix:menu` | `main/menu.yml` | `DeluxeMenus/gui_menus/main/menu.yml` | 54 | 12 |
| `elytrix:rtp` | `main/rtp.yml` | `DeluxeMenus/gui_menus/main/rtp.yml` | 54 | 7 |
| `elytrix:sub` | `main/sub.yml` | `DeluxeMenus/gui_menus/main/sub.yml` | 54 | 10 |
| `elytrix:salary_rub` | `salary/rub.yml` | `DeluxeMenus/gui_menus/salary/rub.yml` | 54 | 10 |
| `elytrix:armor` | `shop/armor.yml` | `DeluxeMenus/gui_menus/shop/armor.yml` | 54 | 13 |
| `elytrix:arrows` | `shop/arrows.yml` | `DeluxeMenus/gui_menus/shop/arrows.yml` | 54 | 9 |
| `elytrix:auras` | `shop/auras.yml` | `DeluxeMenus/gui_menus/shop/auras.yml` | 54 | 7 |
| `elytrix:eggs` | `shop/eggs.yml` | `DeluxeMenus/gui_menus/shop/eggs.yml` | 54 | 14 |
| `elytrix:enchants` | `shop/enchants.yml` | `DeluxeMenus/gui_menus/shop/enchants.yml` | 54 | 8 |
| `elytrix:frun` | `shop/frun.yml` | `DeluxeMenus/gui_menus/shop/frun.yml` | 54 | 9 |
| `elytrix:potions` | `shop/potions.yml` | `DeluxeMenus/gui_menus/shop/potions.yml` | 54 | 10 |
| `elytrix:shari` | `shop/shari.yml` | `DeluxeMenus/gui_menus/shop/shari.yml` | 54 | 9 |
| `elytrix:shop` | `shop/shop.yml` | `DeluxeMenus/gui_menus/shop/shop.yml` | 54 | 15 |
| `elytrix:spawners` | `shop/spawners.yml` | `DeluxeMenus/gui_menus/shop/spawners.yml` | 54 | 7 |
| `elytrix:totems` | `shop/totems.yml` | `DeluxeMenus/gui_menus/shop/totems.yml` | 54 | 9 |
| `elytrix:unical_items` | `shop/unical_items.yml` | `DeluxeMenus/gui_menus/shop/unical_items.yml` | 54 | 10 |
| `elytrix:zelevarki_fr_legacy` | `shop/zelevarki_fr_legacy.yml` | `DeluxeMenus/gui_menus/shop/zelevarki_fr.yml1` | 54 | 9 |

## Команды открытия (BMenu/config.yml)

| Команда | Алиасы | Меню | Право |
|---|---|---|---|
| `/help_freedonate` | фришка | `elytrix:help_freedonate` | — |
| `/help_basics` | основы | `elytrix:help_basics` | — |
| `/help` | помощь, гайд | `elytrix:help` | — |
| `/menu` | — | `elytrix:menu` | — |
| `/rtp` | rtps, ртп | `elytrix:rtp` | — |
| `/sub` | подписка | `elytrix:sub` | — |
| `/donate` | donat, don, d, донат | `elytrix:don` | — |
| `/shop` | магазин | `elytrix:shop` | — |
| `/arrows` | — | `elytrix:arrows` | — |
| `/customize` | custom, title, titles, titul, титулы, титул, кастомизация, ешеду, ешедуы, сгыещьшяу | `elytrix:customizemenu` | — |
| `/talisman_event` | event_talisman, talisman_menu | `elytrix:talisman_event` | — |
| `/merchant_event` | event_merchant, merchant_menu | `elytrix:merchant_event` | — |
| `/shulkerpvp_event` | event_shulkerpvp, shulkerpvp_menu | `elytrix:shulkerpvp_event` | — |
| `/airdrop_event` | event_airdrop, airdrops_menu | `elytrix:airdrop_event` | — |
| `/paradise_event` | event_paradise, paradise_menu | `elytrix:paradise_event` | — |
| `/events` | ивенты, event, таймеры | `elytrix:events` | — |
| `/help_events` | — | `elytrix:help_events` | deluxemenus.admin |
| `/elytrix_lobby` | — | `elytrix:lobby` | — |
| `/rub` | rubsalary | `elytrix:salary_rub` | — |
| `/elytrix_frun_legacy` | — | `elytrix:zelevarki_fr_legacy` | — |
| `/elytrix_wheel` | wheel, case | `elytrix:cases_wheel` | — |

`/wheel` (алиасы `/case`) открывает демонстрационный кейс с анимацией. Остальные меню открываются своими командами; внутренние разделы магазина, ивентов и настроек доступны по клику.

## Анимация колеса (стиль BCases)

Файлы:
- `animation/wheel.yml` — 8 наборов анимации `wheel-0`…`wheel-7`;
- `cases/wheel.yml` — сам кейс `elytrix:cases_wheel` с призами и кнопкой «Крутить».

В BCases (`wheel.yml`) призы вращаются вокруг кейса виртуальными стойками с параметрами `prize_count`, `max_speed`, `speed_fade`, `min_speed`. В GUI BMenu те же принципы реализованы кадрами:
- `prize_count: 8` → кольцо слотов `11,13,15,25,33,31,29,19`;
- `max_speed` → 1 тик на шаг в начале;
- `speed_fade 0.07` → интервалы между кадрами растут `1,1,1,1,1,1,2,2,2,3,3,4,4,5,5,6…13`;
- `min_speed` → 13 тиков на последний шаг;
- призы не дублируются: каждый кадр копирует их из базового слоя (`copy-from-base`), поэтому кольцо реально вращается;
- 8 вариантов анимации отличаются только стартовым сдвигом и дают 8 разных победителей; `[run_rand]` выбирает вариант случайно;
- победитель — приз, оказавшийся в слоте `11` под указателем.

Чтобы изменить призы, отредактируйте `cases/wheel.yml`: слоты кольца и порядок должны совпадать с `ring` в `animation/wheel.yml`. Число шагов и интервалы задаются кадрами `tick:`; звуки — опкодами `sound:`.

## Отличия от DeluxeMenus

1. **Размер 53 → 54.** DeluxeMenus открывает 53 слота как шесть рядов; в Bukkit размер инвентаря кратен 9. Все меню с `size: 53` стали `54`, слот `53` сохранён.
2. **`hide_effects` → `hide_additional_tooltip`.** В 1.21.11 флаг `HIDE_POTION_EFFECTS` удалён. `HIDE_ADDITIONAL_TOOLTIP` помечен deprecated и может не скрывать эффекты полностью: для этого нужен компонент `TooltipDisplay`, которого нет в модели предметов BMenu.
3. **`[openguimenu] help_earnings`.** В `help/help.yml` есть кнопка на меню `help_earnings`, но самого файла в репозитории нет. Кнопка заменена сообщением о недоступности раздела; после добавления меню замените команду на `[open] elytrix:help_earnings`.
4. **Файлы вне `DeluxeMenus/config.yml`.** `lobby/lobby.yml` и `salary/rub.yml` не были зарегистрированы в DeluxeMenus, поэтому получили команды `/elytrix_lobby` и `/rub` (алиас `/rubsalary`). `shop/zelevarki_fr.yml1` — старая копия `frun.yml`; перенесена как `elytrix:zelevarki_fr_legacy` и `/elytrix_frun_legacy`, чтобы не дублировать активное меню. У `lobby` сохранён `[connect] lobby` (BungeeCord).
5. **NBT → компоненты.** `nbt_int: CustomPotionColor:<int>` у стрел заменён на `color: #RRGGBB` (тот же цвет зелья/стрелы). Значение `98145125` больше 24 бит; Bukkit использует младшие 24 бита, поэтому взят эффективный цвет.
6. **Звуки.** Легаси-имена (`ENTITY_PLAYER_LEVELUP`, `BLOCK_NOTE_BLOCK_BELL`, `ENTITY_VILLAGER_NO`, `BLOCK_CHEST_CLOSE`) заменены на современные ключи (`entity.player.levelup` и т. д.).
7. **Обновление плейсхолдеров.** `update_interval` в DeluxeMenus задаётся в секундах (плагин умножает на 20). Предметы с `%…%` получили `tick_speed` (20 c → 400 тиков, 10 c → 200 тиков) и `on_tick: '[update]'`. В отличие от DeluxeMenus, BMenu при `[update]` пересобирает предмет целиком, поэтому `on_view` тоже пересчитывается.
8. **`open_requirement`.** У `help_events` проверка права перенесена в `menu-events.on_open` и в `permission` команды. BMenu не отменяет открытие из `on_open`, поэтому при внутреннем открытии меню игрок увидит сообщение об отказе, но инвентарь откроется; команда `/help_events` закрыта правом `deluxemenus.admin`.
9. **Кириллические алиасы.** Сохранены как есть (`/помощь`, `/магазин`, `/фришка` и др.). Если ваша сборка BMenu/bcmd откажется регистрировать не-ASCII команды, удалите их из `BMenu/config.yml` — остальные алиасы продолжат работать.
10. **Конфликты команд.** `/help`, `/menu`, `/shop`, `/donate`, `/d` и другие короткие имена могут быть заняты ванилью или сторонними плагинами. Если команда не открывает меню, проверьте лог BMenu и отключите DeluxeMenus: два плагина не должны регистрировать одно и то же имя.

## Как включить

1. Сделайте резервную копию сервера и `plugins/BMenu`, `plugins/DeluxeMenus`.
2. Установите BMenu 2.7 для Paper/ShieldSpigot 1.21.11.
3. Скопируйте содержимое папки `BMenu/` репозитория в `plugins/BMenu/` (сохранив пути `menu/elytrix/...`).
4. Не удаляйте DeluxeMenus сразу: отключите его или оставьте на время проверки, чтобы можно было сравнить поведение и откатиться.
5. Перезапустите сервер, проверьте `/menu`, `/shop`, `/events`, `/rtp`, `/donate`, `/rub`, `/wheel`.
6. После проверки отключите DeluxeMenus, чтобы его команды не конфликтовали с BMenu.

## Что проверено

- Все YAML BMenu-папки разбираются, id уникальны, слоты в пределах размера, цели `[open]` и `set_animation` существуют.
- Сверка с исходниками: 1071 размещение предметов, названия, лор, материал, количество, флаги, зачарования, цвет, команды и условия совпадают; единственное намеренное расхождение — отсутствующий `help_earnings`.
- Анимация проверена симуляцией: 24 кадра, монотонные `tick`, 8 разных победителей, последний кадр — приз под указателем.
- На живом сервере не запускалось: фактическая загрузка BMenu 2.7 и поведение плагинов на 1.21.11 требуют проверки в игре.
