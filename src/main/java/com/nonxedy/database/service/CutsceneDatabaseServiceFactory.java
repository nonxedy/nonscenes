package com.nonxedy.database.service;

import java.io.File;

import com.nonxedy.database.exception.DatabaseException;
import com.nonxedy.database.service.impl.MongoDBCutsceneDatabaseService;
import com.nonxedy.database.service.impl.MySQLCutsceneDatabaseService;
import com.nonxedy.database.service.impl.PostgreSQLCutsceneDatabaseService;
import com.nonxedy.database.service.impl.RedisCutsceneDatabaseService;
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
            case SQLITE -> {
                if (config == null || config.trim().isEmpty()) {
                    throw new DatabaseException("SQLite database file path is required");
                }
                File dbFile = new File(config.trim());
                return new SQLiteCutsceneDatabaseService(dbFile);
            }

            case MYSQL -> {
                if (config == null || config.trim().isEmpty()) {
                    throw new DatabaseException("MySQL connection string is required");
                }
                // Parse connection string: jdbc:mysql://host:port/database?user=username&password=password&tablePrefix=prefix
                return parseMySQLConfig(config.trim());
            }

            case POSTGRESQL -> {
                if (config == null || config.trim().isEmpty()) {
                    throw new DatabaseException("PostgreSQL connection string is required");
                }
                return parsePostgreSQLConfig(config.trim());
            }

            case MONGODB -> {
                if (config == null || config.trim().isEmpty()) {
                    throw new DatabaseException("MongoDB connection string is required");
                }
                return parseMongoDBConfig(config.trim());
            }

            case REDIS -> {
                if (config == null || config.trim().isEmpty()) {
                    throw new DatabaseException("Redis connection config is required");
                }
                return parseRedisConfig(config.trim());
            }

            default -> throw new DatabaseException("Unsupported database type: " + type);
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

    private static CutsceneDatabaseService parseMySQLConfig(String config) throws DatabaseException {
        try {
            // Expected format: jdbc:mysql://host:port/database?user=username&password=password&tablePrefix=prefix
            String connectionString = config;
            String tablePrefix = "nonscenes_";

            // Extract table prefix if present
            if (config.contains("&tablePrefix=")) {
                String[] parts = config.split("&tablePrefix=");
                connectionString = parts[0];
                tablePrefix = parts[1];
            }

            return new MySQLCutsceneDatabaseService(connectionString, tablePrefix);
        } catch (Exception e) {
            throw new DatabaseException("Invalid MySQL configuration format", e);
        }
    }

    private static CutsceneDatabaseService parsePostgreSQLConfig(String config) throws DatabaseException {
        try {
            // Expected format: jdbc:postgresql://host:port/database?user=username&password=password&schema=schema
            String connectionString = config;
            String schema = "public";

            // Extract schema if present
            if (config.contains("&schema=")) {
                String[] parts = config.split("&schema=");
                connectionString = parts[0];
                schema = parts[1];
            }

            return new PostgreSQLCutsceneDatabaseService(connectionString, schema);
        } catch (Exception e) {
            throw new DatabaseException("Invalid PostgreSQL configuration format", e);
        }
    }

    private static CutsceneDatabaseService parseMongoDBConfig(String config) throws DatabaseException {
        try {
            // Expected format: mongodb://username:password@host:port/database?collection=collection
            String connectionString = config;
            String databaseName = "minecraft";
            String collectionName = "cutscenes";

            // Extract database and collection if present
            String[] urlParts = config.split("/");
            if (urlParts.length >= 4) {
                String dbAndParams = urlParts[3];
                String[] dbParts = dbAndParams.split("\\?");
                databaseName = dbParts[0];

                if (dbParts.length > 1 && dbParts[1].contains("collection=")) {
                    String[] params = dbParts[1].split("&");
                    for (String param : params) {
                        if (param.startsWith("collection=")) {
                            collectionName = param.substring(11);
                            break;
                        }
                    }
                }
            }

            return new MongoDBCutsceneDatabaseService(connectionString, databaseName, collectionName);
        } catch (Exception e) {
            throw new DatabaseException("Invalid MongoDB configuration format", e);
        }
    }

    private static CutsceneDatabaseService parseRedisConfig(String config) throws DatabaseException {
        try {
            // Expected format: host:port:password:database:keyPrefix
            // Example: localhost:6379::0:nonscenes:
            String[] parts = config.split(":");
            if (parts.length < 5) {
                throw new DatabaseException("Redis config must be in format: host:port:password:database:keyPrefix");
            }

            String host = parts[0];
            int port = Integer.parseInt(parts[1]);
            String password = parts[2].isEmpty() ? null : parts[2];
            int database = Integer.parseInt(parts[3]);
            String keyPrefix = parts[4];

            return new RedisCutsceneDatabaseService(host, port, password, database, keyPrefix);
        } catch (Exception e) {
            throw new DatabaseException("Invalid Redis configuration format. Expected: host:port:password:database:keyPrefix", e);
        }
    }
}
