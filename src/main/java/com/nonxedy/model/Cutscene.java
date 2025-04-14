package com.nonxedy.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a cutscene with a name and a list of frames
 */
public class Cutscene {
    private final String name;
    private final List<CutsceneFrame> frames;
    
    /**
     * Creates a new cutscene
     * @param name The name of the cutscene
     * @param frames The frames of the cutscene
     */
    public Cutscene(String name, List<CutsceneFrame> frames) {
        this.name = name;
        this.frames = new ArrayList<>(frames);
    }
    
    /**
     * Gets the name of the cutscene
     * @return The name
     */
    public String getName() {
        return name;
    }
    
    /**
     * Gets the frames of the cutscene
     * @return The frames
     */
    public List<CutsceneFrame> getFrames() {
        return frames;
    }
}
