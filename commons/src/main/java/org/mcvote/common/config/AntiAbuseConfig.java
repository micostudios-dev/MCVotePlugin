package org.mcvote.common.config;

public record AntiAbuseConfig(boolean enabled, long cooldownMs, int maxDailyVotes) {

    public static final AntiAbuseConfig DISABLED = new AntiAbuseConfig(false, 0, 0);
}
