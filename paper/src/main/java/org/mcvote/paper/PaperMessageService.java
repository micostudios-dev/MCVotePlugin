package org.mcvote.paper;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.mcvote.common.text.MiniMessageText;
import org.mcvote.server.platform.MessageService;

public final class PaperMessageService implements MessageService {

    @Override
    public void send(CommandSender target, String message) {
        if (message != null && !message.isEmpty()) {
            target.sendMessage(MiniMessageText.render(message));
        }
    }

    @Override
    public void broadcast(String message) {
        if (message != null && !message.isEmpty()) {
            Bukkit.broadcast(MiniMessageText.render(message));
        }
    }

    @Override
    public String toLegacy(String message) {
        return MiniMessageText.toLegacySection(message);
    }
}
