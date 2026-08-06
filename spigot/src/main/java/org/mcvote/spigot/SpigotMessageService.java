package org.mcvote.spigot;

import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.mcvote.common.text.MiniMessageText;
import org.mcvote.server.platform.MessageService;

public final class SpigotMessageService implements MessageService, AutoCloseable {

    private final BukkitAudiences audiences;

    public SpigotMessageService(Plugin plugin) {
        this.audiences = BukkitAudiences.create(plugin);
    }

    @Override
    public void send(CommandSender target, String message) {
        if (message != null && !message.isEmpty()) {
            audiences.sender(target).sendMessage(MiniMessageText.render(message));
        }
    }

    @Override
    public void broadcast(String message) {
        if (message != null && !message.isEmpty()) {
            Component component = MiniMessageText.render(message);
            audiences.players().sendMessage(component);
            audiences.console().sendMessage(component);
        }
    }

    @Override
    public String toLegacy(String message) {
        return MiniMessageText.toLegacySection(message);
    }

    @Override
    public void close() {
        audiences.close();
    }
}
