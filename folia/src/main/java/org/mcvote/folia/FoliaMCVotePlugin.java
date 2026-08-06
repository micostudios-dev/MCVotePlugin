package org.mcvote.folia;

import org.bukkit.plugin.java.JavaPlugin;
import org.mcvote.server.MCVoteServer;

public final class FoliaMCVotePlugin extends JavaPlugin {

    private MCVoteServer server;

    @Override
    public void onEnable() {
        this.server = new MCVoteServer(this, new FoliaScheduler(this), new FoliaMessageService());
        server.enable();
    }

    @Override
    public void onDisable() {
        if (server != null) {
            server.disable();
        }
    }
}
