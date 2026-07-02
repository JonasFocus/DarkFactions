package com.darkfactions.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class ElixirDailyRulesTest {

    private static final ZoneId UTC = ZoneId.of("UTC");

    @Test
    void neverClaimedIsEligible() {
        long now = Instant.parse("2024-06-15T12:00:00Z").toEpochMilli();
        assertTrue(ElixirDailyRules.isEligibleForDailyClaim(null, now, UTC));
    }

    @Test
    void sameCalendarDayIsNotEligible() {
        long morning = Instant.parse("2024-06-15T08:00:00Z").toEpochMilli();
        long evening = Instant.parse("2024-06-15T20:00:00Z").toEpochMilli();
        assertFalse(ElixirDailyRules.isEligibleForDailyClaim(morning, evening, UTC));
    }

    @Test
    void nextCalendarDayIsEligible() {
        long dayOne = Instant.parse("2024-06-15T23:59:00Z").toEpochMilli();
        long dayTwo = Instant.parse("2024-06-16T00:01:00Z").toEpochMilli();
        assertTrue(ElixirDailyRules.isEligibleForDailyClaim(dayOne, dayTwo, UTC));
    }

    @Test
    void respectsTimeZoneForDayBoundary() {
        ZoneId usCentral = ZoneId.of("America/Chicago");
        // 2024-06-15 05:00 CDT = 2024-06-15T10:00:00Z
        long morningCentral = Instant.parse("2024-06-15T10:00:00Z").toEpochMilli();
        // 2024-06-15 22:00 CDT = 2024-06-16T03:00:00Z (next UTC day, same Central day)
        long eveningCentral = Instant.parse("2024-06-16T03:00:00Z").toEpochMilli();

        assertFalse(ElixirDailyRules.isEligibleForDailyClaim(morningCentral, eveningCentral, usCentral));
    }
}
