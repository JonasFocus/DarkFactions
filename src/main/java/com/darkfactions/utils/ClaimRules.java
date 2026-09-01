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

    /** World name and chunk coordinates parsed from a claim-map key. */
    public record ParsedKey(String world, int x, int z) {
    }

    /** Build the canonical claim-map key for a chunk: {@code "world:x:z"}. */
    public static String key(String worldName, int x, int z) {
        return worldName + ":" + x + ":" + z;
    }

    /**
     * Split a claim-map key from the last two colons so world names may contain
     * {@code ':'}. Throws {@link IllegalArgumentException} if the key does not
     * have two separators or if the coordinates are not integers.
     */
    public static ParsedKey parseKey(String key) {
        if (key == null) {
            throw new IllegalArgumentException("claim key must not be null");
        }
        int lastColon = key.lastIndexOf(':');
        if (lastColon <= 0) {
            throw new IllegalArgumentException("malformed claim key: " + key);
        }
        int secondLast = key.lastIndexOf(':', lastColon - 1);
        if (secondLast < 0) {
            throw new IllegalArgumentException("malformed claim key: " + key);
        }
        String world = key.substring(0, secondLast);
        if (world.isEmpty()) {
            throw new IllegalArgumentException("malformed claim key: " + key);
        }
        try {
            int x = Integer.parseInt(key.substring(secondLast + 1, lastColon));
            int z = Integer.parseInt(key.substring(lastColon + 1));
            return new ParsedKey(world, x, z);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("malformed claim key: " + key, e);
        }
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

    /**
     * True if the faction can afford one more claim given its effective power
     * and the configured power-per-claim cost. A {@code powerPerClaim <= 0}
     * disables the gate (always allowed).
     */
    public static boolean canClaimMore(int currentClaims, double effectivePower, double powerPerClaim) {
        if (powerPerClaim <= 0) {
            return true;
        }
        return currentClaims + 1 <= effectivePower / powerPerClaim;
    }
}
