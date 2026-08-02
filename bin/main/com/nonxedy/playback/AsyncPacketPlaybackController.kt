package com.nonxedy.playback

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerPositionAndLook
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

// Packet-based cutscene playback
class AsyncPacketPlaybackController(
    private val plugin: JavaPlugin,
    private val updateRate: Int,
    private val onComplete: () -> Unit,
    private val onCancel: () -> Unit
) : CutscenePlaybackController {

    private val active = AtomicBoolean(false)
    private val cleanedUp = AtomicBoolean(false)
    private var executor: ScheduledExecutorService? = null
    private val teleportId = AtomicInteger(-1000)
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

        // Prepare the player on the main thread (Bukkit requires it)
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (!active.get() || !player.isOnline) {
                cleanup(player)
                return@Runnable
            }

            wasFlying = player.isFlying
            wasAllowedFlight = player.allowFlight

            // Do not let the player see their own body during the cutscene
            player.hidePlayer(player)

            // Keep the player hovering in place so they don't fall or drift while
            // the packets move them along the path
            player.setAllowFlight(true)
            player.setFlying(true)

            player.teleport(pathArray[0])
        })

        executor = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "nonscenes-async-playback-${player.uniqueId.toString().take(8)}")
        }.apply {
            scheduleAtFixedRate({
                if (!active.get() || !player.isOnline) {
                    active.set(false)
                    shutdown()
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        cleanup(player)
                        onCancel()
                    })
                    return@scheduleAtFixedRate
                }

                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed >= totalDurationMs) {
                    active.set(false)
                    shutdown()
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        cleanup(player)
                        onComplete()
                    })
                    return@scheduleAtFixedRate
                }

                val progress = elapsed.toDouble() / totalDurationMs.toDouble()
                val loc = samplePath(pathArray, lastIndex, progress)

                if (PacketEvents.getAPI().isInitialized) {
                    try {
                        // Absolute position + absolute rotation. Resending this on every
                        // update locks the player's camera to the cutscene and keeps their
                        // position exact, so the camera cannot be moved independently.
                        val packet = WrapperPlayServerPlayerPositionAndLook(
                            Vector3d(loc.x, loc.y, loc.z),
                            loc.yaw,
                            loc.pitch,
                            ABSOLUTE_FLAGS,
                            teleportId.decrementAndGet(),
                            false
                        )
                        PacketEvents.getAPI().playerManager.sendPacket(player, packet)
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

    private fun cleanup(player: Player) {
        shutdown()
        if (!cleanedUp.compareAndSet(false, true)) return
        playerRef = null

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

    companion object {
        // 0x00 = all of x, y, z, yaw and pitch are absolute.
        private const val ABSOLUTE_FLAGS: Byte = 0x00
    }
}
