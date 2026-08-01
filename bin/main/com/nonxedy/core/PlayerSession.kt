package com.nonxedy.core

import java.util.UUID

// Sealed class representing different session states a player can be in
sealed class PlayerSession {
    abstract val playerId: UUID
    abstract val name: String

    data class RecordingCountdown(
        override val playerId: UUID,
        override val name: String,
        val totalFrames: Int,
        val secondsRemaining: Int
    ) : PlayerSession()

    data class Recording(
        override val playerId: UUID,
        override val name: String,
        val frameCount: Int
    ) : PlayerSession()

    data class Playback(
        override val playerId: UUID,
        override val name: String,
        val currentFrame: Int,
        val totalFrames: Int
    ) : PlayerSession()

    data class PathVisualization(
        override val playerId: UUID,
        override val name: String,
        val duration: Int
    ) : PlayerSession()

    // Returns a user-friendly description of the session
    fun getDescription(): String = when (this) {
        is RecordingCountdown -> "waiting to record cutscene '$name' ($secondsRemaining seconds remaining)"
        is Recording -> "recording cutscene '$name' ($frameCount frames)"
        is Playback -> "watching cutscene '$name' ($currentFrame/$totalFrames)"
        is PathVisualization -> "visualizing path for '$name' ($duration seconds)"
    }
}
