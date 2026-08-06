package org.mcvote.common.storage;

import org.mcvote.common.storage.model.DeliveryType;
import org.mcvote.common.storage.model.PartyTick;
import org.mcvote.common.storage.model.PendingDelivery;
import org.mcvote.common.storage.model.PlayerVoteData;

import java.util.Collection;
import java.util.List;

public interface VoteStorage {

    void init() throws Exception;

    void close();

    PlayerVoteData load(String username);

    void save(PlayerVoteData data);

    void linkIdentity(String username, String uuid, String displayName);

    void enqueue(String username, DeliveryType type, String context, long createdMs);

    long lastServiceVoteMs(String username, String service);

    int countVotesSince(String username, long sinceMs);

    void recordVoteHistory(String username, String service, long votedMs);

    List<PendingDelivery> claim(Collection<String> usernames);

    PartyTick addPartyProgress(int amount, int goal);

    int partyProgress();

    List<PlayerVoteData> topByVotes(int limit);
}
