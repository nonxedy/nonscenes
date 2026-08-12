package com.nonxedy.recording

import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask

class RecordingBossBar(
    private val plugin: JavaPlugin,
    private val player: Player,
    private val cutsceneName: String,
    private val totalFrames: Int,
    barStyle: BarStyle,
    barColor: BarColor,
    private val updateEveryNFrames: Int
) {
    private val adventureColor = mapBukkitColor(barColor)
    private val adventureStyle = mapBukkitStyle(barStyle)

    private val bossBar: BossBar = BossBar.bossBar(
        formatTitle(0),
        0f,
        adventureColor,
        adventureStyle
    )

    private var lastReportedFrame = -1
    private var task: BukkitTask? = null

    init {
        player.showBossBar(bossBar)
        task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (!player.isOnline) {
                remove()
            }
        }, 0L, 20L)
    }

    fun onFrameCaptured(currentFrame: Int) {
        if (currentFrame <= lastReportedFrame) return
        lastReportedFrame = currentFrame

        if (currentFrame % updateEveryNFrames == 0 || currentFrame >= totalFrames) {
            val progress = (currentFrame.toDouble() / totalFrames.toDouble()).toFloat()
            bossBar.progress(progress.coerceIn(0.0f, 1.0f))
            bossBar.name(formatTitle(currentFrame))
        }

        if (currentFrame >= totalFrames) {
            bossBar.progress(1.0f)
            bossBar.name(formatTitle(totalFrames))
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                remove()
            }, 40L)
        }
    }

    fun remove() {
        task?.cancel()
        task = null
        player.hideBossBar(bossBar)
    }

    private fun formatTitle(current: Int): net.kyori.adventure.text.Component {
        val percent = (current.toDouble() / totalFrames.toDouble() * 100).toInt()
        return MiniMessage.miniMessage().deserialize(
            "<gray>Recording <aqua>$cutsceneName</aqua> <dark_gray>|</dark_gray> <yellow>$percent%</yellow> <dark_gray>($current/$totalFrames)</dark_gray>"
        )
    }

    private fun mapBukkitColor(color: BarColor): BossBar.Color = when (color) {
        BarColor.PINK -> BossBar.Color.PINK
        BarColor.BLUE -> BossBar.Color.BLUE
        BarColor.RED -> BossBar.Color.RED
        BarColor.GREEN -> BossBar.Color.GREEN
        BarColor.YELLOW -> BossBar.Color.YELLOW
        BarColor.PURPLE -> BossBar.Color.PURPLE
        BarColor.WHITE -> BossBar.Color.WHITE
    }

    private fun mapBukkitStyle(style: BarStyle): BossBar.Overlay = when (style) {
        BarStyle.SOLID -> BossBar.Overlay.PROGRESS
        BarStyle.SEGMENTED_6 -> BossBar.Overlay.NOTCHED_6
        BarStyle.SEGMENTED_10 -> BossBar.Overlay.NOTCHED_10
        BarStyle.SEGMENTED_12 -> BossBar.Overlay.NOTCHED_12
        BarStyle.SEGMENTED_20 -> BossBar.Overlay.NOTCHED_20
    }
}
