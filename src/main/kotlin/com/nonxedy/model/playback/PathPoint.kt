package com.nonxedy.model.playback

import org.bukkit.Location

data class PathPoint(
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
    val worldName: String,
    val timeMs: Long
) {
    fun toLocation(resolveWorld: (String) -> org.bukkit.World?): Location? {
        val world = resolveWorld(worldName) ?: return null
        return Location(world, x, y, z, yaw, pitch)
    }
}
