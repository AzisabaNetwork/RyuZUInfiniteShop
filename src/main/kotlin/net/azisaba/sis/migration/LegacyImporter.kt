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
import net.azisaba.sis.migration.legacy.LegacyAttributeMigration
import net.azisaba.sis.migration.legacy.LegacyBackupMythicIdMigration
import net.azisaba.sis.migration.legacy.LegacyContainerMigration
import net.azisaba.sis.migration.legacy.LegacyLeatherColorMigration
import net.azisaba.sis.migration.legacy.LegacyMythicTypeMigration
import net.azisaba.sis.migration.legacy.LegacyShieldColorMigration
import net.azisaba.sis.migration.legacy.LegacySkullMigration
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
 * Files of any 1.x version are read, including ones still holding 1.15.2 items (`v: 2230`): the data Bukkit drops
 * when loading those is restored first (see [loadLegacy]).
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
                val original = file.readText(Charsets.UTF_8)
                if (!isLegacyShopFile(original)) return@runCatching report.skipped++
                val yaml = loadLegacy(original, shopDir, file.name)
                val shop = convert(oldId, yaml, options)
                val target = File(sis.repository.shopDir, "${shop.id}.yml")
                if (target.exists() && !overwrite) return@runCatching report.skipped++
                sis.repository.saveBlocking(shop)
                report.shops++
                report.trades += shop.trades.size
            }.onFailure { report.failed[oldId] = it.message ?: it.toString() }
        }
        // Counts of every trade in options.yml (trade IDs are kept), also of shops skipped because they exist:
        // importing the same counts again just sets the same values.
        options.forEach { (trade, option) -> option.counts.forEach { (player, count) -> counts += Triple(player, trade, count) } }
        if (counts.isNotEmpty()) {
            val pending = File(folder, COUNTS_PENDING)
            pending.writeText("Trade counts of options.yml were not imported yet; they are retried on the next start.\n")
            sis.counter.importLegacyBlocking(counts)
            pending.delete()
            report.counts = counts.size
        }
        return report
    }

    /**
     * A 1.x shop file as YAML, with what Bukkit would lose on loading put back first: 1.15.2 items (MYTHIC_TYPE,
     * heads, attributes, leather/shield colours) and items of files already loaded once by 1.21 (taken from the
     * 1.15.2 backups in `shops/backup/`). Same steps, in the same order, as 1.x's own `/sis migrate`.
     */
    private fun loadLegacy(original: String, shopDir: File, fileName: String): YamlConfiguration {
        var text = original
        val version = rawDataVersion(text)
        if (version < LegacyMythicTypeMigration.TO_VERSION) text = LegacyMythicTypeMigration.fixRawYaml(text)
        if (version < LegacySkullMigration.TO_VERSION) text = LegacySkullMigration.fixRawYaml(text)
        if (version < LegacyAttributeMigration.TO_VERSION) text = LegacyAttributeMigration.fixRawYaml(text)
        if (version < LegacyLeatherColorMigration.TO_VERSION) text = LegacyLeatherColorMigration.fixRawYaml(text)
        if (version < LegacyShieldColorMigration.TO_VERSION) text = LegacyShieldColorMigration.fixRawYaml(text)
        val yaml = YamlConfiguration().apply { loadFromString(text) }
        if (version < LegacyBackupMythicIdMigration.TO_VERSION) LegacyBackupMythicIdMigration.restore(yaml, shopDir, fileName)
        if (version < LegacyLeatherColorMigration.TO_VERSION) LegacyLeatherColorMigration.restore(yaml, shopDir, fileName)
        if (version < LegacyShieldColorMigration.TO_VERSION) LegacyShieldColorMigration.restore(yaml, shopDir, fileName)
        if (version < LegacyContainerMigration.TO_VERSION) LegacyContainerMigration.restore(yaml, original, shopDir, fileName)
        return yaml
    }

    private fun rawDataVersion(text: String): Int =
        Regex("(?m)^data-version:\\s*(\\d+)").find(text)?.groupValues?.get(1)?.toInt() ?: 0

    /**
     * On the first start of 2.x in a 1.x data folder: moves the 1.x files to `legacy-1.x/` and imports them.
     * Returns null when there is nothing to migrate.
     */
    fun autoMigrate(dataFolder: File): Report? {
        val shopDir = File(dataFolder, "shops")
        val legacyFiles = shopDir.listFiles { f -> f.isFile && f.name.endsWith(".yml") }
            ?.filter { runCatching { isLegacyShopFile(it.readText(Charsets.UTF_8)) }.getOrDefault(false) }
            .orEmpty()
        val legacy = File(dataFolder, "legacy-1.x")
        if (legacyFiles.isEmpty()) {
            // Moved last time, but the counts could not be written: try those again.
            return if (File(legacy, COUNTS_PENDING).exists()) import(legacy, overwrite = false) else null
        }
        legacy.mkdirs()
        val legacyShops = File(legacy, "shops").apply { mkdirs() }
        legacyFiles.forEach { move(it, File(legacyShops, it.name)) }
        // 1.x keeps the 1.15.2 copies of shop files there; they are needed to restore lost MythicMobs IDs.
        File(shopDir, "backup").takeIf { it.isDirectory }?.let { move(it, File(legacyShops, "backup")) }
        for (name in listOf("options.yml", "limits.yml", "sign.yml", "panel.yml", "log.csv", "itemized_shops", "shop-items", "backups")) {
            val f = File(dataFolder, name)
            if (f.exists()) move(f, File(legacy, name))
        }
        return import(legacy, overwrite = false)
    }

    /** Moves a file or folder; fails loudly instead of leaving a half-moved data folder behind unnoticed. */
    private fun move(from: File, to: File) {
        if (to.exists()) error("cannot move ${from.name} to ${to.path}: it already exists")
        java.nio.file.Files.move(from.toPath(), to.toPath())
    }

    companion object {
        /** Moves a 1.x config.yml aside so the 2.x default can be written. Call before saveDefaultConfig(). */
        fun moveLegacyConfig(dataFolder: File): Boolean {
            val config = File(dataFolder, "config.yml")
            if (!config.exists() || !config.readText(Charsets.UTF_8).contains("AutoSaveInterval")) return false
            val legacy = File(dataFolder, "legacy-1.x").apply { mkdirs() }
            if (!config.renameTo(File(legacy, "config.yml"))) return false
            File(dataFolder, "languages").takeIf { it.exists() }?.renameTo(File(legacy, "languages"))
            return true
        }

        private const val COUNTS_PENDING = "counts-pending.txt"
    }

    /**
     * Shops migrated by a 2.0 build that did not know `invisible` yet: their files have no such line.
     * Takes the flag from the 1.x file in `legacy-1.x/shops/` and appends it to the 2.x file, leaving everything
     * else (edits made since the migration) as it is. Runs once: afterwards the files have the line.
     *
     * @return the IDs of the shops whose NPC became invisible
     */
    fun backfillFlags(dataFolder: File): List<String> {
        val legacyDir = File(dataFolder, "legacy-1.x/shops")
        val shopDir = File(dataFolder, "shops")
        val updated = ArrayList<String>()
        val invisibleLine = Regex("^\\s+Invisible:\\s*true\\s*$", RegexOption.MULTILINE)
        legacyDir.listFiles { f -> f.isFile && f.name.endsWith(".yml") }?.sortedBy { it.name }?.forEach { legacy ->
            val id = newId(legacy.name.removeSuffix(".yml")) ?: return@forEach
            val target = File(shopDir, "$id.yml")
            if (!target.isFile) return@forEach
            val text = target.readText(Charsets.UTF_8)
            if (text.lineSequence().any { it.startsWith("invisible:") }) return@forEach
            val invisible = invisibleLine.containsMatchIn(legacy.readText(Charsets.UTF_8))
            target.appendText((if (text.endsWith("\n")) "" else "\n") + "invisible: $invisible\n", Charsets.UTF_8)
            if (invisible) updated += id
        }
        return updated
    }

    /** The 2.x ID of a 1.x file name `<world>,<x>,<y>,<z>`, or null when it is not one. */
    private fun newId(oldId: String): String? {
        val parts = oldId.split(',')
        if (parts.size < 4) return null
        val coords = parts.takeLast(3).map { it.toDoubleOrNull() ?: return null }
        val world = parts.dropLast(3).joinToString(",")
        return "${world}_${coords[0].toInt()}_${coords[1].toInt()}_${coords[2].toInt()}".replace(Regex("[^A-Za-z0-9_\\-]"), "_")
    }

    private fun isLegacyShopFile(text: String): Boolean =
        text.lineSequence().any { it.startsWith("Npc:") } && text.lineSequence().none { it.startsWith("format:") }

    /** Lets Bukkit read the 1.x classes in YAML (MythicItem, TradeOption). */
    fun registerAliases() {
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
            invisible = yaml.getBoolean("Npc.Options.Invisible", false),
        )
    }

    /**
     * A shop from the data of a 1.x shop item ("compression gem": its shop file without trades), placed at [at]
     * under [id] with [trades].
     */
    fun shopFromGem(shopData: String, id: String, at: ShopLocation, trades: List<Trade>): Shop {
        registerAliases()
        val yaml = YamlConfiguration().apply { loadFromString(shopData) }
        val shop = convert("${at.world},${at.blockX},${at.blockY},${at.blockZ}", yaml, emptyMap())
        return Shop(id, shop.name, at, shop.npc, trades.toMutableList(), shop.locked, shop.searchable, shop.nameVisible, shop.lookAtPlayer)
    }

    /** A trade of a 1.x shop item (`give` / `take` lists, optional `uuid`), with a fresh ID. */
    fun tradeFromGem(map: Map<*, *>): Trade? {
        registerAliases()
        return convertTrade(map, emptyMap())?.copy(id = UUID.randomUUID())
    }

    /** Old item lists as YAML text (`ShopItemStacks: [...]`, the oldest shop item format). */
    fun itemListFromGem(yamlText: String): List<Any?> {
        registerAliases()
        return YamlConfiguration().apply { loadFromString(yamlText) }.getList("ShopItemStacks").orEmpty()
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
            chance = option?.rate?.coerceIn(0, 100) ?: 100,
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
            val profession = (o.getString("Profession") ?: o.getString("prof") ?: "NONE").lowercase(Locale.ROOT)
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
            EntityType.SHEEP -> {
                dye(o.getString("Color"))?.let { tags += "Color:${it}b" }
                if (o.getBoolean("OptionalInfo", false)) tags += "Sheared:1b"
            }
            EntityType.WOLF -> dye(o.getString("Color"))?.let { tags += "CollarColor:${it}b" }
            EntityType.SHULKER -> dye(o.getString("Color"))?.let { tags += "Color:${it}b" }
            EntityType.SNOW_GOLEM -> if (o.getBoolean("Derp", false)) tags += "Pumpkin:0b"
            EntityType.AXOLOTL -> {
                val variants = listOf("LUCY", "WILD", "GOLD", "CYAN", "BLUE")
                tags += "Variant:${variants.indexOf(o.getString("Color", "LUCY")!!.uppercase(Locale.ROOT)).coerceAtLeast(0)}"
            }
            EntityType.TROPICAL_FISH -> {
                // Variant = size | pattern << 8 | body colour << 16 | pattern colour << 24 (patterns 0-5 per size)
                val patterns = listOf("KOB", "SUNSTREAK", "SNOOPER", "DASHER", "BRINELY", "SPOTTY", "FLOPPER", "STRIPEY", "GLITTER", "BLOCKFISH", "BETTY", "CLAYFISH")
                val p = patterns.indexOf(o.getString("Pattern", "KOB")!!.uppercase(Locale.ROOT)).coerceAtLeast(0)
                val body = dye(o.getString("BodyColor", "RED")) ?: DyeColor.RED.ordinal
                val patternColor = dye(o.getString("PatternColor", "RED")) ?: DyeColor.RED.ordinal
                tags += "Variant:${(p / 6) or ((p % 6) shl 8) or (body shl 16) or (patternColor shl 24)}"
            }
            else -> Unit
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
