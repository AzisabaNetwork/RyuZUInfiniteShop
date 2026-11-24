package net.azisaba.sis.migration

import net.azisaba.sis.Sis
import net.azisaba.sis.item.Items
import net.azisaba.sis.model.NpcSpec
import net.azisaba.sis.model.Shop
import net.azisaba.sis.model.ShopLocation
import net.azisaba.sis.model.Trade
import net.azisaba.sis.model.TradeEntry
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.MemoryConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Converts the admin shops of a Shopkeepers `save.yml` put into `shops/` (like 1.x did): each becomes a shop at the
 * same block with the same trades and villager look. Converted entries are removed from the file (the original is
 * kept in `legacy-1.x/`); entries that cannot be converted yet (world not loaded) stay for the next start.
 *
 * `shopkeepers.overwrite`: a shop already at that block gets the Shopkeepers trades, name and look instead of
 * having the trades added. `shopkeepers.searchable`: whether new shops are searchable.
 */
class ShopkeepersImporter(private val sis: Sis) {
    data class Report(var created: Int = 0, var merged: Int = 0, var trades: Int = 0, var left: Int = 0)

    fun import(file: File, overwrite: Boolean, searchable: Boolean): Report? {
        if (!file.isFile) return null
        val yaml = YamlConfiguration.loadConfiguration(file)
        val report = Report()
        val done = ArrayList<String>()
        for (key in yaml.getKeys(false)) {
            if (key == "data-version") continue
            val section = yaml.getConfigurationSection(key) ?: continue
            if (section.getString("type", "none") != "admin") continue
            val world = section.getString("world") ?: continue
            if (Bukkit.getWorld(world) == null) {
                report.left++
                continue
            }
            val type = entityType(section) ?: continue
            val location = ShopLocation(world, section.getInt("x") + 0.5, section.getDouble("y"), section.getInt("z") + 0.5, section.getDouble("yaw", 0.0).toFloat(), 0f)
            val trades = trades(section)
            val name = section.getString("name").orEmpty().let { if (it.isBlank()) "" else "<green>$it" }
            val npc = NpcSpec.Vanilla(type, LegacyImporter(sis).snapshotOf(type, lookOf(section)))
            val existing = sis.shops.all.firstOrNull { it.location.world == world && it.location.blockX == location.blockX && it.location.blockY == location.blockY && it.location.blockZ == location.blockZ }
            if (existing != null) {
                if (overwrite) {
                    existing.trades.clear()
                    existing.name = name
                    existing.npc = npc
                }
                existing.trades += trades
                sis.shops.changed(existing, null, "shopkeepers", key, respawn = overwrite)
                report.merged++
            } else {
                val shop = Shop(sis.shops.newId(location), name, location, npc, trades.toMutableList(), searchable = searchable)
                sis.shops.create(shop, null)
                report.created++
            }
            report.trades += trades.size
            done += key
        }
        if (done.isNotEmpty()) {
            val legacy = File(file.parentFile.parentFile, "legacy-1.x").apply { mkdirs() }
            file.copyTo(File(legacy, "shopkeepers-save-${SimpleDateFormat("yyyyMMdd-HHmmss").format(Date())}.yml"))
            done.forEach { yaml.set(it, null) }
            // Player shops and the like are not converted: they stay in the file (1.x did the same).
            if (yaml.getKeys(false).all { it == "data-version" }) file.delete() else yaml.save(file)
        }
        return report
    }

    private fun entityType(section: ConfigurationSection): org.bukkit.entity.EntityType? {
        val raw = section.getString("object.type") ?: section.getString("object") ?: "villager"
        val key = raw.lowercase(Locale.ROOT).replace('-', '_').substringAfter(':')
        return Registry.ENTITY_TYPE.get(NamespacedKey.minecraft(key))?.takeIf { it.isSpawnable }
    }

    private fun trades(section: ConfigurationSection): List<Trade> {
        val recipes = section.getConfigurationSection("recipes") ?: return emptyList()
        return recipes.getKeys(false).mapNotNull { id ->
            val r = recipes.getConfigurationSection(id) ?: return@mapNotNull null
            fun entry(path: String): TradeEntry? = r.getItemStack(path)?.takeUnless { it.type.isAir }?.let { Items.toEntry(it.clone(), it.amount) }
            val rewards = listOfNotNull(entry("resultItem"))
            val costs = listOfNotNull(entry("item1"), entry("item2"))
            if (rewards.isEmpty() && costs.isEmpty()) null else Trade(costs = merge(costs), rewards = rewards)
        }
    }

    /** item1 and item2 of the same item become one entry. */
    private fun merge(entries: List<TradeEntry>): List<TradeEntry> {
        if (entries.size < 2) return entries
        val (a, b) = entries
        return when {
            a is TradeEntry.Item && b is TradeEntry.Item && a.item.isSimilar(b.item) -> listOf(a.copy(amount = a.amount + b.amount))
            a is TradeEntry.Mythic && b is TradeEntry.Mythic && a.id == b.id -> listOf(a.copy(amount = a.amount + b.amount))
            else -> entries
        }
    }

    /** The Shopkeepers object settings in the key names of 1.x shop files (see [LegacyImporter.snapshotOf]). */
    private fun lookOf(section: ConfigurationSection): ConfigurationSection {
        val o = section.getConfigurationSection("object") ?: MemoryConfiguration()
        val out = MemoryConfiguration()
        if (o.contains("baby")) out.set("Adult", !o.getBoolean("baby"))
        if (o.getBoolean("powered", false)) out.set("Powered", true)
        (o.getString("profession") ?: o.getString("prof"))?.let { out.set("Profession", it.substringAfter(':').uppercase(Locale.ROOT)) }
        o.getString("villagerType")?.let { out.set("Biome", it.substringAfter(':').uppercase(Locale.ROOT)) }
        if (o.contains("villagerLevel")) out.set("Level", o.getInt("villagerLevel"))
        o.getString("color")?.let { out.set("Color", it.substringAfter(':').uppercase(Locale.ROOT)) }
        o.getString("style")?.let { out.set("Style", it.uppercase(Locale.ROOT)) }
        o.getString("parrotVariant")?.let { out.set("Color", it.uppercase(Locale.ROOT)) }
        o.getString("catType")?.let { out.set("CatType", it.substringAfter(':').uppercase(Locale.ROOT)) }
        o.getString("rabbitType")?.let { out.set("RabbitType", it.uppercase(Locale.ROOT)) }
        if (o.getBoolean("sitting", false)) out.set("Sitting", true)
        if (o.getBoolean("shaved", false) || o.getBoolean("angry", false)) out.set("OptionalInfo", true)
        if (o.contains("slimeSize")) out.set("Size", o.getInt("slimeSize"))
        return out
    }

    companion object {
        /** Shopkeepers' file where 1.x expected it. */
        fun fileIn(dataFolder: File) = File(dataFolder, "shops/save.yml")
    }
}
