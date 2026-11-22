package net.azisaba.sis.migration

import net.azisaba.sis.Sis
import net.azisaba.sis.item.Items
import net.azisaba.sis.item.mythic.ItemNormalizer
import net.azisaba.sis.model.NpcSpec
import net.azisaba.sis.model.Shop
import net.azisaba.sis.model.ShopLocation
import net.azisaba.sis.model.Trade
import net.azisaba.sis.model.TradeEntry
import net.azisaba.sis.model.TradeOptions
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.DyeColor
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.configuration.serialization.ConfigurationSerializable
import org.bukkit.configuration.serialization.ConfigurationSerialization
import org.bukkit.entity.EntityType
import org.bukkit.inventory.ItemStack
import java.io.File
import java.util.Locale
import java.util.UUID

/** 1.x `MythicItem` as found in old YAML (`==: ryuzuinfiniteshop...MythicItem`). */
class LegacyMythicItem(val id: String, val amount: Int) : ConfigurationSerializable {
    override fun serialize(): Map<String, Any> = mapOf("id" to id, "amount" to amount)

    companion object {
        @JvmStatic
        fun deserialize(map: Map<String, Any?>): LegacyMythicItem =
            LegacyMythicItem(map["id"].toString(), (map["amount"] as? Number)?.toInt() ?: 1)
    }
}

/** 1.x `TradeOption` (`==: ryuzuinfiniteshop...TradeOption`). */
class LegacyTradeOption(val give: Boolean, val money: Double, val limit: Int, val hide: Boolean, val rate: Int) : ConfigurationSerializable {
    override fun serialize(): Map<String, Any> = mapOf("give" to give, "money" to money, "limit" to limit, "hide" to hide, "rate" to rate)

    companion object {
        @JvmStatic
        fun deserialize(map: Map<String, Any?>): LegacyTradeOption = LegacyTradeOption(
            map["give"] as? Boolean ?: false,
            (map["money"] as? Number)?.toDouble() ?: 0.0,
            (map["limit"] as? Number)?.toInt() ?: 0,
            map["hide"] as? Boolean ?: false,
            (map["rate"] as? Number)?.toInt() ?: 100,
        )
    }
}

/**
 * Reads the data of SearchableInfiniteShop 1.x (`shops/<world>,<x>,<y>,<z>.yml`, `options.yml`) and writes 2.x
 * shop files and trade counts. Trade IDs are kept, so counts and limits carry over.
 *
 * 1.x files must already be on the 1.21.11 item format (DataVersion 4xxx, i.e. migrated by 1.1.x with
 * `/sis migrate`); files still holding 1.15.2 items (`v: 2230`) are skipped and listed.
 */
class LegacyImporter(private val sis: Sis) {
    data class Report(
        var shops: Int = 0,
        var trades: Int = 0,
        var skipped: Int = 0,
        var counts: Int = 0,
        val failed: MutableMap<String, String> = LinkedHashMap(),
    )

    private class LegacyOption(val option: LegacyTradeOption?, val counts: Map<UUID, Int>)

    fun import(folder: File, overwrite: Boolean): Report {
        registerAliases()
        val report = Report()
        val options = readOptions(File(folder, "options.yml"))
        val shopDir = File(folder, "shops")
        val counts = ArrayList<Triple<UUID, UUID, Int>>()
        shopDir.listFiles { f -> f.isFile && f.name.endsWith(".yml") }?.sortedBy { it.name }?.forEach { file ->
            val oldId = file.name.removeSuffix(".yml")
            runCatching {
                val text = file.readText(Charsets.UTF_8)
                if (!isLegacyShopFile(text)) return@runCatching report.skipped++
                if (LEGACY_ITEM.containsMatchIn(text)) error("still has 1.15.2 items (v: 2230); run /sis migrate on 1.1.x first")
                val yaml = YamlConfiguration().apply { loadFromString(text) }
                val shop = convert(oldId, yaml, options)
                val target = File(sis.repository.shopDir, "${shop.id}.yml")
                if (target.exists() && !overwrite) return@runCatching report.skipped++
                sis.repository.saveBlocking(shop)
                report.shops++
                report.trades += shop.trades.size
                for (trade in shop.trades) {
                    options[trade.id]?.counts?.forEach { (player, count) -> counts += Triple(player, trade.id, count) }
                }
            }.onFailure { report.failed[oldId] = it.message ?: it.toString() }
        }
        if (counts.isNotEmpty()) {
            sis.counter.importLegacyBlocking(counts)
            report.counts = counts.size
        }
        return report
    }

