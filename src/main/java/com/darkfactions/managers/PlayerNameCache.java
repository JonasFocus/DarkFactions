package com.darkfactions.managers;

// ==========================================
// PlayerNameCache.java
// Stores player UUID -> name mappings
// So we can always display names even for offline players
// ==========================================

import com.darkfactions.DarkFactions;
import com.darkfactions.utils.NameIndex;
import com.darkfactions.utils.YamlStore;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Map;
import java.util.UUID;

public class PlayerNameCache {

    // Reference to main plugin
    private final DarkFactions plugin;

    // The cache: forward UUID -> name plus a reverse name -> UUID index, so
    // reverse lookups are O(1) instead of scanning every cached entry.
    private final NameIndex index;

    // File for persisting names
    private final File dataFile;

    // ==========================================
    // Constructor
    // ==========================================
    public PlayerNameCache(DarkFactions plugin) {
        this.plugin = plugin;
        this.index = new NameIndex();
        this.dataFile = new File(plugin.getDataFolder(), "names.yml");
    }

    // ==========================================
    // Cache Operations
    // ==========================================

    // Update or add a player to the cache
    public void updateName(UUID uuid, String name) {
        index.put(uuid, name);
    }

    // Update from a Player object (used on join)
    public void updateName(Player player) {
        index.put(player.getUniqueId(), player.getName());
    }

    // Get a name from the cache
    // Returns null if not found
    public String getName(UUID uuid) {
        return index.nameOf(uuid);
    }

    // Get a name, with a fallback (UUID truncated)
    // This is the main method other classes should use
    public String getPlayerName(UUID uuid) {
        String name = index.nameOf(uuid);
        if (name != null) {
            return name;
        }

        // Check if they're online right now
        Player player = plugin.getServer().getPlayer(uuid);
        if (player != null && player.isOnline()) {
            index.put(uuid, player.getName());
            return player.getName();
        }

        // Last resort fallback
        return uuid.toString().substring(0, 8) + "...";
    }

    // Check if we have a cached name for this UUID
    public boolean hasName(UUID uuid) {
        return index.contains(uuid);
    }

    // Try to find a UUID from a name (reverse lookup), O(1) via the index.
    public UUID getUuidFromName(String name) {
        return index.uuidOf(name);
    }

    // Get the entire cache (useful for debugging)
    public Map<UUID, String> getAllNames() {
        return index.entries();
    }

    // ==========================================
    // Save/Load
    // ==========================================

    // Save names to disk
    public void saveNames() {
        FileConfiguration config = new YamlConfiguration();

        for (Map.Entry<UUID, String> entry : index.entries().entrySet()) {
            config.set(entry.getKey().toString(), entry.getValue());
        }

        YamlStore.save(config, dataFile, plugin.getLogger());
    }

    // Load names from disk
    public void loadNames() {
        FileConfiguration config = YamlStore.load(dataFile, plugin.getLogger());

        for (String key : config.getKeys(false)) {
            if (key.equals(YamlStore.VERSION_KEY)) continue; // skip the schema-version marker
            try {
                UUID uuid = UUID.fromString(key);
                String name = config.getString(key);
                index.put(uuid, name);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load cached name for key: " + key);
            }
        }

        plugin.getLogger().info("Loaded " + index.size() + " cached player names!");
    }
}
