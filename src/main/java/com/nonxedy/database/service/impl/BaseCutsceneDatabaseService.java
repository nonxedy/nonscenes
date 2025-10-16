package com.nonxedy.database.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import com.nonxedy.database.exception.DatabaseException;
import com.nonxedy.database.service.CutsceneDatabaseService;
import com.nonxedy.model.Cutscene;
import com.nonxedy.model.CutsceneFrame;

/**
 * Base implementation of CutsceneDatabaseService with common functionality
 */
public abstract class BaseCutsceneDatabaseService implements CutsceneDatabaseService {

    protected final Logger logger;

    public BaseCutsceneDatabaseService() {
        this.logger = Logger.getLogger("Nonscenes-DB");
    }

    @Override
    public void saveCutscene(Cutscene cutscene) throws DatabaseException {
        if (cutscene == null || cutscene.getName() == null || cutscene.getFrames() == null) {
            throw new DatabaseException("Invalid cutscene data");
        }

        if (cutsceneExists(cutscene.getName())) {
            throw new DatabaseException("Cutscene already exists: " + cutscene.getName());
        }

        performSaveCutscene(cutscene);
        logger.info("Saved cutscene: " + cutscene.getName() + " with " + cutscene.getFrames().size() + " frames");
    }

    @Override
    public Optional<Cutscene> loadCutscene(String name) throws DatabaseException {
        if (name == null || name.trim().isEmpty()) {
            throw new DatabaseException("Cutscene name cannot be null or empty");
        }

        CutsceneData data = performLoadCutscene(name.trim().toLowerCase());
        if (data == null) {
            return Optional.empty();
        }

        return Optional.of(convertToCutscene(data));
    }

    @Override
    public List<Cutscene> loadAllCutscenes() throws DatabaseException {
        List<CutsceneData> dataList = performLoadAllCutscenes();
        List<Cutscene> cutscenes = new ArrayList<>();

        for (CutsceneData data : dataList) {
            try {
                cutscenes.add(convertToCutscene(data));
            } catch (Exception e) {
                logger.warning("Failed to convert cutscene data for: " + data.name + ", " + e.getMessage());
            }
        }

        return cutscenes;
    }

    @Override
    public void deleteCutscene(String name) throws DatabaseException {
        if (name == null || name.trim().isEmpty()) {
            throw new DatabaseException("Cutscene name cannot be null or empty");
        }

        if (!cutsceneExists(name)) {
            throw new DatabaseException("Cutscene not found: " + name);
        }

        performDeleteCutscene(name.trim().toLowerCase());
        logger.info("Deleted cutscene: " + name);
    }

    @Override
    public boolean cutsceneExists(String name) throws DatabaseException {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }

        return performCutsceneExists(name.trim().toLowerCase());
    }

    @Override
    public List<String> getCutsceneNames() throws DatabaseException {
        List<String> names = performGetCutsceneNames();
        List<String> result = new ArrayList<>();

        for (String name : names) {
            if (name != null) {
                result.add(name);
            }
        }

        return result;
    }

    /**
     * Protected data class for cutscene data transfer
     */
    protected static class CutsceneData {
        public final String name;
        public final List<FrameData> frames;

        public CutsceneData(String name, List<FrameData> frames) {
            this.name = name;
            this.frames = frames != null ? frames : new ArrayList<>();
        }
    }

    /**
     * Protected data class for frame data transfer
     */
    protected static class FrameData {
        public final String world;
        public final double x;
        public final double y;
        public final double z;
        public final float yaw;
        public final float pitch;

        public FrameData(String world, double x, double y, double z, float yaw, float pitch) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    /**
     * Convert CutsceneData to Cutscene model
     */
    protected Cutscene convertToCutscene(CutsceneData data) throws DatabaseException {
        List<CutsceneFrame> frames = new ArrayList<>();

        for (FrameData frameData : data.frames) {
            if (frameData.world == null) {
                throw new DatabaseException("Frame data missing world for cutscene: " + data.name);
            }

            if (Bukkit.getWorld(frameData.world) == null) {
                logger.warning("World not found: " + frameData.world + " for cutscene: " + data.name);
                continue;
            }

            Location location = new Location(
                Bukkit.getWorld(frameData.world),
                frameData.x,
                frameData.y,
                frameData.z,
                frameData.yaw,
                frameData.pitch
            );

            frames.add(new CutsceneFrame(location));
        }

        if (frames.isEmpty()) {
            throw new DatabaseException("No valid frames found for cutscene: " + data.name);
        }

        return new Cutscene(data.name, frames);
    }

    /**
     * Convert Cutscene model to CutsceneData
     */
    protected CutsceneData convertFromCutscene(Cutscene cutscene) {
        List<FrameData> frames = new ArrayList<>();

        for (CutsceneFrame frame : cutscene.getFrames()) {
            Location location = frame.getLocation();
            if (location.getWorld() != null) {
                frames.add(new FrameData(
                    location.getWorld().getName(),
                    location.getX(),
                    location.getY(),
                    location.getZ(),
                    location.getYaw(),
                    location.getPitch()
                ));
            }
        }

        return new CutsceneData(cutscene.getName(), frames);
    }

    // Abstract methods that must be implemented by specific database services

    protected abstract void performSaveCutscene(Cutscene cutscene) throws DatabaseException;

    protected abstract CutsceneData performLoadCutscene(String name) throws DatabaseException;

    protected abstract List<CutsceneData> performLoadAllCutscenes() throws DatabaseException;

    protected abstract void performDeleteCutscene(String name) throws DatabaseException;

    protected abstract boolean performCutsceneExists(String name) throws DatabaseException;

    protected abstract List<String> performGetCutsceneNames() throws DatabaseException;
}
