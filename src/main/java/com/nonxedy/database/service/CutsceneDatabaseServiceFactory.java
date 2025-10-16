package com.nonxedy.database.service;

import java.io.File;

import com.nonxedy.database.exception.DatabaseException;
import com.nonxedy.database.service.impl.SQLiteCutsceneDatabaseService;

/**
 * Factory for creating database service instances
 */
public class CutsceneDatabaseServiceFactory {

    public enum DatabaseType {
        SQLITE,
        MYSQL,
        POSTGRESQL,
        MONGODB,
        REDIS
    }

    /**
     * Create a database service instance based on type
     * @param type The database type
     * @param config Configuration parameters (file path for SQLite, connection string for others)
     * @return Database service instance
     * @throws DatabaseException if creation fails
     */
    public static CutsceneDatabaseService createService(DatabaseType type, String config) throws DatabaseException {
        switch (type) {
            case SQLITE:
                if (config == null || config.trim().isEmpty()) {
                    throw new DatabaseException("SQLite database file path is required");
                }
                File dbFile = new File(config.trim());
                return new SQLiteCutsceneDatabaseService(dbFile);

            case MYSQL:
                // For now, throw exception as MySQL implementation is not ready
                throw new DatabaseException("MySQL service implementation is in progress. Please use SQLITE for now.");

            case POSTGRESQL:
                throw new DatabaseException("PostgreSQL service implementation is in progress. Please use SQLITE for now.");

            case MONGODB:
                throw new DatabaseException("MongoDB service implementation is in progress. Please use SQLITE for now.");

            case REDIS:
                throw new DatabaseException("Redis service implementation is in progress. Please use SQLITE for now.");

            default:
                throw new DatabaseException("Unsupported database type: " + type);
        }
    }

    /**
     * Create a SQLite database service with default path
     * @param pluginDataFolder The plugin's data folder
     * @return SQLite database service
     */
    public static CutsceneDatabaseService createSQLiteService(File pluginDataFolder) throws DatabaseException {
        File dbFile = new File(pluginDataFolder, "cutscenes.db");
        return createService(DatabaseType.SQLITE, dbFile.getAbsolutePath());
    }
}
