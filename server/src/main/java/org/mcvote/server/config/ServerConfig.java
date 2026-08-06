package org.mcvote.server.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.mcvote.common.config.AntiAbuseConfig;
import org.mcvote.common.config.DatabaseConfig;
import org.mcvote.common.config.PartyConfig;
import org.mcvote.common.config.ReceiverConfig;
import org.mcvote.common.config.StorageType;
import org.mcvote.common.config.StreakConfig;
import org.mcvote.common.config.StreakTier;
import org.mcvote.server.reward.RewardBundle;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ServerConfig {

    private final DatabaseConfig database;
    private final ReceiverConfig receiver;
    private final StreakConfig streak;
    private final PartyConfig party;
    private final AntiAbuseConfig antiAbuse;
    private final long deliveryPollMs;
    private final Messages messages;
    private final RewardBundle voteReward;
    private final RewardBundle partyReward;
    private final Map<String, RewardBundle> streakRewards;
    private final List<String> voteLinks;

    private ServerConfig(FileConfiguration cfg, Messages messages) {
        this.database = new DatabaseConfig(
                StorageType.from(cfg.getString("database.type", "sqlite")),
                cfg.getString("database.file", "database.db"),
                cfg.getString("database.host", "localhost"),
                cfg.getInt("database.port", 3306),
                cfg.getString("database.name", "mcvote"),
                cfg.getString("database.user", "root"),
                cfg.getString("database.password", ""),
                cfg.getInt("database.pool-size", 6));

        this.receiver = new ReceiverConfig(
                cfg.getBoolean("receiver.enabled", false),
                cfg.getString("receiver.host", "0.0.0.0"),
                cfg.getInt("receiver.port", 8192),
                readTokens(cfg.getConfigurationSection("receiver.tokens")),
                cfg.getString("receiver.api-key", ""),
                cfg.getLong("receiver.replay-window-seconds",
                        cfg.getLong("receiver.freshness-seconds", 300)) * 1000L);

        this.streak = new StreakConfig(
                cfg.getBoolean("streaks.enabled", true),
                zone(cfg.getString("streaks.timezone", "UTC")),
                readTiers(cfg.getConfigurationSection("streaks.tiers")));

        this.party = new PartyConfig(
                cfg.getBoolean("voteparty.enabled", true),
                cfg.getInt("voteparty.goal", 50),
                cfg.getString("voteparty.broadcast", ""));

        this.antiAbuse = new AntiAbuseConfig(
                cfg.getBoolean("anti-abuse.enabled", true),
                cfg.getLong("anti-abuse.cooldown-seconds", 21600) * 1000L,
                cfg.getInt("anti-abuse.max-daily-votes", 16));

        this.deliveryPollMs = cfg.getLong("delivery.poll-seconds", 5) * 1000L;

        this.messages = messages;

        this.voteReward = RewardBundle.parse(cfg.getStringList("rewards.vote"));
        this.partyReward = RewardBundle.parse(cfg.getStringList("rewards.party"));
        this.streakRewards = readStreakRewards(cfg.getConfigurationSection("rewards.streak"));
        this.voteLinks = cfg.getStringList("vote-links");
    }

    public static ServerConfig load(FileConfiguration cfg, Messages messages) {
        return new ServerConfig(cfg, messages);
    }

    public static Messages messagesFrom(FileConfiguration langCfg) {
        Map<String, String> values = readStringMap(langCfg);
        values.remove("prefix");

        return new Messages(langCfg.getString("prefix", ""), values, readListMap(langCfg));
    }

    private static List<StreakTier> readTiers(ConfigurationSection section) {
        List<StreakTier> tiers = new ArrayList<>();

        if (section != null) {
            for (String id : section.getKeys(false)) {
                tiers.add(new StreakTier(id, section.getInt(id)));
            }
        }

        tiers.sort((a, b) -> Integer.compare(a.required(), b.required()));

        return tiers;
    }

    private static Map<String, String> readTokens(ConfigurationSection section) {
        Map<String, String> tokens = new LinkedHashMap<>();

        if (section != null) {
            for (String service : section.getKeys(false)) {
                tokens.put(service, section.getString(service, ""));
            }
        }

        return tokens;
    }

    private static Map<String, RewardBundle> readStreakRewards(ConfigurationSection section) {
        Map<String, RewardBundle> rewards = new LinkedHashMap<>();

        if (section != null) {
            for (String id : section.getKeys(false)) {
                rewards.put(id, RewardBundle.parse(section.getStringList(id)));
            }
        }

        return rewards;
    }

    private static Map<String, String> readStringMap(ConfigurationSection section) {
        Map<String, String> values = new LinkedHashMap<>();

        if (section != null) {
            for (String key : section.getKeys(false)) {
                if (section.isString(key)) {
                    values.put(key, section.getString(key));
                }
            }
        }

        return values;
    }

    private static Map<String, List<String>> readListMap(ConfigurationSection section) {
        Map<String, List<String>> lists = new LinkedHashMap<>();

        if (section != null) {
            for (String key : section.getKeys(false)) {
                if (section.isList(key)) {
                    lists.put(key, section.getStringList(key));
                }
            }
        }

        return lists;
    }

    private static ZoneId zone(String id) {
        try {
            return ZoneId.of(id);
        } catch (Exception e) {
            return ZoneId.of("UTC");
        }
    }

    public DatabaseConfig database() {
        return database;
    }

    public ReceiverConfig receiver() {
        return receiver;
    }

    public StreakConfig streak() {
        return streak;
    }

    public PartyConfig party() {
        return party;
    }

    public AntiAbuseConfig antiAbuse() {
        return antiAbuse;
    }

    public long deliveryPollMs() {
        return deliveryPollMs;
    }

    public Messages messages() {
        return messages;
    }

    public RewardBundle voteReward() {
        return voteReward;
    }

    public RewardBundle partyReward() {
        return partyReward;
    }

    public RewardBundle streakReward(String tierId) {
        return streakRewards.getOrDefault(tierId, RewardBundle.EMPTY);
    }

    public List<String> voteLinks() {
        return voteLinks;
    }

    public String rewardSummary() {
        StringBuilder summary = new StringBuilder("vote ")
                .append(voteReward.actions().size())
                .append(", party ")
                .append(partyReward.actions().size());
        for (Map.Entry<String, RewardBundle> entry : streakRewards.entrySet()) {
            summary.append(", streak.").append(entry.getKey())
                    .append(' ').append(entry.getValue().actions().size());
        }

        return summary.toString();
    }
}
