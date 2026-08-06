package org.mcvote.common.net;

import org.junit.jupiter.api.Test;
import org.mcvote.common.net.protocol.VoteSignature;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoteSignatureTest {

    private static final String KEY = "super-secret-api-key";
    private static final String PAYLOAD = "{\"username\":\"Notch\",\"challenge\":\"abc\"}";

    @Test
    void signThenVerifyRoundTrips() {
        String signature = VoteSignature.sign(PAYLOAD, KEY);
        assertTrue(VoteSignature.verify(PAYLOAD, KEY, signature));
    }

    @Test
    void tamperedPayloadFailsVerification() {
        String signature = VoteSignature.sign(PAYLOAD, KEY);
        assertFalse(VoteSignature.verify(PAYLOAD + "x", KEY, signature));
    }

    @Test
    void wrongKeyFailsVerification() {
        String signature = VoteSignature.sign(PAYLOAD, KEY);
        assertFalse(VoteSignature.verify(PAYLOAD, "other-key", signature));
    }

    @Test
    void constantTimeEqualsMatchesOnlyEqualTokens() {
        assertTrue(VoteSignature.constantTimeEquals("challenge123", "challenge123"));
        assertFalse(VoteSignature.constantTimeEquals("challenge123", "challenge124"));
    }
}
