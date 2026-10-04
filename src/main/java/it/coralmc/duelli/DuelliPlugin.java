package it.coralmc.duelli;

import it.coralmc.duelli.commands.DuelCommand;
import it.coralmc.duelli.commands.DuelAdminCommand;
import it.coralmc.duelli.commands.DuelStatsCommand;
import it.coralmc.duelli.commands.DuelTopCommand;
import it.coralmc.duelli.commands.DuelKitCommand;
import it.coralmc.duelli.listeners.DuelListener;
import it.coralmc.duelli.managers.ConfigManager;
import it.coralmc.duelli.managers.DuelManager;
import it.coralmc.duelli.managers.KitManager;
import it.coralmc.duelli.managers.MySQLManager;
import it.coralmc.duelli.managers.RewardManager;
import it.coralmc.duelli.managers.StatsManager;
import it.coralmc.duelli.managers.LeaderboardManager;
import it.coralmc.duelli.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class DuelliPlugin extends JavaPlugin {

    private static DuelliPlugin instance;
    
    private ConfigManager configManager;
    private KitManager kitManager;
    private MySQLManager mysqlManager;
    private StatsManager statsManager;
    private LeaderboardManager leaderboardManager;
    private DuelManager duelManager;
    private RewardManager rewardManager;
    
    private MessageUtil messageUtil;

    @Override
    public void onEnable() {
        instance = this;
        
        configManager = new ConfigManager(this);
        configManager.loadConfigs();
        
        messageUtil = new MessageUtil(this);
        
        mysqlManager = new MySQLManager(this);
        if (configManager.getConfig().getBoolean("mysql.enabled", true)) {
            mysqlManager.connect();
        }
        
        kitManager = new KitManager(this);
        kitManager.loadKits();
        
        statsManager = new StatsManager(this);
        leaderboardManager = new LeaderboardManager(this);
        rewardManager = new RewardManager(this);
        duelManager = new DuelManager(this);
        
        registerCommands();
        
        getServer().getPluginManager().registerEvents(new DuelListener(this), this);
        
        startPeriodicTasks();
        
        if (configManager.getConfig().getBoolean("duel-world.auto-generate", true)) {
            duelManager.initializeDuelWorld();
        }
        
        getLogger().info("DuelliRealistici caricato! Versione " + getDescription().getVersion());
    }

    @Override
    public void onDisable() {
        if (duelManager != null) {
            duelManager.stopAllDuels();
        }
        
        if (mysqlManager != null) {
            mysqlManager.close();
        }
        
        stopPeriodicTasks();
        
        getLogger().info("DuelliRealistici disabilitato.");
    }

    private void registerCommands() {
        DuelCommand duelCommand = new DuelCommand(this);
        getCommand("duello").setExecutor(duelCommand);
        getCommand("duello").setTabCompleter(duelCommand);
        
        DuelAdminCommand adminCommand = new DuelAdminCommand(this);
        getCommand("duelloadmin").setExecutor(adminCommand);
        getCommand("duelloadmin").setTabCompleter(adminCommand);
        
        DuelStatsCommand statsCommand = new DuelStatsCommand(this);
        getCommand("duellostats").setExecutor(statsCommand);
        getCommand("duellostats").setTabCompleter(statsCommand);
        
        DuelTopCommand topCommand = new DuelTopCommand(this);
        getCommand("duellotop").setExecutor(topCommand);
        getCommand("duellotop").setTabCompleter(topCommand);
        
        DuelKitCommand kitCommand = new DuelKitCommand(this);
        getCommand("duellokit").setExecutor(kitCommand);
        getCommand("duellokit").setTabCompleter(kitCommand);
    }

    private void startPeriodicTasks() {
        int cacheMinutes = configManager.getConfig().getInt("leaderboard.cache-update-minutes", 5);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (leaderboardManager != null) {
                    leaderboardManager.refreshCache();
                }
            }
        }.runTaskTimerAsynchronously(this, cacheMinutes * 60 * 20L, cacheMinutes * 60 * 20L);
        
        new BukkitRunnable() {
            @Override
            public void run() {
                if (duelManager != null) {
                    duelManager.cleanupStaleDuels();
                }
            }
        }.runTaskTimer(this, 20 * 60, 20 * 60);
    }

    private void stopPeriodicTasks() {
        Bukkit.getScheduler().cancelTasks(this);
    }

    public static DuelliPlugin getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() { return configManager; }
    public KitManager getKitManager() { return kitManager; }
    public MySQLManager getMySQLManager() { return mysqlManager; }
    public StatsManager getStatsManager() { return statsManager; }
    public LeaderboardManager getLeaderboardManager() { return leaderboardManager; }
    public DuelManager getDuelManager() { return duelManager; }
    public RewardManager getRewardManager() { return rewardManager; }
    public MessageUtil getMessageUtil() { return messageUtil; }
}