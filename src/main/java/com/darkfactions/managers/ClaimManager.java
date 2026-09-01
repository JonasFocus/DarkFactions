package com.darkfactions.managers;

// ==========================================
// ClaimManager.java
// Manages ALL land claiming for factions
// Chunks are stored as "world:x:z" strings for fast lookups
// ALL values come from ConfigManager
// ==========================================

import com.darkfactions.DarkFactions;
import com.darkfactions.models.Faction;
import com.darkfactions.storage.DataStore;
import com.darkfactions.storage.SaveQueue;
import com.darkfactions.utils.ClaimChangeSet;
import com.darkfactions.utils.ClaimRules;
import com.darkfactions.utils.ConfigManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class ClaimManager {

    private final DarkFactions plugin;
    private final Map<String, UUID> claimMap;
    private final Map<UUID, Set<String>> claimsByFaction;
    private final Set<UUID> bypassPlayers;

    // Pending claim writes/deletes awaiting the next async flush. Tracking deletes
    // here is what lets unclaims actually reach the database.
    private final ClaimChangeSet changes;

    // Cached config values
    private int maxClaimsPerFaction;
    private double claimCost;
    private boolean requireConnection;
    private boolean firstClaimFree;
    private int minDistanceFromSpawn;
    private List<String> disabledWorlds;
    private List<String> whitelistWorlds;
    private int claimBufferChunks;

    public ClaimManager(DarkFactions plugin) {
        this.plugin = plugin;
        this.claimMap = new ConcurrentHashMap<>();
        this.claimsByFaction = new ConcurrentHashMap<>();
        this.bypassPlayers = new HashSet<>();
        this.changes = new ClaimChangeSet();
        reloadConfig();
    }

    // ==========================================
    // Load ALL values from ConfigManager
    // ==========================================
    public void reloadConfig() {
        ConfigManager cfg = plugin.getConfigManager();
        this.maxClaimsPerFaction = cfg.getMaxClaimsPerFaction();
        this.claimCost = cfg.getClaimCostElixir();
        this.requireConnection = cfg.isClaimRequireConnection();
        this.firstClaimFree = cfg.isFirstClaimFree();
        this.minDistanceFromSpawn = cfg.getClaimMinDistanceFromSpawn();
        this.disabledWorlds = cfg.getClaimDisabledWorlds();
        this.whitelistWorlds = cfg.getClaimWhitelistWorlds();
        this.claimBufferChunks = cfg.getClaimBufferChunks();
    }

    // ==========================================
    // Key helpers
    // ==========================================

    private String chunkToKey(Chunk chunk) {
        return ClaimRules.key(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }

    private String locationToKey(Location location) {
        return ClaimRules.key(location.getWorld().getName(),
                location.getBlockX() >> 4,
                location.getBlockZ() >> 4);
    }

    // ==========================================
    // Claim Operations
    // ==========================================

    public ClaimResult claimChunk(Chunk chunk, UUID factionId) {

        // Check if claiming is enabled
        if (!plugin.getConfigManager().isClaimEnabled()) {
            return ClaimResult.DISABLED;
        }

        // World blacklist check
        String worldName = chunk.getWorld().getName();
        if (disabledWorlds.contains(worldName)) {
            return ClaimResult.DISABLED_WORLD;
        }

        // World whitelist check (if populated, must be in the list)
        if (!whitelistWorlds.isEmpty() && !whitelistWorlds.contains(worldName)) {
            return ClaimResult.DISABLED_WORLD;
        }

        // Spawn distance check
        if (minDistanceFromSpawn > 0) {
            Location spawn = chunk.getWorld().getSpawnLocation();
            int spawnChunkX = spawn.getBlockX() >> 4;
            int spawnChunkZ = spawn.getBlockZ() >> 4;
            int distX = Math.abs(chunk.getX() - spawnChunkX);
            int distZ = Math.abs(chunk.getZ() - spawnChunkZ);
            // Square exclusion zone: blocked only when inside the radius on BOTH axes,
            // so the protected area is a (2*minDistance-1) chunk box around spawn.
            if (distX < minDistanceFromSpawn && distZ < minDistanceFromSpawn) {
                return ClaimResult.TOO_CLOSE_SPAWN;
            }
        }

        // Already claimed check
        if (isChunkClaimed(chunk)) {
            UUID ownerId = getClaimOwner(chunk);
            if (ownerId != null && ownerId.equals(factionId)) {
                return ClaimResult.ALREADY_OWNED;
            }
            return ClaimResult.ALREADY_CLAIMED;
        }

        // Buffer zone check - no claiming adjacent to other factions
        if (ClaimRules.violatesBuffer(claimMap, worldName, chunk.getX(), chunk.getZ(), factionId, claimBufferChunks)) {
            return ClaimResult.BUFFER_VIOLATION;
        }

        int currentClaims = getClaimCount(factionId);

        // First claim always ignores adjacency; first-claim-free only skips elixir cost.
        if (requireConnection && currentClaims > 0 && !isAdjacentToClaim(chunk, factionId)) {
            return ClaimResult.NOT_CONNECTED;
        }

        // Claim limit check
        if (currentClaims >= maxClaimsPerFaction) {
            return ClaimResult.TOO_MANY;
        }

        // Power gate: each claim requires power-per-claim effective power
        double powerPerClaim = plugin.getConfigManager().getPowerPerClaim();
        if (powerPerClaim > 0) {
            double effective = plugin.getPowerManager().getEffectiveFactionPower(factionId);
            if (!ClaimRules.canClaimMore(currentClaims, effective, powerPerClaim)) {
                return ClaimResult.INSUFFICIENT_POWER;
            }
        }

        // Elixir cost check (skip for first claim if firstClaimFree is on)
        if (claimCost > 0 && !(firstClaimFree && currentClaims == 0)) {
            Faction faction = plugin.getFactionManager().getFaction(factionId);
            if (faction == null || !faction.removeElixir(claimCost)) {
                return ClaimResult.NO_ELIXIR;
            }
        }

        // Claim it!
        String key = chunkToKey(chunk);
        claimMap.put(key, factionId);
        claimsByFaction.computeIfAbsent(factionId, id -> ConcurrentHashMap.newKeySet()).add(key);

        // Give elixir for claiming
        double chunkElixirReward = plugin.getConfigManager().getElixirPerChunkClaim();
        if (chunkElixirReward > 0) {
            Faction faction = plugin.getFactionManager().getFaction(factionId);
            if (faction != null) {
                faction.addElixir(chunkElixirReward);
            }
        }

        changes.recordUpsert(key);
        return ClaimResult.SUCCESS;
    }

    /**
     * Admin overwrite: claim this chunk for {@code factionId} with no player
     * claim rules (world lists, adjacency, power, cap, elixir, spawn, buffer).
     */
    public ClaimResult forceClaim(Chunk chunk, UUID factionId) {
        String key = chunkToKey(chunk);
        UUID currentOwner = claimMap.get(key);

        if (currentOwner != null && currentOwner.equals(factionId)) {
            return ClaimResult.ALREADY_OWNED;
        }

        if (currentOwner != null) {
            claimsByFaction.computeIfPresent(currentOwner, (id, keys) -> {
                keys.remove(key);
                return keys.isEmpty() ? null : keys;
            });
        }

        claimMap.put(key, factionId);
        claimsByFaction.computeIfAbsent(factionId, id -> ConcurrentHashMap.newKeySet()).add(key);
        changes.recordUpsert(key);
        return ClaimResult.SUCCESS;
    }

    public boolean unclaimChunk(Chunk chunk) {
        String key = chunkToKey(chunk);
        UUID factionId = claimMap.remove(key);

        if (factionId != null) {
            claimsByFaction.computeIfPresent(factionId, (id, keys) -> {
                keys.remove(key);
                return keys.isEmpty() ? null : keys;
            });

            Faction faction = plugin.getFactionManager().getFaction(factionId);
            if (faction != null) {
                clearHomeIfOnClaim(faction, key);

                // Lose elixir for unclaiming — skip silently if the faction doesn't
                // have enough, rather than blocking the unclaim entirely.
                double lostElixir = plugin.getConfigManager().getElixirPerChunkLost();
                if (lostElixir > 0 && !faction.removeElixir(lostElixir)) {
                    plugin.getLogger().warning("Faction " + faction.getName()
                            + " had insufficient elixir for unclaim penalty (" + lostElixir + ")");
                }
            }

            changes.recordDelete(key);
            return true;
        }

        return false;
    }

    public int unclaimAll(UUID factionId) {
        // Collect first, then remove: avoids mutating claimMap while iterating it.
        List<String> toRemove = getFactionClaims(factionId);
        Faction faction = plugin.getFactionManager().getFaction(factionId);
        for (String key : toRemove) {
            claimMap.remove(key);
            changes.recordDelete(key);
        }

        claimsByFaction.remove(factionId);

        if (faction != null) {
            for (String key : toRemove) {
                clearHomeIfOnClaim(faction, key);
            }

            // Charge the per-chunk unclaim penalty once. Cap at the current
            // balance so a short treasury cannot block the mass unclaim.
            double lostElixir = plugin.getConfigManager().getElixirPerChunkLost();
            if (lostElixir > 0 && !toRemove.isEmpty()) {
                double charge = Math.min(toRemove.size() * lostElixir, faction.getElixir());
                if (charge > 0) {
                    faction.removeElixir(charge);
                }
            }
        }

        return toRemove.size();
    }

    /** Clear the faction home if it sits on the unclaimed chunk (world + blockX>>4 / blockZ>>4). */
    private void clearHomeIfOnClaim(Faction faction, String claimKey) {
        if (claimContainsHome(faction, claimKey)) {
            faction.setWorldName(null);
        }
    }

    private boolean claimContainsHome(Faction faction, String claimKey) {
        if (!faction.hasHome()) {
            return false;
        }
        int homeChunkX = ((int) Math.floor(faction.getHomeX())) >> 4;
        int homeChunkZ = ((int) Math.floor(faction.getHomeZ())) >> 4;
        return ClaimRules.key(faction.getWorldName(), homeChunkX, homeChunkZ).equals(claimKey);
    }

    // ==========================================
    // Queries
    // ==========================================

    public boolean isChunkClaimed(Chunk chunk) {
        return claimMap.containsKey(chunkToKey(chunk));
    }

    public boolean isLocationClaimed(Location location) {
        return claimMap.containsKey(locationToKey(location));
    }

    public UUID getClaimOwner(Chunk chunk) {
        return claimMap.get(chunkToKey(chunk));
    }

    public UUID getLocationOwner(Location location) {
        return claimMap.get(locationToKey(location));
    }

    public UUID getOwnerByKey(String key) {
        return claimMap.get(key);
    }

    private boolean isAdjacentToClaim(Chunk chunk, UUID factionId) {
        return ClaimRules.isAdjacentToClaim(claimMap, chunk.getWorld().getName(),
                chunk.getX(), chunk.getZ(), factionId);
    }

    private boolean isChunkOwnedBy(World world, int x, int z, UUID factionId) {
        return ClaimRules.isOwnedBy(claimMap, world.getName(), x, z, factionId);
    }

    public int getClaimCount(UUID factionId) {
        Set<String> keys = claimsByFaction.get(factionId);
        return keys == null ? 0 : keys.size();
    }

    public List<String> getFactionClaims(UUID factionId) {
        Set<String> keys = claimsByFaction.get(factionId);
        return keys == null ? new ArrayList<>() : new ArrayList<>(keys);
    }

    // ==========================================
    // ASCII Map - uses config colors and chars
    // ==========================================

    public Component getAsciiMap(Player player, int radius) {
        int playerChunkX = player.getLocation().getChunk().getX();
        int playerChunkZ = player.getLocation().getChunk().getZ();
        String worldName = player.getWorld().getName();

        ConfigManager cfg = plugin.getConfigManager();

        String ownChar = cfg.getMapCharOwnPlayer();
        String ownTile = cfg.getMapCharOwn();
        String allyTile = cfg.getMapCharAlly();
        String enemyTile = cfg.getMapCharEnemy();
        String wildTile = cfg.getMapCharWilderness();

        String ownColor = cfg.getMapColorOwn().replace('&', '\u00A7');
        String allyColor = cfg.getMapColorAlly().replace('&', '\u00A7');
        String enemyColor = cfg.getMapColorEnemy().replace('&', '\u00A7');
        String wildColor = cfg.getMapColorWilderness().replace('&', '\u00A7');

        StringBuilder map = new StringBuilder();
        map.append("§7");

        Faction playerFaction = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());

        for (int z = -radius; z <= radius; z++) {
            for (int x = -radius; x <= radius; x++) {
                String key = worldName + ":" + (playerChunkX + x) + ":" + (playerChunkZ + z);
                UUID ownerId = claimMap.get(key);

                if (x == 0 && z == 0) {
                    if (ownerId != null) {
                        map.append(ownColor).append(ownChar).append("§r");
                    } else {
                        map.append(wildColor).append(ownChar).append("§r");
                    }
                } else if (ownerId != null) {
                    Faction ownerFaction = plugin.getFactionManager().getFaction(ownerId);
                    if (playerFaction != null && ownerId.equals(playerFaction.getFactionId())) {
                        map.append(ownColor).append(ownTile).append("§r");
                    } else if (playerFaction != null && ownerFaction != null
                            && ownerFaction.isAlly(playerFaction.getFactionId())) {
                        map.append(allyColor).append(allyTile).append("§r");
                    } else {
                        map.append(enemyColor).append(enemyTile).append("§r");
                    }
                } else {
                    map.append(wildColor).append(wildTile).append("§r");
                }
            }
            map.append("\n");
        }

        map.append(ownColor).append(ownChar).append("§r You  ")
           .append(allyColor).append(allyTile).append("§r Ally  ")
           .append(enemyColor).append(enemyTile).append("§r Enemy  ")
           .append(wildColor).append(wildTile).append("§r Wild");

        return plugin.getMessageUtils().header("Territory Map")
                .append(Component.newline())
                .append(LegacyComponentSerializer.legacySection().deserialize(map.toString()));
    }

    public boolean isBorderChunk(Chunk chunk, UUID factionId) {
        if (!isChunkOwnedBy(chunk.getWorld(), chunk.getX(), chunk.getZ(), factionId)) {
            return false;
        }

        return !isChunkOwnedBy(chunk.getWorld(), chunk.getX() + 1, chunk.getZ(), factionId) ||
               !isChunkOwnedBy(chunk.getWorld(), chunk.getX() - 1, chunk.getZ(), factionId) ||
               !isChunkOwnedBy(chunk.getWorld(), chunk.getX(), chunk.getZ() + 1, factionId) ||
               !isChunkOwnedBy(chunk.getWorld(), chunk.getX(), chunk.getZ() - 1, factionId);
    }

    public void removeAllFactionClaims(UUID factionId) {
        unclaimAll(factionId);
    }

    public Set<UUID> getBypassPlayers() {
        return bypassPlayers;
    }

    // ==========================================
    // Save/Load via DataStore
    // ==========================================

    public void loadFromStore(DataStore store) {
        // Runs after FactionManager.loadFromStore, so faction lookups are live.
        // Rows referencing a faction that no longer exists (e.g. a crash midway
        // through a faction delete) are dropped and queued for deletion so the
        // database self-heals on the next save cycle.
        int orphans = 0;
        for (Map.Entry<String, UUID> entry : store.loadAllClaims().entrySet()) {
            if (plugin.getFactionManager().getFaction(entry.getValue()) == null) {
                changes.recordDelete(entry.getKey());
                orphans++;
                continue;
            }
            claimMap.put(entry.getKey(), entry.getValue());
            claimsByFaction.computeIfAbsent(entry.getValue(), id -> ConcurrentHashMap.newKeySet()).add(entry.getKey());
        }
        if (orphans > 0) {
            plugin.getLogger().warning("Dropped " + orphans + " orphaned claim(s) referencing deleted factions.");
        }
        plugin.getLogger().info("Loaded " + claimMap.size() + " claims!");
    }

    public void saveToStoreAsync(SaveQueue queue) {
        if (changes.isEmpty()) return;
        ClaimChangeSet.Drain drain = changes.drain();
        queue.submit(() -> {
            try {
                flushDrain(queue.store(), drain);
            } catch (RuntimeException e) {
                for (String key : drain.upserts()) {
                    changes.recordUpsert(key);
                }
                for (String key : drain.deletes()) {
                    changes.recordDelete(key);
                }
                plugin.getLogger().log(Level.SEVERE, "Claim save failed, will retry on the next save cycle", e);
            }
        });
    }

    /** Synchronous save used during plugin shutdown. */
    public void saveToStoreSync(DataStore store) {
        if (changes.isEmpty()) return;
        ClaimChangeSet.Drain drain = changes.drain();
        try {
            flushDrain(store, drain);
        } catch (RuntimeException e) {
            for (String key : drain.upserts()) {
                changes.recordUpsert(key);
            }
            for (String key : drain.deletes()) {
                changes.recordDelete(key);
            }
            plugin.getLogger().log(Level.SEVERE, "Claim save failed during shutdown", e);
        }
    }

    private void flushDrain(DataStore store, ClaimChangeSet.Drain drain) {
        // Re-check live claimMap so this flush is self-contained: an upsert
        // that was unclaimed becomes a delete, and a delete that is now owned
        // is saved. A crash before the next cycle cannot resurrect or drop land.
        for (String key : drain.upserts()) {
            UUID owner = claimMap.get(key);
            if (owner != null) {
                store.saveClaim(key, owner);
            } else {
                store.deleteClaim(key);
            }
        }
        for (String key : drain.deletes()) {
            UUID owner = claimMap.get(key);
            if (owner != null) {
                store.saveClaim(key, owner);
            } else {
                store.deleteClaim(key);
            }
        }
    }
}