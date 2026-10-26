# ElytriX update — 2026-10-26

## Downloads

- `ElytrixCemetery.jar` — Cemetery ghost cap is 8; killed ghosts respawn after 10 minutes; only canonical Ghost Shards drop, with no XP or player kill message. Cemetery ghosts are cleared on plugin shutdown.
- `ElytrixTrader.jar` — updated Trader with the animated ElytriX GUI, per-trader coordinate placeholders, and Ghost trades that accept only canonical Cemetery Ghost Shards. Ghost offers and prices remain intentionally empty until configured with `/etr edit ghost`.
- `Elytrix-Menus.zip` — BMenu command config and the complete `elytrix` menu set, including earning/event pages, `/salary`, the seller placeholder, `/donate`, and the empty `/moneyshop` placeholder. Merge the config and menu paths into the existing BMenu installation; do not replace the full config or unrelated menu folders.

## Ghost schematic prerequisite

The Ghost Trader config references exactly `ghost.schem`. The original schematic was not present in this checkout, so no replacement is included. Place the original file at `plugins/ElytrixTrader/schematics/ghost.schem` or in the configured WorldEdit/FAWE schematic directory before relying on the Ghost Trader build; the plugin can otherwise spawn without its build.

## Required plugins

- Cemetery: WorldEdit, WorldGuard, DecentHolograms; PlaceholderAPI and FAWE are optional.
- Trader: VirtualEntityApi and BLib are required; WorldEdit/FAWE, Vault, PlaceholderAPI and BMenu are optional.

## Build and test notes

Cemetery compiled for Java 8 against the project's local Spigot 1.16.5 and plugin API jars (7 warnings). Trader compiled for Java 17 with ECJ; unavailable `provided` dependency APIs were represented by compile-only stubs matching the declared API signatures, and those stubs are not included in the JAR (29 warnings). The BMenu YAML/menu-link checks passed. No live-server smoke test was available; back up existing files and test on the target server before deployment.
