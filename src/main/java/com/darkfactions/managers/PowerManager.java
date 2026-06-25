package com.darkfactions.managers;

// ==========================================
// PowerManager.java
// Manages faction power - just like the old school factions
// Players gain power over time and lose it when they die
// Power determines if a faction can be raided
// ALL values come from ConfigManager now
// ==========================================

import com.darkfactions.DarkFactions;
import com.darkfactions.models.Faction;
import com.darkfactions.models.FactionPlayer;
import com.darkfactions.utils.ConfigManager;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PowerManager {

    private final DarkFactions plugin;
    private final Map<UUID, FactionPlayer> playerDataMap;
    private final File dataFile;

    // Cached config values (reloaded on /f admin reload)
    private double defaultPlayerPower;
    private double maxPlayerPower;
    private int powerRegenInterval;
    private double powerRegenAmount;
    private double powerLossOnDeath;
    private double powerLossOnPveDeath;
    private double powerGainOnKill;
    private boolean offlineDecayEnabled;
    private double offlineDecayPerHour;
    private int offlineDecayMaxHours;
    private double minPlayerPower;
    private double powerGainOnMobKill;

    // Task ID for the power regen scheduler
    private int regenTaskId = -1;

    public PowerManager(DarkFactions plugin) {
        this.plugin = plugin;
        this.playerDataMap = new HashMap<>();
        this.dataFile = new File(plugin.getDataFolder(), "playerdata.yml");

        reloadConfig();
        startRegenTask();
    }

    // ==========================================
    // Load ALL values from ConfigManager
    // ==========================================
    public void reloadConfig() {
        ConfigManager cfg = plugin.getConfigManager();
        this.defaultPlayerPower = cfg.getDefaultPlayerPower();
        this.maxPlayerPower = cfg.getMaxPlayerPower();
        this.powerRegenInterval = cfg.getPowerRegenInterval();
        this.powerRegenAmount = cfg.getPowerRegenAmount();
        this.powerLossOnDeath = cfg.getPowerLossOnDeath();
        this.powerLossOnPveDeath = cfg.getPowerLossOnPveDeath();
        this.powerGainOnKill = cfg.getPowerGainOnKill();
        this.offlineDecayEnabled = cfg.isOfflineDecayEnabled();
        this.offlineDecayPerHour = cfg.getOfflineDecayPerHour();
        this.offlineDecayMaxHours = cfg.getOfflineDecayMaxHours();
        this.minPlayerPower = cfg.getMinPlayerPower();
        this.powerGainOnMobKill = cfg.getPowerGainOnMobKill();

        // Restart the regen task with new interval
        startRegenTask();
    }

    // Start or restart the power regen background task
    private void startRegenTask() {
        if (regenTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(regenTaskId);
        }

        long ticks = 20L * powerRegenInterval;
        if (ticks > 0) {
            regenTaskId = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
                regenAllPlayerPower();
            }, ticks, ticks).getTaskId();
        }
    }

    // ==========================================
    // Player Data Management
    // ==========================================

    public FactionPlayer getPlayerData(UUID playerUuid) {
        if (!playerDataMap.containsKey(playerUuid)) {
            FactionPlayer data = new FactionPlayer(playerUuid);
            data.setPower(defaultPlayerPower);
            data.setMaxPower(maxPlayerPower);
            playerDataMap.put(playerUuid, data);
        }
        return playerDataMap.get(playerUuid);
    }

    // ==========================================
    // Power Operations
    // ==========================================

    public double getPlayerPower(UUID playerUuid) {
        return getPlayerData(playerUuid).getPower();
    }

    public double getFactionPower(UUID factionId) {
        Faction faction = plugin.getFactionManager().getFaction(factionId);
        if (faction == null) return 0.0;

        double totalPower = 0.0;
        for (UUID memberUuid : faction.getMembers()) {
            totalPower += getPlayerPower(memberUuid);
        }
        return totalPower;
    }

    // Called when a player dies from PVP
    public void onPlayerDeath(UUID playerUuid) {
        FactionPlayer data = getPlayerData(playerUuid);
        double newPower = Math.max(data.getPower() - powerLossOnDeath, minPlayerPower);
        data.setPower(newPower);
        data.setDeaths(data.getDeaths() + 1);
    }

    // Called when a player dies from PVE (mobs, fall, lava, etc.)
    public void onPlayerPveDeath(UUID playerUuid) {
        if (powerLossOnPveDeath <= 0) return;
        FactionPlayer data = getPlayerData(playerUuid);
        double newPower = Math.max(data.getPower() - powerLossOnPveDeath, minPlayerPower);
        data.setPower(newPower);
        data.setDeaths(data.getDeaths() + 1);
    }

    // Called when a player kills another player
    public void onPlayerKill(UUID playerUuid) {
        FactionPlayer data = getPlayerData(playerUuid);
        double newPower = Math.min(data.getPower() + powerGainOnKill, maxPlayerPower);
        data.setPower(newPower);
        data.setKills(data.getKills() + 1);
    }

    // Called when a player kills a mob
    public void onPlayerMobKill(UUID playerUuid) {
        if (powerGainOnMobKill <= 0) return;
        FactionPlayer data = getPlayerData(playerUuid);
        double newPower = Math.min(data.getPower() + powerGainOnMobKill, maxPlayerPower);
        data.setPower(newPower);
    }

    // Called when a player's faction wins a raid
    public void onRaidWin(UUID factionId) {
        double raidPower = plugin.getConfigManager().getPowerGainOnRaidWin();
        if (raidPower <= 0) return;
        Faction faction = plugin.getFactionManager().getFaction(factionId);
        if (faction == null) return;
        faction.addPower(raidPower);
    }

    // Regenerate power for ALL players
    private void regenAllPlayerPower() {
        for (FactionPlayer data : playerDataMap.values()) {
            if (data.getPower() < data.getMaxPower()) {
                double newPower = Math.min(data.getPower() + powerRegenAmount, data.getMaxPower());
                data.setPower(newPower);
            }
        }
    }

    // Handle offline power decay for a player who just logged in
    public void handleOfflineDecay(UUID playerUuid) {
        if (!offlineDecayEnabled) return;

        FactionPlayer data = getPlayerData(playerUuid);
        long lastLogout = data.getLastLogoutTime();
        if (lastLogout == 0) return;

        long offlineMs = System.currentTimeMillis() - lastLogout;
        long offlineHours = offlineMs / (1000 * 60 * 60);

        if (offlineHours <= 0) return;

        // Cap at max decay hours
        long decayHours = Math.min(offlineHours, offlineDecayMaxHours);
        double decayAmount = decayHours * offlineDecayPerHour;

        double newPower = Math.max(data.getPower() - decayAmount, minPlayerPower);
        data.setPower(newPower);
    }

    // ==========================================
    // Power Check - can a faction be raided?
    // ==========================================

    public boolean isFactionRaidable(UUID factionId) {
        Faction faction = plugin.getFactionManager().getFaction(factionId);
        if (faction == null) return true;

        double totalPower = getFactionPower(factionId);
        int memberCount = faction.getMemberCount();

        return memberCount > 0 && totalPower < (memberCount * 0.5);
    }

    // ==========================================
    // Save/Load
    // ==========================================

    public void savePowerData() {
        FileConfiguration config = new YamlConfiguration();

        for (Map.Entry<UUID, FactionPlayer> entry : playerDataMap.entrySet()) {
            String path = "players." + entry.getKey().toString();
            FactionPlayer data = entry.getValue();

            config.set(path + ".power", data.getPower());
            config.set(path + ".maxPower", data.getMaxPower());
            config.set(path + ".kills", data.getKills());
            config.set(path + ".deaths", data.getDeaths());
            config.set(path + ".lastLogin", data.getLastLoginTime());
            config.set(path + ".lastLogout", data.getLastLogoutTime());

            if (data.getFactionId() != null) {
                config.set(path + ".faction", data.getFactionId().toString());
            }
        }

        try {
            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save player power data! " + e.getMessage());
        }
    }

    public void loadPowerData() {
        if (!dataFile.exists()) return;

        FileConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        if (!config.contains("players")) return;

        for (String key : config.getConfigurationSection("players").getKeys(false)) {
            try {
                FactionPlayer data = new FactionPlayer();
                UUID playerUuid = UUID.fromString(key);
                data.setPlayerUuid(playerUuid);
                data.setPower(config.getDouble("players." + key + ".power", defaultPlayerPower));
                data.setMaxPower(config.getDouble("players." + key + ".maxPower", maxPlayerPower));
                data.setKills(config.getInt("players." + key + ".kills", 0));
                data.setDeaths(config.getInt("players." + key + ".deaths", 0));
                data.setLastLoginTime(config.getLong("players." + key + ".lastLogin", System.currentTimeMillis()));
                data.setLastLogoutTime(config.getLong("players." + key + ".lastLogout", 0));

                if (config.contains("players." + key + ".faction")) {
                    data.setFactionId(UUID.fromString(config.getString("players." + key + ".faction")));
                }

                playerDataMap.put(playerUuid, data);

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to load player data for key: " + key);
            }
        }

        plugin.getLogger().info("Loaded power data for " + playerDataMap.size() + " players!");
    }
}