# ElytrixDuelsBridge

Мост между **EpicDuels** и **BMenu**: окна плагина подменяются нашими меню
BMenu — с анимацией сборки, звуками и русским текстом.

- Версия: 1.3.0
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
/duelx pick                      своё окно выбора игрока
/duelx queue <кит|leave>         встать в очередь / выйти
/duelx top                       таблица лидеров
/duelx rounds <1|3|5>            раунды → вызов уходит сразу, карта случайная
/duelx target <ник>              соперник выбран → меню кита
/duelx team                      выбор напарника (быстрый 2 на 2)
/duelx kit <кит>                 кит из нашего меню → раунды или подтверждение
/duelx confirm | cancel          начать бой / встать в очередь / отмена
/duelx kits [reset]              создать 10 готовых наборов (reset — пересоздать)
/duelx open <menu>               открыть меню BMenu
```

`/duelx` вызывает публичные методы `GUIManager` и менеджеров EpicDuels — то
же, что делает клик в окне плагина. Выбор карты убран: после раундов
`finishChallengeWithArena` получает случайную готовую арену.

## Только две команды для игрока

Игроку не нужно знать подкоманд: `/duel` открывает меню, `/duel ник` — сразу
выбор кита. Мост подменяет обработчик `/duel` у EpicDuels:

- ник в таб-дополнении после `/duel ` (`/duel <Tab>` — список онлайн-игроков);
- подкоманды вроде `arena`, `kit`, `queue`, `stats` игроку недоступны;
- разрешены только те, на которые EpicDuels ставит кнопки в чате:
  `accept`, `deny`, `cancel`, `forfeit`, `rematch`, `toggle`;
- админы (`epicduels.admin` или `elytrix.guard.bypass`) видят полный `/duel`.

Кнопки меню работают через `/duelx`, поэтому фильтр им не мешает.

## Своё окно выбора игрока

Список игроков рисует мост: головы онлайн-игроков, страницы по 28,
занятые подписаны «в бою». Клик сразу открывает выбор кита.
`/duel ник` (и `/d ник`) — быстрый вызов без меню.

## 10 готовых наборов

Мост сам создаёт киты в EpicDuels (`plugins/EpicDuels/kits.yml`), если их там
ещё нет: `sword`, `uhc`, `gapple`, `sumo`, `archer`, `mace`, `axe`, `trident`,
`crossbow`, `netherite`. Существующие киты не перезаписываются, принудительно —
`/duelx kits reset`. Состав описан в `EpicDuels/КИТЫ.md`.

Наборы подобраны так, чтобы работать без установки блоков: кристаллов и
«обсидиановых» китов нет, потому что на аренах блоки ставить нельзя.

## Видимость команд (замена PLHide)

Раздел `command-guard` в `config.yml`:

```yaml
command-guard:
  enabled: true
  duel-menu: true              # /duel: игрокам только «/duel» и «/duel ник»
  hide-tab: true               # не отправлять скрытые команды клиенту
  admin-permission: epicduels.admin
  bypass-permission: elytrix.guard.bypass
  blocked: [plugins, pl, version, ver, about, icanhasbukkit, help]
  hidden:  [plugins, pl, version, ver, about, icanhasbukkit, help,
            bmenu, epicduels, duelx, dx, party, p]
```

- `hidden` — команды исчезают из автодополнения (`PlayerCommandSendEvent`);
- `blocked` — команду нельзя выполнить, в том числе `/bukkit:plugins`;
- OP и права выше видят всё;
- списки можно расширять: `/reload` не нужен, достаточно перезапуска.

Готовые альтернативы, если нужен отдельный плагин:
`CommandWhitelist` (YouHaveTrouble, обновляется в 2026),
`AdvancedPlHide`, `CommandDefender`.

## Защита арен

На боевых копиях арен (`arena_instance_*`) мост:

- запрещает ставить блоки и выливать жидкости из ведра;
- гасит взрывы — `blockList` очищается, поэтому ни один блок арены не ломается
  (урон игрокам от взрывов остаётся);
- блокирует поджог.

Шаблоны (`arena_template_*`) не трогаются — там администратор строит арену.
Отключить можно в конфиге: `protection.block-place` и `protection.explosions`.

## Меню

Семь окон лежат в `BMenu/menu/elytrix/duel/`: главное, киты, поиск соперника,
раунды, подтверждение, статистика, помощь. Оформление повторяет дефолтное
`/menu`: имя `&7« &#F8BEFBНазвание &7»`, lore `&#F8BEFB&l┃`, закрытие
`red_dye`, кадр из чёрных и фиолетовых панелей, сборка кадра со звуками.

