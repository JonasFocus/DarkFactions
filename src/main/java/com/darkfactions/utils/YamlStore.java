package com.darkfactions.utils;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Shared, crash-safe YAML persistence for the data managers.
 *
 * <p>Wraps {@link AtomicFiles} so every manager saves through a single
 * implementation that (a) stamps a schema {@code version}, (b) writes
 * atomically with a {@code .bak} rollover, and (c) on load falls back to the
 * backup instead of silently starting empty — which the next auto-save would
 * otherwise persist, permanently erasing the data.
 */
public final class YamlStore {

    /** Current on-disk schema version, stamped into every saved file. */
    public static final int SCHEMA_VERSION = 1;

    /** Root key carrying the schema version. */
    public static final String VERSION_KEY = "version";

    private YamlStore() {
    }

    /**
     * Stamp the schema version and persist atomically. Never throws — an I/O
     * failure is logged and the previous on-disk file is left intact.
     */
    public static void save(FileConfiguration config, File target, Logger log) {
        config.set(VERSION_KEY, SCHEMA_VERSION);
        try {
            AtomicFiles.writeAtomically(target, config::save);
        } catch (IOException e) {
            if (log != null) {
                log.severe("Failed to save " + target.getName() + " (previous file kept): " + e.getMessage());
            }
        }
    }

    /**
     * Load a YAML file, falling back to its {@code .bak} if the primary is
     * missing or corrupt. Returns an empty configuration only when neither file
     * is usable.
     */
    public static YamlConfiguration load(File target, Logger log) {
        YamlConfiguration primary = tryLoad(target, log);
        if (primary != null) {
            warnIfNewer(primary, target, log);
            return primary;
        }

        File bak = AtomicFiles.backupFile(target);
        YamlConfiguration backup = tryLoad(bak, log);
        if (backup != null) {
            if (log != null) {
                log.warning("Recovered " + target.getName() + " from backup " + bak.getName());
            }
            return backup;
        }

        return new YamlConfiguration();
    }

    private static YamlConfiguration tryLoad(File file, Logger log) {
        if (file == null || !file.exists()) {
            return null;
        }
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
            return config;
        } catch (IOException | InvalidConfigurationException e) {
            if (log != null) {
                log.severe("Could not parse " + file.getName() + ": " + e.getMessage());
            }
            return null;
        }
    }

    private static void warnIfNewer(FileConfiguration config, File target, Logger log) {
        int version = config.getInt(VERSION_KEY, 0);
        if (version > SCHEMA_VERSION && log != null) {
            log.warning(target.getName() + " was written by a newer schema (v" + version
                    + " > v" + SCHEMA_VERSION + "); loading anyway.");
        }
    }
}
