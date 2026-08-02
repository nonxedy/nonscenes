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

/**
 * Packet-based cutscene playback.
 *
 * The camera is moved by sending an absolute `PlayerPositionAndLook` packet to
 * the player on every update. Because the packet carries absolute coordinates
 * AND absolute rotation and is resent every update, it both:
 *  - locks the player's camera to the cutscene (they can no longer move/look away), and
 *  - keeps the player positioned exactly on the baked path.
 *
 * The player is NOT mounted on a vehicle and is kept standing, so there is no
 * sitting pose (which used to drop the camera by roughly one block) and the eye
 * height matches the one captured during recording.
 *
 * The player is hidden from their own view for the duration of the playback so
 * they don't see their own body during the cutscene.
 */
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
    private var finalLocation: Location? = null

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
        finalLocation = pathArray.last()

        // Prepare the player on the main thread (Bukkit requires it).
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (!active.get() || !player.isOnline) {
                cleanup(player)
                return@Runnable
            }

            wasFlying = player.isFlying
            wasAllowedFlight = player.allowFlight

            // Do not let the player see their own body during the cutscene.
            player.hidePlayer(player)

            // Keep the player hovering in place so they don't fall or drift while
            // the packets move them along the path.
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
                val index = (progress * lastIndex).toInt().coerceIn(0, lastIndex)
                val loc = pathArray[index]

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

    private fun cleanup(player: Player) {
        shutdown()
        if (!cleanedUp.compareAndSet(false, true)) return
        playerRef = null

        if (!player.isOnline) {
            finalLocation = null
            return
        }

        player.showPlayer(player)
        player.setFlying(wasFlying)
        player.setAllowFlight(wasAllowedFlight)

        finalLocation?.let { loc ->
            player.teleport(loc)
        }
        finalLocation = null
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
