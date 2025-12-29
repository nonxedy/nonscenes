package com.nonxedy.core;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import com.nonxedy.Nonscenes;
import com.nonxedy.database.exception.DatabaseException;
import com.nonxedy.database.service.CutsceneDatabaseService;
import com.nonxedy.listener.CutscenePacketListener;
import com.nonxedy.model.Cutscene;
import com.nonxedy.model.CutsceneFrame;
import com.nonxedy.util.ColorUtil;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public class CutsceneManager {
    private final Nonscenes plugin;
    private final ConfigManager configManager;
    private final CutsceneDatabaseService databaseService;
    private final Map<String, Cutscene> cutscenes = new HashMap<>();
    private final Map<UUID, String> recordingSessions = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> recordingTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> recordingFrameCounters = new ConcurrentHashMap<>();
    private final Map<UUID, String> playbackSessions = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> playbackTasks = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> pathVisualizationTasks = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack[]> savedInventories = new ConcurrentHashMap<>();
    private final Map<UUID, GameMode> savedGameModes = new ConcurrentHashMap<>();
    private final File cutsceneFolder;
    private CutscenePacketListener packetListener;

    public CutsceneManager(Nonscenes plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.databaseService = plugin.getDatabaseService();
        this.cutsceneFolder = new File(plugin.getDataFolder(), "cutscenes");

        if (!cutsceneFolder.exists()) {
            cutsceneFolder.mkdirs();
        }

        // Cutscenes will be loaded later when worlds are available
    }

    public void setPacketListener(CutscenePacketListener packetListener) {
        this.packetListener = packetListener;
    }

    /**
     * Load all cutscenes from database and files.
     * This should be called after worlds are loaded.
     */
    public void loadCutscenes() {
        loadAllCutscenes();
    }

    private void loadAllCutscenes() {
        try {
            List<Cutscene> loadedCutscenes = databaseService.loadAllCutscenes();

            for (Cutscene cutscene : loadedCutscenes) {
                cutscenes.put(cutscene.getName().toLowerCase(), cutscene);
            }

            plugin.getLogger().log(Level.INFO, "Loaded {0} cutscenes from database", cutscenes.size());

            // Also try to load from files for backward compatibility
            loadCutscenesFromFiles();

        } catch (DatabaseException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load cutscenes from database", e);
            // Fallback to file loading
            loadCutscenesFromFiles();
        }
    }

    private void loadCutscenesFromFiles() {
        File[] files = cutsceneFolder.listFiles((dir, name) -> name.endsWith(".yml"));

        if (files == null) {
            return;
        }

        int fileCount = 0;
        for (File file : files) {
            try {
                FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                String name = file.getName().replace(".yml", "");

                // Skip if already loaded from database
                if (cutscenes.containsKey(name.toLowerCase())) {
                    continue;
                }

                List<CutsceneFrame> frames = new ArrayList<>();
                ConfigurationSection framesSection = config.getConfigurationSection("frames");

                if (framesSection != null) {
                    for (String key : framesSection.getKeys(false)) {
                        ConfigurationSection frameSection = framesSection.getConfigurationSection(key);
                        if (frameSection != null) {
                            String worldName = frameSection.getString("world");
                            double x = frameSection.getDouble("x");
                            double y = frameSection.getDouble("y");
                            double z = frameSection.getDouble("z");
                            float yaw = (float) frameSection.getDouble("yaw");
                            float pitch = (float) frameSection.getDouble("pitch");

                            if (worldName != null && Bukkit.getWorld(worldName) != null) {
                                Location location = new Location(Bukkit.getWorld(worldName), x, y, z, yaw, pitch);
                                frames.add(new CutsceneFrame(location));
                            }
                        }
                    }
                }

                if (!frames.isEmpty()) {
                    Cutscene cutscene = new Cutscene(name, frames);
                    cutscenes.put(name.toLowerCase(), cutscene);
                    fileCount++;

                    // Try to save to database for migration
                    try {
                        databaseService.saveCutscene(cutscene);
                        plugin.getLogger().log(Level.INFO, "Migrated cutscene from file to database: {0}", name);
                    } catch (DatabaseException dbException) {
                        plugin.getLogger().log(Level.WARNING, "Failed to migrate cutscene to database: " + name, dbException);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load cutscene from file: " + file.getName(), e);
            }
        }

        if (fileCount > 0) {
            plugin.getLogger().log(Level.INFO, "Loaded {0} cutscenes from files", fileCount);
        }
    }

    public void saveAllCutscenes() {
        for (Cutscene cutscene : cutscenes.values()) {
            saveCutscene(cutscene);
        }
    }

    private void saveCutscene(Cutscene cutscene) {
        File file = new File(cutsceneFolder, cutscene.getName() + ".yml");
        FileConfiguration config = new YamlConfiguration();
        
        config.set("name", cutscene.getName());
        
        List<CutsceneFrame> frames = cutscene.getFrames();
        for (int i = 0; i < frames.size(); i++) {
            CutsceneFrame frame = frames.get(i);
            Location location = frame.getLocation();
            
            config.set("frames." + i + ".world", location.getWorld().getName());
            config.set("frames." + i + ".x", location.getX());
            config.set("frames." + i + ".y", location.getY());
            config.set("frames." + i + ".z", location.getZ());
            config.set("frames." + i + ".yaw", location.getYaw());
            config.set("frames." + i + ".pitch", location.getPitch());
        }
        
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save cutscene: " + cutscene.getName(), e);
        }
    }

    public void startRecording(Player player, String name, int frames) {
        UUID playerId = player.getUniqueId();
        
        if (recordingSessions.containsKey(playerId)) {
            player.sendMessage(ColorUtil.format(configManager.getMessage("already-recording")));
            return;
        }
        
        // Check if cutscene exists in database or memory
        if (cutscenes.containsKey(name.toLowerCase())) {
            player.sendMessage(ColorUtil.format(configManager.getMessage("cutscene-already-exists")
                    .replace("{name}", name)));
            return;
        }

        try {
            if (databaseService.cutsceneExists(name)) {
                player.sendMessage(ColorUtil.format(configManager.getMessage("cutscene-already-exists")
                        .replace("{name}", name)));
                return;
            }
        } catch (DatabaseException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to check if cutscene exists in database: " + name, e);
        }
        
        int countdownSeconds = configManager.getConfig().getInt("settings.countdown-seconds", 3);
        player.sendMessage(ColorUtil.format(configManager.getMessage("recording-countdown")
                .replace("{seconds}", String.valueOf(countdownSeconds))));
        
        new BukkitRunnable() {
            int seconds = countdownSeconds;
            
            @Override
            public void run() {
                if (seconds > 0) {
                    player.sendMessage(ColorUtil.format(configManager.getMessage("countdown")
                            .replace("{seconds}", String.valueOf(seconds))));
                    seconds--;
                } else {
                    cancel();
                    startRecordingProcess(player, name, frames);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void startRecordingProcess(Player player, String name, int totalFrames) {
        UUID playerId = player.getUniqueId();
        List<CutsceneFrame> frames = new ArrayList<>();
        recordingSessions.put(playerId, name);
        recordingFrameCounters.put(playerId, 0);

        player.sendMessage(ColorUtil.format(configManager.getMessage("recording-started")
                .replace("{name}", name)));

        int framesPerSecond = getValidatedFramesPerSecond();
        long delay = Math.max(1, 20 / framesPerSecond);
        
        BukkitTask task = new BukkitRunnable() {
            int frameCount = 0;
            
            @Override
            public void run() {
                if (frameCount >= totalFrames) {
                    finishRecording(player, name, frames);
                    cancel();
                    return;
                }
                
                frames.add(new CutsceneFrame(player.getLocation().clone()));
                frameCount++;
                recordingFrameCounters.put(playerId, frameCount);
                
                if (frameCount % framesPerSecond == 0 || frameCount == totalFrames) {
                    player.sendMessage(ColorUtil.format(configManager.getMessage("recording-progress")
                            .replace("{current}", String.valueOf(frameCount))
                            .replace("{total}", String.valueOf(totalFrames))));
                }
            }
        }.runTaskTimer(plugin, 0L, delay);
        
        recordingTasks.put(playerId, task);
    }

    private void finishRecording(Player player, String name, List<CutsceneFrame> frames) {
        UUID playerId = player.getUniqueId();

        Cutscene cutscene = new Cutscene(name, frames);
        cutscenes.put(name.toLowerCase(), cutscene);

        // Save to database
        try {
            databaseService.saveCutscene(cutscene);
            player.sendMessage(ColorUtil.format(configManager.getMessage("recording-finished")
                    .replace("{name}", name)
                    .replace("{frames}", String.valueOf(frames.size()))));
        } catch (DatabaseException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save cutscene to database: " + name, e);
            player.sendMessage(ColorUtil.format("&cFailed to save cutscene to database. Check console for details."));
        }

        recordingSessions.remove(playerId);
        recordingTasks.remove(playerId);
        recordingFrameCounters.remove(playerId);
    }

    public void playCutscene(Player player, String name) {
        UUID playerId = player.getUniqueId();
        
        if (playbackSessions.containsKey(playerId)) {
            player.sendMessage(ColorUtil.format(configManager.getMessage("already-playing")));
            return;
        }
        
        Cutscene cutscene = cutscenes.get(name.toLowerCase());
        if (cutscene == null) {
            player.sendMessage(ColorUtil.format(configManager.getMessage("cutscene-not-found")
                    .replace("{name}", name)));
            return;
        }
        
        List<CutsceneFrame> frames = cutscene.getFrames();
        if (frames.isEmpty()) {
            player.sendMessage(ColorUtil.format(configManager.getMessage("cutscene-not-found")
                    .replace("{name}", name)));
            return;
        }
        
        playbackSessions.put(playerId, name);
        player.sendMessage(ColorUtil.format(configManager.getMessage("cutscene-playing")
                .replace("{name}", name)));

        // Save player's game mode and set to spectator to hide UI
        GameMode originalGameMode = player.getGameMode();
        savedGameModes.put(playerId, originalGameMode);
        player.setGameMode(GameMode.SPECTATOR);

        // Setup packet listener for camera control
        if (packetListener != null) {
            packetListener.addPlayer(player);
            packetListener.forceFirstPerson(player);
        }

        Location originalLocation = player.getLocation().clone();
        
        boolean hidePlayer = configManager.getConfig().getBoolean("settings.playback.hide-player", true);
        boolean makeInvulnerable = configManager.getConfig().getBoolean("settings.playback.invulnerable", true);
        
        if (hidePlayer) {
            for (Player otherPlayer : Bukkit.getOnlinePlayers()) {
                if (!otherPlayer.equals(player)) {
                    otherPlayer.hidePlayer(plugin, player);
                }
            }
        }
        
        if (makeInvulnerable) {
            player.setInvulnerable(true);
        }

        int framesPerSecond = getValidatedFramesPerSecond();
        final int interpolationSteps = Math.max(1, Math.min(100, configManager.getConfig().getInt("settings.playback.interpolation-steps", 10)));

        // Adjust delay to maintain playback speed: more interpolation steps = faster ticks
        long delay = Math.max(1, 20 / (framesPerSecond * interpolationSteps));

        BukkitTask task = new BukkitRunnable() {
            int currentFrameIndex = 0;
            int currentInterpolationStep = 0;

            @Override
            public void run() {
                if (currentFrameIndex >= frames.size() - 1) {
                    // Last frame - teleport directly
                    CutsceneFrame lastFrame = frames.get(frames.size() - 1);
                    Location lastLocation = lastFrame.getLocation();

                    if (!lastLocation.getWorld().isChunkLoaded(lastLocation.getBlockX() >> 4, lastLocation.getBlockZ() >> 4)) {
                        lastLocation.getWorld().loadChunk(lastLocation.getBlockX() >> 4, lastLocation.getBlockZ() >> 4, true);
                    }

                    player.teleport(lastLocation);

                    String progressText = "<gray>" + frames.size() + "<white>/<gray>" + frames.size();
                    Component actionBar = MiniMessage.miniMessage().deserialize(progressText);
                    player.sendActionBar(actionBar);

                    finishPlayback(player, name, originalLocation, hidePlayer, makeInvulnerable);
                    cancel();
                    return;
                }

                // Get current and next frames for interpolation
                CutsceneFrame currentFrame = frames.get(currentFrameIndex);
                CutsceneFrame nextFrame = frames.get(currentFrameIndex + 1);

                Location currentLocation = currentFrame.getLocation();
                Location nextLocation = nextFrame.getLocation();

                // Ensure chunks are loaded
                if (!currentLocation.getWorld().isChunkLoaded(currentLocation.getBlockX() >> 4, currentLocation.getBlockZ() >> 4)) {
                    currentLocation.getWorld().loadChunk(currentLocation.getBlockX() >> 4, currentLocation.getBlockZ() >> 4, true);
                }
                if (!nextLocation.getWorld().isChunkLoaded(nextLocation.getBlockX() >> 4, nextLocation.getBlockZ() >> 4)) {
                    nextLocation.getWorld().loadChunk(nextLocation.getBlockX() >> 4, nextLocation.getBlockZ() >> 4, true);
                }

                // Interpolate between current and next frame
                float t = (float) currentInterpolationStep / interpolationSteps;
                Location interpolatedLocation = interpolateLocations(currentLocation, nextLocation, t);

                player.teleport(interpolatedLocation);

                // Force first person camera every frame
                if (packetListener != null) {
                    packetListener.forceFirstPerson(player);
                }

                // Update progress display (show frame numbers, not interpolation steps)
                int displayFrame = currentFrameIndex + 1;
                String progressText = "<gray>" + displayFrame + "<white>/<gray>" + frames.size();
                Component actionBar = MiniMessage.miniMessage().deserialize(progressText);
                player.sendActionBar(actionBar);

                // Move to next interpolation step
                currentInterpolationStep++;

                // If all interpolation steps for this segment are done, move to next frame
                if (currentInterpolationStep >= interpolationSteps) {
                    currentFrameIndex++;
                    currentInterpolationStep = 0;
                }
            }
        }.runTaskTimer(plugin, 0L, delay);
        
        playbackTasks.put(playerId, task);
    }

    private void finishPlayback(Player player, String name, Location originalLocation,
                               boolean wasHidden, boolean wasInvulnerable) {
        UUID playerId = player.getUniqueId();

        if (wasHidden) {
            for (Player otherPlayer : Bukkit.getOnlinePlayers()) {
                if (!otherPlayer.equals(player)) {
                    otherPlayer.showPlayer(plugin, player);
                }
            }
        }

        if (wasInvulnerable) {
            player.setInvulnerable(false);
        }

        // Restore player's game mode
        GameMode savedGameMode = savedGameModes.remove(playerId);
        if (savedGameMode != null) {
            player.setGameMode(savedGameMode);
        }

        // Remove from packet listener
        if (packetListener != null) {
            packetListener.removePlayer(player);
        }

        // Ensure the original location chunk is loaded before teleporting back
        if (!originalLocation.getWorld().isChunkLoaded(originalLocation.getBlockX() >> 4, originalLocation.getBlockZ() >> 4)) {
            originalLocation.getWorld().loadChunk(originalLocation.getBlockX() >> 4, originalLocation.getBlockZ() >> 4, true);
        }

        player.teleport(originalLocation);

        player.sendMessage(ColorUtil.format(configManager.getMessage("cutscene-playback-finished")
                .replace("{name}", name)));

        playbackSessions.remove(playerId);
        playbackTasks.remove(playerId);
    }

    private int getValidatedFramesPerSecond() {
        int fps = configManager.getConfig().getInt("settings.frames-per-second", 30);
        return Math.max(1, Math.min(60, fps)); // Clamp between 1 and 60 FPS
    }

    public void deleteCutscene(Player player, String name) {
        if (!cutscenes.containsKey(name.toLowerCase())) {
            player.sendMessage(ColorUtil.format(configManager.getMessage("cutscene-not-found")
                    .replace("{name}", name)));
            return;
        }

        try {
            // Delete from database
            databaseService.deleteCutscene(name);

            // Also delete file if it exists for backward compatibility
            File file = new File(cutsceneFolder, name + ".yml");
            if (file.exists()) {
                file.delete();
            }

            cutscenes.remove(name.toLowerCase());

            player.sendMessage(ColorUtil.format(configManager.getMessage("cutscene-deleted")
                    .replace("{name}", name)));

        } catch (DatabaseException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete cutscene from database: " + name, e);
            player.sendMessage(ColorUtil.format("&cFailed to delete cutscene from database. Check console for details."));
        }
    }

    public void listAllCutscenes(Player player) {
        if (cutscenes.isEmpty()) {
            player.sendMessage(ColorUtil.format(configManager.getMessage("no-cutscenes")));
            return;
        }
        
        player.sendMessage(ColorUtil.format(configManager.getMessage("cutscene-list-header")));
        
        for (Cutscene cutscene : cutscenes.values()) {
            player.sendMessage(ColorUtil.format(configManager.getMessage("cutscene-list-item")
                    .replace("{name}", cutscene.getName())
                    .replace("{frames}", String.valueOf(cutscene.getFrames().size()))));
        }
    }

    public void showCutscenePath(Player player, String name) {
        UUID playerId = player.getUniqueId();
        
        if (pathVisualizationTasks.containsKey(playerId)) {
            player.sendMessage(ColorUtil.format(configManager.getMessage("path-already-showing")));
            return;
        }
        
        Cutscene cutscene = cutscenes.get(name.toLowerCase());
        if (cutscene == null) {
            player.sendMessage(ColorUtil.format(configManager.getMessage("cutscene-not-found")
                    .replace("{name}", name)));
            return;
        }
        
        List<CutsceneFrame> frames = cutscene.getFrames();
        if (frames.isEmpty()) {
            player.sendMessage(ColorUtil.format(configManager.getMessage("cutscene-not-found")
                    .replace("{name}", name)));
            return;
        }
        
        int durationSeconds = configManager.getConfig().getInt("settings.path-visualization-duration", 30);
        player.sendMessage(ColorUtil.format(configManager.getMessage("showing-path")
                .replace("{name}", name)
                .replace("{duration}", String.valueOf(durationSeconds))));
        
        BukkitTask task = new BukkitRunnable() {
            int tickCounter = 0;
            final int totalTicks = durationSeconds * 20;
            
            @Override
            public void run() {
                if (tickCounter >= totalTicks) {
                    cancel();
                    pathVisualizationTasks.remove(playerId);
                    return;
                }
                
                for (int i = 0; i < frames.size() - 1; i++) {
                    Location start = frames.get(i).getLocation();
                    Location end = frames.get(i + 1).getLocation();
                    
                    if (!start.getWorld().equals(end.getWorld())) {
                        continue;
                    }
                    
                    double distance = start.distance(end);
                    Vector direction = end.toVector().subtract(start.toVector()).normalize();
                    
                    for (double d = 0; d < distance; d += 0.5) {
                        Vector point = start.toVector().add(direction.clone().multiply(d));
                        start.getWorld().spawnParticle(
                                Particle.END_ROD, 
                                point.getX(), point.getY(), point.getZ(), 
                                1, 0, 0, 0, 0);
                    }
                }
                
                for (CutsceneFrame frame : frames) {
                    Location loc = frame.getLocation();
                    loc.getWorld().spawnParticle(
                            Particle.FLAME, 
                            loc.getX(), loc.getY(), loc.getZ(), 
                            3, 0.1, 0.1, 0.1, 0.01);
                }
                
                tickCounter++;
            }
        }.runTaskTimer(plugin, 0L, 5L);
        
        pathVisualizationTasks.put(playerId, task);
    }
    
    public void cancelRecording(Player player) {
        UUID playerId = player.getUniqueId();
        
        if (!recordingSessions.containsKey(playerId)) {
            return;
        }
        
        BukkitTask task = recordingTasks.get(playerId);
        if (task != null) {
            task.cancel();
        }
        
        recordingSessions.remove(playerId);
        recordingTasks.remove(playerId);
        recordingFrameCounters.remove(playerId);
        
        player.sendMessage(ColorUtil.format(configManager.getMessage("recording-cancelled")));
    }
    
    public void cancelPlayback(Player player) {
        UUID playerId = player.getUniqueId();

        if (!playbackSessions.containsKey(playerId)) {
            return;
        }

        String cutsceneName = playbackSessions.get(playerId);

        BukkitTask task = playbackTasks.get(playerId);
        if (task != null) {
            task.cancel();
        }

        player.setInvulnerable(false);
        for (Player otherPlayer : Bukkit.getOnlinePlayers()) {
            if (!otherPlayer.equals(player)) {
                otherPlayer.showPlayer(plugin, player);
            }
        }

        // Restore player's game mode
        GameMode savedGameMode = savedGameModes.remove(playerId);
        if (savedGameMode != null) {
            player.setGameMode(savedGameMode);
        }

        // Remove from packet listener
        if (packetListener != null) {
            packetListener.removePlayer(player);
        }

        playbackSessions.remove(playerId);
        playbackTasks.remove(playerId);

        player.sendMessage(ColorUtil.format(configManager.getMessage("playback-cancelled")
                .replace("{name}", cutsceneName)));
    }
    
    public void cancelPathVisualization(Player player) {
        UUID playerId = player.getUniqueId();
        
        BukkitTask task = pathVisualizationTasks.get(playerId);
        if (task != null) {
            task.cancel();
            pathVisualizationTasks.remove(playerId);
            player.sendMessage(ColorUtil.format(configManager.getMessage("path-visualization-cancelled")));
        }
    }
    
    public boolean isRecording(Player player) {
        return recordingSessions.containsKey(player.getUniqueId());
    }
    
    public boolean isWatchingCutscene(Player player) {
        return playbackSessions.containsKey(player.getUniqueId());
    }
    
    public List<String> getCutsceneNames() {
        return new ArrayList<>(cutscenes.keySet());
    }
    
    public Cutscene getCutscene(String name) {
        return cutscenes.get(name.toLowerCase());
    }
    
    public void cancelAllSessions(Player player) {
        UUID playerId = player.getUniqueId();
        boolean cancelledSomething = false;

        // Cancel recording if active
        if (recordingSessions.containsKey(playerId)) {
            cancelRecording(player);
            cancelledSomething = true;
        }

        // Cancel playback if active
        if (playbackSessions.containsKey(playerId)) {
            cancelPlayback(player);
            cancelledSomething = true;
        }

        // Cancel path visualization if active
        if (pathVisualizationTasks.containsKey(playerId)) {
            cancelPathVisualization(player);
            cancelledSomething = true;
        }

        if (!cancelledSomething) {
            player.sendMessage(ColorUtil.format(configManager.getMessage("nothing-to-cancel")));
        }
    }

    /**
     * Interpolates between two locations
     * @param from Starting location
     * @param to Ending location
     * @param t Interpolation factor (0.0 to 1.0)
     * @return Interpolated location
     */
    private Location interpolateLocations(Location from, Location to, float t) {
        // Ensure both locations are in the same world
        if (!from.getWorld().equals(to.getWorld())) {
            return from; // Fallback to 'from' location
        }

        // Linear interpolation for coordinates
        double x = from.getX() + (to.getX() - from.getX()) * t;
        double y = from.getY() + (to.getY() - from.getY()) * t;
        double z = from.getZ() + (to.getZ() - from.getZ()) * t;

        // Interpolate yaw and pitch with proper angle wrapping
        float yawDiff = to.getYaw() - from.getYaw();
        // Handle angle wrapping (e.g., 350° to 10° should go through 360°/0°)
        if (yawDiff > 180) {
            yawDiff -= 360;
        } else if (yawDiff < -180) {
            yawDiff += 360;
        }
        float yaw = from.getYaw() + yawDiff * t;

        float pitchDiff = to.getPitch() - from.getPitch();
        float pitch = from.getPitch() + pitchDiff * t;

        return new Location(from.getWorld(), x, y, z, yaw, pitch);
    }

    public void cleanup() {
        for (BukkitTask task : recordingTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }

        for (BukkitTask task : playbackTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }

        for (BukkitTask task : pathVisualizationTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }

        saveAllCutscenes();

        recordingSessions.clear();
        recordingTasks.clear();
        recordingFrameCounters.clear();
        playbackSessions.clear();
        playbackTasks.clear();
        pathVisualizationTasks.clear();
        savedInventories.clear();
        savedGameModes.clear();
    }
}
