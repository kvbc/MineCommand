package io.github.kvbc.minecommand;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.command.CommandException;
import org.bukkit.configuration.ConfigurationSection;
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
        if (event.getPlayer().getGameMode() != GameMode.SURVIVAL)
            return;

        // Get the block of matching type from the blocks list
        String block_type = event.getBlock().getType().toString().toLowerCase();
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
                List<String> commands = (List<String>) data.get("commands");
                if (commands == null)
                    continue;
                for (String command : commands) {
                    command = command.replaceAll("%player%", event.getPlayer().getName());
                    command = command.replaceAll("%block%", block_type);
                    try {
                        getServer().dispatchCommand(Bukkit.getConsoleSender(), command);
                    } catch (CommandException e) {
                        print_error("Exception when executing command \"" + command + "\" : " + e.getMessage());
                        return;
                    }
                }
                break;
            }
        }
    }
}
