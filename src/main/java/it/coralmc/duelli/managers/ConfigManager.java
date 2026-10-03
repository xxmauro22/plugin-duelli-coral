package it.coralmc.duelli.managers;

import it.coralmc.duelli.DuelliPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

public class ConfigManager {

    private final DuelliPlugin plugin;
    
    private FileConfiguration mainConfig;
    private FileConfiguration kitsConfig;
    private FileConfiguration messagesConfig;
    
    private File mainConfigFile;
    private File kitsConfigFile;

    public ConfigManager(DuelliPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfigs() {
        mainConfigFile = new File(plugin.getDataFolder(), "config.yml");
        if (!mainConfigFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        mainConfig = YamlConfiguration.loadConfiguration(mainConfigFile);
        
        kitsConfigFile = new File(plugin.getDataFolder(), "kits.yml");
        if (!kitsConfigFile.exists()) {
            plugin.saveResource("kits.yml", false);
        }
        kitsConfig = YamlConfiguration.loadConfiguration(kitsConfigFile);
        
        messagesConfig = mainConfig;
        
        plugin.getLogger().info("Configurazioni caricate.");
    }

    public void reloadConfigs() {
        mainConfig = YamlConfiguration.loadConfiguration(mainConfigFile);
        kitsConfig = YamlConfiguration.loadConfiguration(kitsConfigFile);
        messagesConfig = mainConfig;
        plugin.getLogger().info("Configurazioni ricaricate.");
    }

    public void saveMainConfig() {
        try {
            mainConfig.save(mainConfigFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Impossibile salvare config.yml", e);
        }
    }

    public void saveKitsConfig() {
        try {
            kitsConfig.save(kitsConfigFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Impossibile salvare kits.yml", e);
        }
    }

    public FileConfiguration getConfig() { return mainConfig; }
    public FileConfiguration getKitsConfig() { return kitsConfig; }
    public FileConfiguration getMessagesConfig() { return messagesConfig; }
    
    public File getMainConfigFile() { return mainConfigFile; }
    public File getKitsConfigFile() { return kitsConfigFile; }

    public String getString(String path, String def) {
        return mainConfig.getString(path, def);
    }
    
    public int getInt(String path, int def) {
        return mainConfig.getInt(path, def);
    }
    
    public double getDouble(String path, double def) {
        return mainConfig.getDouble(path, def);
    }
    
    public boolean getBoolean(String path, boolean def) {
        return mainConfig.getBoolean(path, def);
    }
    
    public long getLong(String path, long def) {
        return mainConfig.getLong(path, def);
    }
}