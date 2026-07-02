package com.darkfactions.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PowerRulesTest {

    private static final double EPS = 1e-9;

    @Test
    void gainIsClampedToMax() {
        assertEquals(6.0, PowerRules.applyGain(5.0, 1.0, 10.0), EPS);
        assertEquals(10.0, PowerRules.applyGain(9.5, 1.0, 10.0), EPS, "gain must not exceed max");
        assertEquals(10.0, PowerRules.applyGain(10.0, 5.0, 10.0), EPS);
    }

    @Test
    void lossIsClampedToMin() {
        assertEquals(4.0, PowerRules.applyLoss(5.0, 1.0, 0.0), EPS);
        assertEquals(0.0, PowerRules.applyLoss(0.5, 1.0, 0.0), EPS, "loss must not drop below min");
        assertEquals(-2.0, PowerRules.applyLoss(1.0, 5.0, -2.0), EPS, "min floor can be negative");
    }

    @Test
    void offlineDecayCapsHoursThenClampsToMin() {
        // 3 hours offline, 2.0 lost per hour, floor 0 -> 10 - 6 = 4
        assertEquals(4.0, PowerRules.applyOfflineDecay(10.0, 3, 24, 2.0, 0.0), EPS);
        // capped at maxHours: 100 hours but cap 5 -> 5 * 2 = 10 lost -> floor 0
        assertEquals(0.0, PowerRules.applyOfflineDecay(8.0, 100, 5, 2.0, 0.0), EPS);
        // zero hours offline -> unchanged
        assertEquals(8.0, PowerRules.applyOfflineDecay(8.0, 0, 5, 2.0, 0.0), EPS);
    }

    @Test
    void raidableRequiresMembersAndLowPower() {
        // 3 members, threshold 0.5 -> raidable below 1.5 total power
        assertTrue(PowerRules.isRaidable(1.4, 3, 0.5));
        assertFalse(PowerRules.isRaidable(1.5, 3, 0.5), "exactly at threshold is not raidable");
        assertFalse(PowerRules.isRaidable(2.0, 3, 0.5));
    }

    @Test
    void emptyFactionIsNeverRaidable() {
        assertFalse(PowerRules.isRaidable(0.0, 0, 0.5));
    }

    @Test
    void effectiveFactionPowerAddsMemberSumAndBonus() {
        assertEquals(25.0, PowerRules.effectiveFactionPower(20.0, 5.0), EPS);
        assertEquals(20.0, PowerRules.effectiveFactionPower(20.0, 0.0), EPS);
    }

    @Test
    void effectiveFactionMaxPowerAddsMemberSumAndBonus() {
        assertEquals(60.0, PowerRules.effectiveFactionMaxPower(50.0, 10.0), EPS);
        assertEquals(50.0, PowerRules.effectiveFactionMaxPower(50.0, 0.0), EPS);
    }
}
