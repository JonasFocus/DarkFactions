package com.darkfactions.storage;

/** Thrown when a DataStore operation fails, so callers can react (retry a save, or abort startup on a failed load) instead of silently losing or corrupting data. */
public class StorageException extends RuntimeException {
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
