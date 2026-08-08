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

    fun start(player: Player, name: String, durationSeconds: Int) {
        if (isRunning) return
        isRunning = true
        frames.clear()

        val captureIntervalTicks = settings.captureIntervalTicks.coerceAtLeast(1)
        val frameDurationMs = (1000.0 / (20.0 / captureIntervalTicks)).toLong().coerceAtLeast(1L)
        val recordingDurationMs = durationSeconds * 1000L
        val startedAtMs = System.currentTimeMillis()
        var nextFrameAtMs = startedAtMs

        if (settings.progressBarEnabled) {
            bossBar = RecordingBossBar(
                plugin, player, name, durationSeconds,
                settings.barStyle, settings.barColor
            )
        }

        task = object : BukkitRunnable() {
            override fun run() {
                if (!player.isOnline || !isRunning) {
                    cancel()
                    cleanup()
                    onCancel()
                    return
                }

                val now = System.currentTimeMillis()
                while (now >= nextFrameAtMs && nextFrameAtMs < startedAtMs + recordingDurationMs) {
                    frames.add(CutsceneFrame(player.location.clone()))
                    nextFrameAtMs += frameDurationMs
                    bossBar?.onProgress((now - startedAtMs).toDouble() / recordingDurationMs.toDouble())
                }

                if (now >= startedAtMs + recordingDurationMs) {
                    bossBar?.onProgress(1.0)
                    cancel()
                    cleanup()
                    val cutscene = Cutscene(name, frames.toList(), frameDurationMs)
                    onComplete(cutscene)
                }
            }
        }.runTaskTimer(plugin, 0L, captureIntervalTicks.toLong())
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
