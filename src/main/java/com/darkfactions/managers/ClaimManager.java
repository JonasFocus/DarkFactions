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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClaimManager implements Reloadable {

    private final DarkFactions plugin;
    private final Map<String, UUID> claimMap;
    private final Map<UUID, Integer> factionClaimCount;
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
        this.factionClaimCount = new ConcurrentHashMap<>();
        this.bypassPlayers = new HashSet<>();
        this.changes = new ClaimChangeSet();
        reloadConfig();
    }

    // ==========================================
    // Load ALL values from ConfigManager
    // ==========================================
    @Override
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

        int currentClaims = factionClaimCount.getOrDefault(factionId, 0);

        // Connection check (skip for first claim if firstClaimFree is on)
        if (requireConnection && currentClaims > 0 && !isAdjacentToClaim(chunk, factionId)) {
            return ClaimResult.NOT_CONNECTED;
        }

        // Claim limit check
        if (currentClaims >= maxClaimsPerFaction) {
            return ClaimResult.TOO_MANY;
        }

        // Elixir cost check (skip for first claim if firstClaimFree is on)
        if (claimCost > 0 && !(firstClaimFree && currentClaims == 0)) {
            Faction faction = plugin.getFactionManager().getFaction(factionId);
            if (faction == null || !faction.removeElixir(claimCost)) {
                return ClaimResult.NO_ELIXIR;
            }
            plugin.getFactionManager().markDirty();
        }

        // Claim it!
        String key = chunkToKey(chunk);
        claimMap.put(key, factionId);
        factionClaimCount.merge(factionId, 1, Integer::sum);

        // Give elixir for claiming
        double chunkElixirReward = plugin.getConfigManager().getElixirPerChunkClaim();
        if (chunkElixirReward > 0) {
            Faction faction = plugin.getFactionManager().getFaction(factionId);
            if (faction != null) {
                faction.addElixir(chunkElixirReward);
                plugin.getFactionManager().markDirty();
            }
        }

        changes.recordUpsert(key);
        return ClaimResult.SUCCESS;
    }

    public boolean unclaimChunk(Chunk chunk) {
        String key = chunkToKey(chunk);
        UUID factionId = claimMap.remove(key);

        if (factionId != null) {
            factionClaimCount.merge(factionId, -1, Integer::sum);
            if (factionClaimCount.get(factionId) <= 0) {
                factionClaimCount.remove(factionId);
            }

            // Lose elixir for unclaiming — skip silently if the faction doesn't
            // have enough, rather than blocking the unclaim entirely.
            double lostElixir = plugin.getConfigManager().getElixirPerChunkLost();
            if (lostElixir > 0) {
                Faction faction = plugin.getFactionManager().getFaction(factionId);
                if (faction != null) {
                    if (!faction.removeElixir(lostElixir)) {
                        plugin.getLogger().warning("Faction " + faction.getName()
                                + " had insufficient elixir for unclaim penalty (" + lostElixir + ")");
                    } else {
                        plugin.getFactionManager().markDirty();
                    }
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
        for (String key : toRemove) {
            claimMap.remove(key);
            changes.recordDelete(key);
        }

        factionClaimCount.remove(factionId);

        return toRemove.size();
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
        return factionClaimCount.getOrDefault(factionId, 0);
    }

    public List<String> getFactionClaims(UUID factionId) {
        List<String> results = new ArrayList<>();
        for (Map.Entry<String, UUID> entry : claimMap.entrySet()) {
            if (entry.getValue().equals(factionId)) {
                results.add(entry.getKey());
            }
        }
        return results;
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
        claimMap.putAll(store.loadAllClaims());
        for (UUID fid : claimMap.values()) {
            factionClaimCount.merge(fid, 1, Integer::sum);
        }
        plugin.getLogger().info("Loaded " + claimMap.size() + " claims!");
    }

    public void saveToStoreAsync(SaveQueue queue) {
        if (changes.isEmpty()) return;
        ClaimChangeSet.Drain drain = changes.drain();
        queue.submit(() -> {
            DataStore store = queue.store();
            // Both branches re-check the live claimMap rather than trusting the
            // drained key, so the flush is order-independent across SaveQueue's
            // worker threads: if a chunk is unclaimed then reclaimed across two
            // flushes, whichever task runs last still leaves the database matching
            // current ownership.
            for (String key : drain.upserts()) {
                UUID owner = claimMap.get(key);
                if (owner != null) {
                    store.saveClaim(key, owner);
                }
            }
            for (String key : drain.deletes()) {
                if (!claimMap.containsKey(key)) {
                    store.deleteClaim(key);
                }
            }
        });
    }
}