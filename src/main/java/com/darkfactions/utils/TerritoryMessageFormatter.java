package com.darkfactions.utils;

import java.util.Locale;

/**
 * Pure, dependency-free formatting of territory border messages.
 *
 * <p>The enter-own / enter-ally / enter-enemy / exit messages all expand the
 * same set of placeholders ({@code {faction}}, {@code {leader}}, {@code {members}},
 * {@code {power}}, {@code {elixir}}) against a configured template. That
 * substitution was previously copy-pasted four times in {@code FactionListener};
 * centralising it here removes the duplication and lets the rules be unit tested
 * without a server. Numbers are formatted with {@link Locale#ROOT} so a server's
 * default locale can never swap in a comma decimal separator.
 */
public final class TerritoryMessageFormatter {

    private TerritoryMessageFormatter() {
    }

    /**
     * Expand the placeholders in {@code template} with the supplied faction
     * details. A {@code null} template yields an empty string.
     *
     * @param template    the message template (may contain placeholders)
     * @param factionName value for {@code {faction}}
     * @param leaderName  value for {@code {leader}}
     * @param memberCount value for {@code {members}}
     * @param power       value for {@code {power}} (one decimal place)
     * @param elixir      value for {@code {elixir}} (no decimal places)
     * @return the formatted message
     */
    public static String format(String template, String factionName, String leaderName,
                                int memberCount, double power, double elixir) {
        if (template == null) {
            return "";
        }
        return template
                .replace("{faction}", factionName == null ? "" : factionName)
                .replace("{leader}", leaderName == null ? "" : leaderName)
                .replace("{members}", String.valueOf(memberCount))
                .replace("{power}", String.format(Locale.ROOT, "%.1f", power))
                .replace("{elixir}", String.format(Locale.ROOT, "%.0f", elixir));
    }
}
