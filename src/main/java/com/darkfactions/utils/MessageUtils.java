package com.darkfactions.utils;

// ==========================================
// MessageUtils.java
// Helper class for formatting messages
// Makes all the plugin messages look consistent
// ==========================================

import org.bukkit.ChatColor;

public class MessageUtils {

    // Color codes used throughout the plugin
    private static final String PREFIX = ChatColor.DARK_GRAY + "[" + ChatColor.RED + "DarkFactions" + ChatColor.DARK_GRAY + "] " + ChatColor.RESET;
    private static final String HEADER_COLOR = ChatColor.DARK_RED.toString();
    private static final String SUCCESS_COLOR = ChatColor.GREEN.toString();
    private static final String ERROR_COLOR = ChatColor.RED.toString();
    private static final String INFO_COLOR = ChatColor.GRAY.toString();
    private static final String WARNING_COLOR = ChatColor.YELLOW.toString();
    private static final String HELP_COLOR = ChatColor.WHITE.toString();

    // Chat colors
    private static final String FACTION_CHAT_COLOR = ChatColor.LIGHT_PURPLE.toString();
    private static final String ALLY_CHAT_COLOR = ChatColor.DARK_AQUA.toString();

    // ==========================================
    // Message Types
    // ==========================================

    // Standard header for section titles
    public String header(String message) {
        return PREFIX + HEADER_COLOR + "=== " + message + " ===";
    }

    // Success message (green)
    public String success(String message) {
        return PREFIX + SUCCESS_COLOR + message;
    }

    // Error message (red)
    public String error(String message) {
        return PREFIX + ERROR_COLOR + message;
    }

    // Info message (gray)
    public String info(String message) {
        return PREFIX + INFO_COLOR + message;
    }

    // Warning message (yellow)
    public String warning(String message) {
        return PREFIX + WARNING_COLOR + message;
    }

    // Help/command format
    public String help(String command, String description) {
        return INFO_COLOR + command + HELP_COLOR + " - " + description;
    }

    // ==========================================
    // Chat Formatting
    // ==========================================

    // Get just the plugin prefix (no extra formatting)
    public String getChatPrefix() {
        return PREFIX;
    }

    // Format a message for faction-only chat
    public String factionChat(String factionTag, String playerName, String message) {
        return FACTION_CHAT_COLOR + "[F] " + factionTag + ChatColor.WHITE + playerName +
                ChatColor.GRAY + ": " + ChatColor.WHITE + message;
    }

    // Format a message for ally chat
    public String allyChat(String factionTag, String playerName, String message) {
        return ALLY_CHAT_COLOR + "[A] " + factionTag + ChatColor.WHITE + playerName +
                ChatColor.GRAY + ": " + ChatColor.WHITE + message;
    }
}