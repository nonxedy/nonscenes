package com.nonxedy.database.service.impl

import com.nonxedy.database.service.CutsceneDatabaseService
import com.nonxedy.model.Cutscene
import com.nonxedy.model.CutsceneFrame
import org.bukkit.Bukkit
import org.bukkit.Location
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.util.logging.Level

// SQLite implementation of CutsceneDatabaseService
class SQLiteCutsceneDatabaseService(private val databaseFile: File) : CutsceneDatabaseService {

    private var connection: Connection? = null
    private val logger = java.util.logging.Logger.getLogger("nonscenes-db")

    override fun initialize() {
        try {
            // Ensure parent directory exists
            databaseFile.parentFile?.mkdirs()

            // Create SQLite connection
            val url = "jdbc:sqlite:${databaseFile.absolutePath}"
            connection = DriverManager.getConnection(url)

            // Create tables
            createTables()

            logger.info("SQLite database initialized at: ${databaseFile.absolutePath}")
        } catch (e: Exception) {
            logger.severe("Failed to initialize SQLite database: ${e.message}")
            throw RuntimeException("Failed to initialize SQLite database", e)
        }
    }

    override fun shutdown() {
        try {
            connection?.close()
            connection = null
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Error closing database connection", e)
        }
    }

    override fun saveCutscene(cutscene: Cutscene) {
        val conn = connection ?: throw RuntimeException("Database not initialized")

        try {
            conn.autoCommit = false

            // Delete existing cutscene
            val deleteStmt = conn.prepareStatement("DELETE FROM cutscenes WHERE name = ?")
            deleteStmt.setString(1, cutscene.name)
            deleteStmt.executeUpdate()
            deleteStmt.close()

            val deleteFramesStmt = conn.prepareStatement("DELETE FROM cutscene_frames WHERE cutscene_name = ?")
            deleteFramesStmt.setString(1, cutscene.name)
            deleteFramesStmt.executeUpdate()
            deleteFramesStmt.close()

            // Insert cutscene
            val insertStmt = conn.prepareStatement(
                "INSERT INTO cutscenes (name, frame_count) VALUES (?, ?)"
            )
            insertStmt.setString(1, cutscene.name)
            insertStmt.setInt(2, cutscene.frames.size)
            insertStmt.executeUpdate()
            insertStmt.close()

            // Insert frames
            val frameStmt = conn.prepareStatement("""
                INSERT INTO cutscene_frames (cutscene_name, frame_index, world, x, y, z, yaw, pitch)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """)

            cutscene.frames.forEachIndexed { index, frame ->
                val location = frame.location
                frameStmt.setString(1, cutscene.name)
                frameStmt.setInt(2, index)
                frameStmt.setString(3, location.world?.name ?: "world")
                frameStmt.setDouble(4, location.x)
                frameStmt.setDouble(5, location.y)
                frameStmt.setDouble(6, location.z)
                frameStmt.setFloat(7, location.yaw)
                frameStmt.setFloat(8, location.pitch)
                frameStmt.executeUpdate()
            }
            frameStmt.close()

            conn.commit()

        } catch (e: Exception) {
            conn.rollback()
            logger.severe("Failed to save cutscene: ${e.message}")
            throw RuntimeException("Failed to save cutscene: ${cutscene.name}", e)
        } finally {
            conn.autoCommit = true
        }
    }

