package com.darkfactions.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TerritoryMessageFormatterTest {

    @Test
    void expandsAllPlaceholders() {
        String out = TerritoryMessageFormatter.format(
                "{faction} ({leader}) m={members} p={power} e={elixir}",
                "Warriors", "Steve", 3, 12.34, 250.0);
        assertEquals("Warriors (Steve) m=3 p=12.3 e=250", out);
    }

    @Test
    void formatsNumbersWithRootLocale() {
        // Power keeps one decimal, elixir none, regardless of server locale.
        String out = TerritoryMessageFormatter.format("{power}/{elixir}", "F", "L", 1, 0.05, 9.9);
        assertEquals("0.1/10", out);
    }

    @Test
    void nullTemplateYieldsEmptyString() {
        assertEquals("", TerritoryMessageFormatter.format(null, "F", "L", 1, 1.0, 1.0));
    }

    @Test
    void nullNamesBecomeEmpty() {
        assertEquals(" entered  land",
                TerritoryMessageFormatter.format("{leader} entered {faction} land", null, null, 0, 0, 0));
    }

    @Test
    void leavesUnknownPlaceholdersUntouched() {
        assertEquals("hello {world}",
                TerritoryMessageFormatter.format("hello {world}", "F", "L", 0, 0, 0));
    }

    @Test
    void repeatedPlaceholdersAllExpand() {
        assertEquals("Warriors vs Warriors",
                TerritoryMessageFormatter.format("{faction} vs {faction}", "Warriors", "L", 0, 0, 0));
    }
}
