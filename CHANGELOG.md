# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **Tests (232 tests total)** — comprehensive test suite covering:
  - Pure-logic tests: `JavaUtil`, `ShopType`, `TradeOption` (de/serialization), `NpcType`, `OptionType`
  - MockBukkit tests: `LocationUtil`, `Config` (YAML load/save), `ShopTrade` (extended edge cases)
  - Shop characterization tests: creation, YAML round-trip, page calculation, type transitions, serialization format
  - Test infrastructure: `ShopTestBase` with shared MockBukkit/plugin mocking
- **`ShopSerializer`** — extracted YAML serialization/deserialization from `Shop.java`
- **`ShopPageCalculator`** — extracted pure-logic page calculation from `Shop.java`
- **`ShopNPCManager`** — extracted NPC entity lifecycle management from `Shop.java`
- **`ShopRegistry`** — extracted shop instance registry from `ShopUtil`
- **`ShopFactory`** — extracted entity-type-aware shop creation from `ShopUtil`

### Changed

- **GUI and trade-search allocations** — page trades are cached per GUI instead of splitting the trade list on every render, `ShopTrade.getResult` reuses one inventory snapshot, `ObjectItems` gained index-based lookups so trade search no longer allocates an `ItemStack[]` per trade, NPC equipment is converted once per update, and `ShopRegistry` sorts with a single `entrySet` pass
- **`DiffBackupUtil.write`** — data files are written to a temporary file and moved into place, so an interrupted save leaves the previous file intact
- **`Shop.java`** — reduced from 791 to 628 lines by extracting three collaborator classes
- **`ShopUtil.java`** — restructured to delegate internally to `ShopRegistry` and `ShopFactory`; all callers unchanged
- **`RyuZUInfiniteShop.java`** — removed dead code (`registerAllListeners`, `extractClassName`) and unused imports

### Fixed

- **`ShopTrade.getFilter()` / `ItemUtil.getWhitePanel()`** — return copies of the cached panel instead of a shared `ItemStack`, and are built on first use so neither class needs a running server to initialise
- **Trade search during a reload** — the registry and trade lists are walked by index, so a shop reload can no longer break an async search with a `ConcurrentModificationException`
- **Test fixture `legacy/shulker-shop-1.15.2.yml`** — the two `LegacyContainerMigrationTest` cases are skipped when the uncommitted fixture is missing instead of failing
- **CI** — `test.yml` now runs for every pull request and for `dev/**`/`feat/**` pushes, so PRs into version branches are tested
- **Shops in worlds loaded after the plugin (e.g. by Multiverse) never appeared** — such shop files are now deferred and loaded on `WorldLoadEvent` (and set aside again on `WorldUnloadEvent`) instead of aborting the whole load; one broken shop file no longer stops the rest from loading; trade options of deferred shops are kept in `options.yml`; the periodic NPC respawn skips shops whose world is not loaded
- **Typo: `exsistsMythicMob` → `existsMythicMob`** — API (`IMythicHandler`), implementation (`MythicHandlerV5_12_0`), and all callers
- **Typo: `exsited` → `existed`** in `Shop.java`
- **Typo: `changeNPCDirecation` → `changeNPCDirection`** in `Shop.java` and listener class
- **Typo: `ConvartListener` → `ConvertListener`** — class, filename, and all references
- **Typo: `ChangeNpcDirecationListener` → `ChangeNpcDirectionListener`** — class, filename, and all references

### Removed

- **Dead code:** `registerAllListeners()` (reflection-based, never called) and `extractClassName()` (unused) from `RyuZUInfiniteShop.java`
- **Legacy version modules:** `v16older/` and `v16newer/` (removed in `feat/1.21.11` base)
