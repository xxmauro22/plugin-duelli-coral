package it.xxmauro.duelli.listeners;

import it.xxmauro.duelli.DuelliPlugin;
import it.xxmauro.duelli.managers.DuelManager;
import it.xxmauro.duelli.objects.Duel;
import it.xxmauro.duelli.objects.DuelEndReason;
import it.xxmauro.duelli.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class DuelListener implements Listener {

    private final DuelliPlugin plugin;
    private final DuelManager duelManager;

    public DuelListener(DuelliPlugin plugin) {
        this.plugin = plugin;
        this.duelManager = plugin.getDuelManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        var duelOpt = duelManager.getDuel(victim);
        if (duelOpt.isEmpty()) return;
        
        Duel duel = duelOpt.get();
        if (duel.isEnded()) return;
        
        event.getDrops().clear();
        event.setDroppedExp(0);
        
        Player killer = victim.getKiller();
        if (killer != null && duel.getOpponent(victim.getUniqueId()).equals(killer.getUniqueId())) {
            if (victim.getUniqueId().equals(duel.getPlayer1())) {
                duel.endDuel(DuelEndReason.PLAYER2_WIN);
            } else {
                duel.endDuel(DuelEndReason.PLAYER1_WIN);
            }
        } else {
            if (victim.getUniqueId().equals(duel.getPlayer1())) {
                duel.endDuel(DuelEndReason.PLAYER2_WIN);
            } else {
                duel.endDuel(DuelEndReason.PLAYER1_WIN);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        var duelOpt = duelManager.getDuel(player);
        if (duelOpt.isEmpty()) return;
        
        Duel duel = duelOpt.get();
        if (!duel.isActive()) {
            event.setCancelled(true);
            return;
        }
        
        if (duel.getStartTime() > System.currentTimeMillis()) {
            event.setCancelled(true);
            return;
        }
        
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            Player opponent = Bukkit.getPlayer(duel.getOpponent(player.getUniqueId()));
            if (opponent != null) {
                duel.endDuel(victimIsPlayer1(duel, player) ? DuelEndReason.PLAYER2_WIN : DuelEndReason.PLAYER1_WIN);
            }
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        
        var duelOpt = duelManager.getDuel(victim);
        if (duelOpt.isEmpty()) return;
        
        Duel duel = duelOpt.get();
        if (!duel.isActive()) {
            event.setCancelled(true);
            return;
        }
        
        if (!duel.getOpponent(attacker.getUniqueId()).equals(victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        
        if (duel.getStartTime() > System.currentTimeMillis()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        var duelOpt = duelManager.getDuel(player);
        if (duelOpt.isEmpty()) return;
        
        Duel duel = duelOpt.get();
        if (!duel.isActive()) return;
        
        if (duelManager.getDuelWorld() != null && player.getWorld().equals(duelManager.getDuelWorld())) {
            int radius = plugin.getConfigManager().getConfig().getInt("duel-world.world-border-radius", 50);
            Location center = duelManager.getDuelWorld().getSpawnLocation();
            
            double dx = player.getLocation().getX() - center.getX();
            double dz = player.getLocation().getZ() - center.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            
            if (dist > radius) {
                Location safeLoc = player.getLocation().clone();
                double factor = radius / dist;
                safeLoc.setX(center.getX() + dx * factor * 0.95);
                safeLoc.setZ(center.getZ() + dz * factor * 0.95);
                player.teleport(safeLoc);
                player.sendActionBar(plugin.getMessageUtil().colorize("&cNon puoi uscire dall'arena!"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        var duelOpt = duelManager.getDuel(player);
        if (duelOpt.isEmpty()) return;
        
        Duel duel = duelOpt.get();
        if (!duel.isActive()) return;
        
        if (!event.getTo().getWorld().equals(duelManager.getDuelWorld())) {
            if (!player.hasPermission("duelli.bypass")) {
                event.setCancelled(true);
                plugin.getMessageUtil().send(player, "duel-world-leave");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        var duelOpt = duelManager.getDuel(player);
        if (duelOpt.isEmpty()) return;
        
        Duel duel = duelOpt.get();
        if (!duel.isActive()) return;
        
        UUID opponentId = duel.getOpponent(player.getUniqueId());
        if (player.getUniqueId().equals(duel.getPlayer1())) {
            duel.endDuel(DuelEndReason.FORFEIT_P1);
        } else {
            duel.endDuel(DuelEndReason.FORFEIT_P2);
        }
        
        Player opponent = Bukkit.getPlayer(opponentId);
        if (opponent != null) {
            plugin.getMessageUtil().send(opponent, "duel-forfeit",
                MessageUtil.Placeholder.of("player", player.getName()),
                MessageUtil.Placeholder.of("winner", opponent.getName()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        var duelOpt = duelManager.getDuel(player);
        if (duelOpt.isEmpty()) return;
        
        Duel duel = duelOpt.get();
        if (!duel.isActive()) {
            event.setCancelled(true);
            return;
        }
        
        if (plugin.getConfigManager().getConfig().getBoolean("duel-world.protect-blocks", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        var duelOpt = duelManager.getDuel(player);
        if (duelOpt.isEmpty()) return;
        
        Duel duel = duelOpt.get();
        if (!duel.isActive()) {
            event.setCancelled(true);
            return;
        }
        
        if (plugin.getConfigManager().getConfig().getBoolean("duel-world.protect-blocks", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        var duelOpt = duelManager.getDuel(player);
        if (duelOpt.isEmpty()) return;
        
        Duel duel = duelOpt.get();
        if (!duel.isActive()) {
            event.setCancelled(true);
            return;
        }
        
        if (plugin.getConfigManager().getConfig().getBoolean("duel-world.prevent-item-drop", true)) {
            ClickType click = event.getClick();
            if (click == ClickType.DROP || click == ClickType.CONTROL_DROP || click == ClickType.NUMBER_KEY) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        var duelOpt = duelManager.getDuel(player);
        if (duelOpt.isEmpty()) return;
        
        Duel duel = duelOpt.get();
        if (!duel.isActive()) return;
        
        if (plugin.getConfigManager().getConfig().getBoolean("duel-world.prevent-item-drop", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        Player player = event.getPlayer();
        var duelOpt = duelManager.getDuel(player);
        if (duelOpt.isEmpty()) return;
        
        Duel duel = duelOpt.get();
        if (!duel.isActive()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        var duelOpt = duelManager.getDuel(player);
        if (duelOpt.isEmpty()) return;
        
        Duel duel = duelOpt.get();
        if (!duel.isActive()) return;
        
        if (plugin.getConfigManager().getConfig().getBoolean("general.disable-commands-during-duel", true)) {
            String cmd = event.getMessage().split(" ")[0].substring(1).toLowerCase();
            
            String[] allowed = {"duello", "duel", "duellostats", "duelstats", "duellotop", "dueltop", "msg", "tell", "r", "reply", "help", "?"};
            for (String a : allowed) {
                if (cmd.equals(a) || cmd.startsWith(a + ":")) return;
            }
            
            for (String blocked : plugin.getConfigManager().getConfig().getStringList("general.blocked-commands")) {
                if (cmd.equals(blocked.toLowerCase()) || cmd.startsWith(blocked.toLowerCase() + ":")) {
                    event.setCancelled(true);
                    plugin.getMessageUtil().send(player, "command-blocked");
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        var duelOpt = duelManager.getDuel(player);
        if (duelOpt.isEmpty()) return;
        
        Duel duel = duelOpt.get();
        if (!duel.isActive()) return;
        
        if (event.getNewGameMode() != GameMode.SURVIVAL) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        var duelOpt = duelManager.getDuel(player);
        if (duelOpt.isEmpty()) return;
        
        Duel duel = duelOpt.get();
        if (!duel.isActive()) return;
        
        event.setRespawnLocation(duelManager.getSpawn1());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        var duelOpt = duelManager.getDuel(player);
        if (duelOpt.isEmpty()) return;
        
        Duel duel = duelOpt.get();
        if (!duel.isActive()) {
            event.setCancelled(true);
            return;
        }
        
        if (duel.getStartTime() > System.currentTimeMillis()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        var duelOpt = duelManager.getDuel(player);
        if (duelOpt.isEmpty()) return;
        
        Duel duel = duelOpt.get();
        if (!duel.isActive()) return;
        
        if (event.getFrom().equals(duelManager.getDuelWorld()) && !player.hasPermission("duelli.bypass")) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && !player.getWorld().equals(duelManager.getDuelWorld())) {
                    player.teleport(duelManager.getSpawn1());
                    plugin.getMessageUtil().send(player, "duel-world-leave");
                }
            });
        }
    }

    private boolean victimIsPlayer1(Duel duel, Player victim) {
        return victim.getUniqueId().equals(duel.getPlayer1());
    }
}
