package com.darkfactions.managers;

import com.darkfactions.DarkFactions;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CombatManager {

    private final DarkFactions plugin;
    private final Map<UUID, Long> taggedPlayers;

    public CombatManager(DarkFactions plugin) {
        this.plugin = plugin;
        this.taggedPlayers = new ConcurrentHashMap<>();
    }

    public void tag(UUID playerUuid) {
        int duration = plugin.getConfigManager().getCombatTagDuration();
        if (duration <= 0) return;
        taggedPlayers.put(playerUuid, System.currentTimeMillis() + (duration * 1000L));
    }

    public boolean isTagged(UUID playerUuid) {
        Long expiry = taggedPlayers.get(playerUuid);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            taggedPlayers.remove(playerUuid);
            return false;
        }
        return true;
    }

    public int getRemainingSeconds(UUID playerUuid) {
        Long expiry = taggedPlayers.get(playerUuid);
        if (expiry == null) return 0;
        int remaining = (int) ((expiry - System.currentTimeMillis()) / 1000);
        if (remaining <= 0) {
            taggedPlayers.remove(playerUuid);
            return 0;
        }
        return remaining;
    }

    // Raw tag expiry timestamp (0 if untagged). Lets callers detect a re-tag:
    // a fresh hit always pushes the expiry forward.
    public long getTagExpiry(UUID playerUuid) {
        Long expiry = taggedPlayers.get(playerUuid);
        return expiry != null ? expiry : 0L;
    }

    public void clear(UUID playerUuid) {
        taggedPlayers.remove(playerUuid);
    }

    public boolean handleQuit(UUID playerUuid) {
        if (!isTagged(playerUuid)) return false;
        taggedPlayers.remove(playerUuid);
        return true;
    }
}