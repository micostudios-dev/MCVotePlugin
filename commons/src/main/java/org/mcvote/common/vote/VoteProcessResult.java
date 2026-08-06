package org.mcvote.common.vote;

public record VoteProcessResult(
        boolean accepted,
        String username,
        int streak,
        String milestoneTierId,
        boolean partyTriggered,
        int partyProgress
) {

    public static VoteProcessResult rejected(String username) {
        return new VoteProcessResult(false, username, 0, null, false, 0);
    }

    public boolean hasMilestone() {
        return milestoneTierId != null;
    }
}
