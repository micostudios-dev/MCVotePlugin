package org.mcvote.bungee;

import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import org.mcvote.common.platform.UnifiedLogger;
import org.mcvote.common.sync.SyncProtocol;
import org.mcvote.common.sync.SyncRequestHandler;

public final class BungeeSyncListener implements Listener {

    private final SyncRequestHandler handler;
    private final UnifiedLogger logger;

    public BungeeSyncListener(SyncRequestHandler handler, UnifiedLogger logger) {
        this.handler = handler;
        this.logger = logger;
    }

    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        if (!SyncProtocol.CHANNEL.equals(event.getTag())) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getSender() instanceof Server sender)) {
            logger.warn("Dropped a " + SyncProtocol.CHANNEL + " frame that did not come from a backend server");

            return;
        }

        byte[] response = handler.handle(event.getData());

        if (response != null) {
            sender.sendData(SyncProtocol.CHANNEL, response);
        }
    }
}
