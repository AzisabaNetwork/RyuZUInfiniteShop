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
            lower.startsWith("citizens:") -> text.substring(9).trim().takeIf { it.isNotEmpty() }?.let { NpcSpec.Citizens(it) }
            else -> entityType(lower)?.let { NpcSpec.Vanilla(it) }
        }
    }

    fun entityType(name: String): EntityType? {
        val key = name.removePrefix("minecraft:").lowercase(Locale.ROOT)
        return EntityType.entries.firstOrNull { it != EntityType.UNKNOWN && it.isSpawnable && it.isAlive && (it.key.key == key || it.name.equals(key, true)) }
            ?: EntityType.entries.firstOrNull { it == EntityType.MANNEQUIN && key == "mannequin" }
    }

    fun suggestions(): List<String> {
        val result = EntityType.entries.filter { it != EntityType.UNKNOWN && it.isSpawnable && it.isAlive }.map { it.key.key }.toMutableList()
        result += "block"
        result += "citizens:"
        if (MythicInstanceProvider.isLoaded()) result += MythicInstanceProvider.getInstance().mythicMobNames.map { "mythic:$it" }
        return result
    }
}
