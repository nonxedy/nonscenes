package com.nonxedy.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.md_5.bungee.api.ChatColor

object ColorUtil {
    // Pattern for hex color codes in format &#RRGGBB or #RRGGBB
    private val HEX_PATTERN = Regex("(?i)(?:&|§)#([0-9A-F]{6})")

    // Legacy serializer that supports ampersand (&) color codes
    private val AMPERSAND_SERIALIZER = LegacyComponentSerializer.builder()
        .character('&')
        .hexColors()
        .useUnusualXRepeatedCharacterHexFormat()
        .build()

    // Formats a string with color codes, including hex colors.
    // Supports both traditional color codes (&a, &b, etc.) and hex colors (&#RRGGBB or #RRGGBB)
    fun format(text: String?): String {
        if (text.isNullOrEmpty()) {
            return ""
        }

        // First replace hex colors with Kyori format
        val result = translateHexColors(text)

        // Then translate traditional color codes using Bungee ChatColor
        return ChatColor.translateAlternateColorCodes('&', result)
    }

    // Converts a string to a Kyori Component with all color formatting applied.
    // This is useful for sending messages with the Adventure API
    fun toComponent(text: String?): Component {
        if (text.isNullOrEmpty()) {
            return Component.empty()
        }

        return AMPERSAND_SERIALIZER.deserialize(text)
    }

    // Translates hex color codes in the format &#RRGGBB or #RRGGBB to the format
    private fun translateHexColors(text: String): String {
        return HEX_PATTERN.replace(text) { matchResult ->
            val hexCode = matchResult.groupValues[1]
            val replacement = StringBuilder("&x")

            // Convert to the format &x&R&R&G&G&B&B
            for (c in hexCode) {
                replacement.append("&").append(c)
            }

            replacement.toString()
        }
    }
}
