package net.azisaba.sis.service

import org.bukkit.Bukkit
import org.bukkit.inventory.ItemStack
import xyz.acrylicstyle.storageBox.utils.StorageBoxItemInfo
import xyz.acrylicstyle.storageBox.utils.StorageBoxUtils
import java.util.logging.Logger

/** Items that hold other items (Storage Box): their contents count as items the player has. */
interface StoredItems {
    /** What [item] holds and how many (may be 0), or null when it is not such a container or holds no kind of item. */
    fun read(item: ItemStack): Stored?

    /** A copy of the container [item] holding [amount] of its content. */
    fun withAmount(item: ItemStack, amount: Long): ItemStack

    /** One kind of item, any amount. [content] has an amount of 1. */
    data class Stored(val content: ItemStack, val amount: Long)

    companion object {
        val NONE: StoredItems = object : StoredItems {
            override fun read(item: ItemStack): Stored? = null
            override fun withAmount(item: ItemStack, amount: Long): ItemStack = item
        }

        /** Storage Box support when StorageBox (1.8.0+) is installed, otherwise [NONE]. */
        fun create(logger: Logger): StoredItems {
            if (Bukkit.getPluginManager().getPlugin("StorageBox") == null) return NONE
            return runCatching { StorageBoxItems().also { logger.info("Storage Box contents count for trades") } }.getOrElse {
                logger.warning("StorageBox is installed but is older than 1.8.0; Storage Box contents are not counted: $it")
                NONE
            }
        }
    }
}

/**
 * Storage Box (acrylic-style) 1.8.0+. Only loaded when StorageBox is installed.
 * [StorageBoxItemInfo.read] reads the box from its PDC without parsing the whole item, so scanning inventories is cheap.
 */
private class StorageBoxItems : StoredItems {
    init {
        // Fails here (and the caller falls back) when the server has a StorageBox without this API.
        StorageBoxUtils::class.java.getMethod("setAmount", ItemStack::class.java, Long::class.javaPrimitiveType)
    }

    override fun read(item: ItemStack): StoredItems.Stored? {
        val info = StorageBoxItemInfo.read(item) ?: return null
        if (info.type == null) return null
        // A box emptied by trading keeps its item, so rewards can still go into it.
        val content = info.contentItemStack?.takeUnless { it.type.isAir } ?: return null
        return StoredItems.Stored(content.asOne(), info.amount.coerceAtLeast(0))
    }

    override fun withAmount(item: ItemStack, amount: Long): ItemStack =
        StorageBoxUtils.setAmount(item, amount.coerceAtLeast(0)) ?: item
}
