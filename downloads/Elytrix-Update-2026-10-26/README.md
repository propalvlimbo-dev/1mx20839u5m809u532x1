# ElytriX update — 2026-10-26

## Downloads

- `ElytrixCemetery.jar` — Cemetery ghost cap is 8; killed ghosts respawn after 10 minutes; only canonical Ghost Shards drop, with no XP or player kill message. Cemetery ghosts are cleared on plugin shutdown.
- `ElytrixTrader.jar` (2.3; also available as `downloads/ElytrixTrader-2.3.jar`) — Ghost Trader appears for 1 hour every 2 hours, with a different nearby safe spawn point when alternatives exist. Both traders use the shared `ghost.schem`; hologram lines use visible legacy colors. Ghost trades still accept only canonical Cemetery Ghost Shards, and offers/prices remain empty until configured with `/etr edit ghost`.
- `ElytrixItem-2.5.7.jar` — Donshar now reflects the raw attack damage before the target's armor/mitigation, with a 5% chance. The returned hit uses a magic source and is not reduced by the holder's armor. Existing real enchantments and potion/arrow effects remain.
- `Elytrix-Menus.zip` — updated BMenu menus and config. The salary button runs `/salary` as the player; the seller button opens the existing `/seller`; free donate runs `/free`. `/help` links back to `/event`, and `/event` includes Cemetery and Dungeons; Paradise Place remains absent. Ghost Trader and empty `/moneyshop` info pages use the 54-slot style of the Experienced Trader page.
- `ElytrixSeller-configs.zip` — RU/EN seller defaults with farmer crops (including Nether Wart), a new farm autosell category, deepslate/raw/copper/amethyst ore types and loot from newer mobs/trial chambers. Merge into existing files; this is a config pack, not a Seller JAR.

## BMenu migration notes

Merge the included `BMenu/config.yml` and `menu/elytrix/` paths into the existing installation; do not replace unrelated BMenu settings or menus. Remove old `open_commands` entries for `salary`, `paradise_event`, and `help_freedonate`, plus obsolete menu files `salary/rub.yml`, `help/seller_stub.yml`, `help/help_freedonate.yml`, and `events/paradise_event.yml`. The restored `events/cemetery_event.yml` and `events/dungeons_event.yml` are included in the archive; copy them back if they were removed earlier. The archive's `INSTALL.txt` lists the same cleanup steps.

`/moneyshop` remains an empty placeholder. `/donate` remains the decorative 1299 ₽ item; no new privileges or benefits were added.

## ElytrixSeller config migration

The Seller config pack contains updated RU/EN `items.yml` and `config.yml` defaults plus the autosell category GUI. Back up the current plugin config, merge missing item IDs/prices and the new `farms` category, and add the `category_farms` button without overwriting custom prices or other GUI changes. Use `/rseller reload` after merging. The source project is in `ElytrixSeller/`; its Gradle JAR was not built in this environment.

## Ghost schematic prerequisite

Both bundled trader configs now reference exactly `ghost.schem`. The original schematic was not present in this checkout, so no replacement is included. Place the original file at `plugins/ElytrixTrader/schematics/ghost.schem` or in the configured WorldEdit/FAWE schematic directory; both traders can otherwise spawn without their build.

## Required plugins

- Cemetery: WorldEdit, WorldGuard, DecentHolograms; PlaceholderAPI and FAWE are optional.
- Trader: VirtualEntityApi and BLib are required; WorldEdit/FAWE, Vault, PlaceholderAPI and BMenu are optional.
- ElytrixItem: Paper 1.21.11; optional WorldGuard and Vault integrations.

## Build and test notes

Cemetery compiled for Java 8 against the project's local Spigot 1.16.5 and plugin API jars (7 warnings). Trader compiled for Java 17 with ECJ; unavailable `provided` dependency APIs were represented by compile-only stubs matching the declared API signatures, and those stubs are not included in the JAR. ElytrixItem compiled for Java 21 with ECJ; its 1,193 checked Paper API references had zero signature mismatches. BMenu and Seller YAML/config references passed validation; Seller `ItemsConfig` compiled against the local Spigot 1.16.5 API, but a full Seller JAR build was unavailable because Gradle and its distribution were not present. No live-server smoke test was available; back up existing files and test on the target server before deployment.
