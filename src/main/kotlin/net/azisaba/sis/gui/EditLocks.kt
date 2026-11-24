package net.azisaba.sis.gui

import net.azisaba.sis.config.Messages
import net.azisaba.sis.model.Shop
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

/** A menu that edits a shop. While one is open, the shop is being edited (see [EditLocks]). */
interface ShopEditing {
    val editedShop: Shop
}

/**
 * Who is editing which shop, like the edit lock of 1.x: while an admin has an editor of a shop open (or a dialog
 * opened from one), nobody else can open that shop or trade with it, and trade screens already open are closed.
 *
 * Nothing is stored for open menus: the lock is read from what the players have open, so it can never be left
 * behind. Only dialogs are remembered (the menu is closed while a dialog covers it).
 */
object EditLocks {
    private class Pending(val shopId: String, val until: Long)

    private val dialogs = HashMap<UUID, Pending>()

    /** The player editing [shop], other than [except]. */
    fun editor(shop: Shop, except: Player? = null): Player? {
        val now = System.currentTimeMillis()
        dialogs.values.removeIf { it.until < now }
        return Bukkit.getOnlinePlayers().firstOrNull { p ->
            p != except && (
                (p.openInventory.topInventory.holder as? ShopEditing)?.editedShop?.id == shop.id ||
                    dialogs[p.uniqueId]?.shopId == shop.id
                )
        }
    }

    /** Sends "being edited" and returns true when someone else edits [shop]. */
    fun refuse(player: Player, shop: Shop): Boolean {
        val editor = editor(shop, player) ?: return false
        Messages.send(player, "shop.being-edited", "player" to editor.name)
        return true
    }

    /** An editor of [shopId] was covered by a dialog. */
    fun dialogOpened(player: Player, shopId: String) {
        // A dialog closed with Escape sends nothing back, so the lock of a dialog runs out on its own.
        dialogs[player.uniqueId] = Pending(shopId, System.currentTimeMillis() + 3 * 60_000L)
    }

    fun menuOpened(player: Player) {
        dialogs.remove(player.uniqueId)
    }

    fun quit(player: Player) {
        dialogs.remove(player.uniqueId)
    }

    /** [editor] started editing [shop]: other players' trade screens of it are closed. */
    fun started(editor: Player, shop: Shop) {
        for (p in Bukkit.getOnlinePlayers()) {
            if (p == editor) continue
            val menu = p.openInventory.topInventory.holder as? TradeMenu ?: continue
            if (!menu.shows(shop)) continue
            p.closeInventory()
            Messages.send(p, "shop.being-edited", "player" to editor.name)
        }
    }
}
