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
import com.darkfactions.storage.DataStore;
import com.darkfactions.storage.SaveQueue;
import com.darkfactions.utils.ConfigManager;
import com.darkfactions.utils.PowerRules;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class PowerManager {

    private final DarkFactions plugin;
    private final Map<UUID, FactionPlayer> playerDataMap;
    private final AtomicBoolean dirty;

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

    // Per-member power threshold below which a faction becomes raidable.
    private static final double RAIDABLE_POWER_PER_MEMBER = 0.5;

    // Task ID for the power regen scheduler
    private int regenTaskId = -1;

    public PowerManager(DarkFactions plugin) {
        this.plugin = plugin;
        this.playerDataMap = new ConcurrentHashMap<>();
        this.dirty = new AtomicBoolean(false);
        reloadConfig();
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
            regenTaskId = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                regenAllPlayerPower();
            }, ticks, ticks).getTaskId();
        }
    }

    // ==========================================
    // Player Data Management
    // ==========================================

    public FactionPlayer getPlayerData(UUID playerUuid) {
        return playerDataMap.computeIfAbsent(playerUuid, uuid -> {
            FactionPlayer data = new FactionPlayer(uuid);
            data.setPower(defaultPlayerPower);
            data.setMaxPower(maxPlayerPower);
            return data;
        });
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
        data.setPower(PowerRules.applyLoss(data.getPower(), powerLossOnDeath, minPlayerPower));
        data.setDeaths(data.getDeaths() + 1);
        dirty.set(true);
    }

    // Called when a player dies from PVE (mobs, fall, lava, etc.)
    public void onPlayerPveDeath(UUID playerUuid) {
        if (powerLossOnPveDeath <= 0) return;
        FactionPlayer data = getPlayerData(playerUuid);
        data.setPower(PowerRules.applyLoss(data.getPower(), powerLossOnPveDeath, minPlayerPower));
        data.setDeaths(data.getDeaths() + 1);
        dirty.set(true);
    }

    // Called when a player kills another player
    public void onPlayerKill(UUID playerUuid) {
        FactionPlayer data = getPlayerData(playerUuid);
        data.setPower(PowerRules.applyGain(data.getPower(), powerGainOnKill, maxPlayerPower));
        data.setKills(data.getKills() + 1);
        dirty.set(true);
    }

    // Called when a player kills a mob
    public void onPlayerMobKill(UUID playerUuid) {
        if (powerGainOnMobKill <= 0) return;
        FactionPlayer data = getPlayerData(playerUuid);
        data.setPower(PowerRules.applyGain(data.getPower(), powerGainOnMobKill, maxPlayerPower));
        dirty.set(true);
    }

    // Called when a player's faction wins a raid
    public void onRaidWin(UUID factionId) {
        double raidPower = plugin.getConfigManager().getPowerGainOnRaidWin();
        if (raidPower <= 0) return;
        Faction faction = plugin.getFactionManager().getFaction(factionId);
        if (faction == null) return;
        faction.addPower(raidPower);
        plugin.getFactionManager().markDirty();
    }

    // Regenerate power for ALL players
    private void regenAllPlayerPower() {
        for (FactionPlayer data : playerDataMap.values()) {
            Player player = plugin.getServer().getPlayer(data.getPlayerUuid());
            if (player == null || !player.isOnline()) continue;
            if (data.getPower() < data.getMaxPower()) {
                data.setPower(PowerRules.applyGain(data.getPower(), powerRegenAmount, data.getMaxPower()));
                dirty.set(true);
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

        data.setPower(PowerRules.applyOfflineDecay(data.getPower(), offlineHours,
                offlineDecayMaxHours, offlineDecayPerHour, minPlayerPower));
        dirty.set(true);
    }

    // ==========================================
    // Power Check - can a faction be raided?
    // ==========================================

    public boolean isFactionRaidable(UUID factionId) {
        Faction faction = plugin.getFactionManager().getFaction(factionId);
        if (faction == null) return true;

        double totalPower = getFactionPower(factionId);
        int memberCount = faction.getMemberCount();

        return PowerRules.isRaidable(totalPower, memberCount, RAIDABLE_POWER_PER_MEMBER);
    }

    // ==========================================
    // Save/Load via DataStore
    // ==========================================

    public void loadFromStore(DataStore store) {
        for (FactionPlayer data : store.loadAllPlayerData()) {
            playerDataMap.put(data.getPlayerUuid(), data);
        }
        plugin.getLogger().info("Loaded power data for " + playerDataMap.size() + " players!");
    }

    public void saveToStoreAsync(SaveQueue queue) {
        if (!dirty.getAndSet(false)) return;
        queue.submit(() -> {
            DataStore store = queue.store();
            for (FactionPlayer data : playerDataMap.values()) {
                store.savePlayerData(data);
            }
        });
    }
}