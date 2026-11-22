package net.azisaba.sis.service

import org.bukkit.inventory.ItemStack

/**
 * Inventory arithmetic on a copy of the contents, so a trade can be checked (and applied) all at once without
 * touching the real inventory until everything fits.
 */
object InventorySim {
    fun copy(contents: Array<ItemStack?>): Array<ItemStack?> = Array(contents.size) { contents[it]?.clone() }

    fun count(contents: Array<ItemStack?>, matches: (ItemStack) -> Boolean): Long =
        contents.sumOf { stack -> if (stack != null && !stack.type.isAir && matches(stack)) stack.amount.toLong() else 0L }

    /** Takes [amount] matching items. Returns false (contents partly changed) when there are not enough. */
    fun remove(contents: Array<ItemStack?>, amount: Long, matches: (ItemStack) -> Boolean): Boolean {
        var left = amount
        for (i in contents.indices) {
            if (left <= 0) break
            val stack = contents[i] ?: continue
            if (stack.type.isAir || !matches(stack)) continue
            val taken = minOf(left, stack.amount.toLong()).toInt()
            left -= taken
            if (taken >= stack.amount) contents[i] = null else stack.amount = stack.amount - taken
        }
        return left <= 0
    }

    /**
     * Adds [stack] like Minecraft does: first onto similar stacks, then into empty slots.
     * Returns how many did not fit.
     */
    fun add(contents: Array<ItemStack?>, stack: ItemStack): Int {
        var left = stack.amount
        val max = stack.maxStackSize.coerceAtLeast(1)
        for (i in contents.indices) {
            if (left <= 0) return 0
            val slot = contents[i] ?: continue
            if (slot.type.isAir || !slot.isSimilar(stack) || slot.amount >= max) continue
            val moved = minOf(left, max - slot.amount)
            slot.amount += moved
            left -= moved
        }
        for (i in contents.indices) {
            if (left <= 0) return 0
            val slot = contents[i]
            if (slot != null && !slot.type.isAir) continue
            val moved = minOf(left, max)
            contents[i] = stack.asQuantity(moved)
            left -= moved
        }
        return left
    }
}
