package net.azisaba.sis.migration.legacy

import org.bukkit.Color
import org.bukkit.DyeColor
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.LeatherArmorMeta
import org.bukkit.inventory.meta.ShieldMeta
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.io.TempDir
import org.mockbukkit.mockbukkit.MockBukkit
import org.yaml.snakeyaml.Yaml
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.Base64
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The 1.x migration steps (ported from 1.x together with their tests and real 1.15.2 samples). */
class LegacyMigrationsTest {
    @TempDir
    lateinit var shopsDir: File

    companion object {
        @JvmStatic
        @BeforeAll
        fun setUpServer() {
            MockBukkit.mock()
        }

        @JvmStatic
        @AfterAll
        fun tearDownServer() = MockBukkit.unmock()

        const val UUID_STR = "11111111-2222-3333-4444-555555555555"

        // {MYTHIC_TYPE:"sigenpickaxe10SAC"} taken from a real 1.15.2 shop file
        const val MYTHIC_INTERNAL = "H4sIAAAAAAAA/+NiYOBg4PaNDPHwdI4PiQxwZRAszkxPzSvITM5OrEg1NAh2dGYAAIefIIUlAAAA"

        // Real item from a 1.15.2 shop: the profile sits under "SkullProfile", not "SkullOwner".
        const val REAL_SKULL = "H4sIAAAAAAAA/02KT2+CMBxAf1uyxJF9jF1JSvmjHHYwsmiJ7SYCSm+ARaitMwhu+Ln2Acdxt/fyngFgwMv21Cv12X5VjRIGGCNdRNs14voMk078dH0rrgYAPEzgKc1VL+BXDCHi+xod9qEqB+KNHm+R+iDyMiXndCgWxCN67Ku5tx78f6/b5TtXZXZY8/OmL3SK1nakxCqySp3caBzWdJk4LC4Hdk/ufJd8c/1+p8HRovHczWKCWaAaKgmiwQbxJW+YzDDD0YkGaZPpSLPgaHPJZSZTPX6YLUK/2qM3gAk8kgO85ofCKb3CMv0Kz0zh5pZZOXZpYhshz3ZmCPtTgD8pcErFHAEAAA=="

        /** {BlockEntityTag:{id:"minecraft:banner",Base:13,Patterns:[{cs,0},{ts,4},{tts,14}]},CUSTOM:"keep"} */
        const val PATTERNED_SHIELD = "H4sIAAO/8WoC/1XLwQrCMBCE4aGJGgMWn8dji0dRsD7AGlcJrRtJ9uLba8FDnNvPx3jAo+2mFMa9aNT3QA+HJt6wfUbhkOmuuyuJcDawHRUGsFnDnUiVsxT/beOw+jWaUAwWfZpSxryatCZbk9E/a+ffsr+ch+MBdmR+4QNArX8dqQAAAA=="

        /** {BlockEntityTag:{Base:15}}, taken from a real 1.15.2 shop. */
        const val BLACK_SHIELD = "H4sIAAAAAAAA/+NiYOBi4HPKyU/Ods0rySypDElMZ2ZgcUosTmVgYOBnYAAAyfMZgCEAAAA="
    }

    @Suppress("UNCHECKED_CAST")
    private fun load(text: String): Map<String, Any?> = Yaml().load(text) as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun Any?.map(key: String): Map<String, Any?> = (this as Map<String, Any?>)[key] as Map<String, Any?>

    // ---- MYTHIC_TYPE ------------------------------------------------------------------------------------------

    @Test
    fun `MYTHIC_TYPE items become MythicItem references`() {
        val yaml = "data-version: 2\nTrades:\n- give:\n  - ==: org.bukkit.inventory.ItemStack\n    v: 2230\n    type: DIAMOND_PICKAXE\n    amount: 3\n" +
            "    meta:\n      ==: ItemMeta\n      meta-type: UNSPECIFIC\n      internal: $MYTHIC_INTERNAL\n"
        val trade = (load(LegacyMythicTypeMigration.fixRawYaml(yaml))["Trades"] as List<*>)[0]
        val item = ((trade as Map<*, *>)["give"] as List<*>)[0] as Map<*, *>
        assertEquals(LegacyMythicTypeMigration.MYTHIC_ITEM_CLASS, item["=="])
        assertEquals("sigenpickaxe10SAC", item["id"])
        assertEquals(3, item["amount"])
    }

