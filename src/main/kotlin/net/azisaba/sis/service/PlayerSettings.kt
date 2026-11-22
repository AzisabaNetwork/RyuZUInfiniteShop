package net.azisaba.sis.service

import net.azisaba.sis.storage.Database
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** A player's own settings (`/sis settings`). Defaults are what most players want. */
data class PlayerSettings(
    /** Costs may be paid from Storage Boxes in the inventory. */
    val payFromBoxes: Boolean = true,
    /** Rewards go into a Storage Box holding the same item, when there is one. */
    val rewardsIntoBoxes: Boolean = true,
    /** The how-to dialog was turned off. */
    val tutorialHidden: Boolean = false,
)

/**
 * Settings per player, kept as flags in the database (only differences from the defaults are stored).
 * Read at login (off the main thread), held in memory while online.
 */
class PlayerSettingsService(private val db: Database) {
    private val cache = ConcurrentHashMap<UUID, PlayerSettings>()

    operator fun get(player: UUID): PlayerSettings = cache[player] ?: PlayerSettings()

    /** Blocking: AsyncPlayerPreLoginEvent or another background thread. */
    fun loadBlocking(player: UUID) {
        val flags = db.flagsBlocking(player)
        cache[player] = PlayerSettings(
            payFromBoxes = NO_PAY_FROM_BOXES !in flags,
            rewardsIntoBoxes = NO_REWARDS_INTO_BOXES !in flags,
            tutorialHidden = TUTORIAL_HIDDEN in flags,
        )
    }

    fun unload(player: UUID) {
        cache.remove(player)
    }

    fun set(player: UUID, settings: PlayerSettings) {
        val old = get(player)
        cache[player] = settings
        fun store(flag: String, on: Boolean, was: Boolean) {
            if (on == was) return
            if (on) db.setFlag(player, flag) else db.removeFlag(player, flag)
        }
        store(NO_PAY_FROM_BOXES, !settings.payFromBoxes, !old.payFromBoxes)
        store(NO_REWARDS_INTO_BOXES, !settings.rewardsIntoBoxes, !old.rewardsIntoBoxes)
        store(TUTORIAL_HIDDEN, settings.tutorialHidden, old.tutorialHidden)
    }

    companion object {
        const val NO_PAY_FROM_BOXES = "settings.no-pay-from-boxes"
        const val NO_REWARDS_INTO_BOXES = "settings.no-rewards-into-boxes"

        /** Same flag name as before settings existed, so "don't show again" answers are kept. */
        const val TUTORIAL_HIDDEN = "tutorial"
    }
}
