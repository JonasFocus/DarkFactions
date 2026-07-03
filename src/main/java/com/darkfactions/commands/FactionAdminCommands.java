package com.darkfactions.commands;

// Handles admin, console, and reload subcommands.

import com.darkfactions.DarkFactions;
import com.darkfactions.managers.ClaimResult;
import com.darkfactions.models.Faction;
import com.darkfactions.utils.FactionListFormatter;

import org.bukkit.Chunk;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class FactionAdminCommands extends AbstractFactionSubcommand {

    private final FactionInfoCommands infoCommands;

    public FactionAdminCommands(DarkFactions plugin, FactionInfoCommands infoCommands) {
        super(plugin);
        this.infoCommands = infoCommands;
    }

    // ==========================================
    // ADMIN - /f admin <subcommand>
    // Admin commands for server operators
    // ==========================================
    boolean handleAdmin(Player player, String[] args) {

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
                return infoCommands.handleList(player);

            case "power":
                if (!requireArgs(player, args, 4, "/f admin power <faction> <amount>")) return true;
                Faction powerFaction = plugin.getFactionManager().getFactionByName(args[2]);
                if (powerFaction == null) {
                    player.sendMessage(msg.error("Faction not found!"));
                    return true;
                }
                try {
                    double powerAmount = Double.parseDouble(args[3]);
                    powerFaction.setBonusPower(powerAmount);
                    plugin.getFactionManager().markDirty();
                    player.sendMessage(msg.success("Set " + powerFaction.getName() + "'s bonus power to " + powerAmount));
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
    boolean handleConsole(CommandSender sender, String subCommand, String[] args) {
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
                            effectivePower(f), plugin.getClaimManager().getClaimCount(f.getFactionId()))));
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

    boolean handleConsoleTop(CommandSender sender, String[] args) {
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
                    rank, "", f.getName(), FactionListFormatter.metric(effectivePower(f), 1, "power"))));
            rank++;
        }
        return true;
    }

    boolean handleAdminConsole(CommandSender sender, String[] args) {
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
                    powerFaction.setBonusPower(amount);
                    plugin.getFactionManager().markDirty();
                    sender.sendMessage(msg.success("Set " + powerFaction.getName() + "'s bonus power to " + amount));
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

    boolean handleListConsole(CommandSender sender) {
        List<Faction> factions = plugin.getFactionManager().getAllFactions();
        if (factions.isEmpty()) {
            sender.sendMessage(msg.error("No factions exist yet."));
            return true;
        }
        sender.sendMessage(msg.header("Factions (" + factions.size() + ")"));
        for (Faction f : factions) {
            sender.sendMessage(msg.info(FactionListFormatter.listRow(
                    "",                     f.getName(), f.getMemberCount(),
                    effectivePower(f), plugin.getClaimManager().getClaimCount(f.getFactionId()))));
        }
        return true;
    }

    // ==========================================
    // RELOAD - /f reload
    // Reloads ALL config values from config.yml
    // ==========================================
    boolean handleReload(Player player) {

        if (!player.hasPermission("darkfactions.admin")) {
            player.sendMessage(msg.error("You don't have permission to reload the config!"));
            return true;
        }

        reloadAllConfigs();
        player.sendMessage(msg.success("DarkFactions config reloaded!"));

        return true;
    }

    boolean handleReloadConsole(CommandSender sender) {
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
}
