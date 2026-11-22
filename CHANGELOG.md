# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.0.0] - Unreleased

Rewrite in Kotlin for Paper 1.21.11. Same plugin name, data folder and `/sis` command.

### Added

- Villager-style trade screen: trade list + full view of the selected trade; x1 / x8 / x64 / as many as possible
- Trades with any number of costs and rewards and any amounts (no more 2→1 / 4→4 / 6→2 shop types)
- Money and console-command rewards as trade entries
- Limits per player or shared (stock), resetting daily / weekly / monthly; per-trade permission
- Dialog-based editing and search (no chat prompts); search by the item in hand
- NPC look from any entity (`EntitySnapshot`), mannequin shops with skins, TextDisplay names, look-at-player
- SQLite or MariaDB for trade counts and logs; gz change history per shop with `/sis history` / `/sis restore`
- `ShopPreTradeEvent` / `ShopTradeEvent`
- Automatic migration of 1.x data (moved to `legacy-1.x/`), `/sis migrate` for other folders

### Changed

- Shop files: `shops/<id>.yml` (format 2); IDs like `world_10_64_-3`
- Permissions: `sis.admin` (1.x `sis.op` still grants it)
- 1.x `TradeCompleteEvent` is still fired (deprecated) for plugins that listen to it by name

### Removed

- Per-entity shop classes and editors (replaced by copying an entity's look), XMaterial, NBT string tags,
  commandlib, the shop types

## [Unreleased 1.x]

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

- **`Shop.java`** — reduced from 791 to 628 lines by extracting three collaborator classes
- **`ShopUtil.java`** — restructured to delegate internally to `ShopRegistry` and `ShopFactory`; all callers unchanged
- **`RyuZUInfiniteShop.java`** — removed dead code (`registerAllListeners`, `extractClassName`) and unused imports

### Fixed

- **Shops in worlds loaded after the plugin (e.g. by Multiverse) never appeared** — such shop files are now deferred and loaded on `WorldLoadEvent` (and set aside again on `WorldUnloadEvent`) instead of aborting the whole load; one broken shop file no longer stops the rest from loading; trade options of deferred shops are kept in `options.yml`; the periodic NPC respawn skips shops whose world is not loaded
- **Typo: `exsistsMythicMob` → `existsMythicMob`** — API (`IMythicHandler`), implementation (`MythicHandlerV5_12_0`), and all callers
- **Typo: `exsited` → `existed`** in `Shop.java`
- **Typo: `changeNPCDirecation` → `changeNPCDirection`** in `Shop.java` and listener class
- **Typo: `ConvartListener` → `ConvertListener`** — class, filename, and all references
- **Typo: `ChangeNpcDirecationListener` → `ChangeNpcDirectionListener`** — class, filename, and all references

### Removed

- **Dead code:** `registerAllListeners()` (reflection-based, never called) and `extractClassName()` (unused) from `RyuZUInfiniteShop.java`
- **Legacy version modules:** `v16older/` and `v16newer/` (removed in `feat/1.21.11` base)
