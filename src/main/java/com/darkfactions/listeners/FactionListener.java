package com.darkfactions.listeners;

// ==========================================
// FactionListener.java
// Listens for game events and handles faction-related actions
// Territory protection, chat, deaths, joining/leaving
// ==========================================

import com.darkfactions.DarkFactions;
import com.darkfactions.models.Faction;
import com.darkfactions.utils.ProtectionRules;
import com.darkfactions.utils.PvpRules;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;

import io.papermc.paper.event.player.AsyncChatEvent;

import java.util.List;
import java.util.UUID;

public class FactionListener implements Listener {

    // Reference to main plugin
    private final DarkFactions plugin;

    // Handles territory border messages, auto-claim, and flight on move
    private final TerritoryMovementHandler territoryMovementHandler;

    // Routes faction/ally chat
    private final FactionChatService chatService;

    // ==========================================
    // Constructor
    // ==========================================
    public FactionListener(DarkFactions plugin) {
        this.plugin = plugin;
        this.territoryMovementHandler = new TerritoryMovementHandler(plugin);
        this.chatService = new FactionChatService(plugin);
    }

    // ==========================================
    // TERRITORY PROTECTION - Block Break
    // Prevents players from breaking blocks in enemy territory
    // ==========================================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Faction denier = protectionDenier(event.getPlayer(), event.getBlock(), ProtectionRules.Action.BREAK);
        if (denier != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.getMessageUtils().error(
                    "You cannot break blocks in " + denier.getName() + "'s territory!"
            ));
        }
    }

    // ==========================================
    // TERRITORY PROTECTION - Block Place
    // ==========================================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Faction denier = protectionDenier(event.getPlayer(), event.getBlock(), ProtectionRules.Action.PLACE);
        if (denier != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.getMessageUtils().error(
                    "You cannot place blocks in " + denier.getName() + "'s territory!"
            ));
        }
    }

    // ==========================================
    // TERRITORY PROTECTION - Chest Interaction
    // Protects chests, furnaces, doors, etc.
    // ==========================================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only block interactions (not air)
        if (event.getClickedBlock() == null) {
            return;
        }

        Block block = event.getClickedBlock();

        // Only protect interactive blocks (chests, doors, buttons, etc.)
        if (!isProtectedBlock(block.getType())) {
            return;
        }

        Faction denier = protectionDenier(event.getPlayer(), block, ProtectionRules.Action.INTERACT);
        if (denier != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.getMessageUtils().error(
                    "You cannot use that in " + denier.getName() + "'s territory!"
            ));
        }
    }

    // ==========================================
    // Shared territory-protection check.
    // Returns the owning faction whose claim should block this player's action,
    // or null when the action is allowed (wilderness, admin bypass, own land,
    // or allied land permitted by config for this action type).
    // ==========================================
    private Faction protectionDenier(Player player, Block block, ProtectionRules.Action action) {
        var cfg = plugin.getConfigManager();
        if (!cfg.isProtectionEnabled()) {
            return null;
        }

        UUID ownerId = plugin.getClaimManager().getLocationOwner(block.getLocation());
        if (ownerId == null) {
            return null; // Wilderness - anything goes
        }

        if (plugin.getClaimManager().getBypassPlayers().contains(player.getUniqueId())) {
            return null; // Admin bypass
        }

        Faction ownerFaction = plugin.getFactionManager().getFaction(ownerId);
        if (ownerFaction == null) {
            return null;
        }

        Faction playerFaction = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());
        UUID playerFactionId = playerFaction != null ? playerFaction.getFactionId() : null;
        boolean sameFaction = playerFactionId != null && playerFactionId.equals(ownerId);
        boolean ally = playerFaction != null && ownerFaction.isAlly(playerFaction.getFactionId());
        boolean enemy = playerFaction != null && ownerFaction.isEnemy(playerFaction.getFactionId());
        boolean ownerRaidable = plugin.getPowerManager().isFactionRaidable(ownerId);

        boolean deny = ProtectionRules.shouldDeny(
                true,
                ownerId,
                playerFactionId,
                sameFaction,
                ally,
                enemy,
                ownerRaidable,
                cfg.isRaidableBypass(),
                cfg.isAlliesCanBreak(),
                cfg.isAlliesCanPlace(),
                cfg.isAlliesCanInteract(),
                action
        );

        return deny ? ownerFaction : null;
    }

    // Check if a block type should be protected
    private boolean isProtectedBlock(Material material) {
        return switch (material.name()) {
            case "CHEST", "TRAPPED_CHEST", "ENDER_CHEST",
                 "FURNACE", "BLAST_FURNACE", "SMOKER",
                 "BREWING_STAND", "ENCHANTING_TABLE",
                 "ANVIL", "GRINDSTONE", "STONECUTTER",
                 "CRAFTING_TABLE", "BARREL",
                 "SHULKER_BOX",
                 "OAK_DOOR", "SPRUCE_DOOR", "BIRCH_DOOR", "JUNGLE_DOOR",
                 "ACACIA_DOOR", "DARK_OAK_DOOR", "IRON_DOOR",
                 "OAK_FENCE_GATE", "SPRUCE_FENCE_GATE", "BIRCH_FENCE_GATE",
                 "JUNGLE_FENCE_GATE", "ACACIA_FENCE_GATE", "DARK_OAK_FENCE_GATE",
                 "LEVER", "STONE_BUTTON", "OAK_BUTTON", "SPRUCE_BUTTON",
                 "BIRCH_BUTTON", "JUNGLE_BUTTON", "ACACIA_BUTTON", "DARK_OAK_BUTTON",
                 "REPEATER", "COMPARATOR", "DAYLIGHT_DETECTOR",
                 "HOPPER", "DROPPER", "DISPENSER",
                 "BEACON", "RESPAWN_ANCHOR",
                 "LOOM", "CARTOGRAPHY_TABLE", "FLETCHING_TABLE",
                 "SMITHING_TABLE", "LECTERN", "COMPOSTER" -> true;
            default -> false;
        };
    }

    // ==========================================
    // PVP Protection - Faction vs Faction
    // Handles friendly fire toggle and territory PvP
    // ==========================================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null) {
            return;
        }

        Faction victimFaction = plugin.getFactionManager().getPlayerFaction(victim.getUniqueId());
        Faction attackerFaction = plugin.getFactionManager().getPlayerFaction(attacker.getUniqueId());

        // If neither player is in a faction, allow the hit
        if (victimFaction == null && attackerFaction == null) {
            return;
        }

        boolean sameFaction = victimFaction != null && attackerFaction != null
                && victimFaction.getFactionId().equals(attackerFaction.getFactionId());
        boolean ally = victimFaction != null && attackerFaction != null
                && victimFaction.isAlly(attackerFaction.getFactionId());
        boolean attackerIsEnemy = victimFaction != null && attackerFaction != null
                && victimFaction.isEnemy(attackerFaction.getFactionId());

        Chunk chunk = victim.getLocation().getChunk();
        UUID chunkOwner = plugin.getClaimManager().getClaimOwner(chunk);
        PvpRules.Territory territory = resolveTerritory(chunkOwner, victimFaction);

        PvpRules.Verdict verdict = PvpRules.resolve(
                sameFaction,
                plugin.getConfigManager().isRespectFactionPvpToggle(),
                victimFaction != null && victimFaction.isPvpEnabled(),
                ally,
                attackerFaction != null,
                attackerIsEnemy,
                territory,
                plugin.getConfigManager().isWildernessPvp(),
                plugin.getConfigManager().isOwnTerritoryPvp(),
                plugin.getConfigManager().isAllyTerritoryPvp(),
                plugin.getConfigManager().isEnemyTerritoryPvp());

        switch (verdict) {
            case DENY_FACTION_PVP_DISABLED -> {
                event.setCancelled(true);
                attacker.sendMessage(plugin.getMessageUtils().error("Faction PvP is disabled!"));
                return;
            }
            case DENY_ALLY -> {
                event.setCancelled(true);
                attacker.sendMessage(plugin.getMessageUtils().error("You cannot hurt your allies!"));
                return;
            }
            case DENY_TERRITORY -> {
                event.setCancelled(true);
                return;
            }
            case ALLOW_NO_TAG -> {
                // Sparring with a non-enemy in your own territory: allow the hit,
                // but skip combat tagging (no flight-lock, no combat-log punishment).
                return;
            }
            case ALLOW -> {
                // fall through to combat tagging below
            }
        }

        // Combat tag both players
        plugin.getCombatManager().tag(victim.getUniqueId());
        plugin.getCombatManager().tag(attacker.getUniqueId());

        // Cancel any pending home teleport for the victim
        plugin.getFactionCommand().cancelWarmup(victim.getUniqueId(), true);
    }

    // Classifies the victim's chunk relative to their own faction: unclaimed,
    // their own territory, an ally's, or enemy/unaffiliated territory.
    private static PvpRules.Territory resolveTerritory(UUID chunkOwner, Faction victimFaction) {
        if (chunkOwner == null) {
            return PvpRules.Territory.WILDERNESS;
        }
        if (victimFaction != null && chunkOwner.equals(victimFaction.getFactionId())) {
            return PvpRules.Territory.OWN;
        }
        if (victimFaction != null && victimFaction.isAlly(chunkOwner)) {
            return PvpRules.Territory.ALLY;
        }
        return PvpRules.Territory.OTHER;
    }

    // Cancel home warmup on any damage (environmental, fall, fire, etc.) when configured.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!plugin.getConfigManager().isHomeCancelOnDamage()) {
            return;
        }
        plugin.getFactionCommand().cancelWarmup(player.getUniqueId(), true);
    }

    // Resolves the actual attacking Player for a damage source: the damager
    // itself, whoever fired it if the damager is a projectile (arrow, trident,
    // thrown potion, etc.), the owner if it's a tamed pet (wolf sic'd on a
    // target), or whoever primed it if it's TNT — without this, indirect PvP
    // bypasses every faction/territory protection below.
    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        if (damager instanceof Tameable pet && pet.getOwner() instanceof Player owner) {
            return owner;
        }
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player igniter) {
            return igniter;
        }
        return null;
    }

    // ==========================================
    // TNT Protection - Entity Explode
    // Respects faction TNT toggle
    // ==========================================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isTntProtected);
    }

    // ==========================================
    // Block Explode Protection - Beds / Respawn Anchors
    // Catches explosions that bypass EntityExplodeEvent
    // ==========================================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isTntProtected);
    }

    // True if this block must survive the explosion: either it's in the
    // wilderness and wilderness damage is disabled, or it's in a claim where
    // explosions are disabled or the owning faction has turned TNT off.
    private boolean isTntProtected(Block block) {
        UUID ownerId = plugin.getClaimManager().getLocationOwner(block.getLocation());
        if (ownerId == null) {
            return !plugin.getConfigManager().isExplosionDamageWilderness();
        }
        if (!plugin.getConfigManager().isExplosionsInClaims()) {
            return true;
        }
        if (!plugin.getConfigManager().isRespectFactionTntToggle()) {
            return false;
        }
        Faction ownerFaction = plugin.getFactionManager().getFaction(ownerId);
        return ownerFaction != null && !ownerFaction.isTntEnabled();
    }

    // ==========================================
    // Bucket Protection - Lava/water griefing
    // Emptying a bucket is placing a block; filling is taking one.
    // ==========================================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Block target = event.getBlockClicked().getRelative(event.getBlockFace());
        Faction denier = protectionDenier(event.getPlayer(), target, ProtectionRules.Action.PLACE);
        if (denier != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.getMessageUtils().error(
                    "You cannot place liquids in " + denier.getName() + "'s territory!"
            ));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Faction denier = protectionDenier(event.getPlayer(), event.getBlockClicked(), ProtectionRules.Action.BREAK);
        if (denier != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.getMessageUtils().error(
                    "You cannot take liquids from " + denier.getName() + "'s territory!"
            ));
        }
    }

    // ==========================================
    // Fire Protection - Ignition and burn damage in claims
    // ==========================================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (event.getPlayer() != null) {
            // Player-lit fire follows the normal place rules (own land allowed)
            Faction denier = protectionDenier(event.getPlayer(), event.getBlock(), ProtectionRules.Action.PLACE);
            if (denier != null) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(plugin.getMessageUtils().error(
                        "You cannot light fires in " + denier.getName() + "'s territory!"
                ));
            }
            return;
        }
        // No player involved (spread, lava, fireball): never let fire creep
        // across a border into claimed territory.
        if (plugin.getConfigManager().isProtectionEnabled()
                && plugin.getClaimManager().getLocationOwner(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (plugin.getConfigManager().isProtectionEnabled()
                && plugin.getClaimManager().getLocationOwner(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    // ==========================================
    // Entity Grief Protection - Enderman theft, ravagers, wither block changes
    // Falling blocks are excluded so sand/gravel physics still work.
    // ==========================================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof Player || event.getEntity() instanceof FallingBlock) {
            return;
        }
        if (plugin.getConfigManager().isProtectionEnabled()
                && plugin.getClaimManager().getLocationOwner(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    // ==========================================
    // Piston Protection - Prevent pushing blocks out of claims
    // ==========================================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (crossesClaimBoundary(event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (crossesClaimBoundary(event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    // True if moving any of these blocks one step in `direction` would push or
    // pull it across a claim boundary into claimed territory, which pistons must
    // not do. Movement entirely within one claim, or out into wilderness, is fine.
    private boolean crossesClaimBoundary(List<Block> blocks, BlockFace direction) {
        for (Block block : blocks) {
            Block target = block.getRelative(direction);
            UUID sourceOwner = plugin.getClaimManager().getLocationOwner(block.getLocation());
            UUID targetOwner = plugin.getClaimManager().getLocationOwner(target.getLocation());
            if (targetOwner != null && !targetOwner.equals(sourceOwner)) {
                return true;
            }
        }
        return false;
    }

    // ==========================================
    // PLAYER MOVE - Territory Border Detection & AutoClaim
    // Shows entry/exit messages when crossing borders
    // Handles auto-claiming chunks
    // ==========================================
    // Player move — use NORMAL priority since this handler doesn't cancel events;
    // HIGHEST is reserved for the cancellation handlers above.
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        territoryMovementHandler.handle(event);
    }

    // ==========================================
    // PLAYER JOIN
    // Updates name cache, shows MOTD, handles login time
    // ==========================================
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        // Update the name cache
        plugin.getPlayerNameCache().updateName(player);

        // Decay power for the time spent offline (based on the stored logout
        // time) before stamping the new login time, so power.offline-decay-*
        // actually takes effect. No-op when offline decay is disabled.
        plugin.getPowerManager().handleOfflineDecay(playerUuid);

        // Update login time for power regen (persisted via PowerManager dirty flag)
        plugin.getPowerManager().updateLoginTime(playerUuid);

        // Give daily elixir bonus
        plugin.getElixirManager().onPlayerLogin(playerUuid);

        // Show faction MOTD if they're in a faction
        Faction faction = plugin.getFactionManager().getPlayerFaction(playerUuid);
        if (faction != null && faction.hasMotd()) {
            // Small delay so the MOTD shows after join messages
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                player.sendMessage(plugin.getMessageUtils().header("Faction MOTD"));
                player.sendMessage(plugin.getMessageUtils().info(faction.getMotd()));
            }, 20L); // 1 second delay
        }

        plugin.getLogger().info(player.getName() + " joined - name cached, power data loaded.");
    }

    // ==========================================
    // PLAYER QUIT
    // Saves logout time for power decay calc
    // ==========================================
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        plugin.getPowerManager().updateLogoutTime(playerUuid);

        // Cancel any pending home-teleport warmup so it doesn't fire against
        // a disconnected player.
        plugin.getFactionCommand().cancelWarmup(playerUuid, false);

        // Combat tag check — punish combat loggers
        if (plugin.getCombatManager().handleQuit(playerUuid)) {
            if (plugin.getConfigManager().isCombatTagKillOnQuit()) {
                // PlayerQuitEvent runs while the connection is already closing,
                // so setHealth(0) here frequently doesn't propagate through the
                // normal PlayerDeathEvent pipeline. Apply the power-loss penalty
                // directly instead of relying on that event to fire.
                plugin.getPowerManager().onPlayerDeath(playerUuid);
                player.setHealth(0);
                plugin.getLogger().warning(player.getName() + " combat-logged and was killed.");
            }
        }

        territoryMovementHandler.clear(playerUuid);
    }

    // ==========================================
    // PLAYER DEATH
    // Handles power loss, elixir gains, kill tracking
    // ==========================================
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        UUID victimUuid = victim.getUniqueId();

        Player killer = victim.getKiller();

        // Environmental / mob death: apply the PvE power loss (a no-op when
        // power.loss-on-pve-death is 0) instead of the heavier PvP penalty.
        if (killer == null) {
            plugin.getPowerManager().onPlayerPveDeath(victimUuid);
            return;
        }

        // PvP death: victim takes the PvP power loss, killer gains power.
        // Ignore self-inflicted kills (e.g. damage potions, explosions)
        if (killer.equals(victim)) {
            plugin.getPowerManager().onPlayerDeath(victimUuid);
            plugin.getCombatManager().clear(victimUuid);
            return;
        }
        plugin.getPowerManager().onPlayerDeath(victimUuid);

        UUID killerUuid = killer.getUniqueId();
        plugin.getPowerManager().onPlayerKill(killerUuid);

        // Clear combat tags
        plugin.getCombatManager().clear(victimUuid);
        plugin.getCombatManager().clear(killerUuid);

        // Check faction vs faction combat
        Faction victimFaction = plugin.getFactionManager().getPlayerFaction(victimUuid);
        Faction killerFaction = plugin.getFactionManager().getPlayerFaction(killerUuid);

        if (victimFaction != null && killerFaction != null
                && victimFaction.isEnemy(killerFaction.getFactionId())) {
            plugin.getElixirManager().onEnemyKill(killerFaction.getFactionId());
            killer.sendMessage(plugin.getMessageUtils().success(
                    "Enemy kill! Elixir earned for " + killerFaction.getName()
            ));
        }
    }

    // ==========================================
    // PLAYER CHAT - Faction Chat / Ally Chat
    // Intercepts chat messages and routes them
    // ==========================================
    @EventHandler
    public void onPlayerChat(AsyncChatEvent event) {
        chatService.handle(event);
    }
}