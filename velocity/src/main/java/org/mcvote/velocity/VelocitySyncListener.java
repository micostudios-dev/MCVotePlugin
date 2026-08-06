package org.mcvote.velocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.mcvote.common.platform.UnifiedLogger;
import org.mcvote.common.sync.SyncProtocol;
import org.mcvote.common.sync.SyncRequestHandler;

public final class VelocitySyncListener {

    static final MinecraftChannelIdentifier IDENTIFIER =
            MinecraftChannelIdentifier.from(SyncProtocol.CHANNEL);

    private final SyncRequestHandler handler;
    private final UnifiedLogger logger;

    public VelocitySyncListener(SyncRequestHandler handler, UnifiedLogger logger) {
        this.handler = handler;
        this.logger = logger;
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!IDENTIFIER.equals(event.getIdentifier())) {
            return;
        }

        event.setResult(PluginMessageEvent.ForwardResult.handled());

        if (!(event.getSource() instanceof ServerConnection sender)) {
            logger.warn("Dropped a " + SyncProtocol.CHANNEL + " frame that did not come from a backend server");

            return;
        }

        byte[] response = handler.handle(event.getData());

        if (response != null) {
            sender.sendPluginMessage(IDENTIFIER, response);
        }
    }
}
