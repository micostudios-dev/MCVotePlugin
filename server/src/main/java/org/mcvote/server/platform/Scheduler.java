package org.mcvote.server.platform;

import org.bukkit.entity.Player;

public interface Scheduler {

    void runGlobal(Runnable task);

    void runForPlayer(Player player, Runnable task);

    void runAsync(Runnable task);

    Cancellable runAsyncTimer(Runnable task, long initialDelayMs, long periodMs);

    void shutdown();
}
