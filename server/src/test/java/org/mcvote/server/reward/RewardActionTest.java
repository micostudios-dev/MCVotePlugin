package org.mcvote.server.reward;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RewardActionTest {

    @Test
    void parsesKeywordAndArgument() {
        RewardAction action = RewardAction.parse("command: give %player% diamond 1");
        assertEquals(RewardType.CONSOLE_COMMAND, action.type());
        assertEquals("give %player% diamond 1", action.argument());
    }

    @Test
    void mapsKeywordsToTypes() {
        assertEquals(RewardType.MESSAGE, RewardAction.parse("message: hi").type());
        assertEquals(RewardType.ITEM, RewardAction.parse("item: DIAMOND 3").type());
        assertEquals(RewardType.PLAYER_COMMAND, RewardAction.parse("player: spawn").type());
        assertEquals(RewardType.BROADCAST, RewardAction.parse("broadcast: hey").type());
    }

    @Test
    void treatsLineWithoutKeywordAsConsoleCommand() {
        RewardAction action = RewardAction.parse("say hello");
        assertEquals(RewardType.CONSOLE_COMMAND, action.type());
        assertEquals("say hello", action.argument());
    }
}
