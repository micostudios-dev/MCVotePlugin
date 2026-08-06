package org.mcvote.common.storage.sql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.mcvote.common.config.DatabaseConfig;
import org.mcvote.common.storage.StorageException;
import org.mcvote.common.storage.VoteStorage;
import org.mcvote.common.storage.model.DeliveryType;
import org.mcvote.common.storage.model.PartyTick;
import org.mcvote.common.storage.model.PendingDelivery;
import org.mcvote.common.storage.model.PlayerVoteData;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

public final class MySqlStorage implements VoteStorage {

    private final DatabaseConfig config;
    private HikariDataSource dataSource;

    public MySqlStorage(DatabaseConfig config) {
        this.config = config;
    }

    @Override
    public void init() throws Exception {
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("MCVote-Pool");
        hikari.setDriverClassName("org.mariadb.jdbc.Driver");
        hikari.setJdbcUrl("jdbc:mariadb://" + config.host() + ":" + config.port() + "/" + config.database());
        hikari.setUsername(config.username());
        hikari.setPassword(config.password());
        hikari.setMaximumPoolSize(Math.max(2, config.poolSize()));
        hikari.setMinimumIdle(1);
        hikari.setConnectionTimeout(10_000);
        hikari.addDataSourceProperty("cachePrepStmts", "true");
        hikari.addDataSourceProperty("prepStmtCacheSize", "128");
        this.dataSource = new HikariDataSource(hikari);

        try (Connection connection = dataSource.getConnection()) {
            for (String ddl : SCHEMA) {
                try (PreparedStatement statement = connection.prepareStatement(ddl)) {
                    statement.execute();
                }
            }
        }
    }

