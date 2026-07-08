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
import java.util.function.ObjDoubleConsumer;

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

        // "list" renders with the player's own faction tag; "claim" and "bypass"
        // need a Player for location/permission state. Everything else is
        // shared with the console admin path in handleAdminCommand.
        switch (adminSub) {
            case "list":
                return infoCommands.handleList(player);
            case "claim":
                return handleClaim(player, args);
            case "bypass":
                return handleBypass(player);
            default:
                return handleAdminCommand(player, args);
        }
    }

    private boolean handleClaim(Player player, String[] args) {
        if (!requireArgs(player, args, 3, "/f admin claim <faction>")) return true;
        Faction claimFor = requireFactionByName(player, args[2]);
        if (claimFor == null) return true;
        Chunk chunk = player.getLocation().getChunk();
        ClaimResult result = plugin.getClaimManager().claimChunk(chunk, claimFor.getFactionId());
        if (result.isSuccess()) {
            player.sendMessage(msg.success("Chunk claimed for " + claimFor.getName() + "!"));
            logAdmin(player.getName() + " force-claimed chunk for " + claimFor.getName());
        } else {
            player.sendMessage(msg.error("Could not claim chunk: " + result.getMessage()));
        }
        return true;
    }

    private boolean handleBypass(Player player) {
        boolean bypassing = plugin.getClaimManager().getBypassPlayers().contains(player.getUniqueId());
        if (bypassing) {
            plugin.getClaimManager().getBypassPlayers().remove(player.getUniqueId());
            player.sendMessage(msg.info("Bypass mode disabled."));
            logAdmin(player.getName() + " disabled bypass mode");
        } else {
            plugin.getClaimManager().getBypassPlayers().add(player.getUniqueId());
            player.sendMessage(msg.warning("Bypass mode enabled! You can interact anywhere."));
            logAdmin(player.getName() + " enabled bypass mode");
        }
        return true;
    }

    // ==========================================
    // Console handler — allows admin and read-only commands from server console
    // ==========================================
    boolean handleConsole(CommandSender sender, String subCommand, String[] args) {
        switch (subCommand) {
            case "list":
                return handleListCommand(sender);

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
        String sortBy = "power";
        int limit = plugin.getConfigManager().getLeaderboardDefaultLimit();
        int maxLimit = plugin.getConfigManager().getLeaderboardMaxLimit();

        if (args.length >= 2) {
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
                sender.sendMessage(msg.error("Invalid limit!"));
                return true;
            }
        }
        if (limit < 1) limit = 1;
        if (limit > maxLimit) limit = maxLimit;

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

    // /f admin <subcommand> from console. Needs at least the subcommand itself;
    // each subcommand below validates its own remaining arguments.
    boolean handleAdminConsole(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(msg.error("Usage: /f admin <list|power|elixir|remove> [args]"));
            return true;
        }
        return handleAdminCommand(sender, args);
    }

    // Shared power/elixir/remove/list handling for both the player (/f admin)
    // and console (/f admin, /f admin <sub>) entry points.
    private boolean handleAdminCommand(CommandSender sender, String[] args) {
        String adminSub = args[1].toLowerCase();

        switch (adminSub) {
            case "list":
                return handleListCommand(sender);

            case "power":
                if (!requireArgs(sender, args, 4, "/f admin power <faction> <amount>")) return true;
                return setFactionAmount(sender, args, "bonus power", Faction::setBonusPower);

            case "elixir":
                if (!requireArgs(sender, args, 4, "/f admin elixir <faction> <amount>")) return true;
                return setFactionAmount(sender, args, "elixir", Faction::setElixir);

            case "remove":
                if (!requireArgs(sender, args, 3, "/f admin remove <faction>")) return true;
                return handleRemove(sender, args);

            default:
                sender.sendMessage(msg.error("Unknown admin command!"));
                return true;
        }
    }

    private boolean setFactionAmount(CommandSender sender, String[] args, String label, ObjDoubleConsumer<Faction> setter) {
        Faction faction = requireFactionByName(sender, args[2]);
        if (faction == null) return true;
        try {
            double amount = Double.parseDouble(args[3]);
            setter.accept(faction, amount);
            plugin.getFactionManager().markDirty();
            sender.sendMessage(msg.success("Set " + faction.getName() + "'s " + label + " to " + amount));
            logAdmin(sender.getName() + " set " + faction.getName() + "'s " + label + " to " + amount);
        } catch (NumberFormatException e) {
            sender.sendMessage(msg.error("Invalid number!"));
        }
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        Faction removeFaction = requireFactionByName(sender, args[2]);
        if (removeFaction == null) return true;
        String removedName = removeFaction.getName();

        // deleteFaction() already removes this faction's claims internally.
        plugin.getFactionManager().deleteFaction(removeFaction.getFactionId());
        sender.sendMessage(msg.success("Force removed faction: " + removedName));
        logAdmin(sender.getName() + " force-removed faction " + removedName);
        return true;
    }

    private void logAdmin(String action) {
        if (plugin.getConfigManager().isLogAdminActions()) {
            plugin.getLogger().info("[Admin] " + action);
        }
    }

    private boolean handleListCommand(CommandSender sender) {
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
