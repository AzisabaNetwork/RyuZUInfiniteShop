package net.azisaba.sis.itemize

import com.google.gson.JsonParser
import net.azisaba.sis.Sis
import net.azisaba.sis.migration.LegacyImporter
import net.azisaba.sis.migration.legacy.LegacySkullMigration
import net.azisaba.sis.model.Shop
import net.azisaba.sis.model.ShopLocation
import net.azisaba.sis.model.Trade
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import java.io.File

/**
 * Shop items ("compression gems") made by 1.x. Their data is in the item's custom data:
 * - 1.1.x: `ShopItemId`, pointing at `shop-items/<id>.json` (moved to `legacy-1.x/shop-items/` by the migration)
 * - older: `ShopData` (the shop file without trades), `TradesSize` and `Give<n>` / `Take<n>` (item lists as YAML)
 * A gem holding a single trade is the "trade item" of 1.x.
 */
object LegacyGems {
    class Gem(val shopData: String?, val trades: List<Trade>)

    private fun customData(item: ItemStack?): Map<*, *>? {
        if (item == null || item.type.isAir) return null
        return runCatching {
            val root = LegacySkullMigration.readNbt(item.serializeAsBytes())
            (root["components"] as? Map<*, *>)?.get("minecraft:custom_data") as? Map<*, *>
        }.getOrNull()
    }

    fun isLegacy(item: ItemStack?): Boolean {
        val data = customData(item) ?: return false
        return data["ShopItemId"] is String || data["TradesSize"] is String || data["ShopData"] is String
    }

    /** The gem's shop data and trades, or null when [item] is not a 1.x gem (or its stored file is gone). */
    fun read(sis: Sis, item: ItemStack?): Gem? {
        val data = customData(item) ?: return null
        val importer = LegacyImporter(sis)
        (data["ShopItemId"] as? String)?.let { id -> stored(sis, importer, id)?.let { return it } }
        val size = (data["TradesSize"] as? String)?.toIntOrNull() ?: return (data["ShopData"] as? String)?.let { Gem(it, emptyList()) }
        val trades = (0 until size).mapNotNull { i ->
            val give = (data["Give$i"] as? String)?.let(importer::itemListFromGem).orEmpty()
            val take = (data["Take$i"] as? String)?.let(importer::itemListFromGem).orEmpty()
            importer.tradeFromGem(mapOf("give" to give, "take" to take))
        }
        return Gem(data["ShopData"] as? String, trades)
    }

    private fun stored(sis: Sis, importer: LegacyImporter, id: String): Gem? {
        if (!id.matches(Regex("[0-9a-fA-F-]{36}"))) return null
        val file = listOf("legacy-1.x/shop-items", "shop-items").map { File(sis.plugin.dataFolder, "$it/$id.json") }.firstOrNull { it.isFile } ?: return null
        importer.registerAliases()
        val json = JsonParser.parseString(file.readText(Charsets.UTF_8)).asJsonObject
        val trades = json.getAsJsonArray("trades")?.mapNotNull { element ->
            val yaml = YamlConfiguration().apply { loadFromString(element.asString) }
            yaml.getConfigurationSection("trade")?.getValues(false)?.let(importer::tradeFromGem)
        }.orEmpty()
        return Gem(json.get("shopData")?.takeUnless { it.isJsonNull }?.asString, trades)
    }

    /** A new shop at [at] from a 1.x gem that holds a shop. */
    fun shop(sis: Sis, gem: Gem, id: String, at: ShopLocation): Shop? {
        val data = gem.shopData ?: return null
        return LegacyImporter(sis).shopFromGem(data, id, at, gem.trades)
    }
}