    @Override
    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Override
    public PlayerVoteData load(String username) {
        String key = lower(username);

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT username, display_name, uuid, streak, best_streak, total_votes, last_vote_day, last_vote_ms " +
                             "FROM mcvote_players WHERE username = ?")) {
            statement.setString(1, key);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? read(rs) : null;
            }
        } catch (SQLException e) {
            throw new StorageException("load(" + key + ")", e);
        }
    }

    @Override
    public void save(PlayerVoteData data) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO mcvote_players (username, display_name, uuid, streak, best_streak, total_votes, last_vote_day, last_vote_ms) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                             "ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), uuid = VALUES(uuid), " +
                             "streak = VALUES(streak), best_streak = VALUES(best_streak), total_votes = VALUES(total_votes), " +
                             "last_vote_day = VALUES(last_vote_day), last_vote_ms = VALUES(last_vote_ms)")) {
            statement.setString(1, lower(data.username()));
            statement.setString(2, data.displayName());
            statement.setString(3, data.uuid());
            statement.setInt(4, data.streak());
            statement.setInt(5, data.bestStreak());
            statement.setInt(6, data.totalVotes());
            statement.setInt(7, data.lastVoteDay());
            statement.setLong(8, data.lastVoteMs());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("save(" + data.username() + ")", e);
        }
    }

    @Override
    public void linkIdentity(String username, String uuid, String displayName) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO mcvote_players (username, display_name, uuid) VALUES (?, ?, ?) " +
                             "ON DUPLICATE KEY UPDATE uuid = VALUES(uuid), display_name = VALUES(display_name)")) {
            statement.setString(1, lower(username));
            statement.setString(2, displayName);
            statement.setString(3, uuid);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("linkIdentity(" + username + ")", e);
        }
    }

    @Override
    public void enqueue(String username, DeliveryType type, String context, long createdMs) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO mcvote_deliveries (username, type, context, created_ms) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, lower(username));
            statement.setString(2, type.name());
            statement.setString(3, context == null ? "" : context);
            statement.setLong(4, createdMs);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("enqueue(" + username + ")", e);
        }
    }

    @Override
    public long lastServiceVoteMs(String username, String service) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT MAX(voted_ms) FROM mcvote_vote_history WHERE username = ? AND service = ?")) {
            statement.setString(1, lower(username));
            statement.setString(2, service == null ? "" : service);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new StorageException("lastServiceVoteMs(" + username + ")", e);
        }
    }

    @Override
    public int countVotesSince(String username, long sinceMs) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM mcvote_vote_history WHERE username = ? AND voted_ms >= ?")) {
            statement.setString(1, lower(username));
            statement.setLong(2, sinceMs);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new StorageException("countVotesSince(" + username + ")", e);
        }
    }

    @Override
    public void recordVoteHistory(String username, String service, long votedMs) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO mcvote_vote_history (username, service, voted_ms) VALUES (?, ?, ?)")) {
            statement.setString(1, lower(username));
            statement.setString(2, service == null ? "" : service);
            statement.setLong(3, votedMs);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("recordVoteHistory(" + username + ")", e);
        }
    }

    @Override
    public List<PendingDelivery> claim(Collection<String> usernames) {
        if (usernames.isEmpty()) {
            return List.of();
        }

        StringJoiner placeholders = new StringJoiner(",", "(", ")");
        usernames.forEach(u -> placeholders.add("?"));

        List<PendingDelivery> claimed = new ArrayList<>();
        List<Long> ids = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT id, username, type, context, created_ms FROM mcvote_deliveries " +
                             "WHERE claimed = 0 AND username IN " + placeholders)) {
            int index = 1;
            for (String username : usernames) {
                select.setString(index++, lower(username));
            }
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    ids.add(id);
                    claimed.add(new PendingDelivery(id, rs.getString("username"),
                            DeliveryType.from(rs.getString("type")), rs.getString("context"), rs.getLong("created_ms")));
                }
            }

            if (!ids.isEmpty()) {
                StringJoiner idPlaceholders = new StringJoiner(",", "(", ")");
                ids.forEach(id -> idPlaceholders.add("?"));
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE mcvote_deliveries SET claimed = 1 WHERE id IN " + idPlaceholders)) {
                    int i = 1;
                    for (long id : ids) {
                        update.setLong(i++, id);
                    }
                    update.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new StorageException("claim", e);
        }

        return claimed;
    }

    @Override
    public PartyTick addPartyProgress(int amount, int goal) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int progress = 0;
                long totalParties = 0;
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT progress, total_parties FROM mcvote_party WHERE id = 1 FOR UPDATE")) {
                    try (ResultSet rs = select.executeQuery()) {
                        if (rs.next()) {
                            progress = rs.getInt("progress");
                            totalParties = rs.getLong("total_parties");
                        }
                    }
                }

                int accumulated = progress + amount;
                int triggered = goal > 0 ? accumulated / goal : 0;
                int remainder = goal > 0 ? accumulated % goal : accumulated;
                totalParties += triggered;

                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE mcvote_party SET progress = ?, total_parties = ? WHERE id = 1")) {
                    update.setInt(1, remainder);
                    update.setLong(2, totalParties);
                    update.executeUpdate();
                }

                connection.commit();
                return new PartyTick(remainder, triggered, totalParties);
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new StorageException("addPartyProgress", e);
        }
    }

    @Override
    public int partyProgress() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT progress FROM mcvote_party WHERE id = 1")) {
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt("progress") : 0;
            }
        } catch (SQLException e) {
            throw new StorageException("partyProgress", e);
        }
    }

    @Override
    public List<PlayerVoteData> topByVotes(int limit) {
        List<PlayerVoteData> top = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT username, display_name, uuid, streak, best_streak, total_votes, last_vote_day, last_vote_ms " +
                             "FROM mcvote_players ORDER BY total_votes DESC LIMIT ?")) {
            statement.setInt(1, Math.max(1, limit));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    top.add(read(rs));
                }
            }
        } catch (SQLException e) {
            throw new StorageException("topByVotes", e);
        }
        return top;
    }

    private static PlayerVoteData read(ResultSet rs) throws SQLException {
        return new PlayerVoteData(
                rs.getString("username"),
                rs.getString("display_name"),
                rs.getString("uuid"),
                rs.getInt("streak"),
                rs.getInt("best_streak"),
                rs.getInt("total_votes"),
                rs.getInt("last_vote_day"),
                rs.getLong("last_vote_ms"));
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static final String[] SCHEMA = {
            "CREATE TABLE IF NOT EXISTS mcvote_players (" +
                    "username VARCHAR(32) NOT NULL PRIMARY KEY, " +
                    "display_name VARCHAR(32) NULL, " +
                    "uuid CHAR(36) NULL, " +
                    "streak INT NOT NULL DEFAULT 0, " +
                    "best_streak INT NOT NULL DEFAULT 0, " +
                    "total_votes INT NOT NULL DEFAULT 0, " +
                    "last_vote_day INT NOT NULL DEFAULT 0, " +
                    "last_vote_ms BIGINT NOT NULL DEFAULT 0" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS mcvote_deliveries (" +
                    "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
                    "username VARCHAR(32) NOT NULL, " +
                    "type VARCHAR(16) NOT NULL, " +
                    "context VARCHAR(64) NOT NULL DEFAULT '', " +
                    "created_ms BIGINT NOT NULL, " +
                    "claimed TINYINT NOT NULL DEFAULT 0, " +
                    "INDEX idx_user_claimed (username, claimed)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS mcvote_vote_history (" +
                    "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
                    "username VARCHAR(32) NOT NULL, " +
                    "service VARCHAR(64) NOT NULL, " +
                    "voted_ms BIGINT NOT NULL, " +
                    "INDEX idx_history_user_service (username, service), " +
                    "INDEX idx_history_user_time (username, voted_ms)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS mcvote_party (" +
                    "id INT NOT NULL PRIMARY KEY, " +
                    "progress INT NOT NULL DEFAULT 0, " +
                    "total_parties BIGINT NOT NULL DEFAULT 0" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "INSERT IGNORE INTO mcvote_party (id, progress, total_parties) VALUES (1, 0, 0)"
    };
}
