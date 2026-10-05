package it.xxmauro.duelli.objects;

import it.xxmauro.duelli.DuelliPlugin;
import it.xxmauro.duelli.managers.DuelManager;
import it.xxmauro.duelli.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

public class Duel {

    private final UUID player1;
    private final UUID player2;
    private final String kitName;
    private final DuelliPlugin plugin;
    private final DuelManager duelManager;
    
    private long startTime;
    private int countdown;
    private int maxDuration;
    private BukkitTask countdownTask;
    private BukkitTask durationTask;
    private boolean active = false;
    private boolean ended = false;

    public Duel(UUID player1, UUID player2, String kitName, DuelliPlugin plugin) {
        this.player1 = player1;
        this.player2 = player2;
        this.kitName = kitName;
        this.plugin = plugin;
        this.duelManager = plugin.getDuelManager();
        this.countdown = plugin.getConfigManager().getConfig().getInt("general.challenge-timeout", 5);
        this.maxDuration = plugin.getConfigManager().getConfig().getInt("general.max-duel-duration", 300);
    }

    public void startCountdown() {
        active = true;
        startTime = System.currentTimeMillis();
        
        Player p1 = Bukkit.getPlayer(player1);
        Player p2 = Bukkit.getPlayer(player2);
        
        if (p1 == null || p2 == null) {
            forceEnd(DuelEndReason.ADMIN);
            return;
        }
        
        sendBoth("duel-starting", 
            MessageUtil.Placeholder.of("seconds", String.valueOf(countdown)));
        
        countdownTask = new BukkitRunnable() {
            int remaining = countdown;
            
            @Override
            public void run() {
                if (ended) {
                    cancel();
                    return;
                }
                
                Player cp1 = Bukkit.getPlayer(player1);
                Player cp2 = Bukkit.getPlayer(player2);
                
                if (cp1 == null || cp2 == null || !cp1.isOnline() || !cp2.isOnline()) {
                    forceEnd(DuelEndReason.ADMIN);
                    cancel();
                    return;
                }
                
                if (remaining <= 0) {
                    startDuel();
                    cancel();
                    return;
                }
                
                sendBoth("duel-starting", 
                    MessageUtil.Placeholder.of("seconds", String.valueOf(remaining)));
                
                if (plugin.getConfigManager().getConfig().getBoolean("general.sounds", true)) {
                    cp1.playSound(cp1.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                    cp2.playSound(cp2.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                }
                
                remaining--;
            }
        }.runTaskTimer(plugin, 0, 20);
        
        if (maxDuration > 0) {
            durationTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!ended) {
                        endDuel(DuelEndReason.TIMEOUT);
                    }
                }
            }.runTaskLater(plugin, (countdown + maxDuration) * 20L);
        }
    }

    private void startDuel() {
        Player p1 = Bukkit.getPlayer(player1);
        Player p2 = Bukkit.getPlayer(player2);
        
        if (p1 == null || p2 == null) {
            forceEnd(DuelEndReason.ADMIN);
            return;
        }
        
        sendBoth("duel-start");
        
        if (plugin.getConfigManager().getConfig().getBoolean("general.sounds", true)) {
            p1.playSound(p1.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
            p2.playSound(p2.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
        }
    }

    public void endDuel(DuelEndReason reason) {
        if (ended) return;
        ended = true;
        
        cleanupTasks();
        duelManager.endDuel(this, reason);
    }

    public void forceEnd(DuelEndReason reason) {
        if (ended) return;
        ended = true;
        
        cleanupTasks();
        duelManager.endDuel(this, reason);
    }

    private void cleanupTasks() {
        if (countdownTask != null) countdownTask.cancel();
        if (durationTask != null) durationTask.cancel();
    }

    private void sendBoth(String messagePath, MessageUtil.Placeholder... placeholders) {
        Player p1 = Bukkit.getPlayer(player1);
        Player p2 = Bukkit.getPlayer(player2);
        
        if (p1 != null) plugin.getMessageUtil().send(p1, messagePath, placeholders);
        if (p2 != null) plugin.getMessageUtil().send(p2, messagePath, placeholders);
    }

    public UUID getPlayer1() { return player1; }
    public UUID getPlayer2() { return player2; }
    public String getKitName() { return kitName; }
    public long getStartTime() { return startTime; }
    public int getDuration() { return (int) ((System.currentTimeMillis() - startTime) / 1000); }
    public boolean isActive() { return active && !ended; }
    public boolean isEnded() { return ended; }
    
    public Player getPlayer1Online() { return Bukkit.getPlayer(player1); }
    public Player getPlayer2Online() { return Bukkit.getPlayer(player2); }
    
    public UUID getOpponent(UUID player) {
        if (player.equals(player1)) return player2;
        if (player.equals(player2)) return player1;
        return null;
    }
}
