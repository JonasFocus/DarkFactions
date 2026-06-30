package com.darkfactions.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FactionListFormatterTest {

    @Test
    void listRowWithTag() {
        assertEquals("[WAR] Warriors - 5 members - 42 power - 3 claims",
                FactionListFormatter.listRow("[WAR] ", "Warriors", 5, 42.0, 3));
    }

    @Test
    void listRowWithoutTag() {
        assertEquals("Warriors - 5 members - 42 power - 3 claims",
                FactionListFormatter.listRow("", "Warriors", 5, 42.0, 3));
    }

    @Test
    void listRowNullTagTreatedAsEmpty() {
        assertEquals("Solo - 1 members - 0 power - 0 claims",
                FactionListFormatter.listRow(null, "Solo", 1, 0.0, 0));
    }

    @Test
    void listRowPowerHasNoDecimals() {
        // %.0f rounds half-up: 12.7 -> 13.
        assertEquals("A - 2 members - 13 power - 1 claims",
                FactionListFormatter.listRow("", "A", 2, 12.7, 1));
    }

    @Test
    void listRowUsesRootLocaleDecimalSeparator() {
        // No comma separator even for values a non-ROOT locale might format differently.
        assertEquals("A - 0 members - 1000 power - 0 claims",
                FactionListFormatter.listRow("", "A", 0, 1000.0, 0));
    }

    @Test
    void rankRowWithTag() {
        assertEquals("1. [WAR] Warriors - 12.5 power",
                FactionListFormatter.rankRow(1, "[WAR] ", "Warriors", "12.5 power"));
    }

    @Test
    void rankRowWithoutTag() {
        assertEquals("3. Solo - 7 members",
                FactionListFormatter.rankRow(3, "", "Solo", "7 members"));
    }

    @Test
    void metricFormatsWithGivenDecimals() {
        assertEquals("12.5 power", FactionListFormatter.metric(12.5, 1, "power"));
        assertEquals("100 elixir", FactionListFormatter.metric(100.0, 0, "elixir"));
    }
}
