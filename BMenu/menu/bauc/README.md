# Аукцион BAuctionV2 (bauc) в стиле ElytriX

Папка `BMenu/menu/bauc/` — меню аукциона BAuctionV2. Они подключаются самим плагином через `provider: bauc:*` (команда `/ah`), а не через `open_commands` в `BMenu/config.yml`. Исходная логика, id, провайдеры, слоты лотов и плейсхолдеры **не менялись** — только оформление, анимация и звуки.

## Что изменено

### 1. Палитра

Все меню перекрашены из красно-оранжевой схемы в палитру ElytriX. Формат тегов оставлен прежним (MiniMessage `<#RRGGBB>`), чтобы ничего не сломать.

| Было (bauc) | Стало (elytrix) | Где |
|---|---|---|
| `<#FF0000>` | `<#F8BEFB>` | скобки `[☃] [⟲] [❖]`, акценты, разделители |
| `<#FF6600>` | `<#F8BEFB>` | названия и текст кнопок |
| `<#FFFFE6>` | `<#FFFFFF>` | основной текст лора |
| `<#FFFF00>` | `<#F8BEFB>` | выделения (продавец, срок) |
| `<#00FF00>` | `<#A8FFC0>` | цена |
| `<#009900>` | `<#7CFFA8>` | баланс |
| `<#00D8FF>` | `<#D9B8FF>` | вторичный акцент (сортировка) |
| `<#FF0066>` | `<#FF9AD5>` | «истекает» |
| `<#FF2222>` / `<#FF1500>` | `<#FF9AA2>` | ошибки |
| `<gold>`, `<yellow>`, `<green>`, `<red>` | `<#F8BEFB>`, `<#A8FFC0>`, `<#FF9AA2>` | разметка MiniMessage |

Брендинг панелей `YourServer` заменён на `ElytriX` в фиолетовых градиентах (`<gradient:#F8BEFB:#D9B8FF>…`).

### 2. Анимация

| Меню | Что происходит |
|---|---|
| `home.yml` (аукцион) | нижний ряд 45–53 закрывается чёрной «шторкой», появляется фиолетовая панель в слоте 47, затем кнопки открываются по одной: Хранилище → Обновить → Пред/След. страница → Категории (2 шт.) → Аренда → Сортировка. 10 кадров, ~0.9 с. |
| `vault.yml` (хранилище) | то же для ряда 45–53: панели 45/47/51, затем по одной открываются Назад → Обновить → Пред/След. → Перевыставить → Аренда. 8 кадров, ~0.7 с. |
| `select_category.yml` | иконки категорий прикрыты панелями и открываются тремя рядами: `11-15` → `20-24` → `29-33`. 4 кадра, ~0.45 с. |
| `rent/rent.yml`, `rent/select-currency.yml` | сохранена **родная анимация bauc** (шторка из панелей `b`/`c`), но панели теперь фиолетовая + чёрная вместо красной + оранжевой, а звуки — мягкие. |

Используется приём bauc: предметы остаются в базовом слое (со своими `slot:`), а анимация рисует поверх них панели и потом убирает их опкодом `remove`. Поэтому лоты и кнопки всегда актуальны: `refresh()`/`research()` пересобирают базовый слой, а не копии в слое анимации.

Добавлены два служебных предмета без `slot:` — `frame` (чёрная панель) и `accent` (фиолетовая). Они не ставятся статически, только кадрами `set: frame …` / `set: accent …`.

### 3. Звуки

- `block.glass.place 1 1` в анимациях аренды заменён на мягкую последовательность (как в elytrix-меню): `block.enchantment_table.use` → `block.note_block.chime` → `block.note_block.pling` (тон плавно растёт) → `block.amethyst_block.chime` в финале.
- Все клики и служебные звуки с громкостью `1 1` приглушены до `0.35 1`: `ui.button.click`, `entity.ender_eye.launch`, `entity.enderman.teleport`, `block.note_block.basedrum`, `entity.creeper.hurt` и т. д.
- Все ключи проверены по `sounds.json` Minecraft **1.21.4** (они же есть в 1.21.11).

## Что НЕ менялось

