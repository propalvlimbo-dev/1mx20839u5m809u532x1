# EpicDuels — ElytriX build

Форк [nuroso000/EpicDuels](https://github.com/nuroso000/EpicDuels) версии **3.1.0**
с полностью заменёнными меню, скорбордом и текстами под стиль ElytriX.

- Исходный плагин: https://github.com/nuroso000/EpicDuels (тег `v3.1.0`)
- Лицензия: **CC BY-NC-SA 4.0**, © 2026 Nuri Y. / nuroso000 — см. `LICENSE.txt` и `NOTICE.md`
- Java 21, Paper 1.21.1–1.21.11

## Что заменено

| Файл | Было | Стало |
|---|---|---|
| `src/main/resources/config.yml` | `language: en`, награды коинами, лобби-защита | `language: ru`, награды выключены, `handle-join: false`, `disable-pvp: false`, все `protections: false`, киты только готовые |
| `src/main/resources/menus.yml` | английские заголовки | 18 меню: заголовки `#F8BEFB`, лор `┃`/`●`, навигация `black_dye`/`red_dye`, 303 кнопки |
| `src/main/resources/scoreboard.yml` | `<gold>EpicDuels</gold>` | `#F8BEFB`, `┃` префиксы, 4 вида (лобби/дуэль/зритель/очередь) |
| `src/main/resources/lang/messages_ru.yml` | не было | 288 строк, MiniMessage, префикс `☁ ᴇʟʏᴛʀɪx »` |
| `src/main/resources/plugin.yml` | `version: 3.1.0` | `version: 3.1.0-elytrix` |
| `pom.xml` | `3.1.0`, `finalName: EpicDuels` | `3.1.0-elytrix`, `EpicDuels-ElytriX-3.1.0` |

Java-код **не изменён** — это чистая ресурсная сборка. Поэтому она собирается
ровно так же, как оригинал, и не имеет поведенческих отличий.

## Что осталось от плагина

Часть текста EpicDuels рисует в Java, а не в YAML. Через `menus.yml` её можно
переименовать по слоту (название, лор, материал), но нельзя подставить числа:

- статистика — числа побед и поражений остаются от плагина (слоты 10 и 12);
- список наблюдения — длительность боя и раунд (заменено на «Нажмите, чтобы смотреть»);
- размер группы 2v2/3v3/4v4 — сколько игроков не хватает;
- цвет рамок в списках (оранжевая, бирюзовая, салатовая) — задан в коде.

Полный список — `NOTICE.md`.

## Сборка

Нужен JDK 21 и Maven:

```
mvn -B clean package
```

Готовый jar: `target/EpicDuels-ElytriX-3.1.0.jar`.
Тесты плагина: `mvn -B test`.

Сборка без интернета не пройдёт: `pom.xml` тянет `paper-api` с
`repo.papermc.io`.

## Установка

1. `target/EpicDuels-ElytriX-3.1.0.jar` → `plugins/`
2. Запустить сервер один раз — создастся `plugins/EpicDuels/` с нашими конфигами.
3. `/duel setlobby` на спавне.
4. Арены и киты — по инструкциям в репозитории сервера
   (`EpicDuels/АРЕНЫ.md`, `EpicDuels/КИТЫ.md`).

Если плагин уже стоял — старые `config.yml`, `menus.yml`, `scoreboard.yml`
не перезапишутся: их надо заменить вручную или удалить папку `plugins/EpicDuels/`
перед первым запуском.

## Обновление с upstream

```
git remote add upstream https://github.com/nuroso000/EpicDuels.git
git fetch upstream --tags
git merge upstream/main   # конфликты будут только в ресурсах
```

При мерже ресурсы upstream перезапишут наши — держать ElytriX-версии в
отдельной ветке/папке проще, чем мержить построчно.
