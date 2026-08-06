package org.mcvote.common.config;

public record DatabaseConfig(
        StorageType type,
        String file,
        String host,
        int port,
        String database,
        String username,
        String password,
        int poolSize
) {
}
