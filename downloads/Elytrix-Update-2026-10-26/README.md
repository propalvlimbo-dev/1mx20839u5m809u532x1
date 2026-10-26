# ElytriX update — 2026-10-26

## Downloads

- `ElytrixCemetery.jar` — Cemetery ghost cap is 8; killed ghosts respawn after 10 minutes; only canonical Ghost Shards drop, with no XP or player kill message. Cemetery ghosts are cleared on plugin shutdown.
- `ElytrixTrader.jar` — ElytrixTrader 2.2. Ghost Trader now appears for 1 hour every 2 hours (the first appearance begins when the plugin starts); its own menu placeholders show the next cycle. Ghost trades still accept only canonical Cemetery Ghost Shards, and offers/prices remain empty until configured with `/etr edit ghost`.
- `ElytrixItem-2.5.6.jar` — gear has real, visible vanilla enchantments without duplicate custom lore; potion/arrow effects are real `PotionMeta` effects and visible in the native tooltip. Donshar returns 2× the final hit damage on every hit, using a magic source that bypasses ordinary armor.
- `Elytrix-Menus.zip` — updated BMenu menus and config. The salary button runs `/salary` as the player; the seller button opens the existing `/seller`; free donate runs `/free`. `/event` no longer lists Cemetery, Dungeons, or Paradise Place. The Ghost Trader and empty `/moneyshop` info pages use the 54-slot style of the Experienced Trader page.

## BMenu migration notes

Merge the included `BMenu/config.yml` and `menu/elytrix/` paths into the existing installation; do not replace unrelated BMenu settings or menus. Remove old `open_commands` entries for `salary`, `paradise_event`, and `help_freedonate`, plus obsolete menu files `salary/rub.yml`, `help/seller_stub.yml`, `help/help_freedonate.yml`, `events/cemetery_event.yml`, `events/dungeons_event.yml`, and `events/paradise_event.yml`. The archive's `INSTALL.txt` lists the same cleanup steps.

`/moneyshop` remains an empty placeholder. `/donate` remains the decorative 1299 ₽ item; no new privileges or benefits were added.

## Ghost schematic prerequisite

The Ghost Trader config references exactly `ghost.schem`. The original schematic was not present in this checkout, so no replacement is included. Place the original file at `plugins/ElytrixTrader/schematics/ghost.schem` or in the configured WorldEdit/FAWE schematic directory before relying on the Ghost Trader build; the plugin can otherwise spawn without its build.

## Required plugins

- Cemetery: WorldEdit, WorldGuard, DecentHolograms; PlaceholderAPI and FAWE are optional.
- Trader: VirtualEntityApi and BLib are required; WorldEdit/FAWE, Vault, PlaceholderAPI and BMenu are optional.
- ElytrixItem: Paper 1.21.11; optional WorldGuard and Vault integrations.

## Build and test notes

Cemetery compiled for Java 8 against the project's local Spigot 1.16.5 and plugin API jars (7 warnings). Trader compiled for Java 17 with ECJ; unavailable `provided` dependency APIs were represented by compile-only stubs matching the declared API signatures, and those stubs are not included in the JAR. ElytrixItem compiled for Java 21 with ECJ; its 1,193 checked Paper API references had zero signature mismatches. BMenu YAML/menu-link checks passed. No live-server smoke test was available; back up existing files and test on the target server before deployment.
