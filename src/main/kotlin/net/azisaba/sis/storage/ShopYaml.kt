package net.azisaba.sis.storage

import net.azisaba.sis.model.LimitScope
import net.azisaba.sis.model.NpcSpec
import net.azisaba.sis.model.ResetPeriod
import net.azisaba.sis.model.Shop
import net.azisaba.sis.model.ShopLocation
import net.azisaba.sis.model.Trade
import net.azisaba.sis.model.TradeEntry
import net.azisaba.sis.model.TradeOptions
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.EntityType
import org.bukkit.inventory.ItemStack
import java.util.Locale
import java.util.UUID

/**
 * Shop <-> YAML. One file per shop, readable by hand:
 *
 * ```yaml
 * format: 2
 * name: "<green>Weapon shop"
 * location: { world: world, x: 0.5, y: 64.0, z: 0.5, yaw: 90.0, pitch: 0.0 }
 * npc: { kind: entity, type: VILLAGER, snapshot: "..." }
 * trades:
 * - id: 2f0c...
 *   costs:
 *   - { type: item, amount: 256, item: <ItemStack> }
 *   - { type: money, amount: 100.0 }
 *   rewards:
 *   - { type: mythic, id: cl7_sword, amount: 1 }
 *   limit: { max: 1, scope: player, reset: daily }
 * ```
 */
object ShopYaml {
    const val FORMAT = 2

    fun toYaml(shop: Shop): YamlConfiguration {
        val yaml = YamlConfiguration()
        write(shop, yaml)
        return yaml
    }

    fun write(shop: Shop, out: ConfigurationSection) {
        out.set("format", FORMAT)
        out.set("name", shop.name)
        out.createSection("location").apply {
            set("world", shop.location.world)
            set("x", shop.location.x)
            set("y", shop.location.y)
            set("z", shop.location.z)
            set("yaw", shop.location.yaw.toDouble())
            set("pitch", shop.location.pitch.toDouble())
        }
        out.createSection("npc").apply {
            when (val npc = shop.npc) {
                is NpcSpec.Vanilla -> {
                    set("kind", "entity"); set("type", npc.type.name)
                    if (npc.snapshot != null) set("snapshot", npc.snapshot)
                    if (npc.equipment != null) set("equipment", npc.equipment.map { it ?: ItemStack.empty() })
                }
                is NpcSpec.Mythic -> { set("kind", "mythic"); set("mob", npc.mobId) }
                is NpcSpec.Citizens -> { set("kind", "citizens"); set("id", npc.ref) }
                NpcSpec.Block -> set("kind", "block")
            }
        }
        out.set("locked", shop.locked)
        out.set("searchable", shop.searchable)
        out.set("name-visible", shop.nameVisible)
        out.set("look-at-player", shop.lookAtPlayer)
        out.set("invisible", shop.invisible)
        out.set("trades", shop.trades.map(::tradeToMap))
    }

    fun tradeToMap(trade: Trade): Map<String, Any> {
        val map = LinkedHashMap<String, Any>()
        map["id"] = trade.id.toString()
        map["costs"] = trade.costs.map(::entryToMap)
        map["rewards"] = trade.rewards.map(::entryToMap)
        val o = trade.options
        if (o.hasLimit) map["limit"] = linkedMapOf(
            "max" to o.limit,
            "scope" to o.limitScope.name.lowercase(),
            "reset" to o.reset.name.lowercase(),
        )
        if (o.chance != 100) map["chance"] = o.chance
        if (o.hideChance) map["hide-chance"] = true
        if (o.permission != null) map["permission"] = o.permission
        return map
    }

    fun entryToMap(entry: TradeEntry): Map<String, Any> = when (entry) {
        is TradeEntry.Item -> linkedMapOf("type" to "item", "amount" to entry.amount, "item" to entry.item.asOne())
        is TradeEntry.Mythic -> linkedMapOf("type" to "mythic", "id" to entry.id, "amount" to entry.amount)
        is TradeEntry.Money -> linkedMapOf("type" to "money", "amount" to entry.amount)
        is TradeEntry.Command -> LinkedHashMap<String, Any>().apply {
            put("type", "command"); put("command", entry.command); put("description", entry.description)
            entry.display?.let { put("display", it.asOne()) }
        }
    }

