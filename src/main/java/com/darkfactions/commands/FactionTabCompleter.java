package com.darkfactions.commands;

// ==========================================
// FactionTabCompleter.java
// Provides tab completion for /f commands
// Makes it easier for players to use the plugin
// ==========================================

import com.darkfactions.DarkFactions;
import com.darkfactions.models.Faction;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class FactionTabCompleter implements TabCompleter {

    private final DarkFactions plugin;

    public FactionTabCompleter(DarkFactions plugin) {
        this.plugin = plugin;
    }

    // List of all subcommands for tab completion
    private static final List<String> SUBCOMMANDS = Arrays.asList(
        // Basic commands
        "create", "disband", "invite", "add", "uninvite", "revoke",
        "accept", "deny", "invites",
        "kick", "leave", "promote", "demote", "leader", "rename",
        // Home
        "sethome", "home",
        // Info
        "who", "info", "list", "show", "top",
        // Claiming
        "claim", "unclaim", "unclaimall", "map", "autoclaim",
        // Stats
        "power", "elixir", "elixirbal", "bal",
        // Customization
        "motd", "desc", "description", "tag", "open", "pvp", "tnt",
        // Chat
        "chat", "fc", "allychat", "ac",
        // Relations
        "ally", "enemy", "neutral",
        // Other
        "fly", "logout",
        // Shop
        "shop", "transfer",
        // Admin
        "admin", "reload"
    );

    // Admin subcommands
    private static final List<String> ADMIN_SUBCOMMANDS = Arrays.asList(
        "list", "power", "elixir", "remove", "claim", "bypass"
    );

    // Top sorting options
    private static final List<String> TOP_OPTIONS = Arrays.asList(
        "power", "elixir", "members", "land"
    );

    // Shop items
    private static final List<String> SHOP_OPTIONS = Arrays.asList(
        "power", "maxpower"
    );

    // ==========================================
    // Called when a player presses TAB
    // ==========================================
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        if (!(sender instanceof Player)) {
            return new ArrayList<>();
        }

        Player player = (Player) sender;

        // First argument - suggest subcommands
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return SUBCOMMANDS.stream()
                    .filter(cmd -> cmd.startsWith(partial))
                    .collect(Collectors.toList());
        }

        // Second argument - depends on the subcommand
        if (args.length == 2) {
            String subCmd = args[0].toLowerCase();

            if (subCmd.equals("create") || subCmd.equals("rename")) {
                return Arrays.asList("<name>");
            }

            if (subCmd.equals("invite") || subCmd.equals("add") ||
                subCmd.equals("uninvite") || subCmd.equals("revoke") ||
                subCmd.equals("kick")) {
                // Filter by what the player has typed so far; unlike the static
                // placeholder branches, these are real names worth narrowing down.
                String partial = args[1].toLowerCase();
                return player.getServer().getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(partial))
                        .collect(Collectors.toList());
            }

            if (subCmd.equals("who") || subCmd.equals("info")) {
                String partial = args[1].toLowerCase();
                List<String> suggestions = new ArrayList<>();
                for (Player online : player.getServer().getOnlinePlayers()) {
                    if (online.getName().toLowerCase().startsWith(partial)) {
                        suggestions.add(online.getName());
                    }
                }
                suggestions.addAll(factionNameSuggestions(args[1]));
                return suggestions;
            }

            if (subCmd.equals("show") ||
                subCmd.equals("enemy") || subCmd.equals("neutral") ||
                subCmd.equals("accept") || subCmd.equals("deny") ||
                subCmd.equals("disband")) {
                return factionNameSuggestions(args[1]);
            }

            if (subCmd.equals("ally")) {
                String partial = args[1].toLowerCase();
                List<String> suggestions = new ArrayList<>();
                for (String opt : Arrays.asList("accept", "deny")) {
                    if (opt.startsWith(partial)) {
                        suggestions.add(opt);
                    }
                }
                suggestions.addAll(factionNameSuggestions(args[1]));
                return suggestions;
            }

            if (subCmd.equals("top")) {
                return TOP_OPTIONS;
            }

            if (subCmd.equals("map")) {
                return Arrays.asList("<radius>");
            }

            if (subCmd.equals("motd") || subCmd.equals("desc") || subCmd.equals("description")) {
                return Arrays.asList("<message>");
            }

            if (subCmd.equals("tag")) {
                return Arrays.asList("<tag>");
            }

            if (subCmd.equals("admin")) {
                return ADMIN_SUBCOMMANDS;
            }

            if (subCmd.equals("elixirbal") || subCmd.equals("bal")) {
                return factionNameSuggestions(args[1]);
            }

            if (subCmd.equals("shop")) {
                return SHOP_OPTIONS;
            }

            if (subCmd.equals("transfer")) {
                return factionNameSuggestions(args[1]);
            }
        }

        // Third argument
        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("admin")) {
                String adminSub = args[1].toLowerCase();
                // All four admin subcommands take a faction name as their next arg.
                if (adminSub.equals("power") || adminSub.equals("elixir") ||
                    adminSub.equals("remove") || adminSub.equals("claim")) {
                    return factionNameSuggestions(args[2]);
                }
            }

            if (args[0].equalsIgnoreCase("shop") || args[0].equalsIgnoreCase("transfer")) {
                return Arrays.asList("<amount>");
            }

            if (args[0].equalsIgnoreCase("ally")) {
                String action = args[1].toLowerCase();
                if (action.equals("accept") || action.equals("deny")) {
                    return factionNameSuggestions(args[2]);
                }
            }

            if (args[0].equalsIgnoreCase("top")) {
                return Arrays.asList("<limit>");
            }
        }

        // Fourth argument
        if (args.length == 4) {
            if (args[0].equalsIgnoreCase("admin")) {
                if (args[1].equalsIgnoreCase("power") || args[1].equalsIgnoreCase("elixir")) {
                    return Arrays.asList("<amount>");
                }
            }
        }

        return new ArrayList<>();
    }

    private List<String> factionNameSuggestions(String partial) {
        String lower = partial.toLowerCase();
        return plugin.getFactionManager().getAllFactions().stream()
                .map(Faction::getName)
                .filter(name -> name.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }
}