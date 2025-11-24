package com.nonxedy.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import com.nonxedy.Nonscenes;
import com.nonxedy.core.ConfigManager;
import com.nonxedy.util.ColorUtil;

/**
 * Listener to disable player input during cutscene playback
 */
public class PlayerInputListener implements Listener {
    private final Nonscenes plugin;
    private final ConfigManager configManager;

    public PlayerInputListener(Nonscenes plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
    }

    private boolean shouldDisableInput(Player player) {
        return configManager.getConfig().getBoolean("settings.playback.disable-input", true) &&
               plugin.getCutsceneManager().isWatchingCutscene(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (shouldDisableInput(event.getPlayer())) {
            // Cancel movement by preventing the move
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (shouldDisableInput(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ColorUtil.format(configManager.getMessage("command-disabled-during-cutscene")));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (shouldDisableInput(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && shouldDisableInput(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && shouldDisableInput(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (shouldDisableInput(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (shouldDisableInput(event.getPlayer())) {
            event.setCancelled(true);
        }
    }
}
