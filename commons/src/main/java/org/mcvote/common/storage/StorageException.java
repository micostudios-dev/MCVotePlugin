package org.mcvote.common.storage;

public final class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super("Storage error during " + message, cause);
    }
}
