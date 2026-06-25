package com.darkfactions.utils;

// ==========================================
// MessageUtils.java
// Helper class for formatting messages
// Makes all the plugin messages look consistent
// Every helper returns an Adventure Component
// ==========================================

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class MessageUtils {

    // The plugin prefix, built once: "[" dark_gray, "DarkFactions" red, "] " dark_gray
    private static final Component PREFIX = Component.text("[", NamedTextColor.DARK_GRAY)
            .append(Component.text("DarkFactions", NamedTextColor.RED))
            .append(Component.text("] ", NamedTextColor.DARK_GRAY));

    // ==========================================
    // Message Types
    // ==========================================

    // Standard header for section titles
    public Component header(String message) {
        return PREFIX.append(Component.text("=== " + message + " ===", NamedTextColor.DARK_RED));
    }

    // Success message (green)
    public Component success(String message) {
        return PREFIX.append(Component.text(message, NamedTextColor.GREEN));
    }

    // Error message (red)
    public Component error(String message) {
        return PREFIX.append(Component.text(message, NamedTextColor.RED));
    }

    // Info message (gray)
    public Component info(String message) {
        return PREFIX.append(Component.text(message, NamedTextColor.GRAY));
    }

    // Warning message (yellow)
    public Component warning(String message) {
        return PREFIX.append(Component.text(message, NamedTextColor.YELLOW));
    }

    // Help/command format (no prefix)
    public Component help(String command, String description) {
        return Component.text(command, NamedTextColor.GRAY)
                .append(Component.text(" - ", NamedTextColor.WHITE))
                .append(Component.text(description, NamedTextColor.WHITE));
    }

    // ==========================================
    // Chat Formatting
    // ==========================================

    // Get just the plugin prefix (no extra formatting)
    public Component getChatPrefix() {
        return PREFIX;
    }

    // Format a message for faction-only chat
    public Component factionChat(String factionTag, String playerName, String message) {
        return Component.text("[F] ", NamedTextColor.LIGHT_PURPLE)
                .append(LegacyComponentSerializer.legacySection().deserialize(factionTag))
                .append(Component.text(playerName, NamedTextColor.WHITE))
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(Component.text(message, NamedTextColor.WHITE));
    }

    // Format a message for ally chat
    public Component allyChat(String factionTag, String playerName, String message) {
        return Component.text("[A] ", NamedTextColor.DARK_AQUA)
                .append(LegacyComponentSerializer.legacySection().deserialize(factionTag))
                .append(Component.text(playerName, NamedTextColor.WHITE))
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(Component.text(message, NamedTextColor.WHITE));
    }
}
