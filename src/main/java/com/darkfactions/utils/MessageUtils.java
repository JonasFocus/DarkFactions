package com.darkfactions.utils;

// Central place for building the plugin's chat messages so styling stays
// consistent. Every helper returns an Adventure Component.

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class MessageUtils {

    // The plugin prefix, built once: "[" dark_gray, "DarkFactions" red, "] " dark_gray
    private static final Component PREFIX = Component.text("[", NamedTextColor.DARK_GRAY)
            .append(Component.text("DarkFactions", NamedTextColor.RED))
            .append(Component.text("] ", NamedTextColor.DARK_GRAY));

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

    // Get just the plugin prefix (no extra formatting)
    public Component getChatPrefix() {
        return PREFIX;
    }

    // Format a message for faction-only chat
    public Component factionChat(String factionTag, String playerName, String message) {
        return scopedChat("[F] ", NamedTextColor.LIGHT_PURPLE, factionTag, playerName, message);
    }

    // Format a message for ally chat
    public Component allyChat(String factionTag, String playerName, String message) {
        return scopedChat("[A] ", NamedTextColor.DARK_AQUA, factionTag, playerName, message);
    }

    // Faction and ally chat share the same layout; only the leading scope label
    // and its colour differ. The tag is deserialized as legacy text because it
    // carries embedded colour codes from the faction's configured tag.
    private Component scopedChat(String label, NamedTextColor labelColor,
                                 String factionTag, String playerName, String message) {
        return Component.text(label, labelColor)
                .append(LegacyComponentSerializer.legacySection().deserialize(factionTag))
                .append(Component.text(playerName, NamedTextColor.WHITE))
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(Component.text(message, NamedTextColor.WHITE));
    }
}
