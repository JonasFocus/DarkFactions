package com.darkfactions.utils;

/**
 * Pure, dependency-free power arithmetic.
 *
 * <p>Centralises the clamp-on-gain / clamp-on-loss idiom that was previously
 * copy-pasted across {@code PowerManager}'s death, kill, regen and decay paths,
 * plus the raidable threshold check. Free of any Bukkit/Paper types so it can be
 * unit tested directly.
 */
public final class PowerRules {

    private PowerRules() {
    }

    /** Apply a gain to {@code current}, clamped so it never exceeds {@code maxPower}. */
    public static double applyGain(double current, double gain, double maxPower) {
        return Math.min(current + gain, maxPower);
    }

    /** Apply a loss to {@code current}, clamped so it never drops below {@code minPower}. */
    public static double applyLoss(double current, double loss, double minPower) {
        return Math.max(current - loss, minPower);
    }

    /**
     * Apply offline decay to {@code current}. {@code offlineHours} is capped at
     * {@code maxHours} before being multiplied by {@code perHour}, and the result
     * is clamped to {@code minPower}.
     */
    public static double applyOfflineDecay(double current, long offlineHours, int maxHours,
                                           double perHour, double minPower) {
        long decayHours = Math.min(offlineHours, maxHours);
        double decay = decayHours * perHour;
        return Math.max(current - decay, minPower);
    }

    /**
     * Effective faction power: per-member power summed, plus admin/shop bonus power.
     */
    public static double effectiveFactionPower(double memberPowerSum, double bonusPower) {
        return memberPowerSum + bonusPower;
    }

    /**
     * Effective faction max power: per-member max power summed, plus bonus max from shop upgrades.
     */
    public static double effectiveFactionMaxPower(double memberMaxPowerSum, double bonusMaxPower) {
        return memberMaxPowerSum + bonusMaxPower;
    }

    /**
     * A faction is raidable when it has at least one member and its total power
     * is below {@code perMemberThreshold} per member.
     */
    public static boolean isRaidable(double totalPower, int memberCount, double perMemberThreshold) {
        return memberCount > 0 && totalPower < (memberCount * perMemberThreshold);
    }
}
