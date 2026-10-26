# ElytriX update — 2026-10-26

## Downloads

- `ElytrixCemetery.jar` — Cemetery ghost cap is 8; killed ghosts respawn after 10 minutes; only canonical Ghost Shards drop, with no XP or player kill message. Cemetery ghosts are cleared on plugin shutdown.
- `ElytrixTrader.jar` (2.3.1; also available as `downloads/ElytrixTrader-2.3.1.jar`) — chunk loading for scheduled/manual spawns uses Paper's asynchronous API; there is no synchronous `loadChunk()` or `getChunkAt()` path. The Experienced Trader now checks only safe surface positions (never caves/mines). Ghost Trader still appears for 1 hour every 2 hours, with a different nearby safe spawn point when alternatives exist. Both traders use the shared `ghost.schem`; Ghost trades accept only canonical Cemetery Ghost Shards, and offers/prices remain empty until configured with `/etr edit ghost`. On servers without Paper's async chunk API, unloaded chunks are skipped rather than synchronously loaded.

  SHA-256: `135c10b9f9ce25098295c37a01358ef3076ba2e0fb08f71ddeef33ad7618dd39`.
- `ElytrixItem-2.5.7.jar` — Donshar now reflects the raw attack damage before the target's armor/mitigation, with a 5% chance. The returned hit uses a magic source and is not reduced by the holder's armor. Existing real enchantments and potion/arrow effects remain. `ElytrixItem-2.5.6.jar` in this folder is an older release; use 2.5.7.
- `ElytrixSubscribe-2.0.1.jar` — fixes a lost-wakeup race in asynchronous `data.yml` saves and ensures shutdown writes the final snapshot.
- `ElytrixDuelsBridge-1.12.4.jar` — separates the 2v2 queue-cancel message from the 1v1 `queue-left` key; duplicate YAML key removed.
- `ElytrixVault-1.0.1.jar` — current Vault artifact; Maven and custom-build artifact versions now match the plugin version.
- `NoSpears-1.1.1.jar` — detects Lunge stored on enchanted books and periodically removes later-issued spears/Lunge books from online player inventories, ender chests, and cursors.
- `ElytrixSeller-2.0.2-ElytriX.jar` (RU) and `ElytrixSeller-2.0.2-ElytriX-en.jar` (EN) — include the synchronous DB-schema fix for the reported `no such table` errors and fix `/seller` GUI navigation: button clicks now execute `opengui` actions instead of returning early. Built through GitHub Actions and descriptor-validated. The separate asynchronous PlayerData load/save race in AUD-01 remains; there was no live Paper test, so back up the database and smoke-test before production.

  SHA-256: RU `fca93cb7c09fd05279a0dd3a9251d3ad3d85b9aa6e20836420bed822bf9300b4`; EN `af8fbf84e07ed1d24efa6f18abd59dd6bb0ce4cd44c0352953f5a5f5fa934894`.
- `ElytrixDungeons-1.0.0.jar` — refreshed root download, byte-identical to the current source build (the older `Elytrix-full.zip` remains historical).
- `ElytrixCasino-2.2.1.jar` (also named `ElytrixCasino.jar` at the root) — config upgrades now merge missing defaults and create backups instead of replacing custom settings.
- `Elytrix-Menus.zip` — compact BMenu overlay with the changed ElytriX menus and config.
- `Elytrix-BMenu-Full-Fixed-2026-10-26.zip` — full 72-file BMenu configuration tree from the repository, including all menu folders and examples, plus `INSTALL.txt`. This is config/menu content, not the BMenu plugin JAR; merge server-specific settings instead of blindly replacing them. Includes the same fixes: `/salary`, `/seller`, `/free` run as the player; `/help` links to `/event` with Cemetery and Dungeons; `/moneyshop` stays empty; `/donate` keeps its original rank names, gradients, `%player_name%`, descriptions, prices, materials and actions exactly, with only the stained-glass palette and 3×3 card positions changed; no privileges or purchase/grant actions were added; Paradise Place stays absent.

  SHA-256: `240e4026d7707d85cf81cd19b06623844e6ca7372fc4ef9e17679f2463eea352`.
- `ElytrixSeller-configs.zip` — RU/EN seller defaults with farmer crops (including Nether Wart), a new farm autosell category, deepslate/raw/copper/amethyst ore types and loot from newer mobs/trial chambers. Merge into existing files; this is a config pack, separate from the two Seller JARs above.

## BMenu migration notes

