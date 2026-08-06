package org.mcvote.api;

@FunctionalInterface
public interface VoteListener {

    void onVote(Vote vote);
}
