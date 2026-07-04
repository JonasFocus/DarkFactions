package com.darkfactions.utils;

/**
 * Pure PvP-decision logic, free of Bukkit/Paper types so the faction/ally/territory
 * cascade can be unit tested without a server.
 */
public final class PvpRules {

    public enum Territory {
        WILDERNESS,
        OWN,
        ALLY,
        OTHER
    }

    public enum Verdict {
        ALLOW,
        DENY_FACTION_PVP_DISABLED,
        DENY_ALLY,
        DENY_TERRITORY
    }

    private PvpRules() {
    }

    /**
     * Resolves whether an attack should be allowed.
     *
     * @param sameFaction              true when attacker and victim share a faction
     * @param respectFactionPvpToggle  config flag gating same-faction friendly fire
     * @param victimFactionPvpEnabled  the victim faction's own PvP toggle
     * @param ally                     true when attacker's faction is allied with victim's
     * @param territory                the territory the victim is standing in
     * @param wildernessPvp            config flag allowing PvP in unclaimed land
     * @param ownTerritoryPvp          config flag allowing PvP in the victim's own claims
     * @param allyTerritoryPvp         config flag allowing PvP in an ally's claims
     * @param enemyTerritoryPvp        config flag allowing PvP in enemy/unaffiliated claims
     */
    public static Verdict resolve(boolean sameFaction,
                                   boolean respectFactionPvpToggle,
                                   boolean victimFactionPvpEnabled,
                                   boolean ally,
                                   Territory territory,
                                   boolean wildernessPvp,
                                   boolean ownTerritoryPvp,
                                   boolean allyTerritoryPvp,
                                   boolean enemyTerritoryPvp) {
        if (sameFaction && respectFactionPvpToggle && !victimFactionPvpEnabled) {
            return Verdict.DENY_FACTION_PVP_DISABLED;
        }
        if (ally) {
            return Verdict.DENY_ALLY;
        }
        boolean allowed = switch (territory) {
            case WILDERNESS -> wildernessPvp;
            case OWN -> ownTerritoryPvp;
            case ALLY -> allyTerritoryPvp;
            case OTHER -> enemyTerritoryPvp;
        };
        return allowed ? Verdict.ALLOW : Verdict.DENY_TERRITORY;
    }
}
