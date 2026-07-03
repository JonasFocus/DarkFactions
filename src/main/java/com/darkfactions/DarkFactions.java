package com.darkfactions;

import com.darkfactions.commands.FactionCommand;
import com.darkfactions.commands.FactionTabCompleter;
import com.darkfactions.listeners.FactionListener;
import com.darkfactions.managers.ClaimManager;
import com.darkfactions.managers.CombatManager;
import com.darkfactions.managers.ElixirManager;
import com.darkfactions.managers.FactionManager;
import com.darkfactions.managers.PlayerNameCache;
import com.darkfactions.managers.PowerManager;
import com.darkfactions.storage.DatabaseManager;
import com.darkfactions.storage.DataStore;
import com.darkfactions.storage.SaveQueue;
import com.darkfactions.storage.SqlStore;
import com.darkfactions.utils.ConfigManager;
import com.darkfactions.utils.MessageUtils;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;
import java.util.concurrent.TimeUnit;

public class DarkFactions extends JavaPlugin {

    private static volatile DarkFactions instance;

    private DatabaseManager databaseManager;
    private SaveQueue saveQueue;
    private DataStore dataStore;

    private CombatManager combatManager;
    private FactionManager factionManager;
    private PowerManager powerManager;
    private ElixirManager elixirManager;
    private ClaimManager claimManager;
    private PlayerNameCache playerNameCache;
    private ConfigManager configManager;
    private MessageUtils messageUtils;

    private FactionCommand factionCommand;

    private int autoSaveTaskId = -1;
    private boolean migrateLegacyFactionPower;

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("DarkFactions is loading...");

        saveDefaultConfig();
        this.configManager = new ConfigManager(this);
        this.messageUtils = new MessageUtils();

        if (!initDatabase()) {
            return;
        }

        this.combatManager = new CombatManager(this);
        this.factionManager = new FactionManager(this);
        this.powerManager = new PowerManager(this);
        this.elixirManager = new ElixirManager(this);
        this.claimManager = new ClaimManager(this);
        this.playerNameCache = new PlayerNameCache(this);

        this.factionCommand = new FactionCommand(this);
        var command = getCommand("faction");
        command.setExecutor(factionCommand);
        command.setTabCompleter(new FactionTabCompleter(this));

        getServer().getPluginManager().registerEvents(new FactionListener(this), this);

        loadAllData();
        startAutoSaveTask();

        getLogger().info("DarkFactions has been enabled!");
    }

    private boolean initDatabase() {
        try {
            DatabaseManager.Type dbType = configManager.getDatabaseType();
            String host = configManager.getDatabaseHost();
            int port = configManager.getDatabasePort();
            String dbName = configManager.getDatabaseName();
            String user = configManager.getDatabaseUsername();
            String pass = configManager.getDatabasePassword();

            this.databaseManager = new DatabaseManager(this, dbType, host, port, dbName, user, pass);
            this.dataStore = new SqlStore(this, databaseManager);
            this.dataStore.createTables();

            int schemaVer = dataStore.getSchemaVersion();
            migrateLegacyFactionPower = schemaVer < 2;
            if (schemaVer < 1) {
                dataStore.setSchemaVersion(1);
                schemaVer = 1;
            }
            if (schemaVer < 2) {
                dataStore.migrateSchema(schemaVer);
                dataStore.setSchemaVersion(2);
            }

            this.saveQueue = new SaveQueue(dataStore, 2);

            getLogger().info("Database initialized (" + dbType + ")");
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialize database — disabling plugin", e);
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
    }

    private void startAutoSaveTask() {
        int intervalSeconds = configManager.getAutoSaveInterval();
        if (intervalSeconds <= 0) return;

        long ticks = 20L * intervalSeconds;
        autoSaveTaskId = getServer().getScheduler().runTaskTimer(this, () -> {
            try {
                persistAll();
                getLogger().info("Auto-saved DarkFactions data.");
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Auto-save failed", e);
            }
        }, ticks, ticks).getTaskId();
    }

    private void loadAllData() {
        playerNameCache.loadFromStore(dataStore);
        factionManager.loadFromStore(dataStore);
        claimManager.loadFromStore(dataStore);
        powerManager.loadFromStore(dataStore);
        if (migrateLegacyFactionPower) {
            powerManager.migrateLegacyBonusPower();
            migrateLegacyFactionPower = false;
        }
        elixirManager.loadFromStore(dataStore);
    }

    public void persistAll() {
        if (factionManager != null) factionManager.saveToStoreAsync(saveQueue);
        if (claimManager != null) claimManager.saveToStoreAsync(saveQueue);
        if (powerManager != null) powerManager.saveToStoreAsync(saveQueue);
        if (elixirManager != null) elixirManager.saveToStoreAsync(saveQueue);
        if (playerNameCache != null) playerNameCache.saveToStoreAsync(saveQueue);
    }

    /** Synchronous persistence for all managers; used during shutdown. */
    public void persistAllSync() {
        if (dataStore == null) return;
        if (factionManager != null) factionManager.saveToStoreSync(dataStore);
        if (claimManager != null) claimManager.saveToStoreSync(dataStore);
        if (powerManager != null) powerManager.saveToStoreSync(dataStore);
        if (elixirManager != null) elixirManager.saveToStoreSync(dataStore);
        if (playerNameCache != null) playerNameCache.saveToStoreSync(dataStore);
    }

    @Override
    public void onDisable() {
        getLogger().info("DarkFactions is shutting down... saving data...");

        if (autoSaveTaskId != -1) {
            getServer().getScheduler().cancelTask(autoSaveTaskId);
            autoSaveTaskId = -1;
        }

        persistAllSync();
        if (saveQueue != null) {
            saveQueue.flushAndAwait(30, TimeUnit.SECONDS);
            saveQueue.shutdown();
        }
        if (databaseManager != null) databaseManager.close();

        instance = null;
        getLogger().info("DarkFactions has been disabled.");
    }

    public static DarkFactions getInstance() {
        return instance;
    }

    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public DataStore getDataStore() { return dataStore; }
    public SaveQueue getSaveQueue() { return saveQueue; }
    public CombatManager getCombatManager() { return combatManager; }
    public FactionManager getFactionManager() { return factionManager; }
    public PowerManager getPowerManager() { return powerManager; }
    public ElixirManager getElixirManager() { return elixirManager; }
    public ClaimManager getClaimManager() { return claimManager; }
    public PlayerNameCache getPlayerNameCache() { return playerNameCache; }
    public ConfigManager getConfigManager() { return configManager; }
    public MessageUtils getMessageUtils() { return messageUtils; }
    public FactionCommand getFactionCommand() { return factionCommand; }
}