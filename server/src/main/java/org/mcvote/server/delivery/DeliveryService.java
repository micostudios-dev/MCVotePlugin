package org.mcvote.server.delivery;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.mcvote.common.storage.VoteStorage;
import org.mcvote.common.storage.model.PendingDelivery;
import org.mcvote.common.storage.model.PlayerVoteData;
import org.mcvote.server.cache.PlayerDataCache;
import org.mcvote.server.config.ServerConfig;
import org.mcvote.server.platform.Cancellable;
import org.mcvote.server.platform.MessageService;
import org.mcvote.server.platform.Scheduler;
import org.mcvote.server.reward.RewardBundle;
import org.mcvote.server.reward.RewardExecutor;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DeliveryService {

    private final VoteStorage storage;
    private final ServerConfig config;
    private final RewardExecutor executor;
    private final MessageService messages;
    private final Scheduler scheduler;
    private final PlayerDataCache cache;

    private Cancellable pollTask;

    public DeliveryService(VoteStorage storage, ServerConfig config, RewardExecutor executor,
                           MessageService messages, Scheduler scheduler, PlayerDataCache cache) {
        this.storage = storage;
        this.config = config;
        this.executor = executor;
        this.messages = messages;
        this.scheduler = scheduler;
        this.cache = cache;
    }

    public void start() {
        long period = Math.max(1000L, config.deliveryPollMs());
        this.pollTask = scheduler.runAsyncTimer(this::poll, period, period);
    }

    public void stop() {
        if (pollTask != null) {
            pollTask.cancel();
        }
    }

    public void deliver(Player player) {
        String name = player.getName().toLowerCase(Locale.ROOT);
        scheduler.runAsync(() -> {
            List<PendingDelivery> deliveries = storage.claim(List.of(name));
            dispatch(deliveries);
        });
    }

    private void poll() {
        Map<String, Player> byName = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            byName.put(player.getName().toLowerCase(Locale.ROOT), player);
        }
        if (byName.isEmpty()) {
            return;
        }

        cache.setPartyProgress(storage.partyProgress());

        List<PendingDelivery> deliveries = storage.claim(byName.keySet());
        dispatchTo(deliveries, byName);
    }

    private void dispatch(List<PendingDelivery> deliveries) {
        Map<String, Player> byName = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            byName.put(player.getName().toLowerCase(Locale.ROOT), player);
        }
        dispatchTo(deliveries, byName);
    }

    private void dispatchTo(List<PendingDelivery> deliveries, Map<String, Player> byName) {
        for (PendingDelivery delivery : deliveries) {
            Player player = byName.get(delivery.username());
            if (player == null) {
                storage.enqueue(delivery.username(), delivery.type(), delivery.context(), delivery.createdMs());
                continue;
            }
            execute(player, delivery);
        }
    }

    private void execute(Player player, PendingDelivery delivery) {
        RewardBundle bundle = switch (delivery.type()) {
            case VOTE -> config.voteReward();
            case STREAK -> config.streakReward(delivery.context());
            case PARTY -> config.partyReward();
        };

        Map<String, String> placeholders = placeholders(delivery.username());
        executor.run(player, bundle, placeholders);

        String message = switch (delivery.type()) {
            case VOTE -> config.messages().get("vote-received");
            case STREAK -> config.messages().get("streak-milestone");
            case PARTY -> config.messages().get("party-reward");
        };
        if (!message.isEmpty()) {
            messages.send(player, applyAll(message, player.getName(), placeholders));
        }
    }

    private Map<String, String> placeholders(String username) {
        PlayerVoteData data = storage.load(username);
        cache.put(data);
        int streak = data == null ? 0 : data.streak();
        int goal = config.party().goal();
        int progress = storage.partyProgress();
        org.mcvote.common.config.StreakTier next = config.streak().nextTier(streak);

        Map<String, String> map = new HashMap<>();
        map.put("%streak%", String.valueOf(streak));
        map.put("%best_streak%", data == null ? "0" : String.valueOf(data.bestStreak()));
        map.put("%votes%", data == null ? "0" : String.valueOf(data.totalVotes()));
        map.put("%party_progress%", String.valueOf(progress));
        map.put("%party_goal%", String.valueOf(goal));
        map.put("%party_remaining%", String.valueOf(Math.max(0, goal - progress)));
        map.put("%next_tier%", next == null ? "-" : next.id());
        map.put("%next_tier_required%", String.valueOf(next == null ? 0 : next.required()));
        map.put("%next_tier_in%", String.valueOf(next == null ? 0 : Math.max(0, next.required() - streak)));
        return map;
    }

    private static String applyAll(String raw, String playerName, Map<String, String> placeholders) {
        String result = raw.replace("%player%", playerName);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }
}