    @Test
    fun `plain items stay untouched`() {
        val yaml = "data-version: 2\nitem:\n  ==: org.bukkit.inventory.ItemStack\n  type: DIRT\n"
        assertSame(yaml, LegacyMythicTypeMigration.fixRawYaml(yaml))
    }

    // ---- heads ------------------------------------------------------------------------------------------------

    /** SkullOwner{Id:"..",Name:"Steve",Properties:{textures:[{Value:"VALUE"}]}} as 1.15.2 stored it. */
    private fun legacySkull(extraKey: String?): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(GZIPOutputStream(bytes)).use { out ->
            out.writeByte(10); out.writeUTF("")
            out.writeByte(10); out.writeUTF("SkullOwner")
            out.writeByte(8); out.writeUTF("Id"); out.writeUTF(UUID_STR)
            out.writeByte(8); out.writeUTF("Name"); out.writeUTF("Steve")
            out.writeByte(10); out.writeUTF("Properties")
            out.writeByte(9); out.writeUTF("textures"); out.writeByte(10); out.writeInt(1)
            out.writeByte(8); out.writeUTF("Value"); out.writeUTF("VALUE"); out.writeByte(0)
            out.writeByte(0)
            out.writeByte(0)
            if (extraKey != null) {
                out.writeByte(8); out.writeUTF(extraKey); out.writeUTF("x")
            }
            out.writeByte(0)
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    private fun skullYaml(internal: String) = "data-version: 2\nitem:\n  ==: org.bukkit.inventory.ItemStack\n  v: 2230\n  type: PLAYER_HEAD\n" +
        "  meta:\n    ==: ItemMeta\n    meta-type: SKULL\n    skull-owner: Steve\n    internal: $internal\n"

    private fun skullMeta(text: String) = load(text).map("item").map("meta")

    @Test
    fun `legacy head profile becomes a PlayerProfile`() {
        val meta = skullMeta(LegacySkullMigration.fixRawYaml(skullYaml(legacySkull(null))))
        val profile = meta["skull-owner"] as Map<*, *>
        assertEquals("PlayerProfile", profile["=="])
        assertEquals(UUID_STR, profile["uniqueId"])
        assertEquals("Steve", profile["name"])
        val property = (profile["properties"] as List<*>)[0] as Map<*, *>
        assertEquals("textures", property["name"])
        assertEquals("VALUE", property["value"])
        assertFalse("internal" in meta)
    }

    @Test
    fun `other internal tags stay but the legacy profile is dropped`() {
        val meta = skullMeta(LegacySkullMigration.fixRawYaml(skullYaml(legacySkull("Other"))))
        assertTrue("skull-owner" in meta)
        val nbt = LegacyNbt.readInternal(meta["internal"] as String)!!
        assertEquals("x", nbt["Other"])
        assertFalse("SkullOwner" in nbt)
    }

    @Test
    fun `real SkullProfile tag is converted`() {
        val profile = skullMeta(LegacySkullMigration.fixRawYaml(skullYaml(REAL_SKULL)))["skull-owner"] as Map<*, *>
        assertEquals("PlayerProfile", profile["=="])
        assertNotNull(profile["uniqueId"])
        assertFalse((profile["properties"] as List<*>).isEmpty())
    }

    @Test
    fun `yaml without heads stays untouched`() {
        val text = "data-version: 2\nname: shop\n"
        assertSame(text, LegacySkullMigration.fixRawYaml(text))
    }

    // ---- attributes -------------------------------------------------------------------------------------------

    private fun attributeYaml(key: String, slot: String?) = "data-version: 3\nitem:\n  ==: org.bukkit.inventory.ItemStack\n  type: DIAMOND_SWORD\n" +
        "  meta:\n    ==: ItemMeta\n    meta-type: UNSPECIFIC\n    attribute-modifiers:\n      $key:\n" +
        "      - ==: org.bukkit.attribute.AttributeModifier\n        uuid: $UUID_STR\n        name: $key\n        operation: 0\n        amount: 7.0\n" +
        (if (slot == null) "" else "        slot: $slot\n")

    private fun modifiers(text: String) = load(text).map("item").map("meta").map("attribute-modifiers")

    @Test
    fun `NMS attribute names and uuids are converted`() {
        val mods = modifiers(LegacyAttributeMigration.fixRawYaml(attributeYaml("generic.attackDamage", "HAND")))
        assertEquals(listOf("minecraft:attack_damage"), mods.keys.toList())
        val mod = (mods["minecraft:attack_damage"] as List<*>)[0] as Map<*, *>
        assertEquals("minecraft:$UUID_STR", mod["key"])
        assertFalse("uuid" in mod)
        assertEquals("mainhand", mod["slot"])
        assertEquals(7.0, (mod["amount"] as Number).toDouble())
    }

    @Test
    fun `a modifier without slot keeps working on any slot`() {
        val mod = (modifiers(LegacyAttributeMigration.fixRawYaml(attributeYaml("generic.maxHealth", null)))["minecraft:max_health"] as List<*>)[0] as Map<*, *>
        assertFalse("slot" in mod)
    }

    @Test
    fun `attribute keys`() {
        for (raw in listOf("generic.attackDamage", "GENERIC_ATTACK_DAMAGE", "minecraft:attack_damage")) {
            assertEquals("minecraft:attack_damage", LegacyAttributeMigration.convertAttributeKey(raw))
        }
        assertEquals("minecraft:armor_toughness", LegacyAttributeMigration.convertAttributeKey("generic.armorToughness"))
        assertEquals("minecraft:jump_strength", LegacyAttributeMigration.convertAttributeKey("horse.jumpStrength"))
        assertEquals("minecraft:spawn_reinforcements", LegacyAttributeMigration.convertAttributeKey("zombie.spawnReinforcements"))
        // the container step keeps the 1.16 form for the DataFixer
        assertEquals("minecraft:generic.attack_damage", LegacyContainerMigration.legacyAttributeKey("generic.attackDamage"))
    }

    @Test
    fun `attribute fix runs only once`() {
        val once = LegacyAttributeMigration.fixRawYaml(attributeYaml("generic.attackDamage", "OFF_HAND"))
        assertSame(once, LegacyAttributeMigration.fixRawYaml(once))
    }

    @Test
    fun `shared uuids become unique per attribute`() {
        val mod = "      - ==: org.bukkit.attribute.AttributeModifier\n        uuid: $UUID_STR\n        name: a\n        operation: 1\n        amount: 0.1\n        slot: "
        val text = "data-version: 3\nitem:\n  meta:\n    attribute-modifiers:\n      GENERIC_MOVEMENT_SPEED:\n${mod}HAND\n${mod}OFF_HAND\n"
        val list = modifiers(LegacyAttributeMigration.fixRawYaml(text))["minecraft:movement_speed"] as List<*>
        val (a, b) = list.map { it as Map<*, *> }
        assertNotEquals(a["key"], b["key"])
        assertEquals("mainhand", a["slot"])
        assertEquals("offhand", b["slot"])
    }

    // ---- leather armor ----------------------------------------------------------------------------------------

    private val leatherFile = "lobby1,762,42,1805.yml"
    private val leatherLegacy = """
        |Npc:
        |  Options:
        |    Equipments:
        |    - ==: org.bukkit.inventory.ItemStack
        |      v: 2230
        |      type: AIR
        |    - ==: org.bukkit.inventory.ItemStack
        |      v: 2230
        |      type: LEATHER_LEGGINGS
        |      meta:
        |        ==: ItemMeta
        |        meta-type: LEATHER_ARMOR
        |        color:
        |          ==: Color
        |          RED: 249
        |          BLUE: 29
        |          GREEN: 128
        |    - ==: org.bukkit.inventory.ItemStack
        |      v: 2230
        |      type: LEATHER_HORSE_ARMOR
        |      meta:
        |        ==: ItemMeta
        |        meta-type: LEATHER_ARMOR
        |        color:
        |          ==: Color
        |          RED: 1
        |          BLUE: 2
        |          GREEN: 3
        |""".trimMargin()

    private fun writeBackup(file: String, text: String) {
        val dir = File(shopsDir, "backup/20261105-190025").apply { mkdirs() }
        File(dir, file).writeText(text, Charsets.UTF_8)
    }

    @Test
    fun `leather armor pieces get the current meta type`() {
        val fixed = LegacyLeatherColorMigration.fixRawYaml(leatherLegacy)
        assertTrue("meta-type: COLORABLE_ARMOR" in fixed)
        // Horse armor still uses the LEATHER_ARMOR meta.
        assertTrue("meta-type: LEATHER_ARMOR" in fixed)
        assertTrue("RED: 249" in fixed)
        val text = "Trades: []\n"
        assertSame(text, LegacyLeatherColorMigration.fixRawYaml(text))
    }

    @Test
    fun `leather color lost earlier comes back from the backup`() {
        writeBackup(leatherFile, leatherLegacy)
        val yaml = YamlConfiguration()
        yaml.set("Npc.Options.Equipments", listOf(ItemStack(Material.AIR), ItemStack(Material.LEATHER_LEGGINGS)))
        assertEquals(1, LegacyLeatherColorMigration.restore(yaml, shopsDir, leatherFile))
        val restored = yaml.getList("Npc.Options.Equipments")!![1] as ItemStack
        assertEquals(Color.fromRGB(249, 128, 29), (restored.itemMeta as LeatherArmorMeta).color)
    }

    @Test
    fun `armor dyed since the backup is kept`() {
        writeBackup(leatherFile, leatherLegacy)
        val leggings = ItemStack(Material.LEATHER_LEGGINGS)
        leggings.itemMeta = (leggings.itemMeta as LeatherArmorMeta).apply { setColor(Color.BLUE) }
        val yaml = YamlConfiguration()
        yaml.set("Npc.Options.Equipments", listOf(ItemStack(Material.AIR), leggings))
        assertEquals(0, LegacyLeatherColorMigration.restore(yaml, shopsDir, leatherFile))
    }

    // ---- shields ----------------------------------------------------------------------------------------------

    private val shieldLegacy = """
        |Trades:
        |- give:
        |  - ==: org.bukkit.inventory.ItemStack
        |    v: 2230
        |    type: SHIELD
        |    meta:
        |      ==: ItemMeta
        |      meta-type: TILE_ENTITY
        |      display-name: gift
        |      internal: $PATTERNED_SHIELD
        |      blockMaterial: SHIELD
        |  - ==: org.bukkit.inventory.ItemStack
        |    v: 2230
        |    type: SHIELD
        |    meta:
        |      ==: ItemMeta
        |      meta-type: TILE_ENTITY
        |      internal: $BLACK_SHIELD
        |      blockMaterial: SHIELD
        |""".trimMargin()

    @Test
    fun `legacy shields become shield meta`() {
        val trade = (load(LegacyShieldColorMigration.fixRawYaml(shieldLegacy))["Trades"] as List<*>)[0] as Map<*, *>
        val items = (trade["give"] as List<*>).map { (it as Map<*, *>)["meta"] as Map<*, *> }
        val gift = items[0]
        assertEquals("SHIELD", gift["meta-type"])
        assertEquals("GREEN", gift["base-color"])
        assertEquals("gift", gift["display-name"])
        assertFalse("blockMaterial" in gift)
        assertEquals<Any?>(
            listOf(
                mapOf("==" to "Pattern", "color" to "WHITE", "pattern" to "minecraft:stripe_center"),
                mapOf("==" to "Pattern", "color" to "YELLOW", "pattern" to "minecraft:stripe_top"),
                mapOf("==" to "Pattern", "color" to "RED", "pattern" to "minecraft:triangles_top"),
            ),
            gift["patterns"],
        )
        // Other legacy tags stay in "internal", only the block entity data is moved out.
        assertEquals<Map<String, Any?>?>(mapOf("CUSTOM" to "keep"), LegacyNbt.readInternal(gift["internal"] as String))
        val black = items[1]
        assertEquals("BLACK", black["base-color"])
        assertFalse("patterns" in black)
        assertFalse("internal" in black)
    }

    @Test
    fun `rewritten shields load with their colors`() {
        // MockBukkit's ShieldMetaMock rejects deserialized Pattern objects, so only the pattern-less shield is checked.
        val yaml = YamlConfiguration().apply { loadFromString(LegacyShieldColorMigration.fixRawYaml(shieldLegacy)) }
        val give = (yaml.getMapList("Trades")[0] as Map<*, *>)["give"] as List<*>
        assertEquals(DyeColor.BLACK, ((give[1] as ItemStack).itemMeta as ShieldMeta).baseColor)
        val text = "Trades: []\n"
        assertSame(text, LegacyShieldColorMigration.fixRawYaml(text))
    }

    // ---- NBT ----------------------------------------------------------------------------------------------------

    @Test
    fun `nbt survives a round trip`() {
        val root = linkedMapOf<String, Any?>(
            "b" to 1.toByte(), "s" to 2.toShort(), "i" to 3, "l" to 4L, "f" to 5f, "d" to 6.0, "str" to "x",
            "list" to LegacyNbt.NbtList(8).apply { add("a"); add("b") }, "empty" to LegacyNbt.NbtList(3),
            "compound" to linkedMapOf<String, Any?>("k" to "v"),
        )
        val back = LegacyNbt.read(LegacyNbt.write(root))
        assertEquals(root.toString(), back.toString())
        assertEquals(3.toByte(), (back["empty"] as LegacyNbt.NbtList).elementType)
    }
}
