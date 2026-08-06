package org.mcvote.common.sync;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mcvote.common.platform.UnifiedLogger;
import org.mcvote.common.storage.VoteStorage;
import org.mcvote.common.storage.model.DeliveryType;
import org.mcvote.common.storage.model.PartyTick;
import org.mcvote.common.storage.model.PendingDelivery;
import org.mcvote.common.storage.model.PlayerVoteData;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class ProxyLinkRoundTripTest {

    private FakeStorage proxyStorage;
    private RemoteVoteStorage backendStorage;
    private ExecutorService proxyThread;
    private volatile boolean linkUp;

    @BeforeEach
    void setUp() {
        proxyStorage = new FakeStorage();
        proxyThread = Executors.newSingleThreadExecutor();
        linkUp = true;

        SyncRequestHandler handler = new SyncRequestHandler(proxyStorage, new SilentLogger());

        NodeLink link = frame -> {
            if (!linkUp) {
                return false;
            }

            proxyThread.execute(() -> {
                byte[] response = handler.handle(frame);

                if (response != null) {
                    backendStorage.handleResponse(response);
                }
            });

            return true;
        };

        backendStorage = new RemoteVoteStorage(link, new SilentLogger());
    }

    @Test
    void readsTravelToTheProxyAndBack() {
        proxyStorage.players.put("notch",
                new PlayerVoteData("notch", "Notch", "uuid-1", 4, 9, 27, 20000, 1234L));

        PlayerVoteData data = backendStorage.load("notch");

        assertNotNull(data);
        assertEquals("Notch", data.displayName());
        assertEquals(4, data.streak());
        assertEquals(9, data.bestStreak());
        assertEquals(27, data.totalVotes());
        assertEquals(20000, data.lastVoteDay());
        assertEquals(1234L, data.lastVoteMs());
    }

    @Test
    void missingPlayerComesBackAsNull() {
        assertNull(backendStorage.load("nobody"));
    }

    @Test
    void writesReachTheProxyWithoutAnAnswer() throws Exception {
        backendStorage.enqueue("notch", DeliveryType.STREAK, "week", 555L);

        proxyThread.shutdown();
        assertTrue(proxyThread.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS));

        assertEquals(1, proxyStorage.queue.size());
        assertEquals(DeliveryType.STREAK, proxyStorage.queue.get(0).type());
        assertEquals("week", proxyStorage.queue.get(0).context());
    }

    @Test
    void claimReturnsTheQueuedDeliveries() {
        proxyStorage.enqueue("notch", DeliveryType.VOTE, "MinecraftMP", 1L);
        proxyStorage.enqueue("notch", DeliveryType.PARTY, "", 2L);

        List<PendingDelivery> claimed = backendStorage.claim(List.of("notch"));

        assertEquals(2, claimed.size());
        assertEquals("MinecraftMP", claimed.get(0).context());
        assertTrue(backendStorage.claim(List.of("notch")).isEmpty());
    }

    @Test
    void partyProgressAndTopSurviveTheRoundTrip() {
        proxyStorage.party = 42;
        assertEquals(42, backendStorage.partyProgress());

        proxyStorage.players.put("a", new PlayerVoteData("a", "A", null, 1, 1, 10, 0, 0L));
        proxyStorage.players.put("b", new PlayerVoteData("b", "B", null, 1, 1, 30, 0, 0L));

        List<PlayerVoteData> top = backendStorage.topByVotes(2);
        assertEquals(2, top.size());
        assertEquals("B", top.get(0).displayName());
    }

    @Test
    void aDownLinkDegradesInsteadOfBlowingUp() {
        linkUp = false;

        assertNull(backendStorage.load("notch"));
        assertTrue(backendStorage.claim(List.of("notch")).isEmpty());
        assertEquals(0, backendStorage.partyProgress());
        backendStorage.enqueue("notch", DeliveryType.VOTE, "x", 1L);
    }

    private static final class FakeStorage implements VoteStorage {

        final Map<String, PlayerVoteData> players = new HashMap<>();
        final List<PendingDelivery> queue = new ArrayList<>();
        int party;
        private long nextId = 1;

        @Override
        public void init() {
        }

        @Override
        public void close() {
        }

        @Override
        public PlayerVoteData load(String username) {
            return players.get(username);
        }

        @Override
        public void save(PlayerVoteData data) {
            players.put(data.username(), data);
        }

        @Override
        public void linkIdentity(String username, String uuid, String displayName) {
        }

        @Override
        public void enqueue(String username, DeliveryType type, String context, long createdMs) {
            queue.add(new PendingDelivery(nextId++, username, type, context, createdMs));
        }

        @Override
        public long lastServiceVoteMs(String username, String service) {
            return 0;
        }

        @Override
        public int countVotesSince(String username, long sinceMs) {
            return 0;
        }

        @Override
        public void recordVoteHistory(String username, String service, long votedMs) {
        }

        @Override
        public List<PendingDelivery> claim(Collection<String> usernames) {
            List<PendingDelivery> claimed = new ArrayList<>();
            queue.removeIf(delivery -> {
                if (usernames.contains(delivery.username())) {
                    claimed.add(delivery);

                    return true;
                }

                return false;
            });

            return claimed;
        }

        @Override
        public PartyTick addPartyProgress(int amount, int goal) {
            party += amount;

            return new PartyTick(party, 0, 0L);
        }

        @Override
        public int partyProgress() {
            return party;
        }

        @Override
        public List<PlayerVoteData> topByVotes(int limit) {
            return players.values().stream()
                    .sorted((a, b) -> Integer.compare(b.totalVotes(), a.totalVotes()))
                    .limit(limit)
                    .toList();
        }
    }

    private static final class SilentLogger implements UnifiedLogger {
        @Override
        public void info(String message) {
        }

        @Override
        public void warn(String message) {
        }

        @Override
        public void error(String message, Throwable throwable) {
        }
    }
}
