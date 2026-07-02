package com.darkfactions.utils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Pure, dependency-free daily elixir claim eligibility checks.
 *
 * <p>Uses calendar-day boundaries in the server's default time zone so a player
 * can claim at most once per local day, regardless of how many times they log in.
 */
public final class ElixirDailyRules {

    private ElixirDailyRules() {
    }

    /**
     * Returns {@code true} when {@code lastClaimEpochMillis} is absent or falls on
     * a different calendar day than {@code nowEpochMillis} in {@code zone}.
     */
    public static boolean isEligibleForDailyClaim(Long lastClaimEpochMillis, long nowEpochMillis, ZoneId zone) {
        if (lastClaimEpochMillis == null) {
            return true;
        }
        LocalDate lastClaimDate = Instant.ofEpochMilli(lastClaimEpochMillis).atZone(zone).toLocalDate();
        LocalDate today = Instant.ofEpochMilli(nowEpochMillis).atZone(zone).toLocalDate();
        return !lastClaimDate.equals(today);
    }
}
