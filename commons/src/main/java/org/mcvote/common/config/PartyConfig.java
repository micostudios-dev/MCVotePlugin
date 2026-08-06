package org.mcvote.common.config;

public record PartyConfig(
        boolean enabled,
        int goal,
        String broadcastMessage
) {
}
