package net.azisaba.sis.gui

import net.azisaba.sis.Sis
import net.azisaba.sis.config.Messages
import net.azisaba.sis.dialog.NpcSpecs
import net.azisaba.sis.item.Items
import net.azisaba.sis.itemize.Itemizer
import net.azisaba.sis.model.NpcSpec
import net.azisaba.sis.model.Shop
import net.azisaba.sis.model.Trade
import net.azisaba.sis.model.TradeEntry
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import java.util.UUID

object EditorMenus {
    fun openShopEditor(sis: Sis, player: Player, shop: Shop, parent: Menu? = null) {
        // One editor at a time (two would edit trades by list position and overwrite each other).
        if (EditLocks.refuse(player, shop)) return
        ShopEditorMenu(sis, player, shop).also { it.parent = parent }.open()
    }
}

/** Players waiting to right-click an entity whose look a shop should copy. */
object CaptureRequests {
    private val pending = HashMap<UUID, String>()
    fun request(player: Player, shopId: String) {
        pending[player.uniqueId] = shopId
    }

    fun take(player: Player): String? = pending.remove(player.uniqueId)
}

/**
 * Admin view of a shop: its trades (click to edit, shift-click to move, Q to duplicate) and buttons for the shop
 * itself.
 */
class ShopEditorMenu(private val sis: Sis, viewer: Player, private val shop: Shop) : Menu(viewer, 6), ShopEditing {
    override val editedShop: Shop get() = shop
    private var page = 0
    override val pageButtons = 45 to 47
    private val perPage = 36

    override fun title(): Component = Messages.get("editor.shop-title", "shop" to sis.shops.displayName(shop), "id" to shop.id)

    override fun render() {
        val icons = sis.icons
        val trades = shop.trades
        val pages = maxOf(1, (trades.size + 1 + perPage - 1) / perPage)
        page = page.coerceIn(0, pages - 1)
        for (i in 0 until perPage) {
            val index = page * perPage + i
            val trade = trades.getOrNull(index)
            when {
                trade != null -> slot(i, tradeIcon(trade, index)) { click -> onTradeClick(click, index) }
                index == trades.size -> slot(i, icons.icon("new-trade", Messages.get("editor.new-trade"), Messages.lines("editor.new-trade-lore"))) {
                    TradeEditorMenu(sis, viewer, shop, null).also { it.parent = this }.open()
                }
                else -> slot(i, null)
            }
        }
        val b = 36
        slot(b, icons.icon("preview", Messages.get("editor.preview"))) { if (!TradeMenu.refuseOpen(viewer, shop)) TradeMenu(sis, viewer, shop, adminView = true).also { it.parent = this }.open() }
        slot(b + 1, icons.icon("settings", Messages.get("editor.settings"), Messages.lines("editor.settings-lore",
            "name" to sis.shops.displayName(shop), "locked" to yesNo(shop.locked), "searchable" to yesNo(shop.searchable), "invisible" to yesNo(shop.invisible)))) {
            sis.dialogs.shopSettings(viewer, this, shop)
        }
        slot(b + 2, icons.icon("npc-kind", Messages.get("editor.npc-kind"), Messages.lines("editor.npc-kind-lore", "kind" to NpcSpecs.format(shop.npc)))) {
            sis.dialogs.npcKind(viewer, this, shop)
        }
        slot(b + 3, icons.icon("capture", Messages.get("editor.capture"), Messages.lines("editor.capture-lore"))) {
            CaptureRequests.request(viewer, shop.id)
            viewer.closeInventory()
            Messages.send(viewer, "editor.capture-start")
        }
        slot(b + 4, icons.icon("equipment", Messages.get("editor.equipment"))) {
            val citizens = shop.npc as? NpcSpec.Citizens
            if ((sis.npcs.entityOf(shop) is LivingEntity && shop.npc is NpcSpec.Vanilla) || (citizens != null && net.azisaba.sis.npc.CitizensBridge.equipment(citizens) != null))
                EquipmentMenu(sis, viewer, shop).also { it.parent = this }.open()
            else Messages.send(viewer, "editor.equipment-unavailable")
        }
        slot(b + 5, icons.icon("respawn", Messages.get("editor.respawn"))) {
            sis.npcs.spawn(shop)
            Messages.send(viewer, "editor.respawned")
        }
        slot(b + 6, icons.icon("teleport", Messages.get("editor.teleport"), Messages.lines("editor.teleport-lore", "location" to shop.location.describe()))) {
            val loc = shop.location.toBukkit()
            if (loc == null) Messages.send(viewer, "editor.world-not-loaded") else viewer.teleportAsync(loc)
        }
        slot(b + 7, icons.icon("itemize-shop", Messages.get("editor.itemize-shop"), Messages.lines("editor.itemize-shop-lore"))) {
            val item = Itemizer.shopItem(sis, shop) ?: return@slot Messages.send(viewer, "editor.itemize-too-large")
            viewer.inventory.addItem(item).values.forEach { viewer.world.dropItem(viewer.location, it) }
            Messages.send(viewer, "editor.itemized")
        }
        slot(b + 8, icons.icon("delete", Messages.get("editor.delete-shop"))) {
            sis.dialogs.confirm(viewer, this, Messages.get("editor.delete-shop-confirm", "shop" to sis.shops.displayName(shop), "id" to shop.id)) { p ->
                sis.shops.remove(shop, p)
                Messages.send(p, "editor.shop-deleted", "id" to shop.id)
            }
        }
        if (page > 0) slot(45, icons.icon("previous", Messages.get("gui.previous-page"), Messages.lines("gui.page-hint"))) { page--; refresh() } else slot(45, icons.filler())
        slot(46, icons.icon("info", Messages.get("gui.page", "page" to page + 1, "pages" to pages)))
        if (page < pages - 1) slot(47, icons.icon("next", Messages.get("gui.next-page"), Messages.lines("gui.page-hint"))) { page++; refresh() } else slot(47, icons.filler())
        for (i in 48..52) slot(i, icons.filler())
        slot(53, icons.icon("close", Messages.get("gui.close"))) { back() }
    }

