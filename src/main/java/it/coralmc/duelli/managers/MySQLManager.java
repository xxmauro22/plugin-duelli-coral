package it.coralmc.duelli.managers;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import it.coralmc.duelli.DuelliPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;

public class MySQLManager {

    private final DuelliPlugin plugin;
    private HikariDataSource dataSource;
    private boolean connected = false;

    public MySQLManager(DuelliPlugin plugin) {
        this.plugin = plugin;
    }

    public void connect() {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        ConfigurationSection mysqlSection = config.getConfigurationSection("mysql");
        
        if (mysqlSection == null) {
            plugin.getLogger().warning("Sezione MySQL non trovata in config.yml");
            return;
        }

        String host = mysqlSection.getString("host", "localhost");
        int port = mysqlSection.getInt("port", 3306);
        String database = mysqlSection.getString("database", "coralmc_duelli");
        String username = mysqlSection.getString("username", "root");
        String password = mysqlSection.getString("password", "");
        boolean ssl = mysqlSection.getBoolean("use-ssl", false);
        
        ConfigurationSection poolSection = mysqlSection.getConfigurationSection("pool");
        int maxPoolSize = poolSection != null ? poolSection.getInt("maximum-pool-size", 10) : 10;
        int minIdle = poolSection != null ? poolSection.getInt("minimum-idle", 2) : 2;
        long connectionTimeout = poolSection != null ? poolSection.getLong("connection-timeout", 30000) : 30000;
        long idleTimeout = poolSection != null ? poolSection.getLong("idle-timeout", 600000) : 600000;
        long maxLifetime = poolSection != null ? poolSection.getLong("max-lifetime", 1800000) : 1800000;

        String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=%s&allowPublicKeyRetrieval=true&serverTimezone=UTC", 
            host, port, database, ssl);

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(url);
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        hikariConfig.setMaximumPoolSize(maxPoolSize);
        hikariConfig.setMinimumIdle(minIdle);
        hikariConfig.setConnectionTimeout(connectionTimeout);
        hikariConfig.setIdleTimeout(idleTimeout);
        hikariConfig.setMaxLifetime(maxLifetime);
        hikariConfig.setPoolName("DuelliRealistici-Pool");
        
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
        hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true");

        try {
            dataSource = new HikariDataSource(hikariConfig);
            
            try (Connection conn = dataSource.getConnection()) {
                connected = true;
                plugin.getLogger().info("Connesso a MySQL: " + host + ":" + port + "/" + database);
            }
            
            initializeTables();
            
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Impossibile connettersi a MySQL: " + e.getMessage(), e);
            connected = false;
        }
    }

    private void initializeTables() {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        String statsTable = config.getString("mysql.tables.stats", "duelli_stats");
        String historyTable = config.getString("mysql.tables.history", "duelli_history");

        String createStatsTable = String.format("""
            CREATE TABLE IF NOT EXISTS `%s` (
                `uuid` CHAR(36) NOT NULL PRIMARY KEY,
                `name` VARCHAR(16) NOT NULL,
                `duels` INT NOT NULL DEFAULT 0,
                `wins` INT NOT NULL DEFAULT 0,
                `losses` INT NOT NULL DEFAULT 0,
                `draws` INT NOT NULL DEFAULT 0,
                `kills` INT NOT NULL DEFAULT 0,
                `deaths` INT NOT NULL DEFAULT 0,
                `current_streak` INT NOT NULL DEFAULT 0,
                `best_streak` INT NOT NULL DEFAULT 0,
                `last_duel` BIGINT NOT NULL DEFAULT 0,
                `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX `idx_wins` (`wins`),
                INDEX `idx_duels` (`duels`),
                INDEX `idx_streak` (`current_streak`),
                INDEX `idx_updated` (`updated_at`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
            """, statsTable);

        String createHistoryTable = String.format("""
            CREATE TABLE IF NOT EXISTS `%s` (
                `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                `winner_uuid` CHAR(36),
                `winner_name` VARCHAR(16),
                `loser_uuid` CHAR(36),
                `loser_name` VARCHAR(16),
                `kit` VARCHAR(32) NOT NULL,
                `duration` INT NOT NULL DEFAULT 0,
                `winner_health` DOUBLE NOT NULL DEFAULT 0,
                `loser_health` DOUBLE NOT NULL DEFAULT 0,
                `ended_by` ENUM('DEATH', 'FORFEIT', 'TIMEOUT', 'ADMIN') NOT NULL DEFAULT 'DEATH',
                `played_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX `idx_winner` (`winner_uuid`),
                INDEX `idx_loser` (`loser_uuid`),
                INDEX `idx_played` (`played_at`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
            """, historyTable);

        executeUpdate(createStatsTable);
        executeUpdate(createHistoryTable);
    }

    public void executeUpdate(String sql) {
        try (Connection conn = getConnection(); var stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Errore esecuzione query: " + sql, e);
        }
    }

    public void executeUpdate(String sql, Object... params) {
        try (Connection conn = getConnection(); var stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Errore query preparata: " + sql, e);
        }
    }

    public <T> T query(String sql, java.util.function.Function<ResultSet, T> mapper) {
        try (Connection conn = getConnection(); var stmt = conn.createStatement(); var rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return mapper.apply(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Errore query: " + sql, e);
        }
        return null;
    }

    public <T> T query(String sql, java.util.function.Function<ResultSet, T> mapper, Object... params) {
        try (Connection conn = getConnection(); var stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapper.apply(rs);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Errore query preparata: " + sql, e);
        }
        return null;
    }

    public void queryList(String sql, java.util.function.Consumer<ResultSet> consumer) {
        queryList(sql, consumer, new Object[0]);
    }

    public void queryList(String sql, java.util.function.Consumer<ResultSet> consumer, Object... params) {
        try (Connection conn = getConnection(); var stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    consumer.accept(rs);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Errore query list: " + sql, e);
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("DataSource non inizializzato o chiuso");
        }
        return dataSource.getConnection();
    }

    public boolean isConnected() {
        return connected && dataSource != null && !dataSource.isClosed();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            connected = false;
            plugin.getLogger().info("Connessione MySQL chiusa.");
        }
    }

    public HikariDataSource getDataSource() {
        return dataSource;
    }
}