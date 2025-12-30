package com.nonxedy.listener

import com.nonxedy.Nonscenes
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCommandPreprocessEvent

class CommandBlockerListener(private val plugin: Nonscenes) : Listener {

    @EventHandler
    fun onPlayerCommand(event: PlayerCommandPreprocessEvent) {
        val player = event.player
        val cutsceneManager = plugin.getCutsceneManager()

        // Check if player is currently watching a cutscene
        if (cutsceneManager?.isWatchingCutscene(player) == true) {
            // Allow only the stop command during cutscene playback
            val command = event.message.lowercase().trim()
            if (!command.startsWith("/nonscene stop") && !command.startsWith("/ns stop")) {
                event.isCancelled = true
                val configManager = plugin.getConfigManager()
                val message = configManager?.getMessage("command-disabled-during-cutscene")
                    ?: "§cYou cannot use commands while watching a cutscene! Use /nonscene stop to exit."
                player.sendMessage(message)
            }
        }
    }
}