    fun read(id: String, yaml: ConfigurationSection): Shop {
        val loc = yaml.getConfigurationSection("location") ?: error("shop $id has no location")
        val location = ShopLocation(
            loc.getString("world") ?: error("shop $id has no world"),
            loc.getDouble("x"), loc.getDouble("y"), loc.getDouble("z"),
            loc.getDouble("yaw").toFloat(), loc.getDouble("pitch").toFloat(),
        )
        val npcSection = yaml.getConfigurationSection("npc")
        val npc = when (npcSection?.getString("kind")) {
            "mythic" -> NpcSpec.Mythic(npcSection.getString("mob") ?: error("shop $id: npc.mob missing"))
            "citizens" -> NpcSpec.Citizens(npcSection.getString("id") ?: error("shop $id: npc.id missing"))
            "block" -> NpcSpec.Block
            else -> NpcSpec.Vanilla(
                npcSection?.getString("type")?.let { runCatching { EntityType.valueOf(it.uppercase(Locale.ROOT)) }.getOrNull() } ?: EntityType.VILLAGER,
                npcSection?.getString("snapshot"),
                npcSection?.getList("equipment")?.map { (it as? ItemStack)?.takeUnless { item -> item.type.isAir } },
            )
        }
        val trades = yaml.getMapList("trades").mapIndexed { index, raw ->
            runCatching { tradeFromMap(raw) }.getOrElse { throw IllegalArgumentException("shop $id trade #${index + 1}: ${it.message}", it) }
        }
        return Shop(
            id = id,
            name = yaml.getString("name", "")!!,
            location = location,
            npc = npc,
            trades = trades.toMutableList(),
            locked = yaml.getBoolean("locked", false),
            searchable = yaml.getBoolean("searchable", true),
            nameVisible = yaml.getBoolean("name-visible", true),
            lookAtPlayer = yaml.getBoolean("look-at-player", false),
            invisible = yaml.getBoolean("invisible", false),
        )
    }

    /**
     * Reads a trade from a plain map. Maps inside YAML lists stay plain maps (Bukkit only turns top-level maps into
     * sections), so nested values are read from the maps directly.
     */
    fun tradeFromMap(raw: Map<*, *>): Trade {
        val limit = raw["limit"] as? Map<*, *> ?: (raw["limit"] as? ConfigurationSection)?.getValues(false)
        fun int(map: Map<*, *>?, key: String, default: Int) = (map?.get(key) as? Number)?.toInt() ?: map?.get(key)?.toString()?.toIntOrNull() ?: default
        val options = TradeOptions(
            limit = int(limit, "max", 0).coerceAtLeast(0),
            limitScope = enumOr(limit?.get("scope")?.toString(), LimitScope.PLAYER),
            reset = enumOr(limit?.get("reset")?.toString(), ResetPeriod.NEVER),
            chance = int(raw, "chance", 100).coerceIn(0, 100),
            hideChance = raw["hide-chance"] as? Boolean ?: false,
            permission = raw["permission"]?.toString()?.takeIf { it.isNotBlank() },
        )
        return Trade(
            id = raw["id"]?.toString()?.let(UUID::fromString) ?: UUID.randomUUID(),
            costs = entries(raw["costs"], "cost"),
            rewards = entries(raw["rewards"], "reward"),
            options = options,
        )
    }

    /**
     * Reads one cost or reward. An entry that cannot be read is an error, not skipped: a trade that silently lost a
     * cost would hand out its rewards for free (and the next save would make that permanent).
     */
    fun entryFromMap(raw: Map<*, *>): TradeEntry {
        fun amount(): Int = (raw["amount"] as? Number)?.toInt() ?: 1
        val type = raw["type"]?.toString()
        return when (type) {
            "item" -> (raw["item"] as? ItemStack)?.takeUnless { it.type.isAir }?.let { TradeEntry.Item(it.asOne(), amount()) }
                ?: throw IllegalArgumentException("item entry without a readable item (${raw["item"]?.javaClass?.simpleName ?: "missing"})")
            "mythic" -> raw["id"]?.toString()?.takeIf { it.isNotBlank() }?.let { TradeEntry.Mythic(it, amount()) }
                ?: throw IllegalArgumentException("mythic entry without an id")
            "money" -> (raw["amount"] as? Number)?.toDouble()?.takeIf { it > 0 }?.let { TradeEntry.Money(it) }
                ?: throw IllegalArgumentException("money entry without a positive amount")
            "command" -> raw["command"]?.toString()?.let {
                TradeEntry.Command(it, raw["description"]?.toString() ?: "", raw["display"] as? ItemStack)
            } ?: throw IllegalArgumentException("command entry without a command")
            else -> throw IllegalArgumentException("unknown entry type '$type'")
        }
    }

    private fun entries(raw: Any?, what: String): List<TradeEntry> = (raw as? List<*>).orEmpty().mapIndexed { i, e ->
        val map = e as? Map<*, *> ?: (e as? ConfigurationSection)?.getValues(false)
            ?: throw IllegalArgumentException("$what #${i + 1} is not an entry")
        runCatching { entryFromMap(map) }.getOrElse { throw IllegalArgumentException("$what #${i + 1}: ${it.message}", it) }
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name.equals(n, ignoreCase = true) } } ?: default
}
