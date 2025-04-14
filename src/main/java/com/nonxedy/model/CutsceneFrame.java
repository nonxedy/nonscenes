package com.nonxedy.model;

import org.bukkit.Location;

/**
 * Represents a single frame in a cutscene
 */
public class CutsceneFrame {
    private final Location location;
    
    /**
     * Creates a new cutscene frame
     * @param location The location of the frame
     */
    public CutsceneFrame(Location location) {
        this.location = location;
    }
    
    /**
     * Gets the location of the frame
     * @return The location
     */
    public Location getLocation() {
        return location;
    }
}
