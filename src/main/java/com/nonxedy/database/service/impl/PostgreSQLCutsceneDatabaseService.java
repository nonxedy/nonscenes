package com.nonxedy.database.service.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import com.nonxedy.database.exception.DatabaseException;
import com.nonxedy.model.Cutscene;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * PostgreSQL implementation of CutsceneDatabaseService
 */
public class PostgreSQLCutsceneDatabaseService extends BaseCutsceneDatabaseService {

    private HikariDataSource dataSource;
    private final String connectionString;
    private final String schema;

    public PostgreSQLCutsceneDatabaseService(String connectionString, String schema) {
        this.connectionString = connectionString;
        this.schema = schema != null ? schema : "public";
    }

    @Override
    public void initialize() throws DatabaseException {
        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(connectionString);
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(30000);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);

            // PostgreSQL specific settings
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            this.dataSource = new HikariDataSource(config);

            createSchemaAndTables();
            logger.log(Level.INFO, "PostgreSQL database initialized with connection: {0}", connectionString);

        } catch (SQLException e) {
            throw new DatabaseException("Failed to initialize PostgreSQL database", e);
        }
    }

    private void createSchemaAndTables() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // Create schema if it doesn't exist
            stmt.execute("CREATE SCHEMA IF NOT EXISTS \"" + schema + "\"");

            // Create cutscenes table
            String createCutscenesTable = """
                CREATE TABLE IF NOT EXISTS "%s"."cutscenes" (
                    "name" VARCHAR(255) PRIMARY KEY,
                    "created_at" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                    "updated_at" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
                )
                """.formatted(schema);

            // Create frames table
            String createFramesTable = """
                CREATE TABLE IF NOT EXISTS "%s"."cutscene_frames" (
                    "id" SERIAL PRIMARY KEY,
                    "cutscene_name" VARCHAR(255) NOT NULL,
                    "frame_index" INTEGER NOT NULL,
                    "world" VARCHAR(255) NOT NULL,
                    "x" DOUBLE PRECISION NOT NULL,
                    "y" DOUBLE PRECISION NOT NULL,
                    "z" DOUBLE PRECISION NOT NULL,
                    "yaw" REAL NOT NULL,
                    "pitch" REAL NOT NULL,
                    FOREIGN KEY ("cutscene_name") REFERENCES "%s"."cutscenes"("name") ON DELETE CASCADE,
                    UNIQUE("cutscene_name", "frame_index")
                )
                """.formatted(schema, schema);

            // Create indexes
            String createIndexes = """
                CREATE INDEX IF NOT EXISTS "idx_cutscene_frames_name" ON "%s"."cutscene_frames"("cutscene_name");
                CREATE INDEX IF NOT EXISTS "idx_cutscene_frames_index" ON "%s"."cutscene_frames"("frame_index");
                """.formatted(schema, schema);

            stmt.execute(createCutscenesTable);
            stmt.execute(createFramesTable);
            stmt.execute(createIndexes);

            logger.info("Database schema and tables created/verified");
        }
    }

    @Override
    protected void performSaveCutscene(Cutscene cutscene) throws DatabaseException {
        CutsceneData data = convertFromCutscene(cutscene);

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try {
                // Insert cutscene record
                String insertCutscene = "INSERT INTO \"%s\".\"cutscenes\" (\"name\", \"updated_at\") VALUES (?, CURRENT_TIMESTAMP)".formatted(schema);
                try (PreparedStatement stmt = conn.prepareStatement(insertCutscene)) {
                    stmt.setString(1, data.name.toLowerCase());
                    stmt.executeUpdate();
                }

                // Insert frames
                String insertFrame = "INSERT INTO \"%s\".\"cutscene_frames\" (\"cutscene_name\", \"frame_index\", \"world\", \"x\", \"y\", \"z\", \"yaw\", \"pitch\") VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
                    .formatted(schema);

                try (PreparedStatement stmt = conn.prepareStatement(insertFrame)) {
                    for (int i = 0; i < data.frames.size(); i++) {
                        FrameData frame = data.frames.get(i);
                        stmt.setString(1, data.name.toLowerCase());
                        stmt.setInt(2, i);
                        stmt.setString(3, frame.world);
                        stmt.setDouble(4, frame.x);
                        stmt.setDouble(5, frame.y);
                        stmt.setDouble(6, frame.z);
                        stmt.setFloat(7, frame.yaw);
                        stmt.setFloat(8, frame.pitch);
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                }

                conn.commit();
                logger.log(Level.FINE, "Successfully saved cutscene: {0}", data.name);

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }

        } catch (SQLException e) {
            throw new DatabaseException("Failed to save cutscene: " + cutscene.getName(), e);
        }
    }

    @Override
    protected CutsceneData performLoadCutscene(String name) throws DatabaseException {
        String selectCutscene = "SELECT \"world\", \"x\", \"y\", \"z\", \"yaw\", \"pitch\" FROM \"%s\".\"cutscene_frames\" WHERE \"cutscene_name\" = ? ORDER BY \"frame_index\""
            .formatted(schema);

        List<FrameData> frames = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(selectCutscene)) {

            stmt.setString(1, name);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    frames.add(new FrameData(
                        rs.getString("world"),
                        rs.getDouble("x"),
                        rs.getDouble("y"),
                        rs.getDouble("z"),
                        rs.getFloat("yaw"),
                        rs.getFloat("pitch")
                    ));
                }
            }

        } catch (SQLException e) {
            throw new DatabaseException("Failed to load cutscene: " + name, e);
        }

        return frames.isEmpty() ? null : new CutsceneData(name, frames);
    }

    @Override
    protected List<CutsceneData> performLoadAllCutscenes() throws DatabaseException {
        String selectAllCutscenes = """
            SELECT c."name", cf."world", cf."x", cf."y", cf."z", cf."yaw", cf."pitch", cf."frame_index"
            FROM "%s"."cutscenes" c
            LEFT JOIN "%s"."cutscene_frames" cf ON c."name" = cf."cutscene_name"
            ORDER BY c."name", cf."frame_index"
            """.formatted(schema, schema);

        List<CutsceneData> cutscenes = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(selectAllCutscenes);
             ResultSet rs = stmt.executeQuery()) {

            String currentCutsceneName = null;
            List<FrameData> currentFrames = null;

            while (rs.next()) {
                String cutsceneName = rs.getString("name");

                if (!cutsceneName.equals(currentCutsceneName)) {
                    // Save previous cutscene if exists
                    if (currentCutsceneName != null && currentFrames != null) {
                        cutscenes.add(new CutsceneData(currentCutsceneName, currentFrames));
                    }

                    // Start new cutscene
                    currentCutsceneName = cutsceneName;
                    currentFrames = new ArrayList<>();
                }

                // Add frame if data is not null (handles cutscenes without frames)
                if (rs.getString("world") != null) {
                    currentFrames.add(new FrameData(
                        rs.getString("world"),
                        rs.getDouble("x"),
                        rs.getDouble("y"),
                        rs.getDouble("z"),
                        rs.getFloat("yaw"),
                        rs.getFloat("pitch")
                    ));
                }
            }

            // Add last cutscene if exists
            if (currentCutsceneName != null && currentFrames != null) {
                cutscenes.add(new CutsceneData(currentCutsceneName, currentFrames));
            }

        } catch (SQLException e) {
            throw new DatabaseException("Failed to load all cutscenes", e);
        }

        return cutscenes;
    }

    @Override
    protected void performDeleteCutscene(String name) throws DatabaseException {
        String deleteCutscene = "DELETE FROM \"%s\".\"cutscenes\" WHERE \"name\" = ?".formatted(schema);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(deleteCutscene)) {

            stmt.setString(1, name);
            int affectedRows = stmt.executeUpdate();

            if (affectedRows == 0) {
                throw new DatabaseException("Cutscene not found for deletion: " + name);
            }

        } catch (SQLException e) {
            throw new DatabaseException("Failed to delete cutscene: " + name, e);
        }
    }

    @Override
    protected boolean performCutsceneExists(String name) throws DatabaseException {
        String checkExists = "SELECT COUNT(*) as count FROM \"%s\".\"cutscenes\" WHERE \"name\" = ?".formatted(schema);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(checkExists)) {

            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt("count") > 0;
            }

        } catch (SQLException e) {
            throw new DatabaseException("Failed to check if cutscene exists: " + name, e);
        }
    }

    @Override
    protected List<String> performGetCutsceneNames() throws DatabaseException {
        String selectNames = "SELECT \"name\" FROM \"%s\".\"cutscenes\" ORDER BY \"name\"".formatted(schema);
        List<String> names = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(selectNames);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                names.add(rs.getString("name"));
            }

        } catch (SQLException e) {
            throw new DatabaseException("Failed to get cutscene names", e);
        }

        return names;
    }

    @Override
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("PostgreSQL database connection closed");
        }
    }
}
