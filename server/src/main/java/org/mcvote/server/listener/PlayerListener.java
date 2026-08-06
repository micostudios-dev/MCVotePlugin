package org.mcvote.server.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.mcvote.server.MCVoteServer;

public final class PlayerListener implements Listener {

    private final MCVoteServer server;

    public PlayerListener(MCVoteServer server) {
        this.server = server;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String name = player.getName();

        server.scheduler().runAsync(() -> {
            server.storage().linkIdentity(name, player.getUniqueId().toString(), name);
            server.cache().put(server.storage().load(name));
        });

        server.delivery().deliver(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        server.cache().remove(event.getPlayer().getName());
    }
}
