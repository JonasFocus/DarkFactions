package com.darkfactions.commands;

// Base class for /f subcommand handlers. Holds the plugin/message references and
// the shared helper methods that many handlers rely on.

import com.darkfactions.DarkFactions;
import com.darkfactions.models.Faction;
import com.darkfactions.utils.MessageUtils;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public abstract class AbstractFactionSubcommand {

    // Reference to main plugin
    protected final DarkFactions plugin;

    // Utility for sending fancy messages
    protected final MessageUtils msg;

    protected AbstractFactionSubcommand(DarkFactions plugin) {
        this.plugin = plugin;
        this.msg = plugin.getMessageUtils();
    }

    // ==========================================
    // Helper Methods
    // ==========================================

    // Return the player's faction, or null after telling them they're not in one.
    // Callers do: Faction f = requireFaction(player); if (f == null) return true;
    protected double effectivePower(Faction faction) {
        return plugin.getPowerManager().getEffectiveFactionPower(faction.getFactionId());
    }

    protected double effectiveMaxPower(Faction faction) {
        return plugin.getPowerManager().getFactionMaxPower(faction.getFactionId());
    }

    protected Faction requireFaction(Player player) {
        Faction faction = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage(msg.error("You're not in a faction!"));
        }
        return faction;
    }

    // Ensure at least `min` arguments were given, else show the usage string.
    // Returns true when the args are sufficient.
    protected boolean requireArgs(Player player, String[] args, int min, String usage) {
        if (args.length < min) {
            player.sendMessage(msg.error("Usage: " + usage));
            return false;
        }
        return true;
    }

    // Require the player to be the faction leader, else send `denial`.
    // Callers do: if (!requireLeader(player, faction, "...")) return true;
    protected boolean requireLeader(Player player, Faction faction, String denial) {
        if (!faction.isLeader(player.getUniqueId())) {
            player.sendMessage(msg.error(denial));
            return false;
        }
        return true;
    }

    // Require the player to be the leader or an officer, else send `denial`.
    // Callers do: if (!requireOfficer(player, faction, "...")) return true;
    protected boolean requireOfficer(Player player, Faction faction, String denial) {
        if (!faction.isLeaderOrOfficer(player.getUniqueId())) {
            player.sendMessage(msg.error(denial));
            return false;
        }
        return true;
    }

    // Require leader/officer, or any member when `membersAllowed` is true.
    protected boolean requireOfficerOrMemberPermitted(Player player, Faction faction, boolean membersAllowed, String denial) {
        if (faction.isLeaderOrOfficer(player.getUniqueId())) {
            return true;
        }
        if (membersAllowed && faction.isMember(player.getUniqueId())) {
            return true;
        }
        player.sendMessage(msg.error(denial));
        return false;
    }

    // Find a player's UUID by name within a faction
    protected UUID findPlayerUuidByName(String name, Faction faction) {
        // Check online players first
        Player onlineTarget = Bukkit.getPlayerExact(name);
        if (onlineTarget != null && faction.isMember(onlineTarget.getUniqueId())) {
            return onlineTarget.getUniqueId();
        }

        // Search through all members by name using our cache
        for (UUID memberUuid : faction.getMembers()) {
            String memberName = plugin.getPlayerNameCache().getPlayerName(memberUuid);
            if (memberName != null && memberName.equalsIgnoreCase(name)) {
                return memberUuid;
            }
        }

        // Try reverse lookup from name cache
        UUID cachedUuid = plugin.getPlayerNameCache().getUuidFromName(name);
        if (cachedUuid != null && faction.isMember(cachedUuid)) {
            return cachedUuid;
        }

        return null;
    }

    // Broadcast a message to all online members of a faction
    protected void broadcastToFaction(Faction faction, Component message) {
        for (UUID memberUuid : faction.getMembers()) {
            Player member = Bukkit.getPlayer(memberUuid);
            if (member != null && member.isOnline()) {
                member.sendMessage(message);
            }
        }
    }
}
