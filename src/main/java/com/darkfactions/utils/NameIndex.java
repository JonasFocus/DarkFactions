package com.darkfactions.utils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Pure, dependency-free UUID/name index backing {@code PlayerNameCache}.
 *
 * <p>Maintains a forward {@code UUID -> name} map and a reverse
 * {@code lower-cased name -> UUID} map together, so name lookups are O(1)
 * instead of the previous full scan, and a stale reverse entry is dropped when
 * a UUID's name changes. Free of any Bukkit/Paper types so it can be unit
 * tested directly.
 */
public final class NameIndex {

    private final Map<UUID, String> byUuid = new HashMap<>();
    private final Map<String, UUID> byName = new HashMap<>();

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    /**
     * Record {@code name} for {@code uuid}, keeping both maps in sync. Null
     * arguments are ignored. If the UUID previously had a different name, the
     * old reverse entry is removed only if it still points at this UUID.
     */
    public void put(UUID uuid, String name) {
        if (uuid == null || name == null) {
            return;
        }
        String previous = byUuid.put(uuid, name);
        if (previous != null && !previous.equalsIgnoreCase(name)) {
            byName.remove(key(previous), uuid);
        }
        byName.put(key(name), uuid);
    }

    /** The name recorded for {@code uuid}, or {@code null} if none. */
    public String nameOf(UUID uuid) {
        return byUuid.get(uuid);
    }

    /** The UUID recorded for {@code name} (case-insensitive), or {@code null}. */
    public UUID uuidOf(String name) {
        return name == null ? null : byName.get(key(name));
    }

    /** True if a name is recorded for {@code uuid}. */
    public boolean contains(UUID uuid) {
        return byUuid.containsKey(uuid);
    }

    /** Number of indexed UUIDs. */
    public int size() {
        return byUuid.size();
    }

    /** A defensive snapshot of the {@code UUID -> name} mappings. */
    public Map<UUID, String> entries() {
        return new HashMap<>(byUuid);
    }
}
