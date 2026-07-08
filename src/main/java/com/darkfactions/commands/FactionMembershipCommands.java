package com.darkfactions.commands;

// Handles faction membership subcommands: create, disband, invites, roster changes.

import com.darkfactions.DarkFactions;
import com.darkfactions.models.Faction;
import com.darkfactions.utils.FactionNameValidator;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class FactionMembershipCommands extends AbstractFactionSubcommand {

    public FactionMembershipCommands(DarkFactions plugin) {
        super(plugin);
    }

    // ==========================================
    // CREATE - /f create <name>
    // Creates a brand new faction
    // ==========================================
    boolean handleCreate(Player player, String[] args) {

        if (!player.hasPermission("darkfactions.create")) {
            player.sendMessage(msg.error("You don't have permission to create factions!"));
            return true;
        }

        if (!requireArgs(player, args, 2, "/f create <name>")) return true;

        String factionName = args[1];

        // Validate name length and characters (see FactionNameValidator)
        int minLen = plugin.getConfigManager().getMinFactionNameLength();
        int maxLen = plugin.getConfigManager().getMaxFactionNameLength();
        String allowedPattern = plugin.getConfigManager().getFactionNameAllowedChars();
        FactionNameValidator.Result nameCheck = FactionNameValidator.validate(factionName, minLen, maxLen, allowedPattern);
        if (nameCheck == FactionNameValidator.Result.INVALID_LENGTH) {
            player.sendMessage(msg.error("Faction name must be between " + minLen + " and " + maxLen + " characters!"));
            return true;
        }
        if (nameCheck == FactionNameValidator.Result.INVALID_CHARS) {
            player.sendMessage(msg.error("Faction name contains invalid characters! Allowed: " + allowedPattern));
            return true;
        }

        if (plugin.getFactionManager().isFactionNameTaken(factionName)) {
            player.sendMessage(msg.error("A faction with that name already exists!"));
            return true;
        }

        if (plugin.getFactionManager().getPlayerFaction(player.getUniqueId()) != null) {
            player.sendMessage(msg.error("Failed to create faction! Are you already in one?"));
            return true;
        }

        double createCost = plugin.getConfigManager().getElixirCreateFactionCost();
        if (createCost > 0) {
            if (!plugin.getElixirManager().removePendingElixir(player.getUniqueId(), createCost)) {
                player.sendMessage(msg.error("You need " + String.format("%.0f", createCost)
                        + " pending elixir to create a faction!"));
                return true;
            }
        }

        Faction faction = plugin.getFactionManager().createFaction(factionName, player.getUniqueId());

        if (faction == null) {
            if (createCost > 0) {
                plugin.getElixirManager().addPendingElixir(player.getUniqueId(), createCost);
            }
            player.sendMessage(msg.error("Failed to create faction! Are you already in one?"));
            return true;
        }

        player.sendMessage(msg.success("Faction '" + factionName + "' has been created!"));
        player.sendMessage(msg.info("Use /f invite <player> to add members!"));

        if (plugin.getConfigManager().isBroadcastFactionNews()) {
            Bukkit.broadcast(msg.info("Faction '" + factionName + "' has been founded by " + player.getName() + "!"));
        }

        return true;
    }

    // ==========================================
    // DISBAND - /f disband [factionName]
    // Deletes the faction (leader only); optional name confirm
    // ==========================================
    boolean handleDisband(Player player, String[] args) {

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        if (!requireLeader(player, faction, "Only the faction leader can disband the faction!")) return true;

        String factionName = faction.getName();

        if (plugin.getConfigManager().isDisbandRequiresConfirm()) {
            if (args.length < 2 || !args[1].equalsIgnoreCase(factionName)) {
                player.sendMessage(msg.error("To confirm, type /f disband " + factionName));
                return true;
            }
        }

        // deleteFaction() already removes this faction's claims internally.
        plugin.getFactionManager().deleteFaction(faction.getFactionId());

        // Broadcast to all online members
        for (UUID memberUuid : faction.getMembers()) {
            Player member = Bukkit.getPlayer(memberUuid);
            if (member != null && member.isOnline()) {
                member.sendMessage(msg.error("Your faction '" + factionName + "' has been disbanded!"));
            }
        }

        player.sendMessage(msg.success("Faction '" + factionName + "' has been disbanded!"));

        if (plugin.getConfigManager().isBroadcastFactionNews()) {
            Bukkit.broadcast(msg.info("Faction '" + factionName + "' has been disbanded!"));
        }

        return true;
    }

    // ==========================================
    // INVITE / ADD - /f invite <player>
    // Sends a faction invite to a player (they must /f accept)
    // ==========================================
    boolean handleInvite(Player player, String[] args) {

        if (!requireArgs(player, args, 2, "/f invite <player>")) return true;

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        if (!requireOfficerOrMemberPermitted(player, faction, plugin.getConfigManager().isMembersCanInvite(),
                "Only the leader and officers can invite players!")) return true;

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(msg.error("That player is not online!"));
            return true;
        }

        if (plugin.getFactionManager().getPlayerFaction(target.getUniqueId()) != null) {
            player.sendMessage(msg.error("That player is already in a faction!"));
            return true;
        }

        // Prevent invite spam - check if already invited
        if (plugin.getFactionManager().hasPendingInvite(target.getUniqueId(), faction.getFactionId())) {
            player.sendMessage(msg.error("That player has already been invited!"));
            return true;
        }

        // Send the invite
        plugin.getFactionManager().sendInvite(
                player.getUniqueId(), faction.getFactionId(), target.getUniqueId()
        );

        player.sendMessage(msg.success("Invite sent to " + target.getName() + "!"));
        target.sendMessage(msg.info("You have been invited to join " + faction.getName() + "!"));
        target.sendMessage(msg.info("Type /f accept " + faction.getName() + " to join!"));

        return true;
    }

    // ==========================================
    // UNINVITE / REVOKE - /f uninvite <player>
    // Revokes a pending invite
    // ==========================================
    boolean handleUninvite(Player player, String[] args) {

        if (!requireArgs(player, args, 2, "/f uninvite <player>")) return true;

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        if (!requireOfficerOrMemberPermitted(player, faction, plugin.getConfigManager().isMembersCanInvite(),
                "Only the leader and officers can manage invites!")) return true;

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(msg.error("That player is not online!"));
            return true;
        }

        if (!plugin.getFactionManager().hasPendingInvite(target.getUniqueId(), faction.getFactionId())) {
            player.sendMessage(msg.error("That player doesn't have a pending invite!"));
            return true;
        }

        plugin.getFactionManager().denyInvite(target.getUniqueId(), faction.getFactionId());
        player.sendMessage(msg.success("Invite for " + target.getName() + " has been revoked!"));
        target.sendMessage(msg.info("Your invite to " + faction.getName() + " has been revoked."));

        return true;
    }

    // ==========================================
    // ACCEPT - /f accept <faction>
    // Accepts a pending faction invite
    // ==========================================
    boolean handleAccept(Player player, String[] args) {

        if (!requireArgs(player, args, 2, "/f accept <faction>")) return true;

        // Check theyre not already in a faction
        if (plugin.getFactionManager().getPlayerFaction(player.getUniqueId()) != null) {
            player.sendMessage(msg.error("You're already in a faction! Leave first."));
            return true;
        }

        Faction faction = plugin.getFactionManager().getFactionByName(args[1]);
        if (faction == null) {
            player.sendMessage(msg.error("No faction found with that name!"));
            return true;
        }

        // Check if open faction (anyone can join)
        if (faction.isOpen()) {
            boolean joined = plugin.getFactionManager().addPlayerToFaction(
                    player.getUniqueId(), faction.getFactionId()
            );
            if (!joined) {
                player.sendMessage(msg.error("Could not join that faction! It might be full."));
                return true;
            }
            player.sendMessage(msg.success("You joined " + faction.getName() + "!"));
            broadcastToFaction(faction, msg.info(player.getName() + " has joined the faction!"));
            return true;
        }

        // Check for pending invite
        if (!plugin.getFactionManager().hasPendingInvite(player.getUniqueId(), faction.getFactionId())) {
            player.sendMessage(msg.error("You don't have an invite from that faction!"));
            return true;
        }

        // Accept the invite
        boolean accepted = plugin.getFactionManager().acceptInvite(player.getUniqueId(), faction.getFactionId());
        if (!accepted) {
            player.sendMessage(msg.error("Could not accept that invite!"));
            return true;
        }

        player.sendMessage(msg.success("You joined " + faction.getName() + "!"));

        // Show MOTD if the faction has one
        if (faction.hasMotd()) {
            player.sendMessage(msg.header("Faction MOTD"));
            player.sendMessage(msg.info(faction.getMotd()));
        }

        broadcastToFaction(faction, msg.info(player.getName() + " has joined the faction!"));

        return true;
    }

    // ==========================================
    // DENY - /f deny <faction>
    // Denies a pending faction invite
    // ==========================================
    boolean handleDeny(Player player, String[] args) {

        if (!requireArgs(player, args, 2, "/f deny <faction>")) return true;

        Faction faction = plugin.getFactionManager().getFactionByName(args[1]);
        if (faction == null) {
            player.sendMessage(msg.error("No faction found with that name!"));
            return true;
        }

        if (!plugin.getFactionManager().hasPendingInvite(player.getUniqueId(), faction.getFactionId())) {
            player.sendMessage(msg.error("You don't have an invite from that faction!"));
            return true;
        }

        plugin.getFactionManager().denyInvite(player.getUniqueId(), faction.getFactionId());
        player.sendMessage(msg.info("You denied the invite from " + faction.getName() + "."));

        return true;
    }

    // ==========================================
    // INVITES - /f invites
    // Lists all pending invites for the player
    // ==========================================
    boolean handleInvites(Player player) {

        List<UUID> invites = plugin.getFactionManager().getPendingInvites(player.getUniqueId());

        if (invites.isEmpty()) {
            player.sendMessage(msg.error("You have no pending faction invites!"));
            return true;
        }

        player.sendMessage(msg.header("Your Pending Invites"));

        for (UUID factionId : invites) {
            Faction faction = plugin.getFactionManager().getFaction(factionId);
            if (faction != null) {
                player.sendMessage(msg.info("- " + faction.getName() +
                        " (use /f accept " + faction.getName() + ")"));
            }
        }

        return true;
    }

    // ==========================================
    // KICK - /f kick <player>
    // Removes a player from the faction
    // ==========================================
    boolean handleKick(Player player, String[] args) {

        if (!requireArgs(player, args, 2, "/f kick <player>")) return true;

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        if (!requireOfficerOrMemberPermitted(player, faction, plugin.getConfigManager().isMembersCanKick(),
                "Only the leader and officers can kick players!")) return true;

        Player target = Bukkit.getPlayerExact(args[1]);
        UUID targetUuid;

        if (target != null && target.isOnline() && faction.isMember(target.getUniqueId())) {
            targetUuid = target.getUniqueId();
        } else {
            targetUuid = findPlayerUuidByName(args[1], faction);
            if (targetUuid == null) {
                player.sendMessage(msg.error("Could not find that player in your faction!"));
                return true;
            }
        }

        if (faction.isLeader(targetUuid)) {
            player.sendMessage(msg.error("You cannot kick the faction leader!"));
            return true;
        }

        if (faction.isOfficer(targetUuid) && !faction.isLeader(player.getUniqueId())) {
            player.sendMessage(msg.error("Only the leader can kick officers!"));
            return true;
        }

        plugin.getFactionManager().removePlayerFromFaction(targetUuid);

        player.sendMessage(msg.success("Player has been kicked from the faction!"));

        Player kickedPlayer = Bukkit.getPlayer(targetUuid);
        if (kickedPlayer != null && kickedPlayer.isOnline()) {
            kickedPlayer.sendMessage(msg.error("You have been kicked from " + faction.getName() + "!"));
        }

        broadcastToFaction(faction, msg.info("A player has been kicked from the faction."));

        return true;
    }

    // ==========================================
    // LEAVE - /f leave
    // Player leaves their current faction
    // ==========================================
    boolean handleLeave(Player player) {

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        if (faction.isLeader(player.getUniqueId())) {
            player.sendMessage(msg.error("You are the leader! Use /f leader <player> to transfer leadership first."));
            return true;
        }

        String factionName = faction.getName();
        plugin.getFactionManager().removePlayerFromFaction(player.getUniqueId());

        player.sendMessage(msg.success("You have left " + factionName + "!"));
        broadcastToFaction(faction, msg.info(player.getName() + " has left the faction."));

        return true;
    }

    // ==========================================
    // PROMOTE - /f promote <player>
    // Promotes a member to officer (leader only)
    // ==========================================
    boolean handlePromote(Player player, String[] args) {

        if (!requireArgs(player, args, 2, "/f promote <player>")) return true;

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        if (!requireLeader(player, faction, "Only the faction leader can promote members!")) return true;

        UUID targetUuid = findPlayerUuidByName(args[1], faction);
        if (targetUuid == null) {
            player.sendMessage(msg.error("That player is not in your faction!"));
            return true;
        }

        if (faction.isOfficer(targetUuid)) {
            player.sendMessage(msg.error("That player is already an officer!"));
            return true;
        }

        if (!plugin.getFactionManager().promotePlayer(targetUuid, faction.getFactionId())) {
            player.sendMessage(msg.error("Cannot promote: maximum number of officers reached!"));
            return true;
        }
        player.sendMessage(msg.success(args[1] + " has been promoted to officer!"));

        Player target = Bukkit.getPlayer(targetUuid);
        if (target != null && target.isOnline()) {
            target.sendMessage(msg.success("You have been promoted to officer in " + faction.getName() + "!"));
        }

        return true;
    }

    // ==========================================
    // DEMOTE - /f demote <player>
    // Demotes an officer back to member (leader only)
    // ==========================================
    boolean handleDemote(Player player, String[] args) {

        if (!requireArgs(player, args, 2, "/f demote <player>")) return true;

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        if (!requireLeader(player, faction, "Only the faction leader can demote officers!")) return true;

        UUID targetUuid = findPlayerUuidByName(args[1], faction);
        if (targetUuid == null) {
            player.sendMessage(msg.error("That player is not in your faction!"));
            return true;
        }

        if (!faction.isOfficer(targetUuid)) {
            player.sendMessage(msg.error("That player is not an officer!"));
            return true;
        }

        plugin.getFactionManager().demotePlayer(targetUuid, faction.getFactionId());
        player.sendMessage(msg.success(args[1] + " has been demoted to member!"));

        Player target = Bukkit.getPlayer(targetUuid);
        if (target != null && target.isOnline()) {
            target.sendMessage(msg.error("You have been demoted to member in " + faction.getName() + "!"));
        }

        return true;
    }

    // ==========================================
    // LEADER - /f leader <player>
    // Transfers faction leadership
    // ==========================================
    boolean handleLeader(Player player, String[] args) {

        if (!requireArgs(player, args, 2, "/f leader <player>")) return true;

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        if (!requireLeader(player, faction, "Only the faction leader can transfer leadership!")) return true;

        UUID targetUuid = findPlayerUuidByName(args[1], faction);
        if (targetUuid == null) {
            player.sendMessage(msg.error("That player is not in your faction!"));
            return true;
        }

        if (faction.isLeader(targetUuid)) {
            player.sendMessage(msg.error("You are already the leader!"));
            return true;
        }

        String newLeaderName = plugin.getPlayerNameCache().getPlayerName(targetUuid);

        plugin.getFactionManager().transferLeadership(faction.getFactionId(), targetUuid);
        player.sendMessage(msg.success("Leadership transferred to " + newLeaderName + "!"));

        Player target = Bukkit.getPlayer(targetUuid);
        if (target != null && target.isOnline()) {
            target.sendMessage(msg.success("You are now the leader of " + faction.getName() + "!"));
        }

        broadcastToFaction(faction, msg.info(faction.getName() + " has a new leader: " + newLeaderName + "!"));

        return true;
    }

    // ==========================================
    // RENAME - /f rename <name>
    // Renames the faction (leader only)
    // ==========================================
    boolean handleRename(Player player, String[] args) {

        if (!requireArgs(player, args, 2, "/f rename <name>")) return true;

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        if (!requireLeader(player, faction, "Only the faction leader can rename the faction!")) return true;

        String newName = args[1];

        // Validate name length and characters (see FactionNameValidator)
        int minLen = plugin.getConfigManager().getMinFactionNameLength();
        int maxLen = plugin.getConfigManager().getMaxFactionNameLength();
        String allowed = plugin.getConfigManager().getFactionNameAllowedChars();
        FactionNameValidator.Result nameCheck = FactionNameValidator.validate(newName, minLen, maxLen, allowed);
        if (nameCheck == FactionNameValidator.Result.INVALID_LENGTH) {
            player.sendMessage(msg.error("Faction name must be between " + minLen + " and " + maxLen + " characters!"));
            return true;
        }
        if (nameCheck == FactionNameValidator.Result.INVALID_CHARS) {
            // Report the actual configured pattern instead of a hard-coded guess.
            player.sendMessage(msg.error("Faction name contains invalid characters! Allowed: " + allowed));
            return true;
        }

        double renameCost = plugin.getConfigManager().getElixirRenameCost();
        if (renameCost > 0 && !faction.removeElixir(renameCost)) {
            player.sendMessage(msg.error("Your faction needs " + String.format("%.0f", renameCost)
                    + " elixir to rename!"));
            return true;
        }

        String oldName = faction.getName();

        if (plugin.getFactionManager().renameFaction(faction.getFactionId(), newName)) {
            player.sendMessage(msg.success("Faction renamed from '" + oldName + "' to '" + newName + "'!"));
            broadcastToFaction(faction, msg.info("Faction has been renamed to '" + newName + "'!"));
        } else {
            if (renameCost > 0) {
                faction.addElixir(renameCost);
            }
            player.sendMessage(msg.error("That name is already taken!"));
        }

        return true;
    }
}
