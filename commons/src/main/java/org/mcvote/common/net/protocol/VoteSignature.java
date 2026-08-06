package org.mcvote.common.net.protocol;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class VoteSignature {

    private static final String ALGO = "HmacSHA256";

    private VoteSignature() {
    }

    public static String sign(String payload, String apiKey) {
        try {
            Mac mac = Mac.getInstance(ALGO);
            mac.init(new SecretKeySpec(apiKey.getBytes(StandardCharsets.UTF_8), ALGO));
            return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign payload", e);
        }
    }

    public static boolean verify(String payload, String apiKey, String base64Signature) {
        try {
            byte[] expected = Base64.getDecoder().decode(sign(payload, apiKey));
            byte[] provided = Base64.getDecoder().decode(base64Signature.trim());
            return MessageDigest.isEqual(expected, provided);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
