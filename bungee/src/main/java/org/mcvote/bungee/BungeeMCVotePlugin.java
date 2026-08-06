package org.mcvote.bungee;

import net.kyori.adventure.platform.bungeecord.BungeeAudiences;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import org.mcvote.common.config.AntiAbuseConfig;
import org.mcvote.common.config.DatabaseConfig;
import org.mcvote.common.config.PartyConfig;
import org.mcvote.common.config.ReceiverConfig;
import org.mcvote.common.config.StorageType;
import org.mcvote.common.config.StreakConfig;
import org.mcvote.common.config.StreakTier;
import org.mcvote.common.net.VoteReceiver;
import org.mcvote.common.platform.Broadcaster;
import org.mcvote.common.platform.OnlinePlayers;
import org.mcvote.common.platform.PlayerRef;
import org.mcvote.common.platform.UnifiedLogger;
import org.mcvote.common.storage.StorageFactory;
import org.mcvote.common.storage.VoteStorage;
import org.mcvote.common.sync.SyncProtocol;
import org.mcvote.common.sync.SyncRequestHandler;
import org.mcvote.common.text.MiniMessageText;
import org.mcvote.common.vote.VoteService;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BungeeMCVotePlugin extends Plugin {

    private UnifiedLogger logger;
    private BungeeAudiences audiences;
    private VoteStorage storage;
    private VoteReceiver receiver;
    private String storageLabel = "unknown";

    @Override
    public void onEnable() {
        this.logger = new BungeeLogger(getLogger());
        this.audiences = BungeeAudiences.create(this);

        Configuration cfg = loadConfig();

        if (cfg == null) {
            return;
        }

        StorageType type = StorageType.from(cfg.getString("database.type", "sqlite"));

        if (type == StorageType.PROXY) {
            logger.error("database.type: proxy is meant for backends. This proxy is the node that owns "
                    + "the state, so use sqlite or mysql here.");
            return;
        }

        this.storageLabel = type.name().toLowerCase(Locale.ROOT);

        DatabaseConfig database = new DatabaseConfig(
                type,
                cfg.getString("database.file", "database.db"),
                cfg.getString("database.host", "localhost"),
                cfg.getInt("database.port", 3306),
                cfg.getString("database.name", "mcvote"),
                cfg.getString("database.user", "root"),
                cfg.getString("database.password", ""),
                cfg.getInt("database.pool-size", 6));

        this.storage = StorageFactory.create(database, getDataFolder());

        try {
            storage.init();
        } catch (Exception e) {
            logger.error("Could not open the vote storage, MCVote will not listen", e);

            return;
        }

        getProxy().registerChannel(SyncProtocol.CHANNEL);
        getProxy().getPluginManager().registerListener(this,
                new BungeeSyncListener(new SyncRequestHandler(storage, logger), logger));

        getProxy().getPluginManager().registerCommand(this, new BungeeVoteCommand(this));

        applyConfig(cfg);
    }

    @Override
    public void onDisable() {
        stopReceiver();

        if (storage != null) {
            storage.close();
        }

        if (audiences != null) {
            audiences.close();
        }
    }

    public BungeeAudiences audiences() {
        return audiences;
    }

    public boolean reload() {
        Configuration cfg = loadConfig();

        if (cfg == null) {
            return false;
        }

        stopReceiver();
        applyConfig(cfg);

        return true;
    }

    private void applyConfig(Configuration cfg) {
        StreakConfig streak = new StreakConfig(
                cfg.getBoolean("streaks.enabled", true),
                zone(cfg.getString("streaks.timezone", "UTC")),
                readTiers(cfg.getSection("streaks.tiers")));
        PartyConfig party = new PartyConfig(
                cfg.getBoolean("voteparty.enabled", true),
                cfg.getInt("voteparty.goal", 50),
                cfg.getString("voteparty.broadcast", ""));
        AntiAbuseConfig antiAbuse = new AntiAbuseConfig(
                cfg.getBoolean("anti-abuse.enabled", true),
                cfg.getLong("anti-abuse.cooldown-seconds", 21600) * 1000L,
                cfg.getInt("anti-abuse.max-daily-votes", 16));

        OnlinePlayers online = () -> {
            List<PlayerRef> refs = new ArrayList<>();
            ProxyServer.getInstance().getPlayers()
                    .forEach(p -> refs.add(new PlayerRef(p.getUniqueId(), p.getName())));
            return refs;
        };

        Broadcaster broadcaster = message -> audiences.all().sendMessage(MiniMessageText.render(message));

        VoteService voteService = new VoteService(storage, streak, party, antiAbuse, online, broadcaster, logger);

        ReceiverConfig receiverConfig = new ReceiverConfig(
                true,
                cfg.getString("receiver.host", "0.0.0.0"),
                cfg.getInt("receiver.port", 8192),
                readTokens(cfg.getSection("receiver.tokens")),
                cfg.getString("receiver.api-key", ""),
                cfg.getLong("receiver.replay-window-seconds",
                        cfg.getLong("receiver.freshness-seconds", 300)) * 1000L);

        this.receiver = new VoteReceiver(
                receiverConfig,
                getDataFolder().toPath(),
                vote -> {
                    try {
                        voteService.process(vote);
                    } catch (Exception e) {
                        logger.error("Failed to process vote from " + vote.username(), e);
                    }
                },
                logger);

        try {
            receiver.start();
        } catch (Exception e) {
            logger.error("Could not start the vote receiver", e);
            this.receiver = null;
        }
    }

    private void stopReceiver() {
        if (receiver != null) {
            receiver.stop();
            receiver = null;
        }
    }

    public VoteReceiver receiver() {
        return receiver;
    }

    public String storageLabel() {
        return storageLabel;
    }

    private Configuration loadConfig() {
        try {
            if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
                logger.warn("Could not create the data folder");
            }

            File file = new File(getDataFolder(), "config.yml");

            if (!file.exists()) {
                try (InputStream in = getResourceAsStream("config.yml")) {
                    Files.copy(in, file.toPath());
                }
            }

            return ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);
        } catch (IOException e) {
            logger.error("Could not load config.yml", e);

            return null;
        }
    }

    private static Map<String, String> readTokens(Configuration section) {
        Map<String, String> tokens = new LinkedHashMap<>();

        if (section != null) {
            for (String service : section.getKeys()) {
                tokens.put(service, section.getString(service, ""));
            }
        }

        return tokens;
    }

    private static List<StreakTier> readTiers(Configuration section) {
        List<StreakTier> tiers = new ArrayList<>();

        if (section != null) {
            for (String id : section.getKeys()) {
                tiers.add(new StreakTier(id, section.getInt(id)));
            }
        }

        tiers.sort((a, b) -> Integer.compare(a.required(), b.required()));

        return tiers;
    }

    private static ZoneId zone(String id) {
        try {
            return ZoneId.of(id);
        } catch (Exception e) {
            return ZoneId.of("UTC");
        }
    }

}
