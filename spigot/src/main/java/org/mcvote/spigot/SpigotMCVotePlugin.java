package org.mcvote.spigot;

import org.bukkit.plugin.java.JavaPlugin;
import org.mcvote.server.MCVoteServer;
import org.mcvote.server.platform.bukkit.BukkitClassicScheduler;

public final class SpigotMCVotePlugin extends JavaPlugin {

    private MCVoteServer server;
    private SpigotMessageService messages;

    @Override
    public void onEnable() {
        this.messages = new SpigotMessageService(this);
        this.server = new MCVoteServer(this, new BukkitClassicScheduler(this), messages);
        server.enable();
    }

    @Override
    public void onDisable() {
        if (server != null) {
            server.disable();
        }

        if (messages != null) {
            messages.close();
        }
    }
}
