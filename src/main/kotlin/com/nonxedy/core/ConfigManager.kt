package com.nonxedy.core

import com.nonxedy.Nonscenes
import com.nonxedy.util.ColorUtil
import net.kyori.adventure.text.Component
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.logging.Level

class ConfigManager(private val plugin: Nonscenes) : ConfigManagerInterface {
    override var config: FileConfiguration? = null
        private set
    var messages: FileConfiguration? = null
        private set
    private var configFile: File? = null
    private var messagesFile: File? = null

    override fun loadConfigs() {
        // Create plugin directory if it doesn't exist
        if (!plugin.dataFolder.exists()) {
            plugin.dataFolder.mkdirs()
        }

        // Load config.yml
        val configFileLocal = File(plugin.dataFolder, "config.yml")
        configFile = configFileLocal
        if (!configFileLocal.exists()) {
            plugin.saveResource("config.yml", false)
        }
        config = YamlConfiguration.loadConfiguration(configFileLocal)

        // Check for missing values in config.yml and add defaults
        var configUpdated = false
        val defaultConfigStream = plugin.getResource("config.yml")
        if (defaultConfigStream != null) {
            val defaultConfig = YamlConfiguration.loadConfiguration(
                InputStreamReader(defaultConfigStream, StandardCharsets.UTF_8)
            )

            for (key in defaultConfig.getKeys(true)) {
                if (!config!!.contains(key)) {
                    config!!.set(key, defaultConfig.get(key))
                    configUpdated = true
                }
            }
        }

        if (configUpdated) {
            try {
                config?.save(configFile!!)
            } catch (e: IOException) {
                plugin.logger.log(Level.WARNING, "Could not save updated config.yml", e)
            }
        }

        // Load messages.yml
        val messagesFileLocal = File(plugin.dataFolder, "messages.yml")
        messagesFile = messagesFileLocal
        if (!messagesFileLocal.exists()) {
            plugin.saveResource("messages.yml", false)
        }
        messages = YamlConfiguration.loadConfiguration(messagesFileLocal)

        // Check for missing values in messages.yml and add defaults
        var messagesUpdated = false
        val defaultMessagesStream = plugin.getResource("messages.yml")
        if (defaultMessagesStream != null) {
            val defaultMessages = YamlConfiguration.loadConfiguration(
                InputStreamReader(defaultMessagesStream, StandardCharsets.UTF_8)
            )

            for (key in defaultMessages.getKeys(true)) {
                if (!messages!!.contains(key)) {
                    messages!!.set(key, defaultMessages.get(key))
                    messagesUpdated = true
                }
            }
        }

        if (messagesUpdated) {
            try {
                messages?.save(messagesFile!!)
            } catch (e: IOException) {
                plugin.logger.log(Level.WARNING, "Could not save updated messages.yml", e)
            }
        }
    }

    // Gets a message from messages.yml and formats it with color codes.
    override fun getMessage(path: String): String {
        var message = messages?.getString(path, "Missing message: $path") ?: "Missing message: $path"

        if (message.contains("\${prefix}")) {
            val prefix = messages?.getString("prefix", "&8[&bnonscenes&8] &r") ?: "&8[&bnonscenes&8] &r"
            message = message.replace("\${prefix}", prefix)
        }

        return ColorUtil.format(message)
    }

    // Gets a message from messages.yml as a Component for modern messaging
    override fun getMessageComponent(path: String): Component {
        var message = messages?.getString(path, "Missing message: $path") ?: "Missing message: $path"

        if (message.contains("\${prefix}")) {
            val prefix = messages?.getString("prefix", "&8[&bnonscenes&8] &r") ?: "&8[&bnonscenes&8] &r"
            message = message.replace("\${prefix}", prefix)
        }

        return ColorUtil.toComponent(message)
    }

    // Gets a list of messages from messages.yml and formats them with color codes.
    override fun getMessageList(path: String): List<String> {
        val messageList = messages?.getStringList(path) ?: emptyList()
        return messageList.map { ColorUtil.format(it) }
    }

    // Gets a list of messages from messages.yml as Kyori Components.
    override fun getMessageComponentList(path: String): List<Component> {
        val messageList = messages?.getStringList(path) ?: emptyList()
        return messageList.map { ColorUtil.toComponent(it) }
    }

    // Gets the help messages from messages.yml.
    override fun getHelpMessages(): List<String> = getMessageList("help-messages")

    override fun reloadConfigs() {
        config = configFile?.let { YamlConfiguration.loadConfiguration(it) }
        messages = messagesFile?.let { YamlConfiguration.loadConfiguration(it) }

        // Load defaults if available
        val defaultConfigStream = plugin.getResource("config.yml")
        if (defaultConfigStream != null) {
            val defaultConfig = YamlConfiguration.loadConfiguration(
                InputStreamReader(defaultConfigStream, StandardCharsets.UTF_8)
            )
            config?.setDefaults(defaultConfig)
        }

        val defaultMessagesStream = plugin.getResource("messages.yml")
        if (defaultMessagesStream != null) {
            val defaultMessages = YamlConfiguration.loadConfiguration(
                InputStreamReader(defaultMessagesStream, StandardCharsets.UTF_8)
            )
            messages?.setDefaults(defaultMessages)
        }
    }

    override fun saveConfig() {
        try {
            config?.save(configFile!!)
        } catch (e: IOException) {
            plugin.logger.log(Level.SEVERE, "Could not save config to $configFile", e)
        }
    }

    override fun saveMessages() {
        try {
            val currentMessagesFile = messagesFile
            val currentMessages = messages
            if (currentMessagesFile != null && currentMessages != null) {
                currentMessages.save(currentMessagesFile)
            }
        } catch (e: IOException) {
            plugin.logger.log(Level.SEVERE, "Could not save messages to $messagesFile", e)
        }
    }
}
