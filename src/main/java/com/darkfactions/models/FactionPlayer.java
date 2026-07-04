package com.darkfactions.models;

// FactionPlayer.java - Per-player data (kept separate from Faction so we can
// track individual stats like power, kills and deaths independently).

import java.util.UUID;

public class FactionPlayer {

    // All fields are volatile: the main thread is the only writer, but the
    // async SaveQueue worker reads them mid-save. Without volatile there is no
    // happens-before edge, so the persisted row could hold stale or (for the
    // 64-bit double/long fields) torn values.

    // The player's Minecraft UUID
    private volatile UUID playerUuid;

    // Player's individual power (contributes to faction power)
    private volatile double power;

    // Player's maximum power cap
    private volatile double maxPower;

    // Track kills and deaths for power calculations
    private volatile int kills;
    private volatile int deaths;

    // When the player last logged in (for power regen)
    private volatile long lastLoginTime;

    // When the player last logged out (for power decay offline)
    private volatile long lastLogoutTime;

    public FactionPlayer(UUID playerUuid) {
        this.playerUuid = playerUuid;
        this.power = 10.0; // Default starting power (like old factions)
        this.maxPower = 10.0; // Default max
        this.kills = 0;
        this.deaths = 0;
        this.lastLoginTime = System.currentTimeMillis();
        this.lastLogoutTime = 0;
    }

    // No-arg constructor used when deserializing saved data (see PowerManager.loadPowerData),
    // where fields are populated via setters rather than the starting defaults above.
    public FactionPlayer() {
    }

    // ==========================================
    // Getters and Setters
    // ==========================================

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public void setPlayerUuid(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    public double getPower() {
        return power;
    }

    public void setPower(double power) {
        this.power = power;
    }

    public double getMaxPower() {
        return maxPower;
    }

    public void setMaxPower(double maxPower) {
        this.maxPower = maxPower;
    }

    public int getKills() {
        return kills;
    }

    public void setKills(int kills) {
        this.kills = kills;
    }

    public int getDeaths() {
        return deaths;
    }

    public void setDeaths(int deaths) {
        this.deaths = deaths;
    }

    public long getLastLoginTime() {
        return lastLoginTime;
    }

    public void setLastLoginTime(long lastLoginTime) {
        this.lastLoginTime = lastLoginTime;
    }

    public long getLastLogoutTime() {
        return lastLogoutTime;
    }

    public void setLastLogoutTime(long lastLogoutTime) {
        this.lastLogoutTime = lastLogoutTime;
    }
}