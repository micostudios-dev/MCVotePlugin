package org.mcvote.server.platform.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.mcvote.common.sync.NodeLink;
import org.mcvote.common.sync.RemoteVoteStorage;
import org.mcvote.common.sync.SyncProtocol;
import org.mcvote.server.platform.Scheduler;

public final class BukkitProxyLink implements NodeLink, PluginMessageListener {

    private final JavaPlugin plugin;
    private final Scheduler scheduler;

    private volatile RemoteVoteStorage storage;

    public BukkitProxyLink(JavaPlugin plugin, Scheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    public void register(RemoteVoteStorage storage) {
        this.storage = storage;
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, SyncProtocol.CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, SyncProtocol.CHANNEL, this);
    }

    public void unregister() {
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, SyncProtocol.CHANNEL);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, SyncProtocol.CHANNEL, this);
    }

    @Override
    public boolean send(byte[] frame) {
        Player carrier = anyPlayer();
        if (carrier == null) {
            return false;
        }
        scheduler.runForPlayer(carrier, () -> {
            Player current = carrier.isOnline() ? carrier : anyPlayer();
            if (current != null) {
                current.sendPluginMessage(plugin, SyncProtocol.CHANNEL, frame);
            }
        });
        return true;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!SyncProtocol.CHANNEL.equals(channel)) {
            return;
        }
        RemoteVoteStorage target = storage;
        if (target != null) {
            target.handleResponse(message);
        }
    }

    private static Player anyPlayer() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            return player;
        }
        return null;
    }
}
