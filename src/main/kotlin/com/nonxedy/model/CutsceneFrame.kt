package com.nonxedy.model

import org.bukkit.Location

// Represents a single frame in a cutscene
data class CutsceneFrame(private val location: Location) {
    fun getLocation(): Location = location
}