- `id`, `provider`, `include`, `slots: 0-44`, `vault_type`, `always_show_vault_lot`, `category_line`, `sorting_line`.
- Все плагинные опкоды: `next_category`, `previous_category`, `next_sorting`, `previous_sorting`, `next_page`, `previous_page`, `research`, `resell`, `take_lot`, `buy_lot`, `find_analogs`, `do_sell`, `accept`, `add`, `sub`.
- Плейсхолдеры `{price}`, `{seller_name}`, `{expires}`, `{current_page}`, `{max_page}`, `{has_next_page}`, `%bauctionv2_*%`, `%playerpoints_points%`, `%vault_eco_balance_commas%`.
- Слоты лотов `0–44` и весь `lots_items.yml` по структуре (только цвета в тексте).
- YAML-якоря в `rent/rent.yml` и `rent/select-currency.yml` (файлы правлены текстово, чтобы не сломать `&slot`, `&base_lore`, `<<+: *args`).

## Как откатить

```bash
git revert <commit>   # или
git checkout main -- BMenu/menu/bauc
```

## Как подстроить

- Цвет — теги `<#RRGGBB>` прямо в строках `name`/`lore`.
- Скорость анимации — `tick:` у кадров (1 тик = 50 мс).
- Звук — строки `sound: 'ключ громкость питч'`; удалить строку = убрать звук.
- Панели: `frame` (чёрная) и `accent` (фиолетовая) — меняйте `material` у этих предметов.

## Если `/ah` пишет `Menu not found: bauc:home`

Почти всегда в логе рядом есть настоящая причина, например:

```
[BMenu] Errors in plugins\BMenu\menu\bauc\home.yml
unknown provider bauc:home
```

Это значит: BMenu прочитал файл, но **не знает провайдера** `bauc:home`, поэтому меню не попало в реестр. `/ah` затем падает на `MenuLoader.create()` с `Menu not found` — это следствие, а не причина. Файлы и оформление тут ни при чём.

### Почему провайдер «неизвестен»

- `bauc:*` регистрирует сам BAuctionV2: `MenuBooter.boot(...)` + `BMenu.menuLoader().registerSubLoader(this, subLoader)` — в `onLoad` (`BAuction.java`, строки 108–110).
- BMenu читает папку `menu` в своём `onEnable` (`loader.enable()`), то есть уже после `onLoad` всех плагинов — на нормальном старте порядок безопасен (`BAuctionV2` зависит от `BMenu`).
- В `onDisable` BAuctionV2 делает `BMenu.menuLoader().unregisterSubLoader(this)` (строка 220) — и обратно регистрирует только в `onLoad`.

Отсюда: если BMenu перечитал меню на загрузчике, где саблоадер BAuctionV2 не зарегистрирован, все `bauc:*` становятся «unknown provider», а `/ah` при этом продолжает работать. Типичные случаи: `/reload`, перезагрузка одного плагина через PlugMan, два jar'а BMenu/BAuctionV2 в `plugins`, либо `/aha reload`, прерванный ошибкой.

### Что делать

1. Полный **stop → start** сервера (не `/reload` и не `/bmenu reload`).
2. Убедиться, что в логе старта нет `unknown provider bauc:`.
3. `/bmenu reload` → должно быть `Reloaded N menus!` без ошибок по `bauc`.
4. `/ah`.

Если после полного перезапуска ошибки остались — присылать лог **старта** (строки про `BAuctionV2`, `BMenu`, `MenuBooter`) и список jar'ов в `plugins`: провайдеры не регистрируются вовсе.

### Чего делать нельзя

Не заменять в файлах `provider: bauc:*` на `bmenu:default`. Меню рисует BAuctionV2 — без её провайдера не будет ни лотов, ни плейсхолдеров, ни кнопок покупки.

### Быстрые проверки

- Файл на месте? `plugins/BMenu/menu/bauc/home.yml` (рядом обязателен `lots_items.yml` — он подключается через `include`). Скрытые расширения на Windows (`home.yml.txt`) BMenu не читает.
- `/bmenu open bauc:home` — открылось, значит реестр в порядке и дело в `ah_menu` в `plugins/BAuctionV2/config.yml`; не открылось — меню не загрузилось.
- `Errors in ...` в логе — ошибка разбора YAML; `Failed to load menu config File:` — исключение при загрузке (со стектрейсом).

Важно: оформление и анимация не меняют `id`/`provider` — структура файлов сверена с оригиналом BAuctionV2 1.3 (отличаются только цвета, звуки и добавленный блок `animation:`).