    private fun yesNo(value: Boolean): Component = Messages.get(if (value) "gui.answer-yes" else "gui.answer-no")

    private fun tradeIcon(trade: Trade, index: Int): ItemStack {
        val icons = sis.icons
        val base = when {
            trade.rewards.size == 1 -> icons.entry(trade.rewards[0])
            trade.rewards.isNotEmpty() -> icons.bundle(trade.rewards, Messages.get("trade.more", "count" to trade.rewards.size))
            else -> icons.icon("empty-slot")
        }
        val lore = ArrayList<Component>()
        lore += Messages.get("editor.trade-number", "number" to index + 1)
        lore += Messages.get("trade.costs-header")
        trade.costs.forEach { lore += Messages.get("trade.bullet", "line" to icons.describe(it)) }
        lore += Messages.get("trade.rewards-header")
        trade.rewards.forEach { lore += Messages.get("trade.bullet", "line" to icons.describe(it)) }
        if (!sis.trades.isValid(trade)) lore += Messages.get("editor.trade-invalid")
        lore += Messages.lines("editor.trade-lore")
        return base.addLore(lore)
    }

    private fun onTradeClick(click: Click, index: Int) {
        val trades = shop.trades
        when {
            click.type == ClickType.MIDDLE -> {
                // The icon is the (first) reward: copy that item.
                trades[index].rewards.firstOrNull()?.let { ItemCopy.toCursor(viewer, click.event, it) }
                return
            }
            click.type == ClickType.DROP -> {
                trades.add(index + 1, trades[index].copy(id = UUID.randomUUID()))
                sis.shops.changed(shop, viewer, "duplicate-trade", trades[index].id.toString())
            }
            click.shift && click.left && index > 0 -> {
                trades.add(index - 1, trades.removeAt(index))
                sis.shops.changed(shop, viewer, "move-trade")
            }
            click.shift && click.right && index < trades.size - 1 -> {
                trades.add(index + 1, trades.removeAt(index))
                sis.shops.changed(shop, viewer, "move-trade")
            }
            !click.shift -> {
                TradeEditorMenu(sis, viewer, shop, trades[index]).also { it.parent = this }.open()
                return
            }
        }
        viewer.playSound(viewer.location, Sound.UI_BUTTON_CLICK, 0.5f, 1.2f)
        refresh()
    }
}

