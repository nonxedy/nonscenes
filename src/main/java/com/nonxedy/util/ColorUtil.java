package com.nonxedy.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtil {
    // Pattern for hex color codes in format &#RRGGBB or #RRGGBB
    private static final Pattern HEX_PATTERN = Pattern.compile("(?i)(?:&|§)#([0-9A-F]{6})");
    
    // Legacy serializer that supports ampersand (&) color codes
    private static final LegacyComponentSerializer AMPERSAND_SERIALIZER = 
            LegacyComponentSerializer.builder()
                    .character('&')
                    .hexColors()
                    .useUnusualXRepeatedCharacterHexFormat()
                    .build();
    
    /**
     * Formats a string with color codes, including hex colors.
     * Supports both traditional color codes (&a, &b, etc.) and hex colors (&#RRGGBB or #RRGGBB).
     *
     * @param text The text to format
     * @return The formatted text with colors applied
     */
    public static String format(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        
        // First replace hex colors with Kyori format
        String result = translateHexColors(text);
        
        // Then translate traditional color codes using Bungee ChatColor
        return ChatColor.translateAlternateColorCodes('&', result);
    }
    
    /**
     * Converts a string to a Kyori Component with all color formatting applied.
     * This is useful for sending messages with the Adventure API.
     *
     * @param text The text to convert
     * @return A Component with all formatting applied
     */
    public static Component toComponent(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        
        return AMPERSAND_SERIALIZER.deserialize(text);
    }
    
    /**
     * Translates hex color codes in the format &#RRGGBB or #RRGGBB to the format
     * that can be understood by ChatColor.translateAlternateColorCodes.
     *
     * @param text The text containing hex color codes
     * @return The text with hex colors translated to a format that can be processed by ChatColor
     */
    private static String translateHexColors(String text) {
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        
        while (matcher.find()) {
            String hexCode = matcher.group(1);
            String replacement = "&x";
            
            // Convert to the format &x&R&R&G&G&B&B
            for (char c : hexCode.toCharArray()) {
                replacement += "&" + c;
            }
            
            matcher.appendReplacement(buffer, replacement);
        }
        
        matcher.appendTail(buffer);
        return buffer.toString();
    }
    
    /**
     * Creates a gradient between two colors across a text string.
     * 
     * @param text The text to apply the gradient to
     * @param fromHex The starting hex color (format: #RRGGBB)
     * @param toHex The ending hex color (format: #RRGGBB)
     * @return A Component with the gradient applied
     */
    public static Component gradient(String text, String fromHex, String toHex) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        
        // Parse hex colors
        TextColor from = TextColor.fromHexString(fromHex);
        TextColor to = TextColor.fromHexString(toHex);
        
        if (from == null || to == null) {
            return Component.text(text);
        }
        
        // Extract RGB components
        int fromR = from.red();
        int fromG = from.green();
        int fromB = from.blue();
        
        int toR = to.red();
        int toG = to.green();
        int toB = to.blue();
        
        TextComponent.Builder builder = Component.text();
        int length = text.length();
        
        for (int i = 0; i < length; i++) {
            float ratio = (float) i / (length - 1);
            
            // Calculate the color at this position in the gradient
            int r = (int) (fromR + (toR - fromR) * ratio);
            int g = (int) (fromG + (toG - fromG) * ratio);
            int b = (int) (fromB + (toB - fromB) * ratio);
            
            TextColor color = TextColor.color(r, g, b);
            builder.append(Component.text(text.charAt(i)).color(color));
        }
        
        return builder.build();
    }
    
    /**
     * Rainbow effect for text.
     * 
     * @param text The text to apply the rainbow effect to
     * @param saturation The color saturation (0.0f - 1.0f)
     * @param brightness The color brightness (0.0f - 1.0f)
     * @return A Component with rainbow colors applied
     */
    public static Component rainbow(String text, float saturation, float brightness) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        
        TextComponent.Builder builder = Component.text();
        int length = text.length();
        
        for (int i = 0; i < length; i++) {
            float hue = (float) i / length;
            TextColor color = hsvToRgb(hue, saturation, brightness);
            builder.append(Component.text(text.charAt(i)).color(color));
        }
        
        return builder.build();
    }
    
    /**
     * Converts HSV color values to RGB TextColor.
     */
    private static TextColor hsvToRgb(float hue, float saturation, float value) {
        int h = (int) (hue * 6);
        float f = hue * 6 - h;
        float p = value * (1 - saturation);
        float q = value * (1 - f * saturation);
        float t = value * (1 - (1 - f) * saturation);
        
        float r, g, b;
        switch (h % 6) {
            case 0: r = value; g = t; b = p; break;
            case 1: r = q; g = value; b = p; break;
            case 2: r = p; g = value; b = t; break;
            case 3: r = p; g = q; b = value; break;
            case 4: r = t; g = p; b = value; break;
            case 5: r = value; g = p; b = q; break;
            default: r = 0; g = 0; b = 0;
        }
        
        return TextColor.color(
                (int) (r * 255),
                (int) (g * 255),
                (int) (b * 255)
        );
    }
}
