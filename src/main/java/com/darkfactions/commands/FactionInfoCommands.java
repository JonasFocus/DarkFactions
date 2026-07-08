package com.darkfactions.commands;

// Handles read-only info subcommands: help, who/info, list, show, top.

import com.darkfactions.DarkFactions;
import com.darkfactions.models.Faction;
import com.darkfactions.utils.FactionListFormatter;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class FactionInfoCommands extends AbstractFactionSubcommand {

    public FactionInfoCommands(DarkFactions plugin) {
        super(plugin);
    }

    // ==========================================
    // HELP - Shows all available commands
    // ==========================================
    void sendHelp(Player player) {
        player.sendMessage(msg.header("DarkFactions Commands"));
        player.sendMessage(msg.help("/f create <name>", "Create a new faction"));
        player.sendMessage(msg.help("/f disband [name]", "Disband your faction"));
        player.sendMessage(msg.help("/f invite <player>", "Invite a player to your faction"));
        player.sendMessage(msg.help("/f uninvite <player>", "Revoke a pending invite"));
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
        player.sendMessage(msg.help("/f top [power|elixir|members|land] [limit]", "Show faction leaderboard"));
        player.sendMessage(msg.help("/f power", "Show your faction's power"));
        player.sendMessage(msg.help("/f elixir", "Show your faction's elixir"));
        player.sendMessage(msg.help("/f bal [faction]", "Show a faction's elixir balance"));
        player.sendMessage(msg.help("/f shop", "Spend elixir on faction upgrades"));
        player.sendMessage(msg.help("/f transfer <faction> <amount>", "Send elixir to another faction"));
        player.sendMessage(msg.help("/f motd <message>", "Set faction message of the day"));
        player.sendMessage(msg.help("/f desc <text>", "Set faction description"));
        player.sendMessage(msg.help("/f tag <tag>", "Set faction prefix tag"));
        player.sendMessage(msg.help("/f open", "Toggle open join"));
        player.sendMessage(msg.help("/f pvp", "Toggle faction PvP"));
        player.sendMessage(msg.help("/f tnt", "Toggle TNT in territory"));
        player.sendMessage(msg.help("/f chat", "Toggle faction-only chat"));
        player.sendMessage(msg.help("/f allychat", "Toggle ally chat"));
        player.sendMessage(msg.help("/f ally <faction>", "Send ally request"));
        player.sendMessage(msg.help("/f ally accept|deny <faction>", "Respond to ally request"));
        player.sendMessage(msg.help("/f enemy <faction>", "Declare enemy"));
        player.sendMessage(msg.help("/f neutral <faction>", "Set neutral"));
        player.sendMessage(msg.help("/f fly", "Toggle flight in own territory"));
        player.sendMessage(msg.help("/f logout", "Safely log out while combat tagged"));
        if (player.hasPermission("darkfactions.admin")) {
            player.sendMessage(msg.help("/f admin", "Admin faction management"));
            player.sendMessage(msg.help("/f reload", "Reload the plugin config"));
        }
    }

    // ==========================================
    // WHO / INFO - /f who [player]
    // Shows information about a player's faction
    // ==========================================
    boolean handleInfo(Player player, String[] args) {

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
            if (args.length < 2) {
                player.sendMessage(msg.error("You are not in a faction!"));
            } else {
                player.sendMessage(msg.error("That player is not in a faction!"));
            }
            return true;
        }

        player.sendMessage(msg.header("Faction: " + faction.getFormattedTag() + faction.getName()));
        player.sendMessage(msg.info("Leader: " + plugin.getPlayerNameCache().getPlayerName(faction.getLeaderUuid())));
        player.sendMessage(msg.info("Members: " + faction.getMemberCount()));
        player.sendMessage(msg.info("Power: " + String.format("%.1f", effectivePower(faction))));
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
    boolean handleList(Player player) {

        List<Faction> factions = plugin.getFactionManager().getAllFactions();

        if (factions.isEmpty()) {
            player.sendMessage(msg.error("No factions exist yet! Create one with /f create <name>"));
            return true;
        }

        player.sendMessage(msg.header("Factions (" + factions.size() + ")"));

        for (Faction faction : factions) {
            player.sendMessage(msg.info(FactionListFormatter.listRow(
                    faction.getFormattedTag(), faction.getName(), faction.getMemberCount(),
                    effectivePower(faction), plugin.getClaimManager().getClaimCount(faction.getFactionId()))));
        }

        return true;
    }

    // ==========================================
    // SHOW - /f show <faction>
    // Shows info about a specific faction by name
    // ==========================================
    boolean handleShow(Player player, String[] args) {

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

        player.sendMessage(msg.info("Power: " + String.format("%.1f", effectivePower(faction)) +
                "/" + String.format("%.1f", effectiveMaxPower(faction))));
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
    boolean handleTop(Player player, String[] args) {

        String sortBy = "power"; // Default sort by power
        int limit = plugin.getConfigManager().getLeaderboardDefaultLimit();
        int maxLimit = plugin.getConfigManager().getLeaderboardMaxLimit();

        if (args.length >= 2) {
            // args[1] may be a sort key or a numeric limit
            if (args[1].matches("\\d+")) {
                try {
                    limit = Integer.parseInt(args[1]);
                } catch (NumberFormatException ignored) {
                    // keep default
                }
            } else {
                sortBy = args[1].toLowerCase();
            }
        }
        if (args.length >= 3) {
            try {
                limit = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                player.sendMessage(msg.error("Invalid limit!"));
                return true;
            }
        }
        if (limit < 1) limit = 1;
        if (limit > maxLimit) limit = maxLimit;

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
                default -> FactionListFormatter.metric(effectivePower(faction), 1, "power");
            };
            player.sendMessage(msg.info(FactionListFormatter.rankRow(
                    rank, faction.getFormattedTag(), faction.getName(), value)));
            rank++;
        }

        return true;
    }
}
