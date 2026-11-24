package net.azisaba.sis.gui

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

/**
 * A chest GUI. Subclasses draw with [slot] in [render]; clicks on drawn slots call their handler, everything else
 * in the top inventory is locked. [editableSlots] are normal slots where items can be put and taken (editors).
 */
abstract class Menu(val viewer: Player, private val rows: Int) : InventoryHolder {
    private var inventory: Inventory? = null
    private val handlers = HashMap<Int, (Click) -> Unit>()
    private var renderedTitle: Component? = null

    /** Opened from this menu; [back] returns there. */
    var parent: Menu? = null

    open val editableSlots: Set<Int> = emptySet()

    abstract fun title(): Component

    protected abstract fun render()

    override fun getInventory(): Inventory = inventory ?: error("menu not open")

    /** True while a dialog covers this menu: closing it then is not the player leaving. */
    internal var suspended = false
        private set

    /** Closes the inventory for a dialog; [open] brings the menu back. */
    fun suspendForDialog() {
        suspended = true
        (this as? ShopEditing)?.let { EditLocks.dialogOpened(viewer, it.editedShop.id) }
        if (viewer.openInventory.topInventory.holder === this) viewer.closeInventory()
    }

    fun open() {
        suspended = false
        val title = title()
        val inv = Bukkit.createInventory(this, rows * 9, title)
        inventory = inv
        renderedTitle = title
        draw()
        viewer.openInventory(inv)
        EditLocks.menuOpened(viewer)
        (this as? ShopEditing)?.let { EditLocks.started(viewer, it.editedShop) }
    }

    /** Draws again; reopens when the title changed (titles cannot be changed in place). */
    fun refresh() {
        val inv = inventory ?: return open()
        if (title() != renderedTitle || viewer.openInventory.topInventory !== inv) return open()
        draw()
    }

    private fun draw() {
        val inv = inventory ?: return
        handlers.clear()
        for (i in 0 until inv.size) if (i !in editableSlots) inv.setItem(i, null)
        render()
    }

    protected fun slot(index: Int, item: ItemStack?, handler: ((Click) -> Unit)? = null) {
        inventory?.setItem(index, item)
        if (handler != null) handlers[index] = handler else handlers.remove(index)
    }

    protected fun item(index: Int): ItemStack? = inventory?.getItem(index)

    fun back() {
        val p = parent
        if (p != null) p.open() else viewer.closeInventory()
    }

    /** Called when the player closes this menu (not when another menu replaces it). */
    open fun onClose() {}

    /**
     * Slots of the previous/next page buttons. A click outside the window then turns the page like 1.x:
     * left-click = previous, right-click = next (only when that page exists, i.e. its button is shown).
     */
    open val pageButtons: Pair<Int, Int>? = null

    private fun turnPageOutside(event: InventoryClickEvent) {
        val (previous, next) = pageButtons ?: return
        if (!event.cursor.type.isAir) return // dropping an item, not paging
        val target = when {
            event.click.isLeftClick -> previous
            event.click.isRightClick -> next
            else -> return
        }
        val handler = handlers[target]
        if (handler == null) {
            viewer.playSound(viewer.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.5f, 1f)
            return
        }
        handler(Click(event.click, target, event))
    }

    /** Shift-click from the player's inventory into this menu (editors put it into an editable slot). */
    open fun onShiftIn(event: InventoryClickEvent) {
        event.isCancelled = true
    }

    internal fun handle(event: InventoryClickEvent) {
        val top = event.view.topInventory
        val clickedTop = event.clickedInventory === top
        if (!clickedTop) {
            if (event.clickedInventory == null) {
                event.isCancelled = true
                turnPageOutside(event)
                return
            }
            when (event.action) {
                InventoryAction.MOVE_TO_OTHER_INVENTORY -> onShiftIn(event)
                // Collecting would also pull items out of this menu (editable slots hold copies the player must not get).
                InventoryAction.COLLECT_TO_CURSOR -> event.isCancelled = true
                else -> Unit
            }
            return
        }
        // A fast double click arrives as a normal click followed by DOUBLE_CLICK: only the first one is a button press
        // (otherwise "x64" trades 128 times).
        if (event.click == ClickType.DOUBLE_CLICK) {
            event.isCancelled = true
            return
        }
        if (event.slot in editableSlots) {
            onEditableClick(event)
            return
        }
        event.isCancelled = true
        handlers[event.slot]?.invoke(Click(event.click, event.slot, event))
    }

    /** Clicks on editable slots go through unless overridden. */
    open fun onEditableClick(event: InventoryClickEvent) {}

    internal fun handleDrag(event: InventoryDragEvent) {
        val topSize = event.view.topInventory.size
        // Editable slots take copies through onEditableClick; a drag would move the real items into the menu,
        // where they vanish when it closes.
        if (event.rawSlots.any { it < topSize }) event.isCancelled = true
    }

    data class Click(val type: ClickType, val slot: Int, val event: InventoryClickEvent) {
        val shift: Boolean get() = type.isShiftClick
        val right: Boolean get() = type.isRightClick
        val left: Boolean get() = type.isLeftClick
    }
}

class MenuListener : Listener {
    @EventHandler(priority = EventPriority.HIGH)
    fun onClick(event: InventoryClickEvent) {
        val menu = event.view.topInventory.holder as? Menu ?: return
        menu.handle(event)
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onDrag(event: InventoryDragEvent) {
        val menu = event.view.topInventory.holder as? Menu ?: return
        menu.handleDrag(event)
    }

    /** Any inventory opened (also another plugin's): the player left the dialog of an editor. */
    @EventHandler
    fun onOpen(event: org.bukkit.event.inventory.InventoryOpenEvent) {
        (event.player as? Player)?.let(EditLocks::menuOpened)
    }

    @EventHandler
    fun onClose(event: InventoryCloseEvent) {
        val menu = event.inventory.holder as? Menu ?: return
        if (event.reason == InventoryCloseEvent.Reason.OPEN_NEW || menu.suspended) return
        menu.onClose()
    }
}
