package org.mcvote.server;

import org.mcvote.api.MCVoteAPI;
import org.mcvote.api.Vote;
import org.mcvote.api.VoteListener;
import org.mcvote.common.storage.model.PlayerVoteData;
import org.mcvote.server.cache.PlayerDataCache;
import org.mcvote.server.config.ServerConfig;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

final class MCVoteApiImpl implements MCVoteAPI {

    private final CopyOnWriteArrayList<VoteListener> listeners = new CopyOnWriteArrayList<>();
    private final PlayerDataCache cache;
    private final ServerConfig config;

    MCVoteApiImpl(PlayerDataCache cache, ServerConfig config) {
        this.cache = cache;
        this.config = config;
    }

    void fire(Vote vote) {
        for (VoteListener listener : listeners) {
            try {
                listener.onVote(vote);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void addVoteListener(VoteListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeVoteListener(VoteListener listener) {
        listeners.remove(listener);
    }

    @Override
    public int votePartyProgress() {
        return cache.partyProgress();
    }

    @Override
    public int votePartyGoal() {
        return config.party().goal();
    }

    @Override
    public int streakOf(UUID player) {
        return 0;
    }

    int streakOf(String username) {
        PlayerVoteData data = cache.get(username.toLowerCase(Locale.ROOT));

        return data == null ? 0 : data.streak();
    }

    List<VoteListener> listeners() {
        return listeners;
    }
}
