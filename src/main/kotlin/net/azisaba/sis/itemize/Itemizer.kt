package net.azisaba.sis.itemize

import net.azisaba.sis.Sis
import net.azisaba.sis.config.Messages
import net.azisaba.sis.gui.addLore
import net.azisaba.sis.gui.glint
import net.azisaba.sis.gui.named
import net.azisaba.sis.gui.plainTooltip
import net.azisaba.sis.model.Shop
import net.azisaba.sis.model.ShopLocation
import net.azisaba.sis.model.Trade
import net.azisaba.sis.storage.ShopYaml
import net.kyori.adventure.text.Component
import org.bukkit.NamespacedKey
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

/**
 * Trades and whole shops as items (the "compression gems" of 1.x), for moving them between shops and servers.
 * The data is the same YAML as the shop files, kept in the item's PDC.
 *
 * - trade item: right-click a shop (admin) to add the trade to it
 * - shop item: right-click a shop to add all its trades; sneak + right-click a block to place it as a new shop
 */
object Itemizer {
    lateinit var tradeKey: NamespacedKey
    lateinit var shopKey: NamespacedKey

    fun init(sis: Sis) {
        tradeKey = NamespacedKey(sis.plugin, "itemized_trade")
        shopKey = NamespacedKey(sis.plugin, "itemized_shop")
    }

    fun tradeItem(sis: Sis, shop: Shop, trade: Trade): ItemStack {
        val yaml = YamlConfiguration()
        yaml.set("trade", ShopYaml.tradeToMap(trade))
        val item = sis.config.tradeItemIcon.create()
        item.editPersistentDataContainer { it.set(tradeKey, PersistentDataType.STRING, yaml.saveToString()) }
        item.named(Messages.get("itemize.trade-name", "shop" to sis.shops.displayName(shop))).glint().plainTooltip()
        val lore = ArrayList<Component>()
        lore += Messages.get("trade.costs-header")
        trade.costs.forEach { lore += Messages.get("trade.bullet", "line" to sis.icons.describe(it)) }
        lore += Messages.get("trade.rewards-header")
        trade.rewards.forEach { lore += Messages.get("trade.bullet", "line" to sis.icons.describe(it)) }
        lore += Messages.lines("itemize.trade-lore")
        return item.addLore(lore)
    }

    fun shopItem(sis: Sis, shop: Shop): ItemStack {
        val item = sis.config.shopItemIcon.create()
        item.editPersistentDataContainer { it.set(shopKey, PersistentDataType.STRING, ShopYaml.toYaml(shop).saveToString()) }
        item.named(Messages.get("itemize.shop-name", "shop" to sis.shops.displayName(shop))).glint().plainTooltip()
        return item.addLore(Messages.lines("itemize.shop-lore", "trades" to shop.trades.size))
    }

    /** The trade in a trade item, with a fresh ID (so its limit counts are its own). */
    fun readTrade(item: ItemStack?): Trade? {
        val text = item?.persistentDataContainer?.get(tradeKey, PersistentDataType.STRING) ?: return null
        val yaml = YamlConfiguration().apply { loadFromString(text) }
        val map = yaml.getConfigurationSection("trade")?.getValues(false) ?: return null
        return ShopYaml.tradeFromMap(map).copy(id = UUID.randomUUID())
    }

    /** The shop in a shop item, moved to [at] under [newId], with fresh trade IDs. */
    fun readShop(item: ItemStack?, newId: String, at: ShopLocation): Shop? {
        val text = item?.persistentDataContainer?.get(shopKey, PersistentDataType.STRING) ?: return null
        val yaml = YamlConfiguration().apply { loadFromString(text) }
        val shop = ShopYaml.read(newId, yaml)
        shop.location = at
        shop.trades.replaceAll { it.copy(id = UUID.randomUUID()) }
        return shop
    }

    fun isItemized(item: ItemStack?): Boolean {
        val pdc = item?.persistentDataContainer ?: return false
        return pdc.has(tradeKey) || pdc.has(shopKey)
    }
}
