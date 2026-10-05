package it.xxmauro.duelli.managers;

import it.xxmauro.duelli.DuelliPlugin;
import it.xxmauro.duelli.utils.MessageUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class StatsManager {

    private final DuelliPlugin plugin;
    private final MySQLManager mysqlManager;
    private final Map<UUID, PlayerStats> cache = new HashMap<>();
    private final String statsTable;

    public StatsManager(DuelliPlugin plugin) {
        this.plugin = plugin;
        this.mysqlManager = plugin.getMySQLManager();
        this.statsTable = plugin.getConfigManager().getConfig().getString("mysql.tables.stats", "duelli_stats");
    }

    public CompletableFuture<PlayerStats> getStats(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            PlayerStats cached = cache.get(uuid);
            if (cached != null && System.currentTimeMillis() - cached.lastUpdated < 30000) {
                return cached;
            }
            return loadStats(uuid);
        });
    }

    public PlayerStats getStatsSync(UUID uuid) {
        PlayerStats cached = cache.get(uuid);
        if (cached != null && System.currentTimeMillis() - cached.lastUpdated < 30000) {
            return cached;
        }
        return loadStats(uuid);
    }

    private PlayerStats loadStats(UUID uuid) {
        return mysqlManager.query(
            "SELECT * FROM `" + statsTable + "` WHERE `uuid` = ?",
            rs -> {
                try {
                    return mapResultSet(rs);
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Errore mappatura ResultSet", e);
                    return null;
                }
            },
            uuid.toString()
        );
    }

    private PlayerStats mapResultSet(ResultSet rs) throws SQLException {
        UUID uuid = UUID.fromString(rs.getString("uuid"));
        String name = rs.getString("name");
        int duels = rs.getInt("duels");
        int wins = rs.getInt("wins");
        int losses = rs.getInt("losses");
        int draws = rs.getInt("draws");
        int kills = rs.getInt("kills");
        int deaths = rs.getInt("deaths");
        int currentStreak = rs.getInt("current_streak");
        int bestStreak = rs.getInt("best_streak");
        long lastDuel = rs.getLong("last_duel");

        PlayerStats stats = new PlayerStats(uuid, name, duels, wins, losses, draws, kills, deaths, currentStreak, bestStreak, lastDuel);
        cache.put(uuid, stats);
        return stats;
    }

    public CompletableFuture<Void> createPlayer(UUID uuid, String name) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT IGNORE INTO `" + statsTable + "` (`uuid`, `name`) VALUES (?, ?)";
            mysqlManager.executeUpdate(sql, uuid.toString(), name);
        });
    }

    public CompletableFuture<Void> recordWin(UUID winnerUuid, String winnerName, UUID loserUuid, String loserName, String kit, int duration, double winnerHealth, double loserHealth) {
        return CompletableFuture.runAsync(() -> {
            String winnerSql = """
                INSERT INTO `%s` (`uuid`, `name`, `duels`, `wins`, `kills`, `current_streak`, `best_streak`, `last_duel`)
                VALUES (?, ?, 1, 1, 1, 1, 1, ?)
                ON DUPLICATE KEY UPDATE
                    `name` = VALUES(`name`),
                    `duels` = `duels` + 1,
                    `wins` = `wins` + 1,
                    `kills` = `kills` + 1,
                    `current_streak` = `current_streak` + 1,
                    `best_streak` = GREATEST(`best_streak`, `current_streak` + 1),
                    `last_duel` = VALUES(`last_duel`),
                    `updated_at` = CURRENT_TIMESTAMP
                """.formatted(statsTable);
            mysqlManager.executeUpdate(winnerSql, winnerUuid.toString(), winnerName, System.currentTimeMillis());

            String loserSql = """
                INSERT INTO `%s` (`uuid`, `name`, `duels`, `losses`, `deaths`, `current_streak`, `last_duel`)
                VALUES (?, ?, 1, 1, 1, 0, ?)
                ON DUPLICATE KEY UPDATE
                    `name` = VALUES(`name`),
                    `duels` = `duels` + 1,
                    `losses` = `losses` + 1,
                    `deaths` = `deaths` + 1,
                    `current_streak` = 0,
                    `last_duel` = VALUES(`last_duel`),
                    `updated_at` = CURRENT_TIMESTAMP
                """.formatted(statsTable);
            mysqlManager.executeUpdate(loserSql, loserUuid.toString(), loserName, System.currentTimeMillis());

            String historyTable = plugin.getConfigManager().getConfig().getString("mysql.tables.history", "duelli_history");
            String historySql = """
                INSERT INTO `%s` (`winner_uuid`, `winner_name`, `loser_uuid`, `loser_name`, `kit`, `duration`, `winner_health`, `loser_health`, `ended_by`)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'DEATH')
                """.formatted(historyTable);
            mysqlManager.executeUpdate(historySql, winnerUuid.toString(), winnerName, loserUuid.toString(), loserName, kit, duration, winnerHealth, loserHealth);

            cache.remove(winnerUuid);
            cache.remove(loserUuid);
        });
    }

    public CompletableFuture<Void> recordDraw(UUID uuid1, String name1, UUID uuid2, String name2, String kit, int duration, double health1, double health2) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                INSERT INTO `%s` (`uuid`, `name`, `duels`, `draws`, `last_duel`)
                VALUES (?, ?, 1, 1, ?)
                ON DUPLICATE KEY UPDATE
                    `name` = VALUES(`name`),
                    `duels` = `duels` + 1,
                    `draws` = `draws` + 1,
                    `last_duel` = VALUES(`last_duel`),
                    `updated_at` = CURRENT_TIMESTAMP
                """.formatted(statsTable);
            mysqlManager.executeUpdate(sql, uuid1.toString(), name1, System.currentTimeMillis());
            mysqlManager.executeUpdate(sql, uuid2.toString(), name2, System.currentTimeMillis());

            String historyTable = plugin.getConfigManager().getConfig().getString("mysql.tables.history", "duelli_history");
            String historySql = """
                INSERT INTO `%s` (`winner_uuid`, `winner_name`, `loser_uuid`, `loser_name`, `kit`, `duration`, `winner_health`, `loser_health`, `ended_by`)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'TIMEOUT')
                """.formatted(historyTable);
            mysqlManager.executeUpdate(historySql, uuid1.toString(), name1, uuid2.toString(), name2, kit, duration, health1, health2);

            cache.remove(uuid1);
            cache.remove(uuid2);
        });
    }

    public CompletableFuture<Void> recordForfeit(UUID winnerUuid, String winnerName, UUID loserUuid, String loserName, String kit, int duration) {
        return CompletableFuture.runAsync(() -> {
            String winnerSql = """
                INSERT INTO `%s` (`uuid`, `name`, `duels`, `wins`, `kills`, `current_streak`, `best_streak`, `last_duel`)
                VALUES (?, ?, 1, 1, 1, 1, 1, ?)
                ON DUPLICATE KEY UPDATE
                    `name` = VALUES(`name`),
                    `duels` = `duels` + 1,
                    `wins` = `wins` + 1,
                    `kills` = `kills` + 1,
                    `current_streak` = `current_streak` + 1,
                    `best_streak` = GREATEST(`best_streak`, `current_streak` + 1),
                    `last_duel` = VALUES(`last_duel`),
                    `updated_at` = CURRENT_TIMESTAMP
                """.formatted(statsTable);
            mysqlManager.executeUpdate(winnerSql, winnerUuid.toString(), winnerName, System.currentTimeMillis());

            String loserSql = """
                INSERT INTO `%s` (`uuid`, `name`, `duels`, `losses`, `deaths`, `current_streak`, `last_duel`)
                VALUES (?, ?, 1, 1, 1, 0, ?)
                ON DUPLICATE KEY UPDATE
                    `name` = VALUES(`name`),
                    `duels` = `duels` + 1,
                    `losses` = `losses` + 1,
                    `deaths` = `deaths` + 1,
                    `current_streak` = 0,
                    `last_duel` = VALUES(`last_duel`),
                    `updated_at` = CURRENT_TIMESTAMP
                """.formatted(statsTable);
            mysqlManager.executeUpdate(loserSql, loserUuid.toString(), loserName, System.currentTimeMillis());

            String historyTable = plugin.getConfigManager().getConfig().getString("mysql.tables.history", "duelli_history");
            String historySql = """
                INSERT INTO `%s` (`winner_uuid`, `winner_name`, `loser_uuid`, `loser_name`, `kit`, `duration`, `ended_by`)
                VALUES (?, ?, ?, ?, ?, ?, 'FORFEIT')
                """.formatted(historyTable);
            mysqlManager.executeUpdate(historySql, winnerUuid.toString(), winnerName, loserUuid.toString(), loserName, kit, duration);

            cache.remove(winnerUuid);
            cache.remove(loserUuid);
        });
    }

    public CompletableFuture<Void> updateName(UUID uuid, String name) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE `" + statsTable + "` SET `name` = ? WHERE `uuid` = ?";
            mysqlManager.executeUpdate(sql, name, uuid.toString());
            cache.remove(uuid);
        });
    }

    public static class PlayerStats {
        public final UUID uuid;
        public final String name;
        public final int duels;
        public final int wins;
        public final int losses;
        public final int draws;
        public final int kills;
        public final int deaths;
        public final int currentStreak;
        public final int bestStreak;
        public final long lastDuel;
        public final long lastUpdated;

        public PlayerStats(UUID uuid, String name, int duels, int wins, int losses, int draws,
                          int kills, int deaths, int currentStreak, int bestStreak, long lastDuel) {
            this.uuid = uuid;
            this.name = name;
            this.duels = duels;
            this.wins = wins;
            this.losses = losses;
            this.draws = draws;
            this.kills = kills;
            this.deaths = deaths;
            this.currentStreak = currentStreak;
            this.bestStreak = bestStreak;
            this.lastDuel = lastDuel;
            this.lastUpdated = System.currentTimeMillis();
        }

        public double getWinRate() {
            if (duels == 0) return 0.0;
            return (double) wins / duels * 100.0;
        }

        public double getKDRatio() {
            if (deaths == 0) return kills;
            return (double) kills / deaths;
        }
    }
}
