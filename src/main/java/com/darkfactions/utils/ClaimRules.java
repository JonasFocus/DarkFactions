package com.darkfactions.utils;

import java.util.Map;
import java.util.UUID;

/**
 * Pure, dependency-free land-claim decision logic.
 *
 * <p>Operates only on the in-memory claim map ({@code "world:x:z" -> factionId})
 * and integer chunk coordinates, deliberately free of any Bukkit/Paper types so
 * the connection and buffer rules can be unit tested without a server. The key
 * format produced by {@link #key(String, int, int)} matches the format used by
 * {@code ClaimManager}.
 */
public final class ClaimRules {

    private ClaimRules() {
    }

    /** Build the canonical claim-map key for a chunk: {@code "world:x:z"}. */
    public static String key(String worldName, int x, int z) {
        return worldName + ":" + x + ":" + z;
    }

    /** True if the chunk at (x, z) is claimed by the given faction. */
    public static boolean isOwnedBy(Map<String, UUID> claims, String worldName, int x, int z, UUID factionId) {
        UUID owner = claims.get(key(worldName, x, z));
        return owner != null && owner.equals(factionId);
    }

    /**
     * True if any of the four orthogonally-adjacent chunks is owned by the given
     * faction — the "claims must connect" rule.
     */
    public static boolean isAdjacentToClaim(Map<String, UUID> claims, String worldName, int x, int z, UUID factionId) {
        return isOwnedBy(claims, worldName, x + 1, z, factionId)
                || isOwnedBy(claims, worldName, x - 1, z, factionId)
                || isOwnedBy(claims, worldName, x, z + 1, factionId)
                || isOwnedBy(claims, worldName, x, z - 1, factionId);
    }

    /**
     * True if claiming (x, z) would violate the buffer zone, i.e. some chunk
     * within {@code buffer} chunks (Chebyshev radius, excluding the centre) is
     * owned by a <em>different</em> faction. A buffer of {@code <= 0} disables
     * the rule.
     */
    public static boolean violatesBuffer(Map<String, UUID> claims, String worldName, int x, int z,
                                         UUID factionId, int buffer) {
        if (buffer <= 0) {
            return false;
        }
        for (int dx = -buffer; dx <= buffer; dx++) {
            for (int dz = -buffer; dz <= buffer; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                UUID neighborOwner = claims.get(key(worldName, x + dx, z + dz));
                if (neighborOwner != null && !neighborOwner.equals(factionId)) {
                    return true;
                }
            }
        }
        return false;
    }
}
