package org.mcvote.server.config;

import java.util.List;
import java.util.Map;

public record Messages(String prefix, Map<String, String> values, Map<String, List<String>> blocks) {

    public String raw(String key) {
        return values.getOrDefault(key, "");
    }

    public String get(String key) {
        String value = raw(key);

        return value.isEmpty() ? "" : prefix + value;
    }

    public List<String> block(String key) {
        return blocks.getOrDefault(key, List.of());
    }

    public static String apply(String text, String... placeholders) {
        String result = text;

        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            result = result.replace(placeholders[i], placeholders[i + 1]);
        }

        return result;
    }
}
