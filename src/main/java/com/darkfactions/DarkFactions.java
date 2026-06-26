package com.darkfactions;

// ==========================================
// DarkFactions - Main Plugin Class
// This is the entry point for the whole plugin
// Everything starts here when the server loads us
// ==========================================

import com.darkfactions.commands.FactionCommand;
import com.darkfactions.commands.FactionTabCompleter;
import com.darkfactions.listeners.FactionListener;
import com.darkfactions.managers.ClaimManager;
import com.darkfactions.managers.ElixirManager;
import com.darkfactions.managers.FactionManager;
import com.darkfactions.managers.PlayerNameCache;
import com.darkfactions.managers.PowerManager;
import com.darkfactions.utils.ConfigManager;
import com.darkfactions.utils.MessageUtils;

import org.bukkit.plugin.java.JavaPlugin;

public class DarkFactions extends JavaPlugin {

    // Singleton instance so other classes can access the plugin easily
    private static DarkFactions instance;

    // All our managers that handle the heavy lifting
    private FactionManager factionManager;
    private PowerManager powerManager;
    private ElixirManager elixirManager;
    private ClaimManager claimManager;
    private PlayerNameCache playerNameCache;
    private ConfigManager configManager;
    private MessageUtils messageUtils;

    // Reference to the command handler so the listener can access chat modes
    private FactionCommand factionCommand;

    // Task ID for the periodic auto-save scheduler (-1 when not running)
    private int autoSaveTaskId = -1;

    // Debounced "save soon" task: coalesces bursts of mutations into one save.
    private int pendingSaveTaskId = -1;
    private static final long SAVE_DEBOUNCE_TICKS = 60L; // ~3 seconds

    // Called when the server enables/loads our plugin
    @Override
    public void onEnable() {

        // Store static instance for easy access elsewhere
        instance = this;

        // Log that we're starting up - nice for debugging
        getLogger().info("DarkFactions is loading... brace yourself!");

        // ==========================================
        // Load config files
        // ==========================================
        saveDefaultConfig(); // Saves config.yml from resources if it doesn't exist
        this.configManager = new ConfigManager(this);

        // ==========================================
        // Initialize message utilities
        // ==========================================
        this.messageUtils = new MessageUtils();

        // ==========================================
        // Initialize managers
        // Order matters - FactionManager needs to be first
        // ==========================================
        this.factionManager = new FactionManager(this);
        this.powerManager = new PowerManager(this);
        this.elixirManager = new ElixirManager(this);
        this.claimManager = new ClaimManager(this);
        this.playerNameCache = new PlayerNameCache(this);

        // ==========================================
        // Register our main /f command
        // ==========================================
        this.factionCommand = new FactionCommand(this);
        getCommand("faction").setExecutor(factionCommand);
        getCommand("faction").setTabCompleter(new FactionTabCompleter());

        // ==========================================
        // Register event listeners
        // Handles things like player join/quit, deaths, etc.
        // ==========================================
        getServer().getPluginManager().registerEvents(new FactionListener(this), this);

        // ==========================================
        // Load all saved data from disk
        // ==========================================
        playerNameCache.loadNames();
        factionManager.loadFactions();
        claimManager.loadClaims();
        powerManager.loadPowerData();
        elixirManager.loadElixirData();

        // ==========================================
        // Start the periodic auto-save so a crash
        // never wipes out faction data between restarts
        // ==========================================
        startAutoSaveTask();

        // We made it!
        getLogger().info("DarkFactions has been enabled! Ready for battle!");
    }

    // ==========================================
    // Auto-save - persists all data on an interval
    // Interval comes from general.auto-save-interval-seconds
    // (0 disables it). Runs on the main thread so it reads
    // a consistent snapshot of the in-memory data.
    // ==========================================
    private void startAutoSaveTask() {
        if (autoSaveTaskId != -1) {
            getServer().getScheduler().cancelTask(autoSaveTaskId);
            autoSaveTaskId = -1;
        }

        int intervalSeconds = configManager.getAutoSaveInterval();
        if (intervalSeconds <= 0) {
            return; // Auto-save disabled
        }

        long ticks = 20L * intervalSeconds;
        autoSaveTaskId = getServer().getScheduler().runTaskTimer(this, () -> {
            saveAll();
            getLogger().info("Auto-saved DarkFactions data.");
        }, ticks, ticks).getTaskId();
    }

    // ==========================================
    // Request a save "soon" after a high-value mutation (faction create/disband/
    // rename, claim/unclaim, elixir change, leadership transfer). Coalesces a
    // burst of changes into a single debounced save so a crash loses at most a
    // few seconds of data instead of up to a full auto-save interval. Runs on
    // the main thread, consistent with the rest of our persistence.
    // ==========================================
    public void requestSave() {
        if (pendingSaveTaskId != -1 || !isEnabled()) {
            return; // a save is already queued, or we're shutting down
        }
        pendingSaveTaskId = getServer().getScheduler().runTaskLater(this, () -> {
            pendingSaveTaskId = -1;
            saveAll();
        }, SAVE_DEBOUNCE_TICKS).getTaskId();
    }

    // Persist every manager's data. Shared by auto-save, the debounced
    // requestSave, and shutdown.
    public void saveAll() {
        if (playerNameCache != null) {
            playerNameCache.saveNames();
        }
        if (factionManager != null) {
            factionManager.saveFactions();
        }
        if (claimManager != null) {
            claimManager.saveClaims();
        }
        if (powerManager != null) {
            powerManager.savePowerData();
        }
        if (elixirManager != null) {
            elixirManager.saveElixirData();
        }
    }

    // Called when the server disables/unloads our plugin
    @Override
    public void onDisable() {

        // Save everything before we shut down
        // Dont want to lose faction data!
        getLogger().info("DarkFactions is shutting down... saving data...");

        // Stop the auto-save and any pending debounced save so they can't fire
        // mid-shutdown; the final saveAll below persists everything anyway.
        if (autoSaveTaskId != -1) {
            getServer().getScheduler().cancelTask(autoSaveTaskId);
            autoSaveTaskId = -1;
        }
        if (pendingSaveTaskId != -1) {
            getServer().getScheduler().cancelTask(pendingSaveTaskId);
            pendingSaveTaskId = -1;
        }

        saveAll();

        getLogger().info("DarkFactions has been disabled. See you next time!");
    }

    // ==========================================
    // Getters - so other classes can access our stuff
    // ==========================================

    public static DarkFactions getInstance() {
        return instance;
    }

    public FactionManager getFactionManager() {
        return factionManager;
    }

    public PowerManager getPowerManager() {
        return powerManager;
    }

    public ElixirManager getElixirManager() {
        return elixirManager;
    }

    public ClaimManager getClaimManager() {
        return claimManager;
    }

    public PlayerNameCache getPlayerNameCache() {
        return playerNameCache;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageUtils getMessageUtils() {
        return messageUtils;
    }

    public FactionCommand getFactionCommand() {
        return factionCommand;
    }
}