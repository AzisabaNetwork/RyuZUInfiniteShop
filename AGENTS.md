# AGENTS.md — RyuZUInfiniteShop (SearchableInfiniteShop)

## Project

Spigot/Bukkit plugin — infinite paginated shop GUI with search.  
Root `settings.gradle.kts` name: `SearchableInfiniteShop`, group `com.github.ryuzu`.

## Build & dev commands

- **Build all:** `./gradlew build` — produces fat JAR at `build/libs/` (root project aggregates all submodules)
- **Single submodule:** `./gradlew :searchableinfiniteshop-plugin:build`
- **Publish:** `./gradlew clean test publish` (no tests exist, so this is effectively `clean publish`)
- **JDK:** Zulu 21 (defined in `mise.toml`)

## Multi-module structure

| dir | Gradle project | package | role |
|---|---|---|---|
| `api/` | `-api` | `com.github.ryuzu.searchableinfiniteshop.api` | `IMythicHandler` interface |
| `commandlib/` | `-commandlib` | `com.github.ryuzu.ryuzucommandsgenerator` | borrowed command framework |
| `plugin/` | `-plugin` | `ryuzuinfiniteshop.ryuzuinfiniteshop` | **main shaded JAR**, contains plugin.yml |
| `v16older/` | `-v16older` | `com.github.ryuzu.searchableinfiniteshop.v16older` | MythicMobs v4.12.0 compat |
| `v16newer/` | `-v16newer` | `com.github.ryuzu.searchableinfiniteshop.v16newer` | MythicMobs v5.2.1 compat |
| `v21newer/` | `-v21newer` | `com.github.ryuzu.searchableinfiniteshop.v21newer` | MythicMobs v5.12.0 compat |

The `plugin` module depends on all version modules + commandlib + api.

## Key facts

- **No tests** exist anywhere in the repo.
- **Shadow JAR** relocates `com.saicone.rtag` → `${project.group}.libs.rtag` (mandatory, rtag must NOT leak).
- **Package naming is inconsistent:** main plugin class is `ryuzuinfiniteshop.ryuzuinfiniteshop.RyuZUInfiniteShop`, NOT `com.github.ryuzu...`.
- **plugin.yml:** name `SearchableInfiniteShop`, command alias `sis`, api-version `1.13`. No paper-plugin.yml.
- **SoftDepends:** MythicMobs, Citizens, Vault — check `Bukkit.getPluginManager().getPlugin(...)` before use.
- **Version detection:** `RyuZUInfiniteShop.VERSION` is parsed from Bukkit server version string at static init.
- **Lombok** used (`@Getter` on `plugin` field); IDEA annotation processing required.
- **Build artifact:** `build/libs/*.jar` (uploaded by CI).

## Notable conventions

- All config files use `.yml` extension; loaded/saved via `FileUtil` (custom, not Configurate/ADventure).
- GUI system under `data/gui/` is homegrown (not an external GUI lib).
- `ConfigurationSerialization` is used for `MythicItem` and `TradeOption` (Bukkit serialization).
- The `registerAllListeners()` method does classpath scanning from the JAR — at runtime, not compile-time.
- CI publishes to `https://repo.azisaba.net` with credentials from `gradle.properties` secrets.
