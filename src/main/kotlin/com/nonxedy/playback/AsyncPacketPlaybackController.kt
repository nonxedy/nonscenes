package com.nonxedy.playback

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerPositionAndLook
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
import java.util.concurrent.atomic.AtomicInteger

class AsyncPacketPlaybackController(
    private val plugin: JavaPlugin,
    private val updateRate: Int,
    private val onComplete: () -> Unit,
    private val onCancel: () -> Unit
) : CutscenePlaybackController {

    private val active = AtomicBoolean(false)
    private var executor: ScheduledExecutorService? = null
    private var vehicle: ArmorStand? = null
    private val teleportId = AtomicInteger(-1000)
    private var finalLocation: Location? = null

    override fun start(player: Player, path: List<Location>, totalDurationMs: Long) {
        if (path.isEmpty()) {
            onComplete()
            return
        }

        active.set(true)
        val pathArray = path.toTypedArray()
        val lastIndex = pathArray.lastIndex
        val startTime = System.currentTimeMillis()
        val intervalNs = 1_000_000_000L / updateRate
        finalLocation = pathArray.last()

        val carrier = spawnCarrier(player, pathArray[0])
        vehicle = carrier

        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (!active.get() || !player.isOnline) {
                cleanup(player)
                return@Runnable
            }
            carrier.addPassenger(player)
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
                        val entityPacket = WrapperPlayServerEntityTeleport(
                            carrier.entityId,
                            Vector3d(loc.x, loc.y - PASSENGER_OFFSET_Y, loc.z),
                            loc.yaw,
                            loc.pitch,
                            true
                        )
                        PacketEvents.getAPI().playerManager.sendPacket(player, entityPacket)

                        val rotPacket = WrapperPlayServerPlayerPositionAndLook(
                            Vector3d(0.0, 0.0, 0.0),
                            loc.yaw,
                            loc.pitch,
                            RELATIVE_XYZ_FLAGS,
                            teleportId.decrementAndGet(),
                            false
                        )
                        PacketEvents.getAPI().playerManager.sendPacket(player, rotPacket)
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
        try {
            executor?.shutdownNow()
        } catch (_: Exception) {
        }
    }

    override fun isActive(): Boolean = active.get()

    private fun cleanup(player: Player) {
        active.set(false)
        try {
            executor?.shutdownNow()
        } catch (_: Exception) {
        }
        executor = null

        vehicle?.let { carrier ->
            if (!carrier.isDead) {
                carrier.removePassenger(player)
                carrier.remove()
            }
        }
        vehicle = null

        finalLocation?.let { loc ->
            if (player.isOnline) {
                player.teleport(loc)
            }
        }
        finalLocation = null
    }

    private fun spawnCarrier(player: Player, at: Location): ArmorStand {
        return player.world.spawn(at.clone().subtract(0.0, PASSENGER_OFFSET_Y, 0.0), ArmorStand::class.java) { stand ->
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

    companion object {
        private const val PASSENGER_OFFSET_Y = 0.25
        private const val RELATIVE_XYZ_FLAGS: Byte = 0x07
    }
}
