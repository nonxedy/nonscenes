package com.nonxedy.core

import com.nonxedy.Nonscenes
import com.nonxedy.database.service.CutsceneDatabaseService
import com.nonxedy.database.service.impl.SQLiteCutsceneDatabaseService
import com.nonxedy.model.Cutscene
import com.nonxedy.model.CutsceneFrame
import com.nonxedy.util.ColorUtil
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import kotlin.math.max
import kotlin.math.min

class CutsceneManager(private val plugin: Nonscenes) : CutsceneManagerInterface {
    private val databaseService: CutsceneDatabaseService
    private val cutscenes = mutableMapOf<String, Cutscene>()
    private val recordingSessions = ConcurrentHashMap<UUID, String>()
    private val recordingTasks = ConcurrentHashMap<UUID, BukkitTask>()
    private val recordingFrameCounters = ConcurrentHashMap<UUID, Int>()
    private val playbackSessions = ConcurrentHashMap<UUID, String>()
    private val playbackTasks = ConcurrentHashMap<UUID, BukkitTask>()
    private val pathVisualizationTasks = ConcurrentHashMap<UUID, BukkitTask>()
    private val savedInventories = ConcurrentHashMap<UUID, Array<ItemStack?>>()
    private val savedGameModes = ConcurrentHashMap<UUID, GameMode>()
    private val cutsceneFolder = File(plugin.dataFolder, "cutscenes")

