package com.nonxedy.database.service

import com.nonxedy.model.Cutscene

// Interface for cutscene database operations
interface CutsceneDatabaseService {

    // Initialize the database connection
    fun initialize()

    // Shutdown the database connection
    fun shutdown()

    // Save a cutscene to the database
    fun saveCutscene(cutscene: Cutscene)

    // Load all cutscenes from the database
    fun loadAllCutscenes(): List<Cutscene>

    // Delete a cutscene from the database
    fun deleteCutscene(name: String)

    // Check if a cutscene exists in the database
    fun cutsceneExists(name: String): Boolean
}
