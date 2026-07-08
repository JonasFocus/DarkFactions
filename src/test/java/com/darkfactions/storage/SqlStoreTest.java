package com.darkfactions.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.darkfactions.models.Faction;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqlStoreTest {

    @TempDir
    File tempDir;

    private DatabaseManager databaseManager;
    private SqlStore store;

    @BeforeEach
    void setUp() {
        Logger logger = Logger.getLogger("SqlStoreTest");
        databaseManager = new DatabaseManager(
                logger, tempDir, DatabaseManager.Type.SQLITE, "localhost", 3306, "test.db", "", "");
        store = new SqlStore(logger, databaseManager);
        store.createTables();
    }

    @AfterEach
    void tearDown() {
        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    @Test
    void createTablesAndSchemaVersionRoundTrip() {
        assertEquals(0, store.getSchemaVersion());
        store.setSchemaVersion(1);
        assertEquals(1, store.getSchemaVersion());
        store.setSchemaVersion(2);
        assertEquals(2, store.getSchemaVersion());
    }

    @Test
    void saveAndLoadFactionRoundTrip() {
        UUID leader = UUID.randomUUID();
        Faction faction = new Faction("Warriors", leader);
        faction.setDescription("test desc");
        faction.setBonusPower(3.5);
        faction.setMaxPower(1.0);
        faction.setElixir(12.0);

        store.saveFaction(faction);

        Collection<Faction> loaded = store.loadAllFactions();
        assertEquals(1, loaded.size());
        Faction roundTrip = loaded.iterator().next();
        assertEquals(faction.getFactionId(), roundTrip.getFactionId());
        assertEquals("Warriors", roundTrip.getName());
        assertEquals(leader, roundTrip.getLeaderUuid());
        assertEquals(3.5, roundTrip.getBonusPower(), 0.001);
        assertEquals(1.0, roundTrip.getMaxPower(), 0.001);
        assertEquals(12.0, roundTrip.getElixir(), 0.001);
        assertTrue(roundTrip.isMember(leader));
    }

    @Test
    void deleteFactionRemovesRow() {
        Faction faction = new Faction("Doomed", UUID.randomUUID());
        store.saveFaction(faction);
        assertEquals(1, store.loadAllFactions().size());

        store.deleteFaction(faction.getFactionId());
        assertTrue(store.loadAllFactions().isEmpty());
    }

    @Test
    void saveAndLoadClaimsRoundTrip() {
        UUID factionId = UUID.randomUUID();
        String key = "world:3:-4";
        store.saveClaim(key, factionId);

        Map<String, UUID> claims = store.loadAllClaims();
        assertEquals(1, claims.size());
        assertEquals(factionId, claims.get(key));

        store.deleteClaim(key);
        assertTrue(store.loadAllClaims().isEmpty());
    }

    @Test
    void migrateSchemaIsIdempotentWhenBonusPowerExists() {
        // createTables already includes bonus_power; migrateSchema must be a no-op.
        store.migrateSchema(1);
        store.migrateSchema(1);

        Faction faction = new Faction("Safe", UUID.randomUUID());
        faction.setBonusPower(7.0);
        store.saveFaction(faction);

        Faction loaded = store.loadAllFactions().iterator().next();
        assertEquals(7.0, loaded.getBonusPower(), 0.001);
        assertFalse(store.loadAllFactions().isEmpty());
    }
}
