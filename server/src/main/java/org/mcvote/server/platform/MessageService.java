package org.mcvote.server.platform;

import org.bukkit.command.CommandSender;

public interface MessageService {

    void send(CommandSender target, String message);

    void broadcast(String message);

    String toLegacy(String message);
}
