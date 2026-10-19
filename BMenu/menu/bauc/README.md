# Аукцион BAuctionV2 в стиле ElytriX

Две папки:

- `BMenu/menu/bauc/` — сами меню (BMenu, `provider: bauc:*`);
- `BAuctionV2/` — конфиги плагина: `messages.yml`, `lang.yml`, `config.yml`.

Исходная логика, `id`, провайдеры и плейсхолдеры не менялись. Переписана только визуальная часть: цвета, лоры, названия, кнопки навигации, сообщения.

## Стиль

### Палитра

| Роль | Тег |
|---|---|
| Основной | `<#F8BEFB>` |
| Акцент, текст | `<white>` |
| Второстепенный | `<gray>` / `<dark_gray>` |
| Успех | `<green>` |
| Ошибка, ограничение | `<red>` |

Правила, по которым переписаны все меню:

1. Каждая строка лора начинается с розового жирного разделителя: `'<#F8BEFB><b>┃</b> <white>Текст: <#F8BEFB>{значение}'`.
2. Пустая строка-разделитель — `'<#F8BEFB><b>┃</b> '`.
3. Действия и списки — серым маркером внизу: `'<gray>● <white>Нажмите, чтобы купить'`.
4. Никаких рамок, уголков и декоративных полос из символов.
5. Цена и значения — розовые, подписи — белые. Зелёный только для «успешно/активно», красный — для ошибок и лимитов.
6. «Points/ТК» → **коины**, «Монеты» — валюта Vault (`$`).
7. Никаких комментариев в меню и сообщениях.

### Навигация

| Кнопка | Материал |
|---|---|
| Закрыть / Назад / Отмена | `red_dye` |
| Следующая страница | `black_dye` |
| Предыдущая страница | `black_dye` |
| Подтвердить / Купить | `lime_dye` |
| Недоступный слот | `gray_dye` |

Стрелы (`spectral_arrow`, `tipped_arrow`) и саженцы больше не используются нигде.

## Что изменено в меню

| Файл | Что сделано |
|---|---|
| `home.yml` | Сетка 7×4 (28 лотов) в рамке, кнопки 45–53, `black_dye` на страницах, лоры в стиле `┃`/`●` |
| `vault.yml` | То же для хранилища, `red_dye` на «Назад» |
| `select_category.yml` | 15 категорий без `[❖]`, добавлена кнопка «Назад» (`red_dye`, слот 40) |
| `confirm.yml` | Покупка `lime_dye` (11), отмена `red_dye` (15), предмет в центре (13), чёрные панели вокруг |
| `select_count.yml` | `−1 / −10 / +1 / +10` на дye, лор с ценой за штуку |
| `container_view.yml` | Просмотр содержимого: купить / назад, чёрные панели |
| `sell_gui.yml` | Подтвердить `lime_dye` (5), отменить `red_dye` (3) |
| `lots_items.yml` | Шаблоны лотов переписаны под `┃`/`●`, статусы и подсказки |
| `rent/rent.yml` | Плоский файл без якорей: 5 тарифов (1/3/5/10/20), назад `red_dye` (40) |
| `rent/select-currency.yml` | Выбор валюты: монеты (21) / коины (23), назад `red_dye` (40) |
| `rent/confirm_eco.yml` | Подтверждение аренды: купить (20) / отмена (24), предмет в центре (22) |

`rent/rent.yml` и `rent/select-currency.yml` раньше использовали повторно объявленные якоря (`&args`, `&base_lore`) — строгие YAML-парсеры на них падали. Теперь оба файла плоские, без повторов, и проверяются обычным YAML.

## Анимация

### Слои меню

BMenu рисует меню слоями, снизу вверх:

| Слой | Кто пишет | Что лежит |
|---|---|---|
| 0 | BMenu | `items:` с `slot:` — кнопки, рамка |
| 1 | BMenu | штатный слой `animation:` (`set:` / `remove:`) |
| 2 | BAuctionV2 | лоты (`lotsLayer`) |
| 3 | BAuctionV2 (временами) / мы | верхний слой |

Из-за этого обычный `set:` из `animation:` **не виден на товарах** — лоты лежат выше. Поэтому в `home.yml` и `vault.yml` шторка пишется командой `[layer] 3 set …` прямо в верхний слой, а раскрывается `[layer] 3 remove …`. Опкод `cmd:` принимает список, кадр может делать несколько действий.

Ещё одна особенность: при открытии меню BAuctionV2 пересобирает лоты и очищает слой 3 (`LotsMenu.generate()`), поэтому кадры `tick: 0` и `tick: 1` дублируют установку шторки — первый ставит до пересборки, второй возвращает после.

### Что происходит

| Меню | Анимация | Длительность |
|---|---|---|
| `home.yml` | шторка на всей сетке и панели кнопок → четыре ряда товаров открываются сверху вниз (`10-16` → `19-25` → `28-34` → `37-43`) → панель 45–53 вспыхивает фиолетовым и открывается | 0.55 с, 8 кадров |
| `vault.yml` | то же построчно по всем 45 слотам хранилища, затем панель кнопок | 0.65 с, 9 кадров |
| `select_category.yml` | три ряда категорий | 0.45 с |
| `rent/rent.yml` | тарифы от центра (22) к краям (20/24) | 0.4 с |
| `rent/select-currency.yml` | две валюты по очереди | 0.2 с |

