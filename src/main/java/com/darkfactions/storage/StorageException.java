package com.darkfactions.storage;

/** Thrown when a DataStore write fails, so callers can retry instead of silently losing data. */
public class StorageException extends RuntimeException {
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
