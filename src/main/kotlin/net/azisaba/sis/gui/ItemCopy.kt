package net.azisaba.sis.gui

import net.azisaba.sis.item.Items
import net.azisaba.sis.model.TradeEntry
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack

/**
 * Copying for staff (middle-click or a number key) (like the creative clone in 1.x, where trade items were real items in the GUI):
 * puts a clean copy of the entry's item on the cursor. Not the GUI icon: no editor lore, no display amount, the
 * real item the trade takes or hands out, with the entry's amount up to one stack.
 */
object ItemCopy {
    /**
     * Whether [click] asks for a copy: middle-click (the pick block key), or a number key. The vanilla client sends
     * the pick block key in inventories only in creative mode, so number keys are the way to copy in survival.
     */
    fun isCopy(click: Menu.Click): Boolean = click.type == ClickType.MIDDLE || click.type == ClickType.NUMBER_KEY

    /** Copies the entry's item for staff: onto the cursor (middle-click) or into the hotbar slot of the number key. */
    fun copy(player: Player, click: Menu.Click, entry: TradeEntry) {
        if (click.type == ClickType.NUMBER_KEY) toHotbar(player, click.event.hotbarButton, entry) else toCursor(player, click.event, entry)
    }

    fun toCursor(player: Player, event: InventoryClickEvent, entry: TradeEntry) {
        val item = copyOf(player, entry) ?: return
        event.view.setCursor(item)
        player.playSound(player.location, Sound.ENTITY_ITEM_PICKUP, 0.4f, 1.6f)
    }

    /** Into hotbar slot [slot] (0-8); when it is taken, into the inventory (or dropped when that is full). */
    private fun toHotbar(player: Player, slot: Int, entry: TradeEntry) {
        val item = copyOf(player, entry) ?: return
        val inventory = player.inventory
        if (slot in 0..8 && inventory.getItem(slot)?.type?.isAir != false) inventory.setItem(slot, item)
        else inventory.addItem(item).values.forEach { player.world.dropItem(player.location, it) }
        player.playSound(player.location, Sound.ENTITY_ITEM_PICKUP, 0.4f, 1.6f)
    }

    private fun copyOf(player: Player, entry: TradeEntry): ItemStack? {
        if (!player.hasPermission("sis.admin")) return null
        val amount = when (entry) {
            is TradeEntry.Item -> entry.amount
            is TradeEntry.Mythic -> entry.amount
            else -> return null // money and commands are not items
        }
        val item = Items.template(entry) ?: return null
        item.amount = amount.coerceIn(1, item.maxStackSize)
        return item
    }
}
