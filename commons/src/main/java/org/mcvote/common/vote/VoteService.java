package org.mcvote.common.vote;

import org.mcvote.api.Vote;
import org.mcvote.common.config.AntiAbuseConfig;
import org.mcvote.common.config.PartyConfig;
import org.mcvote.common.config.StreakConfig;
import org.mcvote.common.config.StreakTier;
import org.mcvote.common.platform.Broadcaster;
import org.mcvote.common.platform.OnlinePlayers;
import org.mcvote.common.platform.PlayerRef;
import org.mcvote.common.platform.UnifiedLogger;
import org.mcvote.common.storage.VoteStorage;
import org.mcvote.common.storage.model.DeliveryType;
import org.mcvote.common.storage.model.PartyTick;
import org.mcvote.common.storage.model.PlayerVoteData;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

public final class VoteService {

    private final VoteStorage storage;
    private final StreakConfig streakConfig;
    private final PartyConfig partyConfig;
    private final AntiAbuseConfig antiAbuse;
    private final OnlinePlayers onlinePlayers;
    private final Broadcaster broadcaster;
    private final UnifiedLogger logger;

    public VoteService(VoteStorage storage, StreakConfig streakConfig, PartyConfig partyConfig,
                       AntiAbuseConfig antiAbuse, OnlinePlayers onlinePlayers, Broadcaster broadcaster,
                       UnifiedLogger logger) {
        this.storage = storage;
        this.streakConfig = streakConfig;
        this.partyConfig = partyConfig;
        this.antiAbuse = antiAbuse;
        this.onlinePlayers = onlinePlayers;
        this.broadcaster = broadcaster;
        this.logger = logger;
    }

    public VoteProcessResult process(Vote vote) {
        String username = vote.username().toLowerCase(Locale.ROOT);
        long serverNow = System.currentTimeMillis();

        if (antiAbuse.enabled()) {
            String reason = abuseReason(username, vote.serviceName(), serverNow);

            if (reason != null) {
                logger.warn("Vote from " + vote.username() + " via " + vote.serviceName()
                        + " rejected by anti-abuse (" + reason + ")");
                return VoteProcessResult.rejected(username);
            }

            storage.recordVoteHistory(username, vote.serviceName(), serverNow);
        }

        int today = (int) LocalDate.ofInstant(Instant.ofEpochMilli(serverNow), streakConfig.zone()).toEpochDay();

        PlayerVoteData previous = storage.load(username);
        int previousDay = previous == null ? 0 : previous.lastVoteDay();
        int previousStreak = previous == null ? 0 : previous.streak();
        int bestStreak = previous == null ? 0 : previous.bestStreak();
        int total = previous == null ? 0 : previous.totalVotes();
        String uuid = previous == null ? null : previous.uuid();

        boolean advancedDay = today != previousDay;
        int streak = resolveStreak(previousStreak, previousDay, today);
        bestStreak = Math.max(bestStreak, streak);

        storage.save(new PlayerVoteData(username, vote.username(), uuid, streak, bestStreak, total + 1, today, serverNow));

        storage.enqueue(username, DeliveryType.VOTE, vote.serviceName(), serverNow);

        String milestoneId = resolveMilestone(streak, advancedDay);

        if (milestoneId != null) {
            storage.enqueue(username, DeliveryType.STREAK, milestoneId, serverNow);
        }

        boolean partyTriggered = false;
        int partyProgress = 0;

        if (partyConfig.enabled() && partyConfig.goal() > 0) {
            PartyTick tick = storage.addPartyProgress(1, partyConfig.goal());
            partyProgress = tick.progress();
            partyTriggered = tick.triggered() > 0;

            if (partyTriggered) {
                fireParty(tick.triggered(), serverNow);
            }
        }

        logger.info("Vote from " + vote.username() + " via " + vote.serviceName()
                + " (streak " + streak + (milestoneId != null ? ", milestone " + milestoneId : "") + ")");

        return new VoteProcessResult(true, username, streak, milestoneId, partyTriggered, partyProgress);
    }

    private String abuseReason(String username, String service, long serverNow) {
        if (antiAbuse.cooldownMs() > 0) {
            long last = storage.lastServiceVoteMs(username, service);

            if (last > 0 && serverNow - last < antiAbuse.cooldownMs()) {
                return "cooldown: " + ((antiAbuse.cooldownMs() - (serverNow - last)) / 1000L) + "s left";
            }
        }

        if (antiAbuse.maxDailyVotes() > 0) {
            long dayStart = LocalDate.ofInstant(Instant.ofEpochMilli(serverNow), streakConfig.zone())
                    .atStartOfDay(streakConfig.zone()).toInstant().toEpochMilli();
            int today = storage.countVotesSince(username, dayStart);

            if (today >= antiAbuse.maxDailyVotes()) {
                return "daily cap: " + today + "/" + antiAbuse.maxDailyVotes();
            }
        }

        return null;
    }

    private int resolveStreak(int previousStreak, int previousDay, int today) {
        if (!streakConfig.enabled()) {
            return 0;
        }

        if (previousDay == today) {
            return Math.max(previousStreak, 1);
        }

        if (previousDay == today - 1) {
            return previousStreak + 1;
        }

        return 1;
    }

    private String resolveMilestone(int streak, boolean advancedDay) {
        if (!streakConfig.enabled() || !advancedDay) {
            return null;
        }

        StreakTier tier = streakConfig.tierFor(streak);

        return tier == null ? null : tier.id();
    }

    private void fireParty(int times, long now) {
        List<PlayerRef> online = onlinePlayers.online();

        for (int i = 0; i < times; i++) {
            for (PlayerRef player : online) {
                storage.enqueue(player.name().toLowerCase(Locale.ROOT), DeliveryType.PARTY, "", now);
            }

            if (partyConfig.broadcastMessage() != null && !partyConfig.broadcastMessage().isBlank()) {
                broadcaster.broadcast(partyConfig.broadcastMessage());
            }
        }

        logger.info("VoteParty triggered x" + times + " for " + online.size() + " online players");
    }
}
