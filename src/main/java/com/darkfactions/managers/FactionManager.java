package com.darkfactions.managers;

// ==========================================
// FactionManager.java
// Handles ALL faction CRUD, invites, save/load
// ALL limits come from ConfigManager
// ==========================================

import com.darkfactions.DarkFactions;
import com.darkfactions.models.Faction;
import com.darkfactions.storage.DataStore;
import com.darkfactions.storage.SaveQueue;
import com.darkfactions.utils.ConfigManager;
import com.darkfactions.utils.FactionRankings;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

public class FactionManager {

    private final DarkFactions plugin;
    private final Map<UUID, Faction> factions;
    private final Map<UUID, UUID> playerFactionMap;
    private final Map<UUID, List<UUID>> pendingInvites;
    // Key: target faction receiving the request; Value: requesting faction IDs
    private final Map<UUID, Set<UUID>> pendingAllyRequests;
    private final Map<String, UUID> factionsByName;
    private final Set<UUID> pendingDeletions;

    public FactionManager(DarkFactions plugin) {
        this.plugin = plugin;
        this.factions = new ConcurrentHashMap<>();
        this.playerFactionMap = new ConcurrentHashMap<>();
        this.pendingInvites = new ConcurrentHashMap<>();
        this.pendingAllyRequests = new ConcurrentHashMap<>();
        this.factionsByName = new ConcurrentHashMap<>();
        this.pendingDeletions = ConcurrentHashMap.newKeySet();
    }

    // Kept for the handful of admin call sites that don't hold a direct Faction
    // reference; a Faction now tracks its own dirty state via its mutators, so
    // this is a no-op retained for source compatibility.
    public void markDirty() {
        // no-op: Faction instances mark themselves dirty on mutation.
    }

