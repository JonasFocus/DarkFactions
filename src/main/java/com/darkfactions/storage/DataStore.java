package com.darkfactions.storage;

import com.darkfactions.models.Faction;
import com.darkfactions.models.FactionPlayer;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface DataStore {

    // Schema
    void createTables();
    int getSchemaVersion();
    void setSchemaVersion(int version);

    // Factions
    Collection<Faction> loadAllFactions();
    Map<UUID, UUID> loadPlayerFactionMap();
    void saveFaction(Faction faction);
    void deleteFaction(UUID factionId);

    // Claims
    Map<String, UUID> loadAllClaims();
    void saveClaim(String key, UUID factionId);
    void deleteClaim(String key);
    void deleteAllFactionClaims(UUID factionId);

    // Player data
    Collection<FactionPlayer> loadAllPlayerData();
    void savePlayerData(FactionPlayer data);
    void deletePlayerData(UUID playerUuid);

    // Player names
    Map<UUID, String> loadAllNames();
    void saveName(UUID uuid, String name);

    // Elixir pending
    Map<UUID, Double> loadPendingElixir();
    void savePendingElixir(UUID playerUuid, double amount);
    void deletePendingElixir(UUID playerUuid);
}