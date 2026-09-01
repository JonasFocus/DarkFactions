package com.darkfactions.utils;

/**
 * Pure, dependency-free raid elixir steal arithmetic.
 *
 * <p>A successful raid steals a configured percentage of the victim's current
 * elixir, capped at the balance they actually have so the debit can never fail
 * for lack of funds.
 */
public final class ElixirRaidRules {

    private ElixirRaidRules() {
    }

    /**
     * Elixir to steal from {@code victimElixir} at {@code raidStealPercent}.
     * The result is never larger than the victim's current balance.
     */
    public static double stolenAmount(double victimElixir, double raidStealPercent) {
        return Math.min(victimElixir * raidStealPercent, victimElixir);
    }
}
