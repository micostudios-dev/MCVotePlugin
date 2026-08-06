package org.mcvote.server.menu;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.mcvote.common.config.StreakTier;
import org.mcvote.common.storage.model.DeliveryType;
import org.mcvote.common.storage.model.PlayerVoteData;
import org.mcvote.server.MCVoteServer;
import org.mcvote.server.menu.layout.LinksFill;
import org.mcvote.server.menu.layout.MenuAction;
import org.mcvote.server.menu.layout.MenuIcon;
import org.mcvote.server.menu.layout.MenuLayout;
import org.mcvote.server.menu.layout.TiersFill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MenuService {

    private static final String ADMIN_PERMISSION = "mcvote.admin";

    private final MCVoteServer server;
    private final Map<String, MenuLayout> layouts = new HashMap<>();

    public MenuService(MCVoteServer server) {
        this.server = server;
    }

    public void load(FileConfiguration cfg) {
        layouts.clear();
        ConfigurationSection root = cfg.getConfigurationSection("menus");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section != null) {
                layouts.put(id.toLowerCase(Locale.ROOT), parseLayout(section));
            }
        }
    }

    public boolean isEnabled(String id) {
        MenuLayout layout = layouts.get(id.toLowerCase(Locale.ROOT));
        return layout != null && layout.enabled();
    }

    public boolean open(String id, Player player) {
        MenuLayout layout = layouts.get(id.toLowerCase(Locale.ROOT));
        if (layout == null || !layout.enabled()) {
            return false;
        }

        String key = player.getName().toLowerCase(Locale.ROOT);
        server.scheduler().runAsync(() -> {
            PlayerVoteData data = server.storage().load(key);
            int party = server.storage().partyProgress();
            server.scheduler().runForPlayer(player, () -> build(layout, player, data, party).open(player));
        });
        return true;
    }

    private Menu build(MenuLayout layout, Player player, PlayerVoteData data, int partyProgress) {
        Map<String, String> base = placeholders(player, data, partyProgress);
        Menu menu = new Menu(server.messages().toLegacy(apply(layout.title(), base)), layout.rows());

        for (MenuIcon icon : layout.icons()) {
            if (icon.permission() != null && !icon.permission().isEmpty() && !player.hasPermission(icon.permission())) {
                continue;
            }
            menu.set(icon.slot(), new MenuButton(
                    Items.of(material(icon.material()), apply(icon.name(), base), applyAll(icon.lore(), base), server.messages()),
                    p -> runActions(p, icon.actions())));
        }

        fillLinks(menu, layout.links(), base);
        fillTiers(menu, layout.tiers(), base, data == null ? 0 : data.streak());
        return menu;
    }

    private void fillLinks(Menu menu, LinksFill links, Map<String, String> base) {
        if (links == null || !links.enabled()) {
            return;
        }
        int slot = links.startSlot();
        for (String link : server.config().voteLinks()) {
            String[] parts = link.split("\\|", 2);
            Map<String, String> ph = new HashMap<>(base);
            ph.put("%link_name%", parts.length > 0 ? parts[0] : link);
            ph.put("%link_url%", parts.length > 1 ? parts[1] : "");

            String clickMessage = links.clickMessage();
            menu.set(slot++, new MenuButton(
                    Items.of(material(links.material()), apply(links.name(), ph), applyAll(links.lore(), ph), server.messages()),
                    p -> {
                        if (clickMessage != null && !clickMessage.isEmpty()) {
                            server.messages().send(p, apply(clickMessage, ph));
                        }
                    }));
        }
    }

    private void fillTiers(Menu menu, TiersFill tiers, Map<String, String> base, int streak) {
        if (tiers == null || !tiers.enabled()) {
            return;
        }
        int slot = tiers.startSlot();
        for (StreakTier tier : server.config().streak().tiers()) {
            boolean reached = streak >= tier.required();
            int remaining = Math.max(0, tier.required() - streak);

            Map<String, String> ph = new HashMap<>(base);
            ph.put("%tier_id%", tier.id());
            ph.put("%tier_required%", String.valueOf(tier.required()));
            ph.put("%tier_remaining%", String.valueOf(remaining));
            ph.put("%tier_status%", reached ? tiers.unlockedStatus() : tiers.lockedStatus());

            menu.set(slot++, MenuButton.display(Items.of(
                    material(reached ? tiers.unlockedMaterial() : tiers.lockedMaterial()),
                    apply(tiers.name(), ph), applyAll(tiers.lore(), ph), server.messages())));
        }
    }

    private void runActions(Player player, List<MenuAction> actions) {
        for (MenuAction action : actions) {
            String arg = action.argument().replace("%player%", player.getName());
            switch (action.type()) {
                case OPEN -> open(arg, player);
                case CONSOLE -> server.scheduler().runGlobal(
                        () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), arg));
                case PLAYER -> server.scheduler().runForPlayer(player, () -> player.performCommand(arg));
                case MESSAGE -> server.messages().send(player, arg);
                case CLOSE -> player.closeInventory();
                case RELOAD -> {
                    if (!player.hasPermission(ADMIN_PERMISSION)) {
                        notify(player, "no-permission");
                    } else {
                        server.reload();
                        notify(player, "reloaded");
                        player.closeInventory();
                    }
                }
                case FORCEPARTY -> {
                    if (!player.hasPermission(ADMIN_PERMISSION)) {
                        notify(player, "no-permission");
                    } else {
                        forceParty();
                        notify(player, "party-forced");
                        player.closeInventory();
                    }
                }
                case NONE -> {
                }
            }
        }
    }

    private void notify(Player player, String key) {
        String message = server.config().messages().get(key);
        if (!message.isEmpty()) {
            server.messages().send(player, message);
        }
    }

    private void forceParty() {
        server.scheduler().runAsync(() -> {
            long now = System.currentTimeMillis();
            Bukkit.getOnlinePlayers().forEach(p ->
                    server.storage().enqueue(p.getName().toLowerCase(Locale.ROOT), DeliveryType.PARTY, "", now));
            String broadcast = server.config().party().broadcastMessage();
            if (broadcast != null && !broadcast.isBlank()) {
                server.messages().broadcast(broadcast);
            }
        });
    }

    private Map<String, String> placeholders(Player player, PlayerVoteData data, int partyProgress) {
        int streak = data == null ? 0 : data.streak();
        int goal = server.config().party().goal();
        org.mcvote.common.config.StreakTier next = server.config().streak().nextTier(streak);

        Map<String, String> map = new HashMap<>();
        map.put("%player%", player.getName());
        map.put("%streak%", String.valueOf(streak));
        map.put("%best_streak%", String.valueOf(data == null ? 0 : data.bestStreak()));
        map.put("%votes%", String.valueOf(data == null ? 0 : data.totalVotes()));
        map.put("%party_progress%", String.valueOf(partyProgress));
        map.put("%party_goal%", String.valueOf(goal));
        map.put("%party_remaining%", String.valueOf(Math.max(0, goal - partyProgress)));
        map.put("%next_tier%", next == null ? "-" : next.id());
        map.put("%next_tier_required%", String.valueOf(next == null ? 0 : next.required()));
        map.put("%next_tier_in%", String.valueOf(next == null ? 0 : Math.max(0, next.required() - streak)));
        return map;
    }

    private static String apply(String raw, Map<String, String> placeholders) {
        if (raw == null) {
            return "";
        }
        String result = raw;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private static List<String> applyAll(List<String> lines, Map<String, String> placeholders) {
        if (lines == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(apply(line, placeholders));
        }
        return out;
    }

    private static Material material(String name) {
        return name == null ? null : Material.matchMaterial(name.toUpperCase(Locale.ROOT));
    }

    private MenuLayout parseLayout(ConfigurationSection section) {
        boolean enabled = section.getBoolean("enabled", true);
        String title = section.getString("title", "&8Menu");
        int rows = section.getInt("rows", 3);

        List<MenuIcon> icons = new ArrayList<>();
        ConfigurationSection items = section.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection item = items.getConfigurationSection(key);
                if (item == null) {
                    continue;
                }
                List<MenuAction> actions = new ArrayList<>();
                for (String line : item.getStringList("actions")) {
                    actions.add(MenuAction.parse(line));
                }
                icons.add(new MenuIcon(
                        item.getInt("slot", 0),
                        item.getString("material", "PAPER"),
                        item.getString("name", ""),
                        item.getStringList("lore"),
                        actions,
                        item.getString("permission")));
            }
        }

        return new MenuLayout(enabled, title, rows, icons, parseLinks(section.getConfigurationSection("links")),
                parseTiers(section.getConfigurationSection("tiers")));
    }

    private LinksFill parseLinks(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        return new LinksFill(
                section.getBoolean("enabled", true),
                section.getInt("start-slot", 0),
                section.getString("material", "PAPER"),
                section.getString("name", "&b%link_name%"),
                section.getStringList("lore"),
                section.getString("click-message", "&7%link_name%: &f%link_url%"));
    }

    private TiersFill parseTiers(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        return new TiersFill(
                section.getBoolean("enabled", true),
                section.getInt("start-slot", 0),
                section.getString("unlocked-material", "LIME_DYE"),
                section.getString("locked-material", "GRAY_DYE"),
                section.getString("name", "&e%tier_id% &8(%tier_required% days)"),
                section.getStringList("lore"),
                section.getString("unlocked-status", "&aUnlocked"),
                section.getString("locked-status", "&7Locked"));
    }
}
