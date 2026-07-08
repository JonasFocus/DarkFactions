package com.darkfactions.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.darkfactions.utils.FactionNameValidator.Result;

import org.junit.jupiter.api.Test;

/**
 * Regression tests for the faction-name validation that {@code /f create} and
 * {@code /f rename} rely on. The original default pattern was a bare character
 * class ({@code [a-zA-Z0-9_]}) matched against the whole name, which rejected
 * every name of length >= 2 and made faction creation impossible.
 */
class FactionNameValidatorTest {

    private static final int MIN = 2;
    private static final int MAX = 32;
    private static final String PATTERN = FactionNameValidator.DEFAULT_ALLOWED_CHARS;

    @Test
    void acceptsOrdinaryMultiCharacterNames() {
        assertEquals(Result.VALID, FactionNameValidator.validate("Wolves", MIN, MAX, PATTERN));
        assertEquals(Result.VALID, FactionNameValidator.validate("Dark_Faction_99", MIN, MAX, PATTERN));
        assertEquals(Result.VALID, FactionNameValidator.validate("ab", MIN, MAX, PATTERN));
    }

    @Test
    void rejectsNamesWithDisallowedCharacters() {
        assertEquals(Result.INVALID_CHARS, FactionNameValidator.validate("bad name!", MIN, MAX, PATTERN));
        assertEquals(Result.INVALID_CHARS, FactionNameValidator.validate("§cspoof", MIN, MAX, PATTERN));
        assertEquals(Result.INVALID_CHARS, FactionNameValidator.validate("has-hyphen", MIN, MAX, PATTERN));
    }

    @Test
    void enforcesLengthBounds() {
        assertEquals(Result.INVALID_LENGTH, FactionNameValidator.validate("a", MIN, MAX, PATTERN));
        assertEquals(Result.INVALID_LENGTH, FactionNameValidator.validate("", MIN, MAX, PATTERN));
        assertEquals(Result.INVALID_LENGTH, FactionNameValidator.validate(null, MIN, MAX, PATTERN));
        assertEquals(Result.INVALID_LENGTH,
                FactionNameValidator.validate("x".repeat(MAX + 1), MIN, MAX, PATTERN));
    }

    @Test
    void lengthIsCheckedBeforeCharacters() {
        // A single illegal char that is also too short should report length, not chars.
        assertEquals(Result.INVALID_LENGTH, FactionNameValidator.validate("!", MIN, MAX, PATTERN));
    }

    @Test
    void rejectsColorCodesWhenReusedForTags() {
        // Faction tags reuse this validator with a short length bound. An '&'
        // color code must be rejected so it can't be turned into a real section
        // sign and rendered as formatting in faction/ally chat.
        assertEquals(Result.INVALID_CHARS, FactionNameValidator.validate("&c", 1, 6, PATTERN));
        assertEquals(Result.VALID, FactionNameValidator.validate("KOS", 1, 6, PATTERN));
    }

    @Test
    void defaultPatternIsAnchoredAndQuantified() {
        // Guards against regressing to a bare single-character class, the
        // original bug: such a pattern would match "a" but not "ab".
        assertTrue("ab".matches(PATTERN), "default pattern must match multi-char names");
        assertTrue("a".matches(PATTERN), "default pattern must match single-char names");
    }
}
