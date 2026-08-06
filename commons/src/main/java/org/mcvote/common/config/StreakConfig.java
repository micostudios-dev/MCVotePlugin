package org.mcvote.common.config;

import java.time.ZoneId;
import java.util.List;

public record StreakConfig(
        boolean enabled,
        ZoneId zone,
        List<StreakTier> tiers
) {

    public StreakTier tierFor(int streak) {
        for (StreakTier tier : tiers) {
            if (tier.required() == streak) {
                return tier;
            }
        }
        return null;
    }

    public StreakTier nextTier(int streak) {
        StreakTier best = null;
        for (StreakTier tier : tiers) {
            if (tier.required() > streak && (best == null || tier.required() < best.required())) {
                best = tier;
            }
        }
        return best;
    }
}