    /**
     * On the first start of 2.x in a 1.x data folder: moves the 1.x files to `legacy-1.x/` and imports them.
     * Returns null when there is nothing to migrate.
     */
    fun autoMigrate(dataFolder: File): Report? {
        val shopDir = File(dataFolder, "shops")
        val legacyFiles = shopDir.listFiles { f -> f.isFile && f.name.endsWith(".yml") }
            ?.filter { runCatching { isLegacyShopFile(it.readText(Charsets.UTF_8)) }.getOrDefault(false) }
            .orEmpty()
        if (legacyFiles.isEmpty()) return null
        val legacy = File(dataFolder, "legacy-1.x").apply { mkdirs() }
        val legacyShops = File(legacy, "shops").apply { mkdirs() }
        legacyFiles.forEach { it.renameTo(File(legacyShops, it.name)) }
        for (name in listOf("options.yml", "limits.yml", "sign.yml", "panel.yml", "log.csv", "itemized_shops", "shop-items", "backups")) {
            val f = File(dataFolder, name)
            if (f.exists()) f.renameTo(File(legacy, name))
        }
        return import(legacy, overwrite = false)
    }

    companion object {
        /** Moves a 1.x config.yml aside so the 2.x default can be written. Call before saveDefaultConfig(). */
        fun moveLegacyConfig(dataFolder: File) {
            val config = File(dataFolder, "config.yml")
            if (!config.exists() || !config.readText(Charsets.UTF_8).contains("AutoSaveInterval")) return
            val legacy = File(dataFolder, "legacy-1.x").apply { mkdirs() }
            config.renameTo(File(legacy, "config.yml"))
            File(dataFolder, "languages").takeIf { it.exists() }?.renameTo(File(legacy, "languages"))
        }

        /** An item saved by 1.15.2 Bukkit (`v: 2230`). */
        private val LEGACY_ITEM = Regex("^\\s+v: (1\\d{3}|2\\d{3})\\s*$", RegexOption.MULTILINE)
    }

    private fun isLegacyShopFile(text: String): Boolean =
        text.lineSequence().any { it.startsWith("Npc:") } && text.lineSequence().none { it.startsWith("format:") }

    private fun registerAliases() {
        ConfigurationSerialization.registerClass(LegacyMythicItem::class.java, "ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.item.MythicItem")
        ConfigurationSerialization.registerClass(LegacyTradeOption::class.java, "ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.TradeOption")
    }

    private fun readOptions(file: File): Map<UUID, LegacyOption> {
        if (!file.exists() || file.length() == 0L) return emptyMap()
        val yaml = YamlConfiguration.loadConfiguration(file)
        val result = HashMap<UUID, LegacyOption>()
        for (key in yaml.getKeys(false)) {
            val id = runCatching { UUID.fromString(key) }.getOrNull() ?: continue
            val section = yaml.getConfigurationSection(key) ?: continue
            val option = section.get("options") as? LegacyTradeOption
            val counts = section.getConfigurationSection("counts")?.let { c ->
                c.getKeys(false).mapNotNull { p -> runCatching { UUID.fromString(p) to c.getInt(p) }.getOrNull() }.toMap()
            }.orEmpty()
            result[id] = LegacyOption(option, counts)
        }
        return result
    }

    private fun convert(oldId: String, yaml: ConfigurationSection, options: Map<UUID, LegacyOption>): Shop {
        val parts = oldId.split(',')
        require(parts.size >= 4) { "file name is not <world>,<x>,<y>,<z>" }
        val world = parts.dropLast(3).joinToString(",")
        val (x, y, z) = parts.takeLast(3).map { it.toDouble() }
        val yaw = yaml.getDouble("Npc.Status.Yaw", 0.0).toFloat()
        val location = ShopLocation(world, Math.floor(x) + 0.5, y, Math.floor(z) + 0.5, yaw, 0f)
        val id = "${world}_${x.toInt()}_${y.toInt()}_${z.toInt()}".replace(Regex("[^A-Za-z0-9_\\-]"), "_")

        val trades = yaml.getList("Trades").orEmpty().mapNotNull { raw ->
            val map = raw as? Map<*, *> ?: (raw as? ConfigurationSection)?.getValues(false) ?: return@mapNotNull null
            convertTrade(map, options)
        }
        return Shop(
            id = id,
            name = legacyName(yaml.getString("Npc.Options.DisplayName").orEmpty()),
            location = location,
            npc = convertNpc(yaml),
            trades = trades.toMutableList(),
            locked = yaml.getBoolean("Npc.Status.Lock", false),
            searchable = yaml.getBoolean("Npc.Status.Searchable", true),
            nameVisible = true,
            lookAtPlayer = false,
        )
    }

