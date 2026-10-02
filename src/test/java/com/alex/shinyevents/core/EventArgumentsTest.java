package com.alex.shinyevents.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventArgumentsTest {
    @Test
    void acceptsHumanReadableMultipliers() {
        assertEquals(8, EventArguments.parseMultiplier("8x"));
        assertEquals(8, EventArguments.parseMultiplier("8"));
        assertEquals(2.5, EventArguments.parseMultiplier("2.5X"));
        assertEquals(1.01, EventArguments.parseMultiplier(" 1.01x "));
        assertEquals(EventArguments.MAX_MULTIPLIER, EventArguments.parseMultiplier("1000000x"));
    }

    @Test
    void rejectsInvalidOrOutOfRangeMultipliers() {
        for (String input : new String[]{"", " ", "1x", "0", "-8x", "NaN", "Infinity", "8xx",
                "8 x", "x8", "1e3", "1000001x", "99999999999999999999999999999999999999999999999999x"}) {
            assertThrows(IllegalArgumentException.class, () -> EventArguments.parseMultiplier(input), input);
        }
        assertThrows(IllegalArgumentException.class, () -> EventArguments.parseMultiplier(null));
    }

    @Test
    void acceptsSingleAndCompoundDurations() {
        assertEquals(30_000, EventArguments.parseDurationMillis("30s"));
        assertEquals(900_000, EventArguments.parseDurationMillis("15m"));
        assertEquals(5_400_000, EventArguments.parseDurationMillis("1h30m"));
        assertEquals(86_400_000, EventArguments.parseDurationMillis("1d"));
        assertEquals(90_061_000, EventArguments.parseDurationMillis("1d1h1m1s"));
        assertEquals(900_000, EventArguments.parseDurationMillis("0h15m"));
        assertEquals(1_000, EventArguments.parseDurationMillis(" 1S "));
        assertEquals(EventArguments.MAX_DURATION_MILLIS, EventArguments.parseDurationMillis("7d"));
    }

    @Test
    void rejectsMalformedDurationsAndZero() {
        for (String input : new String[]{"", " ", "0s", "0h0m", "15", "m15", "15ms", "1.5h",
                "1h 30m", "-1m", "+1m", "1h/30m", "1h30", "junk1m", "1m!", "1w"}) {
            assertThrows(IllegalArgumentException.class, () -> EventArguments.parseDurationMillis(input), input);
        }
        assertThrows(IllegalArgumentException.class, () -> EventArguments.parseDurationMillis(null));
    }

    @Test
    void rejectsOversizedDurationsWithoutOverflow() {
        for (String input : new String[]{"7d1s", "8d", "604801s", "9223372036854775807s",
                "9223372036854775808s", "999999999999999999999999999999999999999999d"}) {
            assertThrows(IllegalArgumentException.class, () -> EventArguments.parseDurationMillis(input), input);
        }
    }

    @Test
    void checksTypeSpecificMultiplierLimitsWithoutChangingTheGenericParser() {
        assertEquals(64, EventArguments.parseMultiplier(EventType.BERRIES, "64x"));
        assertEquals(65, EventArguments.parseMultiplier(EventType.POKEMONXP, "65x"));
        assertEquals(65, EventArguments.parseMultiplier("65x"));
        assertThrows(IllegalArgumentException.class, () -> EventArguments.parseMultiplier(EventType.BERRIES, "65x"));
        assertThrows(IllegalArgumentException.class, () -> EventArguments.parseMultiplier(null, "2x"));
    }
}
