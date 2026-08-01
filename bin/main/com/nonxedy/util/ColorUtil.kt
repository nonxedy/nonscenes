package com.nonxedy.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.md_5.bungee.api.ChatColor

// Utility object for handling color formatting
// Supports both traditional color codes and hex colors
object ColorUtil {
    // Pattern for hex color codes in format &#RRGGBB or #RRGGBB
    private val HEX_PATTERN = Regex("(?i)(?:&|§)#([0-9A-F]{6})")

    // Legacy serializer that supports ampersand (&) color codes
    private val AMPERSAND_SERIALIZER = LegacyComponentSerializer.builder()
        .character('&')
        .hexColors()
        .useUnusualXRepeatedCharacterHexFormat()
        .build()

    // Formats a string with color codes, including hex colors
    // Supports both traditional color codes (&a, &b, etc.) and hex colors (&#RRGGBB or #RRGGBB)
    fun format(text: String?): String {
        return text?.takeIf { it.isNotEmpty() }
            ?.let { translateHexColors(it) }
            ?.let { ChatColor.translateAlternateColorCodes('&', it) }
            ?: ""
    }

    // Converts a string to a Kyori Component with all color formatting applied
    // Useful for messages with the Adventure API
    fun toComponent(text: String?): Component {
        return text?.takeIf { it.isNotEmpty() }
            ?.let { AMPERSAND_SERIALIZER.deserialize(it) }
            ?: Component.empty()
    }

    // Translates hex color codes in the format &#RRGGBB or #RRGGBB to the format &x&R&R&G&G&B&B
    private fun translateHexColors(text: String): String {
        return HEX_PATTERN.replace(text) { matchResult ->
            val hexCode = matchResult.groupValues[1]
            buildString {
                append("&x")
                // Convert to the format &x&R&R&G&G&B&B
                hexCode.forEach { c ->
                    append("&").append(c)
                }
            }
        }
    }

    // Strips all color codes from a string
    fun stripColors(text: String?): String {
        return text?.let { ChatColor.stripColor(format(it)) } ?: ""
    }

    // Checks if a string contains color codes
    fun hasColors(text: String?): Boolean {
        return text?.let { HEX_PATTERN.containsMatchIn(it) || it.contains('&') || it.contains('§') } ?: false
    }
}
