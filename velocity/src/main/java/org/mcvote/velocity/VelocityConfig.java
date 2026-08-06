package org.mcvote.velocity;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

final class VelocityConfig {

    private final Map<String, Object> root;

    private VelocityConfig(Map<String, Object> root) {
        this.root = root;
    }

    static VelocityConfig load(Path dataDir, InputStream defaults) throws Exception {
        Files.createDirectories(dataDir);
        Path file = dataDir.resolve("config.yml");
        if (Files.notExists(file) && defaults != null) {
            Files.copy(defaults, file);
        }
        try (InputStream in = Files.newInputStream(file)) {
            Map<String, Object> loaded = new Yaml().load(in);
            return new VelocityConfig(loaded == null ? Map.of() : loaded);
        }
    }

    @SuppressWarnings("unchecked")
    Object raw(String path) {
        Object current = root;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = ((Map<String, Object>) map).get(part);
        }
        return current;
    }

    String getString(String path, String def) {
        Object value = raw(path);
        return value == null ? def : String.valueOf(value);
    }

    int getInt(String path, int def) {
        Object value = raw(path);
        return value instanceof Number n ? n.intValue() : def;
    }

    long getLong(String path, long def) {
        Object value = raw(path);
        return value instanceof Number n ? n.longValue() : def;
    }

    boolean getBoolean(String path, boolean def) {
        Object value = raw(path);
        return value instanceof Boolean b ? b : def;
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> getSection(String path) {
        Object value = raw(path);
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}
