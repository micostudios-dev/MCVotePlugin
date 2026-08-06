package org.mcvote.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
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
import org.mcvote.common.sync.SyncRequestHandler;
import org.mcvote.common.text.MiniMessageText;
import org.mcvote.common.vote.VoteService;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Plugin(id = "mcvote", name = "MCVote", version = "1.0.0", authors = {"mcvote"},
        description = "Votes, streaks and VoteParty for MCVote (Velocity).")
public final class VelocityMCVotePlugin {

    private final ProxyServer proxy;
    private final UnifiedLogger logger;
    private final Path dataDir;

    private VoteStorage storage;
    private VoteReceiver receiver;
    private String storageLabel = "unknown";

    @Inject
    public VelocityMCVotePlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDir) {
        this.proxy = proxy;
        this.logger = new VelocityLogger(logger);
        this.dataDir = dataDir;
    }

    @Subscribe
    public void onInit(ProxyInitializeEvent event) {
        VelocityConfig cfg = loadConfig();
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

        this.storage = StorageFactory.create(database, dataDir.toFile());
        try {
            storage.init();
        } catch (Exception e) {
            logger.error("Could not open the vote storage, MCVote will not listen", e);
            return;
        }

        proxy.getChannelRegistrar().register(VelocitySyncListener.IDENTIFIER);
        proxy.getEventManager().register(this,
                new VelocitySyncListener(new SyncRequestHandler(storage, logger), logger));

        proxy.getCommandManager().register(
                proxy.getCommandManager().metaBuilder("mcvoteproxy").aliases("mcvp").plugin(this).build(),
                new VelocityVoteCommand(this));

        applyConfig(cfg);
    }

    public boolean reload() {
        VelocityConfig cfg = loadConfig();
        if (cfg == null) {
            return false;
        }
        stopReceiver();
        applyConfig(cfg);
        return true;
    }

    private void applyConfig(VelocityConfig cfg) {
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
            proxy.getAllPlayers().forEach(p -> refs.add(new PlayerRef(p.getUniqueId(), p.getUsername())));
            return refs;
        };
        Broadcaster broadcaster = message -> {
            Component component = MiniMessageText.render(message);
            proxy.getAllPlayers().forEach(p -> p.sendMessage(component));
        };

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
                dataDir,
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

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        stopReceiver();
        if (storage != null) {
            storage.close();
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

    private VelocityConfig loadConfig() {
        try {
            return VelocityConfig.load(dataDir, getClass().getResourceAsStream("/config.yml"));
        } catch (Exception e) {
            logger.error("Could not load config.yml", e);
            return null;
        }
    }

    private static Map<String, String> readTokens(Map<String, Object> section) {
        Map<String, String> tokens = new LinkedHashMap<>();
        section.forEach((service, value) -> {
            if (value != null) {
                tokens.put(service, String.valueOf(value));
            }
        });
        return tokens;
    }

    private static List<StreakTier> readTiers(Map<String, Object> section) {
        List<StreakTier> tiers = new ArrayList<>();
        section.forEach((id, value) -> {
            if (value instanceof Number n) {
                tiers.add(new StreakTier(id, n.intValue()));
            }
        });
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
