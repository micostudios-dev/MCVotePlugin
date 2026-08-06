package org.mcvote.api;

public interface MCVoteAPI {

    void addVoteListener(VoteListener listener);

    void removeVoteListener(VoteListener listener);

    int votePartyProgress();

    int votePartyGoal();

    int streakOf(java.util.UUID player);

    static MCVoteAPI get() {
        return MCVoteProvider.get();
    }
}
