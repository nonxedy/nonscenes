package com.nonxedy.playback

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerRotation
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

// Packet-based cutscene playback
class AsyncPacketPlaybackController(
    private val plugin: JavaPlugin,
    private val updateRate: Int,
    private val rideHeightOffset: Double,
    private val onComplete: () -> Unit,
    private val onCancel: () -> Unit
) : CutscenePlaybackController {

    private val active = AtomicBoolean(false)
    private val cleanedUp = AtomicBoolean(false)
    private var executor: ScheduledExecutorService? = null
    private var carrier: ArmorStand? = null
    private var originalLocation: Location? = null

    private var playerRef: Player? = null
    private var wasFlying = false
    private var wasAllowedFlight = false

    override fun start(player: Player, path: List<Location>, totalDurationMs: Long) {
        if (path.isEmpty()) {
            onComplete()
            return
        }

        active.set(true)
        cleanedUp.set(false)
        playerRef = player

        val pathArray = path.toTypedArray()
        val lastIndex = pathArray.lastIndex
        val startTime = System.currentTimeMillis()
        val intervalNs = 1_000_000_000L / updateRate

        // Remember where the player started so we can bring them back afterwards
        originalLocation = player.location.clone()

        // Prepare the player and the carrier on the main thread (Bukkit requires it)
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (!active.get() || !player.isOnline) {
                cleanup(player)
                return@Runnable
            }

            wasFlying = player.isFlying
            wasAllowedFlight = player.allowFlight

            // Do not let the player see their own body during the cutscene
            player.hidePlayer(player)

            // Keep the player hovering so they don't fall or drift while riding
            player.setAllowFlight(true)
            player.setFlying(true)

            val stand = spawnCarrier(player, pathArray[0])
            carrier = stand
            stand.addPassenger(player)
        })

        executor = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "nonscenes-async-playback-${player.uniqueId.toString().take(8)}")
        }.apply {
            // scheduleWithFixedDelay keeps a consistent interval instead of "burst
            // catching up" after any scheduling hiccup, which reduces visible jitter
            scheduleWithFixedDelay({
                if (!active.get() || !player.isOnline) {
                    active.set(false)
                    shutdown()
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        cleanup(player)
                        onCancel()
                    })
                    return@scheduleWithFixedDelay
                }

                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed >= totalDurationMs) {
                    active.set(false)
                    shutdown()
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        cleanup(player)
                        onComplete()
                    })
                    return@scheduleWithFixedDelay
                }

                val progress = elapsed.toDouble() / totalDurationMs.toDouble()
                val loc = samplePath(pathArray, lastIndex, progress)

                val stand = carrier
                if (stand != null && PacketEvents.getAPI().isInitialized) {
                    try {
                        // Move the carrier (and therefore the player's camera) to the
                        // interpolated point. The stand's Y is offset so the sitting
                        // eye height matches the recorded standing eye height
                        val carrierPacket = WrapperPlayServerEntityTeleport(
                            stand.entityId,
                            Vector3d(loc.x, loc.y + rideHeightOffset, loc.z),
                            loc.yaw,
                            loc.pitch,
                            true
                        )
                        PacketEvents.getAPI().playerManager.sendPacket(player, carrierPacket)

                        // Lock the player's own look direction (esp. pitch) each update
                        val rotationPacket = WrapperPlayServerPlayerRotation(loc.yaw, loc.pitch)
                        PacketEvents.getAPI().playerManager.sendPacket(player, rotationPacket)
                    } catch (_: Exception) {
                    }
                }

                val index = (progress * lastIndex).toInt().coerceIn(0, lastIndex)
                if (index % (updateRate / 4).coerceAtLeast(1) == 0) {
                    val text = " ${index + 1} / ${pathArray.size}"
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        if (player.isOnline) {
                            player.sendActionBar(MiniMessage.miniMessage().deserialize(text))
                        }
                    })
                }
            }, 0L, intervalNs, TimeUnit.NANOSECONDS)
        }
    }

    override fun stop() {
        active.set(false)
        shutdown()
        val p = playerRef
        if (p != null) {
            Bukkit.getScheduler().runTask(plugin, Runnable { cleanup(p) })
        }
    }

    override fun isActive(): Boolean = active.get()

    
    // Interpolates between the two neighbouring path points using the fractional
    // progress `t` (in 0..1). `lastIndex` is the index of the final path point
    private fun samplePath(path: Array<Location>, lastIndex: Int, t: Double): Location {
        val scaled = t * lastIndex
        val idx = scaled.toInt().coerceIn(0, lastIndex)
        val frac = (scaled - idx).coerceIn(0.0, 1.0)

        val from = path[idx]
        val to = path[(idx + 1).coerceAtMost(lastIndex)]

        val x = from.x + (to.x - from.x) * frac
        val y = from.y + (to.y - from.y) * frac
        val z = from.z + (to.z - from.z) * frac
        val yaw = lerpAngle(from.yaw, to.yaw, frac)
        val pitch = (from.pitch + (to.pitch - from.pitch) * frac).toFloat()

        return Location(from.world, x, y, z, yaw, pitch)
    }

    private fun lerpAngle(from: Float, to: Float, t: Double): Float {
        var delta = ((to - from) % 360f + 360f) % 360f
        if (delta > 180f) delta -= 360f
        return (from + delta * t).toFloat()
    }

    private fun spawnCarrier(player: Player, at: Location): ArmorStand {
        return player.world.spawn(at.clone().add(0.0, rideHeightOffset, 0.0), ArmorStand::class.java) { stand ->
            stand.isVisible = false
            stand.setGravity(false)
            stand.isInvulnerable = true
            stand.isSilent = true
            stand.setBasePlate(false)
            stand.isSmall = true
            stand.isMarker = true
            stand.customName = null
            stand.setCollidable(false)
        }
    }

    private fun cleanup(player: Player) {
        shutdown()
        if (!cleanedUp.compareAndSet(false, true)) return
        playerRef = null

        val stand = carrier
        carrier = null
        if (stand != null && !stand.isDead) {
            stand.removePassenger(player)
            stand.remove()
        }

        if (!player.isOnline) {
            originalLocation = null
            return
        }

        player.showPlayer(player)
        player.setFlying(wasFlying)
        player.setAllowFlight(wasAllowedFlight)

        // Bring the player back to where they started the cutscene.
        originalLocation?.let { loc ->
            player.teleport(loc)
        }
        originalLocation = null
    }

    private fun shutdown() {
        try {
            executor?.shutdownNow()
        } catch (_: Exception) {
        }
        executor = null
    }
}
