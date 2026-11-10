package com.omnicord.plugin.utils;

import net.md_5.bungee.api.ChatColor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for parsing and transforming Minecraft color codes (legacy '&', modern Hex '#RRGGBB',
 * MiniMessage tags) and Discord Markdown into Minecraft Chat components.
 */
public final class ColorUtil {

    private static final Pattern HEX_PATTERN_1 = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern HEX_PATTERN_2 = Pattern.compile("#([A-Fa-f0-9]{6})");
    private static final Pattern HEX_PATTERN_3 = Pattern.compile("<#([A-Fa-f0-9]{6})>");
    private static final Pattern STRIP_COLOR_PATTERN = Pattern.compile("(?i)&[0-9A-FK-ORX]|§[0-9A-FK-ORX]|&#[A-Fa-f0-9]{6}|#[A-Fa-f0-9]{6}|<#[A-Fa-f0-9]{6}>|<[^>]*>");

    private ColorUtil() {}

    /**
     * Colorize a string supporting legacy & codes and 24-bit RGB Hex codes.
     */
    public static String colorize(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        // Translate &#RRGGBB
        Matcher matcher1 = HEX_PATTERN_1.matcher(message);
        StringBuilder sb = new StringBuilder();
        while (matcher1.find()) {
            String hex = matcher1.group(1);
            matcher1.appendReplacement(sb, Matcher.quoteReplacement(getHexChatColor(hex)));
        }
        matcher1.appendTail(sb);
        message = sb.toString();

        // Translate <#RRGGBB>
        Matcher matcher3 = HEX_PATTERN_3.matcher(message);
        sb = new StringBuilder();
        while (matcher3.find()) {
            String hex = matcher3.group(1);
            matcher3.appendReplacement(sb, Matcher.quoteReplacement(getHexChatColor(hex)));
        }
        matcher3.appendTail(sb);
        message = sb.toString();

        // Translate standalone #RRGGBB if preceded by whitespace or start of line
        Matcher matcher2 = HEX_PATTERN_2.matcher(message);
        sb = new StringBuilder();
        while (matcher2.find()) {
            int start = matcher2.start();
            if (start == 0 || Character.isWhitespace(message.charAt(start - 1)) || message.charAt(start - 1) == '&') {
                String hex = matcher2.group(1);
                matcher2.appendReplacement(sb, Matcher.quoteReplacement(getHexChatColor(hex)));
            } else {
                matcher2.appendReplacement(sb, Matcher.quoteReplacement(matcher2.group(0)));
            }
        }
        matcher2.appendTail(sb);
        message = sb.toString();

        // Translate standard legacy & codes
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    private static String getHexChatColor(String hex) {
        try {
            return ChatColor.of("#" + hex).toString();
        } catch (Throwable t) {
            // Fallback for older Bungee ChatColor implementations
            StringBuilder color = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                color.append('§').append(c);
            }
            return color.toString();
        }
    }

    /**
     * Strip all color codes (legacy and hex) from a string.
     */
    public static String stripColor(String input) {
        if (input == null) return "";
        return STRIP_COLOR_PATTERN.matcher(input).replaceAll("");
    }

    /**
     * Convert Discord Markdown format into Minecraft chat color format.
     */
    public static String discordToMinecraft(String text) {
        if (text == null || text.isEmpty()) return "";

        // Bold: **text** -> &ltext&r
        text = text.replaceAll("\\*\\*(.*?)\\*\\*", "&l$1&r");
        // Underline: __text__ -> &ntext&r
        text = text.replaceAll("__(.*?)__", "&n$1&r");
        // Italic: *text* or _text_ -> &otext&r
        text = text.replaceAll("\\*(.*?)\\*", "&o$1&r");
        text = text.replaceAll("_(.*?)_", "&o$1&r");
        // Strikethrough: ~~text~~ -> &mtext&r
        text = text.replaceAll("~~(.*?)~~", "&m$1&r");
        // Code: `text` -> &7text&r
        text = text.replaceAll("`(.*?)`", "&7$1&r");

        return colorize(text);
    }

    /**
     * Convert Minecraft colors into Discord Markdown clean text.
     */
    public static String minecraftToDiscord(String text) {
        if (text == null) return "";
        // Strip colors before sending to discord
        String clean = stripColor(text);
        // Escape Discord markdown special characters to prevent abuse
        clean = clean.replace("@everyone", "@\u200Beveryone")
                     .replace("@here", "@\u200Bhere");
        return clean;
    }
}
