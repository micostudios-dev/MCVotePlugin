package org.mcvote.server.platform.bukkit;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.mcvote.server.platform.Cancellable;
import org.mcvote.server.platform.Scheduler;

public final class BukkitClassicScheduler implements Scheduler {

    private final Plugin plugin;

    public BukkitClassicScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runGlobal(Runnable task) {
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    @Override
    public void runForPlayer(Player player, Runnable task) {
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    @Override
    public void runAsync(Runnable task) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
    }

    @Override
    public Cancellable runAsyncTimer(Runnable task, long initialDelayMs, long periodMs) {
        long delay = Math.max(1, initialDelayMs / 50);
        long period = Math.max(1, periodMs / 50);
        BukkitTask handle = plugin.getServer().getScheduler()
                .runTaskTimerAsynchronously(plugin, task, delay, period);
        return handle::cancel;
    }

    @Override
    public void shutdown() {
        plugin.getServer().getScheduler().cancelTasks(plugin);
    }
}
