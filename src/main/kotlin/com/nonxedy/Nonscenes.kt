package com.nonxedy

import org.bukkit.command.PluginCommand
import org.bukkit.plugin.java.JavaPlugin
import com.nonxedy.command.NonsceneCommand
import com.nonxedy.core.ConfigManager
import com.nonxedy.core.CutsceneManager
import com.nonxedy.listener.CommandBlockerListener
import java.util.logging.Logger

class Nonscenes : JavaPlugin() {
    private val logger = Logger.getLogger("nonscenes")
    private var configManager: ConfigManager? = null
    private var cutsceneManager: CutsceneManager? = null

    override fun onEnable() {
        try {
            // Initialize config manager
            configManager = ConfigManager(this)
            configManager?.loadConfigs()

            // Initialize cutscene manager
            cutsceneManager = CutsceneManager(this)

            // Register commands
            val nonsceneCommand = NonsceneCommand(this)
            val command: PluginCommand? = getCommand("nonscene")
            command?.apply {
                setExecutor(nonsceneCommand)
                setTabCompleter(nonsceneCommand)
            }

            // Register command blocker listener
            server.pluginManager.registerEvents(CommandBlockerListener(this), this)

            logger.info("nonscenes enabled with cutscene functionality")
        } catch (e: Exception) {
            logger.severe("Failed to initialize plugin: ${e.message}")
            logger.severe("Plugin will be disabled")
            server.pluginManager.disablePlugin(this)
        }
    }

    override fun onDisable() {
        cutsceneManager?.cleanup()
        logger.info("nonscenes disabled")
    }

    fun getConfigManager(): ConfigManager? = configManager

    fun getCutsceneManager(): CutsceneManager? = cutsceneManager
}
