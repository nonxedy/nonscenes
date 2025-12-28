package com.nonxedy.listener;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.nonxedy.Nonscenes;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Packet listener to handle camera and UI during cutscenes
 */
public class CutscenePacketListener extends PacketAdapter {
    private final Nonscenes plugin;
    private final Set<Player> activeCutscenePlayers = ConcurrentHashMap.newKeySet();

    public CutscenePacketListener(Nonscenes plugin) {
        super(plugin, PacketType.Play.Server.CAMERA);
        this.plugin = plugin;
    }

    public void addPlayer(Player player) {
        activeCutscenePlayers.add(player);
    }

    public void removePlayer(Player player) {
        activeCutscenePlayers.remove(player);
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        Player player = event.getPlayer();
        if (!activeCutscenePlayers.contains(player)) {
            return;
        }

        // Prevent camera packets that might change perspective
        event.setCancelled(true);
    }

    /**
     * Force first person camera for the player
     */
    public void forceFirstPerson(Player player) {
        if (!activeCutscenePlayers.contains(player) || plugin.getProtocolManager() == null) {
            return;
        }

        try {
            // Send camera packet to set camera to the player's own entity
            PacketContainer cameraPacket = plugin.getProtocolManager().createPacket(PacketType.Play.Server.CAMERA);
            cameraPacket.getIntegers().write(0, player.getEntityId());
            plugin.getProtocolManager().sendServerPacket(player, cameraPacket);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send camera packet to " + player.getName() + ": " + e.getMessage());
        }
    }


}
