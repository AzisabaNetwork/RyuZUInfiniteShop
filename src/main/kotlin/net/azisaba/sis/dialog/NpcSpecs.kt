package net.azisaba.sis.dialog

import net.azisaba.sis.item.mythic.MythicInstanceProvider
import net.azisaba.sis.model.NpcSpec
import org.bukkit.entity.EntityType
import java.util.Locale

/** Text form of an [NpcSpec] for commands and dialogs: `villager`, `mythic:<id>`, `citizens:<id>`, `block`. */
object NpcSpecs {
    fun format(spec: NpcSpec): String = when (spec) {
        is NpcSpec.Vanilla -> spec.type.key.key
        is NpcSpec.Mythic -> "mythic:${spec.mobId}"
        is NpcSpec.Citizens -> "citizens:${spec.ref}"
        NpcSpec.Block -> "block"
    }

    fun parse(input: String): NpcSpec? {
        val text = input.trim()
        if (text.isEmpty()) return null
        val lower = text.lowercase(Locale.ROOT)
        return when {
            lower == "block" -> NpcSpec.Block
            lower.startsWith("mythic:") -> text.substring(7).takeIf { id ->
                id.isNotBlank() && (!MythicInstanceProvider.isLoaded() || MythicInstanceProvider.getInstance().existsMythicMob(id))
            }?.let { NpcSpec.Mythic(it) }
            // "citizens" alone: a new Citizens NPC is created for the shop (see [NEW_CITIZENS])
            lower == "citizens" || lower == "citizens:" || lower == "citizens:new" -> NpcSpec.Citizens(NEW_CITIZENS)
            lower.startsWith("citizens:") -> text.substring(9).trim().takeIf { it.isNotEmpty() }?.let { NpcSpec.Citizens(it) }
            else -> entityType(lower)?.let { NpcSpec.Vanilla(it) }
        }
    }

    /** Ref of a Citizens spec meaning "create one" (replaced by the new NPC's ID before the shop is saved). */
    const val NEW_CITIZENS = "new"

    /** Entities that can stand at a shop: like 1.x any spawnable entity, also non-living ones (end crystals, ...). */
    private fun usable(type: EntityType) = type != EntityType.UNKNOWN && type.isSpawnable && type != EntityType.PLAYER

    fun entityType(name: String): EntityType? {
        val key = name.removePrefix("minecraft:").lowercase(Locale.ROOT)
        return EntityType.entries.firstOrNull { usable(it) && (it.key.key == key || it.name.equals(key, true)) }
            ?: EntityType.entries.firstOrNull { it == EntityType.MANNEQUIN && key == "mannequin" }
    }

    fun suggestions(): List<String> {
        val result = EntityType.entries.filter { usable(it) }.map { it.key.key }.toMutableList()
        result += "block"
        result += "citizens"
        result += "citizens:"
        if (MythicInstanceProvider.isLoaded()) result += MythicInstanceProvider.getInstance().mythicMobNames.map { "mythic:$it" }
        return result
    }
}
