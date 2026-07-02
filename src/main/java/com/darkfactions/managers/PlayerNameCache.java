package com.darkfactions.managers;

import com.darkfactions.DarkFactions;
import com.darkfactions.storage.DataStore;
import com.darkfactions.storage.SaveQueue;
import com.darkfactions.utils.NameIndex;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class PlayerNameCache {

    private final DarkFactions plugin;
    private final NameIndex index;
    private final AtomicBoolean dirty;

    public PlayerNameCache(DarkFactions plugin) {
        this.plugin = plugin;
        this.index = new NameIndex();
        this.dirty = new AtomicBoolean(false);
    }

    public void updateName(UUID uuid, String name) {
        index.put(uuid, name);
        dirty.set(true);
    }

    public void updateName(Player player) {
        index.put(player.getUniqueId(), player.getName());
        dirty.set(true);
    }

    public String getName(UUID uuid) {
        return index.nameOf(uuid);
    }

    public String getPlayerName(UUID uuid) {
        String name = index.nameOf(uuid);
        if (name != null) {
            return name;
        }

        Player player = plugin.getServer().getPlayer(uuid);
        if (player != null) {
            index.put(uuid, player.getName());
            dirty.set(true);
            return player.getName();
        }

        return uuid.toString().substring(0, 8) + "...";
    }

    public boolean hasName(UUID uuid) {
        return index.contains(uuid);
    }

    public UUID getUuidFromName(String name) {
        return index.uuidOf(name);
    }

    public Map<UUID, String> getAllNames() {
        return index.entries();
    }

    public void loadFromStore(DataStore store) {
        for (Map.Entry<UUID, String> e : store.loadAllNames().entrySet()) {
            index.put(e.getKey(), e.getValue());
        }
        plugin.getLogger().info("Loaded " + index.size() + " cached player names!");
    }

    public void saveToStoreAsync(SaveQueue queue) {
        if (!dirty.getAndSet(false)) return;
        queue.submit(() -> flushToStore(queue.store()));
    }

    /** Synchronous save used during plugin shutdown; clears dirty only after write. */
    public void saveToStoreSync(DataStore store) {
        if (!dirty.get()) return;
        flushToStore(store);
        dirty.set(false);
    }

    private void flushToStore(DataStore store) {
        for (Map.Entry<UUID, String> e : index.entries().entrySet()) {
            store.saveName(e.getKey(), e.getValue());
        }
    }
}