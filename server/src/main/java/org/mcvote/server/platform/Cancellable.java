package org.mcvote.server.platform;

@FunctionalInterface
public interface Cancellable {

    void cancel();
}
