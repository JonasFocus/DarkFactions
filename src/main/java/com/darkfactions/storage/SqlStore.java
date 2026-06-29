package com.darkfactions.storage;

import com.darkfactions.DarkFactions;
import com.darkfactions.models.Faction;
import com.darkfactions.models.FactionPlayer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class SqlStore implements DataStore {

    private static final int SCHEMA_VERSION = 1;

    // Role values stored in faction_members.role
    private static final String ROLE_LEADER = "LEADER";
    private static final String ROLE_OFFICER = "OFFICER";
    private static final String ROLE_MEMBER = "MEMBER";

    // Relation values stored in faction_relations.relation
    private static final String RELATION_ALLY = "ALLY";
    private static final String RELATION_ENEMY = "ENEMY";

    private final DarkFactions plugin;
    private final DatabaseManager db;

    public SqlStore(DarkFactions plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
    }

    @Override
    public void createTables() {
        execute("CREATE TABLE IF NOT EXISTS factions ("
                + "id VARCHAR(36) PRIMARY KEY,"
                + "name VARCHAR(32) NOT NULL,"
                + "leader VARCHAR(36) NOT NULL,"
                + "description TEXT DEFAULT '',"
                + "motd TEXT DEFAULT '',"
                + "tag VARCHAR(16) DEFAULT '',"
                + "home_world VARCHAR(64),"
                + "home_x DOUBLE, home_y DOUBLE, home_z DOUBLE,"
                + "home_yaw FLOAT, home_pitch FLOAT,"
                + "power DOUBLE DEFAULT 0,"
                + "max_power DOUBLE DEFAULT 0,"
                + "elixir DOUBLE DEFAULT 0,"
                + "open BOOLEAN DEFAULT false,"
                + "pvp_enabled BOOLEAN DEFAULT false,"
                + "tnt_enabled BOOLEAN DEFAULT true,"
                + "created BIGINT NOT NULL"
                + ")");

        execute("CREATE TABLE IF NOT EXISTS faction_members ("
                + "faction_id VARCHAR(36) NOT NULL,"
                + "player_uuid VARCHAR(36) NOT NULL,"
                + "role VARCHAR(16) NOT NULL DEFAULT 'MEMBER',"
                + "PRIMARY KEY (faction_id, player_uuid)"
                + ")");

        execute("CREATE TABLE IF NOT EXISTS faction_relations ("
                + "faction_id VARCHAR(36) NOT NULL,"
                + "target_id VARCHAR(36) NOT NULL,"
                + "relation VARCHAR(16) NOT NULL,"
                + "PRIMARY KEY (faction_id, target_id)"
                + ")");

        execute("CREATE TABLE IF NOT EXISTS faction_claims ("
                + "world VARCHAR(64) NOT NULL,"
                + "chunk_x INT NOT NULL,"
                + "chunk_z INT NOT NULL,"
                + "faction_id VARCHAR(36) NOT NULL,"
                + "PRIMARY KEY (world, chunk_x, chunk_z)"
                + ")");

        execute("CREATE TABLE IF NOT EXISTS player_data ("
                + "uuid VARCHAR(36) PRIMARY KEY,"
                + "power DOUBLE DEFAULT 0,"
                + "max_power DOUBLE DEFAULT 10,"
                + "kills INT DEFAULT 0,"
                + "deaths INT DEFAULT 0,"
                + "faction VARCHAR(36),"
                + "last_login BIGINT DEFAULT 0,"
                + "last_logout BIGINT DEFAULT 0"
                + ")");

        execute("CREATE TABLE IF NOT EXISTS player_names ("
                + "uuid VARCHAR(36) PRIMARY KEY,"
                + "name VARCHAR(16) NOT NULL"
                + ")");

        execute("CREATE TABLE IF NOT EXISTS elixir_pending ("
                + "player_uuid VARCHAR(36) PRIMARY KEY,"
                + "amount DOUBLE DEFAULT 0"
                + ")");

        execute("CREATE TABLE IF NOT EXISTS schema_version ("
                + "version INT PRIMARY KEY,"
                + "applied_at BIGINT NOT NULL"
                + ")");
    }

    @Override
    public int getSchemaVersion() {
        try (Connection c = db.getConnection();
             PreparedStatement s = c.prepareStatement("SELECT MAX(version) FROM schema_version");
             ResultSet rs = s.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    @Override
    public void setSchemaVersion(int version) {
        execute("INSERT INTO schema_version (version, applied_at) VALUES (?, ?)",
                version, System.currentTimeMillis());
    }

    // ========== Factions ==========

    @Override
    public Collection<Faction> loadAllFactions() {
        Map<UUID, Faction> result = new HashMap<>();

        String factionSql = "SELECT * FROM factions";
        try (Connection c = db.getConnection();
             PreparedStatement s = c.prepareStatement(factionSql);
             ResultSet rs = s.executeQuery()) {

            while (rs.next()) {
                Faction f = new Faction();
                f.setFactionId(UUID.fromString(rs.getString("id")));
                f.setName(rs.getString("name"));
                f.setLeaderUuid(UUID.fromString(rs.getString("leader")));
                f.setPower(rs.getDouble("power"));
                f.setMaxPower(rs.getDouble("max_power"));
                f.setElixir(rs.getDouble("elixir"));
                f.setOpen(rs.getBoolean("open"));
                f.setPvpEnabled(rs.getBoolean("pvp_enabled"));
                f.setTntEnabled(rs.getBoolean("tnt_enabled"));
                f.setCreationTime(rs.getLong("created"));
                f.setMotd(getStringOrEmpty(rs, "motd"));
                f.setDescription(getStringOrEmpty(rs, "description"));
                f.setTag(getStringOrEmpty(rs, "tag"));

                String hw = rs.getString("home_world");
                if (hw != null && !hw.isEmpty()) {
                    f.setWorldName(hw);
                    f.setHomeX(rs.getDouble("home_x"));
                    f.setHomeY(rs.getDouble("home_y"));
                    f.setHomeZ(rs.getDouble("home_z"));
                    f.setHomeYaw(rs.getFloat("home_yaw"));
                    f.setHomePitch(rs.getFloat("home_pitch"));
                }

                result.put(f.getFactionId(), f);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load factions", e);
        }

        // Load members
        String memberSql = "SELECT player_uuid, role FROM faction_members WHERE faction_id = ?";
        for (Faction f : result.values()) {
            try (Connection c = db.getConnection();
                 PreparedStatement s = c.prepareStatement(memberSql)) {
                s.setString(1, f.getFactionId().toString());
                try (ResultSet rs = s.executeQuery()) {
                    List<UUID> members = new ArrayList<>();
                    List<UUID> officers = new ArrayList<>();
                    while (rs.next()) {
                        UUID puid = UUID.fromString(rs.getString("player_uuid"));
                        String role = rs.getString("role");
                        if (ROLE_OFFICER.equals(role)) {
                            officers.add(puid);
                        } else {
                            members.add(puid);
                        }
                    }
                    f.setMembers(members);
                    f.setOfficers(officers);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load members for " + f.getName(), e);
            }
        }

        // Load relations
        String relSql = "SELECT target_id, relation FROM faction_relations WHERE faction_id = ?";
        for (Faction f : result.values()) {
            try (Connection c = db.getConnection();
                 PreparedStatement s = c.prepareStatement(relSql)) {
                s.setString(1, f.getFactionId().toString());
                try (ResultSet rs = s.executeQuery()) {
                    List<UUID> allies = new ArrayList<>();
                    List<UUID> enemies = new ArrayList<>();
                    while (rs.next()) {
                        UUID tid = UUID.fromString(rs.getString("target_id"));
                        String rel = rs.getString("relation");
                        if (RELATION_ALLY.equals(rel)) {
                            allies.add(tid);
                        } else if (RELATION_ENEMY.equals(rel)) {
                            enemies.add(tid);
                        }
                    }
                    f.setAllies(allies);
                    f.setEnemies(enemies);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load relations for " + f.getName(), e);
            }
        }

        return result.values();
    }

    @Override
    public Map<UUID, UUID> loadPlayerFactionMap() {
        Map<UUID, UUID> map = new HashMap<>();
        String sql = "SELECT player_uuid, faction_id FROM faction_members";
        try (Connection c = db.getConnection();
             PreparedStatement s = c.prepareStatement(sql);
             ResultSet rs = s.executeQuery()) {
            while (rs.next()) {
                map.put(UUID.fromString(rs.getString("player_uuid")),
                        UUID.fromString(rs.getString("faction_id")));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load player faction map", e);
        }
        return map;
    }

    @Override
    public void saveFaction(Faction faction) {
        String sql = "REPLACE INTO factions (id, name, leader, description, motd, tag, "
                + "home_world, home_x, home_y, home_z, home_yaw, home_pitch, "
                + "power, max_power, elixir, open, pvp_enabled, tnt_enabled, created) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        execute(sql,
                faction.getFactionId().toString(),
                faction.getName(),
                faction.getLeaderUuid().toString(),
                faction.getDescription(),
                faction.getMotd(),
                faction.getTag(),
                faction.hasHome() ? faction.getWorldName() : null,
                faction.hasHome() ? faction.getHomeX() : 0,
                faction.hasHome() ? faction.getHomeY() : 0,
                faction.hasHome() ? faction.getHomeZ() : 0,
                faction.hasHome() ? faction.getHomeYaw() : 0,
                faction.hasHome() ? faction.getHomePitch() : 0,
                faction.getPower(),
                faction.getMaxPower(),
                faction.getElixir(),
                faction.isOpen(),
                faction.isPvpEnabled(),
                faction.isTntEnabled(),
                faction.getCreationTime());

        // Members
        execute("DELETE FROM faction_members WHERE faction_id = ?", faction.getFactionId().toString());
        for (UUID muid : faction.getMembers()) {
            String role = faction.isOfficer(muid) ? ROLE_OFFICER : ROLE_MEMBER;
            if (faction.isLeader(muid)) {
                role = ROLE_LEADER;
            }
            execute("INSERT INTO faction_members (faction_id, player_uuid, role) VALUES (?, ?, ?)",
                    faction.getFactionId().toString(), muid.toString(), role);
        }

        // Relations
        execute("DELETE FROM faction_relations WHERE faction_id = ?", faction.getFactionId().toString());
        for (UUID aid : faction.getAllies()) {
            execute("INSERT INTO faction_relations (faction_id, target_id, relation) VALUES (?, ?, ?)",
                    faction.getFactionId().toString(), aid.toString(), RELATION_ALLY);
        }
        for (UUID eid : faction.getEnemies()) {
            execute("INSERT INTO faction_relations (faction_id, target_id, relation) VALUES (?, ?, ?)",
                    faction.getFactionId().toString(), eid.toString(), RELATION_ENEMY);
        }
    }

    @Override
    public void deleteFaction(UUID factionId) {
        String fid = factionId.toString();
        execute("DELETE FROM factions WHERE id = ?", fid);
        execute("DELETE FROM faction_members WHERE faction_id = ?", fid);
        execute("DELETE FROM faction_relations WHERE faction_id = ? OR target_id = ?", fid, fid);
        execute("DELETE FROM faction_claims WHERE faction_id = ?", fid);
    }

    // ========== Claims ==========

    @Override
    public Map<String, UUID> loadAllClaims() {
        Map<String, UUID> map = new HashMap<>();
        String sql = "SELECT world, chunk_x, chunk_z, faction_id FROM faction_claims";
        try (Connection c = db.getConnection();
             PreparedStatement s = c.prepareStatement(sql);
             ResultSet rs = s.executeQuery()) {
            while (rs.next()) {
                String key = rs.getString("world") + ":" + rs.getInt("chunk_x") + ":" + rs.getInt("chunk_z");
                map.put(key, UUID.fromString(rs.getString("faction_id")));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load claims", e);
        }
        return map;
    }

    @Override
    public void saveClaim(String key, UUID factionId) {
        String[] parts = key.split(":", 3);
        execute("REPLACE INTO faction_claims (world, chunk_x, chunk_z, faction_id) VALUES (?, ?, ?, ?)",
                parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), factionId.toString());
    }

    @Override
    public void deleteClaim(String key) {
        String[] parts = key.split(":", 3);
        execute("DELETE FROM faction_claims WHERE world = ? AND chunk_x = ? AND chunk_z = ?",
                parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }

    @Override
    public void deleteAllFactionClaims(UUID factionId) {
        execute("DELETE FROM faction_claims WHERE faction_id = ?", factionId.toString());
    }

    // ========== Player Data ==========

    @Override
    public Collection<FactionPlayer> loadAllPlayerData() {
        Map<UUID, FactionPlayer> map = new HashMap<>();
        String sql = "SELECT * FROM player_data";
        try (Connection c = db.getConnection();
             PreparedStatement s = c.prepareStatement(sql);
             ResultSet rs = s.executeQuery()) {
            while (rs.next()) {
                FactionPlayer p = new FactionPlayer();
                p.setPlayerUuid(UUID.fromString(rs.getString("uuid")));
                p.setPower(rs.getDouble("power"));
                p.setMaxPower(rs.getDouble("max_power"));
                p.setKills(rs.getInt("kills"));
                p.setDeaths(rs.getInt("deaths"));
                p.setLastLoginTime(rs.getLong("last_login"));
                p.setLastLogoutTime(rs.getLong("last_logout"));
                String fid = rs.getString("faction");
                if (fid != null && !fid.isEmpty()) {
                    p.setFactionId(UUID.fromString(fid));
                }
                map.put(p.getPlayerUuid(), p);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load player data", e);
        }
        return map.values();
    }

    @Override
    public void savePlayerData(FactionPlayer data) {
        execute("REPLACE INTO player_data (uuid, power, max_power, kills, deaths, faction, last_login, last_logout) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                data.getPlayerUuid().toString(),
                data.getPower(),
                data.getMaxPower(),
                data.getKills(),
                data.getDeaths(),
                data.getFactionId() != null ? data.getFactionId().toString() : null,
                data.getLastLoginTime(),
                data.getLastLogoutTime());
    }

    @Override
    public void deletePlayerData(UUID playerUuid) {
        execute("DELETE FROM player_data WHERE uuid = ?", playerUuid.toString());
    }

    // ========== Player Names ==========

    @Override
    public Map<UUID, String> loadAllNames() {
        Map<UUID, String> map = new HashMap<>();
        String sql = "SELECT uuid, name FROM player_names";
        try (Connection c = db.getConnection();
             PreparedStatement s = c.prepareStatement(sql);
             ResultSet rs = s.executeQuery()) {
            while (rs.next()) {
                map.put(UUID.fromString(rs.getString("uuid")), rs.getString("name"));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load player names", e);
        }
        return map;
    }

    @Override
    public void saveName(UUID uuid, String name) {
        execute("REPLACE INTO player_names (uuid, name) VALUES (?, ?)",
                uuid.toString(), name);
    }

    // ========== Elixir Pending ==========

    @Override
    public Map<UUID, Double> loadPendingElixir() {
        Map<UUID, Double> map = new HashMap<>();
        String sql = "SELECT player_uuid, amount FROM elixir_pending";
        try (Connection c = db.getConnection();
             PreparedStatement s = c.prepareStatement(sql);
             ResultSet rs = s.executeQuery()) {
            while (rs.next()) {
                map.put(UUID.fromString(rs.getString("player_uuid")), rs.getDouble("amount"));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load pending elixir", e);
        }
        return map;
    }

    @Override
    public void savePendingElixir(UUID playerUuid, double amount) {
        execute("REPLACE INTO elixir_pending (player_uuid, amount) VALUES (?, ?)",
                playerUuid.toString(), amount);
    }

    @Override
    public void deletePendingElixir(UUID playerUuid) {
        execute("DELETE FROM elixir_pending WHERE player_uuid = ?", playerUuid.toString());
    }

    // ========== Helpers ==========

    // Read a string column, mapping SQL NULL to an empty string so callers never
    // store null. Reads the column once instead of the getString-twice ternary.
    private static String getStringOrEmpty(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value != null ? value : "";
    }

    private void execute(String sql, Object... params) {
        try (Connection c = db.getConnection();
             PreparedStatement s = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                s.setObject(i + 1, params[i]);
            }
            s.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "SQL error: " + sql, e);
        }
    }
}