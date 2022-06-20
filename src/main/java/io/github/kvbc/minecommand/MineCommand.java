package io.github.kvbc.minecommand;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.command.CommandException;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class MineCommand extends JavaPlugin implements Listener {
    private void print_error (String msg) {
        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[MineCommand] " + msg);
    }

    private void print_warn (String msg) {
        Bukkit.getConsoleSender().sendMessage(ChatColor.GOLD + "[MineCommand] " + msg);
    }

    private String format_config_string (String s, Player plr, Block block) {
        s = s.replaceAll("%player%",plr.getName())
             .replaceAll("%block%", block.getType().toString().toLowerCase());
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    @Override
    public void onEnable () {
        saveDefaultConfig();
        boolean any_err = false;

        //
        // Config checks
        //
        ConfigurationSection cfg_blocks = getConfig().getConfigurationSection("blocks");
        if (cfg_blocks == null) {
            print_error("Couldn't find the blocks list in the configuration file");
            any_err = true;
        }
        else for (Map.Entry<String,?> entry : cfg_blocks.getValues(true).entrySet()) {
            String cfg_block_type = entry.getKey();
            for (LinkedHashMap<String,?> data : (List<LinkedHashMap<String,?>>) entry.getValue()) {
                List<String> commands = (List<String>) data.get("commands");
                if ((commands == null) || commands.isEmpty())
                    print_warn("Command list is empty for block \"" + cfg_block_type + "\"");

                Number cfg_chance = (Number) data.get("chance");
                if (cfg_chance == null) {
                    print_error("Unspecified chance for block \"" + cfg_block_type + "\"");
                    any_err = true;
                }
                else {
                    double chance = cfg_chance.doubleValue();
                    if ((chance < 0.0) || (chance > 1.0)) {
                        print_error("Invalid chance range for block \"" + cfg_block_type + "\"");
                        any_err = true;
                    }
                }
            }
        }

        if (any_err)
            getServer().getPluginManager().disablePlugin(this);
        else
            getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onBlockBreak (BlockBreakEvent event) {
        Player plr = event.getPlayer();
        Block block = event.getBlock();
        if (plr.getGameMode() != GameMode.SURVIVAL)
            return;

        // Get the block of matching type from the blocks list
        String block_type = block.getType().toString().toLowerCase();
        List<LinkedHashMap<String,?>> cfg_block = (List<LinkedHashMap<String,?>>) getConfig().get("blocks." + block_type);
        if (cfg_block == null)
            return;

        // Sort by lowest chance
        cfg_block.sort((a, b) -> (int)Math.signum(
            ((Number)a.get("chance")).doubleValue() -
            ((Number)b.get("chance")).doubleValue()
        ));
        double random = Math.random();

        for (LinkedHashMap<String,?> data : cfg_block) {
            double chance = ((Number)data.get("chance")).doubleValue();
            if (random <= chance) {
                //
                // Commands
                //
                List<String> commands = (List<String>) data.get("commands");
                if (commands != null) {
                    for (String command : commands) {
                        command = format_config_string(command, plr, block);
                        try {
                            getServer().dispatchCommand(Bukkit.getConsoleSender(), command);
                        } catch (CommandException e) {
                            print_error("Exception when executing command \"" + command + "\" : " + e.getMessage());
                            return;
                        }
                    }
                }
                //
                // Messages Player
                //
                List<String> messages_player = (List<String>) data.get("messages_player");
                if (messages_player != null) {
                    for (String msg : messages_player) {
                        msg = format_config_string(msg, plr, block);
                        event.getPlayer().sendMessage(msg);
                    }
                }
                //
                // Messages Server
                //
                List<String> messages_server = (List<String>) data.get("messages_server");
                if (messages_server != null) {
                    for (String msg : messages_server) {
                        msg = format_config_string(msg, plr, block);
                        getServer().broadcastMessage(msg);
                    }
                }
                break;
            }
        }
    }
}
