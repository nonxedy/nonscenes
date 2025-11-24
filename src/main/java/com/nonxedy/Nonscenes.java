package com.nonxedy;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import com.nonxedy.command.NonsceneCommand;
import com.nonxedy.core.ConfigManager;
import com.nonxedy.core.CutsceneManager;
import com.nonxedy.database.exception.DatabaseException;
import com.nonxedy.database.service.CutsceneDatabaseService;
import com.nonxedy.database.service.CutsceneDatabaseServiceFactory;
import com.nonxedy.listener.PlayerInputListener;
import com.nonxedy.listener.WorldLoadListener;

/**
 * Main plugin class for Nonscenes
 */
public class Nonscenes extends JavaPlugin {
    private static final Logger LOGGER = Logger.getLogger("nonscenes");
    private ConfigManager configManager;
    private CutsceneManager cutsceneManager;
    private CutsceneDatabaseService databaseService;

    @Override
    public void onEnable() {
        try {
            // Initialize config manager
            configManager = new ConfigManager(this);
            configManager.loadConfigs();

            // Initialize database service based on configuration
            databaseService = initializeDatabaseService();
            databaseService.initialize();

            // Initialize cutscene manager
            cutsceneManager = new CutsceneManager(this);

            // Register commands
            NonsceneCommand nonsceneCommand = new NonsceneCommand(this);
            PluginCommand command = getCommand("nonscene");
            if (command != null) {
                command.setExecutor(nonsceneCommand);
                command.setTabCompleter(nonsceneCommand);
            }

            // Register world load listener to load cutscenes after worlds are available
            getServer().getPluginManager().registerEvents(new WorldLoadListener(this), this);

            // Register player input listener to disable input during cutscene playback
            getServer().getPluginManager().registerEvents(new PlayerInputListener(this), this);

            // Fallback: load cutscenes after a delay in case no world load events fire
            getServer().getScheduler().runTaskLater(this, () -> {
                WorldLoadListener listener =
                    new WorldLoadListener(this);
                listener.loadCutscenesIfNeeded();
            }, 100L); // 5 seconds delay

            LOGGER.info("nonscenes enabled with database support");
        } catch (DatabaseException e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize database: {0}", e.getMessage());
            LOGGER.severe("Plugin will be disabled");
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        // Clean up resources
        if (cutsceneManager != null) {
            cutsceneManager.cleanup();
        }

        if (databaseService != null) {
            databaseService.shutdown();
        }

        LOGGER.info("nonscenes disabled");
    }
    
    /**
     * Gets the config manager
     * @return The config manager
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    /**
     * Gets the cutscene manager
     * @return The cutscene manager
     */
    public CutsceneManager getCutsceneManager() {
        return cutsceneManager;
    }

    /**
     * Gets the database service
     * @return The database service
     */
    public CutsceneDatabaseService getDatabaseService() {
        return databaseService;
    }

    /**
     * Initialize database service based on configuration
     * @return Database service instance
     * @throws DatabaseException if initialization fails
     */
    private CutsceneDatabaseService initializeDatabaseService() throws DatabaseException {
        String storageType = configManager.getConfig().getString("storage.type", "SQLITE").toUpperCase();

        try {
            CutsceneDatabaseServiceFactory.DatabaseType type =
                CutsceneDatabaseServiceFactory.DatabaseType.valueOf(storageType);
            String configPath = getDatabaseConfigPath(type);

            LOGGER.log(Level.INFO, "Initializing {0} database service...", storageType);

            return CutsceneDatabaseServiceFactory.createService(type, configPath);

        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "Unknown storage type: {0}. Falling back to SQLITE.", storageType);
            return CutsceneDatabaseServiceFactory.createSQLiteService(getDataFolder());
        }
    }

    /**
     * Get database configuration path based on type
     * @param type Database type
     * @return Configuration string for the factory
     */
    private String getDatabaseConfigPath(CutsceneDatabaseServiceFactory.DatabaseType type) {
        switch (type) {
            case SQLITE -> {
                String sqlitePath = configManager.getConfig().getString("storage.sqlite.file-path", "cutscenes.db");
                return new File(getDataFolder(), sqlitePath).getAbsolutePath();
            }

            case MYSQL -> {
                return buildMySQLConnectionString();
            }

            case POSTGRESQL -> {
                return buildPostgreSQLConnectionString();
            }

            case MONGODB -> {
                return buildMongoDBConnectionString();
            }

            case REDIS -> {
                return buildRedisConnectionString();
            }

            default -> {
                return "";
            }
        }
    }

    private String buildMySQLConnectionString() {
        String host = configManager.getConfig().getString("storage.mysql.host", "localhost");
        int port = configManager.getConfig().getInt("storage.mysql.port", 3306);
        String database = configManager.getConfig().getString("storage.mysql.database", "minecraft");
        String username = configManager.getConfig().getString("storage.mysql.username", "root");
        String password = configManager.getConfig().getString("storage.mysql.password", "password");
        String prefix = configManager.getConfig().getString("storage.mysql.table-prefix", "nonscenes_");

        return String.format("jdbc:mysql://%s:%d/%s?user=%s&password=%s&tablePrefix=%s",
                           host, port, database, username, password, prefix);
    }

    private String buildPostgreSQLConnectionString() {
        String host = configManager.getConfig().getString("storage.postgresql.host", "localhost");
        int port = configManager.getConfig().getInt("storage.postgresql.port", 5432);
        String database = configManager.getConfig().getString("storage.postgresql.database", "minecraft");
        String username = configManager.getConfig().getString("storage.postgresql.username", "postgres");
        String password = configManager.getConfig().getString("storage.postgresql.password", "password");
        String schema = configManager.getConfig().getString("storage.postgresql.schema", "public");

        return String.format("jdbc:postgresql://%s:%d/%s?user=%s&password=%s&schema=%s",
                           host, port, database, username, password, schema);
    }

    private String buildMongoDBConnectionString() {
        String host = configManager.getConfig().getString("storage.mongodb.host", "localhost");
        int port = configManager.getConfig().getInt("storage.mongodb.port", 27017);
        String database = configManager.getConfig().getString("storage.mongodb.database", "minecraft");
        String collection = configManager.getConfig().getString("storage.mongodb.collection", "cutscenes");
        String username = configManager.getConfig().getString("storage.mongodb.username", "");
        String password = configManager.getConfig().getString("storage.mongodb.password", "");

        StringBuilder connectionString = new StringBuilder("mongodb://");

        if (!username.isEmpty() && !password.isEmpty()) {
            connectionString.append(username).append(":").append(password).append("@");
        }

        connectionString.append(host).append(":").append(port);
        connectionString.append("/").append(database);
        connectionString.append("?collection=").append(collection);

        return connectionString.toString();
    }

    private String buildRedisConnectionString() {
        String host = configManager.getConfig().getString("storage.redis.host", "localhost");
        int port = configManager.getConfig().getInt("storage.redis.port", 6379);
        int database = configManager.getConfig().getInt("storage.redis.database", 0);
        String password = configManager.getConfig().getString("storage.redis.password", "");

        return String.format("redis://%s:%d/%d?password=%s", host, port, database, password);
    }
}
