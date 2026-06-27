package com.darkfactions.models;

// FactionPlayer.java - Per-player data (kept separate from Faction so we can
// track individual stats like power, kills and deaths independently).

import java.util.UUID;

public class FactionPlayer {

    // The player's Minecraft UUID
    private UUID playerUuid;

    // The faction this player belongs to (null if no faction)
    private UUID factionId;

    // Player's individual power (contributes to faction power)
    private double power;

    // Player's maximum power cap
    private double maxPower;

    // Track kills and deaths for power calculations
    private int kills;
    private int deaths;

    // When the player last logged in (for power regen)
    private long lastLoginTime;

    // When the player last logged out (for power decay offline)
    private long lastLogoutTime;

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
    // Power Methods
    //
    // These apply simple, hardcoded power changes clamped to [0, maxPower].
    // The live gameplay path runs through PowerManager/PowerRules instead, which
    // are config-driven; adjust balance there, not here.
    // ==========================================

    // Increase power over time (called every X minutes)
    public void regenPower(double amount) {
        this.power = Math.min(this.power + amount, this.maxPower);
    }

    // Lose power on death
    public void losePowerOnDeath() {
        this.power = Math.max(this.power - 1.0, 0.0); // Lose 1 power per death
    }

    // Gain power on kill
    public void gainPowerOnKill() {
        this.power = Math.min(this.power + 0.5, this.maxPower); // Gain 0.5 per kill
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

    public UUID getFactionId() {
        return factionId;
    }

    public void setFactionId(UUID factionId) {
        this.factionId = factionId;
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