Звуки: `block.enchantment_table.use` на шторке, затем `block.note_block.pling` с растущим тоном на каждом ряду и `block.amethyst_block.chime` в финале. Все ключи проверены по `sounds.json` 1.21.4.

## Что изменено в BAuctionV2

- `messages.yml` — все сообщения переписаны с системным префиксом:
  `'<white>☁ <#F8BEFB>ᴇ<#F6BEFB>ʟ<#F3BEFB>ʏ<#F1BFFB>ᴛ<#EEBFFB>ʀ<#ECBFFB>ɪ<#E9BFFB>x <gray>» …'`.
  Успех — `<green>`, ошибки — `<red>`, справка `/ah` — строками `┃`.
- `lang.yml` — заголовки меню `'<#F8BEFB>☁ <white>Аукцион'` / `'… <white>Блоки'`, статусы лотов, визуальные подписи.
- `config.yml` — только визуальные названия (`visual.*.name`); технические ключи, цены, слоты и права не тронуты.
- Комментарии удалены из `config.yml`, `messages.yml`, `lang.yml` и всех меню. В `categories.yml`, `database.yml`, `tags.yml` оставлены: это техническая документация форматов, а не сообщения.

## Что НЕ менялось

- `id`, `provider`, `include`, `vault_type`, `always_show_vault_lot`.
- Плагинные опкоды: `next_category`, `previous_category`, `next_sorting`, `previous_sorting`, `next_page`, `previous_page`, `research`, `resell`, `take_lot`, `buy_lot`, `find_analogs`, `do_sell`, `accept`, `add`, `sub`, `back`, `back_or_close`.
- Плейсхолдеры `{price}`, `{seller_name}`, `{expires}`, `{current_page}`, `{max_page}`, `{has_next_page}`, `%bauctionv2_*%`, `%playerpoints_points%`, `%vault_eco_balance_commas%`.
- Логика аренды: те же цены, пермишены `auc.boost.rent*`, лимиты 6/12 ч, условие `squid_only`, команды снятия денег.

## Как откатить

```bash
git revert <commit>
```

## Как подстроить

- Цвет — теги `<#RRGGBB>` прямо в строках `name`/`lore`.
- Скорость анимации — `tick:` у кадров (1 тик = 50 мс).
- Звук — строки `sound: 'ключ громкость питч'`.
- Количество лотов: `slots: 10-16,19-25,28-34,37-43` (28). Больше — `slots: 9-44` (36) или `0-44` (45, тогда уберите `slot:` у `frame`/`accent`/`gap`).
- Панели `frame`/`gap` (чёрные), `accent` (фиолетовая) — меняйте `material`.

## Ошибка `include: ... sequence entries are not allowed here`

Если в логе появилось:

```
[BMenu] Errors in plugins\BMenu\menu\bauc\home.yml
в файле обнаружено include: я его прочитал но не получилось его проанализировать...
то что я прочитал 'include:  - './lots_items.yml'id: bauc:homeprovider: bauc:hometitle: ...'
Caused by: sequence entries are not allowed here
 in 'reader', line 1, column 11
```

это не порча файла. Так работает препроцессор BMenu 2.7 (`MenuFilePreprocessor`): увидев строку с `include:`, он ищет в ней `]` и, пока не найдёт, **склеивает следующие строки без переводов строки**. Если `]` нет в первой строке, в include попадает `id`, `provider`, `title` — и YAML падает.

Поэтому `include` в BMenu пишется **в одну строку**:

```yaml
include: [ './lots_items.yml' ]
id: bauc:home
provider: bauc:home
```

Так тоже работает (скобка открывается на строке `include`):

```yaml
include: [
  "./lots_items.yml"
]
```

А так — **не работает**, хотя это обычный YAML:

```yaml
include:
  - './lots_items.yml'
```

## Если `/ah` пишет `Menu not found: bauc:home`

Почти всегда в логе рядом есть настоящая причина, например:

```
[BMenu] Errors in plugins\BMenu\menu\bauc\home.yml
unknown provider bauc:home
```

Это значит: BMenu прочитал файл, но **не знает провайдера** `bauc:home`, поэтому меню не попало в реестр. `/ah` затем падает на `MenuLoader.create()` с `Menu not found` — это следствие, а не причина.

### Почему провайдер «неизвестен»

- `bauc:*` регистрирует сам BAuctionV2: `MenuBooter.boot(...)` + `BMenu.menuLoader().registerSubLoader(this, subLoader)` — в `onLoad` (`BAuction.java`, строки 108–110).
- BMenu читает папку `menu` в своём `onEnable`, то есть после `onLoad` всех плагинов — на нормальном старте порядок безопасен.
- В `onDisable` BAuctionV2 делает `unregisterSubLoader(this)` и обратно регистрирует только в `onLoad`.

Типичные причины: `/reload`, перезагрузка одного плагина через PlugMan, два jar'а BMenu/BAuctionV2 в `plugins`, `/aha reload`, прерванный ошибкой.

### Что делать

1. Полный **stop → start** сервера.
2. В логе старта не должно быть `unknown provider bauc:`.
3. `/bmenu reload` → `Reloaded N menus!`.
4. `/ah`.

### Быстрые проверки

- `plugins/BMenu/menu/bauc/home.yml` на месте, рядом `lots_items.yml` (он подключается через `include`).
- `/bmenu open bauc:home` — открылось, значит реестр в порядке.
- `Errors in ...` — ошибка разбора YAML; `Failed to load menu config File:` — исключение при загрузке.
