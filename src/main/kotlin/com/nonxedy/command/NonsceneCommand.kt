package com.nonxedy.command

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import com.nonxedy.Nonscenes
import com.nonxedy.core.ConfigManager
import com.nonxedy.core.CutsceneManager

class NonsceneCommand(private val plugin: Nonscenes) : CommandExecutor, TabCompleter {
    private val configManager: ConfigManager? = plugin.getConfigManager()
    private val cutsceneManager: CutsceneManager? = plugin.getCutsceneManager()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (sender !is Player) {
            val message = configManager?.getMessage("player-only-command") ?: "This command can only be used by players."
            sender.sendMessage(message)
            return true
        }

        val player: Player = sender

        // Check if player has permission
        if (!player.hasPermission("nonscene.use")) {
            val message = configManager?.getMessage("no-permission") ?: "You don't have permission to use this command."
            player.sendMessage(message)
            return true
        }

        if (args.isEmpty()) {
            sendHelpMessage(player)
            return true
        }

        val subCommand = args[0].lowercase()

        when (subCommand) {
            "start" -> {
                if (!player.hasPermission("nonscene.start")) {
                    val message = configManager?.getMessage("no-permission") ?: "§cYou don't have permission to use this command."
                    player.sendMessage(message)
                    return true
                }

                if (args.size < 3) {
                    val message = configManager?.getMessage("invalid-start-args") ?: "§cUsage: /nonscene start <name> <frames>"
                    player.sendMessage(message)
                    return true
                }

                val name = args[1]
                val frames: Int

                try {
                    frames = args[2].toInt()
                    if (frames <= 0) {
                        val message = configManager?.getMessage("invalid-frames-number") ?: "§cFrames number must be positive."
                        player.sendMessage(message)
                        return true
                    }
                } catch (e: NumberFormatException) {
                    val message = configManager?.getMessage("invalid-frames-number") ?: "§cInvalid frames number."
                    player.sendMessage(message)
                    return true
                }

                cutsceneManager?.startRecording(player, name, frames)
            }

            "delete" -> {
                if (!player.hasPermission("nonscene.delete")) {
                    val message = configManager?.getMessage("no-permission") ?: "§cYou don't have permission to use this command."
                    player.sendMessage(message)
                    return true
                }

                if (args.size < 2) {
                    val message = configManager?.getMessage("specify-cutscene-name") ?: "§cPlease specify a cutscene name."
                    player.sendMessage(message)
                    return true
                }

                cutsceneManager?.deleteCutscene(player, args[1])
            }

            "all" -> {
                if (!player.hasPermission("nonscene.list")) {
                    val message = configManager?.getMessage("no-permission") ?: "§cYou don't have permission to use this command."
                    player.sendMessage(message)
                    return true
                }

                cutsceneManager?.listAllCutscenes(player)
            }

            "play" -> {
                if (!player.hasPermission("nonscene.play")) {
                    val message = configManager?.getMessage("no-permission") ?: "§cYou don't have permission to use this command."
                    player.sendMessage(message)
                    return true
                }

                if (args.size < 2) {
                    val message = configManager?.getMessage("specify-cutscene-name") ?: "§cPlease specify a cutscene name."
                    player.sendMessage(message)
                    return true
                }

                cutsceneManager?.playCutscene(player, args[1])
            }

            "showpath" -> {
                if (!player.hasPermission("nonscene.showpath")) {
                    val message = configManager?.getMessage("no-permission") ?: "§cYou don't have permission to use this command."
                    player.sendMessage(message)
                    return true
                }

                if (args.size < 2) {
                    val message = configManager?.getMessage("specify-cutscene-name") ?: "§cPlease specify a cutscene name."
                    player.sendMessage(message)
                    return true
                }

                cutsceneManager?.showCutscenePath(player, args[1])
            }

            "stop" -> {
                if (!player.hasPermission("nonscene.stop")) {
                    val message = configManager?.getMessage("no-permission") ?: "§cYou don't have permission to use this command."
                    player.sendMessage(message)
                    return true
                }

                cutsceneManager?.cancelAllSessions(player)
            }

            else -> sendHelpMessage(player)
        }

        return true
    }

    private fun sendHelpMessage(player: Player) {
        val helpMessages = configManager?.getMessageList("help-messages") ?: listOf(
            "§6=== Nonscenes Plugin ===",
            "§7Available commands:",
            "§7- §e/nonscene start <name> <frames> §7- Start recording",
            "§7- §e/nonscene play <name> §7- Play cutscene",
            "§7- §e/nonscene all §7- List cutscenes",
            "§7- §e/nonscene delete <name> §7- Delete cutscene",
            "§7- §e/nonscene showpath <name> §7- Show path",
            "§7- §e/nonscene stop §7- Cancel current action"
        )

        helpMessages.forEach { message ->
            player.sendMessage(message)
        }
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): MutableList<String> {
        val completions = mutableListOf<String>()

        if (sender !is Player) {
            return completions
        }

        val player: Player = sender

        if (args.size == 1) {
            val subCommands = mutableListOf<String>()

            if (player.hasPermission("nonscene.start")) subCommands.add("start")
            if (player.hasPermission("nonscene.delete")) subCommands.add("delete")
            if (player.hasPermission("nonscene.list")) subCommands.add("all")
            if (player.hasPermission("nonscene.play")) subCommands.add("play")
            if (player.hasPermission("nonscene.showpath")) subCommands.add("showpath")
            if (player.hasPermission("nonscene.stop")) subCommands.add("stop")

            return filterCompletions(subCommands, args[0])
        } else if (args.size == 2) {
            val subCommand = args[0].lowercase()

            if ((subCommand == "delete" || subCommand == "play" || subCommand == "showpath")
                && player.hasPermission("nonscene.$subCommand")) {
                return filterCompletions(cutsceneManager?.getCutsceneNames() ?: emptyList(), args[1])
            }
        }

        return completions
    }

    private fun filterCompletions(options: List<String>, input: String): MutableList<String> {
        return options.filter { option ->
            option.lowercase().startsWith(input.lowercase())
        }.toMutableList()
    }
}
