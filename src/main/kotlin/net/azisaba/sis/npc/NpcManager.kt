package net.azisaba.sis.npc

import net.azisaba.sis.config.PluginConfig
import net.azisaba.sis.item.mythic.MythicInstanceProvider
import net.azisaba.sis.model.NpcSpec
import net.azisaba.sis.model.Shop
import net.azisaba.sis.service.ShopChangeListener
import net.azisaba.sis.service.ShopService
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.entity.Display
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mannequin
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * The entities standing at shops. They are never saved with the world (non-persistent): a shop's NPC is spawned
 * when its chunk loads and removed when it unloads, so there are no duplicates or leftovers after crashes, and a
 * shop file is the only truth about what the NPC looks like.
 */
class NpcManager(
    private val plugin: JavaPlugin,
    private val shops: ShopService,
    private val config: () -> PluginConfig,
) : ShopChangeListener {
    val shopKey = NamespacedKey(plugin, "shop")

    private class Spawned(val npc: UUID?, val label: UUID?)

    private val spawned = HashMap<String, Spawned>()
    private val byEntity = HashMap<UUID, String>()

    /** Shops whose NPC could not be spawned (unknown mob, MythicMobs missing, ...). */
    private val failed = HashSet<String>()

    private fun needsEntity(shop: Shop) = shop.npc is NpcSpec.Vanilla || shop.npc is NpcSpec.Mythic

    fun shopOf(entity: Entity): Shop? {
        val id = byEntity[entity.uniqueId] ?: entity.persistentDataContainer.get(shopKey, PersistentDataType.STRING) ?: return null
        return shops.get(id)
    }

    fun isShopEntity(entity: Entity): Boolean =
        entity.uniqueId in byEntity || entity.persistentDataContainer.has(shopKey, PersistentDataType.STRING)

    fun entityOf(shop: Shop): Entity? = spawned[shop.id]?.npc?.let(Bukkit::getEntity)

    fun spawnLoaded() {
        for (shop in shops.all) if (shop.location.isLoaded()) spawn(shop)
    }

    /** Set while menus are closed for a reload or shutdown (see [recapture]). */
    var closing = false

    fun despawnAll() {
        spawned.keys.toList().forEach(::despawn)
        failed.clear()
    }

    fun spawn(shop: Shop) {
        despawn(shop.id)
        val location = shop.location.toBukkit() ?: return
        if (!location.isChunkLoaded) return
        var created: Entity? = null
        val npc = runCatching { createNpc(shop, location)?.also { created = it; prepare(it, shop) } }.getOrElse {
            // An entity that could not be set up must not stay (it would be a normal, killable mob).
            created?.remove()
            if (shop.id !in failed) plugin.logger.warning("Could not spawn the NPC of ${shop.id}: $it")
            null
        }
        // Remembered so heal() does not despawn and respawn it every few seconds (and log each time); a change
        // of the shop, a reload or /sis respawn tries again.
        if (npc == null && needsEntity(shop)) failed += shop.id else failed -= shop.id
        val label = createLabel(shop, location, npc)
        spawned[shop.id] = Spawned(npc?.uniqueId, label?.uniqueId)
        npc?.let { byEntity[it.uniqueId] = shop.id }
        label?.let { byEntity[it.uniqueId] = shop.id }
    }

    fun despawn(shopId: String) {
        val s = spawned.remove(shopId) ?: return
        listOfNotNull(s.npc, s.label).forEach { id ->
            byEntity.remove(id)
            Bukkit.getEntity(id)?.remove()
        }
    }

    private fun createNpc(shop: Shop, location: Location): Entity? = when (val npc = shop.npc) {
        is NpcSpec.Vanilla -> {
            val snapshot = npc.snapshot?.let { runCatching { Bukkit.getEntityFactory().createEntitySnapshot(it) }.getOrNull() }
            val entity = if (snapshot != null && snapshot.entityType == npc.type) snapshot.createEntity(location)
            else location.world.spawnEntity(location, npc.type)
            val eq = (entity as? LivingEntity)?.equipment
            if (eq != null && npc.equipment != null) {
                eq.helmet = npc.equipment.getOrNull(0)
                eq.chestplate = npc.equipment.getOrNull(1)
                eq.leggings = npc.equipment.getOrNull(2)
                eq.boots = npc.equipment.getOrNull(3)
                eq.setItemInMainHand(npc.equipment.getOrNull(4))
                eq.setItemInOffHand(npc.equipment.getOrNull(5))
            }
            entity
        }
        is NpcSpec.Mythic -> if (MythicInstanceProvider.isLoaded()) MythicInstanceProvider.getInstance().spawnMythicMob(location, npc.mobId) else null
        is NpcSpec.Citizens, NpcSpec.Block -> null
    }

    private fun prepare(entity: Entity, shop: Shop) {
        entity.persistentDataContainer.set(shopKey, PersistentDataType.STRING, shop.id)
        entity.isPersistent = false
        entity.isInvulnerable = true
        entity.isSilent = true
        entity.setGravity(false)
        entity.customName(null)
        entity.isCustomNameVisible = false
        entity.setRotation(shop.location.yaw, shop.location.pitch)
        if (entity is LivingEntity) {
            entity.setAI(false)
            entity.removeWhenFarAway = false
            entity.canPickupItems = false
            entity.isCollidable = false
            entity.bodyYaw = shop.location.yaw
        }
        if (entity is Mob) entity.isAware = false
        if (entity is LivingEntity) entity.isInvisible = shop.invisible
        // Ageing and sun burning still tick without AI: babies would grow up and helmets wear out.
        if (entity is org.bukkit.entity.Breedable) entity.ageLock = true
        if (entity is org.bukkit.entity.Zombie) entity.setShouldBurnInDay(false)
        if (entity is org.bukkit.entity.AbstractSkeleton) entity.setShouldBurnInDay(false)
        if (entity is org.bukkit.entity.Phantom) entity.setShouldBurnInDay(false)
        if (entity is Mannequin) {
            entity.isImmovable = true
            entity.description = null
        }
    }

    private fun createLabel(shop: Shop, location: Location, npc: Entity?): TextDisplay? {
        if (!shop.nameVisible || shop.name.isBlank() || shop.npc is NpcSpec.Citizens) return null
        val height = npc?.height ?: 1.0
        val at = location.clone().add(0.0, height + config().nameOffset, 0.0)
        return location.world.spawn(at, TextDisplay::class.java) { d ->
            d.text(shops.displayName(shop))
            d.billboard = Display.Billboard.CENTER
            d.isPersistent = false
            d.isShadowed = true
            d.persistentDataContainer.set(shopKey, PersistentDataType.STRING, shop.id)
        }
    }

    /** Respawns NPCs that disappeared (killed by another plugin, MythicMobs despawned them, ...). */
    fun heal() {
        for (shop in shops.all) {
            if (!shop.location.isLoaded() || shop.id in failed) continue
            val s = spawned[shop.id]
            val dead = s == null || (needsEntity(shop) && (s.npc == null || Bukkit.getEntity(s.npc)?.isValid != true))
            if (dead) spawn(shop)
        }
    }

    fun lookAtPlayers() {
        val range = config().lookRange
        for (shop in shops.all) {
            if (!shop.lookAtPlayer) continue
            val entity = entityOf(shop) as? LivingEntity ?: continue
            val eye = entity.eyeLocation
            val target = entity.world.getNearbyPlayers(eye, range).filter { !it.isInvisible }.minByOrNull { it.location.distanceSquared(eye) }
            val (yaw, pitch) = if (target == null) shop.location.yaw to shop.location.pitch else angles(eye, target)
            entity.setRotation(yaw, pitch)
            entity.bodyYaw = yaw
        }
    }

    private fun angles(from: Location, to: Player): Pair<Float, Float> {
        val dx = to.eyeLocation.x - from.x
        val dy = to.eyeLocation.y - from.y
        val dz = to.eyeLocation.z - from.z
        val yaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
        val pitch = Math.toDegrees(-atan2(dy, sqrt(dx * dx + dz * dz))).toFloat()
        return yaw to pitch
    }

    fun onChunkLoad(world: String, x: Int, z: Int) {
        shops.inChunk(world, x, z).forEach(::spawn)
    }

    fun onChunkUnload(world: String, x: Int, z: Int) {
        shops.inChunk(world, x, z).forEach { despawn(it.id) }
    }

    /** Takes the full look of an entity into the shop (type, variant, equipment, skin, ...). */
    fun capture(entity: Entity): NpcSpec.Vanilla? {
        val snapshot = entity.createSnapshot() ?: return null
        return NpcSpec.Vanilla(entity.type, snapshot.asString)
    }

    /** Sets the skin of a mannequin shop (by player name) and keeps it in the shop's snapshot. */
    fun applySkin(shop: Shop, playerName: String, actor: org.bukkit.command.CommandSender?) {
        val entity = entityOf(shop) as? Mannequin
        if (entity == null) {
            actor?.let { net.azisaba.sis.config.Messages.send(it, "editor.skin-needs-mannequin") }
            return
        }
        entity.profile = io.papermc.paper.datacomponent.item.ResolvableProfile.resolvableProfile().name(playerName).build()
        recapture(shop, entity, actor, "skin")
    }

    /** Puts equipment on the shop's living NPC and keeps it in the snapshot. Index: helmet, chest, legs, boots, main hand, off hand. */
    fun applyEquipment(shop: Shop, items: List<org.bukkit.inventory.ItemStack?>, actor: org.bukkit.command.CommandSender?) {
        val entity = entityOf(shop) as? LivingEntity ?: return
        val eq = entity.equipment ?: return
        eq.helmet = items.getOrNull(0)
        eq.chestplate = items.getOrNull(1)
        eq.leggings = items.getOrNull(2)
        eq.boots = items.getOrNull(3)
        eq.setItemInMainHand(items.getOrNull(4))
        eq.setItemInOffHand(items.getOrNull(5))
        recapture(shop, entity, actor, "equipment")
    }

    private fun recapture(shop: Shop, entity: Entity, actor: org.bukkit.command.CommandSender?, action: String) {
        if (shop.npc !is NpcSpec.Vanilla) return
        val save = Runnable {
            if (!entity.isValid) return@Runnable
            capture(entity)?.let { shop.npc = it }
            shops.changed(shop, actor, action)
        }
        // Closing the equipment menu on shutdown/reload: no task can be scheduled any more, and the NPC is
        // despawned before the next tick, so save right away.
        if (plugin.isEnabled && !closing) Bukkit.getScheduler().runTask(plugin, save) else save.run()
    }

    override fun added(shop: Shop) = spawn(shop)
    override fun removed(shop: Shop) = despawn(shop.id)
    override fun changed(shop: Shop, respawn: Boolean) {
        if (respawn) spawn(shop) else refreshLabel(shop)
    }

    private fun refreshLabel(shop: Shop) {
        val s = spawned[shop.id] ?: return
        val label = s.label?.let(Bukkit::getEntity) as? TextDisplay
        val wantsLabel = shop.nameVisible && shop.name.isNotBlank() && shop.npc !is NpcSpec.Citizens
        when {
            label != null && wantsLabel -> label.text(shops.displayName(shop))
            label == null && !wantsLabel -> Unit
            else -> spawn(shop)
        }
    }
}
