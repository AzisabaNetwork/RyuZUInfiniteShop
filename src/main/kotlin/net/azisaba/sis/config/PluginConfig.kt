package net.azisaba.sis.config

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.CustomModelData
import net.kyori.adventure.key.Key
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.inventory.ItemStack
import java.time.ZoneId

data class IconSpec(val material: Material, val itemModel: Key? = null, val customModelData: Float? = null) {
    fun create(): ItemStack {
        val item = ItemStack.of(material)
        itemModel?.let { item.setData(DataComponentTypes.ITEM_MODEL, it) }
        customModelData?.let { item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addFloat(it).build()) }
        return item
    }

    companion object {
        fun read(section: ConfigurationSection?, fallback: Material): IconSpec {
            if (section == null) return IconSpec(fallback)
            val material = section.getString("material")?.let { Material.matchMaterial(it) }?.takeIf { it.isItem } ?: fallback
            val model = section.getString("item-model")?.takeIf { it.isNotBlank() }?.let { Key.key(it) }
            val cmd = if (section.contains("custom-model-data")) section.getDouble("custom-model-data").toFloat() else null
            return IconSpec(material, model, cmd)
        }
    }
}

enum class StorageType { SQLITE, MARIADB }

data class MariaDbConfig(
    val host: String,
    val port: Int,
    val database: String,
    val user: String,
    val password: String,
    val poolSize: Int,
)

data class PluginConfig(
    val language: String,
    val storageType: StorageType,
    val tablePrefix: String,
    val mariadb: MariaDbConfig,
    val zone: ZoneId,
    val resetHour: Int,
    val globalRefreshSeconds: Long,
    val mythicStrict: Boolean,
    val mythicExcludedIds: List<String>,
    val logTrades: Boolean,
    val logEdits: Boolean,
    val historyKeep: Int,
    val lookRange: Double,
    val nameOffset: Double,
    val guiTitle: String,
    val searchTitle: String,
    val icons: Map<String, IconSpec>,
    val allowRemoteTrade: Boolean,
    val maxSearchResults: Int,
    val tutorial: Boolean,
    val tradeItemIcon: IconSpec,
    val shopItemIcon: IconSpec,
) {
    fun icon(name: String): ItemStack = (icons[name] ?: IconSpec(Material.PAPER)).create()

    /** Whether items of this MMID must not be saved by MMID. Entries may use "*" wildcards. */
    fun isMythicExcluded(id: String): Boolean = mythicExcludedIds.any { pattern ->
        if ('*' in pattern) Regex(pattern.split('*').joinToString(".*") { Regex.escape(it) }, RegexOption.IGNORE_CASE).matches(id)
        else pattern.equals(id, ignoreCase = true)
    }

    companion object {
        private val ICON_DEFAULTS = mapOf(
            "status-ready" to Material.LIME_STAINED_GLASS_PANE,
            "status-unavailable" to Material.RED_STAINED_GLASS_PANE,
            "status-limited" to Material.ORANGE_STAINED_GLASS_PANE,
            "status-locked" to Material.GRAY_STAINED_GLASS_PANE,
            "filler" to Material.BLACK_STAINED_GLASS_PANE,
            "divider" to Material.GRAY_STAINED_GLASS_PANE,
            "previous" to Material.ARROW,
            "next" to Material.ARROW,
            "arrow" to Material.SPECTRAL_ARROW,
            "buy-1" to Material.LIME_DYE,
            "buy-8" to Material.LIME_DYE,
            "buy-64" to Material.LIME_DYE,
            "buy-max" to Material.EMERALD,
            "money" to Material.GOLD_NUGGET,
            "command" to Material.COMMAND_BLOCK,
            "info" to Material.BOOK,
            "search" to Material.SPYGLASS,
            "open-shop" to Material.CHEST,
            "empty-slot" to Material.LIGHT_GRAY_STAINED_GLASS_PANE,
            "new-trade" to Material.WRITABLE_BOOK,
            "preview" to Material.EMERALD,
            "settings" to Material.NAME_TAG,
            "npc-kind" to Material.ARMOR_STAND,
            "capture" to Material.ENDER_EYE,
            "equipment" to Material.IRON_CHESTPLATE,
            "respawn" to Material.RESPAWN_ANCHOR,
            "teleport" to Material.ENDER_PEARL,
            "itemize-shop" to Material.DIAMOND,
            "itemize-trade" to Material.EMERALD_BLOCK,
            "duplicate" to Material.PAPER,
            "delete" to Material.LAVA_BUCKET,
            "options" to Material.COMPARATOR,
            "edit" to Material.ANVIL,
            "back" to Material.OAK_DOOR,
            "close" to Material.BARRIER,
            "player-settings" to Material.REPEATER,
            "selected" to Material.YELLOW_STAINED_GLASS_PANE,
        )

        fun load(config: FileConfiguration): PluginConfig {
            val iconSection = config.getConfigurationSection("gui.icons")
            val icons = ICON_DEFAULTS.mapValues { (name, fallback) -> IconSpec.read(iconSection?.getConfigurationSection(name), fallback) }
            val zone = runCatching { ZoneId.of(config.getString("limits.timezone", "Asia/Tokyo")) }.getOrDefault(ZoneId.systemDefault())
            return PluginConfig(
                language = config.getString("language", "ja")!!,
                storageType = if (config.getString("storage.type", "sqlite").equals("mariadb", true)) StorageType.MARIADB else StorageType.SQLITE,
                tablePrefix = config.getString("storage.table-prefix", "sis_")!!.filter { it.isLetterOrDigit() || it == '_' },
                mariadb = MariaDbConfig(
                    host = config.getString("storage.mariadb.host", "localhost")!!,
                    port = config.getInt("storage.mariadb.port", 3306),
                    database = config.getString("storage.mariadb.database", "sis")!!,
                    user = config.getString("storage.mariadb.user", "root")!!,
                    password = config.getString("storage.mariadb.password", "")!!,
                    poolSize = config.getInt("storage.mariadb.pool-size", 4).coerceAtLeast(1),
                ),
                zone = zone,
                resetHour = config.getInt("limits.reset-hour", 5).coerceIn(0, 23),
                globalRefreshSeconds = config.getLong("limits.global-refresh-seconds", 30).coerceAtLeast(5),
                mythicStrict = config.getBoolean("matching.mythic-strict", true),
                mythicExcludedIds = config.getStringList("matching.mythic-excluded-ids"),
                logTrades = config.getBoolean("logging.trades", true),
                logEdits = config.getBoolean("logging.edits", true),
                historyKeep = config.getInt("history.keep", 30).coerceAtLeast(0),
                lookRange = config.getDouble("npc.look-range", 8.0),
                nameOffset = config.getDouble("npc.name-offset", 0.35),
                guiTitle = config.getString("gui.title", "<shop>")!!,
                searchTitle = config.getString("gui.search-title", "Search")!!,
                icons = icons,
                allowRemoteTrade = config.getBoolean("search.allow-remote-trade", false),
                maxSearchResults = config.getInt("search.max-results", 2000).coerceAtLeast(1),
                tutorial = config.getBoolean("tutorial", true),
                tradeItemIcon = IconSpec.read(config.getConfigurationSection("itemize.trade-item"), Material.EMERALD),
                shopItemIcon = IconSpec.read(config.getConfigurationSection("itemize.shop-item"), Material.DIAMOND),
            )
        }
    }
}
