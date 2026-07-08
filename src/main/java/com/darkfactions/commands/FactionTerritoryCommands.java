package com.darkfactions.commands;

// Handles land/territory subcommands: home, claim, map, autoclaim, fly, logout.

import com.darkfactions.DarkFactions;
import com.darkfactions.managers.ClaimResult;
import com.darkfactions.models.Faction;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FactionTerritoryCommands extends AbstractFactionSubcommand {

    // Track players who have auto-claim enabled
    private final Map<UUID, Boolean> autoClaimMap;

    // Cooldowns for /f home to prevent spam
    private final Map<UUID, Long> homeCooldowns;

    // Pending home teleport warmups — tracked by BukkitTask so they can be cancelled
    // on movement or damage.
    private final Map<UUID, BukkitTask> pendingWarmups;

    // Pending /f logout warmups — cancelled on movement or re-tag (damage).
    private final Map<UUID, BukkitTask> pendingLogoutWarmups;

    public FactionTerritoryCommands(DarkFactions plugin) {
        super(plugin);
        this.autoClaimMap = new ConcurrentHashMap<>();
        this.homeCooldowns = new ConcurrentHashMap<>();
        this.pendingWarmups = new ConcurrentHashMap<>();
        this.pendingLogoutWarmups = new ConcurrentHashMap<>();
    }

    // ==========================================
    // SETHOME - /f sethome
    // Sets the faction home at the player's location
    // ==========================================
    boolean handleSetHome(Player player) {

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        if (!requireOfficerOrMemberPermitted(player, faction, plugin.getConfigManager().isMembersCanSetHome(),
                "Only the leader and officers can set the faction home!")) return true;

        Chunk chunk = player.getLocation().getChunk();
        UUID ownerId = plugin.getClaimManager().getClaimOwner(chunk);
        if (ownerId == null || !ownerId.equals(faction.getFactionId())) {
            player.sendMessage(msg.error("You can only set home in your faction's claimed land!"));
            return true;
        }

        plugin.getFactionManager().setFactionHome(faction.getFactionId(), player.getLocation());
        player.sendMessage(msg.success("Faction home has been set!"));

        return true;
    }

    // ==========================================
    // HOME - /f home
    // Teleports the player to the faction home with warmup
    // ==========================================
    boolean handleHome(Player player) {

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

    // Cancel a pending home warmup for a player. Called from the listener on move/damage.
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

    // Cancel a pending /f logout warmup. Called from the listener on move/damage.
    public void cancelLogoutWarmup(UUID playerUuid, boolean dueToDamage) {
        BukkitTask task = pendingLogoutWarmups.remove(playerUuid);
        if (task != null) {
            task.cancel();
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                if (dueToDamage) {
                    player.sendMessage(msg.error("Safe logout cancelled - you took damage!"));
                } else {
                    player.sendMessage(msg.error("Safe logout cancelled - you moved!"));
                }
            }
        }
    }

    // ==========================================
    // CLAIM - /f claim
    // Claims the chunk the player is standing in
    // ==========================================
    boolean handleClaim(Player player) {

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        if (!requireOfficerOrMemberPermitted(player, faction, plugin.getConfigManager().isMembersCanClaim(),
                "Only the leader and officers can claim land!")) return true;

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
    boolean handleUnclaim(Player player) {

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        if (!requireOfficerOrMemberPermitted(player, faction, plugin.getConfigManager().isMembersCanClaim(),
                "Only the leader and officers can unclaim land!")) return true;

        Chunk chunk = player.getLocation().getChunk();
        UUID ownerId = plugin.getClaimManager().getClaimOwner(chunk);

        if (ownerId == null) {
            player.sendMessage(msg.error("This chunk is not claimed!"));
            return true;
        }

        if (!ownerId.equals(faction.getFactionId())) {
            if (!plugin.getConfigManager().isCanUnclaimEnemy()) {
                player.sendMessage(msg.error("This chunk belongs to another faction!"));
                return true;
            }

            Faction ownerFaction = plugin.getFactionManager().getFaction(ownerId);
            if (ownerFaction == null) {
                player.sendMessage(msg.error("This chunk belongs to another faction!"));
                return true;
            }

            if (!faction.isEnemy(ownerId)) {
                player.sendMessage(msg.error("You can only raid-unclaim land from enemy factions!"));
                return true;
            }

            if (!plugin.getPowerManager().isFactionRaidable(ownerId)) {
                player.sendMessage(msg.error("That faction is not raidable yet!"));
                return true;
            }

            plugin.getClaimManager().unclaimChunk(chunk);
            plugin.getPowerManager().onRaidWin(faction.getFactionId());
            plugin.getElixirManager().onSuccessfulRaid(faction.getFactionId(), ownerId);
            player.sendMessage(msg.success("Raid unclaim! You took land from " + ownerFaction.getName() + "!"));
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
    boolean handleUnclaimAll(Player player) {

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
    boolean handleMap(Player player, String[] args) {

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
    boolean handleAutoClaim(Player player) {

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        if (!requireOfficerOrMemberPermitted(player, faction, plugin.getConfigManager().isMembersCanClaim(),
                "Only the leader and officers can use auto-claim!")) return true;

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

    // Check if a player has auto-claim enabled
    public boolean isAutoClaiming(UUID playerUuid) {
        return autoClaimMap.getOrDefault(playerUuid, false);
    }

    // ==========================================
    // FLY - /f fly
    // Toggle flight in own territory
    // ==========================================
    boolean handleFly(Player player) {

        if (!plugin.getConfigManager().isFlightEnabled()) {
            player.sendMessage(msg.error("Faction flight is disabled on this server!"));
            return true;
        }

        Faction faction = requireFaction(player);
        if (faction == null) return true;

        if (plugin.getConfigManager().isCombatTagPreventFly()
                && plugin.getCombatManager().isTagged(player.getUniqueId())) {
            player.sendMessage(msg.error("You cannot enable flight during combat!"));
            return true;
        }

        // Check if they're in their own territory (unless config allows flight anywhere)
        if (plugin.getConfigManager().isFlightOwnTerritoryOnly()) {
            Chunk chunk = player.getLocation().getChunk();
            UUID ownerId = plugin.getClaimManager().getClaimOwner(chunk);

            if (ownerId == null || !ownerId.equals(faction.getFactionId())) {
                player.sendMessage(msg.error("You can only fly in your own faction's territory!"));
                return true;
            }
        }

        if (player.getAllowFlight()) {
            player.setAllowFlight(false);
            player.setFlying(false);
            player.sendMessage(msg.info("Flight disabled."));
        } else {
            player.setAllowFlight(true);
            player.setFlying(true);
            player.sendMessage(msg.success(plugin.getConfigManager().isFlightOwnTerritoryOnly()
                    ? "Flight enabled in your territory!"
                    : "Flight enabled!"));
        }

        return true;
    }

    // ==========================================
    // LOGOUT - /f logout
    // Safe logout with warmup (cancelled on damage or movement)
    // ==========================================
    boolean handleLogout(Player player) {

        UUID playerUuid = player.getUniqueId();

        if (!plugin.getCombatManager().isTagged(playerUuid)) {
            player.sendMessage(msg.error("You are not in combat! You can log out normally."));
            return true;
        }

        int warmup = plugin.getConfigManager().getCombatLogoutWarmup();
        if (warmup <= 0) {
            // Clear the tag before kicking, or the quit handler punishes this
            // legitimate logout as combat logging.
            plugin.getCombatManager().clear(playerUuid);
            player.kick(Component.text("You have safely logged out."));
            return true;
        }

        // Replace any in-flight logout warmup for this player.
        BukkitTask previous = pendingLogoutWarmups.remove(playerUuid);
        if (previous != null) {
            previous.cancel();
        }

        // A fresh hit during the warmup pushes the tag expiry forward; compare
        // against the expiry captured now to detect it and abort the logout.
        long expiryAtStart = plugin.getCombatManager().getTagExpiry(playerUuid);

        player.sendMessage(msg.info("Logging out in " + warmup + " seconds... don't move or take damage."));
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            pendingLogoutWarmups.remove(playerUuid);
            if (!player.isOnline()) {
                return;
            }
            if (plugin.getCombatManager().getTagExpiry(playerUuid) > expiryAtStart) {
                player.sendMessage(msg.error("Safe logout cancelled - you took damage!"));
                return;
            }
            plugin.getCombatManager().clear(playerUuid);
            player.kick(Component.text("You have safely logged out."));
        }, warmup * 20L);
        pendingLogoutWarmups.put(playerUuid, task);

        return true;
    }
}
