package com.nonxedy.playback

import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.util.concurrent.atomic.AtomicBoolean

class TickPlaybackController(
    private val plugin: JavaPlugin,
    private val onComplete: () -> Unit,
    private val onCancel: () -> Unit
) : CutscenePlaybackController {

    private val active = AtomicBoolean(false)
    private var task: BukkitTask? = null

    override fun start(player: Player, path: List<Location>, totalDurationMs: Long) {
        if (path.isEmpty()) {
            onComplete()
            return
        }

        active.set(true)
        val startTime = System.currentTimeMillis()
        val pathArray = path.toTypedArray()
        val lastIndex = pathArray.lastIndex

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
                    player.teleport(pathArray[lastIndex])
                    cancel()
                    cleanup()
                    onComplete()
                    return
                }

                val progress = elapsed.toDouble() / totalDurationMs.toDouble()
                val index = (progress * lastIndex).toInt().coerceIn(0, lastIndex)
                val loc = pathArray[index]

                val cx = loc.blockX shr 4
                val cz = loc.blockZ shr 4
                if (!loc.world.isChunkLoaded(cx, cz)) {
                    return
                }

                player.teleport(loc)

                val frameDisplay = " ${index + 1} / ${pathArray.size}"
                player.sendActionBar(MiniMessage.miniMessage().deserialize(frameDisplay))
            }
        }.runTaskTimer(plugin, 0L, 1L)
    }

    override fun stop() {
        active.set(false)
        task?.cancel()
        task = null
    }

    override fun isActive(): Boolean = active.get()

    private fun cleanup() {
        active.set(false)
        task = null
    }
}
