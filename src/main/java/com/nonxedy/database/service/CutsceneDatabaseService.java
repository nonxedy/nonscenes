package com.nonxedy.database.service;

import java.util.List;
import java.util.Optional;

import com.nonxedy.database.exception.DatabaseException;
import com.nonxedy.model.Cutscene;

/**
 * Service interface for database operations on cutscenes
 */
public interface CutsceneDatabaseService {

    /**
     * Initialize the database connection and create tables if needed
     * @throws DatabaseException if initialization fails
     */
    void initialize() throws DatabaseException;

    /**
     * Save a cutscene to the database
     * @param cutscene The cutscene to save
     * @throws DatabaseException if save operation fails
     */
    void saveCutscene(Cutscene cutscene) throws DatabaseException;

    /**
     * Load a cutscene from the database by name
     * @param name The name of the cutscene
     * @return Optional containing the cutscene if found
     * @throws DatabaseException if load operation fails
     */
    Optional<Cutscene> loadCutscene(String name) throws DatabaseException;

    /**
     * Load all cutscenes from the database
     * @return List of all cutscenes
     * @throws DatabaseException if load operation fails
     */
    List<Cutscene> loadAllCutscenes() throws DatabaseException;

    /**
     * Delete a cutscene from the database
     * @param name The name of the cutscene to delete
     * @throws DatabaseException if delete operation fails
     */
    void deleteCutscene(String name) throws DatabaseException;

    /**
     * Check if a cutscene exists in the database
     * @param name The name of the cutscene
     * @return true if cutscene exists
     * @throws DatabaseException if check operation fails
     */
    boolean cutsceneExists(String name) throws DatabaseException;

    /**
     * Get all cutscene names from the database
     * @return List of cutscene names
     * @throws DatabaseException if operation fails
     */
    List<String> getCutsceneNames() throws DatabaseException;

    /**
     * Close the database connection
     */
    void shutdown();
}
