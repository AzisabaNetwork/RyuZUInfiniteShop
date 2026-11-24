package net.azisaba.sis.migration

import net.azisaba.sis.Sis
import net.azisaba.sis.model.NpcSpec
import net.azisaba.sis.model.TradeEntry
import net.azisaba.sis.service.PeriodClock
import net.azisaba.sis.service.TradeCounter
import net.azisaba.sis.storage.Database
import net.azisaba.sis.storage.ShopRepository
import org.bukkit.Material
import org.bukkit.configuration.MemoryConfiguration
import org.bukkit.entity.EntityType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.plugin.PluginMock
import java.io.File
import java.time.ZoneId
import java.util.UUID
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LegacyImporterTest {
    @TempDir
    lateinit var dir: File

    private lateinit var sis: Sis

    @BeforeEach
    fun setUp() {
        val server = MockBukkit.mock()
        val plugin: PluginMock = MockBukkit.createMockPlugin()
        sis = Sis(plugin)
        sis.db = Database.openSqlite(File(dir, "data.db"), "sis_", Logger.getLogger("test"))
        sis.clock = PeriodClock(ZoneId.of("Asia/Tokyo"), 5)
        sis.counter = TradeCounter(sis.db, sis.clock)
        sis.repository = ShopRepository(File(dir, "new"), Logger.getLogger("test")) { 0 }
        server.toString()
    }

    @AfterEach
    fun tearDown() {
        sis.repository.close()
        sis.db.close()
        MockBukkit.unmock()
    }

    private val tradeId = UUID.fromString("762a5b00-ecc8-4c15-ad43-adc95628ab71")
    private val playerId = UUID.fromString("89356568-1798-49c8-aaf4-2dd17732e616")

    private fun writeLegacy(folder: File) {
        File(folder, "shops").mkdirs()
        File(folder, "shops/world,10,64,-3.yml").writeText(
            """
            Npc:
              Options:
                DisplayName: §aSpringEvent
                EntityType: VILLAGER
                Invisible: true
                Equipments: []
                Adult: false
                Profession: FARMER
                Biome: SNOWY_TUNDRA
                Level: 3
              Status:
                Lock: true
                Searchable: false
                Yaw: 90.0
            Shop:
              Options:
                ShopType: TwotoOne
            Trades:
            - give:
              - ==: ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.item.MythicItem
                id: spring_sakura2
                amount: 1
              take:
              - ==: org.bukkit.inventory.ItemStack
                v: 4671
                type: DIAMOND
                amount: 64
              - ==: org.bukkit.inventory.ItemStack
                v: 4671
                type: DIAMOND
                amount: 36
              uuid: $tradeId
            """.trimIndent(),
        )
        File(folder, "options.yml").writeText(
            """
            $tradeId:
              options:
                ==: ryuzuinfiniteshop.ryuzuinfiniteshop.data.system.TradeOption
                give: false
                money: 500.0
                limit: 2
                hide: true
                rate: 50
              counts:
                $playerId: 1
            """.trimIndent(),
        )
    }

    @Test
    fun `1x shop files become 2x shops with options and counts`() {
        val legacy = File(dir, "legacy").also(::writeLegacy)
        val report = LegacyImporter(sis).import(legacy, overwrite = false)
        assertEquals(1, report.shops, report.failed.toString())
        assertEquals(1, report.counts)

        val shop = sis.repository.loadAll().shops.single()
        assertEquals("world_10_64_-3", shop.id)
        assertEquals(10.5, shop.location.x)
        assertEquals(-2.5, shop.location.z)
        assertEquals(90f, shop.location.yaw)
        assertEquals("<green>SpringEvent", shop.name)
        assertTrue(shop.locked)
        assertTrue(!shop.searchable)
        assertTrue(shop.invisible)
        assertEquals(EntityType.VILLAGER, (shop.npc as NpcSpec.Vanilla).type)

        val trade = shop.trades.single()
        assertEquals(tradeId, trade.id)
        assertEquals(listOf(TradeEntry.Mythic("spring_sakura2", 1)), trade.rewards)
        val diamonds = trade.costs.filterIsInstance<TradeEntry.Item>().single()
        assertEquals(Material.DIAMOND, diamonds.item.type)
        assertEquals(100, diamonds.amount)
        assertEquals(TradeEntry.Money(500.0), trade.costs.filterIsInstance<TradeEntry.Money>().single())
        assertEquals(2, trade.options.limit)
        assertEquals(50, trade.options.chance)
        assertTrue(trade.options.hideChance)

        sis.counter.loadPlayerBlocking(playerId)
        assertEquals(1, sis.counter.count(trade, playerId))
    }

    @Test
    fun `auto migration moves 1x files aside`() {
        writeLegacy(dir)
        sis.repository.close()
        sis.repository = ShopRepository(dir, Logger.getLogger("test")) { 0 }
        val report = LegacyImporter(sis).autoMigrate(dir)!!
        assertEquals(1, report.shops)
        assertTrue(File(dir, "legacy-1.x/shops/world,10,64,-3.yml").exists())
        assertTrue(File(dir, "legacy-1.x/options.yml").exists())
        assertTrue(File(dir, "shops/world_10_64_-3.yml").exists())
        assertEquals(null, LegacyImporter(sis).autoMigrate(dir))
    }

    @Test
    fun `shops migrated before invisible existed get it from the 1x files`() {
        // State after a migration by an older 2.0 build: 1.x files in legacy-1.x, 2.x files without invisible
        writeLegacy(dir)
        sis.repository.close()
        sis.repository = ShopRepository(dir, Logger.getLogger("test")) { 0 }
        LegacyImporter(sis).autoMigrate(dir)!!
        val file = File(dir, "shops/world_10_64_-3.yml")
        file.writeText(file.readLines().filterNot { it.startsWith("invisible:") }.joinToString("\n") + "\n")
        // ...and edited since: the edit must survive
        file.writeText(file.readText().replace("name: <green>SpringEvent", "name: edited"))

        assertEquals(listOf("world_10_64_-3"), LegacyImporter(sis).backfillFlags(dir))
        val shop = sis.repository.load("world_10_64_-3")
        assertTrue(shop.locked, "1.x Lock stays the lock")
        assertTrue(shop.invisible)
        assertEquals("edited", shop.name)
        assertEquals(1, shop.trades.size)

        // only once
        val text = file.readText()
        assertEquals(emptyList(), LegacyImporter(sis).backfillFlags(dir))
        assertEquals(text, file.readText())
    }

    @Test
    fun `villager look becomes an entity snapshot`() {
        val section = MemoryConfiguration().apply {
            set("Adult", false); set("Profession", "FARMER"); set("Biome", "SNOWY_TUNDRA"); set("Level", 3)
        }
        val snbt = LegacyImporter(sis).snapshotOf(EntityType.VILLAGER, section)!!
        assertTrue("VillagerData:{profession:\"minecraft:farmer\",type:\"minecraft:snow\",level:3}" in snbt, snbt)
        assertTrue("Age:-24000" in snbt, snbt)
    }

    @Test
    fun `1x 15 2 items and looks are carried over`() {
        val legacy = File(dir, "old").apply { mkdirs() }
        File(legacy, "shops").mkdirs()
        // {MYTHIC_TYPE:"sigenpickaxe10SAC"} from a real 1.15.2 shop file
        val internal = "H4sIAAAAAAAA/+NiYOBg4PaNDPHwdI4PiQxwZRAszkxPzSvITM5OrEg1NAh2dGYAAIefIIUlAAAA"
        File(legacy, "shops/world,1,64,2.yml").writeText(
            """
            |Npc:
            |  Options:
            |    EntityType: SNOW_GOLEM
            |    Derp: true
            |  Status:
            |    Yaw: 0.0
            |Trades:
            |- give:
            |  - ==: org.bukkit.inventory.ItemStack
            |    v: 2230
            |    type: DIAMOND_PICKAXE
            |    amount: 3
            |    meta:
            |      ==: ItemMeta
            |      meta-type: UNSPECIFIC
            |      internal: $internal
            |  take:
            |  - ==: org.bukkit.inventory.ItemStack
            |    v: 2230
            |    type: DIRT
            |    amount: 5
            |  uuid: 11111111-1111-1111-1111-111111111111
            """.trimMargin() + "\n",
        )
        val report = LegacyImporter(sis).import(legacy, overwrite = false)
        assertEquals(1, report.shops, report.failed.toString())
        val shop = sis.repository.load("world_1_64_2")
        val trade = shop.trades.single()
        assertEquals(TradeEntry.Mythic("sigenpickaxe10SAC", 3), trade.rewards.single())
        assertEquals(5, (trade.costs.single() as TradeEntry.Item).amount)
        assertEquals(EntityType.SNOW_GOLEM, (shop.npc as NpcSpec.Vanilla).type)
        // MockBukkit cannot build entity snapshots, so the look is checked on the SNBT itself.
        val look = MemoryConfiguration().apply { set("Derp", true) }
        assertTrue("Pumpkin:0b" in LegacyImporter(sis).snapshotOf(EntityType.SNOW_GOLEM, look)!!)
    }

    @Test
    fun `tropical fish look and chance 0 are kept`() {
        val options = MemoryConfiguration().apply { set("Pattern", "STRIPEY"); set("BodyColor", "BLUE"); set("PatternColor", "YELLOW") }
        val snbt = LegacyImporter(sis).snapshotOf(EntityType.TROPICAL_FISH, options)!!
        // STRIPEY = large pattern 1; BLUE = 11, YELLOW = 4
        assertTrue(snbt.contains("Variant:${1 or (1 shl 8) or (11 shl 16) or (4 shl 24)}"), snbt)
        assertEquals(0, net.azisaba.sis.model.TradeOptions(chance = 0).chance)
    }
}
