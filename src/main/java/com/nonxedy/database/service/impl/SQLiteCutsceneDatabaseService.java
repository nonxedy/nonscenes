package com.nonxedy.database.service.impl;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.nonxedy.database.exception.DatabaseException;
import com.nonxedy.model.Cutscene;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * SQLite implementation of CutsceneDatabaseService
 */
public class SQLiteCutsceneDatabaseService extends BaseCutsceneDatabaseService {

    private HikariDataSource dataSource;
    private final File databaseFile;

    public SQLiteCutsceneDatabaseService(File databaseFile) {
        this.databaseFile = databaseFile;
    }

    @Override
    public void initialize() throws DatabaseException {
        try {
            // Ensure parent directory exists
            if (databaseFile.getParentFile() != null) {
                databaseFile.getParentFile().mkdirs();
            }

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:sqlite:" + databaseFile.getAbsolutePath());
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(30000);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);

            this.dataSource = new HikariDataSource(config);

            createTables();
            logger.info("SQLite database initialized at: " + databaseFile.getAbsolutePath());

        } catch (Exception e) {
            throw new DatabaseException("Failed to initialize SQLite database", e);
        }
    }

    private void createTables() throws SQLException {
        String createCutscenesTable = """
            CREATE TABLE IF NOT EXISTS cutscenes (
                name TEXT PRIMARY KEY,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;

        String createFramesTable = """
            CREATE TABLE IF NOT EXISTS cutscene_frames (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                cutscene_name TEXT NOT NULL,
                frame_index INTEGER NOT NULL,
                world TEXT NOT NULL,
                x REAL NOT NULL,
                y REAL NOT NULL,
                z REAL NOT NULL,
                yaw REAL NOT NULL,
                pitch REAL NOT NULL,
                FOREIGN KEY (cutscene_name) REFERENCES cutscenes (name) ON DELETE CASCADE,
                UNIQUE(cutscene_name, frame_index)
            )
            """;

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(createCutscenesTable);
            stmt.execute(createFramesTable);

            // Create indexes for better performance
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_cutscene_frames_name ON cutscene_frames(cutscene_name)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_cutscene_frames_index ON cutscene_frames(frame_index)");

            logger.info("Database tables created/verified");
        }
    }

    @Override
    protected void performSaveCutscene(Cutscene cutscene) throws DatabaseException {
        CutsceneData data = convertFromCutscene(cutscene);

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try {
                // Insert cutscene record
                String insertCutscene = "INSERT INTO cutscenes (name, updated_at) VALUES (?, CURRENT_TIMESTAMP)";
                try (PreparedStatement stmt = conn.prepareStatement(insertCutscene)) {
                    stmt.setString(1, data.name.toLowerCase());
                    stmt.executeUpdate();
                }

                // Insert frames
                String insertFrame = """
                    INSERT INTO cutscene_frames (cutscene_name, frame_index, world, x, y, z, yaw, pitch)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """;

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
                logger.fine("Successfully saved cutscene: " + data.name);

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
        String selectCutscene = """
            SELECT cf.world, cf.x, cf.y, cf.z, cf.yaw, cf.pitch
            FROM cutscenes c
            LEFT JOIN cutscene_frames cf ON c.name = cf.cutscene_name
            WHERE c.name = ?
            ORDER BY cf.frame_index
            """;

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
            SELECT c.name,
                   cf.world, cf.x, cf.y, cf.z, cf.yaw, cf.pitch, cf.frame_index
            FROM cutscenes c
            LEFT JOIN cutscene_frames cf ON c.name = cf.cutscene_name
            ORDER BY c.name, cf.frame_index
            """;

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
        String deleteCutscene = "DELETE FROM cutscenes WHERE name = ?";

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
        String checkExists = "SELECT COUNT(*) as count FROM cutscenes WHERE name = ?";

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
        String selectNames = "SELECT name FROM cutscenes ORDER BY name";
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
            logger.info("SQLite database connection closed");
        }
    }
}
