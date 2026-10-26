# Elytrix update — 2026-10-26

## Downloads

- `ElytrixCemetery.jar` — updated Cemetery plugin.
- `ElytrixTrader.jar` — updated Trader plugin with transparent text holograms and the End Ghost Trader.
- `Elytrix-Menus.zip` — BMenu command config plus the updated `/event`, `/donate`, `/help` and `/moneyshop` menus. Merge its paths into the existing BMenu installation; do not replace the full menu directory.

## Required plugins

- Cemetery: WorldEdit, WorldGuard, DecentHolograms; PlaceholderAPI and FAWE are optional.
- Trader: VirtualEntityApi and BLib are required; WorldEdit/FAWE, Vault, PlaceholderAPI and BMenu are optional.

Ghost Trader offers are intentionally empty. Add the real items and exact shard costs in game with `/etr edit ghost`; no products or prices were invented.

## Build notes

Cemetery was compiled for Java 8 against the project's local Spigot 1.16.5 and plugin API jars. Trader was compiled for Java 17 with ECJ; the unavailable `provided` dependency APIs were represented by compile-only stubs matching the project's declared API signatures and are not included in the JAR. Both plugins still require their listed server-side dependencies. Please back up existing plugins and menus before installing and smoke-test on the target server.
