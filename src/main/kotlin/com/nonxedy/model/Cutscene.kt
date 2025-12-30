package com.nonxedy.model

// Represents a cutscene with a name and a list of frames
data class Cutscene(
    private val name: String,
    private val frames: List<CutsceneFrame>
) {
    fun getName(): String = name
    fun getFrames(): List<CutsceneFrame> = frames
}
