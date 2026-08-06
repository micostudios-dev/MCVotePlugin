package org.mcvote.server;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.mcvote.api.MCVoteProvider;
import org.mcvote.common.config.StorageType;
import org.mcvote.common.net.VoteReceiver;
import org.mcvote.common.platform.Broadcaster;
import org.mcvote.common.platform.OnlinePlayers;
import org.mcvote.common.platform.PlayerRef;
import org.mcvote.common.platform.UnifiedLogger;
import org.mcvote.common.storage.StorageFactory;
import org.mcvote.common.storage.VoteStorage;
import org.mcvote.common.sync.RemoteVoteStorage;
import org.mcvote.common.vote.VoteProcessResult;
import org.mcvote.common.vote.VoteService;
import org.mcvote.server.cache.PlayerDataCache;
import org.mcvote.server.command.VoteCommand;
import org.mcvote.server.config.ConfigCheck;
import org.mcvote.server.config.Messages;
import org.mcvote.server.config.ServerConfig;
import org.mcvote.server.delivery.DeliveryService;
import org.mcvote.server.hook.MCVotePlaceholders;
import org.mcvote.server.listener.PlayerListener;
import org.mcvote.server.menu.MenuManager;
import org.mcvote.server.menu.MenuService;
import org.mcvote.server.platform.MessageService;
import org.mcvote.server.platform.Scheduler;
import org.mcvote.server.platform.bukkit.BukkitLogger;
import org.mcvote.server.platform.bukkit.BukkitProxyLink;
import org.mcvote.server.reward.RewardExecutor;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class MCVoteServer {

    private final JavaPlugin plugin;
    private final Scheduler scheduler;
    private final MessageService messages;
    private final UnifiedLogger logger;
    private final PlayerDataCache dataCache = new PlayerDataCache();

    private ServerConfig config;
    private VoteStorage storage;
    private RewardExecutor rewardExecutor;
    private DeliveryService delivery;
    private VoteReceiver receiver;
    private MCVoteApiImpl api;
    private MenuService menuService;
    private BukkitProxyLink proxyLink;

    public MCVoteServer(JavaPlugin plugin, Scheduler scheduler, MessageService messages) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.messages = messages;
        this.logger = new BukkitLogger(plugin.getLogger());
    }

    private static final String DEFAULT_LANGUAGE = "en_us";
    private static final String[] LANG_RESOURCES = {"lang/en_us.yml", "lang/es_es.yml"};
    private static final String[] MENU_RESOURCES = {"menu/main.yml", "menu/streak.yml", "menu/admin.yml"};

    public void enable() {
        plugin.saveDefaultConfig();
        saveDefaultResources();
        ConfigCheck.run(new File(plugin.getDataFolder(), "config.yml"), logger);
        this.config = ServerConfig.load(plugin.getConfig(), loadMessages());

        this.storage = createStorage();
        try {
            storage.init();
        } catch (Exception e) {
            logger.error("Could not open the vote storage, disabling MCVote", e);
            Bukkit.getPluginManager().disablePlugin(plugin);
            return;
        }

        this.rewardExecutor = new RewardExecutor(scheduler, messages);
        this.api = new MCVoteApiImpl(dataCache, config);
        MCVoteProvider.set(api);

        this.delivery = new DeliveryService(storage, config, rewardExecutor, messages, scheduler, dataCache);
        delivery.start();

        this.menuService = new MenuService(this);
        loadMenus();

        startReceiver();

        Bukkit.getPluginManager().registerEvents(new MenuManager(), plugin);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), plugin);

        VoteCommand command = new VoteCommand(this);
        register("vote", command);
        register("mcvote", command);

        registerPlaceholders();

        logger.info("MCVote enabled (" + plugin.getName() + ", receiver="
                + (config.receiver().enabled() ? "on" : "off") + ")");
        logger.info("Reward lines loaded: " + config.rewardSummary());
    }

    public void disable() {
        if (receiver != null) {
            receiver.stop();
        }
        if (delivery != null) {
            delivery.stop();
        }
        if (proxyLink != null) {
            proxyLink.unregister();
        }
        scheduler.shutdown();
        if (storage != null) {
            storage.close();
        }
        MCVoteProvider.set(null);
    }

    public void reload() {
        plugin.reloadConfig();
        ConfigCheck.run(new File(plugin.getDataFolder(), "config.yml"), logger);
        this.config = ServerConfig.load(plugin.getConfig(), loadMessages());
        logger.info("Reward lines loaded: " + config.rewardSummary());

        if (receiver != null) {
            receiver.stop();
            receiver = null;
        }
        if (delivery != null) {
            delivery.stop();
        }

        this.rewardExecutor = new RewardExecutor(scheduler, messages);
        this.api = new MCVoteApiImpl(dataCache, config);
        MCVoteProvider.set(api);
        this.delivery = new DeliveryService(storage, config, rewardExecutor, messages, scheduler, dataCache);
        delivery.start();
        if (menuService != null) {
            loadMenus();
        }
        startReceiver();
    }

    private void saveDefaultResources() {
        for (String resource : LANG_RESOURCES) {
            saveResourceIfMissing(resource);
        }
        for (String resource : MENU_RESOURCES) {
            saveResourceIfMissing(resource);
        }
    }

    private void saveResourceIfMissing(String path) {
        if (!new File(plugin.getDataFolder(), path).exists()) {
            plugin.saveResource(path, false);
        }
    }

    private Messages loadMessages() {
        String language = plugin.getConfig().getString("language", DEFAULT_LANGUAGE);
        File file = new File(plugin.getDataFolder(), "lang/" + language + ".yml");
        if (!file.exists()) {
            file = new File(plugin.getDataFolder(), "lang/" + DEFAULT_LANGUAGE + ".yml");
        }
        return ServerConfig.messagesFrom(YamlConfiguration.loadConfiguration(file));
    }

    private void loadMenus() {
        YamlConfiguration combined = new YamlConfiguration();
        ConfigurationSection menus = combined.createSection("menus");

        File dir = new File(plugin.getDataFolder(), "menu");
        File[] files = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files != null) {
            Arrays.sort(files);
            for (File file : files) {
                ConfigurationSection section = YamlConfiguration.loadConfiguration(file)
                        .getConfigurationSection("menus");
                if (section != null) {
                    for (String id : section.getKeys(false)) {
                        menus.set(id, section.get(id));
                    }
                }
            }
        }
        menuService.load(combined);
    }

    private void registerPlaceholders() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        try {
            new MCVotePlaceholders(dataCache, config, plugin.getDescription().getVersion()).register();
            logger.info("Hooked into PlaceholderAPI");
        } catch (Throwable t) {
            logger.warn("Could not register PlaceholderAPI expansion: " + t.getMessage());
        }
    }

    private VoteStorage createStorage() {
        if (config.database().type() != StorageType.PROXY) {
            return StorageFactory.create(config.database(), plugin.getDataFolder());
        }

        this.proxyLink = new BukkitProxyLink(plugin, scheduler);
        RemoteVoteStorage remote = new RemoteVoteStorage(proxyLink, logger);
        proxyLink.register(remote);
        return remote;
    }

    private void startReceiver() {
        if (!config.receiver().enabled()) {
            return;
        }

        OnlinePlayers online = () -> {
            List<PlayerRef> refs = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(p -> refs.add(new PlayerRef(p.getUniqueId(), p.getName())));
            return refs;
        };
        Broadcaster broadcaster = messages::broadcast;

        VoteService voteService = new VoteService(storage, config.streak(), config.party(),
                config.antiAbuse(), online, broadcaster, logger);

        this.receiver = new VoteReceiver(
                config.receiver(),
                plugin.getDataFolder().toPath(),
                vote -> {
                    try {
                        VoteProcessResult result = voteService.process(vote);
                        if (result.accepted()) {
                            api.fire(vote);
                        }
                    } catch (Exception e) {
                        logger.error("Failed to process vote from " + vote.username(), e);
                    }
                },
                logger);

        try {
            receiver.start();
        } catch (Exception e) {
            logger.error("Could not start the vote receiver on port " + config.receiver().port(), e);
        }
    }

    private void register(String name, org.bukkit.command.CommandExecutor executor) {
        var command = plugin.getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
        }
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public ServerConfig config() {
        return config;
    }

    public VoteStorage storage() {
        return storage;
    }

    public MessageService messages() {
        return messages;
    }

    public Scheduler scheduler() {
        return scheduler;
    }

    public DeliveryService delivery() {
        return delivery;
    }

    public PlayerDataCache cache() {
        return dataCache;
    }

    public MenuService menus() {
        return menuService;
    }
}