/**
 * Edits one trade. Costs on the left, rewards on the right; one slot per entry, any amount.
 * Put an item from your inventory on a slot (a copy is used, you keep the item), or shift-click it in your
 * inventory (left = cost, right = reward). Click an entry to change its amount, right-click to remove it.
 * Every change is saved at once.
 */
class TradeEditorMenu(private val sis: Sis, viewer: Player, private val shop: Shop, original: Trade?) : Menu(viewer, 6), ShopEditing {
    override val editedShop: Shop get() = shop
    private var trade: Trade = original ?: Trade(costs = emptyList(), rewards = emptyList())
    private var stored = original != null

    private val costSlots = (0..3).flatMap { r -> (0..3).map { c -> r * 9 + c } }
    private val rewardSlots = (0..3).flatMap { r -> (5..8).map { c -> r * 9 + c } }

    override fun title(): Component = Messages.get("editor.trade-title", "shop" to sis.shops.displayName(shop))

    override fun render() {
        val icons = sis.icons
        drawSide(costSlots, trade.costs, cost = true)
        drawSide(rewardSlots, trade.rewards, cost = false)
        for (r in 0..3) slot(r * 9 + 4, icons.filler("divider"))
        for (i in 36..44) slot(i, icons.filler())
        slot(36, icons.icon("money", Messages.get("editor.add-money-cost"))) { addMoney(cost = true) }
        slot(37, icons.icon("options", Messages.get("editor.options"), optionLore())) {
            sis.dialogs.tradeOptions(viewer, this, trade.options) { update(trade.copy(options = it)) }
        }
        slot(40, icons.icon("info", Messages.get("editor.help"), Messages.lines("editor.help-lore")))
        slot(43, icons.icon("money", Messages.get("editor.add-money-reward"))) { addMoney(cost = false) }
        slot(44, icons.icon("command", Messages.get("editor.add-command"))) {
            sis.dialogs.command(viewer, this, "", "") { cmd, desc -> update(trade.copy(rewards = trade.rewards + TradeEntry.Command(cmd, desc, null))) }
        }
        for (i in 45..53) slot(i, icons.filler())
        slot(45, icons.icon("back", Messages.get("gui.back"))) { back() }
        slot(48, icons.icon("itemize-trade", Messages.get("editor.itemize-trade"), Messages.lines("editor.itemize-trade-lore"))) {
            if (!stored) return@slot
            viewer.inventory.addItem(Itemizer.tradeItem(sis, shop, trade)).values.forEach { viewer.world.dropItem(viewer.location, it) }
            Messages.send(viewer, "editor.itemized")
        }
        slot(50, icons.icon("duplicate", Messages.get("editor.duplicate-trade"))) {
            if (!stored) return@slot
            val copy = trade.copy(id = UUID.randomUUID())
            sis.shops.addTrade(shop, copy, viewer)
            TradeEditorMenu(sis, viewer, shop, copy).also { it.parent = parent }.open()
        }
        slot(53, icons.icon("delete", Messages.get("editor.delete-trade"))) {
            if (stored) {
                shop.trades.removeIf { it.id == trade.id }
                sis.shops.changed(shop, viewer, "remove-trade", trade.id.toString())
            }
            back()
        }
    }

