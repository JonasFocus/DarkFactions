package com.darkfactions.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.darkfactions.models.Faction;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class FactionRankingsTest {

    private Faction faction(String name, double power, double elixir, int extraMembers) {
        Faction f = new Faction(name, UUID.randomUUID());
        f.setPower(power);
        f.setElixir(elixir);
        for (int i = 0; i < extraMembers; i++) {
            f.addMember(UUID.randomUUID());
        }
        return f;
    }

    @Test
    void ordersByPowerDescending() {
        Faction low = faction("Low", 5, 0, 0);
        Faction high = faction("High", 50, 0, 0);
        Faction mid = faction("Mid", 20, 0, 0);

        List<Faction> top = FactionRankings.top(List.of(low, high, mid), FactionRankings.BY_POWER, 10);

        assertEquals(List.of("High", "Mid", "Low"), top.stream().map(Faction::getName).toList());
    }

    @Test
    void ordersByElixirDescending() {
        Faction a = faction("A", 0, 100, 0);
        Faction b = faction("B", 0, 300, 0);

        List<Faction> top = FactionRankings.top(List.of(a, b), FactionRankings.BY_ELIXIR, 10);

        assertEquals(List.of("B", "A"), top.stream().map(Faction::getName).toList());
    }

    @Test
    void ordersByMembersDescending() {
        Faction solo = faction("Solo", 0, 0, 0);   // 1 member (the leader)
        Faction big = faction("Big", 0, 0, 4);     // 5 members

        List<Faction> top = FactionRankings.top(List.of(solo, big), FactionRankings.BY_MEMBERS, 10);

        assertEquals(List.of("Big", "Solo"), top.stream().map(Faction::getName).toList());
    }

    @Test
    void limitTruncatesResults() {
        Faction a = faction("A", 30, 0, 0);
        Faction b = faction("B", 20, 0, 0);
        Faction c = faction("C", 10, 0, 0);

        List<Faction> top = FactionRankings.top(List.of(a, b, c), FactionRankings.BY_POWER, 2);

        assertEquals(List.of("A", "B"), top.stream().map(Faction::getName).toList());
    }

    @Test
    void zeroOrNegativeLimitYieldsEmpty() {
        Faction a = faction("A", 30, 0, 0);
        assertTrue(FactionRankings.top(List.of(a), FactionRankings.BY_POWER, 0).isEmpty());
        assertTrue(FactionRankings.top(List.of(a), FactionRankings.BY_POWER, -5).isEmpty());
    }

    @Test
    void limitLargerThanInputReturnsAll() {
        Faction a = faction("A", 30, 0, 0);
        Faction b = faction("B", 20, 0, 0);

        List<Faction> top = FactionRankings.top(List.of(a, b), FactionRankings.BY_POWER, 100);

        assertEquals(2, top.size());
    }

    @Test
    void doesNotMutateInput() {
        Faction a = faction("A", 10, 0, 0);
        Faction b = faction("B", 30, 0, 0);
        List<Faction> input = new java.util.ArrayList<>(List.of(a, b));

        FactionRankings.top(input, FactionRankings.BY_POWER, 10);

        assertEquals(List.of("A", "B"), input.stream().map(Faction::getName).toList());
    }
}
