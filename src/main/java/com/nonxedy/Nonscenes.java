package com.nonxedy;

import com.nonxedy.command.NonsceneCommand;
import com.nonxedy.core.ConfigManager;
import com.nonxedy.core.CutsceneManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

/**
 * Main plugin class for Nonscenes
 */
public class Nonscenes extends JavaPlugin {
    private static final Logger LOGGER = Logger.getLogger("nonscenes");
    private ConfigManager configManager;
    private CutsceneManager cutsceneManager;

    @Override
    public void onEnable() {
        // Initialize config manager
        configManager = new ConfigManager(this);
        configManager.loadConfigs();
        
        // Initialize cutscene manager
        cutsceneManager = new CutsceneManager(this);
        
        // Register commands
        NonsceneCommand nonsceneCommand = new NonsceneCommand(this);
        PluginCommand command = getCommand("nonscene");
        if (command != null) {
            command.setExecutor(nonsceneCommand);
            command.setTabCompleter(nonsceneCommand);
        }
        
        LOGGER.info("nonscenes enabled");
    }

    @Override
    public void onDisable() {
        // Clean up resources
        if (cutsceneManager != null) {
            cutsceneManager.cleanup();
        }
        
        LOGGER.info("nonscenes disabled");
    }
    
    /**
     * Gets the config manager
     * @return The config manager
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    /**
     * Gets the cutscene manager
     * @return The cutscene manager
     */
    public CutsceneManager getCutsceneManager() {
        return cutsceneManager;
    }
}
