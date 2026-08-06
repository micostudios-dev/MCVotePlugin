package org.mcvote.common.sync;

public final class SyncProtocol {

    public static final String CHANNEL = "mcvote:sync";

    public static final long TIMEOUT_MS = 3_000L;

    public static final String OP_LOAD = "load";
    public static final String OP_SAVE = "save";
    public static final String OP_LINK_IDENTITY = "linkIdentity";
    public static final String OP_ENQUEUE = "enqueue";
    public static final String OP_LAST_SERVICE_VOTE = "lastServiceVote";
    public static final String OP_COUNT_VOTES_SINCE = "countVotesSince";
    public static final String OP_RECORD_HISTORY = "recordHistory";
    public static final String OP_CLAIM = "claim";
    public static final String OP_ADD_PARTY = "addParty";
    public static final String OP_PARTY_PROGRESS = "partyProgress";
    public static final String OP_TOP = "top";

    private SyncProtocol() {
    }
}
