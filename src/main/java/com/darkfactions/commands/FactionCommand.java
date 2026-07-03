package com.darkfactions.commands;

// Handles all /f (and /faction) subcommands by routing args[0] to a handler.

import com.darkfactions.DarkFactions;
import com.darkfactions.utils.MessageUtils;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class FactionCommand implements CommandExecutor {

    // Reference to main plugin
    private final DarkFactions plugin;

    // Utility for sending fancy messages
    private final MessageUtils msg;

    // Per-domain subcommand handlers
    private final FactionMembershipCommands membershipCommands;
    private final FactionTerritoryCommands territoryCommands;
    private final FactionInfoCommands infoCommands;
    private final FactionEconomyCommands economyCommands;
    private final FactionSocialCommands socialCommands;
    private final FactionAdminCommands adminCommands;

    // ==========================================
    // Constructor
    // ==========================================
    public FactionCommand(DarkFactions plugin) {
        this.plugin = plugin;
        this.msg = plugin.getMessageUtils();
        this.membershipCommands = new FactionMembershipCommands(plugin);
        this.territoryCommands = new FactionTerritoryCommands(plugin);
        this.infoCommands = new FactionInfoCommands(plugin);
        this.economyCommands = new FactionEconomyCommands(plugin);
        this.socialCommands = new FactionSocialCommands(plugin);
        this.adminCommands = new FactionAdminCommands(plugin, infoCommands);
    }

    // ==========================================
    // Main command handler
    // Called whenever someone types /f or /faction
    // ==========================================
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // Console can use admin and read-only commands; player-only commands
        // check sender instanceof Player individually.
        if (args.length == 0) {
            if (sender instanceof Player) {
                infoCommands.sendHelp((Player) sender);
            } else {
                sender.sendMessage(msg.error("Usage: /f <subcommand> [args]"));
            }
            return true;
        }

        // Grab the subcommand (lowercase for consistency)
        String subCommand = args[0].toLowerCase();

        // Handle console-safe commands immediately before the player-cast
        if (!(sender instanceof Player)) {
            return adminCommands.handleConsole(sender, subCommand, args);
        }

        Player player = (Player) sender;

        // ==========================================
        // Route to the right subcommand handler
        // ==========================================
        switch (subCommand) {
            // ==========================================
            // Basic faction commands
            // ==========================================
            case "create":    return membershipCommands.handleCreate(player, args);
            case "disband":   return membershipCommands.handleDisband(player);
            case "invite":
            case "add":       return membershipCommands.handleInvite(player, args);
            case "uninvite":
            case "revoke":    return membershipCommands.handleUninvite(player, args);
            case "accept":    return membershipCommands.handleAccept(player, args);
            case "deny":      return membershipCommands.handleDeny(player, args);
            case "invites":   return membershipCommands.handleInvites(player);
            case "kick":      return membershipCommands.handleKick(player, args);
            case "leave":     return membershipCommands.handleLeave(player);
            case "promote":   return membershipCommands.handlePromote(player, args);
            case "demote":    return membershipCommands.handleDemote(player, args);
            case "leader":    return membershipCommands.handleLeader(player, args);
            case "rename":    return membershipCommands.handleRename(player, args);

            // ==========================================
            // Faction home commands
            // ==========================================
            case "sethome":   return territoryCommands.handleSetHome(player);
            case "home":      return territoryCommands.handleHome(player);

            // ==========================================
            // Faction info commands
            // ==========================================
            case "who":
            case "info":      return infoCommands.handleInfo(player, args);
            case "list":      return infoCommands.handleList(player);
            case "show":      return infoCommands.handleShow(player, args);
            case "top":       return infoCommands.handleTop(player, args);

            // ==========================================
            // Land claiming commands
            // ==========================================
            case "claim":     return territoryCommands.handleClaim(player);
            case "unclaim":   return territoryCommands.handleUnclaim(player);
            case "unclaimall": return territoryCommands.handleUnclaimAll(player);
            case "map":       return territoryCommands.handleMap(player, args);
            case "autoclaim": return territoryCommands.handleAutoClaim(player);

            // ==========================================
            // Power and elixir commands
            // ==========================================
            case "power":     return economyCommands.handlePower(player);
            case "elixir":    return economyCommands.handleElixir(player);
            case "elixirbal":
            case "bal":       return economyCommands.handleElixirBal(player, args);
            case "shop":      return economyCommands.handleShop(player, args);
            case "transfer":  return economyCommands.handleElixirTransfer(player, args);

            // ==========================================
            // Faction customization
            // ==========================================
            case "motd":      return socialCommands.handleMotd(player, args);
            case "desc":
            case "description": return socialCommands.handleDesc(player, args);
            case "tag":       return socialCommands.handleTag(player, args);
            case "open":      return socialCommands.handleOpen(player);
            case "pvp":       return socialCommands.handlePvp(player);
            case "tnt":       return socialCommands.handleTnt(player);

            // ==========================================
            // Chat commands
            // ==========================================
            case "chat":
            case "fc":        return socialCommands.handleChat(player);
            case "allychat":
            case "ac":        return socialCommands.handleAllyChat(player);

            // ==========================================
            // Alliance and enemy commands
            // ==========================================
            case "ally":      return socialCommands.handleAlly(player, args);
            case "enemy":     return socialCommands.handleEnemy(player, args);
            case "neutral":   return socialCommands.handleNeutral(player, args);

            // ==========================================
            // Admin commands
            // ==========================================
            case "admin":     return adminCommands.handleAdmin(player, args);
            case "reload":    return adminCommands.handleReload(player);

            // ==========================================
            // Fly command
            // ==========================================
            case "fly":       return territoryCommands.handleFly(player);
            case "logout":    return territoryCommands.handleLogout(player);

            // ==========================================
            // If they typed something we dont recognize
            // ==========================================
            default:
                player.sendMessage(msg.error("Unknown subcommand. Use /f for help."));
                return true;
        }
    }

    // ==========================================
    // Passthroughs used by listeners/managers
    // ==========================================

    // Cancel a pending warmup for a player. Called from the listener on move/damage.
    public void cancelWarmup(UUID playerUuid, boolean dueToDamage) {
        territoryCommands.cancelWarmup(playerUuid, dueToDamage);
    }

    // Check if a player is in faction chat mode
    public String getChatMode(UUID playerUuid) {
        return socialCommands.getChatMode(playerUuid);
    }

    // Clear chat mode for a player (e.g. when kicked/leaves)
    public void clearChatMode(UUID playerUuid) {
        socialCommands.clearChatMode(playerUuid);
    }

    // Check if a player has auto-claim enabled
    public boolean isAutoClaiming(UUID playerUuid) {
        return territoryCommands.isAutoClaiming(playerUuid);
    }
}
