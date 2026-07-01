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

- **`Shop.java`** — reduced from 791 to 628 lines by extracting three collaborator classes
- **`ShopUtil.java`** — restructured to delegate internally to `ShopRegistry` and `ShopFactory`; all callers unchanged
- **`RyuZUInfiniteShop.java`** — removed dead code (`registerAllListeners`, `extractClassName`) and unused imports

### Fixed

- **Typo: `exsistsMythicMob` → `existsMythicMob`** — API (`IMythicHandler`), implementation (`MythicHandlerV5_12_0`), and all callers
- **Typo: `exsited` → `existed`** in `Shop.java`
- **Typo: `changeNPCDirecation` → `changeNPCDirection`** in `Shop.java` and listener class
- **Typo: `ConvartListener` → `ConvertListener`** — class, filename, and all references
- **Typo: `ChangeNpcDirecationListener` → `ChangeNpcDirectionListener`** — class, filename, and all references

### Removed

- **Dead code:** `registerAllListeners()` (reflection-based, never called) and `extractClassName()` (unused) from `RyuZUInfiniteShop.java`
- **Legacy version modules:** `v16older/` and `v16newer/` (removed in `feat/1.21.11` base)
