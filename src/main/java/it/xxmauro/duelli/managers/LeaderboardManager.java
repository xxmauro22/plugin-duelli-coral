package it.xxmauro.duelli.managers;

import it.xxmauro.duelli.DuelliPlugin;
import it.xxmauro.duelli.managers.StatsManager.PlayerStats;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.logging.Level;

public class LeaderboardManager {

    private final DuelliPlugin plugin;
    private final MySQLManager mysqlManager;
    private final String statsTable;
    
    private final Map<String, List<LeaderboardEntry>> cache = new ConcurrentHashMap<>();
    private long lastRefresh = 0;

    public LeaderboardManager(DuelliPlugin plugin) {
        this.plugin = plugin;
        this.mysqlManager = plugin.getMySQLManager();
        this.statsTable = plugin.getConfigManager().getConfig().getString("mysql.tables.stats", "duelli_stats");
    }

    public CompletableFuture<List<LeaderboardEntry>> getLeaderboard(LeaderboardType type, int page, int perPage) {
        return CompletableFuture.supplyAsync(() -> {
            String cacheKey = type.name().toLowerCase();
            List<LeaderboardEntry> cached = cache.get(cacheKey);
            
            if (cached != null && System.currentTimeMillis() - lastRefresh < 300000) {
                int start = (page - 1) * perPage;
                int end = Math.min(start + perPage, cached.size());
                if (start < cached.size()) {
                    return cached.subList(start, end);
                }
                return List.of();
            }
            
            return fetchLeaderboard(type, page, perPage);
        });
    }

    public CompletableFuture<Integer> getTotalPages(LeaderboardType type, int perPage) {
        return CompletableFuture.supplyAsync(() -> {
            String countSql = getCountSql(type);
            Integer count = mysqlManager.query(countSql, rs -> {
                try {
                    return rs.getInt(1);
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Errore query count", e);
                    return 0;
                }
            });
            if (count == null || count == 0) return 1;
            return (int) Math.ceil((double) count / perPage);
        });
    }

    private List<LeaderboardEntry> fetchLeaderboard(LeaderboardType type, int page, int perPage) {
        String sql = getLeaderboardSql(type, page, perPage);
        List<LeaderboardEntry> entries = new ArrayList<>();
        
        mysqlManager.queryList(sql, rs -> {
            try {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                String name = rs.getString("name");
                int value = getValueForType(rs, type);
                entries.add(new LeaderboardEntry(entries.size() + 1 + (page - 1) * perPage, uuid, name, value));
            } catch (Exception e) {
                plugin.getLogger().warning("Errore parsing leaderboard entry: " + e.getMessage());
            }
        });
        
        return entries;
    }

    private String getLeaderboardSql(LeaderboardType type, int page, int perPage) {
        String orderBy = switch (type) {
            case WINS -> "`wins` DESC, `duels` ASC";
            case DUELS -> "`duels` DESC, `wins` DESC";
            case WINRATE -> "(`wins` / NULLIF(`duels`, 0)) DESC, `wins` DESC";
            case STREAK -> "`current_streak` DESC, `best_streak` DESC";
        };
        
        int offset = (page - 1) * perPage;
        return "SELECT `uuid`, `name`, `duels`, `wins`, `losses`, `draws`, `current_streak`, `best_streak` " +
               "FROM `" + statsTable + "` " +
               "WHERE `duels` > 0 " +
               "ORDER BY " + orderBy + " " +
               "LIMIT " + perPage + " OFFSET " + offset;
    }

    private String getCountSql(LeaderboardType type) {
        return "SELECT COUNT(*) FROM `" + statsTable + "` WHERE `duels` > 0";
    }

    private int getValueForType(ResultSet rs, LeaderboardType type) {
        try {
            return switch (type) {
                case WINS -> rs.getInt("wins");
                case DUELS -> rs.getInt("duels");
                case WINRATE -> {
                    int duels = rs.getInt("duels");
                    int wins = rs.getInt("wins");
                    yield duels > 0 ? (int) Math.round((double) wins / duels * 100) : 0;
                }
                case STREAK -> rs.getInt("current_streak");
            };
        } catch (Exception e) {
            return 0;
        }
    }

    public void refreshCache() {
        for (LeaderboardType type : LeaderboardType.values()) {
            List<LeaderboardEntry> entries = fetchLeaderboard(type, 1, 100);
            cache.put(type.name().toLowerCase(), entries);
        }
        lastRefresh = System.currentTimeMillis();
    }

    public enum LeaderboardType {
        WINS("vittorie", "Vittorie"),
        DUELS("duelli", "Duelli Totali"),
        WINRATE("winrate", "Winrate %"),
        STREAK("streak", "Streak Attuale");

        private final String id;
        private final String displayName;

        LeaderboardType(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public String getId() { return id; }
        public String getDisplayName() { return displayName; }

        public static LeaderboardType fromString(String str) {
            for (LeaderboardType type : values()) {
                if (type.id.equalsIgnoreCase(str) || type.name().equalsIgnoreCase(str)) {
                    return type;
                }
            }
            return WINS;
        }
    }

    public record LeaderboardEntry(int position, UUID uuid, String name, int value) {}
}
