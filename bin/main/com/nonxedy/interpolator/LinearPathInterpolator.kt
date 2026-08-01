package com.nonxedy.interpolator

import com.nonxedy.model.CutsceneFrame
import com.nonxedy.model.playback.PathPoint
import org.bukkit.Location

class LinearPathInterpolator : PathInterpolator {

    override fun interpolate(frames: List<CutsceneFrame>, t: Double): PathPoint {
        require(frames.isNotEmpty()) { "Frame list must not be empty" }
        if (frames.size == 1 || t <= 0.0) {
            return frames.first().toPathPoint(0L)
        }
        if (t >= 1.0) {
            return frames.last().toPathPoint(0L)
        }

        val segmentCount = frames.size - 1
        val scaledT = t * segmentCount
        val index = scaledT.toInt()
        val localT = scaledT - index

        val from = frames[index.coerceIn(0, frames.lastIndex)]
        val to = frames[(index + 1).coerceIn(0, frames.lastIndex)]

        val loc = lerpLocation(from.location, to.location, localT.toFloat())
        return PathPoint(
            x = loc.x,
            y = loc.y,
            z = loc.z,
            yaw = loc.yaw,
            pitch = loc.pitch,
            worldName = loc.world?.name ?: from.worldName,
            timeMs = 0L
        )
    }

    private fun lerpLocation(a: Location, b: Location, t: Float): Location {
        val world = a.world ?: b.world
        return Location(
            world,
            a.x + (b.x - a.x) * t,
            a.y + (b.y - a.y) * t,
            a.z + (b.z - a.z) * t,
            lerpAngle(a.yaw, b.yaw, t),
            lerpAngle(a.pitch, b.pitch, t)
        )
    }

    private fun lerpAngle(from: Float, to: Float, t: Float): Float {
        var delta = (to - from) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return (from + delta * t)
    }
}

private fun CutsceneFrame.toPathPoint(timeMs: Long): PathPoint = PathPoint(
    x = location.x,
    y = location.y,
    z = location.z,
    yaw = location.yaw,
    pitch = location.pitch,
    worldName = worldName,
    timeMs = timeMs
)