Главное меню: `20` — дуэль 1 на 1, `21` — бои 2 на 2, `22` — подбор
соперника, `23` — статистика, `24` — рейтинг, `31` — как играть,
`4` — профиль с победами, `45` — возврат в `/menu`, `49` — закрыть.

Групповых окон (`duel_party`, `duel_party_size`) и кнопки «Наблюдение»
больше нет, наблюдение выключено в `EpicDuels/config.yml`
(`features.spectating: false`). Бои 2 на 2 идут через пару: выбрал
тиммейта — встали в очередь вдвоём.

## Статистика

Значения передаются в BMenu как `{wins}`, `{losses}`, `{winrate}`,
`{score}` — PlaceholderAPI и расширение EpicDuels не нужны.

## Какие экраны заменяются

| Окно EpicDuels | Меню BMenu | Почему можно |
|---|---|---|
| `MAIN` (`/duel`) | `elytrix:duel` | статичное, 4 кнопки |
| `STATS` | `elytrix:duel_stats` | цифры передаём сами, без PAPI |
| `MATCHMAKING` | `elytrix:duel_queue` |
| `KIT_SELECT` | `elytrix:duel_kit` |
| `PARTY_KIT` | `elytrix:duel_kit` |
| `PARTY_CONFIRM` | `elytrix:duel_confirm` | состав китов у нас фиксирован |
| `ROUNDS_SELECT` | `elytrix:duel_rounds` | 1 / 3 / 5 |

Остаются за плагином (содержимое зависит от сервера):

`DUELS` (список игроков), `ARENA_SELECT`,
`KIT_LIST`, `KIT_EDITOR_LIST`, `ARENA_LIST`, `KIT_EDIT`, `KIT_PREVIEW`,
`KIT_CUSTOMIZE`.

Эти экраны перекрашены через `menus.yml` EpicDuels (названия, лор, слоты,
заголовки), но их рисует сам плагин: в статичном YAML нельзя вывести список
онлайн-игроков или арен.

## Установка

1. `ElytrixDuelsBridge-1.3.0.jar` → `plugins/`
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

kits:
  auto-install: true     # создать 10 наборов, если их ещё нет

protection:
  block-place: true      # блоки и жидкости на аренах запрещены
  explosions: true       # взрывы не ломают арену
```

Поставить `keep` — окно вернётся к EpicDuels. Недостающие ключи мост
дописывает в файл при запуске сам, уже выставленные значения не трогает.
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
не попадают. В архиве 20 файлов: `plugin.yml`, `config.yml` и 18 классов.

Меню `elytrix/duel/*.yml` (7 штук) генерируются скриптом
`tools/gen_duel_menus.py`. Кадр, палитра, отступы и звуки повторяют
дефолтное `/menu` (`elytrix:menu`): чёрные панели сверху, фиолетовые по низу
и бокам, кнопки собираются по одной. Скрипт проверяет, что в анимации нет
ссылок на несуществующие предметы и нет двух предметов в одном слоте.

## Ограничения и риски

- Мост не тестировался на живом сервере: проверяй на копии.
- Если BMenu не найдёт меню, окно просто не откроется — в консоли будет
  ошибка BMenu; `config.yml` моста правится без пересборки.
- Отмена окна не вызывает `InventoryCloseEvent` EpicDuels, поэтому данные
  вызова (игрок/кит) живут до следующего вызова или до выхода с сервера.
- Плагин не патчит EpicDuels: при обновлении EpicDuels мост продолжит
  работать, пока не изменятся имена классов/методов `GUIManager`.
