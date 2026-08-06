package org.mcvote.common.net;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ReplayGuard {

    private static final int MAX_ENTRIES = 4096;

    private final long windowMs;
    private final Map<String, Long> seen = new LinkedHashMap<>();

    public ReplayGuard(long windowMs) {
        this.windowMs = windowMs;
    }

    public synchronized boolean accept(byte[] block) {
        if (windowMs <= 0) {
            return true;
        }

        long now = System.currentTimeMillis();
        seen.values().removeIf(expiry -> expiry <= now);

        String key = fingerprint(block);

        if (seen.containsKey(key)) {
            return false;
        }

        while (seen.size() >= MAX_ENTRIES) {
            seen.remove(seen.keySet().iterator().next());
        }

        seen.put(key, now + windowMs);

        return true;
    }

    private static String fingerprint(byte[] block) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            return Base64.getEncoder().encodeToString(digest.digest(block));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
