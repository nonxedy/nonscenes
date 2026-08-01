package com.nonxedy.listener

import com.nonxedy.Nonscenes
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCommandPreprocessEvent

class CommandBlockerListener(private val plugin: Nonscenes) : Listener {

    @EventHandler
    fun onPlayerCommand(event: PlayerCommandPreprocessEvent) {
        val player = event.player

        // Check if player is currently watching a cutscene
        if (plugin.cutsceneManager.isWatchingCutscene(player)) {
            // Allow only the stop command during cutscene playback
            val command = event.message.lowercase().trim()
            if (!command.startsWith("/nonscene stop") && !command.startsWith("/ns stop")) {
                event.isCancelled = true
                val message = plugin.configManager.getMessage("command-disabled-during-cutscene")
                player.sendMessage(message)
            }
        }
    }
}
