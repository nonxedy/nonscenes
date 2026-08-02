package com.nonxedy.model.playback

data class PlaybackSettings private constructor(
    val updateRate: Int,
    val mode: PlaybackMode = PlaybackMode.TICK,
    val interpolation: InterpolationType = InterpolationType.CATMULL_ROM,
    val smoothRotation: Boolean = true,
    val bakePath: Boolean = true
) {
    val updateIntervalMs: Long
        get() = 1000L / updateRate

    val isAsyncPacket: Boolean
        get() = mode == PlaybackMode.ASYNC_PACKET

    companion object {
        const val MIN_UPDATE_RATE: Int = 20
        const val MAX_UPDATE_RATE: Int = 240

        operator fun invoke(
            updateRate: Int = 60,
            mode: PlaybackMode = PlaybackMode.TICK,
            interpolation: InterpolationType = InterpolationType.CATMULL_ROM,
            smoothRotation: Boolean = true,
            bakePath: Boolean = true
        ): PlaybackSettings {
            return PlaybackSettings(
                updateRate = updateRate.coerceIn(MIN_UPDATE_RATE, MAX_UPDATE_RATE),
                mode = mode,
                interpolation = interpolation,
                smoothRotation = smoothRotation,
                bakePath = bakePath
            )
        }
    }
}

enum class PlaybackMode {
    TICK,
    ASYNC_PACKET
}

enum class InterpolationType {
    LINEAR,
    CATMULL_ROM,
    BEZIER
}
