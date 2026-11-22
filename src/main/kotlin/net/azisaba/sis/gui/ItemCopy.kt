package net.azisaba.sis.gui

import net.azisaba.sis.item.Items
import net.azisaba.sis.model.TradeEntry
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent

/**
 * Middle-click copying for staff (like the creative clone in 1.x, where trade items were real items in the GUI):
 * puts a clean copy of the entry's item on the cursor. Not the GUI icon: no editor lore, no display amount, the
 * real item the trade takes or hands out, with the entry's amount up to one stack.
 */
object ItemCopy {
    fun toCursor(player: Player, event: InventoryClickEvent, entry: TradeEntry) {
        if (!player.hasPermission("sis.admin")) return
        val amount = when (entry) {
            is TradeEntry.Item -> entry.amount
            is TradeEntry.Mythic -> entry.amount
            else -> return // money and commands are not items
        }
        val item = Items.template(entry) ?: return
        item.amount = amount.coerceIn(1, item.maxStackSize)
        event.view.setCursor(item)
        player.playSound(player.location, Sound.ENTITY_ITEM_PICKUP, 0.4f, 1.6f)
    }
}
