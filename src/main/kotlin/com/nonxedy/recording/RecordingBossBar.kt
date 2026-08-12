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
    private val totalSeconds: Int,
    barStyle: BarStyle,
    barColor: BarColor
) {
    private val adventureColor = mapBukkitColor(barColor)
    private val adventureStyle = mapBukkitStyle(barStyle)

    private val bossBar: BossBar = BossBar.bossBar(
        formatTitle(0f),
        0f,
        adventureColor,
        adventureStyle
    )

    private var task: BukkitTask? = null

    init {
        player.showBossBar(bossBar)
        task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (!player.isOnline) {
                remove()
            }
        }, 0L, 20L)
    }

    fun onProgress(progress: Double) {
        val clampedProgress = progress.coerceIn(0.0, 1.0).toFloat()
        bossBar.progress(clampedProgress)
        bossBar.name(formatTitle(clampedProgress))
    }

    fun remove() {
        task?.cancel()
        task = null
        player.hideBossBar(bossBar)
    }

    private fun formatTitle(progress: Float): net.kyori.adventure.text.Component {
        val percent = (progress * 100).toInt().coerceIn(0, 100)
        val elapsedSeconds = (progress * totalSeconds).toInt().coerceIn(0, totalSeconds)
        return MiniMessage.miniMessage().deserialize(
            "<gray>Recording <aqua>$cutsceneName</aqua> <dark_gray>|</dark_gray> <yellow>$percent%</yellow> <dark_gray>($elapsedSeconds/$totalSeconds s)</dark_gray>"
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
