package it.xxmauro.duelli.utils;

import it.xxmauro.duelli.DuelliPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageUtil {

    private final DuelliPlugin plugin;
    private String prefix;
    private final Pattern placeholderPattern = Pattern.compile("%(\\w+)%");

    public MessageUtil(DuelliPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration config = plugin.getConfigManager().getMessagesConfig();
        this.prefix = colorize(config.getString("messages.prefix", "&8[&bDuelli&8] &r"));
    }

    public String getPrefix() {
        return prefix;
    }

    public FileConfiguration getMessagesConfig() {
        return plugin.getConfigManager().getMessagesConfig();
    }

    public String colorize(String message) {
        if (message == null) return "";
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public String parsePlaceholders(String message, Placeholder... placeholders) {
        if (message == null) return "";
        String result = message;
        for (Placeholder ph : placeholders) {
            result = result.replace("%" + ph.key + "%", ph.value);
        }
        return colorize(result);
    }

    public void send(CommandSender sender, String path, Placeholder... placeholders) {
        FileConfiguration config = plugin.getConfigManager().getMessagesConfig();
        String message = config.getString("messages." + path);
        if (message == null) {
            message = "&cMessaggio non trovato: " + path;
        }
        sender.sendMessage(parsePlaceholders(message, placeholders));
    }

    public void sendRaw(CommandSender sender, String rawMessage, Placeholder... placeholders) {
        sender.sendMessage(parsePlaceholders(rawMessage, placeholders));
    }

    public void broadcast(String path, Placeholder... placeholders) {
        FileConfiguration config = plugin.getConfigManager().getMessagesConfig();
        String message = config.getString("messages." + path);
        if (message != null) {
            Bukkit.broadcastMessage(parsePlaceholders(message, placeholders));
        }
    }

    public void sendActionBar(Player player, String path, Placeholder... placeholders) {
        FileConfiguration config = plugin.getConfigManager().getMessagesConfig();
        String message = config.getString("messages." + path);
        if (message != null) {
            player.sendActionBar(parsePlaceholders(message, placeholders));
        }
    }

    public void sendTitle(Player player, String titlePath, String subtitlePath, Placeholder... placeholders) {
        FileConfiguration config = plugin.getConfigManager().getMessagesConfig();
        String title = config.getString("messages." + titlePath, "");
        String subtitle = config.getString("messages." + subtitlePath, "");
        player.sendTitle(
            parsePlaceholders(title, placeholders),
            parsePlaceholders(subtitle, placeholders),
            10, 70, 20
        );
    }

    public List<String> getList(String path, Placeholder... placeholders) {
        FileConfiguration config = plugin.getConfigManager().getMessagesConfig();
        List<String> list = config.getStringList("messages." + path);
        return list.stream()
            .map(line -> parsePlaceholders(line, placeholders))
            .toList();
    }

    public record Placeholder(String key, String value) {
        public static Placeholder of(String key, String value) {
            return new Placeholder(key, value);
        }
        public static Placeholder of(String key, Object value) {
            return new Placeholder(key, String.valueOf(value));
        }
    }
}
