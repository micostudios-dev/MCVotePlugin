package org.mcvote.common.storage;

import org.mcvote.common.config.DatabaseConfig;
import org.mcvote.common.storage.sql.MySqlStorage;
import org.mcvote.common.storage.sql.SqliteStorage;

import java.io.File;

public final class StorageFactory {

    private StorageFactory() {
    }

    public static VoteStorage create(DatabaseConfig cfg, File dataFolder) {
        return switch (cfg.type()) {
            case MYSQL -> new MySqlStorage(cfg);
            case SQLITE -> {
                String name = cfg.file() == null || cfg.file().isBlank() ? "database.db" : cfg.file();
                yield new SqliteStorage(new File(dataFolder, name));
            }
            case PROXY -> throw new IllegalArgumentException(
                    "PROXY storage is not a database; build a RemoteVoteStorage instead");
        };
    }
}
