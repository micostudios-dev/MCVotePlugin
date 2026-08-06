package org.mcvote.server.cache;

import org.mcvote.common.storage.model.PlayerVoteData;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerDataCache {

    private final ConcurrentHashMap<String, PlayerVoteData> players = new ConcurrentHashMap<>();
    private volatile int partyProgress;

    public void put(PlayerVoteData data) {
        if (data != null && data.username() != null) {
            players.put(data.username().toLowerCase(Locale.ROOT), data);
        }
    }

    public PlayerVoteData get(String username) {
        return username == null ? null : players.get(username.toLowerCase(Locale.ROOT));
    }

    public void remove(String username) {
        if (username != null) {
            players.remove(username.toLowerCase(Locale.ROOT));
        }
    }

    public void setPartyProgress(int progress) {
        this.partyProgress = progress;
    }

    public int partyProgress() {
        return partyProgress;
    }
}
