package com.darkfactions.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ElixirRaidRulesTest {

    private static final double EPS = 1e-9;

    @Test
    void stealsConfiguredPercentOfVictimBalance() {
        assertEquals(50.0, ElixirRaidRules.stolenAmount(100.0, 0.5), EPS);
        assertEquals(25.0, ElixirRaidRules.stolenAmount(100.0, 0.25), EPS);
    }

    @Test
    void capsStealAtVictimBalance() {
        assertEquals(100.0, ElixirRaidRules.stolenAmount(100.0, 1.0), EPS);
        assertEquals(40.0, ElixirRaidRules.stolenAmount(40.0, 1.5), EPS);
    }

    @Test
    void zeroBalanceOrPercentStealsNothing() {
        assertEquals(0.0, ElixirRaidRules.stolenAmount(0.0, 0.5), EPS);
        assertEquals(0.0, ElixirRaidRules.stolenAmount(80.0, 0.0), EPS);
    }
}