Merge the included `BMenu/config.yml` and `menu/elytrix/` paths into the existing installation; do not replace unrelated BMenu settings or menus. Remove old `open_commands` entries for `salary`, `paradise_event`, and `help_freedonate`, plus obsolete menu files `salary/rub.yml`, `help/seller_stub.yml`, `help/help_freedonate.yml`, and `events/paradise_event.yml`. The restored `events/cemetery_event.yml` and `events/dungeons_event.yml` are included in the archive; copy them back if they were removed earlier. The archive's `INSTALL.txt` lists the same cleanup steps.

`/moneyshop` remains an empty placeholder. `/donate` is informational/read-only: all original rank names, gradients, `%player_name%`, prices, descriptions, materials and item content are restored exactly. Only the stained-glass colors and card layout changed; no new privileges or purchase/grant actions were added.

## ElytrixSeller config migration

The Seller config pack contains updated RU/EN `items.yml` and `config.yml` defaults plus the autosell category GUI. Back up the current plugin config, merge missing item IDs/prices and the new `farms` category, and add the `category_farms` button without overwriting custom prices or other GUI changes. Use `/rseller reload` after merging. The RU/EN Seller 2.0.2 JARs are included above and were built through the repository's GitHub Actions workflow. They fix both the missing-table startup race and the `/seller` GUI click handler, which previously skipped submenu actions. The separate async player load/save risk in AUD-01 remains open; no live Paper test was possible. Back up player data and smoke-test on a staging server before production.

## Ghost schematic prerequisite

Both bundled trader configs now reference exactly `ghost.schem`. The original schematic was not present in this checkout, so no replacement is included. Place the original file at `plugins/ElytrixTrader/schematics/ghost.schem` or in the configured WorldEdit/FAWE schematic directory; both traders can otherwise spawn without their build.

## Required plugins

- Cemetery: WorldEdit, WorldGuard, DecentHolograms; PlaceholderAPI and FAWE are optional.
- Trader: VirtualEntityApi and BLib are required; WorldEdit/FAWE, Vault, PlaceholderAPI and BMenu are optional.
- ElytrixItem: Paper 1.21.11; optional WorldGuard and Vault integrations.

## Plugin audit

The full report is `../../PLUGIN_AUDIT.md`. It covers all 24 plugin/config bundles in the repository, including the menu/config-only folders, with findings, affected paths, fixes, and limitations. Seller 2.0.2 fixes both the reported missing-table startup race and broken `/seller` GUI button actions; AUD-01 tracks the separate async player load/save risk, which needs a staging DB test. Trader also needs the server-supplied `ghost.schem`; the legacy DeluxeMenus bundle has conditional route issues if active alongside BMenu.

## Build and test notes

- Casino, Dungeons, Item, PotionStacker, Subscribe, and Vault isolated ECJ builds completed successfully; Subscribe 2.0.1 and Vault 1.0.1 were rebuilt after this audit's changes. DuelsBridge 1.12.4 built successfully. Cemetery compiled to 25 classes with zero ECJ errors/warnings. Trader compiled with compile-only stubs for unavailable provided APIs; those stubs are not packaged.
- NoSpears 1.1.1 compiled against the vendored Spigot API and its JAR passed integrity/plugin-descriptor checks. This is a compile check, not a live Paper behavior test.
- All 58 repository JAR copies (including project targets and download duplicates) passed ZIP integrity checks; all 52 embedded `plugin.yml` descriptors parse and their declared main classes exist. All 249 YAML files parse as YAML streams; the only repeated mapping keys are intentional examples in `BMenu/reference.yml` (`slot` and `on_view`). Current BMenu routing and material references validated.
- Seller 2.0.2 RU/EN Gradle builds succeeded in GitHub Actions (Gradle 9.0.0/JDK 21), and both JARs passed descriptor/integrity checks. The synchronous player/sell-log schema fix addresses the reported `no such table` race, and the GUI click handler now lets button actions run. Source-extracted SQLite DDL passed a smoke test, but no Paper/live-plugin test was run; the separate async player load/save risk remains open. EpicDuels-Fork regression suites were not run (Maven and Adventure/Paper test classpath unavailable); AddonSphere could not be rebuilt because its declared BCases/VirtualEntityApi/BLib dependencies are not present locally. Its source/POM now target 1.43, but the available 1.42 JAR does not contain that source-only fix. Build and smoke-test on the target server before deployment.
- No live-server smoke test was available. `downloads/Elytrix-full.zip` and the dated `Elytrix-Dungeons-Cemetery-2026-10-25` package are historical bundles; use the 2026-10-26 update folder for the refreshed artifacts, and supply `ghost.schem` separately.
