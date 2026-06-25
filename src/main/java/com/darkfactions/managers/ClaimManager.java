package com.darkfactions.managers;

// ==========================================
// ClaimManager.java
// Manages ALL land claiming for factions
// Chunks are stored as "world:x:z" strings for fast lookups
// ALL values come from ConfigManager
// ==========================================

import com.darkfactions.DarkFactions;
import com.darkfactions.models.Faction;
import com.darkfactions.utils.ConfigManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ClaimManager {

    private final DarkFactions plugin;
    private final Map<String, UUID> claimMap;
    private final Map<UUID, Integer> factionClaimCount;
    private final Set<UUID> bypassPlayers;
    private final File dataFile;

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
        this.claimMap = new HashMap<>();
        this.factionClaimCount = new HashMap<>();
        this.bypassPlayers = new HashSet<>();
        this.dataFile = new File(plugin.getDataFolder(), "claims.yml");

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
        return chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
    }

    private String locationToKey(Location location) {
        return location.getWorld().getName() + ":" +
                (location.getBlockX() >> 4) + ":" +
                (location.getBlockZ() >> 4);
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
        if (claimBufferChunks > 0) {
            for (int dx = -claimBufferChunks; dx <= claimBufferChunks; dx++) {
                for (int dz = -claimBufferChunks; dz <= claimBufferChunks; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    UUID neighborOwner = getOwnerAt(chunk.getWorld(), chunk.getX() + dx, chunk.getZ() + dz);
                    if (neighborOwner != null && !neighborOwner.equals(factionId)) {
                        return ClaimResult.BUFFER_VIOLATION;
                    }
                }
            }
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
            }
        }

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

            // Lose elixir for unclaiming
            double lostElixir = plugin.getConfigManager().getElixirPerChunkLost();
            if (lostElixir > 0) {
                Faction faction = plugin.getFactionManager().getFaction(factionId);
                if (faction != null) {
                    faction.removeElixir(lostElixir);
                }
            }

            return true;
        }

        return false;
    }

    public int unclaimAll(UUID factionId) {
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, UUID> entry : claimMap.entrySet()) {
            if (entry.getValue().equals(factionId)) {
                toRemove.add(entry.getKey());
            }
        }

        for (String key : toRemove) {
            claimMap.remove(key);
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

    private UUID getOwnerAt(World world, int x, int z) {
        return claimMap.get(world.getName() + ":" + x + ":" + z);
    }

    private boolean isAdjacentToClaim(Chunk chunk, UUID factionId) {
        int x = chunk.getX();
        int z = chunk.getZ();
        World world = chunk.getWorld();

        return isChunkOwnedBy(world, x + 1, z, factionId) ||
               isChunkOwnedBy(world, x - 1, z, factionId) ||
               isChunkOwnedBy(world, x, z + 1, factionId) ||
               isChunkOwnedBy(world, x, z - 1, factionId);
    }

    private boolean isChunkOwnedBy(World world, int x, int z, UUID factionId) {
        UUID owner = getOwnerAt(world, x, z);
        return owner != null && owner.equals(factionId);
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
                    if (playerFaction != null && ownerId.equals(playerFaction.getFactionId())) {
                        map.append(ownColor).append(ownTile).append("§r");
                    } else if (playerFaction != null && plugin.getFactionManager().getFaction(ownerId) != null
                            && plugin.getFactionManager().getFaction(ownerId).isAlly(playerFaction.getFactionId())) {
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
    // Save/Load
    // ==========================================

    public void saveClaims() {
        FileConfiguration config = new YamlConfiguration();

        for (Map.Entry<String, UUID> entry : claimMap.entrySet()) {
            String factionIdStr = entry.getValue().toString();
            List<String> claimList = config.getStringList("claims." + factionIdStr);
            claimList.add(entry.getKey());
            config.set("claims." + factionIdStr, claimList);
        }

        try {
            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save claim data! " + e.getMessage());
        }
    }

    public void loadClaims() {
        if (!dataFile.exists()) return;

        FileConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        if (!config.contains("claims")) return;

        int totalClaims = 0;

        for (String factionIdStr : config.getConfigurationSection("claims").getKeys(false)) {
            try {
                UUID factionId = UUID.fromString(factionIdStr);
                List<String> claimList = config.getStringList("claims." + factionIdStr);
                int count = 0;

                for (String key : claimList) {
                    claimMap.put(key, factionId);
                    count++;
                }

                factionClaimCount.put(factionId, count);
                totalClaims += count;

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to load claims for faction: " + factionIdStr);
            }
        }

        plugin.getLogger().info("Loaded " + totalClaims + " claims from disk!");
    }
}