    init {
        // Initialize SQLite database
        val databaseFile = File(plugin.dataFolder, "cutscenes.db")
        databaseService = SQLiteCutsceneDatabaseService(databaseFile)

        try {
            databaseService.initialize()
            // Load cutscenes from database
            loadCutscenesFromDatabase()
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "Failed to initialize database, falling back to file storage", e)
            // Fallback to file storage if database fails
            loadCutscenesFromFiles()
        }
    }

    // Load cutscenes from database
    private fun loadCutscenesFromDatabase() {
        try {
            val loadedCutscenes = databaseService.loadAllCutscenes()
            for (cutscene in loadedCutscenes) {
                cutscenes[cutscene.name.lowercase()] = cutscene
            }
            plugin.logger.info("Loaded ${loadedCutscenes.size} cutscenes from database")
        } catch (e: Exception) {
            plugin.logger.log(java.util.logging.Level.SEVERE, "Failed to load cutscenes from database", e)
        }
    }

    private fun loadCutscenesFromFiles() {
        val cutsceneFolder = java.io.File(plugin.dataFolder, "cutscenes")
        val files = cutsceneFolder.listFiles { _, name -> name.endsWith(".yml") } ?: return

        var fileCount = 0
        for (file in files) {
            try {
                val config = YamlConfiguration.loadConfiguration(file)
                val name = file.name.replace(".yml", "")

                // Skip if already loaded from database
                if (cutscenes.containsKey(name.lowercase())) {
                    continue
                }

                val frames = mutableListOf<CutsceneFrame>()
                val framesSection: ConfigurationSection? = config.getConfigurationSection("frames")

                if (framesSection != null) {
                    for (key in framesSection.getKeys(false)) {
                        val frameSection = framesSection.getConfigurationSection(key)
                        if (frameSection != null) {
                            val worldName = frameSection.getString("world")
                            val x = frameSection.getDouble("x")
                            val y = frameSection.getDouble("y")
                            val z = frameSection.getDouble("z")
                            val yaw = frameSection.getDouble("yaw").toFloat()
                            val pitch = frameSection.getDouble("pitch").toFloat()

                            if (worldName != null && Bukkit.getWorld(worldName) != null) {
                                val location = Location(Bukkit.getWorld(worldName), x, y, z, yaw, pitch)
                                frames.add(CutsceneFrame(location))
                            }
                        }
                    }
                }

                if (frames.isNotEmpty()) {
                    val cutscene = Cutscene(name, frames)
                    cutscenes[name.lowercase()] = cutscene
                    fileCount++

                    // Try to save to database for migration
                    try {
                        databaseService.saveCutscene(cutscene)
                        plugin.logger.info("Migrated cutscene from file to database: $name")
                    } catch (dbException: Exception) {
                        plugin.logger.log(java.util.logging.Level.WARNING, "Failed to migrate cutscene to database: $name", dbException)
                    }
                }
            } catch (e: Exception) {
                plugin.logger.warning("Failed to load cutscene from file: ${file.name}")
            }
        }

        if (fileCount > 0) {
            plugin.logger.info("Loaded $fileCount cutscenes from files")
        }
    }

    private fun saveCutscene(cutscene: Cutscene) {
        val file = File(cutsceneFolder, "${cutscene.name}.yml")
        val config = YamlConfiguration()

        config.set("name", cutscene.name)

        val frames = cutscene.frames
        for (i in frames.indices) {
            val frame = frames[i]
            val location = frame.location

            config.set("frames.$i.world", location.world?.name ?: "world")
            config.set("frames.$i.x", location.x)
            config.set("frames.$i.y", location.y)
            config.set("frames.$i.z", location.z)
            config.set("frames.$i.yaw", location.yaw)
            config.set("frames.$i.pitch", location.pitch)
        }

        try {
            config.save(file)
        } catch (e: IOException) {
            plugin.logger.warning("Failed to save cutscene: ${cutscene.name}")
        }
    }

    override fun startRecording(player: Player, name: String, frames: Int) {
        val playerId = player.uniqueId

        if (recordingSessions.containsKey(playerId)) {
            val message = plugin.configManager.getMessage("already-recording")
            player.sendMessage(message)
            return
        }

        if (cutscenes.containsKey(name.lowercase())) {
            val message = plugin.configManager.getMessage("cutscene-already-exists")?.replace("{name}", name) ?: "§cA cutscene with that name already exists!"
            player.sendMessage(message)
            return
        }

        val countdownSeconds = plugin.configManager.config?.getInt("settings.countdown-seconds", 3) ?: 3
        val countdownMessage = plugin.configManager.getMessage("recording-countdown")
            ?.replace("{seconds}", countdownSeconds.toString())
            ?: "§aRecording will start in $countdownSeconds seconds..."
        player.sendMessage(countdownMessage)

        object : BukkitRunnable() {
            var seconds = countdownSeconds

            override fun run() {
                if (seconds > 0) {
                    val countdownTickMessage = plugin.configManager.getMessage("countdown")?.replace("{seconds}", seconds.toString()) ?: "§e$seconds..."
                    player.sendMessage(countdownTickMessage)
                    seconds--
                } else {
                    cancel()
                    startRecordingProcess(player, name, frames)
                }
            }
        }.runTaskTimer(plugin, 0L, 20L)
    }

    private fun startRecordingProcess(player: Player, name: String, totalFrames: Int) {
        val playerId = player.uniqueId
        val frames = mutableListOf<CutsceneFrame>()
        recordingSessions[playerId] = name
        recordingFrameCounters[playerId] = 0

        val message = plugin.configManager.getMessage("recording-started")?.replace("{name}", name) ?: "§aStarted recording cutscene '$name'!"
        player.sendMessage(message)

        val framesPerSecond = plugin.configManager.config?.getInt("settings.frames-per-second", 30) ?: 30
        val delay = max(1L, 20L / framesPerSecond)

        val task = object : BukkitRunnable() {
            var frameCount = 0

            override fun run() {
                if (frameCount >= totalFrames) {
                    finishRecording(player, name, frames)
                    cancel()
                    return
                }

                frames.add(CutsceneFrame(player.location.clone()))
                frameCount++
                recordingFrameCounters[playerId] = frameCount

                if (frameCount % framesPerSecond == 0 || frameCount == totalFrames) {
                    val progressMessage = plugin.configManager.getMessage("recording-progress")
                        ?.replace("{current}", frameCount.toString())
                        ?.replace("{total}", totalFrames.toString()) ?: "§7Recorded $frameCount/$totalFrames frames"
                    player.sendMessage(progressMessage)
                }
            }
        }.runTaskTimer(plugin, 0L, delay)

        recordingTasks[playerId] = task
    }

    private fun finishRecording(player: Player, name: String, frames: List<CutsceneFrame>) {
        val playerId = player.uniqueId

        val cutscene = Cutscene(name, frames)
        cutscenes[name.lowercase()] = cutscene
        saveCutscene(cutscene)

        val message = plugin.configManager.getMessage("recording-finished")
            ?.replace("{name}", name)
            ?.replace("{frames}", frames.size.toString()) ?: "§aFinished recording cutscene '$name' with ${frames.size} frames!"
        player.sendMessage(message)

        recordingSessions.remove(playerId)
        recordingTasks.remove(playerId)
        recordingFrameCounters.remove(playerId)
    }

    override fun playCutscene(player: Player, name: String) {
        val playerId = player.uniqueId

        if (playbackSessions.containsKey(playerId)) {
            val message = plugin.configManager.getMessage("already-playing")
            player.sendMessage(message)
            return
        }

        val cutscene = cutscenes[name.lowercase()]
        if (cutscene == null) {
            val message = plugin.configManager.getMessage("cutscene-not-found")?.replace("{name}", name) ?: "§cCutscene '$name' not found!"
            player.sendMessage(message)
            return
        }

        val frames = cutscene.frames
        if (frames.isEmpty()) {
            val message = plugin.configManager.getMessage("cutscene-not-found")?.replace("{name}", name) ?: "§cCutscene '$name' has no frames!"
            player.sendMessage(message)
            return
        }

        playbackSessions[playerId] = name
        val message = plugin.configManager.getMessage("cutscene-playing")?.replace("{name}", name) ?: "§aPlaying cutscene '$name'..."
        player.sendMessage(message)

        // Save player's game mode and set to spectator
        val originalGameMode = player.gameMode
        savedGameModes[playerId] = originalGameMode
        player.gameMode = GameMode.SPECTATOR

        val originalLocation = player.location.clone()

        // Pre-load all chunks needed for the cutscene to avoid lag during playback
        preloadChunksForCutscene(frames)

        val framesPerSecond = plugin.configManager.config?.getInt("settings.frames-per-second", 30) ?: 30
        val interpolationSteps = plugin.configManager.config?.getInt("settings.playback.interpolation-steps", 10) ?: 10

        val delay = max(1L, 20L / (framesPerSecond * interpolationSteps))

        val task = object : BukkitRunnable() {
            var currentFrameIndex = 0
            var currentInterpolationStep = 0
            val cachedChunks = mutableSetOf<Pair<Int, Int>>() // Cache loaded chunk coordinates

            override fun run() {
                if (currentFrameIndex >= frames.size - 1) {
                    // Last frame - teleport directly
                    val lastFrame = frames[frames.size - 1]
                    val lastLocation = lastFrame.location

                    // Ensure final chunk is loaded (should already be from preload)
                    ensureChunkLoaded(lastLocation, cachedChunks)

                    player.teleport(lastLocation)

                    val progressText = "<gray>${frames.size}<white>/<gray>${frames.size}"
                    val actionBar = MiniMessage.miniMessage().deserialize(progressText)
                    player.sendActionBar(actionBar)

                    finishPlayback(player, name, originalLocation)
                    cancel()
                    return
                }

                // Get current and next frames for interpolation
                val currentFrame = frames[currentFrameIndex]
                val nextFrame = frames[currentFrameIndex + 1]

                val currentLocation = currentFrame.location
                val nextLocation = nextFrame.location

                // Ensure chunks are loaded (use cache to avoid repeated checks)
                ensureChunkLoaded(currentLocation, cachedChunks)
                ensureChunkLoaded(nextLocation, cachedChunks)

                // Interpolate between current and next frame
                val t = currentInterpolationStep.toFloat() / interpolationSteps
                val interpolatedLocation = interpolateLocation(currentLocation, nextLocation, t)

                player.teleport(interpolatedLocation)

                // Update progress display
                val displayFrame = currentFrameIndex + 1
                val progressText = "<gray>$displayFrame<white>/<gray>${frames.size}"
                val actionBar = MiniMessage.miniMessage().deserialize(progressText)
                player.sendActionBar(actionBar)

                // Move to next interpolation step
                currentInterpolationStep++

                // If all interpolation steps for this segment are done, move to next frame
                if (currentInterpolationStep >= interpolationSteps) {
                    currentFrameIndex++
                    currentInterpolationStep = 0
                }
            }
        }.runTaskTimer(plugin, 0L, delay)

        playbackTasks[playerId] = task
    }

    // Pre-loads all chunks needed for a cutscene to prevent lag during playback
    private fun preloadChunksForCutscene(frames: List<CutsceneFrame>) {
        val chunksToLoad = mutableSetOf<Pair<Int, Int>>()

        // Collect all unique chunk coordinates from frames
        frames.forEach { frame ->
            val location = frame.location
            val chunkX = location.blockX shr 4
            val chunkZ = location.blockZ shr 4
            chunksToLoad.add(chunkX to chunkZ)

            // Also preload adjacent chunks for smoother experience
            for (dx in -1..1) {
                for (dz in -1..1) {
                    chunksToLoad.add((chunkX + dx) to (chunkZ + dz))
                }
            }
        }

        // Load chunks asynchronously where possible
        chunksToLoad.forEach { (chunkX, chunkZ) ->
            frames.firstOrNull()?.location?.world?.let { world ->
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    // Use async chunk loading for better performance
                    plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                        world.loadChunk(chunkX, chunkZ, true)
                    })
                }
            }
        }
    }

    // Ensures a chunk is loaded, using a cache to avoid repeated checks
    private fun ensureChunkLoaded(location: Location, cachedChunks: MutableSet<Pair<Int, Int>>) {
        val chunkX = location.blockX shr 4
        val chunkZ = location.blockZ shr 4
        val chunkKey = chunkX to chunkZ

        if (chunkKey !in cachedChunks) {
            if (!location.world.isChunkLoaded(chunkX, chunkZ)) {
                location.world.loadChunk(chunkX, chunkZ, true)
            }
            cachedChunks.add(chunkKey)
        }
    }

    // Location interpolation
    private fun interpolateLocation(from: Location, to: Location, t: Float): Location {
        if (from.world != to.world) {
            return from
        }

        // Pre-calculate differences to avoid repeated calculations
        val deltaX = to.x - from.x
        val deltaY = to.y - from.y
        val deltaZ = to.z - from.z

        // Calculate yaw difference with proper wrapping
        var deltaYaw = to.yaw - from.yaw
        if (deltaYaw > 180) deltaYaw -= 360
        else if (deltaYaw < -180) deltaYaw += 360

        val deltaPitch = to.pitch - from.pitch

        // Interpolate using pre-calculated deltas
        val x = from.x + deltaX * t
        val y = from.y + deltaY * t
        val z = from.z + deltaZ * t
        val yaw = from.yaw + deltaYaw * t
        val pitch = from.pitch + deltaPitch * t

        return Location(from.world, x, y, z, yaw, pitch)
    }

    private fun finishPlayback(player: Player, name: String, originalLocation: Location) {
        val playerId = player.uniqueId

        // Restore player's game mode
        val savedGameMode = savedGameModes.remove(playerId)
        if (savedGameMode != null) {
            player.gameMode = savedGameMode
        }

        // Ensure the original location chunk is loaded before teleporting back
        if (!originalLocation.world.isChunkLoaded(originalLocation.blockX shr 4, originalLocation.blockZ shr 4)) {
            originalLocation.world.loadChunk(originalLocation.blockX shr 4, originalLocation.blockZ shr 4, true)
        }

        player.teleport(originalLocation)
        val message = plugin.configManager.getMessage("cutscene-playback-finished")?.replace("{name}", name) ?: "§aFinished playing cutscene '$name'!"
        player.sendMessage(message)

        playbackSessions.remove(playerId)
        playbackTasks.remove(playerId)
    }

    override fun deleteCutscene(player: Player, name: String) {
        if (!cutscenes.containsKey(name.lowercase())) {
            val message = plugin.configManager.getMessage("cutscene-not-found")?.replace("{name}", name) ?: "§cCutscene '$name' not found!"
            player.sendMessage(message)
            return
        }

        // Delete file if it exists
        val file = File(cutsceneFolder, "$name.yml")
        if (file.exists()) {
            file.delete()
        }

        cutscenes.remove(name.lowercase())
        val message = plugin.configManager.getMessage("cutscene-deleted")?.replace("{name}", name) ?: "§aDeleted cutscene '$name'!"
        player.sendMessage(message)
    }

    override fun listAllCutscenes(player: Player) {
        if (cutscenes.isEmpty()) {
            val message = plugin.configManager.getMessage("no-cutscenes") ?: "§7No cutscenes found."
            player.sendMessage(message)
            return
        }

        val headerMessage = plugin.configManager.getMessage("cutscene-list-header") ?: "§6=== Available Cutscenes ==="
        player.sendMessage(headerMessage)

        for ((_, cutscene) in cutscenes) {
            val itemMessage = plugin.configManager.getMessage("cutscene-list-item")
                ?.replace("{name}", cutscene.name)
                ?.replace("{frames}", cutscene.frames.size.toString()) ?: "§7- §f${cutscene.name} §7(${cutscene.frames.size} frames)"
            player.sendMessage(itemMessage)
        }
    }

    override fun showCutscenePath(player: Player, name: String) {
        val playerId = player.uniqueId

        if (pathVisualizationTasks.containsKey(playerId)) {
            val message = plugin.configManager.getMessage("path-already-showing") ?: "§cYou are already visualizing a path!"
            player.sendMessage(message)
            return
        }

        val cutscene = cutscenes[name.lowercase()]
        if (cutscene == null) {
            val message = plugin.configManager.getMessage("cutscene-not-found")?.replace("{name}", name) ?: "§cCutscene '$name' not found!"
            player.sendMessage(message)
            return
        }

        val frames = cutscene.frames
        if (frames.isEmpty()) {
            val message = plugin.configManager.getMessage("cutscene-not-found")?.replace("{name}", name) ?: "§cCutscene '$name' has no frames!"
            player.sendMessage(message)
            return
        }

        val durationSeconds = plugin.configManager.config?.getInt("settings.path-visualization.duration", 30) ?: 30
        val message = plugin.configManager.getMessage("showing-path")
            ?.replace("{name}", name)
            ?.replace("{duration}", durationSeconds.toString()) ?: "§aShowing path for '$name' ($durationSeconds seconds)..."
        player.sendMessage(message)

        val task = object : BukkitRunnable() {
            var tickCounter = 0
            val totalTicks = durationSeconds * 20

            override fun run() {
                if (tickCounter >= totalTicks) {
                    cancel()
                    pathVisualizationTasks.remove(playerId)
                    return
                }

                for (i in 0 until frames.size - 1) {
                    val start = frames[i].location
                    val end = frames[i + 1].location

                    if (start.world != end.world) {
                        continue
                    }

                    val distance = start.distance(end)
                    val direction = end.toVector().subtract(start.toVector()).normalize()

                    var d = 0.0
                    while (d < distance) {
                        val point = start.toVector().add(direction.clone().multiply(d))
                        start.world.spawnParticle(
                            Particle.END_ROD,
                            point.x, point.y, point.z,
                            1, 0.0, 0.0, 0.0, 0.0
                        )
                        d += 0.5
                    }
                }

                for (frame in frames) {
                    val loc = frame.location
                    loc.world.spawnParticle(
                        Particle.FLAME,
                        loc.x, loc.y, loc.z,
                        3, 0.1, 0.1, 0.1, 0.01
                    )
                }

                tickCounter++
            }
        }.runTaskTimer(plugin, 0L, 5L)

        pathVisualizationTasks[playerId] = task
    }

    override fun cancelRecording(player: Player) {
        val playerId = player.uniqueId

        recordingSessions[playerId]?.let { name ->
            recordingTasks[playerId]?.cancel()
            recordingSessions.remove(playerId)
            recordingTasks.remove(playerId)
            recordingFrameCounters.remove(playerId)

            val message = plugin.configManager.getMessage("playback-cancelled")?.replace("{name}", name) ?: "§cCancelled recording of cutscene '$name'!"
            player.sendMessage(message)
        } ?: run {
            val message = plugin.configManager.getMessage("recording-cancelled") ?: "§7You are not recording anything."
            player.sendMessage(message)
        }
    }

    override fun cancelPlayback(player: Player) {
        val playerId = player.uniqueId

        playbackSessions[playerId]?.let { name ->
            playbackTasks[playerId]?.cancel()

            // Restore player's game mode
            savedGameModes.remove(playerId)?.let { originalGameMode ->
                player.gameMode = originalGameMode
            }

            playbackSessions.remove(playerId)
            playbackTasks.remove(playerId)

            val message = plugin.configManager.getMessage("playback-cancelled")?.replace("{name}", name) ?: "§cCancelled playback of cutscene '$name'!"
            player.sendMessage(message)
        } ?: run {
            val message = plugin.configManager.getMessage("recording-cancelled") ?: "§7You are not watching a cutscene."
            player.sendMessage(message)
        }
    }

    override fun cancelPathVisualization(player: Player) {
        val playerId = player.uniqueId

        pathVisualizationTasks[playerId]?.let { task ->
            task.cancel()
            pathVisualizationTasks.remove(playerId)
            val message = plugin.configManager.getMessage("path-visualization-cancelled") ?: "§aCancelled path visualization!"
            player.sendMessage(message)
        } ?: run {
            val message = plugin.configManager.getMessage("recording-cancelled") ?: "§7You are not visualizing any path."
            player.sendMessage(message)
        }
    }

    override fun isRecording(player: Player): Boolean = recordingSessions.containsKey(player.uniqueId)

    override fun isWatchingCutscene(player: Player): Boolean = playbackSessions.containsKey(player.uniqueId)

    override fun getCutsceneNames(): List<String> = cutscenes.keys.toList()

    override fun getCutscene(name: String): Cutscene? = cutscenes[name.lowercase()]

    override fun cancelAllSessions(player: Player) {
        val playerId = player.uniqueId
        var cancelledSomething = false

        // Cancel recording if active
        if (recordingSessions.containsKey(playerId)) {
            cancelRecording(player)
            cancelledSomething = true
        }

        // Cancel playback if active
        if (playbackSessions.containsKey(playerId)) {
            cancelPlayback(player)
            cancelledSomething = true
        }

        // Cancel path visualization if active
        if (pathVisualizationTasks.containsKey(playerId)) {
            cancelPathVisualization(player)
            cancelledSomething = true
        }

        if (!cancelledSomething) {
            val message = plugin.configManager.getMessage("nothing-to-cancel") ?: "§7Nothing to cancel."
            player.sendMessage(message)
        }
    }



    override fun cleanup() {
        for (task in recordingTasks.values) {
            task?.cancel()
        }

        for (task in playbackTasks.values) {
            task?.cancel()
        }

        for (task in pathVisualizationTasks.values) {
            task?.cancel()
        }

        // Save all cutscenes
        for (cutscene in cutscenes.values) {
            saveCutscene(cutscene)
        }

        recordingSessions.clear()
        recordingTasks.clear()
        recordingFrameCounters.clear()
        playbackSessions.clear()
        playbackTasks.clear()
        pathVisualizationTasks.clear()
        savedInventories.clear()
        savedGameModes.clear()
    }
}
