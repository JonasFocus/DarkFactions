package com.darkfactions.listeners;

// ==========================================
// FactionChatService.java
// Routes faction and ally chat: intercepts messages from players with an
// active chat mode and re-broadcasts them, formatted, to the right audience.
// ==========================================

import com.darkfactions.DarkFactions;
import com.darkfactions.commands.FactionCommand;
import com.darkfactions.models.Faction;
import com.darkfactions.utils.ChatFormatter;

import org.bukkit.entity.Player;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.UUID;

public class FactionChatService {

    private final DarkFactions plugin;

    public FactionChatService(DarkFactions plugin) {
        this.plugin = plugin;
    }

    public void handle(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        Faction faction = plugin.getFactionManager().getPlayerFaction(playerUuid);
        if (faction == null) {
            return; // Not in a faction, normal chat
        }

        // Check the command handler for chat mode
        FactionCommand cmd = plugin.getFactionCommand();
        String chatMode = cmd != null ? cmd.getChatMode(playerUuid) : null;

        if (chatMode == null) {
            return; // Normal chat
        }

        // Cancel the normal chat broadcast
        event.setCancelled(true);

        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        String prefix = LegacyComponentSerializer.legacySection()
                .serialize(plugin.getMessageUtils().getChatPrefix()); // Get formatted prefix

        boolean allyMode = "ally".equals(chatMode);
        String template = allyMode
                ? plugin.getConfigManager().getAllyChatFormat()
                : plugin.getConfigManager().getFactionChatFormat();

        Component rendered = LegacyComponentSerializer.legacySection().deserialize(
                ChatFormatter.format(template, prefix, faction.getFormattedTag(),
                        player.getName(), faction.getName(), message));

        // Always reaches the speaker's own faction; ally mode also fans out to allies.
        broadcastToMembers(faction, rendered);
        if (allyMode) {
            for (UUID allyId : faction.getAllies()) {
                Faction allyFaction = plugin.getFactionManager().getFaction(allyId);
                if (allyFaction != null) {
                    broadcastToMembers(allyFaction, rendered);
                }
            }
        }

        if (plugin.getConfigManager().isLogChatToConsole()) {
            String label = allyMode ? "ALLY CHAT" : "FACTION CHAT";
            plugin.getLogger().info("[" + label + "] " + faction.getName() + " - " + player.getName() + ": " + message);
        }
    }

    // Send a rendered component to every online member of a faction.
    private void broadcastToMembers(Faction faction, Component message) {
        for (UUID memberUuid : faction.getMembers()) {
            Player member = plugin.getServer().getPlayer(memberUuid);
            if (member != null && member.isOnline()) {
                member.sendMessage(message);
            }
        }
    }
}
