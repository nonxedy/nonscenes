package com.nonxedy

import org.bukkit.command.PluginCommand
import org.bukkit.plugin.java.JavaPlugin
import com.nonxedy.command.NonsceneCommand
import com.nonxedy.core.ConfigManager
import com.nonxedy.core.ConfigManagerInterface
import com.nonxedy.core.CutsceneManager
import com.nonxedy.core.CutsceneManagerInterface
import com.nonxedy.listener.CommandBlockerListener
import java.util.logging.Logger

class Nonscenes : JavaPlugin() {
    private val logger = Logger.getLogger("nonscenes")

    // Dependency injection with lateinit var
    lateinit var configManager: ConfigManagerInterface
        private set
    lateinit var cutsceneManager: CutsceneManagerInterface
        private set

    override fun onEnable() {
        try {
            // Initialize dependencies using dependency injection
            initializeDependencies()

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

    // Initialize all dependencies using dependency injection pattern
    private fun initializeDependencies() {
        // Initialize config manager
        val configManagerImpl = ConfigManager(this)
        configManagerImpl.loadConfigs()
        configManager = configManagerImpl

        // Initialize cutscene manager with dependency injection
        val cutsceneManagerImpl = CutsceneManager(this)
        cutsceneManager = cutsceneManagerImpl
    }

    override fun onDisable() {
        if (::cutsceneManager.isInitialized) {
            cutsceneManager.cleanup()
        }
        logger.info("nonscenes disabled")
    }
}
