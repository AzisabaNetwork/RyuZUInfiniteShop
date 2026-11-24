package net.azisaba.sis.migration.legacy

import java.util.UUID

/**
 * Restores player heads saved by 1.15.2 builds.
 *
 * Old versions stored the head's profile only inside the base64 "internal" NBT of the item meta (skull-owner held
 * just the name). Newer servers ignore that NBT, so the heads came back blank. ItemStacks are already deserialized
 * by the time a YamlConfiguration is loaded, so the fix has to run on the raw YAML text, see [fixRawYaml].
 */
object LegacySkullMigration {
    const val FROM_VERSION = 2
    const val TO_VERSION = 3

    private const val SKULL_OWNER = "SkullOwner"
    private const val SKULL_PROFILE = "SkullProfile"

    /** The converted YAML text, or the input itself when no legacy head was found. */
    fun fixRawYaml(text: String): String = LegacyYaml.rewrite(text) { root ->
        LegacyYaml.anyMap(root) { map -> map["meta-type"] == "SKULL" && fixSkullMeta(map) }
    }

    private fun fixSkullMeta(meta: MutableMap<Any?, Any?>): Boolean {
        val internal = meta["internal"] as? String ?: return false
        if (meta["skull-owner"] is Map<*, *>) return false
        val nbt = LegacyNbt.readInternal(internal) ?: return false
        // 1.15.2 Paper stored the profile under "SkullProfile"; vanilla used "SkullOwner".
        val owner = (nbt[SKULL_OWNER] ?: nbt[SKULL_PROFILE]) as? Map<*, *> ?: return false

        val profile = LinkedHashMap<String, Any?>()
        profile["=="] = "PlayerProfile"
        // CraftPlayerProfile.deserialize reads "uniqueId" (not "uuid")
        toUuid(owner["Id"])?.let { profile["uniqueId"] = it.toString() }
        (owner["Name"] as? String)?.takeIf { it.isNotBlank() }?.let { profile["name"] = it }

        val properties = ArrayList<Map<String, Any?>>()
        (owner["Properties"] as? Map<*, *>)?.forEach { (name, values) ->
            for (value in values as? List<*> ?: return@forEach) {
                val v = value as? Map<*, *> ?: continue
                val data = v["Value"] as? String ?: continue
                properties += linkedMapOf<String, Any?>("name" to name.toString(), "value" to data).apply {
                    (v["Signature"] as? String)?.let { put("signature", it) }
                }
            }
        }
        if (properties.isNotEmpty()) profile["properties"] = properties
        if ("uniqueId" !in profile && "name" !in profile) {
            if (properties.isEmpty()) return false
            // A profile needs a uuid or a name; derive a stable one from the texture
            val texture = properties[0]["value"].toString()
            profile["uniqueId"] = UUID.nameUUIDFromBytes(texture.toByteArray(Charsets.UTF_8)).toString()
        }
        meta["skull-owner"] = profile

        // Paper parses a leftover SkullProfile/SkullOwner from "internal" with the current codec, which cannot read
        // the 1.15.2 layout and yields an empty profile; that empty profile then wins over "skull-owner".
        nbt.remove(SKULL_OWNER)
        nbt.remove(SKULL_PROFILE)
        if (nbt.isEmpty()) meta.remove("internal")
        else runCatching { LegacyNbt.writeInternal(nbt) }.onSuccess { meta["internal"] = it }.onFailure { meta.remove("internal") }
        return true
    }

    private fun toUuid(id: Any?): UUID? = runCatching {
        when {
            id is String -> UUID.fromString(id)
            id is IntArray && id.size == 4 ->
                UUID((id[0].toLong() shl 32) or (id[1].toLong() and 0xFFFFFFFFL), (id[2].toLong() shl 32) or (id[3].toLong() and 0xFFFFFFFFL))
            else -> null
        }
    }.getOrNull()
}
