package com.nonxedy.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.scheduler.BukkitRunnable;

import com.nonxedy.Nonscenes;

/**
 * Listener for world load events to trigger cutscene loading after worlds are available
 */
public class WorldLoadListener implements Listener {

    private final Nonscenes plugin;
    private boolean cutscenesLoaded = false;

    public WorldLoadListener(Nonscenes plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        // Load cutscenes after a short delay to ensure all worlds are loaded
        if (!cutscenesLoaded) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!cutscenesLoaded) {
                        plugin.getCutsceneManager().loadCutscenes();
                        cutscenesLoaded = true;
                        plugin.getLogger().info("Cutscenes loaded after world initialization");
                    }
                }
            }.runTaskLater(plugin, 20L); // 1 second delay
        }
    }

    /**
     * Force load cutscenes if they haven't been loaded yet.
     * This can be called as a fallback.
     */
    public void loadCutscenesIfNeeded() {
        if (!cutscenesLoaded) {
            plugin.getCutsceneManager().loadCutscenes();
            cutscenesLoaded = true;
            plugin.getLogger().info("Cutscenes loaded via fallback method");
        }
    }
}
