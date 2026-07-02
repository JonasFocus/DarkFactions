package com.darkfactions.utils;

// ==========================================
// ConfigManager.java
// Central, typed accessor for config.yml. Values are read on demand
// (not cached), so changes take effect after the next reload().
// All managers should use this instead of reading config directly.
// ==========================================

import com.darkfactions.DarkFactions;
import com.darkfactions.storage.DatabaseManager;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public class ConfigManager {

    private final DarkFactions plugin;
    private FileConfiguration config;

    public ConfigManager(DarkFactions plugin) {
        this.plugin = plugin;
        reload();
    }

    // Re-reads config.yml from disk and refreshes our reference.
    // Called when /f admin reload is used.
    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    // Reads a string and translates '&' color codes to the section sign.
    // def is non-null at every call site, so getString never returns null here.
    private String c(String path, String def) {
        String val = config.getString(path, def);
        return val != null ? val.replace('&', '\u00A7') : def;
    }

    // ==========================================
    // DATABASE
    // ==========================================
    public DatabaseManager.Type getDatabaseType() {
        String t = config.getString("database.type", "SQLITE");
        return "MYSQL".equalsIgnoreCase(t) ? DatabaseManager.Type.MYSQL : DatabaseManager.Type.SQLITE;
    }

    public String getDatabaseHost() { return config.getString("database.mysql.host", "localhost"); }
    public int getDatabasePort() { return config.getInt("database.mysql.port", 3306); }
    public String getDatabaseName() { return config.getString("database.mysql.database", "darkfactions"); }
    public String getDatabaseUsername() { return config.getString("database.mysql.username", "root"); }
    public String getDatabasePassword() { return config.getString("database.mysql.password", ""); }

    // ==========================================
    // GENERAL
    // ==========================================
    public String getLanguage() { return config.getString("general.language", "en"); }
    public boolean isCheckUpdates() { return config.getBoolean("general.check-updates", true); }
    public boolean isMetricsEnabled() { return config.getBoolean("general.metrics-enabled", false); }
    public int getAutoSaveInterval() { return config.getInt("general.auto-save-interval-seconds", 300); }

    // ==========================================
    // POWER
    // ==========================================
    public double getDefaultPlayerPower() { return config.getDouble("power.default-player-power", 10.0); }
    public double getMaxPlayerPower() { return config.getDouble("power.max-player-power", 10.0); }
    public int getPowerRegenInterval() { return config.getInt("power.regen-interval-seconds", 300); }
    public double getPowerRegenAmount() { return config.getDouble("power.regen-amount", 0.5); }
    public double getPowerLossOnDeath() { return config.getDouble("power.loss-on-death", 1.0); }
    public double getPowerLossOnPveDeath() { return config.getDouble("power.loss-on-pve-death", 0.0); }
    public double getPowerGainOnKill() { return config.getDouble("power.gain-on-kill", 0.5); }
    public boolean isOfflineDecayEnabled() { return config.getBoolean("power.offline-decay-enabled", false); }
    public double getOfflineDecayPerHour() { return config.getDouble("power.offline-decay-per-hour", 0.5); }
    public int getOfflineDecayMaxHours() { return config.getInt("power.offline-decay-max-hours", 48); }
    public double getMinPlayerPower() { return config.getDouble("power.min-player-power", 0.0); }
    public boolean isShowPowerChanges() { return config.getBoolean("power.show-power-changes", true); }
    public double getPowerGainOnMobKill() { return config.getDouble("power.gain-on-mob-kill", 0.0); }
    public double getPowerGainOnRaidWin() { return config.getDouble("power.gain-on-raid-win", 5.0); }

    // ==========================================
    // ELIXIR
    // ==========================================
    public double getElixirPerEnemyKill() { return config.getDouble("elixir.per-enemy-kill", 5.0); }
    public double getElixirPerAnyKill() { return config.getDouble("elixir.per-any-kill", 1.0); }
    public double getElixirPerRaid() { return config.getDouble("elixir.per-raid", 25.0); }
    public double getElixirRaidStealPercent() { return config.getDouble("elixir.raid-steal-percent", 0.5); }
    public double getElixirDailyBonus() { return config.getDouble("elixir.daily-bonus", 10.0); }
    public boolean isElixirAutoClaimOnJoin() { return config.getBoolean("elixir.auto-claim-on-join", false); }
    public double getElixirPerChunkClaim() { return config.getDouble("elixir.per-chunk-claim", 0.0); }
    public double getElixirPerChunkLost() { return config.getDouble("elixir.per-chunk-lost", 2.0); }
    public double getElixirPerPlaytimeHour() { return config.getDouble("elixir.per-playtime-hour", 2.0); }
    public double getElixirCreateFactionCost() { return config.getDouble("elixir.create-faction-cost", 0.0); }
    public double getElixirRenameCost() { return config.getDouble("elixir.rename-faction-cost", 10.0); }
    public double getElixirSetTagCost() { return config.getDouble("elixir.set-tag-cost", 5.0); }
    public boolean isElixirTransferEnabled() { return config.getBoolean("elixir.transfer-enabled", false); }
    public double getElixirTransferTaxRate() { return config.getDouble("elixir.transfer-tax-rate", 0.0); }

    // ==========================================
    // FACTION
    // ==========================================
    public int getMinFactionNameLength() { return config.getInt("faction.min-name-length", 2); }
    public int getMaxFactionNameLength() { return config.getInt("faction.max-name-length", 32); }
    public String getFactionNameAllowedChars() { return config.getString("faction.name-allowed-chars", FactionNameValidator.DEFAULT_ALLOWED_CHARS); }
    public int getMaxMembers() { return config.getInt("faction.max-members", 30); }
    public double getFactionStartingPower() { return config.getDouble("faction.starting-power", 10.0); }
    public double getFactionStartingMaxPower() { return config.getDouble("faction.starting-max-power", 50.0); }
    public int getMaxOfficers() { return config.getInt("faction.max-officers", 5); }
    public int getHomeCooldown() { return config.getInt("faction.home-cooldown-seconds", 30); }
    public int getHomeTeleportDelay() { return config.getInt("faction.home-teleport-delay-seconds", 0); }
    public boolean isHomeCancelOnDamage() { return config.getBoolean("faction.home-cancel-on-damage", true); }
    public int getMaxFactionsPerPlayer() { return config.getInt("faction.max-factions-per-player", 1); }
    public boolean isAutoDisbandEmpty() { return config.getBoolean("faction.auto-disband-empty", true); }
    public boolean isDisbandRequiresConfirm() { return config.getBoolean("faction.disband-requires-confirm", true); }
    public boolean isBroadcastFactionNews() { return config.getBoolean("faction.broadcast-faction-news", true); }
    public int getMaxTagLength() { return config.getInt("faction.max-tag-length", 6); }
    public int getMaxDescriptionLength() { return config.getInt("faction.max-description-length", 100); }
    public int getMaxMotdLength() { return config.getInt("faction.max-motd-length", 255); }
    public boolean isDefaultOpen() { return config.getBoolean("faction.default-open", false); }
    public boolean isDefaultPvp() { return config.getBoolean("faction.default-pvp", false); }
    public boolean isDefaultTnt() { return config.getBoolean("faction.default-tnt", true); }

    // ==========================================
    // CLAIM
    // ==========================================
    public boolean isClaimEnabled() { return config.getBoolean("claim.enabled", true); }
    public int getMaxClaimsPerFaction() { return config.getInt("claim.max-per-faction", 25); }
    public double getClaimCostElixir() { return config.getDouble("claim.cost-elixir", 0.0); }
    public boolean isClaimRequireConnection() { return config.getBoolean("claim.require-connection", true); }
    public boolean isFirstClaimFree() { return config.getBoolean("claim.first-claim-free", true); }
    public int getClaimMinDistanceFromSpawn() { return config.getInt("claim.min-distance-from-spawn-chunks", 0); }
    public List<String> getClaimDisabledWorlds() { return config.getStringList("claim.disabled-worlds"); }
    public List<String> getClaimWhitelistWorlds() { return config.getStringList("claim.whitelist-worlds"); }
    public boolean isCanUnclaimEnemy() { return config.getBoolean("claim.can-unclaim-enemy", false); }
    public int getMaxClaimPerSession() { return config.getInt("claim.max-claim-per-session", 0); }
    public int getMaxUnclaimPerSession() { return config.getInt("claim.max-unclaim-per-session", 0); }
    public int getClaimBufferChunks() { return config.getInt("claim.claim-buffer-chunks", 0); }
    public boolean isShowBorderParticles() { return config.getBoolean("claim.show-border-particles", true); }
    public String getBorderParticleType() { return config.getString("claim.border-particle-type", "REDSTONE"); }
    public int getBorderOwnColorRed() { return config.getInt("claim.border-own-color.red", 0); }
    public int getBorderOwnColorGreen() { return config.getInt("claim.border-own-color.green", 255); }
    public int getBorderOwnColorBlue() { return config.getInt("claim.border-own-color.blue", 0); }
    public int getBorderEnemyColorRed() { return config.getInt("claim.border-enemy-color.red", 255); }
    public int getBorderEnemyColorGreen() { return config.getInt("claim.border-enemy-color.green", 0); }
    public int getBorderEnemyColorBlue() { return config.getInt("claim.border-enemy-color.blue", 0); }

    // ==========================================
    // PROTECTION
    // ==========================================
    public boolean isProtectionEnabled() { return config.getBoolean("protection.enabled", true); }
    public boolean isAlliesCanInteract() { return config.getBoolean("protection.allies-can-interact", true); }
    public boolean isAlliesCanBreak() { return config.getBoolean("protection.allies-can-break", false); }
    public boolean isAlliesCanPlace() { return config.getBoolean("protection.allies-can-place", false); }
    public boolean isRaidableBypass() { return config.getBoolean("protection.raidable-bypass", true); }
    public boolean isMembersCanInvite() { return config.getBoolean("protection.members-can-invite", false); }
    public boolean isMembersCanKick() { return config.getBoolean("protection.members-can-kick", false); }
    public boolean isMembersCanClaim() { return config.getBoolean("protection.members-can-claim", false); }
    public boolean isMembersCanSetHome() { return config.getBoolean("protection.members-can-sethome", false); }
    public boolean isWildernessPvp() { return config.getBoolean("protection.pvp.wilderness-pvp", true); }
    public boolean isOwnTerritoryPvp() { return config.getBoolean("protection.pvp.own-territory-pvp", true); }
    public boolean isAllyTerritoryPvp() { return config.getBoolean("protection.pvp.ally-territory-pvp", false); }
    public boolean isEnemyTerritoryPvp() { return config.getBoolean("protection.pvp.enemy-territory-pvp", true); }
    public boolean isRespectFactionPvpToggle() { return config.getBoolean("protection.pvp.respect-faction-pvp-toggle", true); }
    public boolean isExplosionsInClaims() { return config.getBoolean("protection.explosions.enabled-in-claims", true); }
    public boolean isRespectFactionTntToggle() { return config.getBoolean("protection.explosions.respect-faction-tnt-toggle", true); }
    public boolean isExplosionDamageWilderness() { return config.getBoolean("protection.explosions.damage-wilderness", true); }

    // ==========================================
    // TERRITORY MESSAGES
    // ==========================================
    public boolean isTerritoryMessagesEnabled() { return config.getBoolean("territory-messages.enabled", true); }
    public String getTerritoryEnterOwn() { return c("territory-messages.enter-own", "&aNow entering {faction}'s territory"); }
    public String getTerritoryEnterAlly() { return c("territory-messages.enter-ally", "&7Now entering {faction}'s territory (ally)"); }
    public String getTerritoryEnterEnemy() { return c("territory-messages.enter-enemy", "&cNow entering {faction}'s territory! Watch your back!"); }
    public String getTerritoryExit() { return c("territory-messages.exit", "&7Now leaving {faction}'s territory"); }

    // ==========================================
    // MAP
    // ==========================================
    public int getMapDefaultRadius() { return config.getInt("map.default-radius", 4); }
    public int getMapMaxRadius() { return config.getInt("map.max-radius", 8); }
    public String getMapCharOwnPlayer() { return config.getString("map.character-own-player", "⬤"); }
    public String getMapCharOwn() { return config.getString("map.character-own", "□"); }
    public String getMapCharAlly() { return config.getString("map.character-ally", "◇"); }
    public String getMapCharEnemy() { return config.getString("map.character-enemy", "■"); }
    public String getMapCharWilderness() { return config.getString("map.character-wilderness", "·"); }
    public String getMapColorOwn() { return config.getString("map.color-own", "&a"); }
    public String getMapColorAlly() { return config.getString("map.color-ally", "&9"); }
    public String getMapColorEnemy() { return config.getString("map.color-enemy", "&c"); }
    public String getMapColorWilderness() { return config.getString("map.color-wilderness", "&7"); }

    // ==========================================
    // CHAT
    // ==========================================
    public boolean isFactionChatEnabled() { return config.getBoolean("chat.faction-chat-enabled", true); }
    public boolean isAllyChatEnabled() { return config.getBoolean("chat.ally-chat-enabled", true); }
    public String getFactionChatFormat() { return config.getString("chat.faction-chat-format", "&d[F] {tag}{player}&7: &f{message}"); }
    public String getAllyChatFormat() { return config.getString("chat.ally-chat-format", "&b[A] {tag}{player}&7: &f{message}"); }
    public boolean isLogChatToConsole() { return config.getBoolean("chat.log-chat-to-console", true); }

    // ==========================================
    // FLIGHT
    // ==========================================
    public boolean isFlightEnabled() { return config.getBoolean("flight.enabled", true); }
    public boolean isFlightOwnTerritoryOnly() { return config.getBoolean("flight.own-territory-only", true); }
    public boolean isFlightAutoDisableOnExit() { return config.getBoolean("flight.auto-disable-on-exit", true); }
    public boolean isFlightNotifyOnExit() { return config.getBoolean("flight.notify-on-exit", true); }

    // ==========================================
    // LEADERBOARD
    // ==========================================
    public int getLeaderboardDefaultLimit() { return config.getInt("leaderboard.default-limit", 10); }
    public int getLeaderboardMaxLimit() { return config.getInt("leaderboard.max-limit", 50); }

    // ==========================================
    // ADMIN
    // ==========================================
    public boolean isAdminBypassProtection() { return config.getBoolean("admin.bypass-protection", true); }
    public boolean isLogAdminActions() { return config.getBoolean("admin.log-admin-actions", true); }

    // ==========================================
    // COMBAT
    // ==========================================
    public int getCombatTagDuration() { return config.getInt("combat.tag-duration-seconds", 30); }
    public boolean isCombatTagKillOnQuit() { return config.getBoolean("combat.kill-on-quit", true); }
    public boolean isCombatTagPreventFly() { return config.getBoolean("combat.prevent-fly", true); }
    public boolean isCombatTagPreventHome() { return config.getBoolean("combat.prevent-home", true); }
    public int getCombatLogoutWarmup() { return config.getInt("combat.logout-warmup-seconds", 10); }

    // ==========================================
    // ECONOMY (Vault)
    // ==========================================
    public boolean isVaultEnabled() { return config.getBoolean("economy.vault-enabled", false); }
    public double getVaultElixirToMoneyRate() { return config.getDouble("economy.vault-elixir-to-money-rate", 10.0); }
    public double getVaultMoneyToElixirRate() { return config.getDouble("economy.vault-money-to-elixir-rate", 12.0); }
}