package com.darkfactions.utils;

/**
 * Pure, dependency-free expansion of the faction/ally chat format templates.
 *
 * <p>Both chat modes expand the same placeholders ({@code {prefix}}, {@code {tag}},
 * {@code {player}}, {@code {faction}}, {@code {message}}) and then translate
 * legacy {@code &} colour codes to the section sign. That logic was duplicated
 * between the faction-chat and ally-chat branches of {@code FactionListener};
 * centralising it here removes the duplication and makes it unit testable.
 */
public final class ChatFormatter {

    /** The legacy section sign that Minecraft uses for colour codes. */
    public static final char SECTION = '§';

    private ChatFormatter() {
    }

    /**
     * Expand a chat template's placeholders and translate {@code &} colour codes.
     * A {@code null} template yields an empty string; any {@code null} value is
     * treated as an empty replacement.
     */
    public static String format(String template, String prefix, String tag,
                                String player, String faction, String message) {
        if (template == null) {
            return "";
        }
        // Substitute the template/tag/name placeholders and translate '&' colour
        // codes first, then substitute {message} last. Doing the message
        // substitution before the colour translation (the old order) let a
        // player inject real colour/formatting codes into faction/ally chat by
        // typing '&' in their message.
        String withoutMessage = template
                .replace("{prefix}", orEmpty(prefix))
                .replace("{tag}", orEmpty(tag))
                .replace("{player}", orEmpty(player))
                .replace("{faction}", orEmpty(faction))
                .replace('&', SECTION);
        return withoutMessage.replace("{message}", orEmpty(message));
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
