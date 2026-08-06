package org.mcvote.common.config;

import org.mcvote.common.net.protocol.VotifierProtocol;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public record ReceiverConfig(
        boolean enabled,
        String host,
        int port,
        Map<String, String> tokens,
        String apiKey,
        long replayWindowMs
) {

    private static final String PLACEHOLDER_KEY = "CHANGE_ME";

    public ReceiverConfig {
        host = host == null ? "" : host.trim();
        apiKey = apiKey == null ? "" : apiKey.trim();

        if (apiKey.equalsIgnoreCase(PLACEHOLDER_KEY)) {
            apiKey = "";
        }

        Map<String, String> normalized = new LinkedHashMap<>();

        if (tokens != null) {
            tokens.forEach((service, token) -> {
                if (service != null && token != null && !token.isBlank()) {
                    normalized.put(service.toLowerCase(Locale.ROOT), token.trim());
                }
            });
        }

        if (!normalized.containsKey(VotifierProtocol.DEFAULT_TOKEN) && !apiKey.isEmpty()) {
            normalized.put(VotifierProtocol.DEFAULT_TOKEN, apiKey);
        }

        tokens = Map.copyOf(normalized);
    }

    public String tokenFor(String serviceName) {
        String service = serviceName == null ? "" : serviceName.toLowerCase(Locale.ROOT);

        return tokens.getOrDefault(service, tokens.getOrDefault(VotifierProtocol.DEFAULT_TOKEN, ""));
    }
}
