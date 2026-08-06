package org.mcvote.common.vote;

import org.junit.jupiter.api.Test;
import org.mcvote.api.Vote;
import org.mcvote.common.config.AntiAbuseConfig;
import org.mcvote.common.config.PartyConfig;
import org.mcvote.common.config.StreakConfig;
import org.mcvote.common.platform.UnifiedLogger;
import org.mcvote.common.storage.VoteStorage;
import org.mcvote.common.storage.model.DeliveryType;
import org.mcvote.common.storage.model.PartyTick;
import org.mcvote.common.storage.model.PendingDelivery;
import org.mcvote.common.storage.model.PlayerVoteData;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class VoteServiceTest {

    private static final StreakConfig STREAK =
            new StreakConfig(true, ZoneId.of("UTC"), List.of());
    private static final PartyConfig NO_PARTY = new PartyConfig(false, 0, "");

    private VoteService service(InMemoryStorage storage, AntiAbuseConfig antiAbuse) {
        return new VoteService(storage, STREAK, NO_PARTY, antiAbuse,
                List::of, message -> {
        }, NO_LOG);
    }

    @Test
    void cooldownBlocksASecondVoteFromTheSamePlayerAndService() {
        InMemoryStorage storage = new InMemoryStorage();
        VoteService service = service(storage, new AntiAbuseConfig(true, 72_000_000L, 0));

        assertTrue(service.process(vote("Steve", "MCVote", System.currentTimeMillis())).accepted());
        assertFalse(service.process(vote("Steve", "MCVote", System.currentTimeMillis())).accepted());
        assertEquals(1, storage.history.size());
    }

    @Test
    void forgedTimestampCannotBypassTheCooldown() {
        InMemoryStorage storage = new InMemoryStorage();
        VoteService service = service(storage, new AntiAbuseConfig(true, 72_000_000L, 0));

        assertTrue(service.process(vote("Steve", "MCVote", System.currentTimeMillis())).accepted());
        assertFalse(service.process(vote("Steve", "MCVote", 0L)).accepted());
    }

    @Test
    void elapsedCooldownLetsTheNextVoteThrough() {
        InMemoryStorage storage = new InMemoryStorage();
        VoteService service = service(storage, new AntiAbuseConfig(true, 1_000L, 0));

        assertTrue(service.process(vote("Steve", "MCVote", System.currentTimeMillis())).accepted());
        storage.history.get(0).votedMs = System.currentTimeMillis() - 10_000L;
        assertTrue(service.process(vote("Steve", "MCVote", System.currentTimeMillis())).accepted());
    }

    @Test
    void dailyCapClosesTheServiceRotationTrick() {
        InMemoryStorage storage = new InMemoryStorage();
        VoteService service = service(storage, new AntiAbuseConfig(true, 0L, 2));

        assertTrue(service.process(vote("Steve", "SiteA", System.currentTimeMillis())).accepted());
        assertTrue(service.process(vote("Steve", "SiteB", System.currentTimeMillis())).accepted());
        assertFalse(service.process(vote("Steve", "SiteC", System.currentTimeMillis())).accepted());
    }

    @Test
    void disabledAntiAbuseNeverRejects() {
        InMemoryStorage storage = new InMemoryStorage();
        VoteService service = service(storage, AntiAbuseConfig.DISABLED);

        assertTrue(service.process(vote("Steve", "MCVote", System.currentTimeMillis())).accepted());
        assertTrue(service.process(vote("Steve", "MCVote", System.currentTimeMillis())).accepted());
        assertTrue(storage.history.isEmpty(), "history is only written when anti-abuse is enabled");
    }

    private static Vote vote(String user, String service, long timestamp) {
        return new Vote(service, user, "", timestamp);
    }

    private static final UnifiedLogger NO_LOG = new UnifiedLogger() {
        @Override
        public void info(String message) {
        }

        @Override
        public void warn(String message) {
        }

        @Override
        public void error(String message, Throwable throwable) {
        }
    };

    private static final class InMemoryStorage implements VoteStorage {

        private static final class Row {
            final String username;
            final String service;
            long votedMs;

            Row(String username, String service, long votedMs) {
                this.username = username;
                this.service = service;
                this.votedMs = votedMs;
            }
        }

        private final List<Row> history = new ArrayList<>();
        private PlayerVoteData saved;

        @Override
        public long lastServiceVoteMs(String username, String service) {
            long max = 0L;
            for (Row row : history) {
                if (row.username.equals(username) && row.service.equals(service)) {
                    max = Math.max(max, row.votedMs);
                }
            }
            return max;
        }

        @Override
        public int countVotesSince(String username, long sinceMs) {
            int count = 0;
            for (Row row : history) {
                if (row.username.equals(username) && row.votedMs >= sinceMs) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public void recordVoteHistory(String username, String service, long votedMs) {
            history.add(new Row(username, service, votedMs));
        }

        @Override
        public PlayerVoteData load(String username) {
            return saved;
        }

        @Override
        public void save(PlayerVoteData data) {
            this.saved = data;
        }

        @Override
        public void init() {
        }

        @Override
        public void close() {
        }

        @Override
        public void linkIdentity(String username, String uuid, String displayName) {
        }

        @Override
        public void enqueue(String username, DeliveryType type, String context, long createdMs) {
        }

        @Override
        public List<PendingDelivery> claim(Collection<String> usernames) {
            return List.of();
        }

        @Override
        public PartyTick addPartyProgress(int amount, int goal) {
            return new PartyTick(0, 0, 0);
        }

        @Override
        public int partyProgress() {
            return 0;
        }

        @Override
        public List<PlayerVoteData> topByVotes(int limit) {
            return List.of();
        }
    }
}
