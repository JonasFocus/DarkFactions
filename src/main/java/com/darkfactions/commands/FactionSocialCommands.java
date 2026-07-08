package com.darkfactions.commands;

// Handles customization, chat, and relation subcommands: motd, desc, tag, open,
// pvp, tnt, chat, allychat, ally, enemy, neutral.

import com.darkfactions.DarkFactions;
import com.darkfactions.models.Faction;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FactionSocialCommands extends AbstractFactionSubcommand {

    // Track players who are in faction chat mode
    private final Map<UUID, String> chatModeMap; // "faction" or "ally" or null

    static final class ChatMode {
        static final String FACTION = "faction";
        static final String ALLY = "ally";
        private ChatMode() {}
    }

    public FactionSocialCommands(DarkFactions plugin) {
        super(plugin);
        this.chatModeMap = new ConcurrentHashMap<>();
    }

    // ==========================================
    // MOTD - /f motd <message>
    // Sets the faction's message of the day
    // ==========================================
    boolean handleMotd(Player player, String[] args) {

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
    boolean handleDesc(Player player, String[] args) {

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
    boolean handleTag(Player player, String[] args) {

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

        double tagCost = plugin.getConfigManager().getElixirSetTagCost();
        if (tagCost > 0 && !faction.removeElixir(tagCost)) {
            player.sendMessage(msg.error("Your faction needs " + String.format("%.0f", tagCost)
                    + " elixir to set a tag!"));
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
    boolean handleOpen(Player player) {

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
    boolean handlePvp(Player player) {

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
    boolean handleTnt(Player player) {

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
    boolean handleChat(Player player) {

        if (!plugin.getConfigManager().isFactionChatEnabled()) {
            player.sendMessage(msg.error("Faction chat is disabled on this server!"));
            return true;
        }

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
    boolean handleAllyChat(Player player) {

        if (!plugin.getConfigManager().isAllyChatEnabled()) {
            player.sendMessage(msg.error("Ally chat is disabled on this server!"));
            return true;
        }

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

    // Check if a player is in faction chat mode
    public String getChatMode(UUID playerUuid) {
        return chatModeMap.get(playerUuid);
    }

    // Clear chat mode for a player (e.g. when kicked/leaves)
    public void clearChatMode(UUID playerUuid) {
        chatModeMap.remove(playerUuid);
    }

    // ==========================================
    // ALLY - /f ally <faction> | /f ally accept|deny <faction>
    // Send or respond to an ally request
    // ==========================================
    boolean handleAlly(Player player, String[] args) {

        if (!requireArgs(player, args, 2, "/f ally <faction> | /f ally accept|deny <faction>")) return true;

        if (!player.hasPermission("darkfactions.ally")) {
            player.sendMessage(msg.error("You don't have permission to manage alliances!"));
            return true;
        }

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        if (!requireOfficer(player, faction, "Only leaders and officers can manage alliances!")) return true;

        String action = args[1].toLowerCase();
        if (action.equals("accept") || action.equals("deny")) {
            if (!requireArgs(player, args, 3, "/f ally " + action + " <faction>")) return true;
            Faction requesting = plugin.getFactionManager().getFactionByName(args[2]);
            if (requesting == null) {
                player.sendMessage(msg.error("No faction found with that name!"));
                return true;
            }
            if (action.equals("accept")) {
                return handleAllyAccept(player, faction, requesting);
            }
            return handleAllyDeny(player, faction, requesting);
        }

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

        if (plugin.getFactionManager().hasPendingAllyRequest(faction.getFactionId(), targetFaction.getFactionId())) {
            player.sendMessage(msg.error("You already have a pending ally request to " + targetFaction.getName() + "!"));
            return true;
        }

        // If they already sent us a request, accepting is cleaner than a second outbound request
        if (plugin.getFactionManager().hasPendingAllyRequest(targetFaction.getFactionId(), faction.getFactionId())) {
            return handleAllyAccept(player, faction, targetFaction);
        }

        plugin.getFactionManager().sendAllyRequest(faction.getFactionId(), targetFaction.getFactionId());
        player.sendMessage(msg.success("Ally request sent to " + targetFaction.getName() + "!"));

        String requesterName = plugin.getPlayerNameCache().getPlayerName(player.getUniqueId());
        for (UUID memberUuid : targetFaction.getMembers()) {
            if (!targetFaction.isLeader(memberUuid) && !targetFaction.isOfficer(memberUuid)) {
                continue;
            }
            Player officer = org.bukkit.Bukkit.getPlayer(memberUuid);
            if (officer != null && officer.isOnline()) {
                officer.sendMessage(msg.info(requesterName + "'s faction (" + faction.getName()
                        + ") wants to ally! Use /f ally accept " + faction.getName()));
            }
        }

        return true;
    }

    private boolean handleAllyAccept(Player player, Faction faction, Faction requesting) {
        if (!plugin.getFactionManager().acceptAllyRequest(faction.getFactionId(), requesting.getFactionId())) {
            player.sendMessage(msg.error("No pending ally request from " + requesting.getName() + "!"));
            return true;
        }

        player.sendMessage(msg.success("You are now allied with " + requesting.getName() + "!"));
        broadcastToFaction(faction, msg.info("Your faction is now allied with " + requesting.getName() + "!"));
        broadcastToFaction(requesting, msg.info(faction.getName() + " accepted your ally request!"));
        return true;
    }

    private boolean handleAllyDeny(Player player, Faction faction, Faction requesting) {
        if (!plugin.getFactionManager().denyAllyRequest(faction.getFactionId(), requesting.getFactionId())) {
            player.sendMessage(msg.error("No pending ally request from " + requesting.getName() + "!"));
            return true;
        }

        player.sendMessage(msg.info("Denied ally request from " + requesting.getName() + "."));
        broadcastToFaction(requesting, msg.info(faction.getName() + " denied your ally request."));
        return true;
    }

    // ==========================================
    // ENEMY - /f enemy <faction>
    // Declare another faction as an enemy
    // ==========================================
    boolean handleEnemy(Player player, String[] args) {

        if (!requireArgs(player, args, 2, "/f enemy <faction>")) return true;

        // Same node as /f ally: plugin.yml describes darkfactions.ally as
        // managing alliances, which covers all relation changes.
        if (!player.hasPermission("darkfactions.ally")) {
            player.sendMessage(msg.error("You don't have permission to manage relations!"));
            return true;
        }

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
    boolean handleNeutral(Player player, String[] args) {

        if (!requireArgs(player, args, 2, "/f neutral <faction>")) return true;

        if (!player.hasPermission("darkfactions.ally")) {
            player.sendMessage(msg.error("You don't have permission to manage relations!"));
            return true;
        }

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
}
