package net.azisaba.sis.item

import net.azisaba.sis.item.mythic.CurrentMythicItemMatcher
import net.azisaba.sis.item.mythic.ItemNormalizer
import net.azisaba.sis.item.mythic.LegacyMythicItemMatcher
import net.azisaba.sis.item.mythic.MythicInstanceProvider
import net.azisaba.sis.item.mythic.VanillaMythicItems
import net.azisaba.sis.model.TradeEntry
import org.bukkit.inventory.ItemStack
import java.util.concurrent.ConcurrentHashMap

/**
 * Item rules shared by the whole plugin: how an item put into a shop is stored, and whether an item a player
 * carries is "the same" as a trade entry. These follow the rules of 1.x and of the other Azisaba plugins
 * (see [ItemNormalizer]).
 */
object Items {
    /** Set from config: MythicMobs items must also have the same texture/lore as what MythicMobs generates now. */
    @Volatile
    var mythicStrict: Boolean = true

    /** Set from config: MMIDs that are never stored by MMID. */
    @Volatile
    var isExcluded: (String) -> Boolean = { false }

    private val generated = ConcurrentHashMap<String, ItemStack>()

    /** Normalized forms of trade entry items (entries are immutable; keyed by identity). */
    private class Normalized(val item: ItemStack?)
    private val normalizedEntries: MutableMap<ItemStack, Normalized> =
        java.util.Collections.synchronizedMap(java.util.WeakHashMap<ItemStack, Normalized>())

    fun clearCache() {
        generated.clear()
        MythicInstanceProvider.invalidateCaches()
    }

    /** What MythicMobs generates for this MMID (a fresh copy, amount 1), or null when it does not exist. */
    fun mythicItem(id: String): ItemStack? {
        if (!MythicInstanceProvider.isLoaded()) return null
        val template = generated[id] ?: MythicInstanceProvider.getInstance().getMythicItem(id, 1)?.also { generated[id] = it } ?: return null
        return template.clone()
    }

    fun mythicId(item: ItemStack?): String? {
        if (item == null || item.type.isAir || !MythicInstanceProvider.isLoaded()) return null
        return MythicInstanceProvider.getInstance().getID(item)
    }

    /**
     * How an item put into a shop is kept (same rules as 1.x):
     * a MythicMobs item becomes an MMID entry, unless its MMID is excluded, its definition is a plain vanilla item,
     * or its lore differs from what MythicMobs generates now (Lv2 items etc. would lose their lore).
     * Everything else is kept as the item itself, normalized like the items players carry.
     */
    fun toEntry(item: ItemStack, amount: Int = item.amount): TradeEntry {
        VanillaMythicItems.revert(item)?.let { return TradeEntry.Item(ItemNormalizer.normalize(it).asOne(), amount) }
        resolveMythicId(item)?.let { return TradeEntry.Mythic(it, amount) }
        return TradeEntry.Item(ItemNormalizer.normalize(item).asOne(), amount)
    }

    private fun resolveMythicId(item: ItemStack): String? {
        if (!MythicInstanceProvider.isLoaded()) return null
        val id = mythicId(item) ?: LegacyMythicItemMatcher.findId(item) ?: CurrentMythicItemMatcher.findId(item) ?: return null
        if (id.isBlank() || isExcluded(id)) return null
        if (VanillaMythicItems.isVanillaDefinition(id)) return null
        if (!CurrentMythicItemMatcher.hasSameLoreAsCurrent(item, id)) return null
        return id
    }

    /** Explains [toEntry] for an item (for /sis mmid). */
    fun explain(item: ItemStack): List<String> {
        if (!MythicInstanceProvider.isLoaded()) return listOf("MythicMobs is not loaded")
        val lines = ArrayList<String>()
        val tagId = mythicId(item)
        lines += "1. MMID on the item: ${tagId ?: "none"}"
        val legacy = if (tagId == null) LegacyMythicItemMatcher.findId(item) else null
        if (tagId == null) lines += "2. legacy definitions: ${legacy ?: "no match"}"
        if (tagId == null && legacy == null) CurrentMythicItemMatcher.explain(item).forEach { lines += "3. current MythicMobs: $it" }
        lines += when (val entry = toEntry(item)) {
            is TradeEntry.Mythic -> "=> saved as MMID ${entry.id}"
            else -> "=> kept as an item"
        }
        return lines
    }

    /** Whether [stack] (carried by a player) counts as one of [entry]. */
    fun matches(entry: TradeEntry, stack: ItemStack?): Boolean {
        if (stack == null || stack.type.isAir) return false
        return when (entry) {
            is TradeEntry.Item -> {
                // Same result as ItemNormalizer.isSimilar, but the entry's normalized form is computed once
                // instead of for every slot of every check.
                val item = entry.item
                if (item.isSimilar(stack)) return true
                if (item.type != stack.type) return false
                // The value must not reference the key, or the weak map never drops it: "unchanged" is stored as null.
                val normalizedEntry = normalizedEntries.computeIfAbsent(item) { key ->
                    Normalized(ItemNormalizer.normalize(key).takeUnless { it === key })
                }.item ?: item
                val normalizedStack = ItemNormalizer.normalize(stack)
                (normalizedEntry !== item || normalizedStack !== stack) && normalizedEntry.isSimilar(normalizedStack)
            }
            is TradeEntry.Mythic -> {
                if (mythicId(stack) != entry.id) return false
                if (!mythicStrict) return true
                val current = mythicItem(entry.id) ?: return false
                ItemNormalizer.sameTextureAndLore(current, stack)
            }
            else -> false
        }
    }

    /** The real item an entry hands out (amount 1), or null when it cannot be made (unknown MMID). */
    fun template(entry: TradeEntry): ItemStack? = when (entry) {
        is TradeEntry.Item -> entry.item.clone()
        is TradeEntry.Mythic -> mythicItem(entry.id)
        else -> null
    }

    /** Whether the entry can be handed out / checked right now. */
    fun isValid(entry: TradeEntry): Boolean = when (entry) {
        is TradeEntry.Mythic -> mythicItem(entry.id) != null
        else -> true
    }

    /** Splits [amount] of [template] into stacks that respect its max stack size. */
    fun stacks(template: ItemStack, amount: Long): List<ItemStack> {
        val max = template.maxStackSize.coerceAtLeast(1)
        val result = ArrayList<ItemStack>()
        var left = amount
        while (left > 0) {
            val n = minOf(left, max.toLong()).toInt()
            result += template.asQuantity(n)
            left -= n
        }
        return result
    }
}
