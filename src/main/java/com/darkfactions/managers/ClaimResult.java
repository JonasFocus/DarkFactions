package com.darkfactions.managers;

// ==========================================
// ClaimResult.java
// Outcome of a claim attempt, with the message shown to the player.
// Replaces the old stringly-typed result codes so every outcome is
// type checked and no failure reason can be silently dropped.
// ==========================================

public enum ClaimResult {

    SUCCESS(true, "Chunk claimed!"),
    ALREADY_OWNED(false, "Your faction already owns this chunk!"),
    ALREADY_CLAIMED(false, "This chunk is already claimed by another faction!"),
    NOT_CONNECTED(false, "You must claim land adjacent to your existing territory first!"),
    TOO_MANY(false, "Your faction has reached the maximum number of claims!"),
    NO_ELIXIR(false, "Your faction doesn't have enough elixir to claim this chunk!"),
    DISABLED(false, "Land claiming is disabled on this server!"),
    DISABLED_WORLD(false, "You cannot claim land in this world!"),
    TOO_CLOSE_SPAWN(false, "You are too close to spawn to claim land here!"),
    BUFFER_VIOLATION(false, "You cannot claim this close to another faction's territory!");

    private final boolean success;
    private final String message;

    ClaimResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}
