package org.mcvote.common.sync;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.mcvote.common.platform.UnifiedLogger;
import org.mcvote.common.storage.VoteStorage;
import org.mcvote.common.storage.model.DeliveryType;

import java.nio.charset.StandardCharsets;

public final class SyncRequestHandler {

    private final VoteStorage storage;
    private final UnifiedLogger logger;

    public SyncRequestHandler(VoteStorage storage, UnifiedLogger logger) {
        this.storage = storage;
        this.logger = logger;
    }

    public byte[] handle(byte[] frame) {
        long id = 0L;
        try {
            JsonObject request = JsonParser.parseString(new String(frame, StandardCharsets.UTF_8)).getAsJsonObject();
            id = request.get("id").getAsLong();
            String op = request.get("op").getAsString();
            JsonObject args = request.getAsJsonObject("args");
            boolean ack = request.has("ack") && request.get("ack").getAsBoolean();

            JsonElement result = execute(op, args);
            if (!ack) {
                return null;
            }

            JsonObject response = new JsonObject();
            response.addProperty("id", id);
            response.add("result", result);
            return response.toString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.warn("Failed to serve a backend storage call: " + e.getMessage());

            JsonObject response = new JsonObject();
            response.addProperty("id", id);
            response.addProperty("error", String.valueOf(e.getMessage()));
            return response.toString().getBytes(StandardCharsets.UTF_8);
        }
    }

    private JsonElement execute(String op, JsonObject args) {
        return switch (op) {
            case SyncProtocol.OP_LOAD ->
                    SyncCodec.write(storage.load(SyncCodec.string(args, "username")));

            case SyncProtocol.OP_LAST_SERVICE_VOTE -> new JsonPrimitive(
                    storage.lastServiceVoteMs(SyncCodec.string(args, "username"), SyncCodec.string(args, "service")));

            case SyncProtocol.OP_COUNT_VOTES_SINCE -> new JsonPrimitive(
                    storage.countVotesSince(SyncCodec.string(args, "username"), args.get("sinceMs").getAsLong()));

            case SyncProtocol.OP_CLAIM ->
                    SyncCodec.writeDeliveries(storage.claim(SyncCodec.readStrings(args.get("usernames"))));

            case SyncProtocol.OP_ADD_PARTY -> SyncCodec.write(
                    storage.addPartyProgress(args.get("amount").getAsInt(), args.get("goal").getAsInt()));

            case SyncProtocol.OP_PARTY_PROGRESS ->
                    new JsonPrimitive(storage.partyProgress());

            case SyncProtocol.OP_TOP ->
                    SyncCodec.writePlayers(storage.topByVotes(args.get("limit").getAsInt()));

            case SyncProtocol.OP_SAVE -> {
                storage.save(SyncCodec.readPlayer(args.get("data")));
                yield JsonNull.INSTANCE;
            }

            case SyncProtocol.OP_LINK_IDENTITY -> {
                storage.linkIdentity(SyncCodec.string(args, "username"), SyncCodec.string(args, "uuid"),
                        SyncCodec.string(args, "displayName"));
                yield JsonNull.INSTANCE;
            }

            case SyncProtocol.OP_ENQUEUE -> {
                storage.enqueue(SyncCodec.string(args, "username"),
                        DeliveryType.from(SyncCodec.string(args, "type")),
                        SyncCodec.string(args, "context"),
                        args.get("createdMs").getAsLong());
                yield JsonNull.INSTANCE;
            }

            case SyncProtocol.OP_RECORD_HISTORY -> {
                storage.recordVoteHistory(SyncCodec.string(args, "username"), SyncCodec.string(args, "service"),
                        args.get("votedMs").getAsLong());
                yield JsonNull.INSTANCE;
            }

            default -> throw new IllegalArgumentException("Unknown op " + op);
        };
    }
}
