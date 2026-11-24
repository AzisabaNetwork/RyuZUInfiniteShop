package net.azisaba.sis.npc

import net.azisaba.sis.model.NpcSpec
import net.citizensnpcs.api.CitizensAPI
import net.citizensnpcs.api.npc.NPC
import net.citizensnpcs.api.trait.trait.Equipment
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.inventory.ItemStack

/**
 * Citizens NPCs for shops (Citizens is optional: every call checks it is enabled; classes of Citizens are only
 * touched inside this object).
 */
object CitizensBridge {
    val available: Boolean get() = Bukkit.getPluginManager().isPluginEnabled("Citizens")

    /** The Citizens NPC of [entity], as a shop NPC spec (its numeric ID). */
    fun specOf(entity: Entity): NpcSpec.Citizens? =
        if (!available || !entity.hasMetadata("NPC")) null else runCatching { Impl.specOf(entity) }.getOrNull()

    /** Creates a player-type Citizens NPC at [at] (like `spawn CITIZEN` of 1.x) and returns its spec. */
    fun create(at: Location, name: String): NpcSpec.Citizens? = if (!available) null else Impl.create(at, name)

    /** Helmet, chest, legs, boots, main hand, off hand (the order of the equipment menu). */
    fun equipment(spec: NpcSpec.Citizens): List<ItemStack?>? = if (!available) null else Impl.equipment(spec)

    fun setEquipment(spec: NpcSpec.Citizens, items: List<ItemStack?>): Boolean = available && Impl.setEquipment(spec, items)

    /** Everything that touches Citizens classes; only loaded when Citizens is there. */
    private object Impl {
        private val SLOTS = listOf(
            Equipment.EquipmentSlot.HELMET, Equipment.EquipmentSlot.CHESTPLATE, Equipment.EquipmentSlot.LEGGINGS,
            Equipment.EquipmentSlot.BOOTS, Equipment.EquipmentSlot.HAND, Equipment.EquipmentSlot.OFF_HAND,
        )

        fun specOf(entity: Entity): NpcSpec.Citizens? =
            CitizensAPI.getNPCRegistry().getNPC(entity)?.let { NpcSpec.Citizens(it.id.toString()) }

        fun create(at: Location, name: String): NpcSpec.Citizens {
            val npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, name.ifBlank { "Shop" })
            npc.spawn(at)
            return NpcSpec.Citizens(npc.id.toString())
        }

        private fun npcOf(spec: NpcSpec.Citizens): NPC? {
            val registry = CitizensAPI.getNPCRegistry()
            return spec.ref.toIntOrNull()?.let(registry::getById)
                ?: runCatching { registry.getByUniqueIdGlobal(java.util.UUID.fromString(spec.ref)) }.getOrNull()
        }

        fun equipment(spec: NpcSpec.Citizens): List<ItemStack?>? {
            val trait = npcOf(spec)?.getOrAddTrait(Equipment::class.java) ?: return null
            return SLOTS.map { trait.get(it)?.takeUnless { item -> item.type.isAir }?.clone() }
        }

        fun setEquipment(spec: NpcSpec.Citizens, items: List<ItemStack?>): Boolean {
            val trait = npcOf(spec)?.getOrAddTrait(Equipment::class.java) ?: return false
            SLOTS.forEachIndexed { i, slot -> trait.set(slot, items.getOrNull(i)) }
            return true
        }
    }
}
