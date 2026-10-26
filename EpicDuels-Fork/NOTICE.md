# NOTICE

## Авторство и лицензия

Этот форк основан на **EpicDuels 3.1.0**
(https://github.com/nuroso000/EpicDuels), автор **Nuri Y. / nuroso000**.

Оригинал распространяется по лицензии
**Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International**
(CC BY-NC-SA 4.0). Полный текст — `LICENSE.txt`.

Что это значит для форка:

- авторство указано, ссылка на лицензию сохранена (`LICENSE.txt`);
- использовать можно свободно, в том числе на своём сервере;
- **продавать нельзя** — ни jar, ни доступ к серверу «за плагин»;
- если форк публикуется, он распространяется под той же лицензией
  CC BY-NC-SA 4.0, и наши изменения — тоже.

## Что изменено в форке

Изменены только ресурсы (`src/main/resources/`) и версия в `pom.xml`.
Java-код (`src/main/java/`) идентичен upstream `v3.1.0`.

```
config.yml      переписан под ElytriX (ru, без наград, лобби без защиты)
menus.yml       переписан полностью: 18 меню, 303 кнопки
scoreboard.yml  переписан: 4 вида, стиль ElytriX
lang/messages_ru.yml   новый файл, 288 строк
plugin.yml      version: 3.1.0-elytrix, описание
pom.xml         3.1.0-elytrix, finalName EpicDuels-ElytriX-3.1.0
README.md       переписан под форк
NOTICE.md       этот файл
```

## Что в плагине осталось непереведённым

Эти строки рисует Java-код, а не YAML:

| Где | Что | Почему не заменено |
|---|---|---|
| экран статистики | числа побед/поражений | числа подставляет код; в `menus.yml` переименованы только названия |
| экран статистики | «Total Duels», «Win Rate» на слотах 14 и 16 | заменены на русский лор без чисел |
| наблюдение | «Duration», раунд | заменено на «Нажмите, чтобы смотреть» |
| группа | «Requires N players» / «Your party: N» | число подставляет код |
| рамки списков | оранжевая/бирюзовая/салатовая панель | материал задан в `GUIManager` |
| `/duel kit edit`, `/duel kit preview` | подписи в редакторе | служебные, только для админа |

Если нужно добрать и это — правится `GUIManager.java`:

1. добавить перегрузку
   `createItem(Material, Component name, List<Component> lore)`
   рядом с текущей `createItem(Material, String, String...)` — `colorize()`
   понимает только legacy `&`-коды, а `Messages.raw()` отдаёт MiniMessage;
2. заменить литералы вида `createItem(Material.X, "&eText", ...)` на
   `createItem(Material.X, Messages.get("gui.<ключ>"), ...)`;
3. материалы рамок — константы в `fillBorder(...)` и `addNavigation(...)`
   (`ARROW` → `BLACK_DYE`, `BARRIER` → `RED_DYE`, панели → `PURPLE/BLACK_STAINED_GLASS_PANE`);
4. добавить ключи `gui.*` в `lang/messages_ru.yml` (и `en` как fallback);
5. `mvn -B clean package`.

## Отказ от ответственности

Плагин предоставляется «как есть». Автор upstream не связан с этим форком
и не обязан его поддерживать. Вопросы по ElytriX-версии — к нам, по логике
плагина — к upstream.
