package it.xxmauro.duelli.commands;

import it.xxmauro.duelli.DuelliPlugin;
import it.xxmauro.duelli.managers.KitManager;
import it.xxmauro.duelli.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.stream.Collectors;

public class DuelKitCommand implements CommandExecutor, TabCompleter {

    private final DuelliPlugin plugin;

    public DuelKitCommand(DuelliPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessageUtil().colorize("&cSolo i giocatori possono usare questo comando."));
            return true;
        }

        if (!player.hasPermission("duelli.kit")) {
            plugin.getMessageUtil().send(player, "no-permission");
            return true;
        }

        if (args.length == 0) {
            showKits(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "list", "l" -> showKits(player);
            case "info", "i" -> {
                if (args.length < 2) {
                    plugin.getMessageUtil().send(player, "error-no-kit");
                    return true;
                }
                showKitInfo(player, args[1]);
            }
            case "give", "g" -> {
                if (!player.hasPermission("duelli.admin")) {
                    plugin.getMessageUtil().send(player, "no-permission");
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage(plugin.getMessageUtil().colorize("&cUso: /duellokit give <giocatore> <kit>"));
                    return true;
                }
                giveKit(player, args[1], args[2]);
            }
            default -> showKits(player);
        }
        return true;
    }

    private void showKits(Player player) {
        player.sendMessage(plugin.getMessageUtil().colorize("&8&m----------------&r &6&lKIT DISPONIBILI &r&8&m----------------"));
        for (KitManager.Kit kit : plugin.getKitManager().getAllKits().values()) {
            boolean canUse = plugin.getKitManager().canUseKit(player, kit.getId());
            String color = canUse ? "&a" : "&c";
            String perm = canUse ? "" : " &8(&cPermesso mancante&8)";
            player.sendMessage(plugin.getMessageUtil().colorize(
                color + kit.getName() + perm + " &8- &7" + 
                (kit.getDescription().isEmpty() ? "Nessuna descrizione" : kit.getDescription().get(0))
            ));
        }
        player.sendMessage(plugin.getMessageUtil().colorize("&8&m--------------------------------------------------"));
        player.sendMessage(plugin.getMessageUtil().colorize("&7Usa &e/duellokit info <nome> &7per dettagli."));
    }

    private void showKitInfo(Player player, String kitName) {
        KitManager.Kit kit = plugin.getKitManager().getKit(kitName);
        if (kit == null) {
            plugin.getMessageUtil().send(player, "kit-not-found",
                MessageUtil.Placeholder.of("kit", kitName));
            return;
        }

        player.sendMessage(plugin.getMessageUtil().colorize("&8&m----------------&r &6&lINFO KIT: " + kit.getName() + " &r&8&m----------------"));
        player.sendMessage(plugin.getMessageUtil().colorize("&eID: &f" + kit.getId()));
        player.sendMessage(plugin.getMessageUtil().colorize("&ePermesso: &f" + kit.getPermission()));
        player.sendMessage(plugin.getMessageUtil().colorize("&7Descrizione:"));
        for (String line : kit.getDescription()) {
            player.sendMessage(plugin.getMessageUtil().colorize("&7  " + line));
        }
        
        player.sendMessage(plugin.getMessageUtil().colorize("&eOggetti:"));
        for (var entry : kit.getItems().entrySet()) {
            ItemStack item = entry.getValue();
            player.sendMessage(plugin.getMessageUtil().colorize(
                "  &7Slot " + entry.getKey() + ": &f" + item.getType().name() + 
                (item.hasItemMeta() && item.getItemMeta().hasDisplayName() ? " &8(&f" + item.getItemMeta().getDisplayName() + "&8)" : "") +
                " x" + item.getAmount()));
        }
        
        player.sendMessage(plugin.getMessageUtil().colorize("&eArmatura:"));
        KitManager.ArmorSet armor = kit.getArmor();
        if (armor.helmet() != null) player.sendMessage(plugin.getMessageUtil().colorize("  &7Elmo: &f" + armor.helmet().getType().name()));
        if (armor.chestplate() != null) player.sendMessage(plugin.getMessageUtil().colorize("  &7Corazza: &f" + armor.chestplate().getType().name()));
        if (armor.leggings() != null) player.sendMessage(plugin.getMessageUtil().colorize("  &7Gambali: &f" + armor.leggings().getType().name()));
        if (armor.boots() != null) player.sendMessage(plugin.getMessageUtil().colorize("  &7Stivali: &f" + armor.boots().getType().name()));
        
        if (!kit.getEffects().isEmpty()) {
            player.sendMessage(plugin.getMessageUtil().colorize("&eEffetti:"));
            for (var effect : kit.getEffects()) {
                player.sendMessage(plugin.getMessageUtil().colorize(
                    "  &7" + effect.getType().getName() + " " + (effect.getAmplifier() + 1) + " &8(" + effect.getDuration() / 20 + "s)"));
            }
        }
        
        player.sendMessage(plugin.getMessageUtil().colorize("&8&m--------------------------------------------------"));
    }

    private void giveKit(Player sender, String targetName, String kitName) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            plugin.getMessageUtil().send(sender, "player-not-found",
                MessageUtil.Placeholder.of("player", targetName));
            return;
        }

        KitManager.Kit kit = plugin.getKitManager().getKit(kitName);
        if (kit == null) {
            plugin.getMessageUtil().send(sender, "kit-not-found",
                MessageUtil.Placeholder.of("kit", kitName));
            return;
        }

        plugin.getKitManager().applyKit(target, kit);
        plugin.getMessageUtil().send(sender, "kit-set",
            MessageUtil.Placeholder.of("kit", kit.getName()),
            MessageUtil.Placeholder.of("target", target.getName()));
        plugin.getMessageUtil().send(target, "kit-set",
            MessageUtil.Placeholder.of("kit", kit.getName()),
            MessageUtil.Placeholder.of("target", "te stesso"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("list", "info", "give").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .toList();
        }
        
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("info") || sub.equals("give")) {
                return plugin.getKitManager().getAllKits().keySet().stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .toList();
            }
        }
        
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                .toList();
        }
        
        return List.of();
    }
}