    private fun optionLore(): List<Component> {
        val o = trade.options
        return Messages.lines(
            "editor.options-lore",
            "limit" to if (o.hasLimit) o.limit.toString() else "-",
            "scope" to Messages.get("dialog.options.scopes.${o.limitScope.name.lowercase()}"),
            "reset" to Messages.get("dialog.options.resets.${o.reset.name.lowercase()}"),
            "chance" to o.chance,
            "permission" to (o.permission ?: "-"),
        )
    }

    private fun drawSide(slots: List<Int>, entries: List<TradeEntry>, cost: Boolean) {
        val icons = sis.icons
        slots.forEachIndexed { i, s ->
            val entry = entries.getOrNull(i)
            if (entry == null) {
                slot(s, icons.icon("empty-slot", Messages.get(if (cost) "editor.empty-cost" else "editor.empty-reward"))) { click -> putFromCursor(click, cost) }
            } else {
                slot(s, icons.entry(entry).addLore(Messages.lines("editor.entry-lore"))) { click -> onEntryClick(click, cost, i) }
            }
        }
    }

    private fun entriesOf(cost: Boolean) = if (cost) trade.costs else trade.rewards

    private fun withEntries(cost: Boolean, list: List<TradeEntry>) = if (cost) trade.copy(costs = list) else trade.copy(rewards = list)

    private fun putFromCursor(click: Click, cost: Boolean) {
        val cursor = click.event.cursor
        if (cursor.type.isAir) return
        add(Items.toEntry(cursor.clone(), cursor.amount), cost)
    }

    override fun onShiftIn(event: InventoryClickEvent) {
        event.isCancelled = true
        val item = event.currentItem ?: return
        if (item.type.isAir) return
        add(Items.toEntry(item.clone(), item.amount), cost = event.isLeftClick)
    }

    /** Adds an entry, merging it into an existing one of the same item. */
    private fun add(entry: TradeEntry, cost: Boolean) {
        val list = entriesOf(cost).toMutableList()
        val slots = if (cost) costSlots else rewardSlots
        val same = list.indexOfFirst { sameKind(it, entry) }
        if (same >= 0) list[same] = plus(list[same], entry)
        else if (list.size >= slots.size) {
            Messages.send(viewer, "editor.side-full")
            return
        } else list += entry
        update(withEntries(cost, list))
    }

    private fun sameKind(a: TradeEntry, b: TradeEntry): Boolean = when {
        a is TradeEntry.Item && b is TradeEntry.Item -> a.item.isSimilar(b.item)
        a is TradeEntry.Mythic && b is TradeEntry.Mythic -> a.id == b.id
        else -> false
    }

    private fun plus(a: TradeEntry, b: TradeEntry): TradeEntry = when {
        a is TradeEntry.Item && b is TradeEntry.Item -> a.copy(amount = a.amount + b.amount)
        a is TradeEntry.Mythic && b is TradeEntry.Mythic -> a.copy(amount = a.amount + b.amount)
        else -> a
    }

    private fun onEntryClick(click: Click, cost: Boolean, index: Int) {
        val list = entriesOf(cost).toMutableList()
        val entry = list[index]
        if (click.type == ClickType.MIDDLE) {
            ItemCopy.toCursor(viewer, click.event, entry)
            return
        }
        if (!click.event.cursor.type.isAir) {
            list[index] = Items.toEntry(click.event.cursor.clone(), click.event.cursor.amount)
            update(withEntries(cost, list))
            return
        }
        when {
            click.right -> {
                list.removeAt(index)
                update(withEntries(cost, list))
            }
            entry is TradeEntry.Command -> sis.dialogs.command(viewer, this, entry.command, entry.description) { cmd, desc ->
                list[index] = entry.copy(command = cmd, description = desc)
                update(withEntries(cost, list))
            }
            entry is TradeEntry.Money -> sis.dialogs.number(viewer, this, Messages.get("dialog.number.money"), entry.amount.toString(), true) {
                list[index] = TradeEntry.Money(it)
                update(withEntries(cost, list))
            }
            else -> {
                val current = (entry as? TradeEntry.Item)?.amount ?: (entry as TradeEntry.Mythic).amount
                sis.dialogs.number(viewer, this, Messages.get("dialog.number.amount"), current.toString(), false) { value ->
                    list[index] = when (entry) {
                        is TradeEntry.Item -> entry.copy(amount = value.toInt())
                        is TradeEntry.Mythic -> entry.copy(amount = value.toInt())
                        else -> entry
                    }
                    update(withEntries(cost, list))
                }
            }
        }
    }

