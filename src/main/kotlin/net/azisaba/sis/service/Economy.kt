package net.azisaba.sis.service

import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer

/** Vault, looked up lazily so the economy plugin may load after us. Absent Vault = money trades are invalid. */
object Economy {
    private var provider: net.milkbowl.vault.economy.Economy? = null

    private fun get(): net.milkbowl.vault.economy.Economy? {
        provider?.let { return it }
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return null
        provider = Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy::class.java)?.provider
        return provider
    }

    val available: Boolean get() = get() != null

    fun balance(player: OfflinePlayer): Double = get()?.getBalance(player) ?: 0.0

    fun withdraw(player: OfflinePlayer, amount: Double): Boolean =
        amount <= 0 || get()?.withdrawPlayer(player, amount)?.transactionSuccess() == true

    fun deposit(player: OfflinePlayer, amount: Double): Boolean =
        amount <= 0 || get()?.depositPlayer(player, amount)?.transactionSuccess() == true

    fun format(amount: Double): String = get()?.format(amount) ?: "%,.2f".format(amount)
}
