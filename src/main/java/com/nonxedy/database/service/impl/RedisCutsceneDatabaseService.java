package com.nonxedy.database.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import com.nonxedy.database.exception.DatabaseException;
import com.nonxedy.model.Cutscene;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * Redis implementation of CutsceneDatabaseService
 */
public class RedisCutsceneDatabaseService extends BaseCutsceneDatabaseService {

    private JedisPool jedisPool;
    private final String host;
    private final int port;
    private final String password;
    private final int database;
    private final String keyPrefix;

    public RedisCutsceneDatabaseService(String host, int port, String password, int database, String keyPrefix) {
        this.host = host != null ? host : "localhost";
        this.port = port > 0 ? port : 6379;
        this.password = password;
        this.database = database >= 0 ? database : 0;
        this.keyPrefix = keyPrefix != null ? keyPrefix : "nonscenes:";
    }

    @Override
    public void initialize() throws DatabaseException {
        try {
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(10);
            poolConfig.setMaxIdle(5);
            poolConfig.setMinIdle(1);
            poolConfig.setTestOnBorrow(true);
            poolConfig.setTestOnReturn(true);
            poolConfig.setTestWhileIdle(true);

            if (password != null && !password.trim().isEmpty()) {
                this.jedisPool = new JedisPool(poolConfig, host, port, 2000, password, database);
            } else {
                this.jedisPool = new JedisPool(poolConfig, host, port, 2000, null, database);
            }

            // Test connection
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.ping();
            }

            logger.log(Level.INFO, "Redis database initialized with host: {0}:{1}, database: {2}",
                      new Object[]{host, port, database});

        } catch (Exception e) {
            throw new DatabaseException("Failed to initialize Redis database", e);
        }
    }

    @Override
    protected void performSaveCutscene(Cutscene cutscene) throws DatabaseException {
        CutsceneData data = convertFromCutscene(cutscene);
        String cutsceneKey = keyPrefix + "cutscene:" + data.name.toLowerCase();

        try (Jedis jedis = jedisPool.getResource()) {
            // Clear existing frames
            jedis.del(cutsceneKey + ":frames");

            // Save cutscene metadata
            jedis.hset(cutsceneKey + ":meta", "name", data.name);
            jedis.hset(cutsceneKey + ":meta", "frame_count", String.valueOf(data.frames.size()));
            jedis.hset(cutsceneKey + ":meta", "updated_at", String.valueOf(System.currentTimeMillis()));

            // Save frames
            for (int i = 0; i < data.frames.size(); i++) {
                FrameData frame = data.frames.get(i);
                String frameKey = cutsceneKey + ":frames:" + i;

                jedis.hset(frameKey, "world", frame.world);
                jedis.hset(frameKey, "x", String.valueOf(frame.x));
                jedis.hset(frameKey, "y", String.valueOf(frame.y));
                jedis.hset(frameKey, "z", String.valueOf(frame.z));
                jedis.hset(frameKey, "yaw", String.valueOf(frame.yaw));
                jedis.hset(frameKey, "pitch", String.valueOf(frame.pitch));

                // Add frame index to frames set for easy retrieval
                jedis.sadd(cutsceneKey + ":frames", String.valueOf(i));
            }

            // Add to cutscenes index
            jedis.sadd(keyPrefix + "cutscenes", data.name.toLowerCase());

            logger.log(Level.FINE, "Successfully saved cutscene: {0}", data.name);

        } catch (Exception e) {
            throw new DatabaseException("Failed to save cutscene: " + cutscene.getName(), e);
        }
    }

    @Override
    protected CutsceneData performLoadCutscene(String name) throws DatabaseException {
        String cutsceneKey = keyPrefix + "cutscene:" + name.toLowerCase();

        try (Jedis jedis = jedisPool.getResource()) {
            // Check if cutscene exists
            if (!jedis.exists(cutsceneKey + ":meta")) {
                return null;
            }

            // Get frame count
            String frameCountStr = jedis.hget(cutsceneKey + ":meta", "frame_count");
            if (frameCountStr == null) {
                return new CutsceneData(name, new ArrayList<>());
            }

            int frameCount = Integer.parseInt(frameCountStr);
            List<FrameData> frames = new ArrayList<>();

            // Load frames
            for (int i = 0; i < frameCount; i++) {
                String frameKey = cutsceneKey + ":frames:" + i;
                Map<String, String> frameData = jedis.hgetAll(frameKey);

                if (!frameData.isEmpty()) {
                    FrameData frame = new FrameData(
                        frameData.get("world"),
                        Double.parseDouble(frameData.get("x")),
                        Double.parseDouble(frameData.get("y")),
                        Double.parseDouble(frameData.get("z")),
                        Float.parseFloat(frameData.get("yaw")),
                        Float.parseFloat(frameData.get("pitch"))
                    );
                    frames.add(frame);
                }
            }

            return new CutsceneData(name, frames);

        } catch (Exception e) {
            throw new DatabaseException("Failed to load cutscene: " + name, e);
        }
    }

    @Override
    protected List<CutsceneData> performLoadAllCutscenes() throws DatabaseException {
        try (Jedis jedis = jedisPool.getResource()) {
            List<CutsceneData> cutscenes = new ArrayList<>();

            // Get all cutscene names
            Set<String> cutsceneNames = jedis.smembers(keyPrefix + "cutscenes");

            for (String cutsceneName : cutsceneNames) {
                CutsceneData cutsceneData = performLoadCutscene(cutsceneName);
                if (cutsceneData != null) {
                    cutscenes.add(cutsceneData);
                }
            }

            return cutscenes;

        } catch (Exception e) {
            throw new DatabaseException("Failed to load all cutscenes", e);
        }
    }

    @Override
    protected void performDeleteCutscene(String name) throws DatabaseException {
        String cutsceneKey = keyPrefix + "cutscene:" + name.toLowerCase();

        try (Jedis jedis = jedisPool.getResource()) {
            // Check if cutscene exists
            if (!jedis.exists(cutsceneKey + ":meta")) {
                throw new DatabaseException("Cutscene not found for deletion: " + name);
            }

            // Remove from index
            jedis.srem(keyPrefix + "cutscenes", name.toLowerCase());

            // Get all frame indices
            Set<String> frameIndices = jedis.smembers(cutsceneKey + ":frames");

            // Delete all frame keys
            for (String frameIndex : frameIndices) {
                jedis.del(cutsceneKey + ":frames:" + frameIndex);
            }

            // Delete metadata and frames set
            jedis.del(cutsceneKey + ":meta");
            jedis.del(cutsceneKey + ":frames");

        } catch (Exception e) {
            throw new DatabaseException("Failed to delete cutscene: " + name, e);
        }
    }

    @Override
    protected boolean performCutsceneExists(String name) throws DatabaseException {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.exists(keyPrefix + "cutscene:" + name.toLowerCase() + ":meta");

        } catch (Exception e) {
            throw new DatabaseException("Failed to check if cutscene exists: " + name, e);
        }
    }

    @Override
    protected List<String> performGetCutsceneNames() throws DatabaseException {
        try (Jedis jedis = jedisPool.getResource()) {
            Set<String> cutsceneNames = jedis.smembers(keyPrefix + "cutscenes");
            return new ArrayList<>(cutsceneNames);

        } catch (Exception e) {
            throw new DatabaseException("Failed to get cutscene names", e);
        }
    }

    @Override
    public void shutdown() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
            logger.info("Redis connection pool closed");
        }
    }
}