    override fun loadAllCutscenes(): List<Cutscene> {
        val conn = connection ?: throw RuntimeException("Database not initialized")
        val cutscenes = mutableListOf<Cutscene>()

        try {
            val stmt = conn.prepareStatement("""
                SELECT c.name, f.frame_index, f.world, f.x, f.y, f.z, f.yaw, f.pitch
                FROM cutscenes c
                JOIN cutscene_frames f ON c.name = f.cutscene_name
                ORDER BY c.name, f.frame_index
            """)

            val rs = stmt.executeQuery()
            var currentCutscene: Cutscene? = null
            var currentFrames = mutableListOf<CutsceneFrame>()

            while (rs.next()) {
                val name = rs.getString("name")

                if (currentCutscene == null || currentCutscene.name != name) {
                    // Save previous cutscene
                    if (currentCutscene != null && currentFrames.isNotEmpty()) {
                        cutscenes.add(currentCutscene)
                    }

                    // Start new cutscene
                    currentFrames = mutableListOf()
                    currentCutscene = Cutscene(name, currentFrames)
                }

                // Add frame
                val worldName = rs.getString("world")
                val world = Bukkit.getWorld(worldName)
                if (world != null) {
                    val location = Location(
                        world,
                        rs.getDouble("x"),
                        rs.getDouble("y"),
                        rs.getDouble("z"),
                        rs.getFloat("yaw"),
                        rs.getFloat("pitch")
                    )
                    currentFrames.add(CutsceneFrame(location))
                }
            }

            // Add last cutscene
            if (currentCutscene != null && currentFrames.isNotEmpty()) {
                cutscenes.add(currentCutscene)
            }

            rs.close()
            stmt.close()

        } catch (e: Exception) {
            logger.severe("Failed to load cutscenes: ${e.message}")
            throw RuntimeException("Failed to load cutscenes", e)
        }

        return cutscenes
    }

    override fun deleteCutscene(name: String) {
        val conn = connection ?: throw RuntimeException("Database not initialized")

        try {
            // Delete frames first (foreign key constraint)
            val deleteFramesStmt = conn.prepareStatement("DELETE FROM cutscene_frames WHERE cutscene_name = ?")
            deleteFramesStmt.setString(1, name)
            deleteFramesStmt.executeUpdate()
            deleteFramesStmt.close()

            // Delete cutscene
            val deleteStmt = conn.prepareStatement("DELETE FROM cutscenes WHERE name = ?")
            deleteStmt.setString(1, name)
            deleteStmt.executeUpdate()
            deleteStmt.close()

        } catch (e: Exception) {
            logger.severe("Failed to delete cutscene: ${e.message}")
            throw RuntimeException("Failed to delete cutscene: $name", e)
        }
    }

    override fun cutsceneExists(name: String): Boolean {
        val conn = connection ?: throw RuntimeException("Database not initialized")

        try {
            val stmt = conn.prepareStatement("SELECT COUNT(*) FROM cutscenes WHERE name = ?")
            stmt.setString(1, name)
            val rs = stmt.executeQuery()
            val exists = rs.getInt(1) > 0
            rs.close()
            stmt.close()
            return exists
        } catch (e: Exception) {
            logger.severe("Failed to check if cutscene exists: ${e.message}")
            throw RuntimeException("Failed to check if cutscene exists: $name", e)
        }
    }

    private fun createTables() {
        val conn = connection ?: throw RuntimeException("Database not initialized")

        try {
            // Create cutscenes table
            val createCutscenesTable = """
                CREATE TABLE IF NOT EXISTS cutscenes (
                    name TEXT PRIMARY KEY,
                    frame_count INTEGER NOT NULL
                )
            """

            // Create frames table
            val createFramesTable = """
                CREATE TABLE IF NOT EXISTS cutscene_frames (
                    cutscene_name TEXT NOT NULL,
                    frame_index INTEGER NOT NULL,
                    world TEXT NOT NULL,
                    x REAL NOT NULL,
                    y REAL NOT NULL,
                    z REAL NOT NULL,
                    yaw REAL NOT NULL,
                    pitch REAL NOT NULL,
                    PRIMARY KEY (cutscene_name, frame_index),
                    FOREIGN KEY (cutscene_name) REFERENCES cutscenes(name) ON DELETE CASCADE
                )
            """

            conn.createStatement().use { stmt ->
                stmt.execute(createCutscenesTable)
                stmt.execute(createFramesTable)
            }

        } catch (e: Exception) {
            logger.severe("Failed to create database tables: ${e.message}")
            throw RuntimeException("Failed to create database tables", e)
        }
    }
}
