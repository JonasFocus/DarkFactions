package com.darkfactions.managers;

// ==========================================
// FactionManager.java
// Handles ALL faction CRUD, invites, save/load
// ALL limits come from ConfigManager
// ==========================================

import com.darkfactions.DarkFactions;
import com.darkfactions.models.Faction;
import com.darkfactions.utils.ConfigManager;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FactionManager {

    private final DarkFactions plugin;
    private final Map<UUID, Faction> factions;
    private final Map<UUID, UUID> playerFactionMap;
    private final Map<UUID, List<UUID>> pendingInvites;
    private final File dataFile;

    public FactionManager(DarkFactions plugin) {
        this.plugin = plugin;
        this.factions = new HashMap<>();
        this.playerFactionMap = new HashMap<>();
        this.pendingInvites = new HashMap<>();
        this.dataFile = new File(plugin.getDataFolder(), "factions.yml");
    }

    // ==========================================
    // CRUD
    // ==========================================

    public Faction createFaction(String name, UUID leaderUuid) {
        if (getFactionByName(name) != null) return null;
        if (getPlayerFaction(leaderUuid) != null) return null;

        ConfigManager cfg = plugin.getConfigManager();

        // Check max factions per player
        int maxFactions = cfg.getMaxFactionsPerPlayer();

        Faction faction = new Faction(name, leaderUuid);
        faction.setPower(cfg.getFactionStartingPower());
        faction.setMaxPower(cfg.getFactionStartingMaxPower());
        faction.setOpen(cfg.isDefaultOpen());
        faction.setPvpEnabled(cfg.isDefaultPvp());
        faction.setTntEnabled(cfg.isDefaultTnt());

        factions.put(faction.getFactionId(), faction);
        playerFactionMap.put(leaderUuid, faction.getFactionId());

        return faction;
    }

    public boolean deleteFaction(UUID factionId) {
        Faction faction = factions.get(factionId);
        if (faction == null) return false;

        plugin.getClaimManager().removeAllFactionClaims(factionId);

        for (UUID memberUuid : faction.getMembers()) {
            playerFactionMap.remove(memberUuid);
        }
        playerFactionMap.remove(faction.getLeaderUuid());

        pendingInvites.values().forEach(list -> list.remove(factionId));
        factions.remove(factionId);

        return true;
    }

    public Faction getFaction(UUID factionId) {
        return factions.get(factionId);
    }

    public Faction getFactionByName(String name) {
        for (Faction faction : factions.values()) {
            if (faction.getName().equalsIgnoreCase(name)) return faction;
        }
        return null;
    }

    public Faction getPlayerFaction(UUID playerUuid) {
        UUID factionId = playerFactionMap.get(playerUuid);
        return factionId == null ? null : factions.get(factionId);
    }

    public boolean isFactionNameTaken(String name) {
        return getFactionByName(name) != null;
    }

    // ==========================================
    // Rename
    // ==========================================

    public boolean renameFaction(UUID factionId, String newName) {
        Faction faction = factions.get(factionId);
        if (faction == null) return false;
        if (isFactionNameTaken(newName) && !faction.getName().equalsIgnoreCase(newName)) return false;
        faction.setName(newName);
        return true;
    }

    // ==========================================
    // Members
    // ==========================================

    public boolean addPlayerToFaction(UUID playerUuid, UUID factionId) {
        if (playerFactionMap.containsKey(playerUuid)) return false;

        Faction faction = factions.get(factionId);
        if (faction == null) return false;

        if (faction.getMemberCount() >= plugin.getConfigManager().getMaxMembers()) return false;

        faction.addMember(playerUuid);
        playerFactionMap.put(playerUuid, factionId);
        pendingInvites.remove(playerUuid);

        return true;
    }

    public boolean removePlayerFromFaction(UUID playerUuid) {
        Faction faction = getPlayerFaction(playerUuid);
        if (faction == null) return false;

        faction.removeMember(playerUuid);
        playerFactionMap.remove(playerUuid);

        if (faction.getMemberCount() == 0 && plugin.getConfigManager().isAutoDisbandEmpty()) {
            plugin.getLogger().info("Auto-disbanding empty faction: " + faction.getName());
            deleteFaction(faction.getFactionId());
        }

        return true;
    }

    public boolean promotePlayer(UUID playerUuid, UUID factionId) {
        Faction faction = factions.get(factionId);
        if (faction == null) return false;
        if (!faction.isMember(playerUuid)) return false;
        if (faction.isOfficer(playerUuid)) return false;

        // Check max officers limit
        int maxOfficers = plugin.getConfigManager().getMaxOfficers();
        if (maxOfficers > 0 && faction.getOfficers().size() >= maxOfficers) return false;

        faction.promoteToOfficer(playerUuid);
        return true;
    }

    public boolean demotePlayer(UUID playerUuid, UUID factionId) {
        Faction faction = factions.get(factionId);
        if (faction == null) return false;
        if (!faction.isOfficer(playerUuid)) return false;
        faction.demoteFromOfficer(playerUuid);
        return true;
    }

    public boolean transferLeadership(UUID factionId, UUID newLeaderUuid) {
        Faction faction = factions.get(factionId);
        if (faction == null) return false;
        if (!faction.isMember(newLeaderUuid)) return false;
        faction.setLeaderUuid(newLeaderUuid);
        return true;
    }

    // ==========================================
    // Invite System
    // ==========================================

    public boolean sendInvite(UUID inviterUuid, UUID factionId, UUID targetUuid) {
        if (playerFactionMap.containsKey(targetUuid)) return false;
        List<UUID> invites = pendingInvites.computeIfAbsent(targetUuid, k -> new ArrayList<>());
        if (!invites.contains(factionId)) invites.add(factionId);
        return true;
    }

    public boolean acceptInvite(UUID playerUuid, UUID factionId) {
        List<UUID> invites = pendingInvites.get(playerUuid);
        if (invites == null || !invites.contains(factionId)) return false;
        return addPlayerToFaction(playerUuid, factionId);
    }

    public boolean denyInvite(UUID playerUuid, UUID factionId) {
        List<UUID> invites = pendingInvites.get(playerUuid);
        if (invites == null) return false;
        return invites.remove(factionId);
    }

    public List<UUID> getPendingInvites(UUID playerUuid) {
        return pendingInvites.getOrDefault(playerUuid, new ArrayList<>());
    }

    public boolean hasPendingInvite(UUID playerUuid, UUID factionId) {
        List<UUID> invites = pendingInvites.get(playerUuid);
        return invites != null && invites.contains(factionId);
    }

    // ==========================================
    // Home
    // ==========================================

    public void setFactionHome(UUID factionId, Location location) {
        Faction faction = factions.get(factionId);
        if (faction == null) return;
        faction.setWorldName(location.getWorld().getName());
        faction.setHomeX(location.getX());
        faction.setHomeY(location.getY());
        faction.setHomeZ(location.getZ());
        faction.setHomeYaw(location.getYaw());
        faction.setHomePitch(location.getPitch());
    }

    public Location getFactionHome(UUID factionId) {
        Faction faction = factions.get(factionId);
        if (faction == null || !faction.hasHome()) return null;
        return new Location(
                plugin.getServer().getWorld(faction.getWorldName()),
                faction.getHomeX(), faction.getHomeY(), faction.getHomeZ(),
                faction.getHomeYaw(), faction.getHomePitch()
        );
    }

    // ==========================================
    // Listing / Sorting
    // ==========================================

    public List<Faction> getAllFactions() {
        return new ArrayList<>(factions.values());
    }

    public int getFactionCount() { return factions.size(); }

    public List<Faction> getTopFactionsByPower(int limit) {
        List<Faction> sorted = new ArrayList<>(factions.values());
        sorted.sort((a, b) -> Double.compare(b.getPower(), a.getPower()));
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }

    public List<Faction> getTopFactionsByElixir(int limit) {
        List<Faction> sorted = new ArrayList<>(factions.values());
        sorted.sort((a, b) -> Double.compare(b.getElixir(), a.getElixir()));
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }

    public List<Faction> getTopFactionsByMembers(int limit) {
        List<Faction> sorted = new ArrayList<>(factions.values());
        sorted.sort((a, b) -> Integer.compare(b.getMemberCount(), a.getMemberCount()));
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }

    // ==========================================
    // Save/Load
    // ==========================================

    public void saveFactions() {
        FileConfiguration config = new YamlConfiguration();

        for (Faction faction : factions.values()) {
            String path = "factions." + faction.getFactionId().toString();

            config.set(path + ".name", faction.getName());
            config.set(path + ".leader", faction.getLeaderUuid().toString());
            config.set(path + ".power", faction.getPower());
            config.set(path + ".maxPower", faction.getMaxPower());
            config.set(path + ".elixir", faction.getElixir());
            config.set(path + ".open", faction.isOpen());
            config.set(path + ".creationTime", faction.getCreationTime());
            config.set(path + ".motd", faction.getMotd());
            config.set(path + ".description", faction.getDescription());
            config.set(path + ".tag", faction.getTag());
            config.set(path + ".pvpEnabled", faction.isPvpEnabled());
            config.set(path + ".tntEnabled", faction.isTntEnabled());

            List<String> memberStrings = new ArrayList<>();
            for (UUID uuid : faction.getMembers()) memberStrings.add(uuid.toString());
            config.set(path + ".members", memberStrings);

            List<String> officerStrings = new ArrayList<>();
            for (UUID uuid : faction.getOfficers()) officerStrings.add(uuid.toString());
            config.set(path + ".officers", officerStrings);

            List<String> enemyStrings = new ArrayList<>();
            for (UUID uuid : faction.getEnemies()) enemyStrings.add(uuid.toString());
            config.set(path + ".enemies", enemyStrings);

            List<String> allyStrings = new ArrayList<>();
            for (UUID uuid : faction.getAllies()) allyStrings.add(uuid.toString());
            config.set(path + ".allies", allyStrings);

            if (faction.hasHome()) {
                config.set(path + ".home.world", faction.getWorldName());
                config.set(path + ".home.x", faction.getHomeX());
                config.set(path + ".home.y", faction.getHomeY());
                config.set(path + ".home.z", faction.getHomeZ());
                config.set(path + ".home.yaw", faction.getHomeYaw());
                config.set(path + ".home.pitch", faction.getHomePitch());
            }
        }

        try {
            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save faction data! " + e.getMessage());
        }
    }

    public void loadFactions() {
        if (!dataFile.exists()) return;

        FileConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection factionSection = config.getConfigurationSection("factions");
        if (factionSection == null) return;

        for (String key : factionSection.getKeys(false)) {
            try {
                Faction faction = new Faction();
                faction.setFactionId(UUID.fromString(key));
                faction.setName(config.getString("factions." + key + ".name"));
                faction.setLeaderUuid(UUID.fromString(config.getString("factions." + key + ".leader")));
                faction.setPower(config.getDouble("factions." + key + ".power", plugin.getConfigManager().getFactionStartingPower()));
                faction.setMaxPower(config.getDouble("factions." + key + ".maxPower", plugin.getConfigManager().getFactionStartingMaxPower()));
                faction.setElixir(config.getDouble("factions." + key + ".elixir", 0.0));
                faction.setOpen(config.getBoolean("factions." + key + ".open", plugin.getConfigManager().isDefaultOpen()));
                faction.setCreationTime(config.getLong("factions." + key + ".creationTime", System.currentTimeMillis()));
                faction.setMotd(config.getString("factions." + key + ".motd", ""));
                faction.setDescription(config.getString("factions." + key + ".description", ""));
                faction.setTag(config.getString("factions." + key + ".tag", ""));
                faction.setPvpEnabled(config.getBoolean("factions." + key + ".pvpEnabled", plugin.getConfigManager().isDefaultPvp()));
                faction.setTntEnabled(config.getBoolean("factions." + key + ".tntEnabled", plugin.getConfigManager().isDefaultTnt()));

                for (String uuidStr : config.getStringList("factions." + key + ".members")) {
                    faction.getMembers().add(UUID.fromString(uuidStr));
                }
                for (String uuidStr : config.getStringList("factions." + key + ".officers")) {
                    faction.getOfficers().add(UUID.fromString(uuidStr));
                }
                for (String uuidStr : config.getStringList("factions." + key + ".enemies")) {
                    faction.getEnemies().add(UUID.fromString(uuidStr));
                }
                for (String uuidStr : config.getStringList("factions." + key + ".allies")) {
                    faction.getAllies().add(UUID.fromString(uuidStr));
                }

                if (config.contains("factions." + key + ".home.world")) {
                    faction.setWorldName(config.getString("factions." + key + ".home.world"));
                    faction.setHomeX(config.getDouble("factions." + key + ".home.x"));
                    faction.setHomeY(config.getDouble("factions." + key + ".home.y"));
                    faction.setHomeZ(config.getDouble("factions." + key + ".home.z"));
                    faction.setHomeYaw((float) config.getDouble("factions." + key + ".home.yaw"));
                    faction.setHomePitch((float) config.getDouble("factions." + key + ".home.pitch"));
                }

                factions.put(faction.getFactionId(), faction);
                for (UUID memberUuid : faction.getMembers()) {
                    playerFactionMap.put(memberUuid, faction.getFactionId());
                }

                plugin.getLogger().info("Loaded faction: " + faction.getName());

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to load faction with key: " + key);
            }
        }
    }
}