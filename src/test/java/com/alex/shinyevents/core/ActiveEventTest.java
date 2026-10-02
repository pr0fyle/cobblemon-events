package com.alex.shinyevents.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActiveEventTest {
    @Test
    void countsDownAndExpiresAtExactDeadline() {
        ActiveEvent event = new ActiveEvent(EventType.SHINY, 8, 10_000, 25_000);
        assertEquals(15_000, event.remainingMillis(10_000));
        assertEquals(1, event.progress(10_000));
        assertEquals(7_500, event.remainingMillis(17_500));
        assertEquals(0.5f, event.progress(17_500));
        assertEquals(8, event.remainingSeconds(17_500));
        assertEquals(1, event.remainingSeconds(24_999));
        assertTrue(event.isActive(24_999));
        assertFalse(event.isActive(25_000));
        assertEquals(0, event.remainingMillis(25_000));
        assertEquals(0, event.remainingSeconds(25_000));
        assertEquals(0, event.progress(25_000));
        assertEquals(0, event.remainingMillis(Long.MAX_VALUE));
    }

    @Test
    void clampsBackwardClockAdjustmentsToFullBarWithoutOverflow() {
        ActiveEvent event = new ActiveEvent(EventType.SHINY, 8, 10_000, 25_000);
        assertEquals(15_000, event.remainingMillis(Long.MIN_VALUE));
        assertEquals(1, event.progress(Long.MIN_VALUE));
        assertTrue(event.isActive(Long.MIN_VALUE));
    }

    @Test
    void validatesPersistedEventData() {
        for (double multiplier : new double[]{Double.NaN, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, -8, 0, 1, 1_000_001}) {
            assertThrows(IllegalArgumentException.class, () -> new ActiveEvent(EventType.SHINY, multiplier, 0, 1_000));
        }
        assertThrows(IllegalArgumentException.class, () -> new ActiveEvent(EventType.SHINY, 8, 10, 10));
        assertThrows(IllegalArgumentException.class, () -> new ActiveEvent(EventType.SHINY, 8, 10, 9));
        assertThrows(IllegalArgumentException.class,
                () -> new ActiveEvent(EventType.SHINY, 8, 0, EventArguments.MAX_DURATION_MILLIS + 1));
        assertThrows(IllegalArgumentException.class,
                () -> new ActiveEvent(EventType.SHINY, 8, Long.MIN_VALUE, Long.MAX_VALUE));
        assertEquals(EventArguments.MAX_DURATION_MILLIS,
                new ActiveEvent(EventType.SHINY, 8, 0, EventArguments.MAX_DURATION_MILLIS).remainingMillis(0));
    }

    @Test
    void boostsDenominatorsAndCapsAtGuaranteedShiny() {
        assertEquals(512f, ActiveEvent.boostShinyRate(4096f, 8));
        assertEquals(1f, ActiveEvent.boostShinyRate(4f, 8));
        assertEquals(1f, ActiveEvent.boostShinyRate(1f, 8));

    }

    @Test
    void preservesGuaranteedShininessForFractionalDenominators() {
        assertEquals(1f, ActiveEvent.boostShinyRate(0.015625f, 8));
        assertEquals(1f, ActiveEvent.boostShinyRate(0.5f, 8));
        assertEquals(1f, ActiveEvent.boostShinyRate(Math.nextDown(1f), 8));
        assertEquals(1f, ActiveEvent.boostShinyRate(Float.MIN_VALUE, 8));
    }

    @Test
    void preservesDisabledAndNonFiniteRates() {
        for (float rate : new float[]{0f, -0f, -1f, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NaN}) {
            assertEquals(Float.floatToIntBits(rate), Float.floatToIntBits(ActiveEvent.boostShinyRate(rate, 8)));
        }
    }

    @Test
    void refusesInvalidBoostMultipliers() {
        assertThrows(IllegalArgumentException.class, () -> ActiveEvent.boostShinyRate(4096f, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> ActiveEvent.boostShinyRate(4096f, 0));
    }

    @Test
    void differentEventTypesHaveIndependentDeadlines() {
        ActiveEvent shiny = new ActiveEvent(EventType.SHINY, 8, 1_000, 61_000);
        ActiveEvent xp = new ActiveEvent(EventType.POKEMONXP, 2, 1_000, 121_000);
        assertFalse(shiny.isActive(61_000));
        assertTrue(xp.isActive(61_000));
        assertEquals(0, shiny.progress(61_000));
        assertEquals(0.5f, xp.progress(61_000));
    }

    @Test
    void appliesTheMultiplierLimitForEachEventType() {
        for (EventType type : EventType.values()) {
            assertEquals(type.maximumMultiplier(),
                    new ActiveEvent(type, type.maximumMultiplier(), 0, 1_000).multiplier());
            assertThrows(IllegalArgumentException.class,
                    () -> new ActiveEvent(type, Math.nextUp(type.maximumMultiplier()), 0, 1_000));
        }
        assertThrows(IllegalArgumentException.class, () -> new ActiveEvent(null, 2, 0, 1_000));
        assertThrows(IllegalArgumentException.class, () -> new ActiveEvent(EventType.BERRIES, 65, 0, 1_000));
    }
}
