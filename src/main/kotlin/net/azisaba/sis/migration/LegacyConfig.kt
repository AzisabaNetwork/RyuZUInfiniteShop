package net.azisaba.sis.migration

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

/**
 * Carries the settings of a 1.x `config.yml` and `panel.yml` over to the 2.x `config.yml`, once, when the 1.x
 * config is moved to `legacy-1.x/` (see [LegacyImporter.moveLegacyConfig]).
 */
object LegacyConfig {
    /** 1.x panel name -> 2.x icon, with the 1.x default (only changed panels are carried over). */
    private val PANELS = listOf(
        Triple("Success", "status-ready", "GREEN_STAINED_GLASS_PANE"),
        Triple("NotEnoughItems", "status-unavailable", "RED_STAINED_GLASS_PANE"),
        Triple("Limited", "status-limited", "RED_STAINED_GLASS_PANE"),
        Triple("Locked", "status-locked", "ORANGE_STAINED_GLASS_PANE"),
    )

    /**
     * Writes the 1.x values into [configFile] (the 2.x config that was just created). Returns the 2.x keys set.
     */
    fun carryOver(legacyConfig: File, legacyPanel: File?, configFile: File): List<String> {
        if (!legacyConfig.isFile || !configFile.isFile) return emptyList()
        val old = YamlConfiguration.loadConfiguration(legacyConfig)
        val new = YamlConfiguration.loadConfiguration(configFile)
        val set = ArrayList<String>()
        fun put(key: String, value: Any?) {
            if (value == null) return
            new.set(key, value)
            set += key
        }
        if (old.contains("EditLog")) put("logging.edits", old.getBoolean("EditLog"))
        if (old.contains("TradeLog")) put("logging.trades", old.getBoolean("TradeLog"))
        // 1.x kept every diff; 2.x keeps a number of versions (0 = off).
        if (old.contains("BackupDiff") && !old.getBoolean("BackupDiff")) put("history.keep", 0)
        if (old.contains("MythicItemExcludedIDs")) put("matching.mythic-excluded-ids", old.getStringList("MythicItemExcludedIDs"))
        // 1.x compared MythicMobs items by MMID only.
        put("matching.mythic-strict", false)
        old.getString("Language")?.let { put("language", if (it.lowercase().startsWith("ja")) "ja" else "en") }
        if (old.contains("OverwriteConverting")) put("shopkeepers.overwrite", old.getBoolean("OverwriteConverting"))
        if (old.contains("DefaultSearchableInConverting")) put("shopkeepers.searchable", old.getBoolean("DefaultSearchableInConverting"))

        if (legacyPanel != null && legacyPanel.isFile) {
            val panel = YamlConfiguration.loadConfiguration(legacyPanel)
            for ((name, icon, default) in PANELS) {
                val base = "Display.Panel.$name"
                val material = panel.getString("$base.Material")
                val cmd = panel.getInt("$base.CustomModelData", -1)
                if ((material == null || material.equals(default, true)) && cmd < 0) continue
                if (material != null) put("gui.icons.$icon.material", material.uppercase())
                if (cmd >= 0) put("gui.icons.$icon.custom-model-data", cmd)
            }
        }
        if (set.isNotEmpty()) new.save(configFile)
        return set
    }
}
