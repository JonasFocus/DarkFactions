package com.darkfactions.managers;

// ==========================================
// PlayerNameCache.java
// Stores player UUID -> name mappings
// So we can always display names even for offline players
// ==========================================

import com.darkfactions.DarkFactions;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerNameCache {

    // Reference to main plugin
    private final DarkFactions plugin;

    // The actual cache: UUID -> Player Name
    private final Map<UUID, String> nameCache;

    // File for persisting names
    private final File dataFile;

    // ==========================================
    // Constructor
    // ==========================================
    public PlayerNameCache(DarkFactions plugin) {
        this.plugin = plugin;
        this.nameCache = new HashMap<>();
        this.dataFile = new File(plugin.getDataFolder(), "names.yml");
    }

    // ==========================================
    // Cache Operations
    // ==========================================

    // Update or add a player to the cache
    public void updateName(UUID uuid, String name) {
        nameCache.put(uuid, name);
    }

    // Update from a Player object (used on join)
    public void updateName(Player player) {
        nameCache.put(player.getUniqueId(), player.getName());
    }

    // Get a name from the cache
    // Returns null if not found
    public String getName(UUID uuid) {
        return nameCache.get(uuid);
    }

    // Get a name, with a fallback (UUID truncated)
    // This is the main method other classes should use
    public String getPlayerName(UUID uuid) {
        String name = nameCache.get(uuid);
        if (name != null) {
            return name;
        }

        // Check if they're online right now
        Player player = plugin.getServer().getPlayer(uuid);
        if (player != null && player.isOnline()) {
            nameCache.put(uuid, player.getName());
            return player.getName();
        }

        // Last resort fallback
        return uuid.toString().substring(0, 8) + "...";
    }

    // Check if we have a cached name for this UUID
    public boolean hasName(UUID uuid) {
        return nameCache.containsKey(uuid);
    }

    // Try to find a UUID from a name (reverse lookup)
    public UUID getUuidFromName(String name) {
        for (Map.Entry<UUID, String> entry : nameCache.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(name)) {
                return entry.getKey();
            }
        }
        return null;
    }

    // Get the entire cache (useful for debugging)
    public Map<UUID, String> getAllNames() {
        return new HashMap<>(nameCache);
    }

    // ==========================================
    // Save/Load
    // ==========================================

    // Save names to disk
    public void saveNames() {
        FileConfiguration config = new YamlConfiguration();

        for (Map.Entry<UUID, String> entry : nameCache.entrySet()) {
            config.set(entry.getKey().toString(), entry.getValue());
        }

        try {
            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save player name cache! " + e.getMessage());
        }
    }

    // Load names from disk
    public void loadNames() {
        if (!dataFile.exists()) {
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(dataFile);

        for (String key : config.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String name = config.getString(key);
                nameCache.put(uuid, name);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load cached name for key: " + key);
            }
        }

        plugin.getLogger().info("Loaded " + nameCache.size() + " cached player names!");
    }
}