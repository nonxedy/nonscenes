package com.nonxedy.core;

import com.nonxedy.Nonscenes;
import com.nonxedy.util.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class ConfigManager {
    private final Nonscenes plugin;
    private FileConfiguration config;
    private FileConfiguration messages;
    private File configFile;
    private File messagesFile;

    public ConfigManager(Nonscenes plugin) {
        this.plugin = plugin;
    }

    public void loadConfigs() {
        // Create plugin directory if it doesn't exist
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        // Load config.yml
        configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        
        // Check for missing values in config.yml and add defaults
        boolean configUpdated = false;
        InputStream defaultConfigStream = plugin.getResource("config.yml");
        if (defaultConfigStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultConfigStream, StandardCharsets.UTF_8));
            
            for (String key : defaultConfig.getKeys(true)) {
                if (!config.contains(key)) {
                    config.set(key, defaultConfig.get(key));
                    configUpdated = true;
                }
            }
        }
        
        if (configUpdated) {
            try {
                config.save(configFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not save updated config.yml", e);
            }
        }

        // Load messages.yml
        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);
        
        // Check for missing values in messages.yml and add defaults
        boolean messagesUpdated = false;
        InputStream defaultMessagesStream = plugin.getResource("messages.yml");
        if (defaultMessagesStream != null) {
            YamlConfiguration defaultMessages = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultMessagesStream, StandardCharsets.UTF_8));
            
            for (String key : defaultMessages.getKeys(true)) {
                if (!messages.contains(key)) {
                    messages.set(key, defaultMessages.get(key));
                    messagesUpdated = true;
                }
            }
        }
        
        if (messagesUpdated) {
            try {
                messages.save(messagesFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not save updated messages.yml", e);
            }
        }
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public FileConfiguration getMessages() {
        return messages;
    }

    /**
     * Gets a message from messages.yml and formats it with color codes.
     * Supports both traditional color codes and hex colors.
     *
     * @param path The path to the message in messages.yml
     * @return The formatted message
     */
    public String getMessage(String path) {
        String message = messages.getString(path, "Missing message: " + path);
    
        if (message.contains("${prefix}")) {
            String prefix = messages.getString("prefix", "&8[&bnonscenes&8] &r");
            message = message.replace("${prefix}", prefix);
        }
    
        return ColorUtil.format(message);
    }

    /**
     * Gets a message from messages.yml as a Kyori Component.
     * This is useful for advanced formatting like gradients.
     *
     * @param path The path to the message in messages.yml
     * @return The message as a Component
     */
    public Component getMessageComponent(String path) {
        String message = messages.getString(path, "Missing message: " + path);
        return ColorUtil.toComponent(message);
    }

    /**
     * Gets a list of messages from messages.yml and formats them with color codes.
     *
     * @param path The path to the message list in messages.yml
     * @return The list of formatted messages
     */
    public List<String> getMessageList(String path) {
        List<String> messageList = messages.getStringList(path);
        return messageList.stream()
                .map(ColorUtil::format)
                .collect(Collectors.toList());
    }

    /**
     * Gets a list of messages from messages.yml as Kyori Components.
     *
     * @param path The path to the message list in messages.yml
     * @return The list of messages as Components
     */
    public List<Component> getMessageComponentList(String path) {
        List<String> messageList = messages.getStringList(path);
        return messageList.stream()
                .map(ColorUtil::toComponent)
                .collect(Collectors.toList());
    }

    /**
     * Gets the help messages from messages.yml.
     *
     * @return The list of formatted help messages
     */
    public List<String> getHelpMessages() {
        return getMessageList("help-messages");
    }

    public void reloadConfigs() {
        config = YamlConfiguration.loadConfiguration(configFile);
        messages = YamlConfiguration.loadConfiguration(messagesFile);
        
        // Load defaults if available
        InputStream defaultConfigStream = plugin.getResource("config.yml");
        if (defaultConfigStream != null) {
            config.setDefaults(YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultConfigStream, StandardCharsets.UTF_8)));
        }
        
        InputStream defaultMessagesStream = plugin.getResource("messages.yml");
        if (defaultMessagesStream != null) {
            messages.setDefaults(YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultMessagesStream, StandardCharsets.UTF_8)));
        }
    }

    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save config to " + configFile, e);
        }
    }

    public void saveMessages() {
        try {
            messages.save(messagesFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save messages to " + messagesFile, e);
        }
    }
}
