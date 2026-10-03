package it.coralmc.duelli.commands;

import it.coralmc.duelli.DuelliPlugin;
import it.coralmc.duelli.objects.DuelEndReason;
import it.coralmc.duelli.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class DuelAdminCommand implements CommandExecutor, TabCompleter {

    private final DuelliPlugin plugin;

    public DuelAdminCommand(DuelliPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("duelli.admin")) {
            plugin.getMessageUtil().send(sender, "no-permission");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "reload", "rl" -> handleReload(sender);
            case "createworld", "create", "cw" -> handleCreateWorld(sender);
            case "setspawn", "spawn", "ss" -> handleSetSpawn(sender, args);
            case "forcestop", "stop", "fs" -> handleForceStop(sender, args);
            case "list", "l" -> handleList(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleReload(CommandSender sender) {
        plugin.getConfigManager().reloadConfigs();
        plugin.getKitManager().loadKits();
        plugin.getMessageUtil().reload();
        plugin.getMessageUtil().send(sender, "admin-reload");
    }

    private void handleCreateWorld(CommandSender sender) {
        if (plugin.getDuelManager().getDuelWorld() != null) {
            plugin.getMessageUtil().send(sender, "admin-world-exists");
            return;
        }
        plugin.getDuelManager().initializeDuelWorld();
        if (plugin.getDuelManager().getDuelWorld() != null) {
            plugin.getMessageUtil().send(sender, "admin-world-created");
        }
    }

    private void handleSetSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessageUtil().colorize("&cSolo i giocatori possono impostare gli spawn."));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(plugin.getMessageUtil().colorize("&cUso: /duelloadmin setspawn <1|2>"));
            return;
        }

        int num;
        try {
            num = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getMessageUtil().colorize("&cNumero spawn non valido. Usa 1 o 2."));
            return;
        }

        if (num != 1 && num != 2) {
            sender.sendMessage(plugin.getMessageUtil().colorize("&cNumero spawn non valido. Usa 1 o 2."));
            return;
        }

        Location loc = player.getLocation();
        plugin.getDuelManager().setSpawn(num, loc);
        plugin.getMessageUtil().send(sender, "admin-spawn-set",
            MessageUtil.Placeholder.of("num", String.valueOf(num)));
    }

    private void handleForceStop(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(plugin.getMessageUtil().colorize("&cUso: /duelloadmin forcestop <giocatore1> <giocatore2>"));
            return;
        }

        Player p1 = Bukkit.getPlayerExact(args[1]);
        Player p2 = Bukkit.getPlayerExact(args[2]);

        if (p1 == null || p2 == null) {
            plugin.getMessageUtil().send(sender, "player-not-found");
            return;
        }

        var duelOpt = plugin.getDuelManager().getDuel(p1);
        if (duelOpt.isEmpty() || !duelOpt.get().getPlayer2().equals(p2.getUniqueId())) {
            plugin.getMessageUtil().send(sender, "admin-no-active-duel");
            return;
        }

        duelOpt.get().forceEnd(DuelEndReason.ADMIN);
        plugin.getMessageUtil().send(sender, "admin-duel-stopped",
            MessageUtil.Placeholder.of("p1", p1.getName()),
            MessageUtil.Placeholder.of("p2", p2.getName()));
    }

    private void handleList(CommandSender sender) {
        sender.sendMessage(plugin.getMessageUtil().colorize("&8&m----------------&r &6&lDUELLI ATTIVI &r&8&m----------------"));
        int count = 0;
        for (var entry : plugin.getDuelManager().getActiveDuels().entrySet()) {
            if (entry.getValue().getPlayer1().equals(entry.getKey())) {
                Player p1 = Bukkit.getPlayer(entry.getValue().getPlayer1());
                Player p2 = Bukkit.getPlayer(entry.getValue().getPlayer2());
                if (p1 != null && p2 != null) {
                    sender.sendMessage(plugin.getMessageUtil().colorize(
                        "&e" + p1.getName() + " &8vs &e" + p2.getName() + 
                        " &7- Kit: &b" + entry.getValue().getKitName() +
                        " &7- Durata: &e" + entry.getValue().getDuration() + "s"
                    ));
                    count++;
                }
            }
        }
        if (count == 0) {
            sender.sendMessage(plugin.getMessageUtil().colorize("&7Nessun duello attivo."));
        }
        sender.sendMessage(plugin.getMessageUtil().colorize("&8&m--------------------------------------------------"));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(plugin.getMessageUtil().colorize("&8&m----------------&r &6&lADMIN DUELLI &r&8&m----------------"));
        sender.sendMessage(plugin.getMessageUtil().colorize("&e/duelloadmin reload &7- Ricarica configurazioni"));
        sender.sendMessage(plugin.getMessageUtil().colorize("&e/duelloadmin createworld &7- Crea mondo duello"));
        sender.sendMessage(plugin.getMessageUtil().colorize("&e/duelloadmin setspawn <1|2> &7- Imposta spawn (alla tua posizione)"));
        sender.sendMessage(plugin.getMessageUtil().colorize("&e/duelloadmin forcestop <p1> <p2> &7- Forza fine duello"));
        sender.sendMessage(plugin.getMessageUtil().colorize("&e/duelloadmin list &7- Lista duelli attivi"));
        sender.sendMessage(plugin.getMessageUtil().colorize("&8&m--------------------------------------------------"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("duelli.admin")) return List.of();
        
        if (args.length == 1) {
            return Arrays.asList("reload", "createworld", "setspawn", "forcestop", "list")
                .stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        
        if (args.length == 2 && args[0].equalsIgnoreCase("setspawn")) {
            return Arrays.asList("1", "2").stream().filter(s -> s.startsWith(args[1])).toList();
        }
        
        if (args.length == 2 && args[0].equalsIgnoreCase("forcestop")) {
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                .toList();
        }
        
        return List.of();
    }
}