    // Canonical key for the name index: lower-cased with a fixed locale so the
    // mapping never shifts with the server's default locale.
    private static String nameKey(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    // ==========================================
    // CRUD
    // ==========================================

    public Faction createFaction(String name, UUID leaderUuid) {
        if (getFactionByName(name) != null) return null;
        if (getPlayerFaction(leaderUuid) != null) return null;

        ConfigManager cfg = plugin.getConfigManager();

        Faction faction = new Faction(name, leaderUuid);
        faction.setOpen(cfg.isDefaultOpen());
        faction.setPvpEnabled(cfg.isDefaultPvp());
        faction.setTntEnabled(cfg.isDefaultTnt());
        faction.setBonusPower(cfg.getFactionStartingPower());
        faction.setMaxPower(cfg.getFactionStartingMaxPower());

        factions.put(faction.getFactionId(), faction);
        factionsByName.put(nameKey(name), faction.getFactionId());
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
        pendingInvites.values().removeIf(List::isEmpty);
        clearAllyRequestsInvolving(factionId);

        // Scrub stale ally/enemy UUIDs from every surviving faction before removal.
        for (Faction other : factions.values()) {
            if (other.getFactionId().equals(factionId)) {
                continue;
            }
            other.removeAlly(factionId);
            other.removeEnemy(factionId);
        }

        factions.remove(factionId);
        factionsByName.remove(nameKey(faction.getName()));
        pendingDeletions.add(factionId);

        return true;
    }

    public Faction getFaction(UUID factionId) {
        return factions.get(factionId);
    }

    public Faction getFactionByName(String name) {
        if (name == null) return null;
        UUID factionId = factionsByName.get(nameKey(name));
        return factionId == null ? null : factions.get(factionId);
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
        factionsByName.remove(nameKey(faction.getName()));
        faction.setName(newName);
        factionsByName.put(nameKey(newName), factionId);
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

        // Clean up chat mode state if the player had one
        plugin.getFactionCommand().clearChatMode(playerUuid);

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
        List<UUID> invites = pendingInvites.computeIfAbsent(targetUuid, k -> new CopyOnWriteArrayList<>());
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
    // Ally Request System
    // ==========================================

    /**
     * Record a pending ally request from {@code fromFactionId} to {@code toFactionId}.
     * @return false if a request from that faction is already pending
     */
    public boolean sendAllyRequest(UUID fromFactionId, UUID toFactionId) {
        Set<UUID> requests = pendingAllyRequests.computeIfAbsent(toFactionId, k -> ConcurrentHashMap.newKeySet());
        return requests.add(fromFactionId);
    }

    public boolean hasPendingAllyRequest(UUID fromFactionId, UUID toFactionId) {
        Set<UUID> requests = pendingAllyRequests.get(toFactionId);
        return requests != null && requests.contains(fromFactionId);
    }

    /**
     * Accept a pending ally request: clear enemies, form mutual ally, clear the request.
     * @return false if no pending request exists
     */
    public boolean acceptAllyRequest(UUID acceptingFactionId, UUID requestingFactionId) {
        Set<UUID> requests = pendingAllyRequests.get(acceptingFactionId);
        if (requests == null || !requests.remove(requestingFactionId)) {
            return false;
        }
        if (requests.isEmpty()) {
            pendingAllyRequests.remove(acceptingFactionId);
        }

        Faction accepting = factions.get(acceptingFactionId);
        Faction requesting = factions.get(requestingFactionId);
        if (accepting == null || requesting == null) {
            return false;
        }

        accepting.removeEnemy(requestingFactionId);
        requesting.removeEnemy(acceptingFactionId);
        accepting.addAlly(requestingFactionId);
        requesting.addAlly(acceptingFactionId);
        return true;
    }

    public boolean denyAllyRequest(UUID denyingFactionId, UUID requestingFactionId) {
        Set<UUID> requests = pendingAllyRequests.get(denyingFactionId);
        if (requests == null) return false;
        boolean removed = requests.remove(requestingFactionId);
        if (requests.isEmpty()) {
            pendingAllyRequests.remove(denyingFactionId);
        }
        return removed;
    }

    private void clearAllyRequestsInvolving(UUID factionId) {
        pendingAllyRequests.remove(factionId);
        pendingAllyRequests.values().forEach(set -> set.remove(factionId));
        pendingAllyRequests.values().removeIf(Set::isEmpty);
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
        org.bukkit.World world = plugin.getServer().getWorld(faction.getWorldName());
        if (world == null) return null;
        return new Location(
                world,
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
        return FactionRankings.top(factions.values(),
                FactionRankings.byPower(f -> plugin.getPowerManager().getEffectiveFactionPower(f.getFactionId())),
                limit);
    }

    public List<Faction> getTopFactionsByElixir(int limit) {
        return FactionRankings.top(factions.values(), FactionRankings.BY_ELIXIR, limit);
    }

    public List<Faction> getTopFactionsByMembers(int limit) {
        return FactionRankings.top(factions.values(), FactionRankings.BY_MEMBERS, limit);
    }

    // ==========================================
    // Save/Load via DataStore
    // ==========================================

    public void loadFromStore(DataStore store) {
        for (Faction f : store.loadAllFactions()) {
            String originalName = f.getName();
            String key = nameKey(originalName);
            if (factionsByName.containsKey(key)) {
                // Duplicate case-insensitive name: rename in-memory so both load.
                String suffix = f.getFactionId().toString().substring(0, 8);
                String renamed = originalName + "-" + suffix;
                plugin.getLogger().severe("Duplicate faction name '" + originalName
                        + "' on load; renaming id " + f.getFactionId() + " to '" + renamed + "'");
                f.setName(renamed);
                key = nameKey(renamed);
                // Keep dirty so the renamed name is persisted on the next save.
            } else {
                // Loading hydrates the faction through its normal setters, which
                // mark it dirty; clear that so a freshly-loaded faction with no
                // real changes isn't rewritten on the very next save cycle.
                f.clearDirty();
            }
            factions.put(f.getFactionId(), f);
            factionsByName.put(key, f.getFactionId());
            for (UUID muid : f.getMembers()) {
                playerFactionMap.put(muid, f.getFactionId());
            }
            plugin.getLogger().info("Loaded faction: " + f.getName());
        }
        plugin.getLogger().info("Loaded " + factions.size() + " factions!");
    }

    // Snapshots and clears the dirty flag of every faction that has unsaved
    // changes, so only those get written out instead of the full set.
    private List<Faction> collectDirty() {
        List<Faction> toSave = new ArrayList<>();
        for (Faction f : factions.values()) {
            if (f.isDirty()) {
                f.clearDirty();
                toSave.add(f);
            }
        }
        return toSave;
    }

    public void saveToStoreAsync(SaveQueue queue) {
        boolean hasDeletions = !pendingDeletions.isEmpty();
        List<Faction> toSave = collectDirty();
        if (toSave.isEmpty() && !hasDeletions) return;

        List<UUID> toDelete = hasDeletions ? new ArrayList<>(pendingDeletions) : List.of();
        pendingDeletions.removeAll(toDelete);

        queue.submit(() -> {
            DataStore store = queue.store();
            try {
                for (UUID factionId : toDelete) {
                    store.deleteFaction(factionId);
                }
                for (Faction f : toSave) {
                    store.saveFaction(f);
                }
            } catch (RuntimeException e) {
                pendingDeletions.addAll(toDelete);
                toSave.forEach(Faction::markDirty);
                plugin.getLogger().log(Level.SEVERE, "Faction save failed, will retry on the next save cycle", e);
            }
        });
    }

    /** Synchronous save used during plugin shutdown; clears dirty only after a confirmed write. */
    public void saveToStoreSync(DataStore store) {
        boolean hasDeletions = !pendingDeletions.isEmpty();
        List<Faction> toSave = factions.values().stream().filter(Faction::isDirty).toList();
        if (toSave.isEmpty() && !hasDeletions) return;

        List<UUID> toDelete = hasDeletions ? new ArrayList<>(pendingDeletions) : List.of();
        pendingDeletions.removeAll(toDelete);

        try {
            for (UUID factionId : toDelete) {
                store.deleteFaction(factionId);
            }
            for (Faction f : toSave) {
                store.saveFaction(f);
            }
            toSave.forEach(Faction::clearDirty);
        } catch (RuntimeException e) {
            pendingDeletions.addAll(toDelete);
            plugin.getLogger().log(Level.SEVERE, "Faction save failed during shutdown", e);
        }
    }
}