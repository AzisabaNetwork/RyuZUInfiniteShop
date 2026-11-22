package net.azisaba.sis.storage

import net.azisaba.sis.model.LimitScope
import net.azisaba.sis.model.NpcSpec
import net.azisaba.sis.model.ResetPeriod
import net.azisaba.sis.model.Shop
import net.azisaba.sis.model.ShopLocation
import net.azisaba.sis.model.Trade
import net.azisaba.sis.model.TradeEntry
import net.azisaba.sis.model.TradeOptions
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.EntityType
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import org.mockbukkit.mockbukkit.MockBukkit
import java.io.File
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShopYamlTest {
    @TempDir
    lateinit var dir: File

    @BeforeEach
    fun setUp() {
        MockBukkit.mock()
    }

    @AfterEach
    fun tearDown() = MockBukkit.unmock()

    private fun sample(): Shop = Shop(
        id = "world_1_64_2",
        name = "<green>武器屋",
        location = ShopLocation("world", 1.5, 64.0, 2.5, 90f, 0f),
        npc = NpcSpec.Vanilla(EntityType.VILLAGER, "{id:\"minecraft:villager\"}"),
        trades = mutableListOf(
            Trade(
                costs = listOf(TradeEntry.Item(ItemStack.of(Material.DIAMOND), 256), TradeEntry.Money(1000.0)),
                rewards = listOf(TradeEntry.Mythic("cl7_sword", 1), TradeEntry.Command("give <player> bread", "パン", null)),
                options = TradeOptions(limit = 3, limitScope = LimitScope.GLOBAL, reset = ResetPeriod.WEEKLY, chance = 40, hideChance = true, permission = "vip"),
            ),
        ),
        locked = true,
        searchable = false,
        nameVisible = false,
        lookAtPlayer = true,
    )

    @Test
    fun `round trip keeps everything`() {
        val shop = sample()
        val text = ShopYaml.toYaml(shop).saveToString()
        val back = ShopYaml.read(shop.id, YamlConfiguration().apply { loadFromString(text) })
        assertEquals(shop.name, back.name)
        assertEquals(shop.location, back.location)
        assertEquals(shop.npc, back.npc)
        assertEquals(shop.locked, back.locked)
        assertEquals(shop.searchable, back.searchable)
        assertEquals(shop.nameVisible, back.nameVisible)
        assertEquals(shop.lookAtPlayer, back.lookAtPlayer)
        assertEquals(shop.trades, back.trades)
    }

    @Test
    fun `repository keeps history and restores it`() {
        val repo = ShopRepository(dir, Logger.getLogger("test")) { 5 }
        val shop = sample()
        repo.saveBlocking(shop)
        File(dir, "history").deleteRecursively()
        shop.name = "changed"
        repo.saveBlocking(shop)
        val history = repo.history(shop.id)
        assertEquals(1, history.size)
        assertEquals("<green>武器屋", repo.readHistory(shop.id, history[0]).name)
        assertEquals("changed", repo.loadAll().shops.single().name)
        repo.close()
    }

    @Test
    fun `broken files do not stop the others from loading`() {
        val repo = ShopRepository(dir, Logger.getLogger("test")) { 0 }
        repo.saveBlocking(sample())
        File(dir, "shops/broken.yml").writeText("format: 2\nlocation: 3\n")
        File(dir, "shops/save.yml").writeText("something: else\n")
        val result = repo.loadAll()
        assertEquals(1, result.shops.size)
        assertTrue("broken" in result.failures)
        assertTrue("save" !in result.failures)
        repo.close()
    }
}
