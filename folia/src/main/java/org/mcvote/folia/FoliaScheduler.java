package org.mcvote.folia;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.mcvote.server.platform.Cancellable;
import org.mcvote.server.platform.Scheduler;

import java.util.concurrent.TimeUnit;

public final class FoliaScheduler implements Scheduler {

    private final Plugin plugin;

    public FoliaScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runGlobal(Runnable task) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, task);
    }

    @Override
    public void runForPlayer(Player player, Runnable task) {
        player.getScheduler().run(plugin, scheduled -> task.run(), null);
    }

    @Override
    public void runAsync(Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, scheduled -> task.run());
    }

    @Override
    public Cancellable runAsyncTimer(Runnable task, long initialDelayMs, long periodMs) {
        ScheduledTask handle = Bukkit.getAsyncScheduler().runAtFixedRate(
                plugin, scheduled -> task.run(),
                Math.max(1, initialDelayMs), Math.max(1, periodMs), TimeUnit.MILLISECONDS);
        return handle::cancel;
    }

    @Override
    public void shutdown() {
        Bukkit.getAsyncScheduler().cancelTasks(plugin);
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
    }
}
