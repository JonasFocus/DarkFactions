package com.darkfactions.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ChatFormatterTest {

    @Test
    void expandsAllPlaceholders() {
        String out = ChatFormatter.format("{prefix}{tag}{player}: {message} in {faction}",
                "[DF] ", "[WAR] ", "Steve", "Warriors", "hello");
        assertEquals("[DF] [WAR] Steve: hello in Warriors", out);
    }

    @Test
    void translatesAmpersandColourCodes() {
        assertEquals("§ahi", ChatFormatter.format("&a{message}", "", "", "", "", "hi"));
    }

    @Test
    void nullTemplateYieldsEmptyString() {
        assertEquals("", ChatFormatter.format(null, "p", "t", "pl", "f", "m"));
    }

    @Test
    void nullValuesBecomeEmpty() {
        assertEquals("x: ", ChatFormatter.format("x: {message}", null, null, null, null, null));
    }

    @Test
    void messageColourCodesAreAlsoTranslated() {
        // A & inside the player's message is translated too, matching the original behaviour.
        assertEquals("§cred", ChatFormatter.format("{message}", "", "", "", "", "&cred"));
    }
}
