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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class ElixirManager {

    private final DarkFactions plugin;
    private final Map<UUID, Double> pendingElixir;
    private final Set<UUID> pendingElixirDeletions;
    private final AtomicBoolean pendingDirty;

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
        this.pendingElixirDeletions = ConcurrentHashMap.newKeySet();
        this.pendingDirty = new AtomicBoolean(false);
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
            pendingDirty.set(true);
        }
    }

    public boolean removeFactionElixir(UUID factionId, double amount) {
        Faction faction = plugin.getFactionManager().getFaction(factionId);
        if (faction != null && faction.removeElixir(amount)) {
            pendingDirty.set(true);
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
            // Cap the steal at the victim's current balance so removeElixir can never fail.
            double stolenAmount = Math.min(perRaid * raidStealPercent, victimFaction.getElixir());
            victimFaction.removeElixir(stolenAmount);
        }
    }

    // Daily login bonus
    public void onPlayerLogin(UUID playerUuid) {
        if (autoClaimOnJoin) {
            // Auto-claim the daily bonus directly to their faction
            Faction faction = plugin.getFactionManager().getPlayerFaction(playerUuid);
            if (faction != null) {
                faction.addElixir(dailyBonus);
                pendingDirty.set(true); // persist the faction's new balance
            }
        } else {
            // Add to pending - claimed via /f elixir
            pendingElixir.merge(playerUuid, dailyBonus, Double::sum);
            pendingDirty.set(true);
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
        pendingDirty.set(true);
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
        plugin.getLogger().info("Loaded elixir data - " + pendingElixir.size() + " players have pending elixir!");
    }

    public void saveToStoreAsync(SaveQueue queue) {
        boolean hasDeletions = !pendingElixirDeletions.isEmpty();
        if (!pendingDirty.getAndSet(false) && !hasDeletions) return;

        List<UUID> toDelete = hasDeletions ? new ArrayList<>(pendingElixirDeletions) : List.of();
        pendingElixirDeletions.removeAll(toDelete);

        queue.submit(() -> {
            DataStore store = queue.store();
            for (UUID playerUuid : toDelete) {
                store.deletePendingElixir(playerUuid);
            }
            for (Map.Entry<UUID, Double> e : pendingElixir.entrySet()) {
                store.savePendingElixir(e.getKey(), e.getValue());
            }
        });
    }
}