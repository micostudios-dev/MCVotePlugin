package org.mcvote.server.reward;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.mcvote.server.platform.MessageService;
import org.mcvote.server.platform.Scheduler;

import java.util.Map;

public final class RewardExecutor {

    private final Scheduler scheduler;
    private final MessageService messages;

    public RewardExecutor(Scheduler scheduler, MessageService messages) {
        this.scheduler = scheduler;
        this.messages = messages;
    }

    public void run(Player player, RewardBundle bundle, Map<String, String> placeholders) {
        for (RewardAction action : bundle.actions()) {
            String raw = action.argument();

            switch (action.type()) {
                case CONSOLE_COMMAND -> scheduler.runGlobal(() -> Bukkit.dispatchCommand(
                        Bukkit.getConsoleSender(), apply(raw, player, placeholders)));
                case PLAYER_COMMAND -> scheduler.runForPlayer(player,
                        () -> player.performCommand(apply(raw, player, placeholders)));
                case MESSAGE -> scheduler.runForPlayer(player,
                        () -> messages.send(player, apply(raw, player, placeholders)));
                case BROADCAST -> scheduler.runGlobal(
                        () -> messages.broadcast(apply(raw, player, placeholders)));
                case ITEM -> scheduler.runForPlayer(player,
                        () -> giveItem(player, apply(raw, player, placeholders)));
                case SOUND -> scheduler.runForPlayer(player,
                        () -> playSound(player, apply(raw, player, placeholders)));
            }
        }
    }

    private void giveItem(Player player, String arg) {
        String[] parts = arg.split("\\s+");
        Material material = Material.matchMaterial(parts[0]);

        if (material == null) {
            return;
        }

        int amount = parts.length > 1 ? parseInt(parts[1], 1) : 1;
        ItemStack stack = new ItemStack(material, Math.max(1, amount));

        Location location = player.getLocation();
        player.getInventory().addItem(stack).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(location, leftover));
    }

    private void playSound(Player player, String arg) {
        String[] parts = arg.split("\\s+");
        float volume = parts.length > 1 ? (float) parseDouble(parts[1], 1.0) : 1.0f;
        float pitch = parts.length > 2 ? (float) parseDouble(parts[2], 1.0) : 1.0f;
        player.playSound(player.getLocation(), parts[0].toLowerCase(), volume, pitch);
    }

    private String apply(String raw, Player player, Map<String, String> placeholders) {
        String result = raw.replace("%player%", player.getName());

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }

        return withPlaceholderApi(player, result);
    }

    private static String withPlaceholderApi(Player player, String text) {
        if (text.indexOf('%') < 0 || Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return text;
        }

        try {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        } catch (Throwable t) {
            return text;
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
