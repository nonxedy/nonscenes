package com.nonxedy.command;

import com.nonxedy.Nonscenes;
import com.nonxedy.core.ConfigManager;
import com.nonxedy.core.CutsceneManager;
import com.nonxedy.util.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class NonsceneCommand implements CommandExecutor, TabCompleter {
    private final Nonscenes plugin;
    private final CutsceneManager cutsceneManager;
    private final ConfigManager configManager;

    public NonsceneCommand(Nonscenes plugin) {
        this.plugin = plugin;
        this.cutsceneManager = plugin.getCutsceneManager();
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(configManager.getMessage("player-only-command"));
            return true;
        }

        Player player = (Player) sender;

        // Check if player has permission
        if (!player.hasPermission("nonscene.use")) {
            player.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sendHelpMessage(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "start":
                if (!player.hasPermission("nonscene.start")) {
                    player.sendMessage(configManager.getMessage("no-permission"));
                    return true;
                }
                
                if (args.length < 3) {
                    player.sendMessage(configManager.getMessage("invalid-start-args"));
                    return true;
                }
                
                String name = args[1];
                int frames;
                
                try {
                    frames = Integer.parseInt(args[2]);
                    if (frames <= 0) {
                        player.sendMessage(configManager.getMessage("invalid-frames-number"));
                        return true;
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage(configManager.getMessage("invalid-frames-number"));
                    return true;
                }
                
                cutsceneManager.startRecording(player, name, frames);
                break;
                
            case "delete":
                if (!player.hasPermission("nonscene.delete")) {
                    player.sendMessage(configManager.getMessage("no-permission"));
                    return true;
                }
                
                if (args.length < 2) {
                    player.sendMessage(configManager.getMessage("specify-cutscene-name"));
                    return true;
                }
                
                cutsceneManager.deleteCutscene(player, args[1]);
                break;
                
            case "all":
                if (!player.hasPermission("nonscene.list")) {
                    player.sendMessage(configManager.getMessage("no-permission"));
                    return true;
                }
                
                cutsceneManager.listAllCutscenes(player);
                break;
                
            case "play":
                if (!player.hasPermission("nonscene.play")) {
                    player.sendMessage(configManager.getMessage("no-permission"));
                    return true;
                }
                
                if (args.length < 2) {
                    player.sendMessage(configManager.getMessage("specify-cutscene-name"));
                    return true;
                }
                
                cutsceneManager.playCutscene(player, args[1]);
                break;
                
            case "showpath":
                if (!player.hasPermission("nonscene.showpath")) {
                    player.sendMessage(configManager.getMessage("no-permission"));
                    return true;
                }
                
                if (args.length < 2) {
                    player.sendMessage(configManager.getMessage("specify-cutscene-name"));
                    return true;
                }
                
                cutsceneManager.showCutscenePath(player, args[1]);
                break;
                
            default:
                sendHelpMessage(player);
                break;
        }
        
        return true;
    }

    private void sendHelpMessage(Player player) {
        for (String line : configManager.getHelpMessages()) {
            player.sendMessage(ColorUtil.format(line));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (!(sender instanceof Player)) {
            return completions;
        }
        
        Player player = (Player) sender;
        
        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>();
            
            if (player.hasPermission("nonscene.start")) subCommands.add("start");
            if (player.hasPermission("nonscene.delete")) subCommands.add("delete");
            if (player.hasPermission("nonscene.list")) subCommands.add("all");
            if (player.hasPermission("nonscene.play")) subCommands.add("play");
            if (player.hasPermission("nonscene.showpath")) subCommands.add("showpath");
            
            return filterCompletions(subCommands, args[0]);
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            
            if ((subCommand.equals("delete") || subCommand.equals("play") || subCommand.equals("showpath")) 
                    && player.hasPermission("nonscene." + subCommand)) {
                return filterCompletions(cutsceneManager.getCutsceneNames(), args[1]);
            }
        }
        
        return completions;
    }
    
    private List<String> filterCompletions(List<String> options, String input) {
        return options.stream()
                .filter(option -> option.toLowerCase().startsWith(input.toLowerCase()))
                .collect(Collectors.toList());
    }
}