    private fun legacyName(text: String): String {
        if (text.isBlank()) return ""
        val component = LegacyComponentSerializer.legacySection().deserialize(text)
        return MiniMessage.miniMessage().serialize(component)
    }

    private fun convertTrade(map: Map<*, *>, options: Map<UUID, LegacyOption>): Trade? {
        val id = (map["uuid"] as? String)?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: UUID.randomUUID()
        val costs = entries(map["take"]).toMutableList()
        val rewards = entries(map["give"]).toMutableList()
        val option = options[id]?.option
        if (option != null && option.money > 0) (if (option.give) rewards else costs) += TradeEntry.Money(option.money)
        if (costs.isEmpty() && rewards.isEmpty()) return null
        val tradeOptions = TradeOptions(
            limit = option?.limit?.coerceAtLeast(0) ?: 0,
            chance = option?.rate?.coerceIn(1, 100) ?: 100,
            hideChance = option?.hide ?: false,
        )
        return Trade(id, costs, rewards, tradeOptions)
    }

    /** Old item lists: ItemStacks and MythicItems, one per slot; equal items are merged into one entry. */
    private fun entries(raw: Any?): List<TradeEntry> {
        val result = ArrayList<TradeEntry>()
        for (obj in (raw as? List<*>).orEmpty()) {
            val entry: TradeEntry = when (obj) {
                is ItemStack -> if (obj.type.isAir) continue else Items.toEntry(obj, obj.amount)
                is LegacyMythicItem -> if (obj.amount <= 0) continue else TradeEntry.Mythic(obj.id, obj.amount)
                else -> continue
            }
            val same = result.indexOfFirst { existing ->
                (existing is TradeEntry.Item && entry is TradeEntry.Item && existing.item.isSimilar(entry.item)) ||
                    (existing is TradeEntry.Mythic && entry is TradeEntry.Mythic && existing.id == entry.id)
            }
            if (same < 0) result += entry else result[same] = when (val e = result[same]) {
                is TradeEntry.Item -> e.copy(amount = e.amount + (entry as TradeEntry.Item).amount)
                is TradeEntry.Mythic -> e.copy(amount = e.amount + (entry as TradeEntry.Mythic).amount)
                else -> e
            }
        }
        return result
    }

    private fun convertNpc(yaml: ConfigurationSection): NpcSpec {
        yaml.getString("Npc.Options.MythicMob")?.takeIf { it.isNotBlank() }?.let { return NpcSpec.Mythic(it) }
        yaml.getString("Npc.Options.Citizen")?.takeIf { it.isNotBlank() }?.let { return NpcSpec.Citizens(it) }
        val typeName = yaml.getString("Npc.Options.EntityType", "VILLAGER")!!.uppercase(Locale.ROOT)
        if (typeName == "BLOCK") return NpcSpec.Block
        val type = entityType(typeName) ?: EntityType.VILLAGER
        val old = yaml.getList("Npc.Options.Equipments").orEmpty().map { (it as? ItemStack)?.takeUnless { item -> item.type.isAir }?.let(ItemNormalizer::normalize) }
        // 1.x stored equipment by EquipmentSlot ordinal: hand, off hand, feet, legs, chest, head
        val equipment = if (old.all { it == null }) null else listOf(old.getOrNull(5), old.getOrNull(4), old.getOrNull(3), old.getOrNull(2), old.getOrNull(0), old.getOrNull(1))
        val snbt = snapshotOf(type, yaml.getConfigurationSection("Npc.Options")!!)
        val snapshot = snbt?.takeIf { s -> runCatching { Bukkit.getEntityFactory().createEntitySnapshot(s) }.isSuccess }
        return NpcSpec.Vanilla(type, snapshot, equipment)
    }

