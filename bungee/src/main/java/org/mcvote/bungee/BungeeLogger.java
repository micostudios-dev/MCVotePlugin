package org.mcvote.bungee;

import org.mcvote.common.platform.UnifiedLogger;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class BungeeLogger implements UnifiedLogger {

    private final Logger logger;

    public BungeeLogger(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void warn(String message) {
        logger.warning(message);
    }

    @Override
    public void error(String message, Throwable throwable) {
        logger.log(Level.SEVERE, message, throwable);
    }
}
