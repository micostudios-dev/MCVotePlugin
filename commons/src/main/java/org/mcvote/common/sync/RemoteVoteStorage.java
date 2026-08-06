package org.mcvote.common.sync;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.mcvote.common.platform.UnifiedLogger;
import org.mcvote.common.storage.VoteStorage;
import org.mcvote.common.storage.model.DeliveryType;
import org.mcvote.common.storage.model.PartyTick;
import org.mcvote.common.storage.model.PendingDelivery;
import org.mcvote.common.storage.model.PlayerVoteData;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class RemoteVoteStorage implements VoteStorage {

    private static final long WARN_INTERVAL_MS = 60_000L;

    private final NodeLink link;
    private final UnifiedLogger logger;
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<Long, CompletableFuture<JsonElement>> pending = new ConcurrentHashMap<>();

    private volatile long lastWarnMs;

    public RemoteVoteStorage(NodeLink link, UnifiedLogger logger) {
        this.link = link;
        this.logger = logger;
    }

    @Override
    public void init() {
        logger.info("Storage: proxy link (no local database)");
    }

    @Override
    public void close() {
        pending.values().forEach(future -> future.complete(null));
        pending.clear();
    }

    public void handleResponse(byte[] frame) {
        try {
            JsonObject json = JsonParser.parseString(new String(frame, StandardCharsets.UTF_8)).getAsJsonObject();
            long id = json.get("id").getAsLong();

            CompletableFuture<JsonElement> future = pending.remove(id);

            if (future == null) {
                return;
            }

            if (json.has("error")) {
                logger.warn("Proxy rejected a storage call: " + json.get("error").getAsString());
                future.complete(null);
            } else {
                future.complete(json.get("result"));
            }
        } catch (Exception e) {
            logger.warn("Malformed frame from the proxy: " + e.getMessage());
        }
    }

    @Override
    public PlayerVoteData load(String username) {
        JsonObject args = new JsonObject();
        args.addProperty("username", username);

        return SyncCodec.readPlayer(call(SyncProtocol.OP_LOAD, args));
    }

    @Override
    public long lastServiceVoteMs(String username, String service) {
        JsonObject args = new JsonObject();
        args.addProperty("username", username);
        args.addProperty("service", service);
        JsonElement result = call(SyncProtocol.OP_LAST_SERVICE_VOTE, args);

        return result == null || result.isJsonNull() ? 0L : result.getAsLong();
    }

    @Override
    public int countVotesSince(String username, long sinceMs) {
        JsonObject args = new JsonObject();
        args.addProperty("username", username);
        args.addProperty("sinceMs", sinceMs);
        JsonElement result = call(SyncProtocol.OP_COUNT_VOTES_SINCE, args);

        return result == null || result.isJsonNull() ? 0 : result.getAsInt();
    }

    @Override
    public List<PendingDelivery> claim(Collection<String> usernames) {
        if (usernames.isEmpty()) {
            return List.of();
        }

        JsonObject args = new JsonObject();
        args.add("usernames", SyncCodec.writeStrings(usernames));

        return SyncCodec.readDeliveries(call(SyncProtocol.OP_CLAIM, args));
    }

    @Override
    public PartyTick addPartyProgress(int amount, int goal) {
        JsonObject args = new JsonObject();
        args.addProperty("amount", amount);
        args.addProperty("goal", goal);
        JsonElement result = call(SyncProtocol.OP_ADD_PARTY, args);

        return result == null || result.isJsonNull() ? new PartyTick(0, 0, 0L) : SyncCodec.readPartyTick(result);
    }

    @Override
    public int partyProgress() {
        JsonElement result = call(SyncProtocol.OP_PARTY_PROGRESS, new JsonObject());

        return result == null || result.isJsonNull() ? 0 : result.getAsInt();
    }

    @Override
    public List<PlayerVoteData> topByVotes(int limit) {
        JsonObject args = new JsonObject();
        args.addProperty("limit", limit);

        return SyncCodec.readPlayers(call(SyncProtocol.OP_TOP, args));
    }

    @Override
    public void save(PlayerVoteData data) {
        JsonObject args = new JsonObject();
        args.add("data", SyncCodec.write(data));
        fire(SyncProtocol.OP_SAVE, args);
    }

    @Override
    public void linkIdentity(String username, String uuid, String displayName) {
        JsonObject args = new JsonObject();
        args.addProperty("username", username);
        args.addProperty("uuid", uuid);
        args.addProperty("displayName", displayName);
        fire(SyncProtocol.OP_LINK_IDENTITY, args);
    }

    @Override
    public void enqueue(String username, DeliveryType type, String context, long createdMs) {
        JsonObject args = new JsonObject();
        args.addProperty("username", username);
        args.addProperty("type", type.name());
        args.addProperty("context", context);
        args.addProperty("createdMs", createdMs);
        fire(SyncProtocol.OP_ENQUEUE, args);
    }

    @Override
    public void recordVoteHistory(String username, String service, long votedMs) {
        JsonObject args = new JsonObject();
        args.addProperty("username", username);
        args.addProperty("service", service);
        args.addProperty("votedMs", votedMs);
        fire(SyncProtocol.OP_RECORD_HISTORY, args);
    }

    private JsonElement call(String op, JsonObject args) {
        long id = random.nextLong();
        CompletableFuture<JsonElement> future = new CompletableFuture<>();
        pending.put(id, future);

        if (!link.send(frame(id, op, args, true))) {
            pending.remove(id);
            warn("no player online to reach the proxy through");

            return null;
        }

        try {
            return future.get(SyncProtocol.TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            pending.remove(id);
            warn("the proxy did not answer '" + op + "' in " + SyncProtocol.TIMEOUT_MS + "ms");

            return null;
        }
    }

    private void fire(String op, JsonObject args) {
        if (!link.send(frame(random.nextLong(), op, args, false))) {
            warn("no player online to reach the proxy through, '" + op + "' was dropped");
        }
    }

    private static byte[] frame(long id, String op, JsonObject args, boolean ack) {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("op", op);
        json.add("args", args);

        if (ack) {
            json.addProperty("ack", true);
        }

        return json.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void warn(String reason) {
        long now = System.currentTimeMillis();

        if (now - lastWarnMs < WARN_INTERVAL_MS) {
            return;
        }

        lastWarnMs = now;
        logger.warn("Proxy link unavailable (" + reason + "). Rewards stay queued on the proxy.");
    }
}
