package com.darkfactions.listeners;

// ==========================================
// TerritoryMovementHandler.java
// Tracks per-player chunk crossings to show territory border messages, drive
// auto-claim, and enforce the own-territory-only flight rule.
// ==========================================

import com.darkfactions.DarkFactions;
import com.darkfactions.commands.FactionCommand;
import com.darkfactions.managers.ClaimResult;
import com.darkfactions.models.Faction;
import com.darkfactions.utils.TerritoryMessageFormatter;

import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TerritoryMovementHandler {

    private final DarkFactions plugin;

    // Track last known chunk for each player (to detect border crossing)
    private final Map<UUID, String> playerLastChunk;

    public TerritoryMovementHandler(DarkFactions plugin) {
        this.plugin = plugin;
        this.playerLastChunk = new HashMap<>();
    }

    public void handle(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Only check on block changes, not just looking around
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        // Cancel home warmup on move
        if (event.getFrom().getBlockX() != event.getTo().getBlockX() || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            plugin.getFactionCommand().cancelWarmup(player.getUniqueId(), false);
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

        UUID newOwnerId = plugin.getClaimManager().getClaimOwner(toChunk);

        // Get old chunk owner from last key
        UUID oldOwnerId = lastKey != null ? plugin.getClaimManager().getOwnerByKey(lastKey) : null;

        announceBorderCrossing(player, newOwnerId, oldOwnerId);
        handleAutoClaim(player, toChunk, newOwnerId);
        handleFlight(player, newOwnerId);
    }

    // Call on player quit to stop tracking their last known chunk.
    public void clear(UUID playerUuid) {
        playerLastChunk.remove(playerUuid);
    }

    // ==========================================
    // Territory border message
    // ==========================================
    private void announceBorderCrossing(Player player, UUID newOwnerId, UUID oldOwnerId) {
        // Only show messages if the owner actually changed
        if (newOwnerId != null && !newOwnerId.equals(oldOwnerId)
                && plugin.getConfigManager().isTerritoryMessagesEnabled()) {
            Faction newFaction = plugin.getFactionManager().getFaction(newOwnerId);

            if (newFaction != null) {
                Faction playerFaction = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());

                String template;
                if (playerFaction != null && playerFaction.getFactionId().equals(newOwnerId)) {
                    template = plugin.getConfigManager().getTerritoryEnterOwn();
                } else if (playerFaction != null && newFaction.isAlly(playerFaction.getFactionId())) {
                    template = plugin.getConfigManager().getTerritoryEnterAlly();
                } else {
                    template = plugin.getConfigManager().getTerritoryEnterEnemy();
                }
                player.sendMessage(formatTerritoryMessage(template, newFaction));
            }
        }

        // Leaving territory message
        if (oldOwnerId != null && newOwnerId == null && plugin.getConfigManager().isTerritoryMessagesEnabled()) {
            Faction oldFaction = plugin.getFactionManager().getFaction(oldOwnerId);
            if (oldFaction != null) {
                player.sendMessage(formatTerritoryMessage(plugin.getConfigManager().getTerritoryExit(), oldFaction));
            }
        }
    }

    // ==========================================
    // Auto-claim check
    // ==========================================
    private void handleAutoClaim(Player player, Chunk toChunk, UUID newOwnerId) {
        if (newOwnerId != null) {
            return; // Already claimed - nothing to auto-claim
        }
        FactionCommand cmd = plugin.getFactionCommand();
        if (cmd != null && cmd.isAutoClaiming(player.getUniqueId())) {
            Faction playerFaction = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());
            if (playerFaction != null) {
                ClaimResult result = plugin.getClaimManager().claimChunk(toChunk, playerFaction.getFactionId());
                if (result.isSuccess()) {
                    player.sendMessage(plugin.getMessageUtils().success("Auto-claimed this chunk!"));
                }
            }
        }
    }

    // ==========================================
    // Flight check - disable flight when leaving own territory
    // ==========================================
    private void handleFlight(Player player, UUID newOwnerId) {
        if (!player.getAllowFlight()
                || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        // Disable flight if combat tagged and config says so
        if (plugin.getConfigManager().isCombatTagPreventFly() && plugin.getCombatManager().isTagged(player.getUniqueId())) {
            player.setAllowFlight(false);
            player.setFlying(false);
            player.sendMessage(plugin.getMessageUtils().error("Flight disabled during combat!"));
        } else if (plugin.getConfigManager().isFlightAutoDisableOnExit()
                && plugin.getConfigManager().isFlightOwnTerritoryOnly()) {
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

    // Expand a territory-message template with a faction's details.
    private String formatTerritoryMessage(String template, Faction faction) {
        return TerritoryMessageFormatter.format(
                template,
                faction.getName(),
                plugin.getPlayerNameCache().getPlayerName(faction.getLeaderUuid()),
                faction.getMemberCount(),
                plugin.getPowerManager().getEffectiveFactionPower(faction.getFactionId()),
                faction.getElixir());
    }
}
