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

        // If player is watching a cutscene, cancel it to restore state
        if (plugin.cutsceneManager.isWatchingCutscene(player)) {
            plugin.cutsceneManager.cancelPlayback(player)
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
