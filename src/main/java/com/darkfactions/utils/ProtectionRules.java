package com.darkfactions.utils;

import java.util.UUID;

/**
 * Pure territory-protection decision logic, free of Bukkit/Paper types so ally,
 * raidable-bypass, and action-type rules can be unit tested without a server.
 */
public final class ProtectionRules {

    public enum Action {
        BREAK,
        PLACE,
        INTERACT
    }

    private ProtectionRules() {
    }

    /**
     * Returns true when the action should be denied in claimed territory.
     *
     * @param protectionEnabled master protection toggle
     * @param ownerId         faction that owns the chunk, or null for wilderness
     * @param playerFactionId acting player's faction, or null if factionless
     * @param sameFaction     true when the player belongs to the owning faction
     * @param ally            true when the player's faction is allied with the owner
     * @param enemy           true when the player's faction is an enemy of the owner
     * @param ownerRaidable   true when the owning faction's power is below the raidable threshold
     * @param raidableBypass  config flag allowing enemy break/place in raidable territory
     * @param alliesCanBreak  config flag for ally block breaking
     * @param alliesCanPlace  config flag for ally block placing
     * @param alliesCanInteract config flag for ally block interaction
     * @param action          the protection action being attempted
     */
    public static boolean shouldDeny(boolean protectionEnabled,
                                     UUID ownerId,
                                     UUID playerFactionId,
                                     boolean sameFaction,
                                     boolean ally,
                                     boolean enemy,
                                     boolean ownerRaidable,
                                     boolean raidableBypass,
                                     boolean alliesCanBreak,
                                     boolean alliesCanPlace,
                                     boolean alliesCanInteract,
                                     Action action) {
        if (!protectionEnabled || ownerId == null) {
            return false;
        }
        if (playerFactionId == null) {
            return true;
        }
        if (sameFaction) {
            return false;
        }
        if (ally) {
            return switch (action) {
                case BREAK -> !alliesCanBreak;
                case PLACE -> !alliesCanPlace;
                case INTERACT -> !alliesCanInteract;
            };
        }
        if (enemy && ownerRaidable && raidableBypass
                && (action == Action.BREAK || action == Action.PLACE)) {
            return false;
        }
        return true;
    }
}
