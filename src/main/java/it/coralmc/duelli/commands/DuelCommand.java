package it.coralmc.duelli.commands;

import it.coralmc.duelli.DuelliPlugin;
import it.coralmc.duelli.managers.KitManager;
import it.coralmc.duelli.objects.DuelRequest;
import it.coralmc.duelli.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class DuelCommand implements CommandExecutor, TabCompleter {

    private final DuelliPlugin plugin;

    public DuelCommand(DuelliPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessageUtil().colorize("&cSolo i giocatori possono usare questo comando."));
            return true;
        }

        if (!player.hasPermission("duelli.use")) {
            plugin.getMessageUtil().send(player, "no-permission");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "help", "h", "?" -> sendHelp(player);
            case "sfida", "challenge", "duel" -> handleChallenge(player, args);
            case "accetta", "accept", "a" -> handleAccept(player, args);
            case "rifiuta", "deny", "r" -> handleDeny(player, args);
            case "annulla", "cancel", "c" -> handleCancel(player);
            case "kit", "k" -> handleKit(player, args);
            case "stats", "statistiche", "s" -> handleStats(player, args);
            case "top", "classifica", "t" -> handleTop(player, command, label, args);
            default -> handleChallenge(player, args);
        }
        return true;
    }

    private void handleChallenge(Player player, String[] args) {
        if (args.length < 1) {
            plugin.getMessageUtil().send(player, "error-no-kit");
            return;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayerExact(targetName);
        
        if (target == null) {
            plugin.getMessageUtil().send(player, "player-not-found",
                MessageUtil.Placeholder.of("player", targetName));
            return;
        }

        if (target.equals(player)) {
            plugin.getMessageUtil().send(player, "cannot-duel-self");
            return;
        }

        String kitName = args.length >= 2 ? args[1] : plugin.getKitManager().getDefaultKit().getId();
        KitManager.Kit kit = plugin.getKitManager().getKit(kitName);
        
        if (kit == null) {
            plugin.getMessageUtil().send(player, "kit-not-found",
                MessageUtil.Placeholder.of("kit", kitName));
            return;
        }

        if (!plugin.getKitManager().canUseKit(player, kit.getId())) {
            plugin.getMessageUtil().send(player, "kit-no-permission",
                MessageUtil.Placeholder.of("kit", kit.getName()));
            return;
        }

        if (!plugin.getKitManager().canUseKit(target, kit.getId())) {
            plugin.getMessageUtil().send(player, "target-kit-no-permission",
                MessageUtil.Placeholder.of("player", target.getName()),
                MessageUtil.Placeholder.of("kit", kit.getName()));
            return;
        }

        if (plugin.getDuelManager().isInDuel(player)) {
            plugin.getMessageUtil().send(player, "already-in-duel");
            return;
        }

        if (plugin.getDuelManager().isInDuel(target)) {
            plugin.getMessageUtil().send(player, "target-already-in-duel",
                MessageUtil.Placeholder.of("player", target.getName()));
            return;
        }

        if (plugin.getDuelManager().isOnCooldown(player)) {
            long remaining = plugin.getDuelManager().getRemainingCooldown(player.getUniqueId());
            plugin.getMessageUtil().send(player, "cooldown",
                MessageUtil.Placeholder.of("seconds", String.valueOf(remaining)));
            return;
        }

        boolean sent = plugin.getDuelManager().sendRequest(player, target, kit.getId());
        
        if (sent) {
            plugin.getMessageUtil().send(player, "challenge-sent",
                MessageUtil.Placeholder.of("target", target.getName()),
                MessageUtil.Placeholder.of("kit", kit.getName()));
            plugin.getMessageUtil().send(target, "challenge-received",
                MessageUtil.Placeholder.of("player", player.getName()),
                MessageUtil.Placeholder.of("kit", kit.getName()));
        }
    }

    private void handleAccept(Player player, String[] args) {
        String fromName = args.length >= 2 ? args[1] : null;
        
        var requestOpt = plugin.getDuelManager().getPendingRequest(player);
        if (requestOpt.isEmpty()) {
            plugin.getMessageUtil().send(player, "no-pending-challenge");
            return;
        }
        
        DuelRequest request = requestOpt.get();
        Player sender = Bukkit.getPlayer(request.getSenderId());
        
        if (sender == null) {
            plugin.getMessageUtil().send(player, "player-offline",
                MessageUtil.Placeholder.of("player", "Sconosciuto"));
            return;
        }
        
        if (fromName != null && !sender.getName().equalsIgnoreCase(fromName)) {
            plugin.getMessageUtil().send(player, "no-pending-challenge-from",
                MessageUtil.Placeholder.of("player", fromName));
            return;
        }
        
        boolean accepted = plugin.getDuelManager().acceptRequest(player);
        if (!accepted) {
            plugin.getMessageUtil().send(player, "error-generic");
        }
    }

    private void handleDeny(Player player, String[] args) {
        String fromName = args.length >= 2 ? args[1] : null;
        
        var requestOpt = plugin.getDuelManager().getPendingRequest(player);
        if (requestOpt.isEmpty()) {
            plugin.getMessageUtil().send(player, "no-pending-challenge");
            return;
        }
        
        DuelRequest request = requestOpt.get();
        Player sender = Bukkit.getPlayer(request.getSenderId());
        
        if (sender != null && fromName != null && !sender.getName().equalsIgnoreCase(fromName)) {
            plugin.getMessageUtil().send(player, "no-pending-challenge-from",
                MessageUtil.Placeholder.of("player", fromName));
            return;
        }
        
        plugin.getDuelManager().denyRequest(player);
    }

    private void handleCancel(Player player) {
        boolean cancelled = plugin.getDuelManager().cancelRequest(player);
        if (cancelled) {
            plugin.getMessageUtil().send(player, "challenge-cancelled");
        } else {
            plugin.getMessageUtil().send(player, "no-pending-challenge");
        }
    }

    private void handleKit(Player player, String[] args) {
        if (args.length == 1) {
            List<KitManager.Kit> kits = new ArrayList<>(plugin.getKitManager().getAllKits().values());
            player.sendMessage(plugin.getMessageUtil().colorize("&8&m----------------&r &6&lKIT DISPONIBILI &r&8&m----------------"));
            for (KitManager.Kit kit : kits) {
                boolean canUse = plugin.getKitManager().canUseKit(player, kit.getId());
                String color = canUse ? "&a" : "&c";
                player.sendMessage(plugin.getMessageUtil().colorize(
                    color + kit.getName() + " &8- &7" + 
                    (kit.getDescription().isEmpty() ? "" : kit.getDescription().get(0))
                ));
            }
            player.sendMessage(plugin.getMessageUtil().colorize("&8&m--------------------------------------------------"));
            return;
        }
        
        if (args.length == 2 && args[1].equalsIgnoreCase("info")) {
            if (args.length < 3) return;
            KitManager.Kit kit = plugin.getKitManager().getKit(args[2]);
            if (kit == null) {
                plugin.getMessageUtil().send(player, "kit-not-found",
                    MessageUtil.Placeholder.of("kit", args[2]));
                return;
            }
            player.sendMessage(plugin.getMessageUtil().colorize("&8&m----------------&r &6&lINFO KIT: " + kit.getName() + " &r&8&m----------------"));
            for (String line : kit.getDescription()) {
                player.sendMessage(plugin.getMessageUtil().colorize("&7" + line));
            }
            player.sendMessage(plugin.getMessageUtil().colorize("&ePermesso: &f" + kit.getPermission()));
            player.sendMessage(plugin.getMessageUtil().colorize("&8&m--------------------------------------------------"));
            return;
        }
        
        plugin.getMessageUtil().send(player, "error-no-kit");
    }

    private void handleStats(Player player, String[] args) {
        Player target = player;
        
        if (args.length >= 2) {
            if (!player.hasPermission("duelli.stats.others")) {
                plugin.getMessageUtil().send(player, "no-permission");
                return;
            }
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                plugin.getMessageUtil().send(player, "player-not-found",
                    MessageUtil.Placeholder.of("player", args[1]));
                return;
            }
        }
        
        final Player targetFinal = target;
        plugin.getStatsManager().getStats(target.getUniqueId()).thenAccept(stats -> {
            if (stats == null) {
                Bukkit.getScheduler().runTask(plugin, () -> 
                    plugin.getMessageUtil().send(player, "no-stats",
                        MessageUtil.Placeholder.of("player", targetFinal.getName())));
                return;
            }
            
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getMessageUtil().sendRaw(player, plugin.getMessageUtil().getMessagesConfig().getString("messages.stats-header", ""));
                plugin.getMessageUtil().sendRaw(player, plugin.getMessageUtil().parsePlaceholders(
                    plugin.getMessageUtil().getMessagesConfig().getString("messages.stats-player", ""),
                    MessageUtil.Placeholder.of("player", targetFinal.getName())));
                plugin.getMessageUtil().sendRaw(player, plugin.getMessageUtil().parsePlaceholders(
                    plugin.getMessageUtil().getMessagesConfig().getString("messages.stats-duels", ""),
                    MessageUtil.Placeholder.of("duels", String.valueOf(stats.duels))));
                plugin.getMessageUtil().sendRaw(player, plugin.getMessageUtil().parsePlaceholders(
                    plugin.getMessageUtil().getMessagesConfig().getString("messages.stats-wins", ""),
                    MessageUtil.Placeholder.of("wins", String.valueOf(stats.wins))));
                plugin.getMessageUtil().sendRaw(player, plugin.getMessageUtil().parsePlaceholders(
                    plugin.getMessageUtil().getMessagesConfig().getString("messages.stats-losses", ""),
                    MessageUtil.Placeholder.of("losses", String.valueOf(stats.losses))));
                plugin.getMessageUtil().sendRaw(player, plugin.getMessageUtil().parsePlaceholders(
                    plugin.getMessageUtil().getMessagesConfig().getString("messages.stats-draws", ""),
                    MessageUtil.Placeholder.of("draws", String.valueOf(stats.draws))));
                plugin.getMessageUtil().sendRaw(player, plugin.getMessageUtil().parsePlaceholders(
                    plugin.getMessageUtil().getMessagesConfig().getString("messages.stats-winrate", ""),
                    MessageUtil.Placeholder.of("winrate", String.format("%.1f", stats.getWinRate()))));
                plugin.getMessageUtil().sendRaw(player, plugin.getMessageUtil().parsePlaceholders(
                    plugin.getMessageUtil().getMessagesConfig().getString("messages.stats-streak", ""),
                    MessageUtil.Placeholder.of("streak", String.valueOf(stats.currentStreak))));
                plugin.getMessageUtil().sendRaw(player, plugin.getMessageUtil().parsePlaceholders(
                    plugin.getMessageUtil().getMessagesConfig().getString("messages.stats-best-streak", ""),
                    MessageUtil.Placeholder.of("beststreak", String.valueOf(stats.bestStreak))));
                plugin.getMessageUtil().sendRaw(player, plugin.getMessageUtil().parsePlaceholders(
                    plugin.getMessageUtil().getMessagesConfig().getString("messages.stats-kills", ""),
                    MessageUtil.Placeholder.of("kills", String.valueOf(stats.kills))));
                plugin.getMessageUtil().sendRaw(player, plugin.getMessageUtil().parsePlaceholders(
                    plugin.getMessageUtil().getMessagesConfig().getString("messages.stats-deaths", ""),
                    MessageUtil.Placeholder.of("deaths", String.valueOf(stats.deaths))));
                plugin.getMessageUtil().sendRaw(player, plugin.getMessageUtil().parsePlaceholders(
                    plugin.getMessageUtil().getMessagesConfig().getString("messages.stats-kd", ""),
                    MessageUtil.Placeholder.of("kd", String.format("%.2f", stats.getKDRatio()))));
                plugin.getMessageUtil().sendRaw(player, plugin.getMessageUtil().getMessagesConfig().getString("messages.stats-footer", ""));
            });
        });
    }

    private void handleTop(Player player, Command command, String label, String[] args) {
        String[] newArgs = new String[args.length - 1];
        System.arraycopy(args, 1, newArgs, 0, args.length - 1);
        new DuelTopCommand(plugin).onCommand(player, command, label, newArgs);
    }

    private void sendHelp(Player player) {
        player.sendMessage(plugin.getMessageUtil().colorize("&8&m----------------&r &6&lDUELLI REALISTICI &r&8&m----------------"));
        player.sendMessage(plugin.getMessageUtil().colorize("&e/duello &7- Mostra questo aiuto"));
        player.sendMessage(plugin.getMessageUtil().colorize("&e/duello <giocatore> [kit] &7- Sfida un giocatore"));
        player.sendMessage(plugin.getMessageUtil().colorize("&e/duello accetta [giocatore] &7- Accetta una sfida"));
        player.sendMessage(plugin.getMessageUtil().colorize("&e/duello rifiuta [giocatore] &7- Rifiuta una sfida"));
        player.sendMessage(plugin.getMessageUtil().colorize("&e/duello annulla &7- Annulla la tua sfida"));
        player.sendMessage(plugin.getMessageUtil().colorize("&e/duello kit [info <nome>] &7- Lista kit / info kit"));
        player.sendMessage(plugin.getMessageUtil().colorize("&e/duello stats [giocatore] &7- Statistiche duelli"));
        player.sendMessage(plugin.getMessageUtil().colorize("&e/duello top [tipo] [pagina] &7- Leaderboard"));
        player.sendMessage(plugin.getMessageUtil().colorize("&8&m--------------------------------------------------"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) return List.of();
        
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("sfida", "accetta", "rifiuta", "annulla", "kit", "stats", "top", "help");
            completions.addAll(subCommands.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList());
            
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p != player && p.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(p.getName());
                }
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("sfida") || sub.equals("challenge") || sub.equals("duel")) {
                for (KitManager.Kit kit : plugin.getKitManager().getAllKits().values()) {
                    if (kit.getId().startsWith(args[1].toLowerCase()) && plugin.getKitManager().canUseKit(player, kit.getId())) {
                        completions.add(kit.getId());
                    }
                }
            } else if (sub.equals("accetta") || sub.equals("rifiuta")) {
                var requestOpt = plugin.getDuelManager().getPendingRequest(player);
                requestOpt.ifPresent(req -> {
                    Player challenger = Bukkit.getPlayer(req.getSenderId());
                    if (challenger != null && challenger.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(challenger.getName());
                    }
                });
            } else if (sub.equals("kit")) {
                completions.addAll(Arrays.asList("info"));
            } else if (sub.equals("stats")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(p.getName());
                    }
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("kit") && args[1].equalsIgnoreCase("info")) {
            for (KitManager.Kit kit : plugin.getKitManager().getAllKits().values()) {
                if (kit.getId().startsWith(args[2].toLowerCase())) {
                    completions.add(kit.getId());
                }
            }
        }
        
        return completions;
    }
}