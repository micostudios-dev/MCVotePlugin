package org.mcvote.common.platform;

public interface UnifiedLogger {

    void info(String message);

    void warn(String message);

    void error(String message, Throwable throwable);

    default void error(String message) {
        error(message, null);
    }
}
