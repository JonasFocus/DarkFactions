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

        // We made it!
        getLogger().info("DarkFactions has been enabled! Ready for battle!");
    }

    // Called when the server disables/unloads our plugin
    @Override
    public void onDisable() {

        // Save everything before we shut down
        // Dont want to lose faction data!
        getLogger().info("DarkFactions is shutting down... saving data...");

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