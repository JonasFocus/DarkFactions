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
import java.util.concurrent.atomic.AtomicBoolean;

public class FactionManager {

    private final DarkFactions plugin;
    private final Map<UUID, Faction> factions;
    private final Map<UUID, UUID> playerFactionMap;
    private final Map<UUID, List<UUID>> pendingInvites;
    private final Map<String, UUID> factionsByName;
    private final Set<UUID> pendingDeletions;
    private final AtomicBoolean dirty;

    public FactionManager(DarkFactions plugin) {
        this.plugin = plugin;
        this.factions = new ConcurrentHashMap<>();
        this.playerFactionMap = new ConcurrentHashMap<>();
        this.pendingInvites = new ConcurrentHashMap<>();
        this.factionsByName = new ConcurrentHashMap<>();
        this.pendingDeletions = ConcurrentHashMap.newKeySet();
        this.dirty = new AtomicBoolean(false);
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
        faction.setPower(cfg.getFactionStartingPower());
        faction.setMaxPower(cfg.getFactionStartingMaxPower());
        faction.setOpen(cfg.isDefaultOpen());
        faction.setPvpEnabled(cfg.isDefaultPvp());
        faction.setTntEnabled(cfg.isDefaultTnt());

        factions.put(faction.getFactionId(), faction);
        factionsByName.put(nameKey(name), faction.getFactionId());
        playerFactionMap.put(leaderUuid, faction.getFactionId());

        dirty.set(true);
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
        factions.remove(factionId);
        factionsByName.remove(nameKey(faction.getName()));
        pendingDeletions.add(factionId);

        dirty.set(true);
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
        dirty.set(true);
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

        dirty.set(true);
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

        dirty.set(true);
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
        dirty.set(true);
        return true;
    }

    public boolean demotePlayer(UUID playerUuid, UUID factionId) {
        Faction faction = factions.get(factionId);
        if (faction == null) return false;
        if (!faction.isOfficer(playerUuid)) return false;
        faction.demoteFromOfficer(playerUuid);
        dirty.set(true);
        return true;
    }

    public boolean transferLeadership(UUID factionId, UUID newLeaderUuid) {
        Faction faction = factions.get(factionId);
        if (faction == null) return false;
        if (!faction.isMember(newLeaderUuid)) return false;
        faction.setLeaderUuid(newLeaderUuid);
        dirty.set(true);
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
        dirty.set(true);
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
        return FactionRankings.top(factions.values(), FactionRankings.BY_POWER, limit);
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
            factions.put(f.getFactionId(), f);
            factionsByName.put(nameKey(f.getName()), f.getFactionId());
            for (UUID muid : f.getMembers()) {
                playerFactionMap.put(muid, f.getFactionId());
            }
            plugin.getLogger().info("Loaded faction: " + f.getName());
        }
        plugin.getLogger().info("Loaded " + factions.size() + " factions!");
    }

    public void saveToStoreAsync(SaveQueue queue) {
        boolean hasDeletions = !pendingDeletions.isEmpty();
        if (!dirty.getAndSet(false) && !hasDeletions) return;

        List<UUID> toDelete = hasDeletions ? new ArrayList<>(pendingDeletions) : List.of();
        pendingDeletions.removeAll(toDelete);

        queue.submit(() -> {
            DataStore store = queue.store();
            for (UUID factionId : toDelete) {
                store.deleteFaction(factionId);
            }
            for (Faction f : factions.values()) {
                store.saveFaction(f);
            }
        });
    }
}