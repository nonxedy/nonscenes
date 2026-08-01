package com.nonxedy.model.playback

data class PlaybackSettings(
    val updateRate: Int = 60,
    val mode: PlaybackMode = PlaybackMode.TICK,
    val interpolation: InterpolationType = InterpolationType.CATMULL_ROM,
    val smoothRotation: Boolean = true,
    val bakePath: Boolean = true
) {
    init {
        require(updateRate in 20..240) { "update-rate must be between 20 and 240" }
    }

    val updateIntervalMs: Long
        get() = 1000L / updateRate

    val isAsyncPacket: Boolean
        get() = mode == PlaybackMode.ASYNC_PACKET
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
