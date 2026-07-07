package com.darkfactions.managers;

// ==========================================
// ElixirManager.java
// Elixir = Faction Points (our unique currency)
// ALL values come from ConfigManager
// ==========================================

import com.darkfactions.DarkFactions;
import com.darkfactions.models.Faction;
import com.darkfactions.storage.DataStore;
import com.darkfactions.storage.SaveQueue;
import com.darkfactions.utils.ConfigManager;
import com.darkfactions.utils.DirtyKeySet;
import com.darkfactions.utils.ElixirDailyRules;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class ElixirManager {

    private final DarkFactions plugin;
    private final Map<UUID, Double> pendingElixir;
    private final Map<UUID, Long> lastDailyClaim;
    private final Set<UUID> pendingElixirDeletions;

    // Per-key dirty tracking so a save cycle only rewrites the players whose
    // pending elixir or daily-claim timestamp actually changed, instead of the
    // whole table on every cycle.
    private final DirtyKeySet<UUID> dirtyPendingElixir;
    private final DirtyKeySet<UUID> dirtyDailyClaims;

    // Cached config values
    private double perEnemyKill;
    private double perAnyKill;
    private double perRaid;
    private double raidStealPercent;
    private double dailyBonus;
    private boolean autoClaimOnJoin;
    private boolean transferEnabled;
    private double transferTaxRate;

    public ElixirManager(DarkFactions plugin) {
        this.plugin = plugin;
        this.pendingElixir = new ConcurrentHashMap<>();
        this.lastDailyClaim = new ConcurrentHashMap<>();
        this.pendingElixirDeletions = ConcurrentHashMap.newKeySet();
        this.dirtyPendingElixir = new DirtyKeySet<>();
        this.dirtyDailyClaims = new DirtyKeySet<>();
        reloadConfig();
    }

    // ==========================================
    // Load ALL values from ConfigManager
    // ==========================================
    public void reloadConfig() {
        ConfigManager cfg = plugin.getConfigManager();
        this.perEnemyKill = cfg.getElixirPerEnemyKill();
        this.perAnyKill = cfg.getElixirPerAnyKill();
        this.perRaid = cfg.getElixirPerRaid();
        this.raidStealPercent = cfg.getElixirRaidStealPercent();
        this.dailyBonus = cfg.getElixirDailyBonus();
        this.autoClaimOnJoin = cfg.isElixirAutoClaimOnJoin();
        this.transferEnabled = cfg.isElixirTransferEnabled();
        this.transferTaxRate = cfg.getElixirTransferTaxRate();
    }

    // ==========================================
    // Balance
    // ==========================================

    public double getFactionElixir(UUID factionId) {
        Faction faction = plugin.getFactionManager().getFaction(factionId);
        return faction == null ? 0.0 : faction.getElixir();
    }

    public void addFactionElixir(UUID factionId, double amount) {
        Faction faction = plugin.getFactionManager().getFaction(factionId);
        if (faction != null) {
            faction.addElixir(amount);
        }
    }

    public boolean removeFactionElixir(UUID factionId, double amount) {
        Faction faction = plugin.getFactionManager().getFaction(factionId);
        if (faction != null && faction.removeElixir(amount)) {
            return true;
        }
        return false;
    }

    // ==========================================
    // Earning Events
    // ==========================================

    // Enemy kill
    public void onEnemyKill(UUID killerFactionId) {
        addFactionElixir(killerFactionId, perEnemyKill);
    }

    // Any player kill
    public void onAnyKill(UUID killerFactionId) {
        addFactionElixir(killerFactionId, perAnyKill);
    }

    // Raid
    public void onSuccessfulRaid(UUID raiderFactionId, UUID victimFactionId) {
        addFactionElixir(raiderFactionId, perRaid);

        Faction victimFaction = plugin.getFactionManager().getFaction(victimFactionId);
        if (victimFaction != null) {
            // Steal a percentage of the victim's actual balance, capped at what they have
            // so removeElixir can never fail.
            double stolenAmount = Math.min(victimFaction.getElixir() * raidStealPercent, victimFaction.getElixir());
            victimFaction.removeElixir(stolenAmount);
        }
    }

    // Daily login bonus — at most once per calendar day (server time zone)
    public void onPlayerLogin(UUID playerUuid) {
        ZoneId zone = ZoneId.systemDefault();
        long now = System.currentTimeMillis();
        if (!ElixirDailyRules.isEligibleForDailyClaim(lastDailyClaim.get(playerUuid), now, zone)) {
            return;
        }

        lastDailyClaim.put(playerUuid, now);
        dirtyDailyClaims.markDirty(playerUuid);

        if (autoClaimOnJoin) {
            // Auto-claim the daily bonus directly to their faction
            Faction faction = plugin.getFactionManager().getPlayerFaction(playerUuid);
            if (faction != null) {
                faction.addElixir(dailyBonus);
            }
        } else {
            // Add to pending - claimed via /f elixir
            pendingElixir.merge(playerUuid, dailyBonus, Double::sum);
            dirtyPendingElixir.markDirty(playerUuid);
        }
    }

    // Claim pending elixir
    public boolean claimPendingElixir(UUID playerUuid) {
        if (!pendingElixir.containsKey(playerUuid)) return false;

        // Keep the pending balance intact if the player has no faction to receive it,
        // so they can still claim once they (re)join one.
        Faction faction = plugin.getFactionManager().getPlayerFaction(playerUuid);
        if (faction == null) return false;

        double amount = pendingElixir.remove(playerUuid);
        faction.addElixir(amount);
        pendingElixirDeletions.add(playerUuid);
        dirtyPendingElixir.clear(playerUuid);
        return true;
    }

    // ==========================================
    // Spending - Shop / Perks
    // ==========================================

    public boolean boostFactionPower(UUID factionId, double elixirCost, double powerAmount) {
        if (removeFactionElixir(factionId, elixirCost)) {
            Faction faction = plugin.getFactionManager().getFaction(factionId);
            if (faction != null) {
                faction.addPower(powerAmount);
                return true;
            }
        }
        return false;
    }

    public boolean increaseMaxPower(UUID factionId, double elixirCost, double additionalMaxPower) {
        if (removeFactionElixir(factionId, elixirCost)) {
            Faction faction = plugin.getFactionManager().getFaction(factionId);
            if (faction != null) {
                faction.setMaxPower(faction.getMaxPower() + additionalMaxPower);
                return true;
            }
        }
        return false;
    }

    // Transfer elixir between factions (with tax)
    public boolean transferElixir(UUID fromFactionId, UUID toFactionId, double amount) {
        if (!transferEnabled) return false;

        // Full amount leaves the sender; the tax is burned (not credited anywhere),
        // so the receiver only gets the post-tax remainder.
        double tax = amount * transferTaxRate;
        double actualTransfer = amount - tax;

        if (removeFactionElixir(fromFactionId, amount)) {
            addFactionElixir(toFactionId, actualTransfer);
            return true;
        }
        return false;
    }

    // ==========================================
    // Save/Load via DataStore
    // ==========================================

    public void loadFromStore(DataStore store) {
        pendingElixir.putAll(store.loadPendingElixir());
        lastDailyClaim.putAll(store.loadLastDailyClaims());
        plugin.getLogger().info("Loaded elixir data - " + pendingElixir.size() + " players have pending elixir!");
    }

    public void saveToStoreAsync(SaveQueue queue) {
        boolean hasDeletions = !pendingElixirDeletions.isEmpty();
        Set<UUID> pendingKeys = dirtyPendingElixir.drain();
        Set<UUID> claimKeys = dirtyDailyClaims.drain();
        if (pendingKeys.isEmpty() && claimKeys.isEmpty() && !hasDeletions) return;

        List<UUID> toDelete = hasDeletions ? new ArrayList<>(pendingElixirDeletions) : List.of();
        pendingElixirDeletions.removeAll(toDelete);

        queue.submit(() -> {
            try {
                flushToStore(queue.store(), toDelete, pendingKeys, claimKeys);
            } catch (RuntimeException e) {
                pendingElixirDeletions.addAll(toDelete);
                dirtyPendingElixir.restore(pendingKeys);
                dirtyDailyClaims.restore(claimKeys);
                plugin.getLogger().log(Level.SEVERE, "Elixir data save failed, will retry on the next save cycle", e);
            }
        });
    }

    /** Synchronous save used during plugin shutdown; clears dirty only after a confirmed write. */
    public void saveToStoreSync(DataStore store) {
        boolean hasDeletions = !pendingElixirDeletions.isEmpty();
        Set<UUID> pendingKeys = dirtyPendingElixir.drain();
        Set<UUID> claimKeys = dirtyDailyClaims.drain();
        if (pendingKeys.isEmpty() && claimKeys.isEmpty() && !hasDeletions) return;

        List<UUID> toDelete = hasDeletions ? new ArrayList<>(pendingElixirDeletions) : List.of();
        pendingElixirDeletions.removeAll(toDelete);

        try {
            flushToStore(store, toDelete, pendingKeys, claimKeys);
        } catch (RuntimeException e) {
            pendingElixirDeletions.addAll(toDelete);
            dirtyPendingElixir.restore(pendingKeys);
            dirtyDailyClaims.restore(claimKeys);
            plugin.getLogger().log(Level.SEVERE, "Elixir data save failed during shutdown", e);
        }
    }

    private void flushToStore(DataStore store, List<UUID> toDelete, Set<UUID> pendingKeys, Set<UUID> claimKeys) {
        for (UUID playerUuid : toDelete) {
            store.deletePendingElixir(playerUuid);
        }
        for (UUID playerUuid : pendingKeys) {
            Double amount = pendingElixir.get(playerUuid);
            if (amount != null) store.savePendingElixir(playerUuid, amount);
        }
        for (UUID playerUuid : claimKeys) {
            Long claim = lastDailyClaim.get(playerUuid);
            if (claim != null) store.saveLastDailyClaim(playerUuid, claim);
        }
    }
}