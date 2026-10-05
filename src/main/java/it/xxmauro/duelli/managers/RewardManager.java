package it.xxmauro.duelli.managers;

import it.xxmauro.duelli.DuelliPlugin;
import it.xxmauro.duelli.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Level;

public class RewardManager {

    private final DuelliPlugin plugin;
    private Object economy;
    private Method depositPlayerMethod;

    public RewardManager(DuelliPlugin plugin) {
        this.plugin = plugin;
        setupEconomy();
    }

    private void setupEconomy() {
        try {
            if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
                Class<?> servicesManagerClass = Bukkit.getServicesManager().getClass();
                Method getRegistrationMethod = servicesManagerClass.getMethod("getRegistration", Class.class);
                
                Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
                Object registration = getRegistrationMethod.invoke(Bukkit.getServicesManager(), economyClass);
                
                if (registration != null) {
                    Method getProviderMethod = registration.getClass().getMethod("getProvider");
                    economy = getProviderMethod.invoke(registration);
                    depositPlayerMethod = economyClass.getMethod("depositPlayer", org.bukkit.OfflinePlayer.class, double.class);
                    plugin.getLogger().info("Vault Economy collegato (via reflection).");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Impossibile collegare Vault Economy: " + e.getMessage());
            economy = null;
            depositPlayerMethod = null;
        }
    }

    public void giveRewards(Player winner, Player loser, String kitName, boolean isDraw) {
        if (!plugin.getConfigManager().getConfig().getBoolean("rewards.enabled", true)) return;
        
        FileConfiguration config = plugin.getConfigManager().getConfig();
        ConfigurationSection rewardsSection = isDraw ? config.getConfigurationSection("rewards.draw") 
            : config.getConfigurationSection("rewards.winner");
        
        if (rewardsSection == null) return;
        
        String winnerName = winner.getName();
        String loserName = loser.getName();
        
        double money = rewardsSection.getDouble("money", 0);
        if (money > 0 && economy != null && depositPlayerMethod != null) {
            try {
                depositPlayerMethod.invoke(economy, winner, money);
                String moneyMsg = plugin.getMessageUtil().parsePlaceholders(
                    plugin.getConfigManager().getMessagesConfig().getString("messages.reward-money", "&e$%amount%"),
                    MessageUtil.Placeholder.of("amount", String.format("%.2f", money))
                );
                plugin.getMessageUtil().send(winner, "reward-received",
                    MessageUtil.Placeholder.of("rewards", moneyMsg));
            } catch (Exception e) {
                plugin.getLogger().warning("Errore deposito denaro: " + e.getMessage());
            }
        }
        
        List<String> commands = rewardsSection.getStringList("commands");
        for (String cmd : commands) {
            String parsed = parseCommand(cmd, winnerName, loserName, kitName);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }
        
        List<String> items = rewardsSection.getStringList("items");
        for (String itemStr : items) {
            ItemStack item = parseItem(itemStr);
            if (item != null) {
                winner.getInventory().addItem(item);
            }
        }
    }

    private String parseCommand(String cmd, String winner, String loser, String kit) {
        return cmd
            .replace("%winner%", winner)
            .replace("%loser%", loser)
            .replace("%kit%", kit);
    }

    private ItemStack parseItem(String itemStr) {
        String[] parts = itemStr.split(":");
        String materialName = parts[0].toUpperCase();
        int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
        
        Material material = Material.getMaterial(materialName);
        if (material == null) return null;
        
        return new ItemStack(material, Math.min(amount, material.getMaxStackSize()));
    }

    public boolean hasEconomy() {
        return economy != null && depositPlayerMethod != null;
    }
}
