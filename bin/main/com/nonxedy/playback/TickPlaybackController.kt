package com.nonxedy.playback

import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.util.concurrent.atomic.AtomicBoolean

// Main-thread cutscene playback
class TickPlaybackController(
    private val plugin: JavaPlugin,
    private val onComplete: () -> Unit,
    private val onCancel: () -> Unit
) : CutscenePlaybackController {

    private val active = AtomicBoolean(false)
    private val cleanedUp = AtomicBoolean(false)
    private var task: BukkitTask? = null
    private var playerRef: Player? = null
    private var originalLocation: Location? = null
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
        originalLocation = player.location.clone()

        val startTime = System.currentTimeMillis()
        val pathArray = path.toTypedArray()
        val lastIndex = pathArray.lastIndex

        wasFlying = player.isFlying
        wasAllowedFlight = player.allowFlight
        player.hidePlayer(player)
        player.setAllowFlight(true)
        player.setFlying(true)

        task = object : BukkitRunnable() {
            override fun run() {
                if (!player.isOnline || !active.get()) {
                    cancel()
                    cleanup()
                    onCancel()
                    return
                }

                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed >= totalDurationMs) {
                    cancel()
                    cleanup()
                    onComplete()
                    return
                }

                val progress = elapsed.toDouble() / totalDurationMs.toDouble()
                val loc = samplePath(pathArray, lastIndex, progress)

                val cx = loc.blockX shr 4
                val cz = loc.blockZ shr 4
                if (!loc.world.isChunkLoaded(cx, cz)) {
                    return
                }

                player.teleport(loc)
                player.setRotation(loc.yaw, loc.pitch)

                val index = (progress * lastIndex).toInt().coerceIn(0, lastIndex)
                val frameDisplay = " ${index + 1} / ${pathArray.size}"
                player.sendActionBar(MiniMessage.miniMessage().deserialize(frameDisplay))
            }
        }.runTaskTimer(plugin, 0L, 1L)
    }

    override fun stop() {
        active.set(false)
        task?.cancel()
        task = null
        cleanup()
    }

    override fun isActive(): Boolean = active.get()

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

    private fun cleanup() {
        if (!cleanedUp.compareAndSet(false, true)) return
        val player = playerRef ?: return
        playerRef = null

        if (!player.isOnline) {
            originalLocation = null
            return
        }

        player.showPlayer(player)
        player.setFlying(wasFlying)
        player.setAllowFlight(wasAllowedFlight)

        originalLocation?.let { loc ->
            player.teleport(loc)
        }
        originalLocation = null
    }
}
