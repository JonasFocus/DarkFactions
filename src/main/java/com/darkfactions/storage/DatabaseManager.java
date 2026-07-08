package com.darkfactions.storage;

import com.darkfactions.DarkFactions;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseManager implements AutoCloseable {

    public enum Type { SQLITE, MYSQL }

    private final Logger logger;
    private final File dataFolder;
    private final Type type;
    private HikariDataSource dataSource;

    public DatabaseManager(DarkFactions plugin, Type type, String host, int port, String database,
                           String username, String password) {
        this(plugin.getLogger(), plugin.getDataFolder(), type, host, port, database, username, password);
    }

    /** Test-friendly constructor that does not require a live Bukkit plugin. */
    public DatabaseManager(Logger logger, File dataFolder, Type type, String host, int port, String database,
                           String username, String password) {
        this.logger = logger;
        this.dataFolder = dataFolder;
        this.type = type;
        init(type, host, port, database, username, password);
    }

    private void init(Type type, String host, int port, String database, String username, String password) {
        HikariConfig config = new HikariConfig();

        if (type == Type.SQLITE) {
            File dbFile = new File(dataFolder, database);
            config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            config.setDriverClassName("org.sqlite.JDBC");
            config.setPoolName("DarkFactions-SQLite");
            config.setMaximumPoolSize(1);
            config.setConnectionTestQuery("SELECT 1");
        } else {
            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true");
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            config.setUsername(username);
            config.setPassword(password);
            config.setPoolName("DarkFactions-MySQL");
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setMaxLifetime(1800000);
            config.setConnectionTimeout(5000);
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");
            config.addDataSourceProperty("rewriteBatchedStatements", "true");
        }

        try {
            this.dataSource = new HikariDataSource(config);
            logger.info("Database connection established (" + type + ")");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize database connection", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Database connection pool is closed");
        }
        return dataSource.getConnection();
    }

    public Type getType() {
        return type;
    }

    public boolean isRunning() {
        return dataSource != null && !dataSource.isClosed();
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Database connection pool closed.");
        }
    }
}