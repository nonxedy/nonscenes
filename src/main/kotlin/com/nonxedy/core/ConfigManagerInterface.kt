package com.nonxedy.core

import net.kyori.adventure.text.Component
import org.bukkit.configuration.file.FileConfiguration

// Interface for configuration management functionality
interface ConfigManagerInterface {
    val config: FileConfiguration?

    fun loadConfigs()
    fun reloadConfigs()
    fun saveConfig()
    fun saveMessages()

    fun getMessage(path: String): String
    fun getMessageComponent(path: String): Component
    fun getMessageList(path: String): List<String>
    fun getMessageComponentList(path: String): List<Component>
    fun getHelpMessages(): List<String>
}
