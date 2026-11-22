package net.azisaba.sis.dialog

import io.papermc.paper.dialog.Dialog
import io.papermc.paper.dialog.DialogResponseView
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.azisaba.sis.Sis
import net.azisaba.sis.config.Messages
import net.azisaba.sis.gui.Menu
import net.azisaba.sis.gui.SearchMenus
import net.azisaba.sis.model.LimitScope
import net.azisaba.sis.model.ResetPeriod
import net.azisaba.sis.model.TradeOptions
import net.azisaba.sis.service.PlayerSettings
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickCallback
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.time.Duration

/**
 * Forms shown with the 1.21.6+ dialog screen. They replace the chat prompts of 1.x ("type the name in chat").
 *
 * A dialog covers the GUI: [from] is closed without its close handler, and opened again when the dialog is
 * answered or cancelled with the cancel button.
 */
class Dialogs(private val sis: Sis) {

    private val callbackOptions = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(15)).build()

    private fun button(label: Component, width: Int = 150, onClick: ((DialogResponseView, Player) -> Unit)?): ActionButton {
        val builder = ActionButton.builder(label).width(width)
        if (onClick != null) {
            builder.action(DialogAction.customClick({ view, audience ->
                val player = audience as? Player ?: return@customClick
                Bukkit.getScheduler().runTask(sis.plugin, Runnable { onClick(view, player) })
            }, callbackOptions))
        }
        return builder.build()
    }

    private fun show(
        player: Player,
        from: Menu?,
        title: Component,
        body: List<DialogBody> = emptyList(),
        inputs: List<DialogInput> = emptyList(),
        confirmLabel: Component = Messages.get("dialog.save"),
        onConfirm: (DialogResponseView, Player) -> Unit,
    ) {
        from?.suspendForDialog()
        val yes = button(confirmLabel) { view, p -> onConfirm(view, p) }
        val no = button(Messages.get("dialog.cancel")) { _, _ -> from?.open() }
        val dialog = Dialog.create { factory ->
            factory.empty()
                .base(DialogBase.builder(title).body(body).inputs(inputs).canCloseWithEscape(true).afterAction(DialogBase.DialogAfterAction.CLOSE).build())
                .type(DialogType.confirmation(yes, no))
        }
        player.showDialog(dialog)
    }

    private fun text(key: String, label: String, initial: String, max: Int = 256, width: Int = 300) =
        DialogInput.text(key, Messages.get(label)).initial(initial).maxLength(max).width(width).build()

    private fun bool(key: String, label: String, initial: Boolean) =
        DialogInput.bool(key, Messages.get(label)).initial(initial).build()

    private fun <E : Enum<E>> options(key: String, label: String, values: Array<E>, initial: E, prefix: String) =
        DialogInput.singleOption(key, Messages.get(label), values.map {
            SingleOptionDialogInput.OptionEntry.create(it.name, Messages.get("$prefix.${it.name.lowercase()}"), it == initial)
        }).build()

    // ---- shop editing -------------------------------------------------------------------------------------------

    fun shopSettings(player: Player, from: Menu?, shop: net.azisaba.sis.model.Shop) {
        show(
            player, from, Messages.get("dialog.shop.title", "id" to shop.id),
            body = listOf(DialogBody.plainMessage(Messages.get("dialog.shop.body"))),
            inputs = listOf(
                text("name", "dialog.shop.name", shop.name),
                bool("locked", "dialog.shop.locked", shop.locked),
                bool("searchable", "dialog.shop.searchable", shop.searchable),
                bool("nameVisible", "dialog.shop.name-visible", shop.nameVisible),
                bool("lookAtPlayer", "dialog.shop.look-at-player", shop.lookAtPlayer),
                DialogInput.numberRange("yaw", Messages.get("dialog.shop.yaw"), -180f, 180f).step(15f)
                    .initial((Math.round(shop.location.yaw / 15f) * 15f).coerceIn(-180f, 180f)).width(300).build(),
            ),
        ) { view, p ->
            val yaw = view.getFloat("yaw") ?: shop.location.yaw
            val respawn = yaw != shop.location.yaw
            shop.name = view.getText("name") ?: shop.name
            shop.locked = view.getBoolean("locked") ?: shop.locked
            shop.searchable = view.getBoolean("searchable") ?: shop.searchable
            shop.nameVisible = view.getBoolean("nameVisible") ?: shop.nameVisible
            shop.lookAtPlayer = view.getBoolean("lookAtPlayer") ?: shop.lookAtPlayer
            shop.location = shop.location.copy(yaw = yaw)
            sis.shops.changed(shop, p, "settings", respawn = respawn)
            Messages.send(p, "editor.saved")
            from?.open()
        }
    }

    /** NPC kind: entity type, mythic:<id>, citizens:<id> or block; plus the mannequin skin. */
    fun npcKind(player: Player, from: Menu?, shop: net.azisaba.sis.model.Shop) {
        val current = NpcSpecs.format(shop.npc)
        show(
            player, from, Messages.get("dialog.npc.title"),
            body = listOf(DialogBody.plainMessage(Messages.get("dialog.npc.body"))),
            inputs = listOf(
                text("kind", "dialog.npc.kind", current, 128),
                text("skin", "dialog.npc.skin", "", 16, 200),
            ),
        ) { view, p ->
            val input = view.getText("kind")?.trim().orEmpty()
            val spec = NpcSpecs.parse(input)
            if (spec == null) {
                Messages.send(p, "editor.invalid-npc", "input" to input)
                from?.open()
                return@show
            }
            val keep = spec == shop.npc || (spec is net.azisaba.sis.model.NpcSpec.Vanilla && shop.npc.let { it is net.azisaba.sis.model.NpcSpec.Vanilla && it.type == spec.type })
            if (!keep) shop.npc = spec
            sis.shops.changed(shop, p, "npc", NpcSpecs.format(shop.npc), respawn = true)
            val skin = view.getText("skin")?.trim().orEmpty()
            if (skin.isNotEmpty()) sis.npcs.applySkin(shop, skin, p)
            Messages.send(p, "editor.saved")
            from?.open()
        }
    }

    fun tradeOptions(player: Player, from: Menu?, options: TradeOptions, onSave: (TradeOptions) -> Unit) {
        show(
            player, from, Messages.get("dialog.options.title"),
            inputs = listOf(
                text("limit", "dialog.options.limit", options.limit.toString(), 9, 120),
                options("scope", "dialog.options.scope", LimitScope.entries.toTypedArray(), options.limitScope, "dialog.options.scopes"),
                options("reset", "dialog.options.reset", ResetPeriod.entries.toTypedArray(), options.reset, "dialog.options.resets"),
                DialogInput.numberRange("chance", Messages.get("dialog.options.chance"), 1f, 100f).step(1f).initial(options.chance.toFloat()).width(300).build(),
                bool("hideChance", "dialog.options.hide-chance", options.hideChance),
                text("permission", "dialog.options.permission", options.permission.orEmpty(), 128),
            ),
        ) { view, p ->
            val limit = view.getText("limit")?.trim()?.toIntOrNull()
            if (limit == null || limit < 0) {
                Messages.send(p, "editor.invalid-number")
                from?.open()
                return@show
            }
            val updated = TradeOptions(
                limit = limit,
                limitScope = view.getText("scope")?.let { runCatching { LimitScope.valueOf(it) }.getOrNull() } ?: options.limitScope,
                reset = view.getText("reset")?.let { runCatching { ResetPeriod.valueOf(it) }.getOrNull() } ?: options.reset,
                chance = (view.getFloat("chance") ?: options.chance.toFloat()).toInt().coerceIn(1, 100),
                hideChance = view.getBoolean("hideChance") ?: options.hideChance,
                permission = view.getText("permission")?.trim()?.takeIf { it.isNotEmpty() },
            )
            onSave(updated)
            from?.open()
        }
    }

    fun number(player: Player, from: Menu?, title: Component, initial: String, allowDecimal: Boolean, onSave: (Double) -> Unit) {
        show(player, from, title, inputs = listOf(text("value", "dialog.number.value", initial, 18, 200))) { view, p ->
            val value = view.getText("value")?.trim()?.replace(",", "")?.toDoubleOrNull()
            if (value == null || value <= 0 || (!allowDecimal && value != Math.floor(value)) || value > Int.MAX_VALUE) {
                Messages.send(p, "editor.invalid-number")
            } else {
                onSave(value)
            }
            from?.open()
        }
    }

    fun command(player: Player, from: Menu?, initialCommand: String, initialDescription: String, onSave: (String, String) -> Unit) {
        show(
            player, from, Messages.get("dialog.command.title"),
            body = listOf(DialogBody.plainMessage(Messages.get("dialog.command.body"))),
            inputs = listOf(
                text("command", "dialog.command.command", initialCommand, 512, 300),
                text("description", "dialog.command.description", initialDescription, 256, 300),
            ),
        ) { view, p ->
            val cmd = view.getText("command")?.trim().orEmpty()
            if (cmd.isEmpty()) Messages.send(p, "editor.invalid-command") else onSave(cmd, view.getText("description")?.trim().orEmpty())
            from?.open()
        }
    }

    fun confirm(player: Player, from: Menu?, message: Component, onYes: (Player) -> Unit) {
        show(
            player, from, Messages.get("dialog.confirm.title"),
            body = listOf(DialogBody.plainMessage(message)),
            confirmLabel = Messages.get("dialog.confirm.yes"),
        ) { _, p -> onYes(p) }
    }

    // ---- players ------------------------------------------------------------------------------------------------

    fun search(player: Player, from: Menu? = player.openInventory.topInventory.holder as? Menu) {
        show(
            player, from, Messages.get("dialog.search.title"),
            body = listOf(DialogBody.plainMessage(Messages.get("dialog.search.body"))),
            inputs = listOf(
                text("keyword", "dialog.search.keyword", "", 64),
                options("target", "dialog.search.target", SearchTarget.entries.toTypedArray(), SearchTarget.ALL, "dialog.search.targets"),
                bool("held", "dialog.search.held", false),
            ),
            confirmLabel = Messages.get("dialog.search.run"),
        ) { view, p ->
            val target = view.getText("target")?.let { runCatching { SearchTarget.valueOf(it) }.getOrNull() } ?: SearchTarget.ALL
            val held = if (view.getBoolean("held") == true) p.inventory.itemInMainHand.takeUnless { it.type.isAir } else null
            SearchMenus.run(sis, p, view.getText("keyword")?.trim().orEmpty(), target, held)
        }
    }

    /** The player's own settings (`/sis settings`, the button in the trade screen). */
    fun playerSettings(player: Player, from: Menu?) {
        val current = sis.settings[player.uniqueId]
        show(
            player, from, Messages.get("dialog.settings.title"),
            body = listOf(DialogBody.plainMessage(Messages.get("dialog.settings.body"))),
            inputs = listOf(
                bool("payFromBoxes", "dialog.settings.pay-from-boxes", current.payFromBoxes),
                bool("rewardsIntoBoxes", "dialog.settings.rewards-into-boxes", current.rewardsIntoBoxes),
                bool("tutorial", "dialog.settings.tutorial", !current.tutorialHidden),
            ),
        ) { view, p ->
            sis.settings.set(p.uniqueId, PlayerSettings(
                payFromBoxes = view.getBoolean("payFromBoxes") ?: current.payFromBoxes,
                rewardsIntoBoxes = view.getBoolean("rewardsIntoBoxes") ?: current.rewardsIntoBoxes,
                tutorialHidden = !(view.getBoolean("tutorial") ?: !current.tutorialHidden),
            ))
            Messages.send(p, "settings.saved")
            from?.open()
        }
    }

    fun tutorial(player: Player, then: () -> Unit) {
        val ok = button(Messages.get("dialog.tutorial.ok"), 200) { view, p ->
            if (view.getBoolean("hide") == true) sis.settings.set(p.uniqueId, sis.settings[p.uniqueId].copy(tutorialHidden = true))
            then()
        }
        val dialog = Dialog.create { factory ->
            factory.empty()
                .base(
                    DialogBase.builder(Messages.get("dialog.tutorial.title"))
                        .body(Messages.lines("dialog.tutorial.body").map { DialogBody.plainMessage(it, 300) })
                        .inputs(listOf(bool("hide", "dialog.tutorial.hide", true)))
                        .canCloseWithEscape(true)
                        .build(),
                )
                .type(DialogType.notice(ok))
        }
        player.showDialog(dialog)
    }

}

enum class SearchTarget { ALL, REWARDS, COSTS, SHOPS }
