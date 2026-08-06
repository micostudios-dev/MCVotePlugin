package org.mcvote.common.storage.model;

public record PlayerVoteData(
        String username,
        String displayName,
        String uuid,
        int streak,
        int bestStreak,
        int totalVotes,
        int lastVoteDay,
        long lastVoteMs
) {
}
