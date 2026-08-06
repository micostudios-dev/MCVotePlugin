package org.mcvote.common.config;

public enum StorageType {

    SQLITE,

    MYSQL,

    PROXY;

    public static StorageType from(String raw) {
        return switch (raw == null ? "" : raw.trim().toLowerCase()) {
            case "mysql", "mariadb" -> MYSQL;
            case "proxy", "bungee", "velocity", "none" -> PROXY;
            default -> SQLITE;
        };
    }
}
