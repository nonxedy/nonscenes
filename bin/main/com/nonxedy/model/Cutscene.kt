package com.nonxedy.model

import kotlin.math.ceil

// Represents a cutscene with a name and a list of frames
data class Cutscene(
    val name: String,
    val frames: List<CutsceneFrame>,
    val frameDurationMs: Long = 50L
) {
    constructor(name: String, frames: List<CutsceneFrame>, ticksPerFrame: Int) : this(
        name,
        frames,
        ticksPerFrame.coerceAtLeast(1) * 50L
    )

    val ticksPerFrame: Int
        get() = ceil(frameDurationMs.coerceAtLeast(1L) / 50.0).toInt()
}
