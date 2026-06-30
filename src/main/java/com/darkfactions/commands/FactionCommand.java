package com.darkfactions.commands;

// Handles all /f (and /faction) subcommands by routing args[0] to a handler.

import com.darkfactions.DarkFactions;
import com.darkfactions.managers.ClaimResult;
import com.darkfactions.models.Faction;
import com.darkfactions.utils.FactionListFormatter;
import com.darkfactions.utils.FactionNameValidator;
import com.darkfactions.utils.MessageUtils;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FactionCommand implements CommandExecutor {

    // Reference to main plugin
    private final DarkFactions plugin;

    // Utility for sending fancy messages
    private final MessageUtils msg;

    // Track players who have auto-claim enabled
    private final Map<UUID, Boolean> autoClaimMap;

    // Track players who are in faction chat mode
    private final Map<UUID, String> chatModeMap; // "faction" or "ally" or null

    // Cooldowns for /f home to prevent spam
    private final Map<UUID, Long> homeCooldowns;

    // Pending home teleport warmups — tracked by BukkitTask so they can be cancelled
    // on movement or damage.
    private final Map<UUID, BukkitTask> pendingWarmups;

    private static final class ChatMode {
        static final String FACTION = "faction";
        static final String ALLY = "ally";
        private ChatMode() {}
    }

    // ==========================================
    // Constructor
    // ==========================================
    public FactionCommand(DarkFactions plugin) {
        this.plugin = plugin;
        this.msg = plugin.getMessageUtils();
        this.autoClaimMap = new ConcurrentHashMap<>();
        this.chatModeMap = new ConcurrentHashMap<>();
        this.homeCooldowns = new ConcurrentHashMap<>();
        this.pendingWarmups = new ConcurrentHashMap<>();
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
                sendHelp((Player) sender);
            } else {
                sender.sendMessage(msg.error("Usage: /f <subcommand> [args]"));
            }
            return true;
        }

        // Grab the subcommand (lowercase for consistency)
        String subCommand = args[0].toLowerCase();

        // Handle console-safe commands immediately before the player-cast
        if (!(sender instanceof Player)) {
            return handleConsole(sender, subCommand, args);
        }

        Player player = (Player) sender;

        // ==========================================
        // Route to the right subcommand handler
        // ==========================================
        switch (subCommand) {
            // ==========================================
            // Basic faction commands
            // ==========================================
            case "create":    return handleCreate(player, args);
            case "disband":   return handleDisband(player);
            case "invite":
            case "add":       return handleInvite(player, args);
            case "uninvite":
            case "revoke":    return handleUninvite(player, args);
            case "accept":    return handleAccept(player, args);
            case "deny":      return handleDeny(player, args);
            case "invites":   return handleInvites(player);
            case "kick":      return handleKick(player, args);
            case "leave":     return handleLeave(player);
            case "promote":   return handlePromote(player, args);
            case "demote":    return handleDemote(player, args);
            case "leader":    return handleLeader(player, args);
            case "rename":    return handleRename(player, args);

            // ==========================================
            // Faction home commands
            // ==========================================
            case "sethome":   return handleSetHome(player);
            case "home":      return handleHome(player);

            // ==========================================
            // Faction info commands
            // ==========================================
            case "who":
            case "info":      return handleInfo(player, args);
            case "list":      return handleList(player);
            case "show":      return handleShow(player, args);
            case "top":       return handleTop(player, args);

            // ==========================================
            // Land claiming commands
            // ==========================================
            case "claim":     return handleClaim(player);
            case "unclaim":   return handleUnclaim(player);
            case "unclaimall": return handleUnclaimAll(player);
            case "map":       return handleMap(player, args);
            case "autoclaim": return handleAutoClaim(player);

            // ==========================================
            // Power and elixir commands
            // ==========================================
            case "power":     return handlePower(player);
            case "elixir":    return handleElixir(player);
            case "elixirbal":
            case "bal":       return handleElixirBal(player, args);
            case "shop":      return handleShop(player, args);
            case "transfer":  return handleElixirTransfer(player, args);

            // ==========================================
            // Faction customization
            // ==========================================
            case "motd":      return handleMotd(player, args);
            case "desc":
            case "description": return handleDesc(player, args);
            case "tag":       return handleTag(player, args);
            case "open":      return handleOpen(player);
            case "pvp":       return handlePvp(player);
            case "tnt":       return handleTnt(player);

            // ==========================================
            // Chat commands
            // ==========================================
            case "chat":
            case "fc":        return handleChat(player);
            case "allychat":
            case "ac":        return handleAllyChat(player);

            // ==========================================
            // Alliance and enemy commands
            // ==========================================
            case "ally":      return handleAlly(player, args);
            case "enemy":     return handleEnemy(player, args);
            case "neutral":   return handleNeutral(player, args);

            // ==========================================
            // Admin commands
            // ==========================================
            case "admin":     return handleAdmin(player, args);
            case "reload":    return handleReload(player);

            // ==========================================
            // Fly command
            // ==========================================
            case "fly":       return handleFly(player);
            case "logout":    return handleLogout(player);

            // ==========================================
            // If they typed something we dont recognize
            // ==========================================
            default:
                player.sendMessage(msg.error("Unknown subcommand. Use /f for help."));
                return true;
        }
    }

    // ==========================================
    // HELP - Shows all available commands
    // ==========================================
    private void sendHelp(Player player) {
        player.sendMessage(msg.header("DarkFactions Commands"));
        player.sendMessage(msg.help("/f create <name>", "Create a new faction"));
        player.sendMessage(msg.help("/f invite <player>", "Invite a player to your faction"));
        player.sendMessage(msg.help("/f accept <faction>", "Accept a faction invite"));
        player.sendMessage(msg.help("/f deny <faction>", "Deny a faction invite"));
        player.sendMessage(msg.help("/f invites", "List your pending invites"));
        player.sendMessage(msg.help("/f kick <player>", "Kick a player from your faction"));
        player.sendMessage(msg.help("/f leave", "Leave your current faction"));
        player.sendMessage(msg.help("/f promote <player>", "Promote a member to officer"));
        player.sendMessage(msg.help("/f demote <player>", "Demote an officer to member"));
        player.sendMessage(msg.help("/f leader <player>", "Transfer faction leadership"));
        player.sendMessage(msg.help("/f rename <name>", "Rename your faction"));
        player.sendMessage(msg.help("/f sethome", "Set your faction's home"));
        player.sendMessage(msg.help("/f home", "Teleport to faction home"));
        player.sendMessage(msg.help("/f claim", "Claim the chunk you're standing in"));
        player.sendMessage(msg.help("/f unclaim", "Unclaim the current chunk"));
        player.sendMessage(msg.help("/f unclaimall", "Unclaim all faction land"));
        player.sendMessage(msg.help("/f map [radius]", "Show a map of nearby claims"));
        player.sendMessage(msg.help("/f autoclaim", "Toggle auto-claiming chunks"));
        player.sendMessage(msg.help("/f who [player]", "Show faction info"));
        player.sendMessage(msg.help("/f list", "List all factions"));
        player.sendMessage(msg.help("/f show <faction>", "Show faction details"));
        player.sendMessage(msg.help("/f top [power|elixir]", "Show faction leaderboard"));
        player.sendMessage(msg.help("/f power", "Show your faction's power"));
        player.sendMessage(msg.help("/f elixir", "Show your faction's elixir"));
        player.sendMessage(msg.help("/f motd <message>", "Set faction message of the day"));
        player.sendMessage(msg.help("/f desc <text>", "Set faction description"));
        player.sendMessage(msg.help("/f tag <tag>", "Set faction prefix tag"));
        player.sendMessage(msg.help("/f open", "Toggle open join"));
        player.sendMessage(msg.help("/f pvp", "Toggle faction PvP"));
        player.sendMessage(msg.help("/f tnt", "Toggle TNT in territory"));
        player.sendMessage(msg.help("/f chat", "Toggle faction-only chat"));
        player.sendMessage(msg.help("/f allychat", "Toggle ally chat"));
        player.sendMessage(msg.help("/f ally <faction>", "Send ally request"));
        player.sendMessage(msg.help("/f enemy <faction>", "Declare enemy"));
        player.sendMessage(msg.help("/f neutral <faction>", "Set neutral"));
        player.sendMessage(msg.help("/f fly", "Toggle flight in own territory"));
    }

    // ==========================================
    // CREATE - /f create <name>
    // Creates a brand new faction
    // ==========================================
    private boolean handleCreate(Player player, String[] args) {

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

        Faction faction = plugin.getFactionManager().createFaction(factionName, player.getUniqueId());

        if (faction == null) {
            player.sendMessage(msg.error("Failed to create faction! Are you already in one?"));
            return true;
        }

        player.sendMessage(msg.success("Faction '" + factionName + "' has been created!"));
        player.sendMessage(msg.info("Use /f invite <player> to add members!"));

        return true;
    }

    // ==========================================
    // DISBAND - /f disband
    // Deletes the faction (leader only)
    // ==========================================
    private boolean handleDisband(Player player) {

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        if (!requireLeader(player, faction, "Only the faction leader can disband the faction!")) return true;

        String factionName = faction.getName();

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

        return true;
    }

    // ==========================================
    // INVITE / ADD - /f invite <player>
    // Sends a faction invite to a player (they must /f accept)
    // ==========================================
    private boolean handleInvite(Player player, String[] args) {

        if (!requireArgs(player, args, 2, "/f invite <player>")) return true;

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        if (!requireOfficer(player, faction, "Only the leader and officers can invite players!")) return true;

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
    private boolean handleUninvite(Player player, String[] args) {

        if (!requireArgs(player, args, 2, "/f uninvite <player>")) return true;

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        if (!requireOfficer(player, faction, "Only the leader and officers can manage invites!")) return true;

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
    private boolean handleAccept(Player player, String[] args) {

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
    private boolean handleDeny(Player player, String[] args) {

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
    private boolean handleInvites(Player player) {

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
    private boolean handleKick(Player player, String[] args) {

        if (!requireArgs(player, args, 2, "/f kick <player>")) return true;

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        if (!requireOfficer(player, faction, "Only the leader and officers can kick players!")) return true;

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
    private boolean handleLeave(Player player) {

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
    private boolean handlePromote(Player player, String[] args) {

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

        plugin.getFactionManager().promotePlayer(targetUuid, faction.getFactionId());
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
    private boolean handleDemote(Player player, String[] args) {

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
    private boolean handleLeader(Player player, String[] args) {

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
    private boolean handleRename(Player player, String[] args) {

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

        String oldName = faction.getName();

        if (plugin.getFactionManager().renameFaction(faction.getFactionId(), newName)) {
            player.sendMessage(msg.success("Faction renamed from '" + oldName + "' to '" + newName + "'!"));
            broadcastToFaction(faction, msg.info("Faction has been renamed to '" + newName + "'!"));
        } else {
            player.sendMessage(msg.error("That name is already taken!"));
        }

        return true;
    }

    // ==========================================
    // SETHOME - /f sethome
    // Sets the faction home at the player's location
    // ==========================================
    private boolean handleSetHome(Player player) {

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        if (!requireOfficer(player, faction, "Only the leader and officers can set the faction home!")) return true;

        plugin.getFactionManager().setFactionHome(faction.getFactionId(), player.getLocation());
        player.sendMessage(msg.success("Faction home has been set!"));

        return true;
    }

    // ==========================================
    // HOME - /f home
    // Teleports the player to the faction home with warmup
    // ==========================================
    private boolean handleHome(Player player) {

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        if (!faction.hasHome()) {
            player.sendMessage(msg.error("Your faction hasn't set a home yet! Use /f sethome."));
            return true;
        }

        // Combat tag check — no teleporting out of combat
        if (plugin.getConfigManager().isCombatTagPreventHome() && plugin.getCombatManager().isTagged(player.getUniqueId())) {
            player.sendMessage(msg.error("You cannot teleport home during combat!"));
            return true;
        }

        int cooldown = plugin.getConfigManager().getHomeCooldown();
        if (cooldown > 0) {
            long lastUsed = homeCooldowns.getOrDefault(player.getUniqueId(), 0L);
            long elapsed = (System.currentTimeMillis() - lastUsed) / 1000;
            if (elapsed < cooldown) {
                long remaining = cooldown - elapsed;
                player.sendMessage(msg.error("You must wait " + remaining + " more seconds to use /f home!"));
                return true;
            }
        }

        Location home = plugin.getFactionManager().getFactionHome(faction.getFactionId());
        if (home == null) {
            player.sendMessage(msg.error("The faction home world doesn't exist anymore!"));
            return true;
        }

        int delay = plugin.getConfigManager().getHomeTeleportDelay();
        if (delay > 0) {
            player.sendMessage(msg.info("Teleporting in " + delay + " seconds... don't move."));
            int warmupTicks = delay * 20;
            BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                pendingWarmups.remove(player.getUniqueId());
                if (!player.isOnline()) return;
                homeCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
                player.teleport(home);
                player.sendMessage(msg.success("Welcome to your faction home!"));
            }, warmupTicks);
            pendingWarmups.put(player.getUniqueId(), task);
        } else {
            homeCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
            player.teleport(home);
            player.sendMessage(msg.success("Welcome to your faction home!"));
        }

        return true;
    }

    // Cancel a pending warmup for a player. Called from the listener on move/damage.
    public void cancelWarmup(UUID playerUuid, boolean dueToDamage) {
        BukkitTask task = pendingWarmups.remove(playerUuid);
        if (task != null) {
            task.cancel();
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                if (dueToDamage) {
                    player.sendMessage(msg.error("Teleport cancelled due to damage!"));
                } else {
                    player.sendMessage(msg.error("Teleport cancelled!"));
                }
            }
        }
    }

    // ==========================================
    // CLAIM - /f claim
    // Claims the chunk the player is standing in
    // ==========================================
    private boolean handleClaim(Player player) {

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        if (!requireOfficer(player, faction, "Only the leader and officers can claim land!")) return true;

        Chunk chunk = player.getLocation().getChunk();
        ClaimResult result = plugin.getClaimManager().claimChunk(chunk, faction.getFactionId());

        if (result.isSuccess()) {
            player.sendMessage(msg.success("Chunk claimed for " + faction.getName() + "!"));
            player.sendMessage(msg.info("Claims: " + plugin.getClaimManager().getClaimCount(faction.getFactionId())));
        } else {
            player.sendMessage(msg.error(result.getMessage()));
        }

        return true;
    }

    // ==========================================
    // UNCLAIM - /f unclaim
    // Unclaims the chunk the player is standing in
    // ==========================================
    private boolean handleUnclaim(Player player) {

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        if (!requireOfficer(player, faction, "Only the leader and officers can unclaim land!")) return true;

        Chunk chunk = player.getLocation().getChunk();
        UUID ownerId = plugin.getClaimManager().getClaimOwner(chunk);

        if (ownerId == null) {
            player.sendMessage(msg.error("This chunk is not claimed!"));
            return true;
        }

        if (!ownerId.equals(faction.getFactionId())) {
            player.sendMessage(msg.error("This chunk belongs to another faction!"));
            return true;
        }

        plugin.getClaimManager().unclaimChunk(chunk);
        player.sendMessage(msg.success("Chunk unclaimed!"));

        return true;
    }

    // ==========================================
    // UNCLAIM ALL - /f unclaimall
    // Unclaims all faction territory
    // ==========================================
    private boolean handleUnclaimAll(Player player) {

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        if (!requireLeader(player, faction, "Only the faction leader can unclaim all land!")) return true;

        int count = plugin.getClaimManager().unclaimAll(faction.getFactionId());

        player.sendMessage(msg.success("Unclaimed " + count + " chunks!"));

        return true;
    }

    // ==========================================
    // MAP - /f map [radius]
    // Shows a visual map of surrounding claims
    // ==========================================
    private boolean handleMap(Player player, String[] args) {

        int radius = plugin.getConfigManager().getMapDefaultRadius();

        if (args.length >= 2) {
            try {
                radius = Integer.parseInt(args[1]);
                int maxRadius = plugin.getConfigManager().getMapMaxRadius();
                if (radius < 1 || radius > maxRadius) {
                    player.sendMessage(msg.error("Radius must be between 1 and " + maxRadius + "!"));
                    return true;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(msg.error("Invalid radius!"));
                return true;
            }
        }

        Component map = plugin.getClaimManager().getAsciiMap(player, radius);
        player.sendMessage(map);

        // Show current chunk info
        Chunk chunk = player.getLocation().getChunk();
        UUID ownerId = plugin.getClaimManager().getClaimOwner(chunk);

        if (ownerId != null) {
            Faction owner = plugin.getFactionManager().getFaction(ownerId);
            if (owner != null) {
                player.sendMessage(msg.info("Location: " + owner.getName() + "'s territory"));
                return true;
            }
        }

        player.sendMessage(msg.info("Location: Wilderness"));

        return true;
    }

    // ==========================================
    // AUTOCLAIM - /f autoclaim
    // Toggles auto-claim when walking into unclaimed chunks
    // ==========================================
    private boolean handleAutoClaim(Player player) {

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        if (!requireOfficer(player, faction, "Only the leader and officers can use auto-claim!")) return true;

        boolean current = autoClaimMap.getOrDefault(player.getUniqueId(), false);
        boolean newState = !current;

        autoClaimMap.put(player.getUniqueId(), newState);

        if (newState) {
            player.sendMessage(msg.success("Auto-claim enabled! Walk into unclaimed chunks to claim them."));
        } else {
            player.sendMessage(msg.info("Auto-claim disabled."));
        }

        return true;
    }

    // ==========================================
    // WHO / INFO - /f who [player]
    // Shows information about a player's faction
    // ==========================================
    private boolean handleInfo(Player player, String[] args) {

        Faction faction;

        if (args.length >= 2) {
            // Look up by player name first
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target != null && target.isOnline()) {
                faction = plugin.getFactionManager().getPlayerFaction(target.getUniqueId());
            } else {
                // Try as faction name
                Faction byName = plugin.getFactionManager().getFactionByName(args[1]);
                if (byName != null) {
                    faction = byName;
                } else {
                    // Check name cache for offline player
                    UUID offlineUuid = plugin.getPlayerNameCache().getUuidFromName(args[1]);
                    if (offlineUuid != null) {
                        faction = plugin.getFactionManager().getPlayerFaction(offlineUuid);
                    } else {
                        player.sendMessage(msg.error("Player or faction not found!"));
                        return true;
                    }
                }
            }
        } else {
            faction = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());
        }

        if (faction == null) {
            player.sendMessage(msg.error("That player is not in a faction!"));
            return true;
        }

        player.sendMessage(msg.header("Faction: " + faction.getFormattedTag() + faction.getName()));
        player.sendMessage(msg.info("Leader: " + plugin.getPlayerNameCache().getPlayerName(faction.getLeaderUuid())));
        player.sendMessage(msg.info("Members: " + faction.getMemberCount()));
        player.sendMessage(msg.info("Power: " + String.format("%.1f", faction.getPower())));
        player.sendMessage(msg.info("Elixir: " + String.format("%.0f", faction.getElixir())));
        player.sendMessage(msg.info("Land: " + plugin.getClaimManager().getClaimCount(faction.getFactionId())));
        player.sendMessage(msg.info("Home Set: " + (faction.hasHome() ? "Yes" : "No")));
        player.sendMessage(msg.info("Open: " + (faction.isOpen() ? "Yes" : "No")));

        if (faction.hasDescription()) {
            player.sendMessage(msg.info("Description: " + faction.getDescription()));
        }

        if (!faction.getEnemies().isEmpty()) {
            player.sendMessage(msg.info("Enemies: " + faction.getEnemies().size()));
        }
        if (!faction.getAllies().isEmpty()) {
            player.sendMessage(msg.info("Allies: " + faction.getAllies().size()));
        }

        return true;
    }

    // ==========================================
    // LIST - /f list
    // Shows all factions on the server
    // ==========================================
    private boolean handleList(Player player) {

        List<Faction> factions = plugin.getFactionManager().getAllFactions();

        if (factions.isEmpty()) {
            player.sendMessage(msg.error("No factions exist yet! Create one with /f create <name>"));
            return true;
        }

        player.sendMessage(msg.header("Factions (" + factions.size() + ")"));

        for (Faction faction : factions) {
            player.sendMessage(msg.info(FactionListFormatter.listRow(
                    faction.getFormattedTag(), faction.getName(), faction.getMemberCount(),
                    faction.getPower(), plugin.getClaimManager().getClaimCount(faction.getFactionId()))));
        }

        return true;
    }

    // ==========================================
    // SHOW - /f show <faction>
    // Shows info about a specific faction by name
    // ==========================================
    private boolean handleShow(Player player, String[] args) {

        if (!requireArgs(player, args, 2, "/f show <faction>")) return true;

        Faction faction = plugin.getFactionManager().getFactionByName(args[1]);

        if (faction == null) {
            player.sendMessage(msg.error("No faction found with that name!"));
            return true;
        }

        player.sendMessage(msg.header("=== " + faction.getFormattedTag() + faction.getName() + " ==="));
        player.sendMessage(msg.info("Leader: " + plugin.getPlayerNameCache().getPlayerName(faction.getLeaderUuid())));

        // Show description if there is one
        if (faction.hasDescription()) {
            player.sendMessage(msg.info("\"" + faction.getDescription() + "\""));
        }

        player.sendMessage(msg.info("Members (" + faction.getMemberCount() + "):"));

        for (UUID memberUuid : faction.getMembers()) {
            String prefix = "  - ";
            if (faction.isLeader(memberUuid)) {
                prefix = "  - **Leader** ";
            } else if (faction.isOfficer(memberUuid)) {
                prefix = "  - *Officer* ";
            }
            player.sendMessage(msg.info(prefix + plugin.getPlayerNameCache().getPlayerName(memberUuid)));
        }

        player.sendMessage(msg.info("Power: " + String.format("%.1f", faction.getPower()) +
                "/" + String.format("%.1f", faction.getMaxPower())));
        player.sendMessage(msg.info("Elixir: " + String.format("%.0f", faction.getElixir())));
        player.sendMessage(msg.info("Land: " + plugin.getClaimManager().getClaimCount(faction.getFactionId()) + " chunks"));
        player.sendMessage(msg.info("Open: " + (faction.isOpen() ? "Yes - Anyone can join" : "No - Invite only")));
        player.sendMessage(msg.info("PvP: " + (faction.isPvpEnabled() ? "Enabled" : "Disabled")));
        player.sendMessage(msg.info("TNT: " + (faction.isTntEnabled() ? "Enabled" : "Disabled")));

        if (faction.hasHome()) {
            player.sendMessage(msg.info("Home: " + faction.getWorldName() + " at " +
                    String.format("%.0f", faction.getHomeX()) + ", " +
                    String.format("%.0f", faction.getHomeY()) + ", " +
                    String.format("%.0f", faction.getHomeZ())));
        }

        player.sendMessage(msg.info("Created: " + new java.text.SimpleDateFormat("MM/dd/yyyy")
                .format(new java.util.Date(faction.getCreationTime()))));

        return true;
    }

    // ==========================================
    // TOP - /f top [power|elixir|members|land]
    // Shows a leaderboard of factions
    // ==========================================
    private boolean handleTop(Player player, String[] args) {

        String sortBy = "power"; // Default sort by power
        if (args.length >= 2) {
            sortBy = args[1].toLowerCase();
        }

        int limit = plugin.getConfigManager().getLeaderboardDefaultLimit();
        List<Faction> sorted;

        switch (sortBy) {
            case "elixir":
                sorted = plugin.getFactionManager().getTopFactionsByElixir(limit);
                player.sendMessage(msg.header("Top Factions by Elixir"));
                break;
            case "members":
                sorted = plugin.getFactionManager().getTopFactionsByMembers(limit);
                player.sendMessage(msg.header("Top Factions by Members"));
                break;
            case "land":
                // Sort by claim count manually
                sorted = plugin.getFactionManager().getAllFactions();
                sorted.sort((a, b) -> Integer.compare(
                        plugin.getClaimManager().getClaimCount(b.getFactionId()),
                        plugin.getClaimManager().getClaimCount(a.getFactionId())
                ));
                sorted = sorted.subList(0, Math.min(limit, sorted.size()));
                player.sendMessage(msg.header("Top Factions by Land"));
                break;
            default: // power
                sorted = plugin.getFactionManager().getTopFactionsByPower(limit);
                player.sendMessage(msg.header("Top Factions by Power"));
                break;
        }

        if (sorted.isEmpty()) {
            player.sendMessage(msg.error("No factions to show!"));
            return true;
        }

        int rank = 1;
        for (Faction faction : sorted) {
            String value = switch (sortBy) {
                case "elixir" -> FactionListFormatter.metric(faction.getElixir(), 0, "elixir");
                case "members" -> faction.getMemberCount() + " members";
                case "land" -> plugin.getClaimManager().getClaimCount(faction.getFactionId()) + " claims";
                default -> FactionListFormatter.metric(faction.getPower(), 1, "power");
            };
            player.sendMessage(msg.info(FactionListFormatter.rankRow(
                    rank, faction.getFormattedTag(), faction.getName(), value)));
            rank++;
        }

        return true;
    }

    // ==========================================
    // POWER - /f power
    // Shows the faction's current power
    // ==========================================
    private boolean handlePower(Player player) {

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        double totalPower = plugin.getPowerManager().getFactionPower(faction.getFactionId());
        double playerPower = plugin.getPowerManager().getPlayerPower(player.getUniqueId());

        player.sendMessage(msg.header("Faction Power"));
        player.sendMessage(msg.info("Total Power: " + String.format("%.1f", totalPower)));
        player.sendMessage(msg.info("Your Power: " + String.format("%.1f", playerPower)));
        player.sendMessage(msg.info("Max Power: " + String.format("%.1f", faction.getMaxPower())));

        if (plugin.getPowerManager().isFactionRaidable(faction.getFactionId())) {
            player.sendMessage(msg.warning("Your faction is raidable! Power is too low!"));
        } else {
            player.sendMessage(msg.success("Your faction is protected!"));
        }

        return true;
    }

    // ==========================================
    // ELIXIR - /f elixir
    // Shows the faction's elixir balance
    // ==========================================
    private boolean handleElixir(Player player) {

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        double elixir = plugin.getElixirManager().getFactionElixir(faction.getFactionId());

        player.sendMessage(msg.header("Faction Elixir"));
        player.sendMessage(msg.info("Balance: " + String.format("%.0f", elixir) + " Elixir"));

        if (plugin.getElixirManager().claimPendingElixir(player.getUniqueId())) {
            player.sendMessage(msg.success("Daily elixir bonus claimed!"));
        }

        return true;
    }

    // ==========================================
    // ELIXIR BAL - /f bal <faction>
    // Check another faction's elixir
    // ==========================================
    private boolean handleElixirBal(Player player, String[] args) {

        Faction faction;

        if (args.length >= 2) {
            faction = plugin.getFactionManager().getFactionByName(args[1]);
        } else {
            faction = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());
        }

        if (faction == null) {
            player.sendMessage(msg.error("Faction not found!"));
            return true;
        }

        double elixir = plugin.getElixirManager().getFactionElixir(faction.getFactionId());
        player.sendMessage(msg.info(faction.getName() + " has " + String.format("%.0f", elixir) + " Elixir."));

        return true;
    }

    // ==========================================
    // SHOP - /f shop
    // Buy faction upgrades with elixir
    // ==========================================
    private boolean handleShop(Player player, String[] args) {

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        if (!requireOfficer(player, faction, "Only the leader and officers can use the shop!")) return true;

        if (args.length >= 3) {
            String item = args[1].toLowerCase();
            String amount = args[2];
            switch (item) {
                case "power":
                    handleShopPower(player, faction, amount);
                    return true;
                case "maxpower":
                    handleShopMaxPower(player, faction, amount);
                    return true;
                default:
                    player.sendMessage(msg.error("Unknown shop item! Use /f shop to see items."));
                    return true;
            }
        }

        double elixir = faction.getElixir();
        player.sendMessage(msg.header("Faction Shop"));
        player.sendMessage(msg.info("Balance: " + String.format("%.0f", elixir) + " Elixir"));
        player.sendMessage(msg.help("/f shop power [amount]", "Boost faction power (10 elixir per 1 power)"));
        player.sendMessage(msg.help("/f shop maxpower [amount]", "Increase max power (20 elixir per 5 max power)"));

        return true;
    }

    private void handleShopPower(Player player, Faction faction, String amountStr) {
        try {
            int amount = Integer.parseInt(amountStr);
            if (amount <= 0) { player.sendMessage(msg.error("Amount must be positive!")); return; }
            double cost = amount * 10.0;
            if (plugin.getElixirManager().boostFactionPower(faction.getFactionId(), cost, amount)) {
                player.sendMessage(msg.success("Boosted faction power by " + amount + " for " + String.format("%.0f", cost) + " elixir!"));
            } else {
                player.sendMessage(msg.error("Not enough elixir! Need " + String.format("%.0f", cost)));
            }
        } catch (NumberFormatException e) {
            player.sendMessage(msg.error("Invalid amount!"));
        }
    }

    private void handleShopMaxPower(Player player, Faction faction, String amountStr) {
        try {
            int amount = Integer.parseInt(amountStr);
            if (amount <= 0) { player.sendMessage(msg.error("Amount must be positive!")); return; }
            double cost = amount * 4.0;
            double powerBoost = amount * 5.0;
            if (plugin.getElixirManager().increaseMaxPower(faction.getFactionId(), cost, powerBoost)) {
                player.sendMessage(msg.success("Increased max power by " + String.format("%.0f", powerBoost) + " for " + String.format("%.0f", cost) + " elixir!"));
            } else {
                player.sendMessage(msg.error("Not enough elixir! Need " + String.format("%.0f", cost)));
            }
        } catch (NumberFormatException e) {
            player.sendMessage(msg.error("Invalid amount!"));
        }
    }

    // ==========================================
    // ELIXIR TRANSFER - /f transfer <target> <amount>
    // Send elixir to another faction
    // ==========================================
    private boolean handleElixirTransfer(Player player, String[] args) {

        if (!requireArgs(player, args, 3, "/f transfer <faction> <amount>")) return true;

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        if (!requireLeader(player, faction, "Only the leader can transfer elixir!")) return true;

        Faction target = plugin.getFactionManager().getFactionByName(args[1]);
        if (target == null) {
            player.sendMessage(msg.error("No faction found with that name!"));
            return true;
        }

        if (target.getFactionId().equals(faction.getFactionId())) {
            player.sendMessage(msg.error("You can't transfer elixir to yourself!"));
            return true;
        }

        try {
            double amount = Double.parseDouble(args[2]);
            if (amount <= 0) { player.sendMessage(msg.error("Amount must be positive!")); return true; }
            if (plugin.getElixirManager().transferElixir(faction.getFactionId(), target.getFactionId(), amount)) {
                player.sendMessage(msg.success("Transferred " + String.format("%.0f", amount) + " elixir to " + target.getName() + "!"));
            } else {
                player.sendMessage(msg.error("Transfer failed! Not enough elixir or transfers are disabled."));
            }
        } catch (NumberFormatException e) {
            player.sendMessage(msg.error("Invalid amount!"));
        }

        return true;
    }

    // ==========================================
    // LOGOUT - /f logout
    // Safe logout with warmup (cancelled on damage)
    // ==========================================
    private boolean handleLogout(Player player) {

        if (!plugin.getCombatManager().isTagged(player.getUniqueId())) {
            player.sendMessage(msg.error("You are not in combat! Use /f logout to safely log out."));
            return true;
        }

        int warmup = plugin.getConfigManager().getCombatLogoutWarmup();
        if (warmup <= 0) {
            player.kick(Component.text("You have safely logged out."));
            return true;
        }

        player.sendMessage(msg.info("Logging out in " + warmup + " seconds... don't move or take damage."));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && plugin.getCombatManager().isTagged(player.getUniqueId())) {
                player.kick(Component.text("You have safely logged out."));
            }
        }, warmup * 20L);

        return true;
    }

    // ==========================================
    // MOTD - /f motd <message>
    // Sets the faction's message of the day
    // ==========================================
    private boolean handleMotd(Player player, String[] args) {

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        if (!requireOfficer(player, faction, "Only the leader and officers can set the faction MOTD!")) return true;

        if (args.length < 2) {
            // Show current MOTD
            if (faction.hasMotd()) {
                player.sendMessage(msg.header("Faction MOTD"));
                player.sendMessage(msg.info(faction.getMotd()));
            } else {
                player.sendMessage(msg.error("No MOTD set. Usage: /f motd <message>"));
            }
            return true;
        }

        // Join all args after "motd" into one string
        StringBuilder motd = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            motd.append(args[i]).append(" ");
        }

        String motdText = motd.toString().trim();
        int maxMotdLength = plugin.getConfigManager().getMaxMotdLength();
        if (motdText.length() > maxMotdLength) {
            player.sendMessage(msg.error("MOTD must be " + maxMotdLength + " characters or less!"));
            return true;
        }

        faction.setMotd(motdText);
        player.sendMessage(msg.success("Faction MOTD has been updated!"));

        return true;
    }

    // ==========================================
    // DESC / DESCRIPTION - /f desc <text>
    // Sets the faction's description
    // ==========================================
    private boolean handleDesc(Player player, String[] args) {

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        if (!requireOfficer(player, faction, "Only the leader and officers can set the faction description!")) return true;

        if (args.length < 2) {
            // Show current description
            if (faction.hasDescription()) {
                player.sendMessage(msg.info("Description: " + faction.getDescription()));
            } else {
                player.sendMessage(msg.error("No description set. Usage: /f desc <text>"));
            }
            return true;
        }

        StringBuilder desc = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            desc.append(args[i]).append(" ");
        }

        String descText = desc.toString().trim();
        int maxDescLength = plugin.getConfigManager().getMaxDescriptionLength();
        if (descText.length() > maxDescLength) {
            player.sendMessage(msg.error("Description must be " + maxDescLength + " characters or less!"));
            return true;
        }

        faction.setDescription(descText);
        player.sendMessage(msg.success("Faction description has been updated!"));

        return true;
    }

    // ==========================================
    // TAG - /f tag <tag>
    // Sets the faction's prefix tag
    // ==========================================
    private boolean handleTag(Player player, String[] args) {

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        if (!requireLeader(player, faction, "Only the faction leader can set the faction tag!")) return true;

        if (args.length < 2) {
            // Show current tag
            if (faction.hasTag()) {
                player.sendMessage(msg.info("Current tag: " + faction.getFormattedTag()));
            } else {
                player.sendMessage(msg.error("No tag set. Usage: /f tag <tag>"));
            }
            return true;
        }

        String tag = args[1];

        int maxTagLength = plugin.getConfigManager().getMaxTagLength();
        if (tag.length() > maxTagLength) {
            player.sendMessage(msg.error("Tag must be " + maxTagLength + " characters or less!"));
            return true;
        }

        faction.setTag(tag);
        player.sendMessage(msg.success("Faction tag set to [" + tag + "]!"));

        return true;
    }

    // ==========================================
    // OPEN - /f open
    // Toggles whether the faction is open for anyone to join
    // ==========================================
    private boolean handleOpen(Player player) {

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        if (!requireLeader(player, faction, "Only the faction leader can toggle open join!")) return true;

        boolean newState = !faction.isOpen();
        faction.setOpen(newState);

        if (newState) {
            player.sendMessage(msg.success("Faction is now open! Anyone can join."));
        } else {
            player.sendMessage(msg.info("Faction is now invite-only."));
        }

        return true;
    }

    // ==========================================
    // PVP - /f pvp
    // Toggles friendly fire within the faction
    // ==========================================
    private boolean handlePvp(Player player) {

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        if (!requireLeader(player, faction, "Only the faction leader can toggle PvP!")) return true;

        boolean newState = !faction.isPvpEnabled();
        faction.setPvpEnabled(newState);

        if (newState) {
            player.sendMessage(msg.warning("Faction PvP enabled! Members can now hurt each other."));
        } else {
            player.sendMessage(msg.success("Faction PvP disabled! Friendly fire is off."));
        }

        return true;
    }

    // ==========================================
    // TNT - /f tnt
    // Toggles TNT damage in faction territory
    // ==========================================
    private boolean handleTnt(Player player) {

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        if (!requireLeader(player, faction, "Only the faction leader can toggle TNT!")) return true;

        boolean newState = !faction.isTntEnabled();
        faction.setTntEnabled(newState);

        if (newState) {
            player.sendMessage(msg.warning("TNT enabled in faction territory!"));
        } else {
            player.sendMessage(msg.success("TNT disabled in faction territory!"));
        }

        return true;
    }

    // ==========================================
    // CHAT - /f chat (alias: /f fc)
    // Toggles faction-only chat mode
    // ==========================================
    private boolean handleChat(Player player) {

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        String current = chatModeMap.get(player.getUniqueId());

        // Toggle faction chat
        if ("faction".equals(current)) {
            chatModeMap.remove(player.getUniqueId());
            player.sendMessage(msg.info("Faction chat disabled. You are now speaking globally."));
        } else {
            chatModeMap.put(player.getUniqueId(), "faction");
            player.sendMessage(msg.success("Faction chat enabled! All messages will go to your faction."));
        }

        return true;
    }

    // ==========================================
    // ALLY CHAT - /f allychat (alias: /f ac)
    // Toggles ally chat mode
    // ==========================================
    private boolean handleAllyChat(Player player) {

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        String current = chatModeMap.get(player.getUniqueId());

        if ("ally".equals(current)) {
            chatModeMap.remove(player.getUniqueId());
            player.sendMessage(msg.info("Ally chat disabled. You are now speaking globally."));
        } else {
            chatModeMap.put(player.getUniqueId(), "ally");
            player.sendMessage(msg.success("Ally chat enabled! Messages go to your faction and allies."));
        }

        return true;
    }

    // ==========================================
    // ALLY - /f ally <faction>
    // Send an ally request to another faction
    // ==========================================
    private boolean handleAlly(Player player, String[] args) {

        if (!requireArgs(player, args, 2, "/f ally <faction>")) return true;

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        if (!requireOfficer(player, faction, "Only leaders and officers can manage alliances!")) return true;

        Faction targetFaction = plugin.getFactionManager().getFactionByName(args[1]);
        if (targetFaction == null) {
            player.sendMessage(msg.error("No faction found with that name!"));
            return true;
        }

        if (targetFaction.getFactionId().equals(faction.getFactionId())) {
            player.sendMessage(msg.error("You can't ally with yourself!"));
            return true;
        }

        if (faction.isAlly(targetFaction.getFactionId())) {
            player.sendMessage(msg.error("You are already allied with " + targetFaction.getName() + "!"));
            return true;
        }

        faction.addAlly(targetFaction.getFactionId());
        targetFaction.addAlly(faction.getFactionId());

        player.sendMessage(msg.success("You are now allied with " + targetFaction.getName() + "!"));
        broadcastToFaction(targetFaction, msg.info(
                plugin.getPlayerNameCache().getPlayerName(player.getUniqueId()) +
                "'s faction has declared an alliance!"
        ));

        return true;
    }

    // ==========================================
    // ENEMY - /f enemy <faction>
    // Declare another faction as an enemy
    // ==========================================
    private boolean handleEnemy(Player player, String[] args) {

        if (!requireArgs(player, args, 2, "/f enemy <faction>")) return true;

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        if (!requireOfficer(player, faction, "Only leaders and officers can manage enemies!")) return true;

        Faction targetFaction = plugin.getFactionManager().getFactionByName(args[1]);
        if (targetFaction == null) {
            player.sendMessage(msg.error("No faction found with that name!"));
            return true;
        }

        if (targetFaction.getFactionId().equals(faction.getFactionId())) {
            player.sendMessage(msg.error("You can't declare war on yourself!"));
            return true;
        }

        if (faction.isEnemy(targetFaction.getFactionId())) {
            player.sendMessage(msg.error("You are already enemies with " + targetFaction.getName() + "!"));
            return true;
        }

        faction.removeAlly(targetFaction.getFactionId());
        targetFaction.removeAlly(faction.getFactionId());

        faction.addEnemy(targetFaction.getFactionId());
        targetFaction.addEnemy(faction.getFactionId());

        player.sendMessage(msg.warning("You have declared " + targetFaction.getName() + " as an enemy!"));
        broadcastToFaction(targetFaction, msg.warning(
                plugin.getPlayerNameCache().getPlayerName(player.getUniqueId()) +
                "'s faction has declared war on you!"
        ));

        return true;
    }

    // ==========================================
    // NEUTRAL - /f neutral <faction>
    // Remove enemy/ally status
    // ==========================================
    private boolean handleNeutral(Player player, String[] args) {

        if (!requireArgs(player, args, 2, "/f neutral <faction>")) return true;

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        if (!requireOfficer(player, faction, "Only leaders and officers can manage relations!")) return true;

        Faction targetFaction = plugin.getFactionManager().getFactionByName(args[1]);
        if (targetFaction == null) {
            player.sendMessage(msg.error("No faction found with that name!"));
            return true;
        }

        faction.removeEnemy(targetFaction.getFactionId());
        faction.removeAlly(targetFaction.getFactionId());
        targetFaction.removeEnemy(faction.getFactionId());
        targetFaction.removeAlly(faction.getFactionId());

        player.sendMessage(msg.info("You are now neutral with " + targetFaction.getName() + "."));

        return true;
    }

    // ==========================================
    // FLY - /f fly
    // Toggle flight in own territory
    // ==========================================
    private boolean handleFly(Player player) {

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        // Check if they're in their own territory
        Chunk chunk = player.getLocation().getChunk();
        UUID ownerId = plugin.getClaimManager().getClaimOwner(chunk);

        if (ownerId == null || !ownerId.equals(faction.getFactionId())) {
            player.sendMessage(msg.error("You can only fly in your own faction's territory!"));
            return true;
        }

        if (player.getAllowFlight()) {
            player.setAllowFlight(false);
            player.setFlying(false);
            player.sendMessage(msg.info("Flight disabled."));
        } else {
            player.setAllowFlight(true);
            player.setFlying(true);
            player.sendMessage(msg.success("Flight enabled in your territory!"));
        }

        return true;
    }

    // ==========================================
    // ADMIN - /f admin <subcommand>
    // Admin commands for server operators
    // ==========================================
    private boolean handleAdmin(Player player, String[] args) {

        if (!player.hasPermission("darkfactions.admin")) {
            player.sendMessage(msg.error("You don't have permission to use admin commands!"));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(msg.header("DarkFactions Admin Commands"));
            player.sendMessage(msg.help("/f admin list", "List all factions with details"));
            player.sendMessage(msg.help("/f admin power <faction> <amount>", "Set faction power"));
            player.sendMessage(msg.help("/f admin elixir <faction> <amount>", "Set faction elixir"));
            player.sendMessage(msg.help("/f admin remove <faction>", "Force remove a faction"));
            player.sendMessage(msg.help("/f admin claim <faction>", "Force claim current chunk for a faction"));
            player.sendMessage(msg.help("/f admin bypass", "Toggle territory bypass mode"));
            return true;
        }

        String adminSub = args[1].toLowerCase();

        switch (adminSub) {
            case "list":
                return handleList(player);

            case "power":
                if (!requireArgs(player, args, 4, "/f admin power <faction> <amount>")) return true;
                Faction powerFaction = plugin.getFactionManager().getFactionByName(args[2]);
                if (powerFaction == null) {
                    player.sendMessage(msg.error("Faction not found!"));
                    return true;
                }
                try {
                    double powerAmount = Double.parseDouble(args[3]);
                    powerFaction.setPower(powerAmount);
                    plugin.getFactionManager().markDirty();
                    player.sendMessage(msg.success("Set " + powerFaction.getName() + "'s power to " + powerAmount));
                } catch (NumberFormatException e) {
                    player.sendMessage(msg.error("Invalid number!"));
                }
                return true;

            case "elixir":
                if (!requireArgs(player, args, 4, "/f admin elixir <faction> <amount>")) return true;
                Faction elixirFaction = plugin.getFactionManager().getFactionByName(args[2]);
                if (elixirFaction == null) {
                    player.sendMessage(msg.error("Faction not found!"));
                    return true;
                }
                try {
                    double elixirAmount = Double.parseDouble(args[3]);
                    elixirFaction.setElixir(elixirAmount);
                    plugin.getFactionManager().markDirty();
                    player.sendMessage(msg.success("Set " + elixirFaction.getName() + "'s elixir to " + elixirAmount));
                } catch (NumberFormatException e) {
                    player.sendMessage(msg.error("Invalid number!"));
                }
                return true;

            case "remove":
                if (!requireArgs(player, args, 3, "/f admin remove <faction>")) return true;
                Faction removeFaction = plugin.getFactionManager().getFactionByName(args[2]);
                if (removeFaction == null) {
                    player.sendMessage(msg.error("Faction not found!"));
                    return true;
                }
                String removedName = removeFaction.getName();

                // deleteFaction() already removes this faction's claims internally.
                plugin.getFactionManager().deleteFaction(removeFaction.getFactionId());
                player.sendMessage(msg.success("Force removed faction: " + removedName));
                return true;

            case "claim":
                if (!requireArgs(player, args, 3, "/f admin claim <faction>")) return true;
                Faction claimFor = plugin.getFactionManager().getFactionByName(args[2]);
                if (claimFor == null) {
                    player.sendMessage(msg.error("Faction not found!"));
                    return true;
                }
                Chunk chunk = player.getLocation().getChunk();
                ClaimResult result = plugin.getClaimManager().claimChunk(chunk, claimFor.getFactionId());
                if (result.isSuccess()) {
                    player.sendMessage(msg.success("Chunk claimed for " + claimFor.getName() + "!"));
                } else {
                    player.sendMessage(msg.error("Could not claim chunk: " + result.getMessage()));
                }
                return true;

            case "bypass":
                boolean bypassing = plugin.getClaimManager().getBypassPlayers().contains(player.getUniqueId());
                if (bypassing) {
                    plugin.getClaimManager().getBypassPlayers().remove(player.getUniqueId());
                    player.sendMessage(msg.info("Bypass mode disabled."));
                } else {
                    plugin.getClaimManager().getBypassPlayers().add(player.getUniqueId());
                    player.sendMessage(msg.warning("Bypass mode enabled! You can interact anywhere."));
                }
                return true;

            default:
                player.sendMessage(msg.error("Unknown admin command!"));
                return true;
        }
    }

    // ==========================================
    // Console handler — allows admin and read-only commands from server console
    // ==========================================
    private boolean handleConsole(CommandSender sender, String subCommand, String[] args) {
        switch (subCommand) {
            case "list":
                List<Faction> factions = plugin.getFactionManager().getAllFactions();
                if (factions.isEmpty()) {
                    sender.sendMessage(msg.error("No factions exist yet."));
                    return true;
                }
                sender.sendMessage(msg.header("Factions (" + factions.size() + ")"));
                for (Faction f : factions) {
                    sender.sendMessage(msg.info(FactionListFormatter.listRow(
                            "", f.getName(), f.getMemberCount(),
                            f.getPower(), plugin.getClaimManager().getClaimCount(f.getFactionId()))));
                }
                return true;

            case "top":
                return handleConsoleTop(sender, args);

            case "reload":
                return handleReloadConsole(sender);

            case "admin":
                if (args.length >= 2) {
                    return handleAdminConsole(sender, args);
                }
                sender.sendMessage(msg.error("Usage: /f admin <subcommand> [args]"));
                return true;

            default:
                sender.sendMessage(msg.error("Only players can use that command."));
                return true;
        }
    }

    private boolean handleConsoleTop(CommandSender sender, String[] args) {
        String sortBy = args.length >= 2 ? args[1].toLowerCase() : "power";
        int limit = plugin.getConfigManager().getLeaderboardDefaultLimit();
        List<Faction> sorted;

        switch (sortBy) {
            case "elixir":
                sorted = plugin.getFactionManager().getTopFactionsByElixir(limit);
                break;
            case "members":
                sorted = plugin.getFactionManager().getTopFactionsByMembers(limit);
                break;
            default:
                sorted = plugin.getFactionManager().getTopFactionsByPower(limit);
                break;
        }

        if (sorted.isEmpty()) {
            sender.sendMessage(msg.error("No factions to show!"));
            return true;
        }

        int rank = 1;
        for (Faction f : sorted) {
            sender.sendMessage(msg.info(FactionListFormatter.rankRow(
                    rank, "", f.getName(), FactionListFormatter.metric(f.getPower(), 1, "power"))));
            rank++;
        }
        return true;
    }

    private boolean handleAdminConsole(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(msg.error("Usage: /f admin <list|power|elixir|remove> [args]"));
            return true;
        }
        String adminSub = args[1].toLowerCase();
        switch (adminSub) {
            case "list":
                return handleListConsole(sender);
            case "power":
                if (args.length < 4) {
                    sender.sendMessage(msg.error("Usage: /f admin power <faction> <amount>"));
                    return true;
                }
                Faction powerFaction = plugin.getFactionManager().getFactionByName(args[2]);
                if (powerFaction == null) {
                    sender.sendMessage(msg.error("Faction not found!"));
                    return true;
                }
                try {
                    double amount = Double.parseDouble(args[3]);
                    powerFaction.setPower(amount);
                    plugin.getFactionManager().markDirty();
                    sender.sendMessage(msg.success("Set " + powerFaction.getName() + "'s power to " + amount));
                } catch (NumberFormatException e) {
                    sender.sendMessage(msg.error("Invalid number!"));
                }
                return true;
            case "elixir":
                if (args.length < 4) {
                    sender.sendMessage(msg.error("Usage: /f admin elixir <faction> <amount>"));
                    return true;
                }
                Faction elixirFaction = plugin.getFactionManager().getFactionByName(args[2]);
                if (elixirFaction == null) {
                    sender.sendMessage(msg.error("Faction not found!"));
                    return true;
                }
                try {
                    double amount = Double.parseDouble(args[3]);
                    elixirFaction.setElixir(amount);
                    plugin.getFactionManager().markDirty();
                    sender.sendMessage(msg.success("Set " + elixirFaction.getName() + "'s elixir to " + amount));
                } catch (NumberFormatException e) {
                    sender.sendMessage(msg.error("Invalid number!"));
                }
                return true;
            case "remove":
                Faction removeFaction = plugin.getFactionManager().getFactionByName(args[2]);
                if (removeFaction == null) {
                    sender.sendMessage(msg.error("Faction not found!"));
                    return true;
                }
                String removedName = removeFaction.getName();
                plugin.getClaimManager().removeAllFactionClaims(removeFaction.getFactionId());
                plugin.getFactionManager().deleteFaction(removeFaction.getFactionId());
                sender.sendMessage(msg.success("Force removed faction: " + removedName));
                return true;
            default:
                sender.sendMessage(msg.error("Unknown admin subcommand. Usage: /f admin <list|power|elixir|remove> [args]"));
                return true;
        }
    }

    private boolean handleListConsole(CommandSender sender) {
        List<Faction> factions = plugin.getFactionManager().getAllFactions();
        if (factions.isEmpty()) {
            sender.sendMessage(msg.error("No factions exist yet."));
            return true;
        }
        sender.sendMessage(msg.header("Factions (" + factions.size() + ")"));
        for (Faction f : factions) {
            sender.sendMessage(msg.info(FactionListFormatter.listRow(
                    "", f.getName(), f.getMemberCount(),
                    f.getPower(), plugin.getClaimManager().getClaimCount(f.getFactionId()))));
        }
        return true;
    }

    // ==========================================
    // Chat Mode Methods - called by the listener
    // ==========================================

    // Check if a player is in faction chat mode
    public String getChatMode(UUID playerUuid) {
        return chatModeMap.get(playerUuid);
    }

    // Clear chat mode for a player (e.g. when kicked/leaves)
    public void clearChatMode(UUID playerUuid) {
        chatModeMap.remove(playerUuid);
    }

    // Check if a player has auto-claim enabled
    public boolean isAutoClaiming(UUID playerUuid) {
        return autoClaimMap.getOrDefault(playerUuid, false);
    }

    // ==========================================
    // RELOAD - /f reload
    // Reloads ALL config values from config.yml
    // ==========================================
    private boolean handleReload(Player player) {

        if (!player.hasPermission("darkfactions.admin")) {
            player.sendMessage(msg.error("You don't have permission to reload the config!"));
            return true;
        }

        reloadAllConfigs();
        player.sendMessage(msg.success("DarkFactions config reloaded!"));

        return true;
    }

    private boolean handleReloadConsole(CommandSender sender) {
        reloadAllConfigs();
        sender.sendMessage(msg.success("DarkFactions config reloaded!"));
        return true;
    }

    private void reloadAllConfigs() {
        plugin.getConfigManager().reload();
        plugin.getPowerManager().reloadConfig();
        plugin.getElixirManager().reloadConfig();
        plugin.getClaimManager().reloadConfig();
    }

    // ==========================================
    // Helper Methods
    // ==========================================

    // Return the player's faction, or null after telling them they're not in one.
    // Callers do: Faction f = requireFaction(player); if (f == null) return true;
    private Faction requireFaction(Player player) {
        Faction faction = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage(msg.error("You're not in a faction!"));
        }
        return faction;
    }

    // Ensure at least `min` arguments were given, else show the usage string.
    // Returns true when the args are sufficient.
    private boolean requireArgs(Player player, String[] args, int min, String usage) {
        if (args.length < min) {
            player.sendMessage(msg.error("Usage: " + usage));
            return false;
        }
        return true;
    }

    // Require the player to be the faction leader, else send `denial`.
    // Callers do: if (!requireLeader(player, faction, "...")) return true;
    private boolean requireLeader(Player player, Faction faction, String denial) {
        if (!faction.isLeader(player.getUniqueId())) {
            player.sendMessage(msg.error(denial));
            return false;
        }
        return true;
    }

    // Require the player to be the leader or an officer, else send `denial`.
    // Callers do: if (!requireOfficer(player, faction, "...")) return true;
    private boolean requireOfficer(Player player, Faction faction, String denial) {
        if (!faction.isLeaderOrOfficer(player.getUniqueId())) {
            player.sendMessage(msg.error(denial));
            return false;
        }
        return true;
    }

    // Find a player's UUID by name within a faction
    private UUID findPlayerUuidByName(String name, Faction faction) {
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
    private void broadcastToFaction(Faction faction, Component message) {
        for (UUID memberUuid : faction.getMembers()) {
            Player member = Bukkit.getPlayer(memberUuid);
            if (member != null && member.isOnline()) {
                member.sendMessage(message);
            }
        }
    }
}