    private fun addMoney(cost: Boolean) {
        sis.dialogs.number(viewer, this, Messages.get("dialog.number.money"), "100", true) { value ->
            val list = entriesOf(cost).filterNot { it is TradeEntry.Money } + TradeEntry.Money(value)
            update(withEntries(cost, list))
        }
    }

    /** Takes the new state and saves it (a new trade is added to the shop once it has something in it). */
    private fun update(updated: Trade) {
        trade = updated
        when {
            stored && trade.isEmpty -> Unit // kept until deleted explicitly, so an accidental clear is not lost
            stored -> {
                shop.replaceTrade(trade)
                sis.shops.changed(shop, viewer, "edit-trade", trade.id.toString())
            }
            !trade.isEmpty -> {
                stored = true
                sis.shops.addTrade(shop, trade, viewer)
            }
        }
        viewer.playSound(viewer.location, Sound.UI_BUTTON_CLICK, 0.4f, 1.5f)
        refresh()
    }
}

/** The six equipment slots of a vanilla NPC; closing applies them to the NPC and its snapshot. */
class EquipmentMenu(private val sis: Sis, viewer: Player, private val shop: Shop) : Menu(viewer, 1), ShopEditing {
    override val editedShop: Shop get() = shop
    private val slots = listOf(1, 2, 3, 4, 6, 7)
    override val editableSlots: Set<Int> = slots.toSet()

    override fun title(): Component = Messages.get("editor.equipment-title")

    override fun render() {
        val citizens = shop.npc as? NpcSpec.Citizens
        val current = if (citizens != null) net.azisaba.sis.npc.CitizensBridge.equipment(citizens).orEmpty() else {
            val eq = (sis.npcs.entityOf(shop) as? LivingEntity)?.equipment
            listOf(eq?.helmet, eq?.chestplate, eq?.leggings, eq?.boots, eq?.itemInMainHand, eq?.itemInOffHand)
        }
        slots.forEachIndexed { i, s -> slot(s, current.getOrNull(i)?.takeUnless { it.type.isAir }?.clone()) }
        slot(0, sis.icons.icon("info", Messages.get("editor.equipment-help"), Messages.lines("editor.equipment-help-lore")))
        slot(5, sis.icons.filler("divider"))
        slot(8, sis.icons.icon("back", Messages.get("gui.back"))) { save(); back() }
    }

    override fun onShiftIn(event: InventoryClickEvent) {
        // Copies the item into the first free slot instead of moving it.
        event.isCancelled = true
        val item = event.currentItem?.takeUnless { it.type.isAir } ?: return
        val free = slots.firstOrNull { inventory.getItem(it)?.type?.isAir != false } ?: return
        inventory.setItem(free, item.clone())
    }

    override fun onEditableClick(event: InventoryClickEvent) {
        // Placing copies the cursor item; taking just clears the slot. Nothing is moved to or from the player.
        event.isCancelled = true
        val cursor = event.cursor
        inventory.setItem(event.slot, if (cursor.type.isAir) null else cursor.clone())
    }

    override fun onClose() = save()

    private fun save() {
        val items = slots.map { inventory.getItem(it)?.takeUnless { item -> item.type == Material.AIR } }
        // Citizens keeps the equipment of its NPCs itself (Equipment trait).
        val citizens = shop.npc as? NpcSpec.Citizens
        if (citizens != null) net.azisaba.sis.npc.CitizensBridge.setEquipment(citizens, items)
        else sis.npcs.applyEquipment(shop, items, viewer)
    }
}
