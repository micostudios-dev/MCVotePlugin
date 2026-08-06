package org.mcvote.server.hook;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.mcvote.common.config.StreakTier;
import org.mcvote.common.storage.model.PlayerVoteData;
import org.mcvote.server.cache.PlayerDataCache;
import org.mcvote.server.config.ServerConfig;

import java.util.Locale;

public final class MCVotePlaceholders extends PlaceholderExpansion {

    private final PlayerDataCache cache;
    private final ServerConfig config;
    private final String version;

    public MCVotePlaceholders(PlayerDataCache cache, ServerConfig config, String version) {
        this.cache = cache;
        this.config = config;
        this.version = version;
    }

    @Override
    public String getIdentifier() {
        return "mcvote";
    }

    @Override
    public String getAuthor() {
        return "mcvote";
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        PlayerVoteData data = player == null ? null : cache.get(player.getName());
        int streak = data == null ? 0 : data.streak();
        int goal = config.party().goal();
        StreakTier next = config.streak().nextTier(streak);

        return switch (params.toLowerCase(Locale.ROOT)) {
            case "streak" -> String.valueOf(streak);
            case "best_streak" -> String.valueOf(data == null ? 0 : data.bestStreak());
            case "votes" -> String.valueOf(data == null ? 0 : data.totalVotes());
            case "party_progress" -> String.valueOf(cache.partyProgress());
            case "party_goal" -> String.valueOf(goal);
            case "party_remaining" -> String.valueOf(Math.max(0, goal - cache.partyProgress()));
            case "next_tier" -> next == null ? "-" : next.id();
            case "next_tier_required" -> String.valueOf(next == null ? 0 : next.required());
            case "next_tier_in" -> String.valueOf(next == null ? 0 : Math.max(0, next.required() - streak));
            default -> null;
        };
    }
}
