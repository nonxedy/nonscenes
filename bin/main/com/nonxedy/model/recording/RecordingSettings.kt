package com.nonxedy.model.recording

import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle

data class RecordingSettings(
    val progressBarEnabled: Boolean = true,
    val barStyle: BarStyle = BarStyle.SOLID,
    val barColor: BarColor = BarColor.BLUE,
    val updateEveryNFrames: Int = 1,
    val captureIntervalTicks: Int = 1
)
