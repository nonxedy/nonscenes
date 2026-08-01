package com.nonxedy.recording

import com.nonxedy.model.Cutscene
import com.nonxedy.model.CutsceneFrame
import com.nonxedy.model.recording.RecordingSettings
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask

class CutsceneRecorder(
    private val plugin: JavaPlugin,
    private val settings: RecordingSettings,
    private val onComplete: (Cutscene) -> Unit,
    private val onCancel: () -> Unit
) {
    private var task: BukkitTask? = null
    private val frames = mutableListOf<CutsceneFrame>()
    private var bossBar: RecordingBossBar? = null
    private var isRunning = false

    fun start(player: Player, name: String, totalFrames: Int) {
        if (isRunning) return
        isRunning = true
        frames.clear()

        if (settings.progressBarEnabled) {
            bossBar = RecordingBossBar(
                plugin, player, name, totalFrames,
                settings.barStyle, settings.barColor, settings.updateEveryNFrames
            )
        }

        val frameDurationMs = (1000.0 / (20.0 / settings.captureIntervalTicks.coerceAtLeast(1))).toLong().coerceAtLeast(1L)
        var nextFrameAtMs = System.currentTimeMillis()
        var frameCount = 0

        task = object : BukkitRunnable() {
            override fun run() {
                if (!player.isOnline || !isRunning) {
                    cancel()
                    cleanup()
                    onCancel()
                    return
                }

                val now = System.currentTimeMillis()
                while (frameCount < totalFrames && now >= nextFrameAtMs) {
                    frames.add(CutsceneFrame(player.location.clone()))
                    frameCount++
                    nextFrameAtMs += frameDurationMs
                    bossBar?.onFrameCaptured(frameCount)
                }

                if (frameCount >= totalFrames) {
                    cancel()
                    cleanup()
                    val cutscene = Cutscene(name, frames.toList(), frameDurationMs)
                    onComplete(cutscene)
                }
            }
        }.runTaskTimer(plugin, 0L, settings.captureIntervalTicks.coerceAtLeast(1).toLong())
    }

    fun cancel() {
        isRunning = false
        task?.cancel()
        cleanup()
    }

    private fun cleanup() {
        task = null
        bossBar?.remove()
        bossBar = null
    }
}
