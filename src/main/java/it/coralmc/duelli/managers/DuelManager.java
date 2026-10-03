package it.coralmc.duelli.managers;

import it.coralmc.duelli.DuelliPlugin;
import it.coralmc.duelli.objects.Duel;
import it.coralmc.duelli.objects.DuelEndReason;
import it.coralmc.duelli.objects.DuelRequest;
import it.coralmc.duelli.objects.SavedInventory;
import it.coralmc.duelli.managers.KitManager.Kit;
import it.coralmc.duelli.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DuelManager {

    private final DuelliPlugin plugin;
    
    private final Map<UUID, Duel> activeDuels = new ConcurrentHashMap<>();
    private final Map<UUID, DuelRequest> pendingRequests = new ConcurrentHashMap<>();
    private final Map<UUID, SavedInventory> savedInventories = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    
    private World duelWorld;
    private Location spawn1;
    private Location spawn2;

    public DuelManager(DuelliPlugin plugin) {
        this.plugin = plugin;
    }

    public void initializeDuelWorld() {
        String worldName = plugin.getConfigManager().getConfig().getString("general.duel-world", "duelli_world");
        
        duelWorld = Bukkit.getWorld(worldName);
        if (duelWorld != null) {
            loadSpawns();
            plugin.getLogger().info("Mondo duello '" + worldName + "' caricato.");
            return;
        }

        if (plugin.getConfigManager().getConfig().getBoolean("duel-world.auto-generate", true)) {
            createDuelWorld(worldName);
        }
    }

    private void createDuelWorld(String worldName) {
        String worldTypeStr = plugin.getConfigManager().getConfig().getString("duel-world.world-type", "FLAT");
        WorldType worldType = WorldType.valueOf(worldTypeStr.toUpperCase());
        
        WorldCreator creator = new WorldCreator(worldName);
        creator.type(worldType);
        creator.generateStructures(false);
        
        duelWorld = Bukkit.createWorld(creator);
        if (duelWorld == null) {
            plugin.getLogger().severe("Impossibile creare il mondo duello!");
            return;
        }

        duelWorld.setAutoSave(false);
        duelWorld.setKeepSpawnInMemory(true);
        duelWorld.setDifficulty(org.bukkit.Difficulty.PEACEFUL);
        duelWorld.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
        duelWorld.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
        duelWorld.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);
        duelWorld.setGameRule(org.bukkit.GameRule.DO_FIRE_TICK, false);
        duelWorld.setTime(6000);
        
        setupDefaultSpawns();
        
        plugin.getLogger().info("Mondo duello '" + worldName + "' creato!");
    }

    private void setupDefaultSpawns() {
        if (duelWorld == null) return;
        
        Location defaultSpawn = duelWorld.getSpawnLocation();
        
        FileConfiguration config = plugin.getConfigManager().getConfig();
        ConfigurationSection spawn1Sec = config.getConfigurationSection("duel-world.spawn-1");
        ConfigurationSection spawn2Sec = config.getConfigurationSection("duel-world.spawn-2");
        
        if (spawn1Sec != null) {
            spawn1 = new Location(duelWorld,
                spawn1Sec.getDouble("x", defaultSpawn.getX() + 10),
                spawn1Sec.getDouble("y", defaultSpawn.getY()),
                spawn1Sec.getDouble("z", defaultSpawn.getZ() + 10),
                (float) spawn1Sec.getDouble("yaw", 180),
                (float) spawn1Sec.getDouble("pitch", 0));
        } else {
            spawn1 = defaultSpawn.clone().add(10, 0, 10);
            spawn1.setYaw(180);
        }
        
        if (spawn2Sec != null) {
            spawn2 = new Location(duelWorld,
                spawn2Sec.getDouble("x", defaultSpawn.getX() - 10),
                spawn2Sec.getDouble("y", defaultSpawn.getY()),
                spawn2Sec.getDouble("z", defaultSpawn.getZ() - 10),
                (float) spawn2Sec.getDouble("yaw", 0),
                (float) spawn2Sec.getDouble("pitch", 0));
        } else {
            spawn2 = defaultSpawn.clone().add(-10, 0, -10);
            spawn2.setYaw(0);
        }
    }

    private void loadSpawns() {
        if (duelWorld == null) return;
        setupDefaultSpawns();
    }

    public void setSpawn(int num, Location loc) {
        if (duelWorld == null) return;
        
        FileConfiguration config = plugin.getConfigManager().getConfig();
        String path = "duel-world.spawn-" + num;
        
        config.set(path + ".x", loc.getX());
        config.set(path + ".y", loc.getY());
        config.set(path + ".z", loc.getZ());
        config.set(path + ".yaw", loc.getYaw());
        config.set(path + ".pitch", loc.getPitch());
        
        plugin.getConfigManager().saveMainConfig();
        loadSpawns();
    }

    public boolean sendRequest(Player sender, Player target, String kitName) {
        if (sender.equals(target)) return false;
        
        if (isOnCooldown(sender)) return false;
        
        if (isInDuel(sender) || isInDuel(target)) return false;
        
        Kit kit = plugin.getKitManager().getKit(kitName);
        if (kit == null) kit = plugin.getKitManager().getDefaultKit();
        if (kit == null) return false;
        
        if (!plugin.getKitManager().canUseKit(sender, kit.getId()) || 
            !plugin.getKitManager().canUseKit(target, kit.getId())) {
            return false;
        }

        DuelRequest request = new DuelRequest(sender.getUniqueId(), target.getUniqueId(), kit.getId());
        pendingRequests.put(target.getUniqueId(), request);
        
        int timeout = plugin.getConfigManager().getConfig().getInt("general.challenge-timeout", 30);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pendingRequests.remove(target.getUniqueId(), request)) {
                plugin.getMessageUtil().send(sender, "challenge-expired", 
                    MessageUtil.Placeholder.of("player", target.getName()));
                plugin.getMessageUtil().send(target, "challenge-expired-target",
                    MessageUtil.Placeholder.of("player", sender.getName()));
            }
        }, timeout * 20L);
        
        return true;
    }

    public Optional<DuelRequest> getPendingRequest(Player target) {
        return Optional.ofNullable(pendingRequests.get(target.getUniqueId()));
    }

    public boolean acceptRequest(Player target) {
        DuelRequest request = pendingRequests.remove(target.getUniqueId());
        if (request == null) return false;
        
        Player sender = Bukkit.getPlayer(request.getSenderId());
        if (sender == null || !sender.isOnline()) {
            plugin.getMessageUtil().send(target, "player-offline",
                MessageUtil.Placeholder.of("player", sender != null ? sender.getName() : "Sconosciuto"));
            return false;
        }
        
        if (isInDuel(sender) || isInDuel(target)) return false;
        
        Kit kit = plugin.getKitManager().getKit(request.getKitId());
        if (kit == null) kit = plugin.getKitManager().getDefaultKit();
        
        startDuel(sender, target, kit);
        return true;
    }

    public boolean denyRequest(Player target) {
        DuelRequest request = pendingRequests.remove(target.getUniqueId());
        if (request == null) return false;
        
        Player sender = Bukkit.getPlayer(request.getSenderId());
        if (sender != null && sender.isOnline()) {
            plugin.getMessageUtil().send(sender, "challenge-denied-target",
                MessageUtil.Placeholder.of("player", target.getName()));
        }
        plugin.getMessageUtil().send(target, "challenge-denied",
            MessageUtil.Placeholder.of("player", sender != null ? sender.getName() : "Sconosciuto"));
        return true;
    }

    public boolean cancelRequest(Player sender) {
        return pendingRequests.entrySet().removeIf(entry -> {
            if (entry.getValue().getSenderId().equals(sender.getUniqueId())) {
                Player target = Bukkit.getPlayer(entry.getKey());
                if (target != null && target.isOnline()) {
                    plugin.getMessageUtil().send(target, "challenge-cancelled-target",
                        MessageUtil.Placeholder.of("player", sender.getName()));
                }
                return true;
            }
            return false;
        });
    }

    private void startDuel(Player player1, Player player2, Kit kit) {
        SavedInventory inv1 = new SavedInventory(player1);
        SavedInventory inv2 = new SavedInventory(player2);
        savedInventories.put(player1.getUniqueId(), inv1);
        savedInventories.put(player2.getUniqueId(), inv2);
        
        Duel duel = new Duel(player1.getUniqueId(), player2.getUniqueId(), kit.getId(), plugin);
        activeDuels.put(player1.getUniqueId(), duel);
        activeDuels.put(player2.getUniqueId(), duel);
        
        teleportToDuelWorld(player1, spawn1);
        teleportToDuelWorld(player2, spawn2);
        
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.getKitManager().applyKit(player1, kit);
            plugin.getKitManager().applyKit(player2, kit);
            duel.startCountdown();
        }, 5L);
    }

    private void teleportToDuelWorld(Player player, Location loc) {
        if (duelWorld == null) {
            initializeDuelWorld();
            if (duelWorld == null) return;
        }
        player.teleport(loc);
    }

    public void endDuel(Duel duel, DuelEndReason reason) {
        UUID p1Id = duel.getPlayer1();
        UUID p2Id = duel.getPlayer2();
        
        Player p1 = Bukkit.getPlayer(p1Id);
        Player p2 = Bukkit.getPlayer(p2Id);
        
        activeDuels.remove(p1Id);
        activeDuels.remove(p2Id);
        
        int cooldown = plugin.getConfigManager().getConfig().getInt("general.cooldown-seconds", 30);
        setCooldown(p1Id, cooldown);
        setCooldown(p2Id, cooldown);
        
        if (p1 != null && p1.isOnline()) {
            restorePlayer(p1);
        }
        if (p2 != null && p2.isOnline()) {
            restorePlayer(p2);
        }
        
        savedInventories.remove(p1Id);
        savedInventories.remove(p2Id);
        
        handleDuelEnd(duel, p1, p2, reason);
    }

    private void handleDuelEnd(Duel duel, Player p1, Player p2, DuelEndReason reason) {
        UUID winner = null;
        UUID loser = null;
        boolean isDraw = false;
        
        switch (reason) {
            case PLAYER1_WIN -> { winner = duel.getPlayer1(); loser = duel.getPlayer2(); }
            case PLAYER2_WIN -> { winner = duel.getPlayer2(); loser = duel.getPlayer1(); }
            case DRAW, TIMEOUT -> { isDraw = true; }
            case FORFEIT_P1 -> { winner = duel.getPlayer2(); loser = duel.getPlayer1(); }
            case FORFEIT_P2 -> { winner = duel.getPlayer1(); loser = duel.getPlayer2(); }
        }
        
        String kitName = duel.getKitName();
        int duration = duel.getDuration();
        double p1Health = p1 != null ? p1.getHealth() : 0;
        double p2Health = p2 != null ? p2.getHealth() : 0;
        
        if (isDraw) {
            if (p1 != null && p2 != null) {
                plugin.getStatsManager().recordDraw(p1.getUniqueId(), p1.getName(), 
                    p2.getUniqueId(), p2.getName(), kitName, duration, p1Health, p2Health);
            }
        } else if (winner != null && loser != null) {
            Player winnerPlayer = Bukkit.getPlayer(winner);
            Player loserPlayer = Bukkit.getPlayer(loser);
            if (winnerPlayer != null && loserPlayer != null) {
                plugin.getStatsManager().recordWin(winner, winnerPlayer.getName(), 
                    loser, loserPlayer.getName(), kitName, duration, 
                    winnerPlayer.getHealth(), loserPlayer.getHealth());
                
                plugin.getRewardManager().giveRewards(winnerPlayer, loserPlayer, kitName, false);
            }
        }
    }

    private void restorePlayer(Player player) {
        SavedInventory saved = savedInventories.get(player.getUniqueId());
        if (saved != null) {
            saved.restore(player);
        }
        
        Location returnLoc = saved != null ? saved.getLocation() : null;
        if (returnLoc == null || returnLoc.getWorld() == null) {
            returnLoc = player.getWorld().getSpawnLocation();
        }
        final Location finalReturnLoc = returnLoc;
        
        int delay = plugin.getConfigManager().getConfig().getInt("duel-world.return-delay", 5);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.teleport(finalReturnLoc);
            }
        }, delay * 20L);
        
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    public boolean isOnCooldown(Player player) {
        return isOnCooldown(player.getUniqueId());
    }

    public boolean isOnCooldown(UUID uuid) {
        Long until = cooldowns.get(uuid);
        if (until == null) return false;
        if (System.currentTimeMillis() >= until) {
            cooldowns.remove(uuid);
            return false;
        }
        return true;
    }

    public long getRemainingCooldown(UUID uuid) {
        Long until = cooldowns.get(uuid);
        if (until == null) return 0;
        long remaining = (until - System.currentTimeMillis()) / 1000;
        return Math.max(0, remaining);
    }

    public void setCooldown(UUID uuid, int seconds) {
        cooldowns.put(uuid, System.currentTimeMillis() + (seconds * 1000L));
    }

    public boolean isInDuel(Player player) {
        return activeDuels.containsKey(player.getUniqueId());
    }

    public Optional<Duel> getDuel(Player player) {
        return Optional.ofNullable(activeDuels.get(player.getUniqueId()));
    }

    public Optional<Duel> getDuel(UUID uuid) {
        return Optional.ofNullable(activeDuels.get(uuid));
    }

    public void stopAllDuels() {
        for (Duel duel : activeDuels.values()) {
            duel.forceEnd(DuelEndReason.ADMIN);
        }
        activeDuels.clear();
        pendingRequests.clear();
    }

    public void cleanupStaleDuels() {
        long now = System.currentTimeMillis();
        activeDuels.values().stream()
            .filter(d -> !d.isActive() || now - d.getStartTime() > 3600000)
            .forEach(d -> d.forceEnd(DuelEndReason.ADMIN));
    }

    public Map<UUID, Duel> getActiveDuels() {
        return new HashMap<>(activeDuels);
    }

    public World getDuelWorld() { return duelWorld; }
    public Location getSpawn1() { return spawn1; }
    public Location getSpawn2() { return spawn2; }
}