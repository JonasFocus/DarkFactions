package com.darkfactions.utils;

/**
 * Pure, dependency-free validation of faction names.
 *
 * <p>Kept free of any Bukkit/Paper types so the rules can be unit tested in
 * isolation. The allowed-characters value is treated as a full-string regex
 * (it is fed to {@link String#matches(String)}), so it must be anchored and
 * quantified — a bare character class such as {@code [a-zA-Z0-9_]} only ever
 * matches a single character and would reject every real name.
 */
public final class FactionNameValidator {

    /**
     * Default allowed-characters pattern: one or more letters, digits, or
     * underscores, matching the whole name. This is the single source of truth
     * shared by {@link ConfigManager} and the bundled {@code config.yml}.
     */
    public static final String DEFAULT_ALLOWED_CHARS = "^[a-zA-Z0-9_]+$";

    private FactionNameValidator() {
    }

    /** Outcome of validating a candidate faction name. */
    public enum Result {
        VALID,
        INVALID_LENGTH,
        INVALID_CHARS
    }

    /**
     * Validate a candidate faction name against the configured length bounds
     * and allowed-characters regex. Length is checked before characters so the
     * caller can surface the most relevant message.
     *
     * @param name           the candidate name (may be {@code null})
     * @param minLen         inclusive minimum length
     * @param maxLen         inclusive maximum length
     * @param allowedPattern a full-string regex describing allowed names
     * @return the validation {@link Result}
     */
    public static Result validate(String name, int minLen, int maxLen, String allowedPattern) {
        if (name == null) {
            return Result.INVALID_LENGTH;
        }
        int len = name.length();
        if (len < minLen || len > maxLen) {
            return Result.INVALID_LENGTH;
        }
        if (allowedPattern != null && !allowedPattern.isEmpty() && !name.matches(allowedPattern)) {
            return Result.INVALID_CHARS;
        }
        return Result.VALID;
    }
}
