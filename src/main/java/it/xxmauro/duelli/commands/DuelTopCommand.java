package it.xxmauro.duelli.commands;

import it.xxmauro.duelli.DuelliPlugin;
import it.xxmauro.duelli.managers.LeaderboardManager;
import it.xxmauro.duelli.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class DuelTopCommand implements CommandExecutor, TabCompleter {

    private final DuelliPlugin plugin;

    public DuelTopCommand(DuelliPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessageUtil().colorize("&cSolo i giocatori possono usare questo comando."));
            return true;
        }

        if (!player.hasPermission("duelli.top")) {
            plugin.getMessageUtil().send(player, "no-permission");
            return true;
        }

        String typeStr = args.length >= 1 ? args[0] : "vittorie";
        int page = args.length >= 2 ? parsePage(args[1]) : 1;
        
        LeaderboardManager.LeaderboardType type = LeaderboardManager.LeaderboardType.fromString(typeStr);
        
        int perPage = plugin.getConfigManager().getConfig().getInt("leaderboard.entries-per-page", 10);
        
        plugin.getLeaderboardManager().getLeaderboard(type, page, perPage).thenAccept(entries -> {
            plugin.getLeaderboardManager().getTotalPages(type, perPage).thenAccept(totalPages -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    String header = plugin.getMessageUtil().parsePlaceholders(
                        plugin.getMessageUtil().getMessagesConfig().getString("messages.top-header", ""),
                        MessageUtil.Placeholder.of("type", type.getDisplayName()));
                    player.sendMessage(plugin.getMessageUtil().colorize(header));
                    
                    if (entries.isEmpty()) {
                        player.sendMessage(plugin.getMessageUtil().colorize(
                            plugin.getMessageUtil().getMessagesConfig().getString("messages.top-empty", "")));
                    } else {
                        for (LeaderboardManager.LeaderboardEntry entry : entries) {
                            String line = plugin.getMessageUtil().parsePlaceholders(
                                plugin.getMessageUtil().getMessagesConfig().getString("messages.top-entry", ""),
                                MessageUtil.Placeholder.of("pos", String.valueOf(entry.position())),
                                MessageUtil.Placeholder.of("player", entry.name()),
                                MessageUtil.Placeholder.of("value", String.valueOf(entry.value())));
                            player.sendMessage(plugin.getMessageUtil().colorize(line));
                        }
                    }
                    
                    String footer = plugin.getMessageUtil().parsePlaceholders(
                        plugin.getMessageUtil().getMessagesConfig().getString("messages.top-footer", ""));
                    player.sendMessage(plugin.getMessageUtil().colorize(footer));
                    
                    String pageMsg = plugin.getMessageUtil().parsePlaceholders(
                        plugin.getMessageUtil().getMessagesConfig().getString("messages.top-page", ""),
                        MessageUtil.Placeholder.of("page", String.valueOf(page)),
                        MessageUtil.Placeholder.of("pages", String.valueOf(totalPages)),
                        MessageUtil.Placeholder.of("type", type.getId()),
                        MessageUtil.Placeholder.of("next", String.valueOf(Math.min(page + 1, totalPages))));
                    player.sendMessage(plugin.getMessageUtil().colorize(pageMsg));
                });
            });
        });
        
        return true;
    }

    private int parsePage(String str) {
        try {
            int page = Integer.parseInt(str);
            return Math.max(1, page);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.stream(LeaderboardManager.LeaderboardType.values())
                .map(LeaderboardManager.LeaderboardType::getId)
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        if (args.length == 2) {
            return Arrays.asList("1", "2", "3", "4", "5").stream()
                .filter(s -> s.startsWith(args[1]))
                .toList();
        }
        return List.of();
    }
}
