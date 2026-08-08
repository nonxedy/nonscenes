package com.nonxedy.model.recording

import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle

data class RecordingSettings(
    val progressBarEnabled: Boolean = true,
    val barStyle: BarStyle = BarStyle.SOLID,
    val barColor: BarColor = BarColor.BLUE,
    val captureIntervalTicks: Int = 1
)
