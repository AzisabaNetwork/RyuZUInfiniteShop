package net.azisaba.sis.gui

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.BundleContents
import io.papermc.paper.datacomponent.item.ItemLore
import io.papermc.paper.datacomponent.item.TooltipDisplay
import net.azisaba.sis.config.Messages
import net.azisaba.sis.config.PluginConfig
import net.azisaba.sis.item.Items
import net.azisaba.sis.model.TradeEntry
import net.azisaba.sis.service.Economy
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/** Small helpers to decorate GUI items through data components. */
fun ItemStack.named(name: Component): ItemStack = apply { setData(DataComponentTypes.CUSTOM_NAME, name) }

fun ItemStack.loreLines(lines: List<Component>): ItemStack = apply {
    if (lines.isEmpty()) unsetData(DataComponentTypes.LORE) else setData(DataComponentTypes.LORE, ItemLore.lore(lines))
}

fun ItemStack.addLore(lines: List<Component>): ItemStack = apply {
    if (lines.isEmpty()) return@apply
    val old = getData(DataComponentTypes.LORE)?.lines().orEmpty()
    setData(DataComponentTypes.LORE, ItemLore.lore(old + lines))
}

fun ItemStack.glint(on: Boolean = true): ItemStack = apply { setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, on) }

/** Shows only the name (no attributes, enchantments etc. of the icon). */
fun ItemStack.plainTooltip(): ItemStack = apply {
    setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().hiddenComponents(
        setOf(DataComponentTypes.ATTRIBUTE_MODIFIERS, DataComponentTypes.ENCHANTMENTS, DataComponentTypes.BUNDLE_CONTENTS),
    ).build())
}

fun ItemStack.noTooltip(): ItemStack = apply { setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().hideTooltip(true).build()) }

class Icons(private val config: () -> PluginConfig) {
    fun icon(name: String, label: Component? = null, lore: List<Component> = emptyList()): ItemStack {
        val item = config().icon(name)
        if (label != null) item.named(label)
        item.loreLines(lore)
        return item.plainTooltip()
    }

    fun filler(name: String = "filler"): ItemStack = config().icon(name).noTooltip()

    /**
     * How a trade entry looks in a GUI. Item counts above 64 are shown up to 99 (max_stack_size of the display
     * copy), larger amounts additionally in the lore.
     */
    fun entry(entry: TradeEntry): ItemStack = when (entry) {
        is TradeEntry.Item, is TradeEntry.Mythic -> {
            val amount = if (entry is TradeEntry.Item) entry.amount else (entry as TradeEntry.Mythic).amount
            val template = Items.template(entry)
            if (template == null) {
                icon("status-unavailable", Messages.get("entry.unknown-mythic", "id" to (entry as TradeEntry.Mythic).id))
            } else {
                template.setData(DataComponentTypes.MAX_STACK_SIZE, 99)
                template.amount = amount.coerceIn(1, 99)
                if (amount > 99 || amount > template.type.maxStackSize) template.addLore(listOf(Messages.get("entry.amount", "amount" to "%,d".format(amount))))
                template
            }
        }
        is TradeEntry.Money -> icon("money", Messages.get("entry.money", "amount" to Economy.format(entry.amount)))
        is TradeEntry.Command -> {
            val base = entry.display?.clone() ?: config().icon("command")
            base.named(Messages.get("entry.command"))
            if (entry.description.isNotBlank()) base.loreLines(entry.description.split('\n').map { Messages.parse(it) })
            base
        }
    }

    /** A bundle whose tooltip previews [entries] (used when a trade has more entries than slots). */
    fun bundle(entries: List<TradeEntry>, label: Component): ItemStack {
        val item = ItemStack.of(Material.BUNDLE)
        val contents = entries.map { e ->
            val shown = entry(e)
            shown.amount = shown.amount.coerceAtMost(shown.type.maxStackSize)
            shown
        }
        item.setData(DataComponentTypes.BUNDLE_CONTENTS, BundleContents.bundleContents(contents))
        item.named(label)
        return item
    }

    /** One line per entry, for lore summaries ("Diamond ×256", "1,000 coins"). */
    fun describe(entry: TradeEntry): Component = when (entry) {
        is TradeEntry.Item -> Messages.get("entry.line", "name" to itemName(Items.template(entry)), "amount" to "%,d".format(entry.amount))
        is TradeEntry.Mythic -> Messages.get("entry.line", "name" to itemName(Items.template(entry)), "amount" to "%,d".format(entry.amount))
        is TradeEntry.Money -> Messages.get("entry.money-line", "amount" to Economy.format(entry.amount))
        is TradeEntry.Command -> Messages.get("entry.command-line", "description" to (entry.description.lineSequence().firstOrNull() ?: entry.command))
    }

    fun itemName(item: ItemStack?): Component = item?.effectiveName() ?: Messages.get("entry.unknown")
}
