package com.darkfactions.storage;

import com.darkfactions.DarkFactions;
import com.darkfactions.models.Faction;
import com.darkfactions.models.FactionPlayer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SqlStore implements DataStore {

    // Role values stored in faction_members.role
    private static final String ROLE_LEADER = "LEADER";
    private static final String ROLE_OFFICER = "OFFICER";
    private static final String ROLE_MEMBER = "MEMBER";

    // Relation values stored in faction_relations.relation
    private static final String RELATION_ALLY = "ALLY";
    private static final String RELATION_ENEMY = "ENEMY";

    private final Logger logger;
    private final DatabaseManager db;

    public SqlStore(DarkFactions plugin, DatabaseManager db) {
        this(plugin.getLogger(), db);
    }

    /** Test-friendly constructor that does not require a live Bukkit plugin. */
    public SqlStore(Logger logger, DatabaseManager db) {
        this.logger = logger;
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
                + "bonus_power DOUBLE DEFAULT 0,"
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
                // Legacy, unused: faction_members is the sole source of truth for membership.
                // Left in place rather than dropped to avoid a schema migration.
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

        execute("CREATE TABLE IF NOT EXISTS elixir_daily_claims ("
                + "player_uuid VARCHAR(36) PRIMARY KEY,"
                + "claimed_at BIGINT NOT NULL"
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
            logger.log(Level.SEVERE, "Failed to read schema version", e);
            return 0;
        }
    }

    @Override
    public void setSchemaVersion(int version) {
        execute("INSERT INTO schema_version (version, applied_at) VALUES (?, ?)",
                version, System.currentTimeMillis());
    }

    /**
     * Schema v2: add {@code bonus_power} for admin/shop boosts. The legacy {@code power}
     * column is copied into {@code bonus_power} once, then ignored on read/write.
     */
    @Override
    public void migrateSchema(int fromVersion) {
        if (fromVersion >= 2) {
            return;
        }
        if (!columnExists("factions", "bonus_power")) {
            execute("ALTER TABLE factions ADD COLUMN bonus_power DOUBLE DEFAULT 0");
            execute("UPDATE factions SET bonus_power = power");
            logger.info("Added factions.bonus_power and copied legacy power values.");
        }
    }

    private boolean columnExists(String table, String column) {
        try (Connection c = db.getConnection()) {
            var meta = c.getMetaData();
            try (ResultSet rs = meta.getColumns(null, null, table, column)) {
                if (rs.next()) {
                    return true;
                }
            }
            // SQLite stores unquoted table names lowercase; retry if needed.
            try (ResultSet rs = meta.getColumns(null, null, table.toLowerCase(), column)) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Could not inspect column " + table + "." + column, e);
            return false;
        }
    }

    private static boolean hasColumn(ResultSet rs, String column) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            if (column.equalsIgnoreCase(meta.getColumnLabel(i))) {
                return true;
            }
        }
        return false;
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
                if (hasColumn(rs, "bonus_power")) {
                    f.setBonusPower(rs.getDouble("bonus_power"));
                } else {
                    // Pre-v2 database before migrateSchema runs — load legacy total into bonus for migration.
                    f.setBonusPower(rs.getDouble("power"));
                }
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
            logger.log(Level.SEVERE, "Failed to load factions", e);
            throw new StorageException("Failed to load factions", e);
        }

        // Load members for every faction in one query, then group in memory, so
        // startup cost is two extra queries rather than two per faction.
        Map<UUID, List<UUID>> membersByFaction = new HashMap<>();
        Map<UUID, List<UUID>> officersByFaction = new HashMap<>();
        try (Connection c = db.getConnection();
             PreparedStatement s = c.prepareStatement("SELECT faction_id, player_uuid, role FROM faction_members");
             ResultSet rs = s.executeQuery()) {
            while (rs.next()) {
                UUID fid = UUID.fromString(rs.getString("faction_id"));
                if (!result.containsKey(fid)) {
                    continue; // orphan row with no parent faction
                }
                UUID puid = UUID.fromString(rs.getString("player_uuid"));
                if (ROLE_OFFICER.equals(rs.getString("role"))) {
                    officersByFaction.computeIfAbsent(fid, k -> new ArrayList<>()).add(puid);
                } else {
                    membersByFaction.computeIfAbsent(fid, k -> new ArrayList<>()).add(puid);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load faction members", e);
            throw new StorageException("Failed to load faction members", e);
        }

        // Load relations for every faction in one query, then group in memory.
        Map<UUID, List<UUID>> alliesByFaction = new HashMap<>();
        Map<UUID, List<UUID>> enemiesByFaction = new HashMap<>();
        try (Connection c = db.getConnection();
             PreparedStatement s = c.prepareStatement("SELECT faction_id, target_id, relation FROM faction_relations");
             ResultSet rs = s.executeQuery()) {
            while (rs.next()) {
                UUID fid = UUID.fromString(rs.getString("faction_id"));
                if (!result.containsKey(fid)) {
                    continue; // orphan row with no parent faction
                }
                UUID tid = UUID.fromString(rs.getString("target_id"));
                String rel = rs.getString("relation");
                if (RELATION_ALLY.equals(rel)) {
                    alliesByFaction.computeIfAbsent(fid, k -> new ArrayList<>()).add(tid);
                } else if (RELATION_ENEMY.equals(rel)) {
                    enemiesByFaction.computeIfAbsent(fid, k -> new ArrayList<>()).add(tid);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load faction relations", e);
            throw new StorageException("Failed to load faction relations", e);
        }

        for (Faction f : result.values()) {
            UUID fid = f.getFactionId();
            f.setMembers(membersByFaction.getOrDefault(fid, new ArrayList<>()));
            f.setOfficers(officersByFaction.getOrDefault(fid, new ArrayList<>()));
            f.setAllies(alliesByFaction.getOrDefault(fid, new ArrayList<>()));
            f.setEnemies(enemiesByFaction.getOrDefault(fid, new ArrayList<>()));
        }

        return result.values();
    }

    @Override
    public void saveFaction(Faction faction) {
        String fid = faction.getFactionId().toString();
        String factionSql = "REPLACE INTO factions (id, name, leader, description, motd, tag, "
                + "home_world, home_x, home_y, home_z, home_yaw, home_pitch, "
                + "power, bonus_power, max_power, elixir, open, pvp_enabled, tnt_enabled, created) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        // The faction row, its members and its relations are written together in a
        // single transaction so a mid-save failure can't leave a faction with stale
        // or missing members/relations. All statements share one pooled connection.
        try (Connection c = db.getConnection()) {
            boolean previousAutoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                try (PreparedStatement s = c.prepareStatement(factionSql)) {
                    bind(s,
                            fid,
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
                            0.0, // legacy power column — unused; kept for backward-compatible schema
                            faction.getBonusPower(),
                            faction.getMaxPower(),
                            faction.getElixir(),
                            faction.isOpen(),
                            faction.isPvpEnabled(),
                            faction.isTntEnabled(),
                            faction.getCreationTime());
                    s.executeUpdate();
                }

                // Members: clear and rewrite from the current in-memory state.
                try (PreparedStatement s = c.prepareStatement(
                        "DELETE FROM faction_members WHERE faction_id = ?")) {
                    s.setString(1, fid);
                    s.executeUpdate();
                }
                try (PreparedStatement s = c.prepareStatement(
                        "INSERT INTO faction_members (faction_id, player_uuid, role) VALUES (?, ?, ?)")) {
                    for (UUID muid : faction.getMembers()) {
                        String role = faction.isLeader(muid) ? ROLE_LEADER
                                : faction.isOfficer(muid) ? ROLE_OFFICER : ROLE_MEMBER;
                        bind(s, fid, muid.toString(), role);
                        s.addBatch();
                    }
                    s.executeBatch();
                }

                // Relations: clear and rewrite allies then enemies.
                try (PreparedStatement s = c.prepareStatement(
                        "DELETE FROM faction_relations WHERE faction_id = ?")) {
                    s.setString(1, fid);
                    s.executeUpdate();
                }
                try (PreparedStatement s = c.prepareStatement(
                        "INSERT INTO faction_relations (faction_id, target_id, relation) VALUES (?, ?, ?)")) {
                    Set<UUID> allyIds = new HashSet<>(faction.getAllies());
                    for (UUID aid : allyIds) {
                        bind(s, fid, aid.toString(), RELATION_ALLY);
                        s.addBatch();
                    }
                    for (UUID eid : faction.getEnemies()) {
                        // faction_relations is keyed by (faction_id, target_id), so a
                        // target can hold only one relation. If the command layer left
                        // a target in both sets, ally wins (matching the user's intent
                        // and the old per-statement behavior) and the duplicate enemy
                        // row is skipped so the batch and its transaction don't fail.
                        if (!allyIds.contains(eid)) {
                            bind(s, fid, eid.toString(), RELATION_ENEMY);
                            s.addBatch();
                        }
                    }
                    s.executeBatch();
                }

                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to save faction " + faction.getName(), e);
            throw new StorageException("Failed to save faction " + faction.getName(), e);
        }
    }

    @Override
    public void deleteFaction(UUID factionId) {
        String fid = factionId.toString();
        // All four deletes run in one transaction so a mid-delete failure can't
        // leave orphaned member/relation/claim rows pointing at a gone faction.
        try (Connection c = db.getConnection()) {
            boolean previousAutoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                try (PreparedStatement s = c.prepareStatement("DELETE FROM factions WHERE id = ?")) {
                    s.setString(1, fid);
                    s.executeUpdate();
                }
                try (PreparedStatement s = c.prepareStatement("DELETE FROM faction_members WHERE faction_id = ?")) {
                    s.setString(1, fid);
                    s.executeUpdate();
                }
                try (PreparedStatement s = c.prepareStatement(
                        "DELETE FROM faction_relations WHERE faction_id = ? OR target_id = ?")) {
                    s.setString(1, fid);
                    s.setString(2, fid);
                    s.executeUpdate();
                }
                try (PreparedStatement s = c.prepareStatement("DELETE FROM faction_claims WHERE faction_id = ?")) {
                    s.setString(1, fid);
                    s.executeUpdate();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to delete faction " + fid, e);
            throw new StorageException("Failed to delete faction " + fid, e);
        }
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
            logger.log(Level.SEVERE, "Failed to load claims", e);
            throw new StorageException("Failed to load claims", e);
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
                map.put(p.getPlayerUuid(), p);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load player data", e);
            throw new StorageException("Failed to load player data", e);
        }
        return map.values();
    }

    @Override
    public void savePlayerData(FactionPlayer data) {
        execute("REPLACE INTO player_data (uuid, power, max_power, kills, deaths, last_login, last_logout) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                data.getPlayerUuid().toString(),
                data.getPower(),
                data.getMaxPower(),
                data.getKills(),
                data.getDeaths(),
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
            logger.log(Level.SEVERE, "Failed to load player names", e);
            throw new StorageException("Failed to load player names", e);
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
            logger.log(Level.SEVERE, "Failed to load pending elixir", e);
            throw new StorageException("Failed to load pending elixir", e);
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

    // ========== Elixir Daily Claims ==========

    @Override
    public Map<UUID, Long> loadLastDailyClaims() {
        Map<UUID, Long> map = new HashMap<>();
        String sql = "SELECT player_uuid, claimed_at FROM elixir_daily_claims";
        try (Connection c = db.getConnection();
             PreparedStatement s = c.prepareStatement(sql);
             ResultSet rs = s.executeQuery()) {
            while (rs.next()) {
                map.put(UUID.fromString(rs.getString("player_uuid")), rs.getLong("claimed_at"));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load daily elixir claims", e);
            throw new StorageException("Failed to load daily elixir claims", e);
        }
        return map;
    }

    @Override
    public void saveLastDailyClaim(UUID playerUuid, long epochMillis) {
        execute("REPLACE INTO elixir_daily_claims (player_uuid, claimed_at) VALUES (?, ?)",
                playerUuid.toString(), epochMillis);
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
            bind(s, params);
            s.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "SQL error: " + sql, e);
            throw new StorageException("SQL error: " + sql, e);
        }
    }

    // Bind positional parameters (1-based) onto a prepared statement.
    private static void bind(PreparedStatement s, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            s.setObject(i + 1, params[i]);
        }
    }
}