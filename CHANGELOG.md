# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.0.0] - Unreleased

Rewrite in Kotlin for Paper 1.21.11. Same plugin name, data folder and `/sis` command.

### Added

- Trade screen
  - Villager-style layout: trade list on the left, every cost and reward of the selected trade on the right;
    the selected trade is marked with a yellow pane
  - Buttons for x1 / x8 / x64 / as many as possible; the result arrow trades like a villager's result slot
  - 1.x click keys on the arrow and the list: right-click 1, shift 8, wheel 64, Q as many as possible
    (a plain left click in the list selects); every clickable item shows these keys
  - Clicking an item in a trade searches all shops: a cost finds trades that give it, a reward finds trades that
    take it (`sis.search`)
  - Villager voices: "yes" on a trade, "no" when it cannot be done (the reason goes to chat)
  - Clicking outside the window turns pages (left = previous, right = next); page arrows only when that page exists
  - Bundles preview trades with more entries than slots
- Trades
  - Any number of costs and rewards, any amounts (no more 2→1 / 4→4 / 6→2 shop types)
  - Money (Vault) and console-command rewards as trade entries
  - Limits per player or shared stock, resetting daily / weekly / monthly; success chance; per-trade permission
- Storage Box (1.8.0, maven.azisaba.net): box contents count as items the player has and pay costs after loose
  items; rewards go into a box that already holds that item, even with a full inventory
- Personal settings (`/sis settings`, button in the trade screen): pay from Storage Boxes, rewards into Storage
  Boxes, trade from the list, show the how-to dialog
- Editing
  - Dialogs instead of chat prompts (shop settings, NPC kind, trade options, amounts, commands, search)
  - Own icon per editor button
  - Middle-click copies a trade's item to the cursor for staff (trade editor, shop editor, trade screen)
  - NPC look copied from any entity (`EntitySnapshot`), mannequin shops with skins, equipment, invisible NPC,
    look-at-player, TextDisplay names
  - Change history per shop with `/sis history` / `/sis restore`
- SQLite or MariaDB for trade counts, logs and player settings
- `ShopPreTradeEvent` / `ShopTradeEvent`
- Automatic migration of 1.x data (moved to `legacy-1.x/`), `/sis migrate` for other folders; shops migrated by
  an earlier 2.0 build take their invisible-NPC setting from `legacy-1.x/` on the next start

### Changed

- Shop files: `shops/<id>.yml` (format 2); IDs like `world_10_64_-3`
- Lock and searchable work as in 1.x: a locked shop opens only for `sis.locked` (no view-only state),
  `/sis open` still opens it; unsearchable shops stay out of search and the shop list
- Shops without trades cannot be opened (from the NPC, `/sis open`, the list, search or the editor)
- Permissions: `sis.admin`, `sis.locked` (1.x `sis.op` still grants them)
- Messages: the bundled texts are always used; `lang/<lang>.yml` holds only overrides and `lang/default/` has the
  current texts. A full copy written by an early 2.0 build is moved to `lang/<lang>.yml.old`
- 1.x `TradeCompleteEvent` is still fired (deprecated) for plugins that listen to it by name

### Fixed

- Message keys `yes` / `no` were read as booleans by YAML, so their texts never showed (now checked by a test)
- Baby NPCs grew up and zombie-type NPCs burned in daylight
- Trade limits were dropped when a shop file was read back

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
