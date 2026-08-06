package org.mcvote.velocity;

import org.mcvote.common.platform.UnifiedLogger;
import org.slf4j.Logger;

public final class VelocityLogger implements UnifiedLogger {

    private final Logger logger;

    public VelocityLogger(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void warn(String message) {
        logger.warn(message);
    }

    @Override
    public void error(String message, Throwable throwable) {
        if (throwable != null) {
            logger.error(message, throwable);
        } else {
            logger.error(message);
        }
    }
}
