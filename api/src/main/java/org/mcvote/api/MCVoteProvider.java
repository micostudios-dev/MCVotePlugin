package org.mcvote.api;

public final class MCVoteProvider {

    private static volatile MCVoteAPI instance;

    private MCVoteProvider() {
    }

    public static MCVoteAPI get() {
        MCVoteAPI api = instance;

        if (api == null) {
            throw new IllegalStateException("MCVote is not enabled yet");
        }

        return api;
    }

    public static void set(MCVoteAPI api) {
        instance = api;
    }
}
