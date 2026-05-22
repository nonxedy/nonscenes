package com.nonxedy.listener

import com.nonxedy.Nonscenes
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class PlayerStateRestorer(private val plugin: Nonscenes) : Listener {

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player

        // Cancel any active session so recording/showpath tasks do not leak after disconnect.
        if (plugin.cutsceneManager.hasActiveSession(player)) {
            plugin.cutsceneManager.cancelAllSessions(player)
        }
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        // Check if player has a saved location from an interrupted cutscene
        // This shouldn't normally happen since cancelPlayback is called on quit,
        // but this is a safety net in case of server crashes or other issues
        // For now, don't need to do anything special on join since cleanup() clears everything on shutdown
    }
}
