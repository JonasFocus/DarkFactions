package com.darkfactions.listeners;

// ==========================================
// FactionListener.java
// Listens for game events and handles faction-related actions
// Territory protection, chat, deaths, joining/leaving
// ==========================================

import com.darkfactions.DarkFactions;
import com.darkfactions.commands.FactionCommand;
import com.darkfactions.managers.ClaimResult;
import com.darkfactions.models.Faction;
import com.darkfactions.utils.ChatFormatter;
import com.darkfactions.utils.TerritoryMessageFormatter;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.HashMap;
import java.util.Map;
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
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Faction denier = protectionDenier(event.getPlayer(), event.getBlock());
        if (denier != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.getMessageUtils().error(
                    "You cannot break blocks in " + denier.getName() + "'s territory!"
            ));
        }
    }

    // ==========================================
    // TERRITORY PROTECTION - Block Place
    // ==========================================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        Faction denier = protectionDenier(event.getPlayer(), event.getBlock());
        if (denier != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.getMessageUtils().error(
                    "You cannot place blocks in " + denier.getName() + "'s territory!"
            ));
        }
    }

    // ==========================================
    // TERRITORY PROTECTION - Chest Interaction
    // Protects chests, furnaces, doors, etc.
    // ==========================================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only block interactions (not air)
        if (event.getClickedBlock() == null) {
            return;
        }

        Block block = event.getClickedBlock();

        // Only protect interactive blocks (chests, doors, buttons, etc.)
        if (!isProtectedBlock(block.getType())) {
            return;
        }

        Faction denier = protectionDenier(event.getPlayer(), block);
        if (denier != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.getMessageUtils().error(
                    "You cannot use that in " + denier.getName() + "'s territory!"
            ));
        }
    }

    // ==========================================
    // Shared territory-protection check.
    // Returns the owning faction whose claim should block this player's action,
    // or null when the action is allowed (wilderness, admin bypass, own land,
    // or allied land).
    // ==========================================
    private Faction protectionDenier(Player player, Block block) {
        UUID ownerId = plugin.getClaimManager().getLocationOwner(block.getLocation());
        if (ownerId == null) {
            return null; // Wilderness - anything goes
        }

        if (plugin.getClaimManager().getBypassPlayers().contains(player.getUniqueId())) {
            return null; // Admin bypass
        }

        Faction ownerFaction = plugin.getFactionManager().getFaction(ownerId);
        if (ownerFaction == null) {
            return null;
        }

        Faction playerFaction = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());
        if (playerFaction == null) {
            return ownerFaction;
        }

        // Allow the owning faction and its allies.
        if (playerFaction.getFactionId().equals(ownerId) || ownerFaction.isAlly(playerFaction.getFactionId())) {
            return null;
        }

        return ownerFaction;
    }

    // ==========================================
    // Expand a territory-message template with a faction's details.
    // ==========================================
    private String formatTerritoryMessage(String template, Faction faction) {
        return TerritoryMessageFormatter.format(
                template,
                faction.getName(),
                plugin.getPlayerNameCache().getPlayerName(faction.getLeaderUuid()),
                faction.getMemberCount(),
                faction.getPower(),
                faction.getElixir());
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
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null) {
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

        // Territory check: enforce PvP config per territory type
        Chunk chunk = victim.getLocation().getChunk();
        UUID chunkOwner = plugin.getClaimManager().getClaimOwner(chunk);

        if (chunkOwner == null) {
            // Wilderness — respect wilderness PvP toggle
            if (!plugin.getConfigManager().isWildernessPvp()) {
                event.setCancelled(true);
                return;
            }
        } else if (victimFaction != null && chunkOwner.equals(victimFaction.getFactionId())) {
            // Victim is in their own territory
            if (!plugin.getConfigManager().isOwnTerritoryPvp()) {
                event.setCancelled(true);
                return;
            }
            // Allow non-enemies within own territory (standard factions behavior)
            if (attackerFaction != null && !victimFaction.isEnemy(attackerFaction.getFactionId())) {
                return;
            }
        } else if (victimFaction != null && victimFaction.isAlly(chunkOwner)) {
            // Victim is in allied territory
            if (!plugin.getConfigManager().isAllyTerritoryPvp()) {
                event.setCancelled(true);
                return;
            }
        } else if (chunkOwner != null) {
            // Victim is in enemy/unaffiliated territory
            if (!plugin.getConfigManager().isEnemyTerritoryPvp()) {
                event.setCancelled(true);
                return;
            }
        }

        // Combat tag both players
        plugin.getCombatManager().tag(victim.getUniqueId());
        plugin.getCombatManager().tag(attacker.getUniqueId());

        // Cancel any pending home teleport for the victim
        plugin.getFactionCommand().cancelWarmup(victim.getUniqueId(), true);
    }

    // Resolves the actual attacking Player for a damage source: either the
    // damager itself, or whoever fired it if the damager is a projectile
    // (arrow, trident, thrown potion, etc.) — without this, ranged PvP
    // bypasses every faction/territory protection below.
    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    // ==========================================
    // TNT Protection - Entity Explode
    // Respects faction TNT toggle
    // ==========================================
    @EventHandler(priority = EventPriority.HIGHEST)
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
    // Block Explode Protection - Beds / Respawn Anchors
    // Catches explosions that bypass EntityExplodeEvent
    // ==========================================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> {
            UUID ownerId = plugin.getClaimManager().getLocationOwner(block.getLocation());
            if (ownerId == null) return false;

            Faction ownerFaction = plugin.getFactionManager().getFaction(ownerId);
            return ownerFaction != null && !ownerFaction.isTntEnabled();
        });
    }

    // ==========================================
    // Piston Protection - Prevent pushing blocks out of claims
    // ==========================================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            Block target = block.getRelative(event.getDirection());
            UUID sourceOwner = plugin.getClaimManager().getLocationOwner(block.getLocation());
            UUID targetOwner = plugin.getClaimManager().getLocationOwner(target.getLocation());
            if (sourceOwner == null || !sourceOwner.equals(targetOwner)) {
                if (targetOwner != null) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            Block target = block.getRelative(event.getDirection());
            UUID sourceOwner = plugin.getClaimManager().getLocationOwner(block.getLocation());
            UUID targetOwner = plugin.getClaimManager().getLocationOwner(target.getLocation());
            if (sourceOwner == null || !sourceOwner.equals(targetOwner)) {
                if (targetOwner != null) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    // ==========================================
    // PLAYER MOVE - Territory Border Detection & AutoClaim
    // Shows entry/exit messages when crossing borders
    // Handles auto-claiming chunks
    // ==========================================
    // Player move — use NORMAL priority since this handler doesn't cancel events;
    // HIGHEST is reserved for the cancellation handlers above.
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
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

        // ==========================================
        // Auto-claim check
        // ==========================================
        if (newOwnerId == null) {
            // This chunk is wilderness - check if player has auto-claim on
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
        if (player.getAllowFlight() && plugin.getConfigManager().isFlightAutoDisableOnExit()) {
            // Disable flight if combat tagged and config says so
            if (plugin.getConfigManager().isCombatTagPreventFly() && plugin.getCombatManager().isTagged(player.getUniqueId())) {
                player.setAllowFlight(false);
                player.setFlying(false);
                player.sendMessage(plugin.getMessageUtils().error("Flight disabled during combat!"));
            }
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

        // Decay power for the time spent offline (based on the stored logout
        // time) before stamping the new login time, so power.offline-decay-*
        // actually takes effect. No-op when offline decay is disabled.
        plugin.getPowerManager().handleOfflineDecay(playerUuid);

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

        // Cancel any pending home-teleport warmup so it doesn't fire against
        // a disconnected player.
        plugin.getFactionCommand().cancelWarmup(playerUuid, false);

        // Combat tag check — punish combat loggers
        if (plugin.getCombatManager().handleQuit(playerUuid)) {
            if (plugin.getConfigManager().isCombatTagKillOnQuit()) {
                // PlayerQuitEvent runs while the connection is already closing,
                // so setHealth(0) here frequently doesn't propagate through the
                // normal PlayerDeathEvent pipeline. Apply the power-loss penalty
                // directly instead of relying on that event to fire.
                plugin.getPowerManager().onPlayerDeath(playerUuid);
                player.setHealth(0);
                plugin.getLogger().warning(player.getName() + " combat-logged and was killed.");
            }
        }

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

        Player killer = victim.getKiller();

        // Environmental / mob death: apply the PvE power loss (a no-op when
        // power.loss-on-pve-death is 0) instead of the heavier PvP penalty.
        if (killer == null) {
            plugin.getPowerManager().onPlayerPveDeath(victimUuid);
            return;
        }

        // PvP death: victim takes the PvP power loss, killer gains power.
        // Ignore self-inflicted kills (e.g. damage potions, explosions)
        if (killer.equals(victim)) {
            plugin.getPowerManager().onPlayerDeath(victimUuid);
            plugin.getCombatManager().clear(victimUuid);
            return;
        }
        plugin.getPowerManager().onPlayerDeath(victimUuid);

        UUID killerUuid = killer.getUniqueId();
        plugin.getPowerManager().onPlayerKill(killerUuid);

        // Clear combat tags
        plugin.getCombatManager().clear(victimUuid);
        plugin.getCombatManager().clear(killerUuid);

        // Check faction vs faction combat
        Faction victimFaction = plugin.getFactionManager().getPlayerFaction(victimUuid);
        Faction killerFaction = plugin.getFactionManager().getPlayerFaction(killerUuid);

        if (victimFaction != null && killerFaction != null
                && victimFaction.isEnemy(killerFaction.getFactionId())) {
            plugin.getElixirManager().onEnemyKill(killerFaction.getFactionId());
            killer.sendMessage(plugin.getMessageUtils().success(
                    "Enemy kill! Elixir earned for " + killerFaction.getName()
            ));
        }
    }

    // ==========================================
    // PLAYER CHAT - Faction Chat / Ally Chat
    // Intercepts chat messages and routes them
    // ==========================================
    @EventHandler
    public void onPlayerChat(AsyncChatEvent event) {
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

        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        String prefix = LegacyComponentSerializer.legacySection()
                .serialize(plugin.getMessageUtils().getChatPrefix()); // Get formatted prefix

        boolean allyMode = "ally".equals(chatMode);
        String template = allyMode
                ? plugin.getConfigManager().getAllyChatFormat()
                : plugin.getConfigManager().getFactionChatFormat();

        Component rendered = LegacyComponentSerializer.legacySection().deserialize(
                ChatFormatter.format(template, prefix, faction.getFormattedTag(),
                        player.getName(), faction.getName(), message));

        // Always reaches the speaker's own faction; ally mode also fans out to allies.
        broadcastToMembers(faction, rendered);
        if (allyMode) {
            for (UUID allyId : faction.getAllies()) {
                Faction allyFaction = plugin.getFactionManager().getFaction(allyId);
                if (allyFaction != null) {
                    broadcastToMembers(allyFaction, rendered);
                }
            }
        }

        if (plugin.getConfigManager().isLogChatToConsole()) {
            String label = allyMode ? "ALLY CHAT" : "FACTION CHAT";
            plugin.getLogger().info("[" + label + "] " + faction.getName() + " - " + player.getName() + ": " + message);
        }
    }

    // Send a rendered component to every online member of a faction.
    private void broadcastToMembers(Faction faction, Component message) {
        for (UUID memberUuid : faction.getMembers()) {
            Player member = plugin.getServer().getPlayer(memberUuid);
            if (member != null && member.isOnline()) {
                member.sendMessage(message);
            }
        }
    }
}