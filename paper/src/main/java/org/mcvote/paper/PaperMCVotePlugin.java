package org.mcvote.paper;

import org.bukkit.plugin.java.JavaPlugin;
import org.mcvote.server.MCVoteServer;
import org.mcvote.server.platform.bukkit.BukkitClassicScheduler;

public final class PaperMCVotePlugin extends JavaPlugin {

    private MCVoteServer server;

    @Override
    public void onEnable() {
        this.server = new MCVoteServer(this, new BukkitClassicScheduler(this), new PaperMessageService());
        server.enable();
    }

    @Override
    public void onDisable() {
        if (server != null) {
            server.disable();
        }
    }
}
