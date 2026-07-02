package com.darkfactions.utils;

import com.darkfactions.models.Faction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Pure, dependency-free leaderboard ordering for factions.
 *
 * <p>{@code FactionManager} previously carried three near-identical
 * {@code getTopFactionsBy*} methods that each copied the same
 * copy-into-list / sort-descending / sub-list-to-limit dance. This centralises
 * that logic and the descending comparators, leaving the manager a thin
 * delegate, and lets the ordering be unit tested without a server.
 */
public final class FactionRankings {

    /** Highest effective power first; pass a resolver because power is computed from members + bonus. */
    public static Comparator<Faction> byPower(java.util.function.ToDoubleFunction<Faction> effectivePower) {
        return Comparator.comparingDouble(effectivePower).reversed();
    }

    /**
     * @deprecated Sort by {@link #byPower(java.util.function.ToDoubleFunction)} with a
     *             PowerManager-backed effective power resolver instead.
     */
    @Deprecated
    public static final Comparator<Faction> BY_POWER =
            Comparator.comparingDouble(Faction::getBonusPower).reversed();

    /** Highest elixir first. */
    public static final Comparator<Faction> BY_ELIXIR =
            Comparator.comparingDouble(Faction::getElixir).reversed();

    /** Most members first. */
    public static final Comparator<Faction> BY_MEMBERS =
            Comparator.comparingInt(Faction::getMemberCount).reversed();

    private FactionRankings() {
    }

    /**
     * Return the top {@code limit} factions from {@code factions} ordered by
     * {@code order}. A {@code limit} of zero or less yields an empty list; a
     * limit larger than the input returns every faction. The input is not
     * modified and the result is an independent list.
     */
    public static List<Faction> top(Collection<Faction> factions, Comparator<Faction> order, int limit) {
        if (limit <= 0) {
            return new ArrayList<>();
        }
        List<Faction> sorted = new ArrayList<>(factions);
        sorted.sort(order);
        return new ArrayList<>(sorted.subList(0, Math.min(limit, sorted.size())));
    }
}
