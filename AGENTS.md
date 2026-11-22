# AGENTS.md — SearchableInfiniteShop 2.x

Paper 1.21.11 plugin: villager-style shops with unlimited trade entries, search, limits and itemized trades.
Rewrite of 1.x (branch `ver/1.21.11`) in Kotlin. Plugin name, data folder and `/sis` are unchanged.

## Build

- `./gradlew build` → `build/libs/SearchableInfiniteShop-<version>.jar` (tests run as part of build)
- JDK 21. Single Gradle module. Kotlin stdlib, HikariCP, sqlite-jdbc and mariadb-java-client are NOT shaded:
  Paper downloads them from `plugin.yml` `libraries` (versions filled in by `processResources`).

## Layout (`src/main/kotlin/net/azisaba/sis`)

| package | role |
|---|---|
| `model` | `Shop`, `Trade`, `TradeEntry` (Item / Mythic / Money / Command), `TradeOptions`, `NpcSpec` |
| `storage` | `ShopYaml` (format 2), `ShopRepository` (one file per shop + gz history), `Database` (SQLite/MariaDB) |
| `service` | `TradeService` (check + do N trades at once), `TradeCounter` (limits, per period), `PeriodClock`, `ShopService` (registry; call `changed()` after editing a shop) |
| `npc` | `NpcManager`: non-persistent NPCs spawned per chunk load from `EntitySnapshot`s |
| `gui` | `Menu` base, `TradeMenu` (villager-style), editors, search, shop list |
| `dialog` | Paper Dialog API forms (replace 1.x chat prompts) |
| `itemize` | trades/shops as items (PDC holds the YAML) |
| `migration` | `LegacyImporter`: 1.x `shops/<world>,<x>,<y>,<z>.yml` + `options.yml` → 2.x |
| `api.event` | `ShopPreTradeEvent`, `ShopTradeEvent` |

Java parts:
- `item/mythic/ItemNormalizer.java` is SHARED with AziSync, EnderChestPlus, StorageBox, ItemStash (and mirrored in
  MoreDataFix). Keep it identical apart from the package line; change all copies together.
- `item/mythic/*MythicItemMatcher.java`, `VanillaMythicItems.java`: carried over from 1.x; all MythicMobs access goes
  through `MythicInstanceProvider`.
- `ryuzuinfiniteshop/ryuzuinfiniteshop/event/TradeCompleteEvent.java`: 1.x event under its old name for
  LifeQuestCore, which listens by class name via reflection. Do not rename or remove.

## Rules

- Commands are declared in `plugin.yml` (no Brigadier-only registration).
- Item identity: `Items.toEntry` / `Items.matches` (same rules as 1.x: MMID entries unless excluded / vanilla
  definition / different lore; `matching.mythic-strict` also compares texture and lore).
- Database access only through `Database.async` (single thread, ordered) — never on the main thread.
- Shop files are written on a background thread; the YAML text is built on the main thread.

## Tests

JUnit 5 + MockBukkit (`src/test/kotlin`): trade engine, period keys, YAML round trip/history, 1.x import.
