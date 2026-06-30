package com.darkfactions.utils;

import java.util.Locale;

/**
 * Pure, dependency-free rendering of faction list and leaderboard rows.
 *
 * <p>{@code FactionCommand} previously copy-pasted the same row layout across
 * {@code handleList}, the two console list handlers, and the player/console
 * leaderboard handlers. Centralising it here removes the duplication, keeps the
 * layout consistent, and lets the formatting be unit tested without a server.
 * Numbers are formatted with {@link Locale#ROOT} so a server's default locale
 * can never swap in a comma decimal separator.
 */
public final class FactionListFormatter {

    private FactionListFormatter() {
    }

    /**
     * A "/f list" row: {@code "[TAG] Name - N members - P power - C claims"}.
     *
     * @param formattedTag the display tag (e.g. {@code "[WAR] "}), or empty/null for none
     * @param name         the faction name
     * @param members      the member count
     * @param power        the faction power (rendered with no decimals)
     * @param claims       the claim count
     */
    public static String listRow(String formattedTag, String name, int members, double power, int claims) {
        return orEmpty(formattedTag) + name
                + " - " + members + " members"
                + " - " + fmt(power, 0) + " power"
                + " - " + claims + " claims";
    }

    /**
     * A leaderboard row: {@code "rank. [TAG] Name - value"}.
     *
     * @param rank         the 1-based rank
     * @param formattedTag the display tag, or empty/null for none
     * @param name         the faction name
     * @param value        the pre-formatted metric (e.g. {@code "12.0 power"})
     */
    public static String rankRow(int rank, String formattedTag, String name, String value) {
        return rank + ". " + orEmpty(formattedTag) + name + " - " + value;
    }

    /** Format a power-style value with one decimal place, e.g. {@code "12.5 power"}. */
    public static String metric(double value, int decimals, String label) {
        return fmt(value, decimals) + " " + label;
    }

    private static String fmt(double value, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
