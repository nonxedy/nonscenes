package com.nonxedy.core

import com.nonxedy.model.Cutscene
import org.bukkit.entity.Player

// Interface for cutscene management functionality
interface CutsceneManagerInterface {
    fun startRecording(player: Player, name: String, frames: Int)
    fun playCutscene(player: Player, name: String)
    fun deleteCutscene(player: Player, name: String)
    fun listAllCutscenes(player: Player)
    fun showCutscenePath(player: Player, name: String)

    fun cancelRecording(player: Player)
    fun cancelPlayback(player: Player)
    fun cancelPathVisualization(player: Player)
    fun cancelAllSessions(player: Player)

    fun isRecording(player: Player): Boolean
    fun isWatchingCutscene(player: Player): Boolean

    fun getCutsceneNames(): List<String>
    fun getCutscene(name: String): Cutscene?

    fun cleanup()
}
