package net.azisaba.sis.service

import net.azisaba.sis.model.LimitScope
import net.azisaba.sis.model.NpcSpec
import net.azisaba.sis.model.ResetPeriod
import net.azisaba.sis.model.Shop
import net.azisaba.sis.model.ShopLocation
import net.azisaba.sis.model.Trade
import net.azisaba.sis.model.TradeEntry
import net.azisaba.sis.model.TradeOptions
import net.azisaba.sis.storage.Database
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import org.mockbukkit.mockbukkit.entity.PlayerMock
import java.io.File
import java.time.ZoneId
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals

class TradeServiceTest {
    private lateinit var server: ServerMock
    private lateinit var player: PlayerMock
    private lateinit var db: Database
    private lateinit var counter: TradeCounter
    private lateinit var service: TradeService
    private val traded = ArrayList<Int>()

    @TempDir
    lateinit var dir: File

    private val shop get() = Shop("test", "", ShopLocation("world", 0.0, 64.0, 0.0), NpcSpec.Block)

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
        player = server.addPlayer()
        db = Database.openSqlite(File(dir, "test.db"), "sis_", Logger.getLogger("test"))
        counter = TradeCounter(db, PeriodClock(ZoneId.of("Asia/Tokyo"), 5))
        service = TradeService(counter) { _, _, _, times, _ -> traded += times }
    }

    @AfterEach
    fun tearDown() {
        db.close()
        MockBukkit.unmock()
    }

    private fun item(material: Material) = TradeEntry.Item(ItemStack.of(material), 1)

    private fun trade(costs: List<TradeEntry>, rewards: List<TradeEntry>, options: TradeOptions = TradeOptions()) =
        Trade(costs = costs, rewards = rewards, options = options)

    @Test
    fun `costs above a stack size are counted across stacks`() {
        player.inventory.addItem(ItemStack.of(Material.DIAMOND, 64), ItemStack.of(Material.DIAMOND, 64), ItemStack.of(Material.DIAMOND, 64), ItemStack.of(Material.DIAMOND, 64))
        val t = trade(listOf(item(Material.DIAMOND).copy(amount = 200)), listOf(item(Material.EMERALD)))
        assertEquals(Evaluation(1, TradeStatus.READY), service.evaluate(player, shop, t, 64))

        val outcome = service.execute(player, shop, t, 1)
        assertEquals(1, outcome.times)
        assertEquals(56, player.inventory.all(Material.DIAMOND).values.sumOf { it.amount })
        assertEquals(1, player.inventory.all(Material.EMERALD).values.sumOf { it.amount })
        assertEquals(listOf(1), traded)
    }

    @Test
    fun `buying many at once stops at what the player has`() {
        player.inventory.addItem(ItemStack.of(Material.IRON_INGOT, 20))
        val t = trade(listOf(item(Material.IRON_INGOT).copy(amount = 3)), listOf(item(Material.BREAD)))
        val outcome = service.execute(player, shop, t, service.maxPerClick)
        assertEquals(6, outcome.times)
        assertEquals(2, player.inventory.all(Material.IRON_INGOT).values.sumOf { it.amount })
        assertEquals(6, player.inventory.all(Material.BREAD).values.sumOf { it.amount })
    }

    @Test
    fun `nothing is taken when items are missing`() {
        val t = trade(listOf(item(Material.GOLD_INGOT)), listOf(item(Material.BREAD)))
        assertEquals(TradeStatus.NOT_ENOUGH_ITEMS, service.execute(player, shop, t, 1).status)
        assertEquals(0, player.inventory.all(Material.BREAD).size)
    }

    @Test
    fun `rewards must fit into the inventory`() {
        repeat(36) { player.inventory.setItem(it, ItemStack.of(Material.STONE, 64)) }
        val t = trade(emptyList(), listOf(item(Material.DIAMOND_SWORD)))
        assertEquals(Evaluation(0, TradeStatus.INVENTORY_FULL), service.evaluate(player, shop, t, 1))
    }

    @Test
    fun `paying frees the room for the reward`() {
        repeat(36) { player.inventory.setItem(it, ItemStack.of(Material.STONE, 64)) }
        val t = trade(listOf(item(Material.STONE).copy(amount = 64)), listOf(item(Material.DIAMOND_SWORD)))
        assertEquals(Evaluation(1, TradeStatus.READY), service.evaluate(player, shop, t, 1))
    }

    @Test
    fun `limit per player and per period`() {
        player.inventory.addItem(ItemStack.of(Material.DIRT, 64))
        val t = trade(listOf(item(Material.DIRT)), listOf(item(Material.BREAD)), TradeOptions(limit = 3, reset = ResetPeriod.DAILY))
        assertEquals(3, service.execute(player, shop, t, 10).times)
        assertEquals(TradeStatus.LIMIT_REACHED, service.evaluate(player, shop, t, 1).status)
        assertEquals(3, counter.count(t, player.uniqueId))
    }

    @Test
    fun `global stock is shared by players`() {
        val other = server.addPlayer()
        val t = trade(emptyList(), listOf(item(Material.BREAD)), TradeOptions(limit = 2, limitScope = LimitScope.GLOBAL))
        assertEquals(1, service.execute(player, shop, t, 1).times)
        assertEquals(1, service.execute(other, shop, t, 5).times)
        assertEquals(TradeStatus.LIMIT_REACHED, service.evaluate(player, shop, t, 1).status)
    }

    @Test
    fun `counts survive a reload from the database`() {
        val t = trade(emptyList(), listOf(item(Material.BREAD)), TradeOptions(limit = 5))
        service.execute(player, shop, t, 2)
        counter.unloadPlayer(player.uniqueId)
        counter.loadPlayerBlocking(player.uniqueId)
        assertEquals(2, counter.count(t, player.uniqueId))
    }

    @Test
    fun `locked shops are view only`() {
        val locked = shop.apply { this.locked = true }
        val t = trade(emptyList(), listOf(item(Material.BREAD)))
        assertEquals(TradeStatus.LOCKED, service.evaluate(player, locked, t, 1).status)
        assertEquals(TradeStatus.READY, service.evaluate(player, locked, t, 1, bypassLock = true).status)
    }

    @Test
    fun `chance below 100 still takes the costs`() {
        player.inventory.addItem(ItemStack.of(Material.DIRT, 64))
        val t = trade(listOf(item(Material.DIRT)), listOf(item(Material.BREAD)), TradeOptions(chance = 1))
        val outcome = service.execute(player, shop, t, 64)
        assertEquals(64, outcome.times)
        assertEquals(0, player.inventory.all(Material.DIRT).size)
        assertEquals(outcome.successes, player.inventory.all(Material.BREAD).values.sumOf { it.amount })
    }

    /** Stands in for Storage Box: a chest with "box_type"/"box_amount" in its PDC. */
    private val fakeBoxes = object : StoredItems {
        val type = org.bukkit.NamespacedKey("test", "box_type")
        val amount = org.bukkit.NamespacedKey("test", "box_amount")
        override fun read(item: ItemStack): StoredItems.Stored? {
            val pdc = item.persistentDataContainer
            val t = pdc.get(type, org.bukkit.persistence.PersistentDataType.STRING) ?: return null
            val n = pdc.get(amount, org.bukkit.persistence.PersistentDataType.LONG) ?: return null
            return StoredItems.Stored(ItemStack.of(Material.valueOf(t)), n.coerceAtLeast(0))
        }
        override fun withAmount(item: ItemStack, amount: Long): ItemStack = item.clone().also { copy ->
            copy.editPersistentDataContainer { it.set(this.amount, org.bukkit.persistence.PersistentDataType.LONG, amount) }
        }
        fun box(material: Material, n: Long) = ItemStack.of(Material.CHEST).also { item ->
            item.editPersistentDataContainer {
                it.set(type, org.bukkit.persistence.PersistentDataType.STRING, material.name)
                it.set(amount, org.bukkit.persistence.PersistentDataType.LONG, n)
            }
        }
        fun amountIn(item: ItemStack?) = item?.persistentDataContainer?.get(amount, org.bukkit.persistence.PersistentDataType.LONG)
    }

    @Test
    fun `storage box contents count and are taken after loose items`() {
        val boxed = TradeService(counter, fakeBoxes) { _, _, _, _, _ -> }
        player.inventory.setItem(0, ItemStack.of(Material.DIAMOND, 10))
        player.inventory.setItem(5, fakeBoxes.box(Material.DIAMOND, 300))
        val t = trade(listOf(item(Material.DIAMOND).copy(amount = 256)), listOf(item(Material.EMERALD)))
        assertEquals(Evaluation(1, TradeStatus.READY), boxed.evaluate(player, shop, t, 64))

        assertEquals(1, boxed.execute(player, shop, t, 1).times)
        assertEquals(0, player.inventory.all(Material.DIAMOND).values.sumOf { it.amount })
        assertEquals(54L, fakeBoxes.amountIn(player.inventory.getItem(5)))
        assertEquals(1, player.inventory.all(Material.EMERALD).values.sumOf { it.amount })
    }

    @Test
    fun `a storage box in the off hand counts too`() {
        val boxed = TradeService(counter, fakeBoxes) { _, _, _, _, _ -> }
        player.inventory.setItemInOffHand(fakeBoxes.box(Material.IRON_INGOT, 5))
        val t = trade(listOf(item(Material.IRON_INGOT).copy(amount = 2)), listOf(item(Material.BREAD)))
        assertEquals(2, boxed.execute(player, shop, t, boxed.maxPerClick).times)
        assertEquals(1L, fakeBoxes.amountIn(player.inventory.itemInOffHand))
    }

    @Test
    fun `a storage box is never taken as the item it looks like`() {
        val boxed = TradeService(counter, fakeBoxes) { _, _, _, _, _ -> }
        player.inventory.setItem(0, fakeBoxes.box(Material.DIRT, 5))
        val t = trade(listOf(item(Material.CHEST)), listOf(item(Material.BREAD)))
        assertEquals(TradeStatus.NOT_ENOUGH_ITEMS, boxed.evaluate(player, shop, t, 1).status)
    }

    @Test
    fun `rewards go into a storage box of the same item`() {
        val boxed = TradeService(counter, fakeBoxes) { _, _, _, _, _ -> }
        repeat(36) { player.inventory.setItem(it, ItemStack.of(Material.STONE, 64)) }
        player.inventory.setItem(3, fakeBoxes.box(Material.EMERALD, 0))
        val t = trade(emptyList(), listOf(item(Material.EMERALD).copy(amount = 500)))
        // the inventory is full, but the box takes everything
        assertEquals(2, boxed.execute(player, shop, t, 2).times)
        assertEquals(1000L, fakeBoxes.amountIn(player.inventory.getItem(3)))
        assertEquals(0, player.inventory.all(Material.EMERALD).values.sumOf { it.amount })
    }

    @Test
    fun `storage boxes are left alone when the player turned it off`() {
        val off = PlayerSettings(payFromBoxes = false, rewardsIntoBoxes = false)
        val boxed = TradeService(counter, fakeBoxes, { off }) { _, _, _, _, _ -> }
        player.inventory.setItem(0, fakeBoxes.box(Material.DIAMOND, 100))
        player.inventory.setItem(1, fakeBoxes.box(Material.EMERALD, 1))
        val pay = trade(listOf(item(Material.DIAMOND)), listOf(item(Material.BREAD)))
        assertEquals(TradeStatus.NOT_ENOUGH_ITEMS, boxed.evaluate(player, shop, pay, 1).status)

        val get = trade(emptyList(), listOf(item(Material.EMERALD).copy(amount = 3)))
        assertEquals(1, boxed.execute(player, shop, get, 1).times)
        assertEquals(1L, fakeBoxes.amountIn(player.inventory.getItem(1)))
        assertEquals(3, player.inventory.all(Material.EMERALD).values.sumOf { it.amount })
    }

    @Test
    fun `player settings are kept in the database`() {
        val settings = PlayerSettingsService(db)
        val id = player.uniqueId
        settings.set(id, PlayerSettings(payFromBoxes = false, rewardsIntoBoxes = true, tradeFromList = false, tutorialHidden = true))
        settings.unload(id)
        settings.loadBlocking(id)
        assertEquals(PlayerSettings(payFromBoxes = false, rewardsIntoBoxes = true, tradeFromList = false, tutorialHidden = true), settings[id])
        settings.set(id, PlayerSettings())
        settings.loadBlocking(id)
        assertEquals(PlayerSettings(), settings[id])
    }

    @Test
    fun `money without Vault makes the trade invalid`() {
        val t = trade(listOf(TradeEntry.Money(10.0)), listOf(item(Material.BREAD)))
        assertEquals(TradeStatus.INVALID, service.evaluate(player, shop, t, 1).status)
    }
}
