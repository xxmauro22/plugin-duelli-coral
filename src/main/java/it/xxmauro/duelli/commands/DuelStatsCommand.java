package it.xxmauro.duelli.commands;

import it.xxmauro.duelli.DuelliPlugin;
import it.xxmauro.duelli.managers.StatsManager;
import it.xxmauro.duelli.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public class DuelStatsCommand implements CommandExecutor, TabCompleter {

    private final DuelliPlugin plugin;

    public DuelStatsCommand(DuelliPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessageUtil().colorize("&cSolo i giocatori possono usare questo comando."));
            return true;
        }

        Player target = player;
        
        if (args.length >= 1) {
            if (!player.hasPermission("duelli.stats.others")) {
                plugin.getMessageUtil().send(player, "no-permission");
                return true;
            }
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                plugin.getMessageUtil().send(player, "player-not-found",
                    MessageUtil.Placeholder.of("player", args[0]));
                return true;
            }
        }
        
        showStats(player, target);
        return true;
    }

    private void showStats(Player viewer, Player target) {
        plugin.getStatsManager().getStats(target.getUniqueId()).thenAccept(stats -> {
            if (stats == null) {
                Bukkit.getScheduler().runTask(plugin, () -> 
                    plugin.getMessageUtil().send(viewer, "no-stats",
                        MessageUtil.Placeholder.of("player", target.getName())));
                return;
            }
            
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getMessageUtil().sendRaw(viewer, plugin.getMessageUtil().getMessagesConfig().getString("messages.stats-header", ""));
                plugin.getMessageUtil().sendRaw(viewer, plugin.getMessageUtil().parsePlaceholders(
                    plugin.getMessageUtil().getMessagesConfig().getString("messages.stats-player", ""),
                    MessageUtil.Placeholder.of("player", target.getName())));
                plugin.getMessageUtil().sendRaw(viewer, plugin.getMessageUtil().parsePlaceholders(
                    plugin.getMessageUtil().getMessagesConfig().getString("messages.stats-duels", ""),
                    MessageUtil.Placeholder.of("duels", String.valueOf(stats.duels))));
                plugin.getMessageUtil().sendRaw(viewer, plugin.getMessageUtil().parsePlaceholders(
                    plugin.getMessageUtil().getMessagesConfig().getString("messages.stats-wins", ""),
                    MessageUtil.Placeholder.of("wins", String.valueOf(stats.wins))));
                plugin.getMessageUtil().sendRaw(viewer, plugin.getMessageUtil().parsePlaceholders(
                    plugin.getMessageUtil().getMessagesConfig().getString("messages.stats-losses", ""),
                    MessageUtil.Placeholder.of("losses", String.valueOf(stats.losses))));
                plugin.getMessageUtil().sendRaw(viewer, plugin.getMessageUtil().parsePlaceholders(
                    plugin.getMessageUtil().getMessagesConfig().getString("messages.stats-draws", ""),
                    MessageUtil.Placeholder.of("draws", String.valueOf(stats.draws))));
                plugin.getMessageUtil().sendRaw(viewer, plugin.getMessageUtil().parsePlaceholders(
                    plugin.getMessageUtil().getMessagesConfig().getString("messages.stats-winrate", ""),
                    MessageUtil.Placeholder.of("winrate", String.format("%.1f", stats.getWinRate()))));
                plugin.getMessageUtil().sendRaw(viewer, plugin.getMessageUtil().parsePlaceholders(
                    plugin.getMessageUtil().getMessagesConfig().getString("messages.stats-streak", ""),
                    MessageUtil.Placeholder.of("streak", String.valueOf(stats.currentStreak))));
                plugin.getMessageUtil().sendRaw(viewer, plugin.getMessageUtil().parsePlaceholders(
                    plugin.getMessageUtil().getMessagesConfig().getString("messages.stats-best-streak", ""),
                    MessageUtil.Placeholder.of("beststreak", String.valueOf(stats.bestStreak))));
                plugin.getMessageUtil().sendRaw(viewer, plugin.getMessageUtil().parsePlaceholders(
                    plugin.getMessageUtil().getMessagesConfig().getString("messages.stats-kills", ""),
                    MessageUtil.Placeholder.of("kills", String.valueOf(stats.kills))));
                plugin.getMessageUtil().sendRaw(viewer, plugin.getMessageUtil().parsePlaceholders(
                    plugin.getMessageUtil().getMessagesConfig().getString("messages.stats-deaths", ""),
                    MessageUtil.Placeholder.of("deaths", String.valueOf(stats.deaths))));
                plugin.getMessageUtil().sendRaw(viewer, plugin.getMessageUtil().parsePlaceholders(
                    plugin.getMessageUtil().getMessagesConfig().getString("messages.stats-kd", ""),
                    MessageUtil.Placeholder.of("kd", String.format("%.2f", stats.getKDRatio()))));
                plugin.getMessageUtil().sendRaw(viewer, plugin.getMessageUtil().getMessagesConfig().getString("messages.stats-footer", ""));
            });
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("duelli.stats.others")) {
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                .toList();
        }
        return List.of();
    }
}
