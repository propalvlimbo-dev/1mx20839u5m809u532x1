# dHolograms — новый вид голограмм

Ставим **dHolograms** вместо DecentHolograms. Это отдельный плагин на тех же
display-сущностях, но с MiniMessage, анимациями и 3D-предметами в строках —
поэтому голограммы выглядят иначе, а не «дефолтно».

- Paper **1.21+**, Java **21+**, версия **1.1.4** (1.21.11 в списке).
- Скачать: <https://modrinth.com/plugin/dholograms> (или Spigot / BuiltByBit).
- Исходники: <https://github.com/DogeTennant/dHolograms>

## Установка

1. Останови сервер, удали `plugins/DecentHolograms/`.
2. Закинь `dHolograms-1.1.4.jar`, запусти сервер один раз (создастся `plugins/dHolograms/`).
3. Останови, замени `plugins/dHolograms/holograms.yml` на файл из этого архива.
4. Запусти. Проверка: `/dh list`, `/dh reload`, `/dh nearby`.

Координаты и текст — те же, что были: `spawn`, `addvk 82.5/53.5/-25.5`,
`cases 41.611/52.9/0.564`, `don 0.528/61.4/11.462`, `don1 0.392/61.4/-10.55`,
`hello 11.408/59.4/0.437`, `helps -10.444/60/0.535`.

## Что изменилось визуально

| Было (DecentHolograms) | Стало (dHolograms) |
|---|---|
| плоский цвет `&#F8BEFB&lЗАГОЛОВОК` | градиент `<gradient:#F8BEFB:#89A0FF>` + жирный |
| нет разделителей | анимированная линия `▬▬▬▬` (WAVE, переливается F8BEFB → 89A0FF) |
| подзаголовки одним цветом | мягкий градиент F8BEFB → C7D8FF |
| `#ICON: chest` — крутящаяся стойка | честный `ItemDisplay`: вращение 1.5°/тик + покачивание |
| плашка по умолчанию | прозрачная, `shadow: true`, `lineWidth: 400` |
| нет анимаций | 5 типов: `RAINBOW`, `WAVE`, `BURN`, `TYPEWRITER`, `SCROLL` |

## Команды

```
/dh list                     все голограммы
/dh nearby                   что рядом
/dh reload                   перечитать holograms.yml
/dh tp <имя>                 телепорт к ней
/dh movehere <имя>           поставить туда, где стоишь
/dh addline <имя> <текст>    добавить строку
/dh setline <имя> <номер> <текст>
/dh removeline <имя> <номер>
/dh del <имя>
```

Мини-примеры правок прямо в игре:

```
/dh setline hello 1 <gradient:#F8BEFB:#89A0FF><bold>ИНФОРМАЦИЯ</bold></gradient>
/dh addline hello <#3A3F52>▬▬▬▬▬▬▬▬▬▬</#3A3F52>
```

## Строки в holograms.yml

```yaml
holograms:
  hello:
    world: spawn
    x: 11.408
    y: 59.4
    z: 0.437
    refresh-ticks: 20
    display-settings: billboard=CENTER|alignment=CENTER|scaleX=1.0|scaleY=1.0|scaleZ=1.0|backgroundColor=0|shadow=true|range=48.0|updateRange=-1.0|lineHeight=0.3|lineWidth=400|facing=0.0|downOrigin=false|unifiedBackground=true
    pages:
      - - type: TEXT
          content: <gradient:#F8BEFB:#89A0FF><bold>ИНФОРМАЦИЯ</bold></gradient>
          height-offset: 0.0
        - type: ITEM
          content: BOOK
          height-offset: 0.0
          rotation-speed: 1.5
          bob-amplitude: 0.12
```

* `type: TEXT` — текст (MiniMessage + любые PAPI-плейсхолдеры);
* `type: ITEM` — предмет: `BOOK`, `CHEST`, `GOLD_BLOCK`, `DIAMOND_SWORD`…
  `rotation-speed` — градусов в тик, `bob-amplitude` — покачивание в блоках;
* `anim-type: WAVE` + `anim-color1` / `anim-color2` (числа, 0xRRGGBB) + `anim-speed`;
* `display-settings` — строка через `|`: `billboard`, `alignment`, `scaleX/Y/Z`,
  `backgroundColor` (0 = нет плашки, 1 = стандартная, иначе ARGB),
  `shadow`, `range`, `updateRange`, `lineHeight`, `lineWidth`, `facing`,
  `downOrigin`, `unifiedBackground`.

Цвета: `&7`-коды тоже работают, но в файле всё уже переведено в MiniMessage:
`&#F8BEFB` → `<#F8BEFB>`, `&l` → `<bold>`, `&7` → `<gray>`.

## Если хочешь другой стиль

* плашка под текстом: `backgroundColor=1` в `display-settings` или ARGB, например `backgroundColor=-1610612736`;
* крупнее: `scaleX=1.2|scaleY=1.2|scaleZ=1.2`;
* радужный заголовок: `content: <rainbow>ЗАГОЛОВОК</rainbow>`;
* другая анимация: `anim-type: BURN` / `TYPEWRITER` / `SCROLL` / `RAINBOW`;
* предмет не крутится: убрать `rotation-speed` и `bob-amplitude`.

## Старые файлы

`DecentHolograms/holograms/*.yml` в репозитории оставлены как исходник —
из них собран `dHolograms/holograms.yml`. Умный импорт есть и у самого плагина:
`/dh import decentholograms` (читает `plugins/DecentHolograms/holograms/`),
но тогда вид будет старый, без градиентов и анимаций.
