package com.darkfactions.listeners;

// ==========================================
// FactionListener.java
// Listens for game events and handles faction-related actions
// Territory protection, chat, deaths, joining/leaving
// ==========================================

import com.darkfactions.DarkFactions;
import com.darkfactions.commands.FactionCommand;
import com.darkfactions.models.Faction;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class FactionListener implements Listener {

    // Reference to main plugin
    private final DarkFactions plugin;

    // Track last known chunk for each player (to detect border crossing)
    private final Map<UUID, String> playerLastChunk;

    // ==========================================
    // Constructor
    // ==========================================
    public FactionListener(DarkFactions plugin) {
        this.plugin = plugin;
        this.playerLastChunk = new HashMap<>();
    }

    // ==========================================
    // TERRITORY PROTECTION - Block Break
    // Prevents players from breaking blocks in enemy territory
    // ==========================================
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        // Check if the location is claimed
        UUID ownerId = plugin.getClaimManager().getLocationOwner(block.getLocation());
        if (ownerId == null) {
            return; // Wilderness - anything goes
        }

        // Check admin bypass
        if (plugin.getClaimManager().getBypassPlayers().contains(player.getUniqueId())) {
            return; // Admin can break anywhere
        }

        Faction ownerFaction = plugin.getFactionManager().getFaction(ownerId);
        if (ownerFaction == null) {
            return;
        }

        Faction playerFaction = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());

        // If the player owns this territory, allow it
        if (playerFaction != null && playerFaction.getFactionId().equals(ownerId)) {
            return;
        }

        // Allies can break in allied territory
        if (playerFaction != null && ownerFaction.isAlly(playerFaction.getFactionId())) {
            return;
        }

        // Deny!
        event.setCancelled(true);
        player.sendMessage(plugin.getMessageUtils().error(
                "You cannot break blocks in " + ownerFaction.getName() + "'s territory!"
        ));
    }

    // ==========================================
    // TERRITORY PROTECTION - Block Place
    // ==========================================
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        UUID ownerId = plugin.getClaimManager().getLocationOwner(block.getLocation());
        if (ownerId == null) {
            return;
        }

        if (plugin.getClaimManager().getBypassPlayers().contains(player.getUniqueId())) {
            return;
        }

        Faction ownerFaction = plugin.getFactionManager().getFaction(ownerId);
        if (ownerFaction == null) {
            return;
        }

        Faction playerFaction = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());

        if (playerFaction != null && playerFaction.getFactionId().equals(ownerId)) {
            return;
        }

        // Allies can place in allied territory
        if (playerFaction != null && ownerFaction.isAlly(playerFaction.getFactionId())) {
            return;
        }

        event.setCancelled(true);
        player.sendMessage(plugin.getMessageUtils().error(
                "You cannot place blocks in " + ownerFaction.getName() + "'s territory!"
        ));
    }

    // ==========================================
    // TERRITORY PROTECTION - Chest Interaction
    // Protects chests, furnaces, doors, etc.
    // ==========================================
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only block interactions (not air)
        if (event.getClickedBlock() == null) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();

        // Only protect interactive blocks (chests, doors, buttons, etc.)
        if (!isProtectedBlock(block.getType())) {
            return;
        }

        UUID ownerId = plugin.getClaimManager().getLocationOwner(block.getLocation());
        if (ownerId == null) {
            return;
        }

        if (plugin.getClaimManager().getBypassPlayers().contains(player.getUniqueId())) {
            return;
        }

        Faction ownerFaction = plugin.getFactionManager().getFaction(ownerId);
        if (ownerFaction == null) {
            return;
        }

        Faction playerFaction = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());

        if (playerFaction != null && playerFaction.getFactionId().equals(ownerId)) {
            return;
        }

        // Allies can use allied territory blocks
        if (playerFaction != null && ownerFaction.isAlly(playerFaction.getFactionId())) {
            return;
        }

        event.setCancelled(true);
        player.sendMessage(plugin.getMessageUtils().error(
                "You cannot use that in " + ownerFaction.getName() + "'s territory!"
        ));
    }

    // Check if a block type should be protected
    private boolean isProtectedBlock(Material material) {
        return switch (material.name()) {
            case "CHEST", "TRAPPED_CHEST", "ENDER_CHEST",
                 "FURNACE", "BLAST_FURNACE", "SMOKER",
                 "BREWING_STAND", "ENCHANTING_TABLE",
                 "ANVIL", "GRINDSTONE", "STONECUTTER",
                 "CRAFTING_TABLE", "BARREL",
                 "SHULKER_BOX",
                 "OAK_DOOR", "SPRUCE_DOOR", "BIRCH_DOOR", "JUNGLE_DOOR",
                 "ACACIA_DOOR", "DARK_OAK_DOOR", "IRON_DOOR",
                 "OAK_FENCE_GATE", "SPRUCE_FENCE_GATE", "BIRCH_FENCE_GATE",
                 "JUNGLE_FENCE_GATE", "ACACIA_FENCE_GATE", "DARK_OAK_FENCE_GATE",
                 "LEVER", "STONE_BUTTON", "OAK_BUTTON", "SPRUCE_BUTTON",
                 "BIRCH_BUTTON", "JUNGLE_BUTTON", "ACACIA_BUTTON", "DARK_OAK_BUTTON",
                 "REPEATER", "COMPARATOR", "DAYLIGHT_DETECTOR",
                 "HOPPER", "DROPPER", "DISPENSER",
                 "BEACON", "RESPAWN_ANCHOR",
                 "LOOM", "CARTOGRAPHY_TABLE", "FLETCHING_TABLE",
                 "SMITHING_TABLE", "LECTERN", "COMPOSTER" -> true;
            default -> false;
        };
    }

    // ==========================================
    // PVP Protection - Faction vs Faction
    // Handles friendly fire toggle and territory PvP
    // ==========================================
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }

        Faction victimFaction = plugin.getFactionManager().getPlayerFaction(victim.getUniqueId());
        Faction attackerFaction = plugin.getFactionManager().getPlayerFaction(attacker.getUniqueId());

        // If neither player is in a faction, allow the hit
        if (victimFaction == null && attackerFaction == null) {
            return;
        }

        // Same faction check
        if (victimFaction != null && attackerFaction != null &&
            victimFaction.getFactionId().equals(attackerFaction.getFactionId())) {

            // Check if faction PvP is enabled (respect config toggle)
            if (plugin.getConfigManager().isRespectFactionPvpToggle() && !victimFaction.isPvpEnabled()) {
                event.setCancelled(true);
                attacker.sendMessage(plugin.getMessageUtils().error("Faction PvP is disabled!"));
                return;
            }
        }

        // Ally check - allies should not be able to hurt each other
        if (victimFaction != null && attackerFaction != null &&
            victimFaction.isAlly(attackerFaction.getFactionId())) {
            event.setCancelled(true);
            attacker.sendMessage(plugin.getMessageUtils().error("You cannot hurt your allies!"));
            return;
        }

        // Check territory protection - if victim is in their own territory, attacker cant hurt them
        // unless attacker is an enemy
        Chunk chunk = victim.getLocation().getChunk();
        UUID chunkOwner = plugin.getClaimManager().getClaimOwner(chunk);

        if (chunkOwner != null && victimFaction != null &&
            chunkOwner.equals(victimFaction.getFactionId())) {

            // Player is in their own territory - check if attacker is an enemy
            if (attackerFaction != null && !victimFaction.isEnemy(attackerFaction.getFactionId())) {
                // Allow PvP if both are in faction but not enemies in own territory - still allowed
                // This is standard factions behavior - but allow config to control
                return;
            }
        }
    }

    // ==========================================
    // TNT Protection - Entity Explode
    // Respects faction TNT toggle
    // ==========================================
    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        // Check each block in the explosion
        event.blockList().removeIf(block -> {
            UUID ownerId = plugin.getClaimManager().getLocationOwner(block.getLocation());
            if (ownerId == null) {
                return false; // Allow in wilderness
            }

            Faction ownerFaction = plugin.getFactionManager().getFaction(ownerId);
            if (ownerFaction != null && !ownerFaction.isTntEnabled()) {
                return true; // TNT is disabled in this territory
            }

            return false;
        });
    }

    // ==========================================
    // PLAYER MOVE - Territory Border Detection & AutoClaim
    // Shows entry/exit messages when crossing borders
    // Handles auto-claiming chunks
    // ==========================================
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Only check on block changes, not just looking around
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Chunk toChunk = event.getTo().getChunk();
        String toKey = toChunk.getWorld().getName() + ":" + toChunk.getX() + ":" + toChunk.getZ();

        String lastKey = playerLastChunk.get(player.getUniqueId());

        // If the chunk hasnt changed, do nothing
        if (toKey.equals(lastKey)) {
            return;
        }

        // Update last known chunk
        playerLastChunk.put(player.getUniqueId(), toKey);

        // ==========================================
        // Territory border message
        // ==========================================
        UUID newOwnerId = plugin.getClaimManager().getClaimOwner(toChunk);

        // Get old chunk owner from last key
        UUID oldOwnerId = null;
        if (lastKey != null) {
            oldOwnerId = plugin.getClaimManager().getOwnerByKey(lastKey);
        }

        // Only show messages if the owner actually changed
        if (newOwnerId != null && !newOwnerId.equals(oldOwnerId)) {
            Faction newFaction = plugin.getFactionManager().getFaction(newOwnerId);

            if (newFaction != null) {
                Faction playerFaction = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());

                // Check if border messages are enabled in config
                if (plugin.getConfigManager().isTerritoryMessagesEnabled()) {
                    String msg;
                    if (playerFaction != null && playerFaction.getFactionId().equals(newOwnerId)) {
                        msg = plugin.getConfigManager().getTerritoryEnterOwn()
                                .replace("{faction}", newFaction.getName())
                                .replace("{leader}", plugin.getPlayerNameCache().getPlayerName(newFaction.getLeaderUuid()))
                                .replace("{members}", String.valueOf(newFaction.getMemberCount()))
                                .replace("{power}", String.format("%.1f", newFaction.getPower()))
                                .replace("{elixir}", String.format("%.0f", newFaction.getElixir()));
                        player.sendMessage(msg);
                    } else if (playerFaction != null && newFaction.isAlly(playerFaction.getFactionId())) {
                        msg = plugin.getConfigManager().getTerritoryEnterAlly()
                                .replace("{faction}", newFaction.getName())
                                .replace("{leader}", plugin.getPlayerNameCache().getPlayerName(newFaction.getLeaderUuid()))
                                .replace("{members}", String.valueOf(newFaction.getMemberCount()))
                                .replace("{power}", String.format("%.1f", newFaction.getPower()))
                                .replace("{elixir}", String.format("%.0f", newFaction.getElixir()));
                        player.sendMessage(msg);
                    } else {
                        msg = plugin.getConfigManager().getTerritoryEnterEnemy()
                                .replace("{faction}", newFaction.getName())
                                .replace("{leader}", plugin.getPlayerNameCache().getPlayerName(newFaction.getLeaderUuid()))
                                .replace("{members}", String.valueOf(newFaction.getMemberCount()))
                                .replace("{power}", String.format("%.1f", newFaction.getPower()))
                                .replace("{elixir}", String.format("%.0f", newFaction.getElixir()));
                        player.sendMessage(msg);
                    }
                }
            }
        }

        // Leaving territory message
        if (oldOwnerId != null && newOwnerId == null && plugin.getConfigManager().isTerritoryMessagesEnabled()) {
            Faction oldFaction = plugin.getFactionManager().getFaction(oldOwnerId);
            if (oldFaction != null) {
                String msg = plugin.getConfigManager().getTerritoryExit()
                        .replace("{faction}", oldFaction.getName())
                        .replace("{leader}", plugin.getPlayerNameCache().getPlayerName(oldFaction.getLeaderUuid()))
                        .replace("{members}", String.valueOf(oldFaction.getMemberCount()))
                        .replace("{power}", String.format("%.1f", oldFaction.getPower()))
                        .replace("{elixir}", String.format("%.0f", oldFaction.getElixir()));
                player.sendMessage(msg);
            }
        }

        // ==========================================
        // Auto-claim check
        // ==========================================
        if (newOwnerId == null) {
            // This chunk is wilderness - check if player has auto-claim on
            FactionCommand cmd = (FactionCommand) plugin.getCommand("faction").getExecutor();
            if (cmd != null && cmd.isAutoClaiming(player.getUniqueId())) {
                Faction playerFaction = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());
                if (playerFaction != null) {
                    String result = plugin.getClaimManager().claimChunk(toChunk, playerFaction.getFactionId());
                    if (result.equals("success")) {
                        player.sendMessage(plugin.getMessageUtils().success("Auto-claimed this chunk!"));
                    }
                }
            }
        }

        // ==========================================
        // Flight check - disable flight when leaving own territory
        // ==========================================
        if (player.getAllowFlight() && plugin.getConfigManager().isFlightAutoDisableOnExit()) {
            Faction playerFaction = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());
            if (playerFaction == null || newOwnerId == null ||
                !newOwnerId.equals(playerFaction.getFactionId())) {
                // Not in own territory - disable flight
                player.setAllowFlight(false);
                player.setFlying(false);
                if (plugin.getConfigManager().isFlightNotifyOnExit()) {
                    player.sendMessage(plugin.getMessageUtils().error("Flight disabled outside your territory!"));
                }
            }
        }
    }

    // ==========================================
    // PLAYER JOIN
    // Updates name cache, shows MOTD, handles login time
    // ==========================================
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        // Update the name cache
        plugin.getPlayerNameCache().updateName(player);

        // Update login time for power regen
        plugin.getPowerManager().getPlayerData(playerUuid).setLastLoginTime(System.currentTimeMillis());

        // Give daily elixir bonus
        plugin.getElixirManager().onPlayerLogin(playerUuid);

        // Show faction MOTD if they're in a faction
        Faction faction = plugin.getFactionManager().getPlayerFaction(playerUuid);
        if (faction != null && faction.hasMotd()) {
            // Small delay so the MOTD shows after join messages
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                player.sendMessage(plugin.getMessageUtils().header("Faction MOTD"));
                player.sendMessage(plugin.getMessageUtils().info(faction.getMotd()));
            }, 20L); // 1 second delay
        }

        plugin.getLogger().info(player.getName() + " joined - name cached, power data loaded.");
    }

    // ==========================================
    // PLAYER QUIT
    // Saves logout time for power decay calc
    // ==========================================
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        plugin.getPowerManager().getPlayerData(playerUuid).setLastLogoutTime(System.currentTimeMillis());

        // Clean up tracking data
        playerLastChunk.remove(playerUuid);
    }

    // ==========================================
    // PLAYER DEATH
    // Handles power loss, elixir gains, kill tracking
    // ==========================================
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        UUID victimUuid = victim.getUniqueId();

        plugin.getPowerManager().onPlayerDeath(victimUuid);

        if (victim.getKiller() != null) {
            Player killer = victim.getKiller();
            UUID killerUuid = killer.getUniqueId();

            plugin.getPowerManager().onPlayerKill(killerUuid);

            // Check faction vs faction combat
            Faction victimFaction = plugin.getFactionManager().getPlayerFaction(victimUuid);
            Faction killerFaction = plugin.getFactionManager().getPlayerFaction(killerUuid);

            if (victimFaction != null && killerFaction != null) {
                if (victimFaction.isEnemy(killerFaction.getFactionId())) {
                    plugin.getElixirManager().onEnemyKill(killerFaction.getFactionId());
                    killer.sendMessage(plugin.getMessageUtils().success(
                            "Enemy kill! Elixir earned for " + killerFaction.getName()
                    ));
                }
            }
        }
    }

    // ==========================================
    // PLAYER CHAT - Faction Chat / Ally Chat
    // Intercepts chat messages and routes them
    // ==========================================
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
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

        String message = event.getMessage();
        String prefix = plugin.getMessageUtils().getChatPrefix(); // Get formatted prefix

        if ("faction".equals(chatMode)) {
            // Send only to faction members using config format
            String format = plugin.getConfigManager().getFactionChatFormat();
            format = format.replace("{prefix}", plugin.getMessageUtils().getChatPrefix());
            format = format.replace("{tag}", faction.getFormattedTag());
            format = format.replace("{player}", player.getName());
            format = format.replace("{faction}", faction.getName());
            format = format.replace("{message}", message);
            // Translate color codes
            format = format.replace('&', '\u00A7');

            for (UUID memberUuid : faction.getMembers()) {
                Player member = player.getServer().getPlayer(memberUuid);
                if (member != null && member.isOnline()) {
                    member.sendMessage(format);
                }
            }

            // Log if enabled
            if (plugin.getConfigManager().isLogChatToConsole()) {
                plugin.getLogger().info("[FACTION CHAT] " + faction.getName() + " - " + player.getName() + ": " + message);
            }

        } else if ("ally".equals(chatMode)) {
            // Send to faction members AND allies using config format
            String format = plugin.getConfigManager().getAllyChatFormat();
            format = format.replace("{prefix}", plugin.getMessageUtils().getChatPrefix());
            format = format.replace("{tag}", faction.getFormattedTag());
            format = format.replace("{player}", player.getName());
            format = format.replace("{faction}", faction.getName());
            format = format.replace("{message}", message);
            format = format.replace('&', '\u00A7');

            // Send to own faction
            for (UUID memberUuid : faction.getMembers()) {
                Player member = player.getServer().getPlayer(memberUuid);
                if (member != null && member.isOnline()) {
                    member.sendMessage(format);
                }
            }

            // Send to allies
            for (UUID allyId : faction.getAllies()) {
                Faction allyFaction = plugin.getFactionManager().getFaction(allyId);
                if (allyFaction != null) {
                    for (UUID memberUuid : allyFaction.getMembers()) {
                        Player member = player.getServer().getPlayer(memberUuid);
                        if (member != null && member.isOnline()) {
                            member.sendMessage(format);
                        }
                    }
                }
            }

            if (plugin.getConfigManager().isLogChatToConsole()) {
                plugin.getLogger().info("[ALLY CHAT] " + faction.getName() + " - " + player.getName() + ": " + message);
            }
        }
    }
}