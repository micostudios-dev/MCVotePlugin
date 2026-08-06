package org.mcvote.common.storage;

import org.junit.jupiter.api.Test;
import org.mcvote.common.storage.model.DeliveryType;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DeliveryTypeTest {

    @Test
    void parsesKnownTypesCaseInsensitively() {
        assertEquals(DeliveryType.STREAK, DeliveryType.from("STREAK"));
        assertEquals(DeliveryType.PARTY, DeliveryType.from("party"));
        assertEquals(DeliveryType.VOTE, DeliveryType.from("vote"));
    }

    @Test
    void defaultsToVoteForUnknownOrNull() {
        assertEquals(DeliveryType.VOTE, DeliveryType.from("nonsense"));
        assertEquals(DeliveryType.VOTE, DeliveryType.from(null));
    }
}
