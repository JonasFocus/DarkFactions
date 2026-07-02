package com.darkfactions.models;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link Faction}'s set-backed membership: dedup, O(1)
 * membership semantics, officer rules, insertion-order snapshots and the
 * defensive copies returned by the getters.
 */
class FactionTest {

    @Test
    void newFactionHasLeaderAsSoleMember() {
        UUID leader = UUID.randomUUID();
        Faction faction = new Faction("Warriors", leader);

        assertEquals(1, faction.getMemberCount());
        assertTrue(faction.isMember(leader));
        assertTrue(faction.isLeader(leader));
        assertEquals(List.of(leader), faction.getMembers());
    }

    @Test
    void addMemberIsIdempotent() {
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        Faction faction = new Faction("Warriors", leader);

        faction.addMember(member);
        faction.addMember(member);

        assertEquals(2, faction.getMemberCount());
        assertTrue(faction.isMember(member));
    }

    @Test
    void promotionRequiresMembershipAndIsIdempotent() {
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        Faction faction = new Faction("Warriors", leader);
        faction.addMember(member);

        // A non-member can never become an officer.
        faction.promoteToOfficer(stranger);
        assertFalse(faction.isOfficer(stranger));

        faction.promoteToOfficer(member);
        faction.promoteToOfficer(member);
        assertTrue(faction.isOfficer(member));
        assertEquals(1, faction.getOfficers().size());
    }

    @Test
    void removingMemberAlsoStripsOfficerRole() {
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        Faction faction = new Faction("Warriors", leader);
        faction.addMember(member);
        faction.promoteToOfficer(member);

        faction.removeMember(member);

        assertFalse(faction.isMember(member));
        assertFalse(faction.isOfficer(member));
    }

    @Test
    void enemiesAndAlliesDedupe() {
        Faction faction = new Faction("Warriors", UUID.randomUUID());
        UUID other = UUID.randomUUID();

        faction.addEnemy(other);
        faction.addEnemy(other);
        faction.addAlly(other);
        faction.addAlly(other);

        assertTrue(faction.isEnemy(other));
        assertTrue(faction.isAlly(other));
        assertEquals(1, faction.getEnemies().size());
        assertEquals(1, faction.getAllies().size());
    }

    @Test
    void gettersReturnDefensiveCopies() {
        UUID leader = UUID.randomUUID();
        Faction faction = new Faction("Warriors", leader);

        List<UUID> members = faction.getMembers();
        members.add(UUID.randomUUID()); // mutate the copy

        assertEquals(1, faction.getMemberCount());
    }

    @Test
    void snapshotsPreserveInsertionOrder() {
        UUID leader = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        Faction faction = new Faction("Warriors", leader);
        faction.addMember(second);
        faction.addMember(third);

        assertEquals(List.of(leader, second, third), faction.getMembers());
    }

    @Test
    void bonusPowerAccumulates() {
        Faction faction = new Faction("Warriors", UUID.randomUUID());
        assertEquals(0.0, faction.getBonusPower());

        faction.addBonusPower(5.0);
        faction.addBonusPower(3.0);
        assertEquals(8.0, faction.getBonusPower());
    }

    @Test
    void setMembersReplacesAndPreservesOrder() {
        Faction faction = new Faction("Warriors", UUID.randomUUID());
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        faction.setMembers(List.of(a, b));

        assertEquals(List.of(a, b), faction.getMembers());
        assertEquals(2, faction.getMemberCount());
        assertTrue(faction.isMember(a));
    }
}
