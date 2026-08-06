package org.mcvote.server.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mcvote.common.storage.model.DeliveryType;
import org.mcvote.common.storage.model.PlayerVoteData;
import org.mcvote.common.text.MiniMessageText;
import org.mcvote.server.MCVoteServer;
import org.mcvote.server.config.Messages;

import java.util.List;
import java.util.Locale;

public final class VoteCommand implements CommandExecutor {

    private static final String ADMIN_PERMISSION = "mcvote.admin";
    private static final int TOP_SIZE = 10;

    private final MCVoteServer server;

    public VoteCommand(MCVoteServer server) {
        this.server = server;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player) || !server.menus().open("main", player)) {
                links(sender);
            }
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "streak" -> streak(sender);
            case "admin" -> admin(sender);
            case "links" -> links(sender);
            case "top" -> top(sender);
            case "party" -> party(sender);
            case "reload" -> reload(sender);
            case "forceparty" -> forceParty(sender);
            case "test" -> test(sender, args);
            default -> help(sender);
        }
        return true;
    }

    private void streak(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return;
        }
        if (server.menus().open("streak", player)) {
            return;
        }
        streakChat(player);
    }

    private void streakChat(Player player) {
        String key = player.getName().toLowerCase(Locale.ROOT);
        server.scheduler().runAsync(() -> {
            PlayerVoteData data = server.storage().load(key);
            send(player, "streak-info",
                    "%player%", player.getName(),
                    "%streak%", number(data == null ? 0 : data.streak()),
                    "%best_streak%", number(data == null ? 0 : data.bestStreak()),
                    "%votes%", number(data == null ? 0 : data.totalVotes()));
        });
    }

    private void admin(CommandSender sender) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            deny(sender);
            return;
        }
        if (!(sender instanceof Player player)) {
            send(sender, "admin-console");
            return;
        }
        if (!server.menus().open("admin", player)) {
            send(player, "admin-menu-disabled");
        }
    }

    private void party(CommandSender sender) {
        if (!server.config().party().enabled()) {
            send(sender, "party-disabled");
            return;
        }
        server.scheduler().runAsync(() -> {
            int goal = server.config().party().goal();
            int progress = server.storage().partyProgress();
            send(sender, "party-progress",
                    "%party_progress%", number(progress),
                    "%party_goal%", number(goal),
                    "%party_remaining%", number(Math.max(0, goal - progress)));
        });
    }

    private void top(CommandSender sender) {
        server.scheduler().runAsync(() -> {
            List<PlayerVoteData> top = server.storage().topByVotes(TOP_SIZE);
            if (top.isEmpty()) {
                send(sender, "top-empty");
                return;
            }
            send(sender, "top-header");
            int rank = 1;
            for (PlayerVoteData data : top) {
                String name = data.displayName() != null ? data.displayName() : data.username();
                line(sender, "top-entry",
                        "%rank%", number(rank++),
                        "%player%", name,
                        "%votes%", number(data.totalVotes()));
            }
        });
    }

    private void links(CommandSender sender) {
        List<String> links = server.config().voteLinks();
        if (links.isEmpty()) {
            send(sender, "links-empty");
            return;
        }
        send(sender, "links-header");
        for (String link : links) {
            String[] parts = link.split("\\|", 2);
            String name = parts.length > 1 ? parts[0] : link;
            String url = parts.length > 1 ? parts[1] : link;

            line(sender, "links-entry",
                    "%name%", MiniMessageText.escape(name),
                    "%url%", MiniMessageText.escape(url));
        }
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            deny(sender);
            return;
        }
        server.reload();
        send(sender, "reloaded");
    }

    private void forceParty(CommandSender sender) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            deny(sender);
            return;
        }
        server.scheduler().runAsync(() -> {
            long now = System.currentTimeMillis();
            Bukkit.getOnlinePlayers().forEach(p ->
                    server.storage().enqueue(p.getName().toLowerCase(Locale.ROOT), DeliveryType.PARTY, "", now));
            String broadcast = server.config().party().broadcastMessage();
            if (broadcast != null && !broadcast.isBlank()) {
                server.messages().broadcast(broadcast);
            }
            send(sender, "party-forced");
        });
    }

    private void test(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            deny(sender);
            return;
        }
        if (args.length < 2) {
            send(sender, "test-usage");
            return;
        }
        String target = args[1].toLowerCase(Locale.ROOT);
        server.scheduler().runAsync(() -> {
            server.storage().enqueue(target, DeliveryType.VOTE, "MCVoteTest", System.currentTimeMillis());
            send(sender, "test-queued", "%player%", args[1]);
        });
    }

    private void help(CommandSender sender) {
        block(sender, "help");
        if (sender.hasPermission(ADMIN_PERMISSION)) {
            block(sender, "help-admin");
        }
    }

    private void deny(CommandSender sender) {
        send(sender, "no-permission");
    }

    private void send(CommandSender sender, String key, String... placeholders) {
        String message = server.config().messages().get(key);
        if (!message.isEmpty()) {
            server.messages().send(sender, Messages.apply(message, placeholders));
        }
    }

    private void line(CommandSender sender, String key, String... placeholders) {
        String message = server.config().messages().raw(key);
        if (!message.isEmpty()) {
            server.messages().send(sender, Messages.apply(message, placeholders));
        }
    }

    private void block(CommandSender sender, String key, String... placeholders) {
        for (String raw : server.config().messages().block(key)) {
            server.messages().send(sender, Messages.apply(raw, placeholders));
        }
    }

    private static String number(int value) {
        return String.valueOf(value);
    }
}
