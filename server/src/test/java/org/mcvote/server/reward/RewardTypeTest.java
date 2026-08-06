package org.mcvote.server.reward;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RewardTypeTest {

    @Test
    void resolvesKnownKeywords() {
        assertEquals(RewardType.PLAYER_COMMAND, RewardType.fromKeyword("player"));
        assertEquals(RewardType.ITEM, RewardType.fromKeyword("give"));
        assertEquals(RewardType.BROADCAST, RewardType.fromKeyword("broadcast"));
        assertEquals(RewardType.SOUND, RewardType.fromKeyword("sound"));
    }

    @Test
    void defaultsToConsoleCommand() {
        assertEquals(RewardType.CONSOLE_COMMAND, RewardType.fromKeyword("anything-else"));
    }
}
