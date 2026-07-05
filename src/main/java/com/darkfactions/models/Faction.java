package com.darkfactions.models;

// ==========================================
// Faction.java - The core model for a faction
// This holds ALL the data about a faction
// ==========================================

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class Faction {

    // Scalar fields are volatile for the same reason the sets below are
    // synchronized: the main thread is the only writer, but the async SaveQueue
    // worker (and the async chat listener) read them mid-save. Without volatile
    // there is no happens-before edge, so a save could persist stale or (for
    // the 64-bit double/long fields) torn values.

    // Unique identifier for this faction - used for saving/loading
    private volatile UUID factionId;

    // The name of the faction (e.g. "Warriors", "DarkElite")
    private volatile String name;

    // Who created the faction - they are the leader by default
    private volatile UUID leaderUuid;

    // Members, officers, enemies and allies keyed by UUID. Stored as sets so
    // membership tests are O(1) and duplicates can't sneak in, wrapped in a
    // synchronized LinkedHashSet to keep insertion order and let the async chat
    // listener safely snapshot them while the main thread mutates.
    private Set<UUID> members;
    private Set<UUID> officers;
    private Set<UUID> enemies;
    private Set<UUID> allies;

    // Where the faction home is located (null if not set)
    private volatile String worldName;
    private volatile double homeX;
    private volatile double homeY;
    private volatile double homeZ;
    private volatile float homeYaw;
    private volatile float homePitch;

    /**
     * Admin/shop bonus power added on top of the sum of member player power.
     * Persisted as {@code bonus_power}; the legacy {@code power} column is no longer read.
     */
    private volatile double bonusPower;

    /**
     * Bonus max power from shop upgrades, added on top of the sum of member max player power.
     * Persisted as {@code max_power}.
     */
    private volatile double maxPower;

    // When this faction was created, in epoch milliseconds (System.currentTimeMillis()).
    // Consumers wrap this directly in java.util.Date, so it must stay millis, not seconds.
    private volatile long creationTime;

    // Elixir points - basically faction currency/points
    private volatile double elixir;

    // Whether the faction is open for anyone to join
    private volatile boolean open;

    // ==========================================
    // NEW FIELDS - Adding more faction features
    // ==========================================

    // Faction message of the day (shown when members log in)
    private volatile String motd;

    // Short description of the faction
    private volatile String description;

    // Faction tag/prefix (e.g. "[WAR]") shown before member names
    private volatile String tag;

    // PvP toggle - if false, faction members cant hurt each other
    private volatile boolean pvpEnabled;

    // TNT toggle - if false, TNT does no damage in faction territory
    private volatile boolean tntEnabled;

    // Tracks whether this faction has unsaved changes, so FactionManager only
    // has to persist the factions that actually changed on a given save cycle
    // instead of rewriting every faction in memory. Starts true so a
    // freshly-created faction is saved at least once.
    private volatile boolean dirty = true;

    // ==========================================
    // Constructor - Makes a brand new faction!
    // ==========================================
    public Faction(String name, UUID leaderUuid) {
        this.factionId = UUID.randomUUID();
        this.name = name;
        this.leaderUuid = leaderUuid;
        this.members = newMemberSet();
        this.officers = newMemberSet();
        this.enemies = newMemberSet();
        this.allies = newMemberSet();
        this.bonusPower = 0.0;
        this.maxPower = 0.0;
        this.creationTime = System.currentTimeMillis();
        this.elixir = 0.0; // Start with 0 elixir
        this.open = false; // Invite-only by default
        this.motd = ""; // No MOTD by default
        this.description = ""; // No description by default
        this.tag = ""; // No tag by default
        this.pvpEnabled = false; // Friendly fire off by default
        this.tntEnabled = true; // TNT on by default

        // Add the leader as the first member
        this.members.add(leaderUuid);
    }

    // Empty constructor for loading from config
    public Faction() {
        this.members = newMemberSet();
        this.officers = newMemberSet();
        this.enemies = newMemberSet();
        this.allies = newMemberSet();
    }

    // ==========================================
    // Dirty tracking - lets FactionManager save only what changed
    // ==========================================

    public void markDirty() {
        this.dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        this.dirty = false;
    }

    // Order-preserving, thread-safe set used for members/officers/enemies/allies.
    private static Set<UUID> newMemberSet() {
        return Collections.synchronizedSet(new LinkedHashSet<>());
    }

    // Snapshot a member set under its own lock so the async chat listener can
    // copy it safely while the main thread mutates the original.
    private static List<UUID> snapshot(Set<UUID> set) {
        synchronized (set) {
            return new ArrayList<>(set);
        }
    }

    // ==========================================
    // Member Management Methods
    // ==========================================

    // Adds a player to the faction
    public void addMember(UUID playerUuid) {
        members.add(playerUuid);
        markDirty();
    }

    // Removes a player from the faction
    public void removeMember(UUID playerUuid) {
        members.remove(playerUuid);
        officers.remove(playerUuid);
        markDirty();
    }

    // Check if a player is in this faction
    public boolean isMember(UUID playerUuid) {
        return members.contains(playerUuid);
    }

    // ==========================================
    // Officer Management
    // ==========================================

    // Promote a member to officer
    public void promoteToOfficer(UUID playerUuid) {
        if (members.contains(playerUuid)) {
            officers.add(playerUuid);
            markDirty();
        }
    }

    // Demote an officer back to member
    public void demoteFromOfficer(UUID playerUuid) {
        officers.remove(playerUuid);
        markDirty();
    }

    // Check if a player is an officer
    public boolean isOfficer(UUID playerUuid) {
        return officers.contains(playerUuid);
    }

    // Check if a player is the leader
    public boolean isLeader(UUID playerUuid) {
        return leaderUuid != null && leaderUuid.equals(playerUuid);
    }

    // Check if a player is leader or officer (has moderation powers)
    public boolean isLeaderOrOfficer(UUID playerUuid) {
        return isLeader(playerUuid) || isOfficer(playerUuid);
    }

    // ==========================================
    // Faction Home Methods
    // ==========================================

    // Check if faction has a home set
    public boolean hasHome() {
        return worldName != null && !worldName.isEmpty();
    }

    // ==========================================
    // Bonus Power Methods (admin / shop / raid rewards)
    // ==========================================

    public void addBonusPower(double amount) {
        this.bonusPower += amount;
        markDirty();
    }

    public void setBonusPower(double bonusPower) {
        this.bonusPower = bonusPower;
        markDirty();
    }

    public double getBonusPower() {
        return bonusPower;
    }

    /** @deprecated Use {@link #addBonusPower(double)} — kept for shop integration. */
    @Deprecated
    public void addPower(double amount) {
        addBonusPower(amount);
    }

    /** @deprecated Use {@link #setBonusPower(double)}. */
    @Deprecated
    public void setPower(double power) {
        setBonusPower(power);
    }

    /** @deprecated Bonus power only; use PowerManager effective power for display and raidable checks. */
    @Deprecated
    public double getPower() {
        return bonusPower;
    }

    public void addBonusMaxPower(double amount) {
        this.maxPower += amount;
        markDirty();
    }

    // ==========================================
    // Elixir Methods
    // ==========================================

    // Add elixir points
    public void addElixir(double amount) {
        this.elixir += amount;
        markDirty();
    }

    // Remove elixir points (if they have enough)
    public boolean removeElixir(double amount) {
        if (this.elixir >= amount) {
            this.elixir -= amount;
            markDirty();
            return true;
        }
        return false;
    }

    // ==========================================
    // Enemy/Ally Management
    // ==========================================

    public void addEnemy(UUID factionId) {
        enemies.add(factionId);
        markDirty();
    }

    public void removeEnemy(UUID factionId) {
        enemies.remove(factionId);
        markDirty();
    }

    public boolean isEnemy(UUID factionId) {
        return enemies.contains(factionId);
    }

    public void addAlly(UUID factionId) {
        allies.add(factionId);
        markDirty();
    }

    public void removeAlly(UUID factionId) {
        allies.remove(factionId);
        markDirty();
    }

    public boolean isAlly(UUID factionId) {
        return allies.contains(factionId);
    }

    // ==========================================
    // MOTD Methods
    // ==========================================

    public String getMotd() {
        return motd;
    }

    public void setMotd(String motd) {
        this.motd = motd;
        markDirty();
    }

    public boolean hasMotd() {
        return motd != null && !motd.isEmpty();
    }

    // ==========================================
    // Description Methods
    // ==========================================

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        markDirty();
    }

    public boolean hasDescription() {
        return description != null && !description.isEmpty();
    }

    // ==========================================
    // Tag Methods
    // ==========================================

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
        markDirty();
    }

    public boolean hasTag() {
        return tag != null && !tag.isEmpty();
    }

    // Get the tag formatted for display (with brackets if needed)
    public String getFormattedTag() {
        if (hasTag()) {
            return "[" + tag + "] ";
        }
        return "";
    }

    // ==========================================
    // Getters and Setters (existing)
    // ==========================================

    public UUID getFactionId() {
        return factionId;
    }

    public void setFactionId(UUID factionId) {
        this.factionId = factionId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        markDirty();
    }

    public UUID getLeaderUuid() {
        return leaderUuid;
    }

    public void setLeaderUuid(UUID leaderUuid) {
        this.leaderUuid = leaderUuid;
        markDirty();
    }

    public List<UUID> getMembers() {
        return snapshot(members);
    }

    public void setMembers(List<UUID> members) {
        this.members = Collections.synchronizedSet(new LinkedHashSet<>(members));
    }

    public List<UUID> getOfficers() {
        return snapshot(officers);
    }

    public void setOfficers(List<UUID> officers) {
        this.officers = Collections.synchronizedSet(new LinkedHashSet<>(officers));
    }

    public List<UUID> getEnemies() {
        return snapshot(enemies);
    }

    public void setEnemies(List<UUID> enemies) {
        this.enemies = Collections.synchronizedSet(new LinkedHashSet<>(enemies));
    }

    public List<UUID> getAllies() {
        return snapshot(allies);
    }

    public void setAllies(List<UUID> allies) {
        this.allies = Collections.synchronizedSet(new LinkedHashSet<>(allies));
    }

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public double getHomeX() {
        return homeX;
    }

    public void setHomeX(double homeX) {
        this.homeX = homeX;
    }

    public double getHomeY() {
        return homeY;
    }

    public void setHomeY(double homeY) {
        this.homeY = homeY;
    }

    public double getHomeZ() {
        return homeZ;
    }

    public void setHomeZ(double homeZ) {
        this.homeZ = homeZ;
    }

    public float getHomeYaw() {
        return homeYaw;
    }

    public void setHomeYaw(float homeYaw) {
        this.homeYaw = homeYaw;
    }

    public float getHomePitch() {
        return homePitch;
    }

    public void setHomePitch(float homePitch) {
        this.homePitch = homePitch;
    }

    public double getMaxPower() {
        return maxPower;
    }

    public void setMaxPower(double maxPower) {
        this.maxPower = maxPower;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(long creationTime) {
        this.creationTime = creationTime;
    }

    public double getElixir() {
        return elixir;
    }

    public void setElixir(double elixir) {
        this.elixir = elixir;
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public boolean isPvpEnabled() {
        return pvpEnabled;
    }

    public void setPvpEnabled(boolean pvpEnabled) {
        this.pvpEnabled = pvpEnabled;
    }

    public boolean isTntEnabled() {
        return tntEnabled;
    }

    public void setTntEnabled(boolean tntEnabled) {
        this.tntEnabled = tntEnabled;
    }

    // Get the total number of members including leader
    public int getMemberCount() {
        synchronized (members) {
            return members.size();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Faction other)) return false;
        return Objects.equals(factionId, other.factionId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(factionId);
    }
}