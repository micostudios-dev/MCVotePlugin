package org.mcvote.common.platform;

@FunctionalInterface
public interface Broadcaster {

    void broadcast(String message);
}
