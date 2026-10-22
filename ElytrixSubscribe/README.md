# ElytrixSubscribe 2.0

Подписка ElytriX+ для Paper 1.21.11: меню на BMenu, функции и **три питомца**
(попугай, аллей, пчела) на VirtualEntityApi.

## Что нового

- **Меню переехали в BMenu** с нашей анимацией: `elytrix:sub`,
  `elytrix:sub_pets`, `elytrix:sub_pet_parrot`, `elytrix:sub_pet_allay`,
  `elytrix:sub_pet_bee`. Файлы — в `BMenu/menu/elytrix/main/`.
- **Три питомца**, включить можно **только одного**: включение второго
  выключает первого. У каждого свои режимы:
  - попугай — Круг, Рядом, На плече (+ окрас, 5 цветов);
  - аллей — Круг, Рядом, Позади, Танец (+ свечение);
  - пчела — Круг, Рядом, Зигзаг, Кувырок (+ размер, малыш).
- **ПКМ по питомцу в мире** открывает его личное меню настроек (имя, режим,
  внешний вид). Внутри — кнопки «Включить/выключить», «Поведение», «Окрас»,
  «Имя».
- **Настройки сохраняются** между перезаходами (`plugins/ElytrixSubscribe/data.yml`).

## Установка

1. Положить `ElytrixSubscribe-2.0.jar` в `plugins/`.
2. Зависимости на сервере: **Paper 1.21.11**, **BMenu**, **PlaceholderAPI**,
   **LuckPerms**, **VirtualEntityApi** + **BLib** (без них питомцы отключатся).
3. Скопировать 5 файлов меню из `BMenu/menu/elytrix/main/` в
   `plugins/BMenu/menu/elytrix/main/`.
4. Перезапустить сервер: создастся `config.yml` и `data.yml`.

## Команды

| Команда | Что делает |
|---|---|
| `/subscribe` | меню подписки (функции) |
| `/subscribe pets` | выбор питомца |
| `/subscribe pet <parrot\|allay\|bee> toggle` | включить/выключить |
| `/subscribe pet <…> behavior` | следующий режим |
| `/subscribe pet <…> variant` | следующий окрас/свечение/размер |
| `/subscribe pet <…> name <имя>` | имя (`сброс` — убрать) |
| `/subscribe delay` | сколько осталось |
| `/subscribe add <ник> <дни>` | выдать (право `elytrixsubscribe.add`) |
| `/subscribe remove <ник>` | забрать (право `elytrixsubscribe.add`) |

Имя питомца можно ввести и в чате: кнопка «Имя питомца» в меню ждёт строку
(на Paper). Написать `отмена` — выйти.

## Плейсхолдеры

`%slidesubscribe_hassubscribe%`, `%slidesubscribe_sub_status%`,
`%slidesubscribe_pet%`, `%slidesubscribe_fn_auto-totem%`,
`%slidesubscribe_pet_parrot_status%`, `%slidesubscribe_pet_parrot_name%`,
`%slidesubscribe_pet_parrot_behavior%`, `%slidesubscribe_pet_parrot_look%`
(то же для `allay` и `bee`).

## Важно при обновлении

- Старый `database.db` (SQLite) **не читается**: драйвера `sqlite-jdbc` на
  сервере нет, поэтому прежняя версия не сохраняла данные. Срок подписки
  теперь пишется в `data.yml`.
- Если игрокам нужно вернуть срок — `/subscribe add <ник> <дни>`.
- Настройки «БД» из старого `config.yml` (`database.*`) больше не нужны.

## Сборка

В песочнице нет Maven — собираем ECJ:

```bash
bash tools/build.sh <путь к java-runtime> /tmp/ecj.jar
```

Готовый jar: `ElytrixSubscribe-2.0.jar`. Стабы Bukkit/Adventure/
VirtualEntityApi лежат в `stubs/` и в jar не попадают.
