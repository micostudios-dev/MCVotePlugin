package org.mcvote.common.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.mcvote.common.storage.model.DeliveryType;
import org.mcvote.common.storage.model.PartyTick;
import org.mcvote.common.storage.model.PendingDelivery;
import org.mcvote.common.storage.model.PlayerVoteData;

import java.util.ArrayList;
import java.util.List;

final class SyncCodec {

    private SyncCodec() {
    }

    static JsonElement write(PlayerVoteData data) {
        if (data == null) {
            return JsonNull.INSTANCE;
        }
        JsonObject json = new JsonObject();
        json.addProperty("username", data.username());
        json.addProperty("displayName", data.displayName());
        json.addProperty("uuid", data.uuid());
        json.addProperty("streak", data.streak());
        json.addProperty("bestStreak", data.bestStreak());
        json.addProperty("totalVotes", data.totalVotes());
        json.addProperty("lastVoteDay", data.lastVoteDay());
        json.addProperty("lastVoteMs", data.lastVoteMs());
        return json;
    }

    static PlayerVoteData readPlayer(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        JsonObject json = element.getAsJsonObject();
        return new PlayerVoteData(
                string(json, "username"),
                string(json, "displayName"),
                string(json, "uuid"),
                json.get("streak").getAsInt(),
                json.get("bestStreak").getAsInt(),
                json.get("totalVotes").getAsInt(),
                json.get("lastVoteDay").getAsInt(),
                json.get("lastVoteMs").getAsLong());
    }

    static JsonElement write(PendingDelivery delivery) {
        JsonObject json = new JsonObject();
        json.addProperty("id", delivery.id());
        json.addProperty("username", delivery.username());
        json.addProperty("type", delivery.type().name());
        json.addProperty("context", delivery.context());
        json.addProperty("createdMs", delivery.createdMs());
        return json;
    }

    static PendingDelivery readDelivery(JsonElement element) {
        JsonObject json = element.getAsJsonObject();
        return new PendingDelivery(
                json.get("id").getAsLong(),
                string(json, "username"),
                DeliveryType.from(string(json, "type")),
                string(json, "context"),
                json.get("createdMs").getAsLong());
    }

    static JsonElement write(PartyTick tick) {
        JsonObject json = new JsonObject();
        json.addProperty("progress", tick.progress());
        json.addProperty("triggered", tick.triggered());
        json.addProperty("totalParties", tick.totalParties());
        return json;
    }

    static PartyTick readPartyTick(JsonElement element) {
        JsonObject json = element.getAsJsonObject();
        return new PartyTick(
                json.get("progress").getAsInt(),
                json.get("triggered").getAsInt(),
                json.get("totalParties").getAsLong());
    }

    static JsonArray writeDeliveries(List<PendingDelivery> deliveries) {
        JsonArray array = new JsonArray();
        deliveries.forEach(delivery -> array.add(write(delivery)));
        return array;
    }

    static List<PendingDelivery> readDeliveries(JsonElement element) {
        List<PendingDelivery> deliveries = new ArrayList<>();
        if (element != null && element.isJsonArray()) {
            element.getAsJsonArray().forEach(item -> deliveries.add(readDelivery(item)));
        }
        return deliveries;
    }

    static JsonArray writePlayers(List<PlayerVoteData> players) {
        JsonArray array = new JsonArray();
        players.forEach(player -> array.add(write(player)));
        return array;
    }

    static List<PlayerVoteData> readPlayers(JsonElement element) {
        List<PlayerVoteData> players = new ArrayList<>();
        if (element != null && element.isJsonArray()) {
            element.getAsJsonArray().forEach(item -> players.add(readPlayer(item)));
        }
        return players;
    }

    static JsonArray writeStrings(Iterable<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    static List<String> readStrings(JsonElement element) {
        List<String> values = new ArrayList<>();
        if (element != null && element.isJsonArray()) {
            element.getAsJsonArray().forEach(item -> values.add(item.getAsString()));
        }
        return values;
    }

    static String string(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : null;
    }
}