    private fun entityType(name: String): EntityType? {
        val key = when (name) {
            "MUSHROOM_COW" -> "mooshroom"
            "SNOWMAN" -> "snow_golem"
            "PIG_ZOMBIE" -> "zombified_piglin"
            else -> name.lowercase(Locale.ROOT)
        }
        return Registry.ENTITY_TYPE.get(NamespacedKey.minecraft(key))
    }

    /** An SNBT entity snapshot with the look 1.x kept per entity type. Best effort; unknown keys are ignored. */
    fun snapshotOf(type: EntityType, o: ConfigurationSection): String? {
        val tags = ArrayList<String>()
        tags += "id:\"${type.key}\""
        val zombieLike = type in setOf(EntityType.ZOMBIE, EntityType.HUSK, EntityType.DROWNED, EntityType.ZOMBIE_VILLAGER, EntityType.ZOMBIFIED_PIGLIN, EntityType.PIGLIN)
        if (o.contains("Adult") && !o.getBoolean("Adult", true)) tags += if (zombieLike) "IsBaby:1b" else "Age:-24000"
        if (type == EntityType.VILLAGER || type == EntityType.ZOMBIE_VILLAGER) {
            val profession = o.getString("Profession", "NONE")!!.lowercase(Locale.ROOT)
            val biome = villagerType(o.getString("Biome", "PLAINS")!!)
            val level = o.getInt("Level", 1).coerceIn(1, 5)
            tags += "VillagerData:{profession:\"minecraft:$profession\",type:\"minecraft:$biome\",level:$level}"
        }
        if (o.getBoolean("Powered", false)) tags += "powered:1b"
        if (o.contains("Size")) tags += "Size:${(o.getInt("Size", 1) - 1).coerceAtLeast(0)}"
        if (o.getBoolean("Sitting", false)) tags += "Sitting:1b"
        when (type) {
            EntityType.CAT -> o.getString("CatType")?.let { tags += "variant:\"minecraft:${it.lowercase(Locale.ROOT)}\"" }
            EntityType.HORSE -> {
                val colors = listOf("WHITE", "CREAMY", "CHESTNUT", "BROWN", "BLACK", "GRAY", "DARK_BROWN")
                val styles = listOf("NONE", "WHITE", "WHITEFIELD", "WHITE_DOTS", "BLACK_DOTS")
                val c = colors.indexOf(o.getString("Color", "WHITE")!!.uppercase(Locale.ROOT)).coerceAtLeast(0)
                val s = styles.indexOf(o.getString("Style", "NONE")!!.uppercase(Locale.ROOT)).coerceAtLeast(0)
                tags += "Variant:${c or (s shl 8)}"
            }
            EntityType.RABBIT -> {
                val types = mapOf("BROWN" to 0, "WHITE" to 1, "BLACK" to 2, "BLACK_AND_WHITE" to 3, "GOLD" to 4, "SALT_AND_PEPPER" to 5, "THE_KILLER_BUNNY" to 99)
                tags += "RabbitType:${types[o.getString("RabbitType", "BROWN")!!.uppercase(Locale.ROOT)] ?: 0}"
            }
            EntityType.PARROT -> {
                val variants = listOf("RED", "BLUE", "GREEN", "CYAN", "GRAY")
                tags += "Variant:${variants.indexOf(o.getString("Color", "RED")!!.uppercase(Locale.ROOT)).coerceAtLeast(0)}"
            }
            EntityType.SHEEP -> dye(o.getString("Color"))?.let { tags += "Color:${it}b" }
            EntityType.WOLF -> dye(o.getString("Color"))?.let { tags += "CollarColor:${it}b" }
            else -> Unit
        }
        if (o.getBoolean("Invisible", false)) {
            tags += "active_effects:[{id:\"minecraft:invisibility\",amplifier:0b,duration:-1,show_particles:0b,show_icon:0b}]"
        }
        return "{${tags.joinToString(",")}}"
    }

    private fun dye(name: String?): Int? = name?.let { n -> DyeColor.entries.firstOrNull { it.name.equals(n, true) }?.ordinal ?: n.toIntOrNull() }

    private fun villagerType(biome: String): String {
        val b = biome.uppercase(Locale.ROOT)
        return when {
            "SNOW" in b || "ICE" in b || "FROZEN" in b -> "snow"
            "DESERT" in b -> "desert"
            "JUNGLE" in b -> "jungle"
            "SAVANNA" in b -> "savanna"
            "SWAMP" in b -> "swamp"
            "TAIGA" in b -> "taiga"
            else -> "plains"
        }
    }
}
